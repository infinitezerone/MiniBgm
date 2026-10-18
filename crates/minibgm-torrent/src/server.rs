use std::borrow::Cow;
use std::collections::HashSet;
use std::net::Ipv4Addr;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;

use anyhow::{bail, Context, Result};
use bytes::Bytes;
use librqbit::api::{Api, TorrentDetailsResponseFile, TorrentIdOrHash};
use librqbit::http_api::{HttpApi, HttpApiOptions};
use librqbit::storage::filesystem::FilesystemStorageFactory;
use librqbit::storage::StorageFactoryExt;
use librqbit::{
    AddTorrent, AddTorrentOptions, AddTorrentResponse, ManagedTorrent, Session, SessionOptions,
    TorrentStatsState,
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
    /// 引擎级错误态（librqbit `TorrentStatsState::Error`）。
    ///
    /// 与「死种」是两回事：Peer 连不上只是 0 速度，librqbit 不会把种子判为 Error；
    /// 该状态只在磁盘写入失败（`write_chunk`）或初始化校验失败这类致命错误时进入，
    /// 必须重新初始化才能恢复。不上报的话，用户会在死种看门狗超时后被告知
    /// 「连不上做种节点」——把磁盘写满这类真实原因伪装成死种。
    pub engine_error: bool,
    /// 引擎级错误原文（`TorrentStats.error`），仅在 `engine_error` 为真时才有意义。
    pub error_message: Option<String>,
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

        let dht_nodes_path = config.save_dir.join("dht_nodes.json");
        let dht_bootstrap = vec![
            "router.bittorrent.com:6881".to_string(),
            "router.utorrent.com:6881".to_string(),
            "dht.transmissionbt.com:6881".to_string(),
            "dht.libtorrent.org:25401".to_string(),
            "dht.aelitis.com:6881".to_string(),
        ];

        let session_opts = SessionOptions {
            default_storage_factory: Some(storage_factory),
            fastresume: false,
            persistence: None,
            dht: Some(librqbit::DhtSessionConfig {
                persistence: Some(librqbit::dht::DhtPersistenceConfig {
                    config_filename: Some(dht_nodes_path),
                    dump_interval: Some(Duration::from_secs(30)),
                }),
                bootstrap_addrs: Some(dht_bootstrap),
                ..Default::default()
            }),
            client_name_and_version: Some("qBittorrent/4.6.5".to_string()),
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

        // 必须以暂停态添加：元数据解析完成即停在 Paused，不建立 Peer 连接、不请求任何分片。
        // 若此处 paused: false，种子会先按"整包视频文件"开始选片，直到第 5 步才收敛为单集，
        // 该窗口在合集/BD 包上会白下其他剧集的分片（详见第 6 步的 unpause）。
        let add_opts = AddTorrentOptions {
            paused: true,
            overwrite: true,
            only_files_regex: Some(video_extension_regex()),
            ..Default::default()
        };

        let add_res = session
            .add_torrent(add, Some(add_opts))
            .await
            .map_err(|err| {
                // librqbit 的 only_files_regex 在无任何文件匹配时会直接 bail，原始文案
                // （"none of the filenames match the given regex"）对用户不可操作，需映射为可读提示。
                if is_no_matching_video_error(&err) {
                    anyhow::anyhow!(
                        "种子中未找到可播放的视频文件（该种子可能仅含音频、图片或原盘镜像）"
                    )
                } else {
                    err.context("添加种子至 Session 失败")
                }
            })?;

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

        // 空串按"无集数信息"处理：Android 侧在无集数上下文时会下发空串而非 null
        let episode_hint = config
            .episode_hint
            .as_deref()
            .map(str::trim)
            .filter(|hint| !hint.is_empty());

        let (target_file_id, target_file_len, target_components) = select_target_video_file(
            &valid_video_files,
            episode_hint,
        )?
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

        // 6. 单集锁定完成，此刻才解冻：从这一步起才建立 Peer 连接并只请求目标文件的分片。
        // 元数据阶段全程处于 Paused，零下载、零白下；update_only_files 在 Paused 态已收敛
        // 分片选择，TorrentStateLive::new 直接继承该 chunk tracker，解冻即按单集生效。
        session
            .unpause(&handle)
            .await
            .context("启动目标单集下载失败")?;

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

        // 只在 Error 态透出错误原文：种子的错误态必须经重新初始化才能恢复，
        // 状态码与文案同源同帧，杜绝"已恢复但文案仍在"的错配。
        let engine_error = matches!(stats.state, TorrentStatsState::Error);

        StreamStats {
            downloaded_bytes,
            total_bytes,
            speed_bytes_per_sec,
            connected_peers,
            engine_error,
            error_message: if engine_error {
                stats.error.clone()
            } else {
                None
            },
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

/// 视频扩展名白名单的**唯一真源**。
///
/// `only_files_regex`（添加种子时的前置过滤）与 `is_video_file`（元数据就绪后的二次校验）
/// 必须由同一份清单派生：两份硬编码清单一旦漂移，就会出现"文件被 included 却永不入选"
/// 或"非视频文件混入候选"的静默不一致，且只在特定种子命名下才暴露。
const VIDEO_EXTENSIONS: [&str; 7] = ["mkv", "mp4", "webm", "avi", "flv", "ts", "m2ts"];

/// 由 `VIDEO_EXTENSIONS` 派生 librqbit `only_files_regex`，杜绝第二份硬编码清单。
fn video_extension_regex() -> String {
    format!(r"(?i)\.({})$", VIDEO_EXTENSIONS.join("|"))
}

pub fn is_video_file(name: &str) -> bool {
    let ext = name.rsplit('.').next().unwrap_or("");
    VIDEO_EXTENSIONS
        .iter()
        .any(|allowed| allowed.eq_ignore_ascii_case(ext))
}

/// librqbit 的 `only_files_regex` 在无任何文件匹配时会直接让 `add_torrent` 失败
/// （`none of the filenames match the given regex`）。该文案对用户不可操作，
/// 需识别出来映射为可读提示；其余失败原因保持原样透传，不做无差别改写。
fn is_no_matching_video_error(err: &anyhow::Error) -> bool {
    err.chain().any(|cause| {
        cause
            .to_string()
            .contains("none of the filenames match the given regex")
    })
}

/// 从候选视频文件中挑选目标单集（纯函数，便于单测覆盖"多视频文件禁止静默回退"守卫）。
///
/// 返回 `(文件索引, 文件字节数, 落盘路径分量)`；候选为空时返回 `None`。
///
/// - 命中 `episode_hint` 时取其中体积最大者（同一集可能被切分或附带特典）；
/// - 未命中且候选多于一个时**显式失败**：合集/BD 包会稳定挑错集，用户看到的是
///   "播了别的集"且全程无任何提示，比直接报错严重得多；
/// - 候选仅一个时直接选中（那就是唯一正片，无需集数信息）。
fn select_target_video_file(
    video_files: &[(usize, &TorrentDetailsResponseFile)],
    episode_hint: Option<&str>,
) -> Result<Option<(usize, u64, Vec<String>)>> {
    let picked = if let Some(hint) = episode_hint {
        video_files
            .iter()
            .filter(|(_, f)| matches_episode_hint(&f.name, hint))
            .copied()
            .max_by_key(|(_, f)| f.length)
    } else {
        None
    };

    let picked = match (picked, video_files.len()) {
        (Some(picked), _) => Some(picked),
        (None, 1) => video_files.first().copied(),
        (None, 0) => None,
        (None, count) => bail!(
            "该种子包含 {count} 个视频文件，未能按集数 {} 定位目标文件，请改用单集资源",
            episode_hint.unwrap_or("（未提供）")
        ),
    };

    Ok(picked.map(|(idx, f)| (idx, f.length, f.components.clone())))
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
                            // 'v' 覆盖 `04v2` / `01v2` 这类重制版命名，缺失会把单集种子
                            // 误判为"未命中集数"，进而触发多视频文件的显式失败。
                            const SUFFIX_SEPARATORS: [char; 11] =
                                ['-', '_', '.', ']', ')', '}', '话', '集', '/', '\\', 'v'];
                            let next_char = lower[end..].chars().next().unwrap();
                            next_char.is_whitespace() || SUFFIX_SEPARATORS.contains(&next_char)
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

    #[test]
    fn test_matches_episode_hint_accepts_versioned_episode_suffix() {
        // `04v2` / `01v2` 重制版命名：'v' 必须在后缀白名单内，
        // 否则单集种子会被误判为"未命中集数"，进而触发多视频文件的显式失败。
        assert!(matches_episode_hint(
            "[Sakurato] Steel Ball Run [04v2].mkv",
            "4"
        ));
        assert!(matches_episode_hint("SBR - 01v2.mkv", "1"));
        assert!(!matches_episode_hint("SBR - 04v2.mkv", "5"));
    }

    #[test]
    fn test_video_extension_whitelist_is_single_source_of_truth() {
        let regex = video_extension_regex();
        assert_eq!(regex, r"(?i)\.(mkv|mp4|webm|avi|flv|ts|m2ts)$");

        for ext in VIDEO_EXTENSIONS {
            assert!(
                is_video_file(&format!("sample.{ext}")),
                "{ext} 未通过 is_video_file 校验"
            );
            assert!(
                regex.contains(ext),
                "only_files_regex 未包含扩展名 {ext}，两份白名单已漂移"
            );
        }

        for ext in ["iso", "flac", "nfo", "png", "srt", "rar", "zip"] {
            assert!(
                !is_video_file(&format!("sample.{ext}")),
                "{ext} 不应被判为视频文件"
            );
            assert!(
                !regex.contains(ext),
                "only_files_regex 不应放行非视频扩展名 {ext}"
            );
        }
    }

    #[test]
    fn test_is_no_matching_video_error_detects_librqbit_empty_regex_failure() {
        let direct = anyhow::anyhow!("none of the filenames match the given regex");
        assert!(is_no_matching_video_error(&direct));

        let wrapped = anyhow::anyhow!("none of the filenames match the given regex")
            .context("添加种子至 Session 失败");
        assert!(is_no_matching_video_error(&wrapped));

        let unrelated = anyhow::anyhow!("解析磁力元数据超时").context("添加种子至 Session 失败");
        assert!(!is_no_matching_video_error(&unrelated));
    }

    fn video_file(name: &str, length: u64) -> TorrentDetailsResponseFile {
        TorrentDetailsResponseFile {
            name: name.to_string(),
            components: vec![name.to_string()],
            length,
            included: true,
            attributes: Default::default(),
        }
    }

    #[test]
    fn test_select_target_video_file_picks_hinted_episode() {
        let ep01 = video_file("SBR - 01.mkv", 1_000);
        let ep04 = video_file("SBR - 04.mkv", 2_000);
        let ep05 = video_file("SBR - 05.mkv", 9_000);
        let files = vec![(0, &ep01), (1, &ep04), (2, &ep05)];

        let (idx, len, components) = select_target_video_file(&files, Some("4"))
            .expect("命中集数时不应失败")
            .expect("应选中 Ep 04");
        assert_eq!(idx, 1);
        assert_eq!(len, 2_000);
        assert_eq!(components, vec!["SBR - 04.mkv".to_string()]);
    }

    #[test]
    fn test_select_target_video_file_rejects_silent_max_file_fallback() {
        let ep01 = video_file("SBR - 01.mkv", 1_000);
        let ep12 = video_file("SBR - 12.mkv", 9_000);
        let files = vec![(0, &ep01), (1, &ep12)];

        // 无集数信息 + 多视频文件：严禁静默挑"体积最大"的 Ep 12
        let err = select_target_video_file(&files, None).expect_err("多视频且无集数必须显式失败");
        assert!(err.to_string().contains("2 个视频文件"));

        // 有集数但未命中同样必须失败，而不是回退最大文件
        assert!(select_target_video_file(&files, Some("7")).is_err());
    }

    #[test]
    fn test_select_target_video_file_allows_single_video_without_hint() {
        let only = video_file("SBR - Movie.mkv", 5_000);
        let files = vec![(0, &only)];

        let (idx, len, _) = select_target_video_file(&files, None)
            .expect("单视频种子不应失败")
            .expect("应选中唯一正片");
        assert_eq!(idx, 0);
        assert_eq!(len, 5_000);

        assert!(select_target_video_file(&[], None).unwrap().is_none());
    }
}
