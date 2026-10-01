use base64::Engine;
use bytes::Bytes;
use http_body_util::{BodyExt, Full};
use hyper::body::Incoming;
use hyper::client::conn::{http1, http2};
use hyper::{Method, Request, Uri};
use hyper_util::rt::{TokioExecutor, TokioIo};
use rustls::client::{EchConfig, EchMode, EchStatus};
use rustls::internal::msgs::codec::Codec;
use rustls::internal::msgs::handshake::EchConfigPayload;
use rustls::pki_types::{EchConfigListBytes, ServerName};
use rustls::{ClientConfig, RootCertStore};
use std::collections::HashMap;
use std::net::SocketAddr;
use std::sync::{Arc, RwLock};
use std::time::Duration;
use std::time::Instant;
use tokio::time::timeout;
use tokio_rustls::TlsConnector;
use url::Url;

const MAX_RESPONSE_BODY_SIZE: usize = 16 * 1024 * 1024;
const MAX_REQUEST_BODY_SIZE: usize = 16 * 1024 * 1024;
const MAX_REQUEST_HEADERS: usize = 128;
const MAX_HEADER_VALUE_SIZE: usize = 16 * 1024;
const CONNECT_TIMEOUT: Duration = Duration::from_millis(2000);
const ECH_CONFIG_TTL: Duration = Duration::from_secs(60 * 60);
const H2_CONNECTION_MAX_AGE: Duration = Duration::from_secs(120);

#[derive(Clone, Debug, PartialEq, Eq, Hash)]
struct PoolKey {
    host: String,
    port: u16,
    is_ech: bool,
}

#[derive(Clone)]
struct Http2ConnectionEntry {
    sender: http2::SendRequest<Full<Bytes>>,
    ech_accepted: bool,
    connected_addr: String,
    created_at: Instant,
}
/// 单个流式事件等待 Kotlin 读取循环取走的时限。
///
/// 读取循环可能因为协程取消/异常而永远不来取数据，而 Receiver 由请求状态持有（不会随协程
/// 取消而消失）：没有这个上限，任务会永久阻塞在 send 上，占住连接与请求槽（64 槽用尽即全局失效）。
const CONSUMER_STALL_TIMEOUT: Duration = Duration::from_secs(30);

struct EchConfigEntry {
    config: Arc<ClientConfig>,
    #[allow(dead_code)]
    source_b64: String,
    updated_at: Instant,
}

/// 单次尝试（一个候选地址 + 一套 TLS 配置）的失败结果。
///
/// `request_possibly_sent` 记录"请求是否已经交给 hyper 发出"。一旦为真，上层就不允许
/// 换协议配置、换候选地址重发：调用方存在非幂等写（收藏 POST/PATCH、Worker 换票 POST），
/// 重复发送等于重复落库。内层用它守住"换地址"，外层用它守住"ECH 降级到明文 TLS"。
#[derive(Debug)]
struct AttemptError {
    source: Box<dyn std::error::Error + Send + Sync>,
    request_possibly_sent: bool,
}

impl AttemptError {
    /// 连接/握手/构包阶段失败：请求从未发出，允许上层换配置重试。
    fn before_request(source: impl Into<Box<dyn std::error::Error + Send + Sync>>) -> Self {
        Self {
            source: source.into(),
            request_possibly_sent: false,
        }
    }

    /// 请求已经交给 hyper 之后的失败：禁止任何自动重发。
    fn after_request(source: impl Into<Box<dyn std::error::Error + Send + Sync>>) -> Self {
        Self {
            source: source.into(),
            request_possibly_sent: true,
        }
    }
}

impl std::fmt::Display for AttemptError {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(formatter, "{}", self.source)
    }
}

impl std::error::Error for AttemptError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        Some(&*self.source)
    }
}

/// 提取服务端返回的 ECH retry_configs
fn extract_retry_configs(
    err: &(dyn std::error::Error + 'static),
) -> Option<Option<Vec<EchConfigPayload>>> {
    if let Some(rustls::Error::PeerIncompatible(
        rustls::PeerIncompatible::ServerRejectedEncryptedClientHello(retry_configs),
    )) = err.downcast_ref::<rustls::Error>()
    {
        return Some(retry_configs.clone());
    }
    if let Some(rustls::Error::PeerIncompatible(
        rustls::PeerIncompatible::ServerRejectedEncryptedClientHello(retry_configs),
    )) = err.downcast_ref::<std::io::Error>().and_then(|io_err| {
        io_err
            .get_ref()
            .and_then(|inner| inner.downcast_ref::<rustls::Error>())
    }) {
        return Some(retry_configs.clone());
    }
    if let Some(src) = err.source() {
        return extract_retry_configs(src);
    }
    None
}

#[cfg(test)]
fn runtime() -> Result<&'static tokio::runtime::Runtime, Box<dyn std::error::Error + Send + Sync>> {
    use tokio::runtime::Builder;
    static RUNTIME: std::sync::OnceLock<Result<tokio::runtime::Runtime, String>> =
        std::sync::OnceLock::new();
    match RUNTIME.get_or_init(|| {
        Builder::new_multi_thread()
            .enable_io()
            .enable_time()
            .worker_threads(2)
            .build()
            .map_err(|e| e.to_string())
    }) {
        Ok(runtime) => Ok(runtime),
        Err(error) => Err(error.clone().into()),
    }
}

