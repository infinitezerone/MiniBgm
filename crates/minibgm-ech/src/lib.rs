mod client;

use client::{EchHttpClient, HttpResponse, StreamEvent};
use jni::objects::{JByteArray, JClass, JObjectArray, JString};
use jni::sys::{jboolean, jint, jlong, jobject};
use jni::JNIEnv;
use std::collections::HashMap;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use tokio::runtime::{Builder, Runtime};
use tokio::task::JoinHandle;

static CLIENT: OnceLock<Result<EchHttpClient, String>> = OnceLock::new();
static RUNTIME: OnceLock<Result<Runtime, String>> = OnceLock::new();
static NEXT_REQUEST_ID: AtomicI64 = AtomicI64::new(1);
static REQUESTS: OnceLock<Mutex<HashMap<jlong, Arc<RequestState>>>> = OnceLock::new();
const MAX_ACTIVE_REQUESTS: usize = 64;

struct RequestState {
    events: Mutex<tokio::sync::mpsc::Receiver<StreamEvent>>,
    task: Mutex<Option<JoinHandle<()>>>,
}

impl RequestState {
    fn new(events: tokio::sync::mpsc::Receiver<StreamEvent>) -> Self {
        Self {
            events: Mutex::new(events),
            task: Mutex::new(None),
        }
    }
}

fn requests() -> &'static Mutex<HashMap<jlong, Arc<RequestState>>> {
    REQUESTS.get_or_init(|| Mutex::new(HashMap::new()))
}

fn runtime() -> Result<&'static Runtime, String> {
    match RUNTIME.get_or_init(|| {
        Builder::new_multi_thread()
            .enable_io()
            .enable_time()
            .worker_threads(2)
            .build()
            .map_err(|error| error.to_string())
    }) {
        Ok(runtime) => Ok(runtime),
        Err(error) => Err(error.clone()),
    }
}

fn get_client() -> Result<&'static EchHttpClient, String> {
    match CLIENT.get_or_init(|| EchHttpClient::new().map_err(|error| error.to_string())) {
        Ok(client) => Ok(client),
        Err(error) => Err(error.clone()),
    }
}

#[cfg(target_os = "android")]
fn init_logging() {
    static LOG_INIT: std::sync::Once = std::sync::Once::new();
    LOG_INIT.call_once(|| {
        android_logger::init_once(
            android_logger::Config::default()
                .with_tag("Bgm/EchNative")
                .with_max_level(log::LevelFilter::Debug),
        );
    });
}

#[cfg(not(target_os = "android"))]
fn init_logging() {}

fn read_string_array(
    env: &mut JNIEnv,
    array: &JObjectArray,
) -> Result<Vec<String>, Box<dyn std::error::Error>> {
    let len = env.get_array_length(array)?;
    let mut values = Vec::with_capacity(len as usize);
    for index in 0..len {
        let value: JString = env.get_object_array_element(array, index)?.into();
        values.push(env.get_string(&value)?.into());
    }
    Ok(values)
}

