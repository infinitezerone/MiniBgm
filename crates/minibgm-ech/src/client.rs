use base64::Engine;
use bytes::Bytes;
use http_body_util::{BodyExt, Full};
use hyper::body::Incoming;
use hyper::client::conn::http1;
use hyper::{Method, Request, Uri};
use hyper_util::rt::TokioIo;
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
const CONNECT_TIMEOUT: Duration = Duration::from_millis(5000);
const ECH_CONFIG_TTL: Duration = Duration::from_secs(60 * 60);

struct EchConfigEntry {
    config: Arc<ClientConfig>,
    source_b64: String,
    updated_at: Instant,
}

/// 提取服务端返回的 ECH retry_configs
fn extract_retry_configs(
    err: &(dyn std::error::Error + 'static),
) -> Option<Option<Vec<EchConfigPayload>>> {
    if let Some(rustls_err) = err.downcast_ref::<rustls::Error>() {
        if let rustls::Error::PeerIncompatible(rustls::PeerIncompatible::ServerRejectedEncryptedClientHello(rc)) = rustls_err {
            return Some(rc.clone());
        }
    }
    if let Some(io_err) = err.downcast_ref::<std::io::Error>() {
        if let Some(rustls_err) = io_err.get_ref().and_then(|e| e.downcast_ref::<rustls::Error>()) {
            if let rustls::Error::PeerIncompatible(rustls::PeerIncompatible::ServerRejectedEncryptedClientHello(rc)) = rustls_err {
                return Some(rc.clone());
            }
        }
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

    let config_ech = ClientConfig::builder_with_provider(Arc::new(
        rustls::crypto::aws_lc_rs::default_provider(),
    ))
    .with_ech(ech_mode)
    .map_err(|e| format!("Configure with_ech failed: {:?}", e))?
    .with_root_certificates(root_store.clone())
    .with_no_client_auth();

    Ok(config_ech)
}

impl EchHttpClient {
    /// 构造 ECH HTTP 客户端
    pub fn new() -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        let root_store = RootCertStore {
            roots: webpki_roots::TLS_SERVER_ROOTS.into(),
        };

        let config_std = ClientConfig::builder_with_provider(Arc::new(
            rustls::crypto::aws_lc_rs::default_provider(),
        ))
        .with_safe_default_protocol_versions()
        .map_err(|e| format!("Configure safe protocol versions failed: {:?}", e))?
        .with_root_certificates(root_store.clone())
        .with_no_client_auth();

        Ok(Self {
            root_store,
            tls_configs_with_ech: RwLock::new(HashMap::new()),
            tls_config_standard: Arc::new(config_std),
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
                log::info!("ECH configuration dynamically updated for {host} from server retry_configs");
                Some(b64)
            }
            Err(e) => {
                log::warn!("Failed to update ECH configuration from retry_configs: {e}");
                None
            }
        }
    }

    /// 发起 HTTP/1.1 请求
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
            )
            .await;
        }

        if let Some(cfg_b64) = dynamic_ech_config {
            let needs_update = {
                let lock = self
                    .tls_configs_with_ech
                    .read()
                    .unwrap_or_else(|p| p.into_inner());
                lock.get(host)
                    .map(|entry| {
                        entry.source_b64 != cfg_b64.trim()
                            || entry.updated_at.elapsed() > ECH_CONFIG_TTL
                    })
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
                )
                .await;
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
        )
        .await
        {
            Ok(resp) => Ok(resp),
            Err(e) => {
                if let Some(opt_retry_configs) = extract_retry_configs(&*e) {
                    if let Some(retry_configs) = opt_retry_configs {
                        log::warn!(
                            "Server rejected ECH for {host} with retry_configs; self-healing ECH config and retrying..."
                        );
                        if let Some(new_ech_b64) = self.update_ech_from_retry_configs(host, &retry_configs) {
                            let updated_cfg = {
                                let guard = self.tls_configs_with_ech.read().unwrap_or_else(|p| p.into_inner());
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
                                )
                                .await;
                            }
                        }
                    }
                }

                if require_ech {
                    return Err(format!(
                        "ECH request failed and require_ech is enabled: {e}"
                    )
                    .into());
                }
                log::warn!("ECH handshake failed for {host}; falling back to standard TLS: {e}");
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
                )
                .await
            }
        }
    }
}