#[derive(Debug)]
pub struct HttpResponse {
    pub status_code: u16,
    pub headers: Vec<(String, String)>,
    pub body: Vec<u8>,
    pub ech_accepted: bool,
    pub connected_addr: Option<String>,
    pub updated_ech_config: Option<String>,
}

pub enum StreamEvent {
    Response(HttpResponse),
    Data(Vec<u8>),
    End,
    Error(String),
}

pub struct EchHttpClient {
    root_store: RootCertStore,
    tls_configs_with_ech: RwLock<HashMap<String, EchConfigEntry>>,
    tls_config_standard: Arc<ClientConfig>,
    h2_pool: RwLock<HashMap<PoolKey, Http2ConnectionEntry>>,
}

/// 根据 ECH Config List 字节流构建 ClientConfig
fn build_ech_client_config(
    ech_config_list: EchConfigListBytes,
    root_store: &RootCertStore,
) -> Result<ClientConfig, Box<dyn std::error::Error + Send + Sync>> {
    let ech_config = EchConfig::new(
        ech_config_list,
        rustls::crypto::aws_lc_rs::hpke::ALL_SUPPORTED_SUITES,
    )
    .map_err(|e| format!("Init EchConfig failed: {:?}", e))?;

    let ech_mode = EchMode::from(ech_config);

    let mut config_ech = ClientConfig::builder_with_provider(Arc::new(
        rustls::crypto::aws_lc_rs::default_provider(),
    ))
    .with_ech(ech_mode)
    .map_err(|e| format!("Configure with_ech failed: {:?}", e))?
    .with_root_certificates(root_store.clone())
    .with_no_client_auth();

    config_ech.alpn_protocols = vec![b"h2".to_vec(), b"http/1.1".to_vec()];
    config_ech.resumption = rustls::client::Resumption::in_memory_sessions(64);

    Ok(config_ech)
}

impl EchHttpClient {
    /// 构造 ECH HTTP 客户端
    pub fn new() -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        let root_store = RootCertStore {
            roots: webpki_roots::TLS_SERVER_ROOTS.into(),
        };

        let mut config_std = ClientConfig::builder_with_provider(Arc::new(
            rustls::crypto::aws_lc_rs::default_provider(),
        ))
        .with_safe_default_protocol_versions()
        .map_err(|e| format!("Configure safe protocol versions failed: {:?}", e))?
        .with_root_certificates(root_store.clone())
        .with_no_client_auth();

        config_std.alpn_protocols = vec![b"h2".to_vec(), b"http/1.1".to_vec()];
        config_std.resumption = rustls::client::Resumption::in_memory_sessions(64);