fn make_response(
    env: &mut JNIEnv,
    response: Result<HttpResponse, String>,
) -> Result<jobject, Box<dyn std::error::Error>> {
    let (status, headers, body, ech_accepted, error, connected_addr, updated_config) =
        match response {
            Ok(response) => (
                response.status_code,
                response.headers,
                response.body,
                response.ech_accepted,
                None,
                response.connected_addr,
                response.updated_ech_config,
            ),
            Err(error) => (0, Vec::new(), Vec::new(), false, Some(error), None, None),
        };

    let response_class =
        env.find_class("com/infinitezerone/minibgm/core/network/ech/EchNativeResponse")?;
    let string_class = env.find_class("java/lang/String")?;
    let keys = env.new_object_array(headers.len() as i32, &string_class, JString::default())?;
    let values = env.new_object_array(headers.len() as i32, &string_class, JString::default())?;
    for (index, (key, value)) in headers.iter().enumerate() {
        let key = env.new_string(key)?;
        let value = env.new_string(value)?;
        env.set_object_array_element(&keys, index as i32, key)?;
        env.set_object_array_element(&values, index as i32, value)?;
    }

    let body = env.byte_array_from_slice(&body)?;
    let error = match error {
        Some(error) => env.new_string(error)?,
        None => JString::default(),
    };
    let connected_addr = match connected_addr {
        Some(addr) => env.new_string(addr)?,
        None => JString::default(),
    };
    let updated_config = match updated_config {
        Some(config) => env.new_string(config)?,
        None => JString::default(),
    };

    let signature = "(I[Ljava/lang/String;[Ljava/lang/String;[BZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V";
    let object = env.new_object(
        response_class,
        signature,
        &[
            (status as jint).into(),
            (&keys).into(),
            (&values).into(),
            (&body).into(),
            (ech_accepted as jboolean).into(),
            (&error).into(),
            (&connected_addr).into(),
            (&updated_config).into(),
        ],
    )?;
    Ok(object.into_raw())
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_core_network_ech_EchNativeClient_nativeStart(
    mut env: JNIEnv,
    _class: JClass,
    j_url: JString,
    j_method: JString,
    j_header_keys: JObjectArray,
    j_header_values: JObjectArray,
    j_body: JByteArray,
    j_timeout_ms: jlong,
    j_target_addrs: JObjectArray,
    j_enable_ech: jboolean,
    j_require_ech: jboolean,
    j_ech_config: JString,
) -> jlong {
    init_logging();

    let parsed = (|| -> Result<_, Box<dyn std::error::Error>> {
        let url: String = env.get_string(&j_url)?.into();
        let method: String = env.get_string(&j_method)?.into();
        let header_keys = read_string_array(&mut env, &j_header_keys)?;
        let header_values = read_string_array(&mut env, &j_header_values)?;
        if header_keys.len() != header_values.len() {
            return Err("header key/value array lengths differ".into());
        }
        let headers = header_keys
            .into_iter()
            .zip(header_values)
            .collect::<Vec<_>>();
        let body = if j_body.is_null() {
            None
        } else {
            Some(env.convert_byte_array(&j_body)?)
        };
        let target_addrs = if j_target_addrs.is_null() {
            None
        } else {
            Some(read_string_array(&mut env, &j_target_addrs)?)
        };
        let ech_config = if j_ech_config.is_null() {
            None
        } else {
            let config: String = env.get_string(&j_ech_config)?.into();
            let config = config.trim().to_owned();
            (!config.is_empty()).then_some(config)
        };
        Ok((url, method, headers, body, target_addrs, ech_config))
    })();

    let (url, method, headers, body, target_addrs, ech_config) = match parsed {
        Ok(args) => args,
        Err(error) => {
            log::warn!("nativeStart argument error: {error}");
            return 0;
        }
    };
    let client = match get_client() {
        Ok(client) => client,
        Err(error) => {
            log::error!("nativeStart initialization error: {error}");
            return 0;
        }
    };
    let runtime = match runtime() {
        Ok(runtime) => runtime,
        Err(error) => {
            log::error!("nativeStart runtime error: {error}");
            return 0;
        }
    };

    let id = NEXT_REQUEST_ID.fetch_add(1, Ordering::Relaxed) as jlong;
    let (event_tx, event_rx) = tokio::sync::mpsc::channel(4);
    let state = Arc::new(RequestState::new(event_rx));
    {
        let mut active = requests()
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        if active.len() >= MAX_ACTIVE_REQUESTS {
            log::warn!("nativeStart rejected: active request limit reached");
            return 0;
        }
        active.insert(id, state.clone());
    }
    let task = runtime.spawn(async move {
        let result = client
            .fetch(
                &url,
                &method,
                &headers,
                body.as_deref(),
                j_timeout_ms.max(0) as u64,
                target_addrs.as_deref(),
                j_enable_ech != 0,
                j_require_ech != 0,
                ech_config.as_deref(),
                Some(event_tx.clone()),
            )
            .await
            .map_err(|error| error.to_string());
        if let Err(error) = result {
            let _ = event_tx.send(StreamEvent::Error(error)).await;
        }
    });
    *state
        .task
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner()) = Some(task);
    id
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_core_network_ech_EchNativeClient_nativeAwait(
    mut env: JNIEnv,
    _class: JClass,
    request_id: jlong,
) -> jobject {
    let state = requests()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
        .get(&request_id)
        .cloned();
    let Some(state) = state else {
        return std::ptr::null_mut();
    };
    let event = state
        .events
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
        .blocking_recv();
    let response = match event {
        Some(StreamEvent::Response(response)) => Ok(response),
        Some(StreamEvent::Error(error)) => {
            requests()
                .lock()
                .unwrap_or_else(|p| p.into_inner())
                .remove(&request_id);
            Err(error)
        }
        Some(StreamEvent::End) | Some(StreamEvent::Data(_)) | None => {
            requests()
                .lock()
                .unwrap_or_else(|p| p.into_inner())
                .remove(&request_id);
            Err("native response stream ended before response headers".to_owned())
        }
    };
    match make_response(&mut env, response) {
        Ok(object) => object,
        Err(error) => {
            log::error!("nativeAwait response conversion failed: {error}");
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_core_network_ech_EchNativeClient_nativeReadBodyChunk(
    mut env: JNIEnv,
    _class: JClass,
    request_id: jlong,
) -> jobject {
    let state = requests()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
        .get(&request_id)
        .cloned();
    let Some(state) = state else {
        return std::ptr::null_mut();
    };
    let event = state
        .events
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
        .blocking_recv();
    match event {
        Some(StreamEvent::Data(bytes)) => env
            .byte_array_from_slice(&bytes)
            .map_or(std::ptr::null_mut(), |array| array.into_raw()),
        Some(StreamEvent::End) => {
            requests()
                .lock()
                .unwrap_or_else(|p| p.into_inner())
                .remove(&request_id);
            env.byte_array_from_slice(&[])
                .map_or(std::ptr::null_mut(), |array| array.into_raw())
        }
        Some(StreamEvent::Error(error)) => {
            requests()
                .lock()
                .unwrap_or_else(|p| p.into_inner())
                .remove(&request_id);
            let _ = env.throw_new("java/io/IOException", error);
            std::ptr::null_mut()
        }
        Some(StreamEvent::Response(_)) | None => {
            requests()
                .lock()
                .unwrap_or_else(|p| p.into_inner())
                .remove(&request_id);
            let _ = env.throw_new("java/io/IOException", "response stream ended unexpectedly");
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_core_network_ech_EchNativeClient_nativeCancel(
    _env: JNIEnv,
    _class: JClass,
    request_id: jlong,
) {
    let state = requests()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
        .remove(&request_id);
    if let Some(state) = state {
        if let Some(task) = state
            .task
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .take()
        {
            task.abort();
        }
    }
}