/// Execute one request through hyper over an ECH-capable TLS stream.
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
) -> Result<HttpResponse, Box<dyn std::error::Error + Send + Sync>> {
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
    )
    .await
}

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
) -> Result<HttpResponse, Box<dyn std::error::Error + Send + Sync>> {
    let host = parsed_url.host_str().ok_or("URL has no host")?;
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
        "/".parse()?
    } else {
        request_target.parse()?
    };
    let method: Method = method.parse()?;
    let request_timeout = Duration::from_millis(timeout_ms.max(3000));
    let deadline = tokio::time::Instant::now() + request_timeout;
    let server_name: ServerName<'static> = host.to_owned().try_into()?;
    let mut last_error: Option<Box<dyn std::error::Error + Send + Sync>> = None;

    for addr_str in addrs_to_try {
        let remaining = deadline.saturating_duration_since(tokio::time::Instant::now());
        if remaining.is_zero() {
            return Err("request timed out while resolving candidate addresses".into());
        }
        let resolved_addrs: Vec<SocketAddr> =
            match timeout(remaining, tokio::net::lookup_host(addr_str.as_str())).await {
                Ok(Ok(iter)) => iter.collect(),
                Ok(Err(error)) => {
                    last_error = Some(Box::new(error));
                    continue;
                }
                Err(error) => return Err(Box::new(error)),
            };

        for socket_addr in resolved_addrs {
            // Once the request has been handed to hyper, retrying another address can
            // duplicate a non-idempotent POST/PUT. Only connection and TLS failures
            // are safe to try on the next candidate.
            let mut request_started = false;
            let remaining = deadline.saturating_duration_since(tokio::time::Instant::now());
            if remaining.is_zero() {
                return Err("request timed out before connecting to candidate address".into());
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

                let io = TokioIo::new(tls);
                let (mut sender, connection) = http1::handshake(io).await?;
                tokio::spawn(async move {
                    if let Err(error) = connection.await {
                        log::debug!("HTTP/1.1 connection closed: {error}");
                    }
                });

                let mut request = Request::builder().method(method.clone()).uri(uri.clone());
                for (name, value) in headers {
                    if name.eq_ignore_ascii_case("host") || name.eq_ignore_ascii_case("connection") {
                        continue;
                    }
                    request = request.header(name, value);
                }
                request = request.header("host", &authority);
                request = request.header("connection", "close");
                let request =
                    request.body(Full::new(Bytes::copy_from_slice(body.unwrap_or_default())))?;
                request_started = true;
                let hyper_response = sender.send_request(request).await?;
                let status_code = hyper_response.status().as_u16();
                let response_headers = hyper_response
                    .headers()
                    .iter()
                    .map(|(name, value)| {
                        (name.to_string(), value.to_str().unwrap_or("").to_string())
                    })
                    .collect();
                let mut response = HttpResponse {
                    status_code,
                    headers: response_headers,
                    body: Vec::new(),
                    ech_accepted,
                    connected_addr: Some(socket_addr.to_string()),
                    updated_ech_config: updated_ech_config.clone(),
                };
                if let Some(stream) = &stream {
                    stream
                        .send(StreamEvent::Response(response))
                        .await
                        .map_err(|_| "response consumer was cancelled")?;
                    let mut response_body = hyper_response.into_body();
                    let mut total = 0usize;
                    while let Some(frame) = response_body.frame().await {
                        match frame {
                            Ok(frame) => {
                                if let Ok(data) = frame.into_data() {
                                    total = total.saturating_add(data.len());
                                    if total > MAX_RESPONSE_BODY_SIZE {
                                        let _ = stream
                                            .send(StreamEvent::Error(format!(
                                                "HTTP response exceeds {} bytes",
                                                MAX_RESPONSE_BODY_SIZE
                                             )))
                                            .await;
                                        return Ok::<_, Box<dyn std::error::Error + Send + Sync>>(
                                            HttpResponse {
                                                status_code,
                                                headers: Vec::new(),
                                                body: Vec::new(),
                                                ech_accepted,
                                                connected_addr: Some(socket_addr.to_string()),
                                                updated_ech_config: updated_ech_config.clone(),
                                            },
                                        );
                                    }
                                    for chunk in data.chunks(64 * 1024) {
                                        if stream
                                            .send(StreamEvent::Data(chunk.to_vec()))
                                            .await
                                            .is_err()
                                        {
                                            return Err("response consumer was cancelled".into());
                                        }
                                    }
                                }
                            }
                            Err(error) => {
                                let _ = stream.send(StreamEvent::Error(error.to_string())).await;
                                return Ok(HttpResponse {
                                    status_code,
                                    headers: Vec::new(),
                                    body: Vec::new(),
                                    ech_accepted,
                                    connected_addr: Some(socket_addr.to_string()),
                                    updated_ech_config: updated_ech_config.clone(),
                                });
                            }
                        }
                    }
                    let _ = stream.send(StreamEvent::End).await;
                    Ok(HttpResponse {
                        status_code,
                        headers: Vec::new(),
                        body: Vec::new(),
                        ech_accepted,
                        connected_addr: Some(socket_addr.to_string()),
                        updated_ech_config: updated_ech_config.clone(),
                    })
                } else {
                    response.body = collect_response_body(hyper_response.into_body()).await?;
                    Ok(response)
                }
            })
            .await;

            match result {
                Ok(Ok(response)) => return Ok(response),
                Ok(Err(error)) => {
                    last_error = Some(error);
                    if request_started {
                        return Err(last_error.expect("request error was just recorded"));
                    }
                }
                Err(error) => {
                    last_error = Some(Box::new(error));
                    if request_started {
                        return Err(last_error.expect("request timeout was just recorded"));
                    }
                }
            }
        }
    }

    Err(last_error.unwrap_or_else(|| "All candidate connections failed".into()))
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
        let payload_list = Vec::<EchConfigPayload>::read(&mut Reader::init(&raw_ech_bytes)).unwrap();

        let rejected_with_configs = rustls::Error::PeerIncompatible(
            rustls::PeerIncompatible::ServerRejectedEncryptedClientHello(Some(payload_list.clone())),
        );
        let io_rejected_configs = std::io::Error::new(std::io::ErrorKind::InvalidData, rejected_with_configs);
        let extracted = extract_retry_configs(&io_rejected_configs);
        assert_eq!(extracted, Some(Some(payload_list.clone())));

        let client = EchHttpClient::new().unwrap();
        let updated_b64 = client.update_ech_from_retry_configs("api.bgm.tv", &payload_list);
        assert!(updated_b64.is_some());
        assert!(client.tls_configs_with_ech.read().unwrap().contains_key("api.bgm.tv"));
    }
}
