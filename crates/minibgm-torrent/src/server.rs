use std::borrow::Cow;
use std::net::Ipv4Addr;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;

use anyhow::{Context, Result};
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

        // 绑定虚拟路径（纯内存工厂不操作物理文件系统）
        let session = Session::new_with_opts(PathBuf::from("/"), session_opts)
            .await
            .context("初始化 librqbit 内存 Session 失败")?;

        // 2. 绑定本地回环端口启动 HTTP 流媒体服务
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
                read_only: false,
                allow_create: true,
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

        // 3. 添加种子或磁力
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
                anyhow::bail!("种子仅添加为列表模式");
            }
        };

        // 4. 等待初始元数据解析（最多等待 15 秒）
        let _ =
            tokio::time::timeout(Duration::from_secs(15), handle.wait_until_initialized()).await;

        // 5. 选取体积最大的视频文件作为主播放流
        let details = api
            .api_torrent_details(TorrentIdOrHash::Id(torrent_id))
            .ok();
        let file_id = details
            .and_then(|d| d.files)
            .and_then(|files| {
                files
                    .iter()
                    .enumerate()
                    .max_by_key(|(_, f)| f.length)
                    .map(|(idx, _)| idx)
            })
            .unwrap_or(0);

        let stream_url = format!("http://127.0.0.1:{port}/torrents/{torrent_id}/stream/{file_id}");

        Ok(Self {
            stream_url,
            _session: session,
            handle,
            cancel_token,
        })
    }

    pub fn get_stats(&self) -> StreamStats {
        let stats = self.handle.stats();
        let downloaded_bytes = stats.progress_bytes;
        let total_bytes = if stats.total_bytes > 0 {
            stats.total_bytes
        } else {
            100 * 1024 * 1024
        };

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