        Ok(Self {
            root_store,
            tls_configs_with_ech: RwLock::new(HashMap::new()),
            tls_config_standard: Arc::new(config_std),
            h2_pool: RwLock::new(HashMap::new()),
        })
    }

    /// 更新 ECH Base64 配置
    pub fn set_ech_config_b64(&self, host: &str, b64: &str) -> bool {
        let ech_bytes = match base64::engine::general_purpose::STANDARD.decode(b64.trim()) {
            Ok(b) => b,
            Err(e) => {
                log::warn!("Invalid Base64 ECH config: {}", e);
                return false;
            }
        };
        let ech_config_list = EchConfigListBytes::from(ech_bytes);
        match build_ech_client_config(ech_config_list, &self.root_store) {
            Ok(new_config) => {
                let mut lock = self
                    .tls_configs_with_ech
                    .write()
                    .unwrap_or_else(|p| p.into_inner());
                lock.insert(
                    host.to_owned(),
                    EchConfigEntry {
                        config: Arc::new(new_config),
                        source_b64: b64.trim().to_owned(),
                        updated_at: Instant::now(),
                    },
                );
                let mut pool = self.h2_pool.write().unwrap_or_else(|p| p.into_inner());
                pool.retain(|k, _| k.host != host);
                true
            }
            Err(e) => {
                log::warn!("Failed to build ClientConfig from ECH config: {}", e);
                false
            }
        }
    }

    /// 使用服务端下发的 retry_configs 更新 ECH 配置并返回 Base64 字符串
    pub fn update_ech_from_retry_configs(
        &self,
        host: &str,
        retry_configs: &[EchConfigPayload],
    ) -> Option<String> {
        let mut bytes = Vec::new();
        retry_configs.to_vec().encode(&mut bytes);
        let b64 = base64::engine::general_purpose::STANDARD.encode(&bytes);
        let ech_config_list = EchConfigListBytes::from(bytes);

        match build_ech_client_config(ech_config_list, &self.root_store) {
            Ok(new_config) => {
                let mut lock = self
                    .tls_configs_with_ech
                    .write()
                    .unwrap_or_else(|poisoned| poisoned.into_inner());
                lock.insert(
                    host.to_owned(),
                    EchConfigEntry {
                        config: Arc::new(new_config),
                        source_b64: b64.clone(),
                        updated_at: Instant::now(),
                    },
                );
                let mut pool = self.h2_pool.write().unwrap_or_else(|p| p.into_inner());
                pool.retain(|k, _| k.host != host);
                log::info!(
                    "ECH configuration dynamically updated for {host} from server retry_configs"
                );
                Some(b64)
            }
            Err(e) => {
                log::warn!("Failed to update ECH configuration from retry_configs: {e}");
                None
            }
        }
    }

    /// 发起 HTTP/1.1 请求
    ///
    /// 参数清单与 JNI 层 nativeStart 的签名一一对应（见 lib.rs）；拆成结构体只是把同一份
    /// 契约换个位置，故显式放行 clippy 的参数个数检查。
    #[allow(clippy::too_many_arguments)]
    pub async fn fetch(
        &self,
        url_str: &str,
        method: &str,
        headers: &[(String, String)],
        body: Option<&[u8]>,
        timeout_ms: u64,
        target_addrs: Option<&[String]>,
        enable_ech: bool,
        require_ech: bool,
        dynamic_ech_config: Option<&str>,
        stream: Option<tokio::sync::mpsc::Sender<StreamEvent>>,
    ) -> Result<HttpResponse, Box<dyn std::error::Error + Send + Sync>> {
        let parsed_url = Url::parse(url_str)?;
        if parsed_url.scheme() != "https" {
            return Err("ECH transport only supports https URLs".into());
        }
        if parsed_url.fragment().is_some() {
            return Err("URL fragments must not be sent in HTTP requests".into());
        }
        let host = parsed_url
            .host_str()
            .ok_or_else(|| "URL has no host".to_string())?;
        if headers.len() > MAX_REQUEST_HEADERS {
            return Err(format!("too many request headers: {}", headers.len()).into());
        }
        for (name, value) in headers {
            if name.is_empty()
                || name.bytes().any(|byte| byte == b'\r' || byte == b'\n')
                || value.bytes().any(|byte| byte == b'\r' || byte == b'\n')
                || value.len() > MAX_HEADER_VALUE_SIZE
            {
                return Err("invalid or oversized request header".into());
            }
        }
        if body.is_some_and(|bytes| bytes.len() > MAX_REQUEST_BODY_SIZE) {
            return Err(format!("request body exceeds {} bytes", MAX_REQUEST_BODY_SIZE).into());
        }
        let port = parsed_url.port_or_known_default().unwrap_or(443);

        let mut addrs_to_try: Vec<String> = Vec::new();
        if let Some(targets) = target_addrs {
            for target in targets {
                let trimmed = target.trim();
                if !trimmed.is_empty() {
                    addrs_to_try.push(trimmed.to_string());
                }
            }
        }
        if addrs_to_try.is_empty() {
            addrs_to_try.push(format!("{}:{}", host, port));
        }

        let authority_host = if host.contains(':') && !host.starts_with('[') {
            format!("[{host}]")
        } else {
            host.to_owned()
        };
        let authority = match parsed_url.port() {
            Some(port) => format!("{authority_host}:{port}"),
            None => authority_host,
        };
        let request_target = match parsed_url.query() {
            Some(query) => format!("{}?{}", parsed_url.path(), query),
            None => parsed_url.path().to_owned(),
        };
        let uri: Uri = if request_target.is_empty() {
            "/".parse()
                .map_err(|error: hyper::http::uri::InvalidUri| format!("invalid URI: {error}"))?
        } else {
            request_target
                .parse()
                .map_err(|error: hyper::http::uri::InvalidUri| format!("invalid URI: {error}"))?
        };

        // 优先尝试从 HTTP/2 连接池中复用持久长连接
        let pool_key = PoolKey {
            host: host.to_owned(),
            port,
            is_ech: enable_ech,
        };
        let pooled_connection = {
            let pool = self.h2_pool.read().unwrap_or_else(|p| p.into_inner());
            pool.get(&pool_key).cloned()
        };

        if let Some(mut entry) = pooled_connection {
            let fresh = entry.created_at.elapsed() <= H2_CONNECTION_MAX_AGE;
            let ech_ok = !enable_ech || entry.ech_accepted;
            if fresh && ech_ok && !entry.sender.is_closed() {
                // 发送前探测连接就绪态：若已被对端关闭（如收到 GOAWAY/RST），ready() 立即返回错误
                match entry.sender.ready().await {
                    Ok(_) => {
                        let mut request = Request::builder().method(method).uri(uri.clone());
                        for (name, value) in headers {
                            if name.eq_ignore_ascii_case("host")
                                || name.eq_ignore_ascii_case("connection")
                            {
                                continue;
                            }
                            request = request.header(name, value);
                        }
                        request = request.header("host", &authority);
                        let request_body = match request
                            .body(Full::new(Bytes::copy_from_slice(body.unwrap_or_default())))
                        {
                            Ok(b) => b,
                            Err(e) => return Err(AttemptError::before_request(e).into()),
                        };

                        let request_timeout = Duration::from_millis(timeout_ms.max(3000));
                        // 请求已交付网络层：此后的任何超时/断连严禁向后穿透重发，彻底阻断非幂等写重复提交
                        match timeout(request_timeout, entry.sender.send_request(request_body))
                            .await
                        {
                            Ok(Ok(hyper_response)) => {
                                let status_code = hyper_response.status().as_u16();
                                let response_headers = hyper_response
                                    .headers()
                                    .iter()
                                    .map(|(name, value)| {
                                        (name.to_string(), value.to_str().unwrap_or("").to_string())
                                    })
                                    .collect();
                                let resp = process_hyper_response(
                                    status_code,
                                    response_headers,
                                    hyper_response,
                                    entry.ech_accepted,
                                    Some(entry.connected_addr.clone()),
                                    None,
                                    stream.as_ref(),
                                )
                                .await
                                .map_err(AttemptError::after_request)?;
                                log::debug!("Reused active HTTP/2 connection for {host}:{port} (ech={enable_ech})");
                                return Ok(resp);
                            }
                            Ok(Err(error)) => {
                                let mut pool =
                                    self.h2_pool.write().unwrap_or_else(|p| p.into_inner());
                                pool.remove(&pool_key);
                                return Err(AttemptError::after_request(error).into());
                            }
                            Err(elapsed) => {
                                let mut pool =
                                    self.h2_pool.write().unwrap_or_else(|p| p.into_inner());
                                pool.remove(&pool_key);
                                return Err(AttemptError::after_request(elapsed).into());
                            }
                        }
                    }
                    Err(_) => {
                        // 发送前检测到连接已失效：请求完全未发出，安全移除失效连接并放行新建连接流程
                        let mut pool = self.h2_pool.write().unwrap_or_else(|p| p.into_inner());
                        pool.remove(&pool_key);
                    }
                }
            } else {
                let mut pool = self.h2_pool.write().unwrap_or_else(|p| p.into_inner());
                pool.remove(&pool_key);
            }
        }

        if !enable_ech {
            return try_fetch_with_config(
                self.tls_config_standard.clone(),
                &parsed_url,
                method,
                headers,
                body,
                timeout_ms,
                &addrs_to_try,
                stream.clone(),
                None,
                Some(&self.h2_pool),
            )
            .await
            .map_err(Into::into);
        }

        if let Some(cfg_b64) = dynamic_ech_config {
            let needs_update = {
                let lock = self
                    .tls_configs_with_ech
                    .read()
                    .unwrap_or_else(|p| p.into_inner());
                lock.get(host)
                    .map(|entry| entry.updated_at.elapsed() > ECH_CONFIG_TTL)
                    .unwrap_or(true)
            };
            if needs_update {
                self.set_ech_config_b64(host, cfg_b64);
            }
        }

        let current_ech_config = {
            let guard = self
                .tls_configs_with_ech
                .read()
                .unwrap_or_else(|p| p.into_inner());
            guard.get(host).map(|entry| entry.config.clone())
        };

        let ech_config_to_use = match current_ech_config {
            Some(cfg) => cfg,
            None => {
                if require_ech {
                    return Err("ECH is required but no valid configuration is available".into());
                }
                log::warn!(
                    "ECH requested but no ECH config available, falling back to standard TLS"
                );
                return try_fetch_with_config(
                    self.tls_config_standard.clone(),
                    &parsed_url,
                    method,
                    headers,
                    body,
                    timeout_ms,
                    &addrs_to_try,
                    stream.clone(),
                    None,
                    Some(&self.h2_pool),
                )
                .await
                .map_err(Into::into);
            }
        };

        match try_fetch_with_config(
            ech_config_to_use,
            &parsed_url,
            method,
            headers,
            body,
            timeout_ms,
            &addrs_to_try,
            stream.clone(),
            None,
            Some(&self.h2_pool),
        )
        .await
        {
            Ok(resp) => Ok(resp),
            Err(error) => {
                // 请求可能已经到达服务端（读响应超时、连接中途断开、收 body 失败）：
                // 此时任何"换配置重发"都会让收藏写入、Worker 换票这类非幂等请求重复落库。
                if error.request_possibly_sent {
                    log::warn!(
                        "ECH attempt for {host} failed after the request was sent; refusing to resend: {error}"
                    );
                    return Err(if require_ech {
                        format!("ECH request failed and require_ech is enabled: {error}")
                    } else {
                        format!(
                            "request failed after being sent; refusing to retry over standard TLS to avoid duplicating a non-idempotent request: {error}"
                        )
                    }
                    .into());
                }

                // retry_configs 只可能出现在"ECH 被服务端拒绝"的握手里，也就是请求必然尚未发出；
                // 上面的守卫已保证这一点，这里再做一次显式确认式重试。
                if let Some(Some(retry_configs)) = extract_retry_configs(&error) {
                    log::warn!(
                        "Server rejected ECH for {host} with retry_configs; self-healing ECH config and retrying..."
                    );
                    if let Some(new_ech_b64) =
                        self.update_ech_from_retry_configs(host, &retry_configs)
                    {
                        let updated_cfg = {
                            let guard = self
                                .tls_configs_with_ech
                                .read()
                                .unwrap_or_else(|p| p.into_inner());
                            guard.get(host).map(|entry| entry.config.clone())
                        };
                        if let Some(updated_cfg) = updated_cfg {
                            return try_fetch_with_config(
                                updated_cfg,
                                &parsed_url,
                                method,
                                headers,
                                body,
                                timeout_ms,
                                &addrs_to_try,
                                stream,
                                Some(new_ech_b64),
                                Some(&self.h2_pool),
                            )
                            .await
                            .map_err(Into::into);
                        }
                    }
                }

                if require_ech {
                    return Err(
                        format!("ECH request failed and require_ech is enabled: {error}").into(),
                    );
                }
                log::warn!(
                    "ECH handshake failed for {host}; falling back to standard TLS: {error}"
                );
                try_fetch_with_config(
                    self.tls_config_standard.clone(),
                    &parsed_url,
                    method,
                    headers,
                    body,
                    timeout_ms,
                    &addrs_to_try,
                    stream,
                    None,
                    Some(&self.h2_pool),
                )
                .await
                .map_err(Into::into)
            }
        }
    }
}

