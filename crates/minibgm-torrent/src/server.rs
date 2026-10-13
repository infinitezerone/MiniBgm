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
use librqbit::storage::filesystem::FilesystemStorageFactory;
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
    pub episode_hint: Option<String>,
}

pub struct StreamStats {
    pub downloaded_bytes: u64,
    pub total_bytes: u64,
    pub speed_bytes_per_sec: u64,
    pub connected_peers: u64,
}

pub struct StreamServer {
    pub stream_url: String,
    pub target_file_path: String,
    _session: Arc<Session>,
    handle: ManagedTorrentHandle,
    target_file_id: usize,
    target_file_len: u64,
    cancel_token: CancellationToken,
}

impl StreamServer {
    pub async fn start(config: StreamConfig) -> Result<Self> {
        let cancel_token = CancellationToken::new();
        match Self::start_with_token(config, cancel_token.clone()).await {
            Ok(server) => Ok(server),
            Err(err) => {
                // 初始化中途失败时立即取消 Session 与本地 HTTP 监听协程，杜绝后台 DHT / UDP 端口泄漏
                cancel_token.cancel();
                Err(err)
            }
        }
    }

    async fn start_with_token(
        config: StreamConfig,
        cancel_token: CancellationToken,
    ) -> Result<Self> {
        // 1. 初始化标准文件系统存储工厂（利用 OS Page Cache 高性能零拷贝，彻底杜绝 OOM）
        let storage_factory = FilesystemStorageFactory::default().boxed();

        let session_opts = SessionOptions {
            default_storage_factory: Some(storage_factory),
            fastresume: false,
            persistence: None,
            dht: Some(librqbit::DhtSessionConfig {
                persistence: None,
                ..Default::default()
            }),
            cancellation_token: Some(cancel_token.clone()),
            ..Default::default()
        };

        // 使用应用传入的私有缓存目录作为 Session 根目录（杜绝根目录 / 权限异常）
        let session = Session::new_with_opts(config.save_dir, session_opts)
            .await
            .context("初始化 librqbit 文件系统 Session 失败")?;

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

        // 3. 添加种子或磁力（前置正则过滤视频后缀，防止合集/BD包全部文件进队列造成内存爆炸；
        // 开启 overwrite: true 允许复用/覆盖缓存目录中已存在的同名媒体文件，避免二次启动报 os error 17）
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
            overwrite: true,
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

        let valid_video_files: Vec<_> = details
            .files
            .as_ref()
            .map(|files| {
                files
                    .iter()
                    .enumerate()
                    // 严格双重校验：必须被 only_files_regex 标记为 included 且扩展名确为视频
                    // 杜绝合集中体积更大的非视频文件（ISO/解压包/RAW原盘）夺取下载目标
                    .filter(|(_, f)| f.included && is_video_file(&f.name))
                    .collect()
            })
            .unwrap_or_default();

        let matched_target = if let Some(ref hint) = config.episode_hint {
            let matches: Vec<_> = valid_video_files
                .iter()
                .filter(|(_, f)| matches_episode_hint(&f.name, hint))
                .copied()
                .collect();
            // 若命中多个同分集文件（如不同分段或特典），取其中体积最大者
            matches.into_iter().max_by_key(|(_, f)| f.length)
        } else {
            None
        };

        let (target_file_id, target_file_len, target_components) = matched_target
            .or_else(|| valid_video_files.into_iter().max_by_key(|(_, f)| f.length))
            .map(|(idx, f)| (idx, f.length, f.components.clone()))
            .unwrap_or((0, 0, Vec::new()));

        if target_file_len == 0 {
            bail!("种子中未发现有效的视频媒体文件");
        }

        let target_file_path = resolve_target_file_path(&details.output_folder, &target_components);

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
            target_file_path,
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

pub fn resolve_target_file_path(output_folder: &str, components: &[String]) -> String {
    let mut target_file_path_buf = PathBuf::from(output_folder);
    for comp in components {
        target_file_path_buf.push(comp);
    }
    target_file_path_buf.to_string_lossy().into_owned()
}

pub fn is_video_file(name: &str) -> bool {
    let ext = name.rsplit('.').next().unwrap_or("");
    matches!(
        ext.to_ascii_lowercase().as_str(),
        "mkv" | "mp4" | "webm" | "avi" | "flv" | "ts" | "m2ts"
    )
}

pub fn matches_episode_hint(filename: &str, hint: &str) -> bool {
    let hint = hint.trim();
    if hint.is_empty() {
        return false;
    }

    if let Ok(target_num) = hint.parse::<u32>() {
        let lower = filename.to_lowercase();
        let bytes = lower.as_bytes();
        let mut i = 0;
        while i < bytes.len() {
            if bytes[i].is_ascii_digit() {
                let start = i;
                while i < bytes.len() && bytes[i].is_ascii_digit() {
                    i += 1;
                }
                let end = i;
                if let Ok(found_num) = lower[start..end].parse::<u32>() {
                    if found_num == target_num {
                        let is_valid_prefix = if start == 0 {
                            true
                        } else {
                            let prev_char = lower[..start].chars().last().unwrap();
                            prev_char.is_whitespace()
                                || matches!(
                                    prev_char,
                                    '-' | '_' | '.' | '[' | '(' | '{' | '第' | '/' | '\\' | 'e'
                                )
                                || (prev_char == 'p'
                                    && start >= 2
                                    && lower[..start].ends_with("ep"))
                        };

                        let is_valid_suffix = if end >= bytes.len() {
                            true
                        } else {
                            let next_char = lower[end..].chars().next().unwrap();
                            next_char.is_whitespace()
                                || matches!(
                                    next_char,
                                    '-' | '_' | '.' | ']' | ')' | '}' | '话' | '集' | '/' | '\\'
                                )
                        };

                        if is_valid_prefix && is_valid_suffix {
                            return true;
                        }
                    }
                }
            } else {
                i += 1;
            }
        }
        return false;
    }

    filename.to_lowercase().contains(&hint.to_lowercase())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_is_video_file_identifies_video_extensions_correctly() {
        assert!(is_video_file("[Baha] ep01.mp4"));
        assert!(is_video_file("Episode 1 [1080p].MKV"));
        assert!(is_video_file("raw_stream.m2ts"));
        assert!(!is_video_file("SP01_Bonus_Soundtrack.flac"));
        assert!(!is_video_file("Scans_Cover.png"));
        assert!(!is_video_file("Disc1.iso"));
        assert!(!is_video_file("Release.nfo"));
    }

    #[test]
    fn test_resolve_target_file_path_joins_output_folder_and_components() {
        let path = resolve_target_file_path(
            "/data/user/0/pkg/files/downloads",
            &["[Sakurato] Steel Ball Run [04].mkv".to_string()],
        );
        assert!(path.ends_with("[Sakurato] Steel Ball Run [04].mkv"));
    }

    #[test]
    fn test_matches_episode_hint_correctly_filters_episodes() {
        assert!(matches_episode_hint(
            "[Sakurato] Steel Ball Run [04].mkv",
            "4"
        ));
        assert!(matches_episode_hint(
            "[Sakurato] Steel Ball Run [04].mkv",
            "04"
        ));
        assert!(!matches_episode_hint(
            "[Sakurato] Steel Ball Run [04].mkv",
            "05"
        ));
        assert!(matches_episode_hint("Chainsaw Man EP02 [1080p].mp4", "2"));
        assert!(matches_episode_hint("Sousou no Frieren - 12.mkv", "12"));
        assert!(!matches_episode_hint("Sousou no Frieren - 12.mkv", "1"));
        assert!(matches_episode_hint("Delicious in Dungeon 第03话.mkv", "3"));
    }
}
