mod server;

use std::path::PathBuf;
use std::sync::{Mutex, MutexGuard, OnceLock};

use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jlongArray, jstring};
use jni::JNIEnv;
use server::{StreamConfig, StreamServer, StreamStats};
use tokio::runtime::{Builder, Runtime};

static RUNTIME: OnceLock<Result<Runtime, String>> = OnceLock::new();
static ACTIVE_SERVER: OnceLock<Mutex<Option<StreamServer>>> = OnceLock::new();

fn runtime() -> Result<&'static Runtime, String> {
    RUNTIME
        .get_or_init(|| {
            Builder::new_multi_thread()
                .enable_io()
                .enable_time()
                .worker_threads(2)
                .build()
                .map_err(|e| format!("初始化 Tokio 运行时失败: {e}"))
        })
        .as_ref()
        .map_err(|e| e.clone())
}

fn active_server() -> &'static Mutex<Option<StreamServer>> {
    ACTIVE_SERVER.get_or_init(|| Mutex::new(None))
}

/// 取活跃会话锁，并容忍毒锁。
///
/// `extern "system"` 边界无法让 panic 展开：一旦 panic 逃逸出 FFI 边界，Rust 会直接 abort
/// 整个进程（用户看到的是应用闪退）。因此持锁期间任何内部 panic（librqbit 在 `stop()` /
/// `get_stats()` 内失败等）都不允许把后续调用一起拖崩——毒锁一律取回内部数据继续工作，
/// 严禁在 JNI 边界写 `lock().unwrap()` / `new_string().unwrap()`。
fn lock_active_server() -> MutexGuard<'static, Option<StreamServer>> {
    active_server()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
}

/// 取出当前活跃会话（锁内只做 `take`）：teardown 可能干实事（drop `Session` / 关闭监听），
/// 必须在锁外执行，否则每秒一次的 stats 轮询会一起排队，也会把锁暴露在 drop panic 下。
fn take_active_server() -> Option<StreamServer> {
    lock_active_server().take()
}

