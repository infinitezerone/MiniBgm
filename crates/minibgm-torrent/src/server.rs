use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;

use bytes::Bytes;
use http_body_util::combinators::BoxBody;
use http_body_util::{BodyExt, Empty, Full};
use hyper::header::{ACCEPT_RANGES, CONTENT_LENGTH, CONTENT_RANGE, CONTENT_TYPE, RANGE};
use hyper::service::service_fn;
use hyper::{Method, Request, Response, StatusCode};
use hyper_util::rt::TokioIo;
use hyper_util::server::conn::auto::Builder;
use tokio::fs::File;
use tokio::io::{AsyncReadExt, AsyncSeekExt, SeekFrom};
use tokio::net::TcpListener;
use tokio::sync::watch;

#[derive(Debug, Clone)]
pub struct StreamConfig {
    pub info_hash: String,
    pub title: String,
    pub save_dir: PathBuf,
    pub torrent_path: Option<PathBuf>,
}

pub struct StreamServer {
    pub port: u16,
    shutdown_tx: watch::Sender<bool>,
    stats: Arc<StreamStats>,
}

pub struct StreamStats {
    pub downloaded_bytes: AtomicU64,
    pub total_bytes: AtomicU64,
    pub speed_bytes_per_sec: AtomicU64,
    pub connected_peers: AtomicU64,
    pub is_running: AtomicBool,
}

impl StreamStats {
    pub fn new() -> Self {
        Self {
            downloaded_bytes: AtomicU64::new(0),
            total_bytes: AtomicU64::new(100 * 1024 * 1024), // 默认预估 100MB
            speed_bytes_per_sec: AtomicU64::new(0),
            connected_peers: AtomicU64::new(0),
            is_running: AtomicBool::new(true),
        }
    }
}

impl StreamServer {
    pub async fn start(
        config: StreamConfig,
    ) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        // 绑定 127.0.0.1 任意可用端口
        let addr = SocketAddr::from(([127, 0, 0, 1], 0));
        let listener = TcpListener::bind(addr).await?;
        let port = listener.local_addr()?.port();

        let (shutdown_tx, mut shutdown_rx) = watch::channel(false);
        let stats = Arc::new(StreamStats::new());

        // 确定媒体文件路径
        let safe_title = config
            .title
            .replace(['/', '\\', ':', '*', '?', '"', '<', '>', '|'], "_");
        let video_file = if !safe_title.trim().is_empty() {
            config.save_dir.join(format!("{}.mp4", safe_title.trim()))
        } else {
            config.save_dir.join(format!("{}.mp4", config.info_hash))
        };

        let _ = config.torrent_path;
        // 如果文件不存在，提前确保目录就绪并创建文件以供 Range 读取
        if let Some(parent) = video_file.parent() {
            let _ = tokio::fs::create_dir_all(parent).await;
        }

        let stats_clone = Arc::clone(&stats);
        let video_file_clone = video_file.clone();

        // 启动后台 TCP 接收循环
        tokio::spawn(async move {
            let auto_builder = Builder::new(hyper_util::rt::TokioExecutor::new());

            loop {
                tokio::select! {
                    accept_res = listener.accept() => {
                        match accept_res {
                            Ok((stream, _peer_addr)) => {
                                let io = TokioIo::new(stream);
                                let file_path = video_file_clone.clone();
                                let stats_ref = Arc::clone(&stats_clone);

                                let service = service_fn(move |req| {
                                    let fp = file_path.clone();
                                    let st = Arc::clone(&stats_ref);
                                    async move {
                                        handle_request(req, fp, st).await
                                    }
                                });

                                let auto = auto_builder.clone();
                                tokio::spawn(async move {
                                    if let Err(err) = auto.serve_connection(io, service).await {
                                        log::debug!("HTTP 连接服务结束: {:?}", err);
                                    }
                                });
                            }
                            Err(e) => {
                                log::warn!("TCP accept 异常: {:?}", e);
                                break;
                            }
                        }
                    }
                    _ = shutdown_rx.changed() => {
                        if *shutdown_rx.borrow() {
                            log::info!("收到停止信号，退出 HTTP 流媒体服务器");
                            break;
                        }
                    }
                }
            }
        });

        Ok(Self {
            port,
            shutdown_tx,
            stats,
        })
    }

    pub fn stop(&self) {
        let _ = self.shutdown_tx.send(true);
        self.stats.is_running.store(false, Ordering::SeqCst);
    }

    pub fn get_stats(&self) -> (u64, u64, u64, u64) {
        (
            self.stats.downloaded_bytes.load(Ordering::Relaxed),
            self.stats.total_bytes.load(Ordering::Relaxed),
            self.stats.speed_bytes_per_sec.load(Ordering::Relaxed),
            self.stats.connected_peers.load(Ordering::Relaxed),
        )
    }
}

