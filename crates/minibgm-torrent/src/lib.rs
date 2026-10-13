mod server;

use std::path::PathBuf;
use std::sync::{Mutex, OnceLock};

use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jlongArray, jstring};
use jni::JNIEnv;
use server::{StreamConfig, StreamServer};
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
) -> jstring {
    init_logger();

    let magnet_str: String = match env.get_string(&magnet_url) {
        Ok(s) => s.into(),
        Err(e) => {
            return env
                .new_string(format!("ERROR: 解析 magnet_url 失败: {e}"))
                .unwrap()
                .into_raw()
        }
    };

    let torrent_path_str: String = match env.get_string(&torrent_path) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };

    let save_dir_str: String = match env.get_string(&save_dir) {
        Ok(s) => s.into(),
        Err(e) => {
            return env
                .new_string(format!("ERROR: 解析 save_dir 失败: {e}"))
                .unwrap()
                .into_raw()
        }
    };

    let rt = match runtime() {
        Ok(r) => r,
        Err(e) => return env.new_string(format!("ERROR: {e}")).unwrap().into_raw(),
    };

    let config = StreamConfig {
        magnet_url: magnet_str,
        torrent_path: if torrent_path_str.is_empty() {
            None
        } else {
            Some(PathBuf::from(torrent_path_str))
        },
        save_dir: PathBuf::from(save_dir_str),
    };

    // 停止已存在的活跃服务
    {
        let mut server_guard = active_server().lock().unwrap();
        if let Some(old) = server_guard.take() {
            old.stop();
        }
    }

    // 启动新的 HTTP 流媒体服务器
    let start_result = rt.block_on(async { StreamServer::start(config).await });

    match start_result {
        Ok(server) => {
            let stream_url = server.stream_url.clone();
            log::info!("原生 BT 内存流化服务启动成功: {}", stream_url);

            let mut server_guard = active_server().lock().unwrap();
            *server_guard = Some(server);

            env.new_string(stream_url).unwrap().into_raw()
        }
        Err(e) => {
            log::error!("原生 BT 内存流化服务启动失败: {:?}", e);
            env.new_string(format!("ERROR: 启动流服务失败: {e}"))
                .unwrap()
                .into_raw()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_core_data_download_AndroidTorrentStreamEngine_nativeStopStream(
    _env: JNIEnv,
    _class: JClass,
) {
    let mut server_guard = active_server().lock().unwrap();
    if let Some(server) = server_guard.take() {
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
        let server_guard = active_server().lock().unwrap();
        server_guard.as_ref().map(|s| s.get_stats())
    };

    let (downloaded, total, speed, peers) = match stats {
        Some(s) => (
            s.downloaded_bytes,
            s.total_bytes,
            s.speed_bytes_per_sec,
            s.connected_peers,
        ),
        None => (0, 0, 0, 0),
    };

    let array = match env.new_long_array(4) {
        Ok(arr) => arr,
        Err(_) => return std::ptr::null_mut(),
    };

    let values = [downloaded as i64, total as i64, speed as i64, peers as i64];
    let _ = env.set_long_array_region(&array, 0, &values);

    array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_core_data_download_AndroidTorrentStreamEngine_nativeGetTargetFilePath(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let path = {
        let server_guard = active_server().lock().unwrap();
        server_guard
            .as_ref()
            .map(|s| s.target_file_path.clone())
            .unwrap_or_default()
    };

    match env.new_string(path) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
