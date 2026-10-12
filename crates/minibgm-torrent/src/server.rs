use std::borrow::Cow;
use std::collections::HashSet;
use std::net::Ipv4Addr;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;

use anyhow::{bail, Context, Result};
use bytes::Bytes;
use librqbit::api::{Api, TorrentIdOrHash};
use librqbit::http_api::{HttpApi, HttpApiOptions};
use librqbit::storage::examples::inmemory::InMemoryExampleStorageFactory;
use librqbit::storage::StorageFactoryExt;
use librqbit::{
    AddTorrent, AddTorrentOptions, AddTorrentResponse, ManagedTorrent, Session, SessionOptions,
};
use librqbit_dualstack_sockets::{BindOpts, TcpListener};
use tokio_util::sync::CancellationToken;

type ManagedTorrentHandle = Arc<ManagedTorrent>;

pub struct StreamConfig {
    pub magnet_url: String,
    pub torrent_path: Option<PathBuf>,
    pub save_dir: PathBuf,
}

pub struct StreamStats {
    pub downloaded_bytes: u64,
    pub total_bytes: u64,
    pub speed_bytes_per_sec: u64,
    pub connected_peers: u64,
}

pub struct StreamServer {
    pub stream_url: String,
    _session: Arc<Session>,
    handle: ManagedTorrentHandle,
    target_file_id: usize,
    target_file_len: u64,
    cancel_token: CancellationToken,
}

impl StreamServer {
    pub async fn start(config: StreamConfig) -> Result<Self> {
        // 1. 初始化纯内存存储工厂（0 闪存写入，纯 RAM 流化）
        let storage_factory = InMemoryExampleStorageFactory::default().boxed();

        let cancel_token = CancellationToken::new();
        let session_opts = SessionOptions {
            default_storage_factory: Some(storage_factory),
            fastresume: false,
            persistence: None,
            cancellation_token: Some(cancel_token.clone()),
            ..Default::default()
        };

        // 使用应用传入的私有缓存目录作为 Session 根目录（杜绝根目录 / 权限异常）
        let session = Session::new_with_opts(config.save_dir, session_opts)
            .await
            .context("初始化 librqbit 内存 Session 失败")?;

        // 2. 绑定本地回环端口启动只读 HTTP 流媒体服务（消除未授权写入/控制攻击面）
        let bind_addr = (Ipv4Addr::LOCALHOST, 0).into();
        let sock = TcpListener::bind_tcp(
            bind_addr,
            BindOpts {
                request_dualstack: false,
                reuseport: false,
                device: None,
            },
        )
        .context("绑定 HTTP 流服务端口失败")?;
        let port = sock.bind_addr().port();

        let api = Api::new(session.clone(), None, None);
        let http = HttpApi::new(
            api.clone(),
            Some(HttpApiOptions {
                read_only: true,
                allow_create: false,
                ..Default::default()
            }),
        );

        let cancel_for_http = cancel_token.clone();
        tokio::spawn(async move {
            tokio::select! {
                _ = cancel_for_http.cancelled() => {}
                _ = http.make_http_api_and_run(sock, None) => {}
            }
        });

        // 3. 添加种子或磁力（前置正则过滤视频后缀，防止合集/BD包全部文件进队列造成内存爆炸）
        let add = if let Some(path) = &config.torrent_path {
            let data = tokio::fs::read(path)
                .await
                .with_context(|| format!("读取种子文件失败: {:?}", path))?;
            AddTorrent::TorrentFileBytes(Bytes::from(data))
        } else {
            AddTorrent::Url(Cow::Borrowed(&config.magnet_url))
        };

        let add_opts = AddTorrentOptions {
            paused: false,
            only_files_regex: Some(r"(?i)\.(mkv|mp4|webm|avi|flv|ts|m2ts)$".to_string()),
            ..Default::default()
        };

        let add_res = session
            .add_torrent(add, Some(add_opts))
            .await
            .context("添加种子至 Session 失败")?;

        let (torrent_id, handle) = match add_res {
            AddTorrentResponse::Added(id, h) => (id, h),
            AddTorrentResponse::AlreadyManaged(id, h) => (id, h),
            AddTorrentResponse::ListOnly(_) => {
                bail!("种子仅添加为列表模式");
            }
        };

        // 4. 等待初始元数据解析（显式超时判定，严禁吞掉超时伪装就绪）
        match tokio::time::timeout(Duration::from_secs(30), handle.wait_until_initialized()).await {
            Ok(Ok(())) => {}
            Ok(Err(e)) => bail!("解析种子元数据失败: {e}"),
            Err(_) => {
                bail!("解析磁力元数据超时（30秒内未寻得做种 Peer），请检查网络连接或更换可用源")
            }
        }

        // 5. 选取精确的单集视频文件，并二次收敛 only_files 为单文件
        let details = api
            .api_torrent_details(TorrentIdOrHash::Id(torrent_id))
            .context("获取种子文件列表详情失败")?;

        let (target_file_id, target_file_len) = details
            .files
            .as_ref()
            .and_then(|files| {
                files
                    .iter()
                    .enumerate()
                    .max_by_key(|(_, f)| f.length)
                    .map(|(idx, f)| (idx, f.length))
            })
            .unwrap_or((0, 0));

        if target_file_len == 0 {
            bail!("种子中未发现有效的视频媒体文件");
        }

        // 精确锁定仅下载该目标视频文件，彻底杜绝合集其他剧集占爆 RAM
        let only_single_file = HashSet::from([target_file_id]);
        session
            .update_only_files(&handle, &only_single_file)
            .await
            .context("配置单文件精准下载队列失败")?;

        let stream_url =
            format!("http://127.0.0.1:{port}/torrents/{torrent_id}/stream/{target_file_id}");

        Ok(Self {
            stream_url,
            _session: session,
            handle,
            target_file_id,
            target_file_len,
            cancel_token,
        })
    }

    pub fn get_stats(&self) -> StreamStats {
        let stats = self.handle.stats();
        // 进度真实分母：精准对应当前选中播放文件的长度与下载量
        let downloaded_bytes = stats
            .file_progress
            .get(self.target_file_id)
            .copied()
            .unwrap_or(0);
        let total_bytes = self.target_file_len;

        let (speed_bytes_per_sec, connected_peers) = if let Some(live) = &stats.live {
            (
                live.download_speed.as_bytes(),
                live.snapshot.peer_stats.live as u64,
            )
        } else {
            (0, 0)
        };

        StreamStats {
            downloaded_bytes,
            total_bytes,
            speed_bytes_per_sec,
            connected_peers,
        }
    }

    pub fn stop(&self) {
        self.cancel_token.cancel();
    }
}