/// 分配 Java 字符串：JNI 异常 / OOM 等分配失败降级为 `null`（Kotlin 侧走失败分支），
/// 而不是 `unwrap()` 把进程 abort 掉。
fn to_jstring(env: &JNIEnv, value: String) -> jstring {
    match env.new_string(value) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// 长度不足 5 的数组是 Kotlin 侧唯一可识别的"读取失败"信号
/// （`AndroidTorrentStreamEngine` 只在 `stats == null || stats.size < 5` 时累加
/// `MAX_CONSECUTIVE_STAT_FAILURES`）。无活跃会话时必须返回它，严禁用全零数组兜底：
/// 那会把"引擎失联"伪装成"健康的零进度"，`streamState` 不再发射也不收敛 Error，
/// 下游 `first {}` 与死种看门狗双双无界挂起（任务永远停在 DOWNLOADING）。
fn empty_stats_array(env: &JNIEnv) -> jlongArray {
    match env.new_long_array(0) {
        Ok(array) => array.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// 统计数组第 5 位（下标 4）的引擎状态码，与 Kotlin 侧 `STATS_STATE_*` 成对维护。
const STATS_STATE_HEALTHY: i64 = 0;
const STATS_STATE_ENGINE_ERROR: i64 = 1;

/// 把活跃会话统计折叠为 JNI 返回的五个数值 `[已下载, 总长, 速度, Peer 数, 引擎状态码]`；
/// 无活跃会话返回 `None`（契约见 [`empty_stats_array`]）。
///
/// 状态码必须上报：librqbit 的 `TorrentStatsState::Error`（磁盘写入失败 / 初始化校验失败等
/// 致命错误）在过去被折叠成 0 速度 + 0 Peer，用户只能在 45 秒后被死种看门狗告知
/// 「连不上做种节点」——真实原因被伪装成死种。错误原文经 `nativeGetLastError` 单独读取。
fn stats_to_jni(stats: Option<StreamStats>) -> Option<[i64; 5]> {
    let stats = stats?;
    Some([
        stats.downloaded_bytes as i64,
        stats.total_bytes as i64,
        stats.speed_bytes_per_sec as i64,
        stats.connected_peers as i64,
        if stats.engine_error {
            STATS_STATE_ENGINE_ERROR
        } else {
            STATS_STATE_HEALTHY
        },
    ])
}

fn init_logger() {
    #[cfg(target_os = "android")]
    {
        use android_logger::Config;
        use log::LevelFilter;
        let _ = android_logger::init_once(
            Config::default()
                .with_max_level(LevelFilter::Debug)
                .with_tag("Bgm/TorrentNative"),
        );
    }
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_core_data_download_AndroidTorrentStreamEngine_nativeIsAvailable(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    1
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_core_data_download_AndroidTorrentStreamEngine_nativeStartStream(
    mut env: JNIEnv,
    _class: JClass,
    magnet_url: JString,
    torrent_path: JString,
    save_dir: JString,
    episode_hint: JString,
) -> jstring {
    init_logger();

    let magnet_str: String = match env.get_string(&magnet_url) {
        Ok(s) => s.into(),
        Err(e) => return to_jstring(&env, format!("ERROR: 解析 magnet_url 失败: {e}")),
    };

    let torrent_path_str: String = match env.get_string(&torrent_path) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };

    let save_dir_str: String = match env.get_string(&save_dir) {
        Ok(s) => s.into(),
        Err(e) => return to_jstring(&env, format!("ERROR: 解析 save_dir 失败: {e}")),
    };

    let episode_hint_str: String = match env.get_string(&episode_hint) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };

    let rt = match runtime() {
        Ok(r) => r,
        Err(e) => return to_jstring(&env, format!("ERROR: {e}")),
    };

    let config = StreamConfig {
        magnet_url: magnet_str,
        torrent_path: if torrent_path_str.is_empty() {
            None
        } else {
            Some(PathBuf::from(torrent_path_str))
        },
        save_dir: PathBuf::from(save_dir_str),
        episode_hint: if episode_hint_str.trim().is_empty() {
            None
        } else {
            Some(episode_hint_str.trim().to_string())
        },
    };

    // 停止已存在的活跃服务（旧会话必须先同步退出，再启动新会话复用同一落盘目录）
    if let Some(old) = take_active_server() {
        old.stop();
    }

    // 启动新的 HTTP 流媒体服务器
    let start_result = rt.block_on(async { StreamServer::start(config).await });

    match start_result {
        Ok(server) => {
            let stream_url = server.stream_url.clone();
            log::info!("原生 BT 内存流化服务启动成功: {}", stream_url);

            *lock_active_server() = Some(server);

            to_jstring(&env, stream_url)
        }
        Err(e) => {
            log::error!("原生 BT 内存流化服务启动失败: {:?}", e);
            // 必须用 {e:#} 展开 anyhow 的 context 链：`{}` 只打印最外层 context，
            // 会把"none of the filenames match..."这类真实原因吞掉，用户只看到笼统失败。
            to_jstring(&env, format!("ERROR: 启动流服务失败: {e:#}"))
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_core_data_download_AndroidTorrentStreamEngine_nativeStopStream(
    _env: JNIEnv,
    _class: JClass,
) {
    if let Some(server) = take_active_server() {
        log::info!("停止原生 BT 流化服务");
        server.stop();
    }
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_core_data_download_AndroidTorrentStreamEngine_nativeGetStreamStats(
    env: JNIEnv,
    _class: JClass,
) -> jlongArray {
    let stats = {
        let server_guard = lock_active_server();
        server_guard.as_ref().map(|s| s.get_stats())
    };

    let values = match stats_to_jni(stats) {
        Some(values) => values,
        None => return empty_stats_array(&env),
    };

    let array = match env.new_long_array(5) {
        Ok(arr) => arr,
        Err(_) => return std::ptr::null_mut(),
    };

    if env.set_long_array_region(&array, 0, &values).is_err() {
        // 写入失败同样必须降级为"读取失败"，严禁把全零数组当成功结果交给 Kotlin。
        return empty_stats_array(&env);
    }

    array.into_raw()
}

/// 读取引擎级错误原文（`TorrentStats.error`）：仅在 Kotlin 侧检测到统计数组第 5 位为
/// 错误码时调用，把"磁盘写满 / 初始化校验失败"这类真实原因透出到 UI，
/// 而不是让用户看到一个伪装的死种超时。无活跃会话或健康态时返回空串。
#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_core_data_download_AndroidTorrentStreamEngine_nativeGetLastError(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let message = {
        let server_guard = lock_active_server();
        server_guard
            .as_ref()
            .and_then(|s| s.get_stats().error_message)
    };

    to_jstring(&env, message.unwrap_or_default())
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_core_data_download_AndroidTorrentStreamEngine_nativeGetTargetFilePath(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let path = {
        let server_guard = lock_active_server();
        server_guard
            .as_ref()
            .map(|s| s.target_file_path.clone())
            .unwrap_or_default()
    };

    to_jstring(&env, path)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn healthy_stats() -> StreamStats {
        StreamStats {
            downloaded_bytes: 1,
            total_bytes: 2,
            speed_bytes_per_sec: 3,
            connected_peers: 4,
            engine_error: false,
            error_message: None,
        }
    }

    /// 无活跃会话必须落为 `None`（JNI 侧即长度不足 5 的数组），严禁全零数组兜底：
    /// 全零会被 Kotlin 侧当成"健康的零进度"而永不累加 `MAX_CONSECUTIVE_STAT_FAILURES`，
    /// 引擎失联时 `streamState` 既不发射也不收敛 Error，下游 `first {}` 与死种看门狗双双无界挂起。
    #[test]
    fn stats_to_jni_reports_failure_without_active_session() {
        assert!(stats_to_jni(None).is_none());
    }

    /// [已下载, 总长, 速度, Peer 数, 状态码] 的顺序是 Kotlin 侧 `stats[0..4]` 的下标契约，不得改动。
    #[test]
    fn stats_to_jni_maps_active_session_fields_in_order() {
        assert_eq!(stats_to_jni(Some(healthy_stats())), Some([1, 2, 3, 4, 0]));
    }

    /// 引擎级错误态必须上报为非零状态码：否则磁盘写满这类致命错误只表现为"0 速度 0 Peer"，
    /// 用户只能等到死种看门狗超时，被告知一个与真实原因无关的"连不上做种节点"。
    #[test]
    fn stats_to_jni_surfaces_engine_error_state() {
        let stats = StreamStats {
            engine_error: true,
            error_message: Some("fatal error: 磁盘空间不足".to_string()),
            ..healthy_stats()
        };

        let mapped = stats_to_jni(Some(stats)).expect("活跃会话必须返回统计数组");
        assert_eq!(mapped[4], STATS_STATE_ENGINE_ERROR);
        assert_ne!(mapped[4], STATS_STATE_HEALTHY);
    }
}