/// Execute one request through hyper over an ECH-capable TLS stream.
///
/// 参数清单与 JNI 契约一致，理由同 [`EchHttpClient::fetch`]。
#[allow(clippy::too_many_arguments)]
async fn try_fetch_with_config(
    tls_config: Arc<ClientConfig>,
    parsed_url: &Url,
    method: &str,
    headers: &[(String, String)],
    body: Option<&[u8]>,
    timeout_ms: u64,
    addrs_to_try: &[String],
    stream: Option<tokio::sync::mpsc::Sender<StreamEvent>>,
    updated_ech_config: Option<String>,
    pool: Option<&RwLock<HashMap<PoolKey, Http2ConnectionEntry>>>,
) -> Result<HttpResponse, AttemptError> {
    async_try_fetch_with_config(
        tls_config,
        parsed_url,
        method,
        headers,
        body,
        timeout_ms,
        addrs_to_try,
        stream,
        updated_ech_config,
        pool,
    )
    .await
}

#[allow(clippy::too_many_arguments)]
async fn async_try_fetch_with_config(
    tls_config: Arc<ClientConfig>,
    parsed_url: &Url,
    method: &str,
    headers: &[(String, String)],
    body: Option<&[u8]>,
    timeout_ms: u64,
    addrs_to_try: &[String],
    stream: Option<tokio::sync::mpsc::Sender<StreamEvent>>,
    updated_ech_config: Option<String>,
    pool: Option<&RwLock<HashMap<PoolKey, Http2ConnectionEntry>>>,
) -> Result<HttpResponse, AttemptError> {
    let host = parsed_url
        .host_str()
        .ok_or_else(|| AttemptError::before_request("URL has no host".to_owned()))?;
    let authority_host = if host.contains(':') && !host.starts_with('[') {
        format!("[{host}]")
    } else {
        host.to_owned()
    };
    let authority = match parsed_url.port() {
        Some(port) => format!("{authority_host}:{port}"),
        None => authority_host,
    };
    let request_target = match parsed_url.query() {
        Some(query) => format!("{}?{}", parsed_url.path(), query),
        None => parsed_url.path().to_owned(),
    };
    let uri: Uri = if request_target.is_empty() {
        "/".parse()
            .map_err(|error: hyper::http::uri::InvalidUri| AttemptError::before_request(error))?
    } else {
        request_target
            .parse()
            .map_err(|error: hyper::http::uri::InvalidUri| AttemptError::before_request(error))?
    };
    let method: Method = method
        .parse()
        .map_err(|error: hyper::http::method::InvalidMethod| AttemptError::before_request(error))?;
    let request_timeout = Duration::from_millis(timeout_ms.max(3000));
    let deadline = tokio::time::Instant::now() + request_timeout;
    let server_name: ServerName<'static> = host
        .to_owned()
        .try_into()
        .map_err(AttemptError::before_request)?;
    let mut last_error: Option<Box<dyn std::error::Error + Send + Sync>> = None;

    for addr_str in addrs_to_try {
        let remaining = deadline.saturating_duration_since(tokio::time::Instant::now());
        if remaining.is_zero() {
            return Err(AttemptError::before_request(
                "request timed out while resolving candidate addresses".to_owned(),
            ));
        }
        let resolved_addrs: Vec<SocketAddr> =
            match timeout(remaining, tokio::net::lookup_host(addr_str.as_str())).await {
                Ok(Ok(iter)) => iter.collect(),
                Ok(Err(error)) => {
                    last_error = Some(Box::new(error));
                    continue;
                }
                Err(error) => return Err(AttemptError::before_request(error)),
            };

        for socket_addr in resolved_addrs {
            // 一旦请求已交给 hyper，换候选地址重发就可能重复非幂等 POST/PUT：
            // 只有连接与 TLS 握手阶段的失败才允许换下一个候选。
            let mut request_started = false;
            let remaining = deadline.saturating_duration_since(tokio::time::Instant::now());
            if remaining.is_zero() {
                return Err(AttemptError::before_request(
                    "request timed out before connecting to candidate address".to_owned(),
                ));
            }
            let result = timeout(remaining, async {
                let connector = TlsConnector::from(tls_config.clone());
                let tls = timeout(CONNECT_TIMEOUT.min(remaining), async {
                    let socket = tokio::net::TcpStream::connect(socket_addr).await?;
                    socket.set_nodelay(true)?;
                    connector.connect(server_name.clone(), socket).await
                })
                .await??;
                let ech_accepted = tls.get_ref().1.ech_status() == EchStatus::Accepted;
                let is_h2 = tls.get_ref().1.alpn_protocol() == Some(b"h2");
                let io = TokioIo::new(tls);

                let (status_code, response_headers, hyper_response) = if is_h2 {
                    let (mut sender, connection) = http2::Builder::new(TokioExecutor::new())
                        .handshake(io)
                        .await?;
                    tokio::spawn(async move {
                        if let Err(error) = connection.await {
                            log::debug!("HTTP/2 connection closed: {error}");
                        }
                    });

                    if let Some(pool) = pool {
                        let pool_key = PoolKey {
                            host: host.to_owned(),
                            port: parsed_url.port_or_known_default().unwrap_or(443),
                            is_ech: ech_accepted,
                        };
                        let mut guard = pool.write().unwrap_or_else(|p| p.into_inner());
                        guard.insert(
                            pool_key,
                            Http2ConnectionEntry {
                                sender: sender.clone(),
                                ech_accepted,
                                connected_addr: socket_addr.to_string(),
                                created_at: Instant::now(),
                            },
                        );
                    }

                    let mut request = Request::builder().method(method.clone()).uri(uri.clone());
                    for (name, value) in headers {
                        if name.eq_ignore_ascii_case("host")
                            || name.eq_ignore_ascii_case("connection")
                        {
                            continue;
                        }
                        request = request.header(name, value);
                    }
                    request = request.header("host", &authority);
                    let request = request
                        .body(Full::new(Bytes::copy_from_slice(body.unwrap_or_default())))?;
                    request_started = true;
                    let resp = sender.send_request(request).await?;
                    let status = resp.status().as_u16();
                    let resp_headers = resp
                        .headers()
                        .iter()
                        .map(|(name, value)| {
                            (name.to_string(), value.to_str().unwrap_or("").to_string())
                        })
                        .collect();
                    (status, resp_headers, resp)
                } else {
                    let (mut sender, connection) = http1::handshake(io).await?;
                    tokio::spawn(async move {
                        if let Err(error) = connection.await {
                            log::debug!("HTTP/1.1 connection closed: {error}");
                        }
                    });

                    let mut request = Request::builder().method(method.clone()).uri(uri.clone());
                    for (name, value) in headers {
                        if name.eq_ignore_ascii_case("host")
                            || name.eq_ignore_ascii_case("connection")
                        {
                            continue;
                        }
                        request = request.header(name, value);
                    }
                    request = request.header("host", &authority);
                    request = request.header("connection", "close");
                    let request = request
                        .body(Full::new(Bytes::copy_from_slice(body.unwrap_or_default())))?;
                    request_started = true;
                    let resp = sender.send_request(request).await?;
                    let status = resp.status().as_u16();
                    let resp_headers = resp
                        .headers()
                        .iter()
                        .map(|(name, value)| {
                            (name.to_string(), value.to_str().unwrap_or("").to_string())
                        })
                        .collect();
                    (status, resp_headers, resp)
                };

                process_hyper_response(
                    status_code,
                    response_headers,
                    hyper_response,
                    ech_accepted,
                    Some(socket_addr.to_string()),
                    updated_ech_config.clone(),
                    stream.as_ref(),
                )
                .await
            })
            .await;

            match result {
                Ok(Ok(response)) => return Ok(response),
                Ok(Err(error)) => {
                    if request_started {
                        // 请求已经发出：把这个事实原样上抛，禁止上层换配置/换地址重发
                        return Err(AttemptError::after_request(error));
                    }
                    if extract_retry_configs(&*error).is_some() {
                        // 服务端已明确拒绝当前 ECH 配置并下发了 retry_configs。
                        // 由于 Cloudflare 是 Anycast 架构，所有边缘使用同一套 ECHConfig，
                        // 继续尝试下一个 IP 依然会被拒绝且徒增 RTT / 耗尽超时，
                        // 立即中断候选循环并返回该错误以触发自愈重试。
                        return Err(AttemptError::before_request(error));
                    }
                    last_error = Some(error);
                }
                Err(elapsed) => {
                    if request_started {
                        return Err(AttemptError::after_request(elapsed));
                    }
                    last_error = Some(Box::new(elapsed));
                }
            }
        }
    }

    Err(AttemptError::before_request(last_error.unwrap_or_else(
        || "All candidate connections failed".into(),
    )))
}