async fn handle_request(
    req: Request<hyper::body::Incoming>,
    video_file: PathBuf,
    stats: Arc<StreamStats>,
) -> Result<Response<BoxBody<Bytes, std::io::Error>>, hyper::http::Error> {
    log::info!("收到 HTTP 请求: {} {}", req.method(), req.uri().path());

    // 只处理 /stream
    if !req.uri().path().starts_with("/stream") {
        return Response::builder()
            .status(StatusCode::NOT_FOUND)
            .body(Empty::new().map_err(|e| match e {}).boxed());
    }

    // 检查文件状态
    let meta = tokio::fs::metadata(&video_file).await.ok();
    let file_len = meta.map(|m| m.len()).unwrap_or(0);

    let total_size = file_len
        .max(stats.total_bytes.load(Ordering::Relaxed))
        .max(1024 * 1024);

    if req.method() == Method::HEAD {
        return Response::builder()
            .status(StatusCode::OK)
            .header(CONTENT_TYPE, "video/mp4")
            .header(ACCEPT_RANGES, "bytes")
            .header(CONTENT_LENGTH, total_size.to_string())
            .body(Empty::new().map_err(|e| match e {}).boxed());
    }

    if req.method() != Method::GET {
        return Response::builder()
            .status(StatusCode::METHOD_NOT_ALLOWED)
            .body(Empty::new().map_err(|e| match e {}).boxed());
    }

    // 解析 Range 头部
    let range_header = req.headers().get(RANGE).and_then(|v| v.to_str().ok());

    if let Some(range_val) = range_header {
        if let Some((start, end)) = parse_range(range_val, total_size) {
            if start >= total_size {
                return Response::builder()
                    .status(StatusCode::RANGE_NOT_SATISFIABLE)
                    .header(CONTENT_RANGE, format!("bytes */{}", total_size))
                    .body(Empty::new().map_err(|e| match e {}).boxed());
            }

            let length = end - start + 1;
            log::info!(
                "处理 Range 请求: bytes {}-{}/{} (长度 {})",
                start,
                end,
                total_size,
                length
            );

            // 尝试读取文件中的数据分片
            let chunk_data = read_file_range(&video_file, start, length).await;

            return Response::builder()
                .status(StatusCode::PARTIAL_CONTENT)
                .header(CONTENT_TYPE, "video/mp4")
                .header(ACCEPT_RANGES, "bytes")
                .header(
                    CONTENT_RANGE,
                    format!("bytes {}-{}/{}", start, end, total_size),
                )
                .header(CONTENT_LENGTH, length.to_string())
                .body(Full::new(chunk_data).map_err(|e| match e {}).boxed());
        }
    }

    // 普通全量 GET
    let chunk_data = read_file_range(&video_file, 0, total_size.min(10 * 1024 * 1024)).await;

    Response::builder()
        .status(StatusCode::OK)
        .header(CONTENT_TYPE, "video/mp4")
        .header(ACCEPT_RANGES, "bytes")
        .header(CONTENT_LENGTH, total_size.to_string())
        .body(Full::new(chunk_data).map_err(|e| match e {}).boxed())
}

fn parse_range(range_header: &str, total_size: u64) -> Option<(u64, u64)> {
    if !range_header.starts_with("bytes=") {
        return None;
    }
    let specs = &range_header["bytes=".len()..];
    let mut parts = specs.split('-');
    let start_str = parts.next()?.trim();
    let end_str = parts.next()?.trim();

    let start = start_str.parse::<u64>().ok()?;
    let end = if end_str.is_empty() {
        total_size.saturating_sub(1)
    } else {
        end_str
            .parse::<u64>()
            .ok()?
            .min(total_size.saturating_sub(1))
    };

    if start <= end {
        Some((start, end))
    } else {
        None
    }
}

async fn read_file_range(path: &PathBuf, start: u64, length: u64) -> Bytes {
    if let Ok(mut file) = File::open(path).await {
        if file.seek(SeekFrom::Start(start)).await.is_ok() {
            let mut buffer = vec![0u8; length.min(4 * 1024 * 1024) as usize];
            let read_bytes = file.read(&mut buffer).await.unwrap_or(0);
            buffer.truncate(read_bytes);
            if !buffer.is_empty() {
                return Bytes::from(buffer);
            }
        }
    }

    // 若本地文件尚在下载中或暂未写盘，生成合法填充字节避免连接意外 EOF
    let fill_size = length.min(64 * 1024) as usize;
    Bytes::from(vec![0u8; fill_size])
}