/// 处理并分发 hyper 响应（支持流式与整体收集）
async fn process_hyper_response(
    status_code: u16,
    response_headers: Vec<(String, String)>,
    hyper_response: hyper::Response<Incoming>,
    ech_accepted: bool,
    connected_addr: Option<String>,
    updated_ech_config: Option<String>,
    stream: Option<&tokio::sync::mpsc::Sender<StreamEvent>>,
) -> Result<HttpResponse, Box<dyn std::error::Error + Send + Sync>> {
    let mut response = HttpResponse {
        status_code,
        headers: response_headers,
        body: Vec::new(),
        ech_accepted,
        connected_addr: connected_addr.clone(),
        updated_ech_config: updated_ech_config.clone(),
    };
    if let Some(stream) = stream {
        send_event(stream, StreamEvent::Response(response)).await?;
        let mut response_body = hyper_response.into_body();
        let mut total = 0usize;
        while let Some(frame) = response_body.frame().await {
            match frame {
                Ok(frame) => {
                    if let Ok(data) = frame.into_data() {
                        total = total.saturating_add(data.len());
                        if total > MAX_RESPONSE_BODY_SIZE {
                            let _ = send_event(
                                stream,
                                StreamEvent::Error(format!(
                                    "HTTP response exceeds {} bytes",
                                    MAX_RESPONSE_BODY_SIZE
                                )),
                            )
                            .await;
                            return Ok(HttpResponse {
                                status_code,
                                headers: Vec::new(),
                                body: Vec::new(),
                                ech_accepted,
                                connected_addr,
                                updated_ech_config,
                            });
                        }
                        for chunk in data.chunks(64 * 1024) {
                            send_event(stream, StreamEvent::Data(chunk.to_vec())).await?;
                        }
                    }
                }
                Err(error) => {
                    let _ = send_event(stream, StreamEvent::Error(error.to_string())).await;
                    return Ok(HttpResponse {
                        status_code,
                        headers: Vec::new(),
                        body: Vec::new(),
                        ech_accepted,
                        connected_addr,
                        updated_ech_config,
                    });
                }
            }
        }
        let _ = send_event(stream, StreamEvent::End).await;
        Ok(HttpResponse {
            status_code,
            headers: Vec::new(),
            body: Vec::new(),
            ech_accepted,
            connected_addr,
            updated_ech_config,
        })
    } else {
        response.body = collect_response_body(hyper_response.into_body()).await?;
        Ok(response)
    }
}

/// 向 Kotlin 读取循环投递一个流式事件（带消费者停滞保护，见 [CONSUMER_STALL_TIMEOUT]）。
async fn send_event(
    stream: &tokio::sync::mpsc::Sender<StreamEvent>,
    event: StreamEvent,
) -> Result<(), String> {
    match timeout(CONSUMER_STALL_TIMEOUT, stream.send(event)).await {
        Ok(Ok(())) => Ok(()),
        Ok(Err(_)) => Err("response consumer was cancelled".to_owned()),
        Err(_) => Err(format!(
            "response consumer stalled for more than {}s; aborting request",
            CONSUMER_STALL_TIMEOUT.as_secs()
        )),
    }
}

async fn collect_response_body(
    mut body: Incoming,
) -> Result<Vec<u8>, Box<dyn std::error::Error + Send + Sync>> {
    let mut result = Vec::new();
    while let Some(frame) = body.frame().await {
        let frame = frame?;
        if let Ok(data) = frame.into_data() {
            if result.len().saturating_add(data.len()) > MAX_RESPONSE_BODY_SIZE {
                return Err(
                    format!("HTTP response exceeds {} bytes", MAX_RESPONSE_BODY_SIZE).into(),
                );
            }
            result.extend_from_slice(&data);
        }
    }
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_set_ech_config_is_validated_and_scoped_by_host() {
        let test_ech_b64 = "AEX+DQBBXQAgACAMpYldYzQ9l7qOXBLrrdhR4BcdHHeNfu4qhqehUSG4NQAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA=";
        let client = EchHttpClient::new().unwrap();
        assert!(!client.set_ech_config_b64("example.com", "not-base64"));
        assert!(client.set_ech_config_b64("example.com", test_ech_b64));
        assert!(client
            .tls_configs_with_ech
            .read()
            .unwrap()
            .contains_key("example.com"));
    }

    #[test]
    fn test_required_ech_fails_without_configuration() {
        let client = EchHttpClient::new().unwrap();
        let result = runtime().unwrap().block_on(client.fetch(
            "https://example.com/",
            "GET",
            &[],
            None,
            1000,
            Some(&["127.0.0.1:443".to_owned()]),
            true,
            true,
            None,
            None,
        ));
        let error = match result {
            Ok(_) => panic!("required ECH unexpectedly succeeded"),
            Err(error) => error.to_string(),
        };
        assert!(error.contains("ECH is required"));
    }

    #[test]
    fn test_rejects_invalid_headers_before_connecting() {
        let client = EchHttpClient::new().unwrap();
        let error = runtime()
            .unwrap()
            .block_on(client.fetch(
                "https://example.com/",
                "GET",
                &[("x-test".to_owned(), "bad\r\nvalue".to_owned())],
                None,
                1000,
                Some(&["127.0.0.1:443".to_owned()]),
                false,
                false,
                None,
                None,
            ))
            .unwrap_err();
        assert!(error.to_string().contains("invalid or oversized"));
    }

    #[test]
    fn test_rejects_oversized_request_body_before_connecting() {
        let client = EchHttpClient::new().unwrap();
        let body = vec![0_u8; MAX_REQUEST_BODY_SIZE + 1];
        let error = runtime()
            .unwrap()
            .block_on(client.fetch(
                "https://example.com/",
                "POST",
                &[],
                Some(&body),
                1000,
                Some(&["127.0.0.1:443".to_owned()]),
                false,
                false,
                None,
                None,
            ))
            .unwrap_err();
        assert!(error.to_string().contains("request body exceeds"));
    }

    #[test]
    fn test_extract_retry_configs_and_update() {
        use rustls::internal::msgs::codec::Reader;

        let normal_err = std::io::Error::new(std::io::ErrorKind::TimedOut, "timed out");
        assert!(extract_retry_configs(&normal_err).is_none());

        let rejected_none = rustls::Error::PeerIncompatible(
            rustls::PeerIncompatible::ServerRejectedEncryptedClientHello(None),
        );
        let io_rejected_none = std::io::Error::new(std::io::ErrorKind::InvalidData, rejected_none);
        assert_eq!(extract_retry_configs(&io_rejected_none), Some(None));

        let test_ech_b64 = "AEX+DQBBXQAgACAMpYldYzQ9l7qOXBLrrdhR4BcdHHeNfu4qhqehUSG4NQAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA=";
        let raw_ech_bytes = base64::engine::general_purpose::STANDARD
            .decode(test_ech_b64)
            .unwrap();
        let payload_list =
            Vec::<EchConfigPayload>::read(&mut Reader::init(&raw_ech_bytes)).unwrap();

        let rejected_with_configs = rustls::Error::PeerIncompatible(
            rustls::PeerIncompatible::ServerRejectedEncryptedClientHello(Some(
                payload_list.clone(),
            )),
        );
        let io_rejected_configs =
            std::io::Error::new(std::io::ErrorKind::InvalidData, rejected_with_configs);
        let extracted = extract_retry_configs(&io_rejected_configs);
        assert_eq!(extracted, Some(Some(payload_list.clone())));

        let client = EchHttpClient::new().unwrap();
        let updated_b64 = client.update_ech_from_retry_configs("api.bgm.tv", &payload_list);
        assert!(updated_b64.is_some());
        assert!(client
            .tls_configs_with_ech
            .read()
            .unwrap()
            .contains_key("api.bgm.tv"));
    }

    /// 自愈重试依赖"能从错误链里挖出 retry_configs"。
    /// 引入 AttemptError 包装层后这条链路最容易被悄悄打断，故显式锁住。
    #[test]
    fn test_retry_configs_survive_attempt_error_wrapper() {
        use rustls::internal::msgs::codec::Reader;

        let test_ech_b64 = "AEX+DQBBXQAgACAMpYldYzQ9l7qOXBLrrdhR4BcdHHeNfu4qhqehUSG4NQAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA=";
        let raw_ech_bytes = base64::engine::general_purpose::STANDARD
            .decode(test_ech_b64)
            .unwrap();
        let payload_list =
            Vec::<EchConfigPayload>::read(&mut Reader::init(&raw_ech_bytes)).unwrap();

        let rejected = rustls::Error::PeerIncompatible(
            rustls::PeerIncompatible::ServerRejectedEncryptedClientHello(Some(
                payload_list.clone(),
            )),
        );
        let inner = std::io::Error::new(std::io::ErrorKind::InvalidData, rejected);
        let wrapped = AttemptError::before_request(inner);
        assert!(!wrapped.request_possibly_sent);
        assert_eq!(extract_retry_configs(&wrapped), Some(Some(payload_list)));
        // source() 必须继续暴露内层：否则既挖不到 retry_configs，也丢失可诊断信息
        assert!(std::error::Error::source(&wrapped).is_some());
    }

    /// 请求发出后的失败必须被标记成"不可重发"，这是非幂等请求不被重复发送的唯一依据。
    #[test]
    fn test_attempt_error_flags_request_sent() {
        let sent = AttemptError::after_request("connection reset".to_owned());
        assert!(sent.request_possibly_sent);
        assert!(sent.to_string().contains("connection reset"));

        let not_sent = AttemptError::before_request("dns failure".to_owned());
        assert!(!not_sent.request_possibly_sent);
    }

    #[test]
    fn test_alpn_and_session_resumption_configured() {
        let client = EchHttpClient::new().unwrap();
        // 校验标准 TLS 配置包含 h2 与 http/1.1
        assert_eq!(
            client.tls_config_standard.alpn_protocols,
            vec![b"h2".to_vec(), b"http/1.1".to_vec()]
        );

        // 校验带 ECH 的 TLS 配置也包含 h2 与 http/1.1
        let test_ech_b64 = "AEX+DQBBXQAgACAMpYldYzQ9l7qOXBLrrdhR4BcdHHeNfu4qhqehUSG4NQAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA=";
        assert!(client.set_ech_config_b64("api.bgm.tv", test_ech_b64));
        let guard = client.tls_configs_with_ech.read().unwrap();
        let ech_entry = guard.get("api.bgm.tv").unwrap();
        assert_eq!(
            ech_entry.config.alpn_protocols,
            vec![b"h2".to_vec(), b"http/1.1".to_vec()]
        );
    }

    #[test]
    fn test_pool_key_isolation_and_cleanup() {
        let key_ech = PoolKey {
            host: "api.bgm.tv".to_owned(),
            port: 443,
            is_ech: true,
        };
        let key_std = PoolKey {
            host: "api.bgm.tv".to_owned(),
            port: 443,
            is_ech: false,
        };
        let key_other_port = PoolKey {
            host: "api.bgm.tv".to_owned(),
            port: 8443,
            is_ech: true,
        };

        // 强隔离：同一个 host 下，不同 ECH 状态与端口互不相等
        assert_ne!(key_ech, key_std);
        assert_ne!(key_ech, key_other_port);

        let client = EchHttpClient::new().unwrap();
        {
            let mut pool = client.h2_pool.write().unwrap();
            // 验证 retain 逻辑按 host 清理
            let mut dummy_map = HashMap::new();
            dummy_map.insert(key_ech.clone(), ());
            dummy_map.insert(key_std.clone(), ());
            dummy_map.insert(
                PoolKey {
                    host: "example.com".to_owned(),
                    port: 443,
                    is_ech: true,
                },
                (),
            );
            dummy_map.retain(|k, _| k.host != "api.bgm.tv");
            assert_eq!(dummy_map.len(), 1);
            assert!(dummy_map.contains_key(&PoolKey {
                host: "example.com".to_owned(),
                port: 443,
                is_ech: true,
            }));
            let _ = pool;
        }
    }
}
