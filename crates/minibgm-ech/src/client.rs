use std::io::{Read, Write};
use std::net::{TcpStream, ToSocketAddrs};
use std::sync::{Arc, RwLock};
use std::time::Duration;
use base64::Engine;
use rustls::client::{EchConfig, EchMode, EchStatus};
use rustls::internal::msgs::codec::Codec;
use rustls::internal::msgs::handshake::EchConfigPayload;
use rustls::pki_types::{EchConfigListBytes, ServerName};
use rustls::{ClientConfig, RootCertStore, StreamOwned};
use url::Url;

pub struct HttpResponse {
    pub status_code: u16,
    pub headers: Vec<(String, String)>,
    pub body: Vec<u8>,
    pub ech_accepted: bool,
    pub connected_addr: Option<String>,
    pub updated_ech_config: Option<String>,
}

pub struct EchHttpClient {
    root_store: RootCertStore,
    tls_config_with_ech: RwLock<Option<Arc<ClientConfig>>>,
    tls_config_standard: Arc<ClientConfig>,
}

/// 根据给定的 ECH Config List 字节流构建带 ECH 的 ClientConfig
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

/// 从错误链路中递归提取服务端返回的 ECH 拒绝对话与重试配置 (retry_configs)
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

impl EchHttpClient {
    /// 构造通用 ECH HTTP 客户端
    ///
    /// 保持引擎纯洁性：不再硬编码特定站点或厂商的 Base64 密钥。
    /// 若传入 `initial_ech_config_b64` 则立即构建初始 ECH 配置，否则保持未配置状态，
    /// 后续可经由 `fetch()` 调用或服务端 `retry_configs` 自愈时动态注入。
    pub fn new(initial_ech_config_b64: Option<&str>) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        let root_store = RootCertStore {
            roots: webpki_roots::TLS_SERVER_ROOTS.into(),
        };

        // 1. 初始化 ECH 配置 (若外部提供有效 Base64 字符串)
        let initial_config_ech = if let Some(b64) = initial_ech_config_b64 {
            if let Ok(ech_bytes) = base64::engine::general_purpose::STANDARD.decode(b64.trim()) {
                let ech_config_list = EchConfigListBytes::from(ech_bytes);
                build_ech_client_config(ech_config_list, &root_store).ok().map(Arc::new)
            } else {
                None
            }
        } else {
            None
        };

        // 2. 常规 TLS 配置 (用于降级或无 ECH 请求)
        let config_std = ClientConfig::builder_with_provider(Arc::new(
            rustls::crypto::aws_lc_rs::default_provider(),
        ))
        .with_safe_default_protocol_versions()
        .map_err(|e| format!("Configure safe protocol versions failed: {:?}", e))?
        .with_root_certificates(root_store.clone())
        .with_no_client_auth();

        Ok(Self {
            root_store,
            tls_config_with_ech: RwLock::new(initial_config_ech),
            tls_config_standard: Arc::new(config_std),
        })
    }

    /// 从服务端下发的 retry_configs 动态重新构建并热更新 ECH ClientConfig
    /// 返回更新后的 ECHConfigList 的 Base64 编码字符串，供调用方（上层平台）持久化存储
    pub fn update_ech_from_retry_configs(&self, retry_configs: &[EchConfigPayload]) -> Option<String> {
        let mut bytes = Vec::new();
        retry_configs.to_vec().encode(&mut bytes);
        let b64 = base64::engine::general_purpose::STANDARD.encode(&bytes);
        let ech_config_list = EchConfigListBytes::from(bytes);

        match build_ech_client_config(ech_config_list, &self.root_store) {
            Ok(new_config) => {
                let mut lock = self.tls_config_with_ech.write().unwrap_or_else(|poisoned| poisoned.into_inner());
                *lock = Some(Arc::new(new_config));
                log::info!("ECH configuration dynamically updated from server retry_configs");
                Some(b64)
            }
            Err(e) => {
                log::warn!("Failed to update ECH configuration from retry_configs: {}", e);
                None
            }
        }
    }

    /// 由调用方主动动态设置或刷新 ECH Base64 配置
    pub fn set_ech_config_b64(&self, b64: &str) -> bool {
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
                let mut lock = self.tls_config_with_ech.write().unwrap_or_else(|p| p.into_inner());
                *lock = Some(Arc::new(new_config));
                true
            }
            Err(e) => {
                log::warn!("Failed to build ClientConfig from ECH config: {}", e);
                false
            }
        }
    }

    /// 通用 HTTP/1.1 请求（支持可选目标地址列表、动态 ECH 注入与服务端密钥轮换自愈）
    pub fn fetch(
        &self,
        url_str: &str,
        method: &str,
        headers: &[(String, String)],
        body: Option<&[u8]>,
        timeout_ms: u64,
        target_addrs: Option<&[String]>,
        enable_ech: bool,
        dynamic_ech_config: Option<&str>,
    ) -> Result<HttpResponse, Box<dyn std::error::Error + Send + Sync>> {
        let parsed_url = Url::parse(url_str)?;
        let host = parsed_url
            .host_str()
            .ok_or_else(|| "URL has no host".to_string())?;
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
            );
        }

        // 若传入了动态 ECH 配置且本地尚未就绪，尝试动态构建
        if let Some(cfg_b64) = dynamic_ech_config {
            let needs_update = {
                let lock = self.tls_config_with_ech.read().unwrap_or_else(|p| p.into_inner());
                lock.is_none()
            };
            if needs_update {
                self.set_ech_config_b64(cfg_b64);
            }
        }

        // 尝试 ECH 请求；若服务端轮换了 ECH 密钥 (ServerRejectedEncryptedClientHello)，自动自愈重试
        let current_ech_config = {
            let guard = self.tls_config_with_ech.read().unwrap_or_else(|p| p.into_inner());
            guard.clone()
        };

        let ech_config_to_use = match current_ech_config {
            Some(cfg) => cfg,
            None => {
                log::warn!("ECH requested but no ECH config available, falling back to standard TLS");
                return try_fetch_with_config(
                    self.tls_config_standard.clone(),
                    &parsed_url,
                    method,
                    headers,
                    body,
                    timeout_ms,
                    &addrs_to_try,
                );
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
        ) {
            Ok(resp) => Ok(resp),
            Err(e) => {
                if let Some(opt_retry_configs) = extract_retry_configs(&*e) {
                    if let Some(retry_configs) = opt_retry_configs {
                        log::warn!(
                            "Server rejected ECH with retry_configs; self-healing ECH config and retrying..."
                        );
                        if let Some(new_ech_b64) = self.update_ech_from_retry_configs(&retry_configs) {
                            let updated_ech_config = {
                                let guard = self.tls_config_with_ech.read().unwrap_or_else(|p| p.into_inner());
                                guard.clone()
                            };
                            if let Some(updated_cfg) = updated_ech_config {
                                let mut retry_resp = try_fetch_with_config(
                                    updated_cfg,
                                    &parsed_url,
                                    method,
                                    headers,
                                    body,
                                    timeout_ms,
                                    &addrs_to_try,
                                )?;
                                retry_resp.updated_ech_config = Some(new_ech_b64);
                                return Ok(retry_resp);
                            }
                        }
                    } else {
                        log::warn!("Server rejected ECH without retry_configs; falling back to standard TLS...");
                        return try_fetch_with_config(
                            self.tls_config_standard.clone(),
                            &parsed_url,
                            method,
                            headers,
                            body,
                            timeout_ms,
                            &addrs_to_try,
                        );
                    }
                }
                Err(e)
            }
        }
    }
}

/// 执行单次带指定 ClientConfig 的 HTTP/1.1 请求尝试
fn try_fetch_with_config(
    tls_config: Arc<ClientConfig>,
    parsed_url: &Url,
    method: &str,
    headers: &[(String, String)],
    body: Option<&[u8]>,
    timeout_ms: u64,
    addrs_to_try: &[String],
) -> Result<HttpResponse, Box<dyn std::error::Error + Send + Sync>> {
    let host = parsed_url
        .host_str()
        .ok_or_else(|| "URL has no host".to_string())?;
    let path = if parsed_url.query().is_some() {
        format!(
            "{}?{}",
            parsed_url.path(),
            parsed_url.query().unwrap_or("")
        )
    } else {
        parsed_url.path().to_string()
    };

    let timeout = Duration::from_millis(timeout_ms.max(3000));
    let inner_sni: ServerName<'static> = host.to_string().try_into()?;
    let mut last_error: Option<Box<dyn std::error::Error + Send + Sync>> = None;

    for addr_str in addrs_to_try {
        let resolved_addrs: Vec<_> = match addr_str.to_socket_addrs() {
            Ok(iter) => iter.collect(),
            Err(e) => {
                last_error = Some(Box::new(e));
                continue;
            }
        };

        for sock_addr in resolved_addrs {
            let connect_timeout = Duration::from_millis(1200.min(timeout_ms));
            let sock = match TcpStream::connect_timeout(&sock_addr, connect_timeout) {
                Ok(s) => s,
                Err(e) => {
                    last_error = Some(Box::new(e));
                    continue;
                }
            };

            let _ = sock.set_nodelay(true);
            let _ = sock.set_read_timeout(Some(timeout));
            let _ = sock.set_write_timeout(Some(timeout));

            let conn = match rustls::ClientConnection::new(tls_config.clone(), inner_sni.clone()) {
                Ok(c) => c,
                Err(e) => {
                    last_error = Some(Box::new(e));
                    continue;
                }
            };

            let mut tls = StreamOwned::new(conn, sock);

            // 构建标准 HTTP/1.1 请求报文
            let mut req_bytes = Vec::with_capacity(512);
            let _ = write!(req_bytes, "{} {} HTTP/1.1\r\n", method.to_uppercase(), path);
            let _ = write!(req_bytes, "Host: {}\r\n", host);
            let _ = write!(req_bytes, "Connection: close\r\n");

            let mut has_content_length = false;
            for (k, v) in headers {
                if k.eq_ignore_ascii_case("content-length") {
                    has_content_length = true;
                }
                let _ = write!(req_bytes, "{}: {}\r\n", k, v);
            }

            if let Some(b) = body {
                if !has_content_length {
                    let _ = write!(req_bytes, "Content-Length: {}\r\n", b.len());
                }
            } else if !has_content_length
                && (method.eq_ignore_ascii_case("POST") || method.eq_ignore_ascii_case("PUT"))
            {
                let _ = write!(req_bytes, "Content-Length: 0\r\n");
            }

            let _ = write!(req_bytes, "\r\n");
            if let Some(b) = body {
                req_bytes.extend_from_slice(b);
            }

            if let Err(e) = tls.write_all(&req_bytes) {
                last_error = Some(Box::new(e));
                continue;
            }
            let _ = tls.flush();

            let ech_accepted = tls.conn.ech_status() == EchStatus::Accepted;

            // 使用 httparse 与流式状态机接收并解析 HTTP 响应
            match read_http_response(&mut tls, ech_accepted, Some(addr_str.clone())) {
                Ok(resp) => return Ok(resp),
                Err(e) => {
                    last_error = Some(Box::new(e));
                    continue;
                }
            }
        }
    }

    Err(last_error.unwrap_or_else(|| "All candidate connections failed".into()))
}

/// 基于官方 httparse 的流式 HTTP/1.1 响应接收与解析
fn read_http_response<R: Read>(
    stream: &mut R,
    ech_accepted: bool,
    connected_addr: Option<String>,
) -> Result<HttpResponse, std::io::Error> {
    let mut buffer = Vec::with_capacity(8192);
    let mut chunk = [0u8; 4096];

    // Phase 1: 流式读取直至 Headers 完整结束
    let header_len = loop {
        let n = stream.read(&mut chunk)?;
        if n == 0 {
            return Err(std::io::Error::new(
                std::io::ErrorKind::UnexpectedEof,
                "Connection closed before HTTP headers were fully received",
            ));
        }
        buffer.extend_from_slice(&chunk[..n]);

        let mut headers = [httparse::EMPTY_HEADER; 64];
        let mut resp = httparse::Response::new(&mut headers);
        match resp.parse(&buffer) {
            Ok(httparse::Status::Complete(len)) => break len,
            Ok(httparse::Status::Partial) => {
                if buffer.len() > 65536 {
                    return Err(std::io::Error::new(
                        std::io::ErrorKind::InvalidData,
                        "HTTP headers exceed max allowed size (64KB)",
                    ));
                }
                continue;
            }
            Err(e) => {
                return Err(std::io::Error::new(
                    std::io::ErrorKind::InvalidData,
                    format!("Failed to parse HTTP headers: {}", e),
                ));
            }
        }
    };

    // Phase 2: 使用 httparse 提取状态码和 Headers
    let mut headers = [httparse::EMPTY_HEADER; 64];
    let mut resp = httparse::Response::new(&mut headers);
    let _ = resp.parse(&buffer);

    let status_code = resp.code.unwrap_or(200);
    let mut out_headers = Vec::with_capacity(resp.headers.len());
    let mut content_length: Option<usize> = None;
    let mut is_chunked = false;

    for h in resp.headers {
        let name = h.name.to_string();
        let value = String::from_utf8_lossy(h.value).trim().to_string();
        if name.eq_ignore_ascii_case("content-length") {
            content_length = value.parse::<usize>().ok();
        } else if name.eq_ignore_ascii_case("transfer-encoding") && value.to_ascii_lowercase().contains("chunked") {
            is_chunked = true;
        }
        out_headers.push((name, value));
    }

    // Phase 3: 流式获取 Body（精准截断，杜绝阻塞挂起与 O(N^2) 重复扫描）
    let initial_body = &buffer[header_len..];

    // 1xx, 204, 304 规范无 Body
    if (100..200).contains(&status_code) || status_code == 204 || status_code == 304 {
        return Ok(HttpResponse {
            status_code,
            headers: out_headers,
            body: Vec::new(),
            ech_accepted,
            connected_addr,
            updated_ech_config: None,
        });
    }

    let final_body = if is_chunked {
        read_chunked_body(initial_body, stream)?
    } else if let Some(expected_len) = content_length {
        read_content_length_body(initial_body, stream, expected_len)?
    } else {
        // Connection: close 读到 EOF
        let mut body = initial_body.to_vec();
        stream.read_to_end(&mut body)?;
        body
    };

    Ok(HttpResponse {
        status_code,
        headers: out_headers,
        body: final_body,
        ech_accepted,
        connected_addr,
        updated_ech_config: None,
    })
}

/// 读取并截取指定 Content-Length 的响应体
fn read_content_length_body<R: Read>(
    initial: &[u8],
    stream: &mut R,
    expected_len: usize,
) -> Result<Vec<u8>, std::io::Error> {
    let mut body = Vec::with_capacity(expected_len);
    if initial.len() >= expected_len {
        body.extend_from_slice(&initial[..expected_len]);
        return Ok(body);
    }
    body.extend_from_slice(initial);
    let mut chunk = [0u8; 8192];
    while body.len() < expected_len {
        let remaining = expected_len - body.len();
        let to_read = remaining.min(chunk.len());
        let n = stream.read(&mut chunk[..to_read])?;
        if n == 0 {
            break;
        }
        body.extend_from_slice(&chunk[..n]);
    }
    Ok(body)
}

/// 读取并解码 chunked 传输格式的响应体
fn read_chunked_body<R: Read>(
    initial: &[u8],
    stream: &mut R,
) -> Result<Vec<u8>, std::io::Error> {
    let mut buffer = initial.to_vec();
    let mut body = Vec::new();
    let mut chunk_buf = [0u8; 8192];

    loop {
        match httparse::parse_chunk_size(&buffer) {
            Ok(httparse::Status::Complete((data_start, chunk_size))) => {
                let chunk_size = chunk_size as usize;
                if chunk_size == 0 {
                    break;
                }
                let data_end = data_start + chunk_size;
                let next_chunk_start = data_end + 2; // 包含 chunk 尾部的 \r\n

                while buffer.len() < next_chunk_start {
                    let n = stream.read(&mut chunk_buf)?;
                    if n == 0 {
                        return Err(std::io::Error::new(
                            std::io::ErrorKind::UnexpectedEof,
                            "Chunk data prematurely truncated",
                        ));
                    }
                    buffer.extend_from_slice(&chunk_buf[..n]);
                }

                body.extend_from_slice(&buffer[data_start..data_end]);
                buffer.drain(..next_chunk_start);
            }
            Ok(httparse::Status::Partial) => {
                let n = stream.read(&mut chunk_buf)?;
                if n == 0 {
                    return Err(std::io::Error::new(
                        std::io::ErrorKind::UnexpectedEof,
                        "Incomplete chunk header before EOF",
                    ));
                }
                buffer.extend_from_slice(&chunk_buf[..n]);
            }
            Err(e) => {
                return Err(std::io::Error::new(
                    std::io::ErrorKind::InvalidData,
                    format!("Invalid HTTP chunked format: {:?}", e),
                ));
            }
        }
    }

    Ok(body)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_content_length_body() {
        let mut stream = std::io::Cursor::new(b"hello world");
        let body = read_content_length_body(b"hel", &mut stream, 11).unwrap();
        assert_eq!(body, b"helhello wo");
    }

    #[test]
    fn test_read_chunked_body() {
        let raw = b"6\r\n world\r\n0\r\n\r\n";
        let mut cursor = std::io::Cursor::new(raw);
        let body = read_chunked_body(b"5\r\nhello\r\n", &mut cursor).unwrap();
        assert_eq!(body, b"hello world");
    }

    #[test]
    fn test_read_http_response_with_httparse() {
        let raw = b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 13\r\n\r\n{\"key\":\"val\"}";
        let mut cursor = std::io::Cursor::new(raw);
        let resp = read_http_response(&mut cursor, true, Some("127.0.0.1:443".into())).unwrap();
        assert_eq!(resp.status_code, 200);
        assert_eq!(resp.body, b"{\"key\":\"val\"}");
        assert!(resp.ech_accepted);
        assert_eq!(resp.connected_addr, Some("127.0.0.1:443".into()));
    }

    #[test]
    fn test_read_no_content() {
        let raw = b"HTTP/1.1 204 No Content\r\n\r\n";
        let mut cursor = std::io::Cursor::new(raw);
        let resp = read_http_response(&mut cursor, false, None).unwrap();
        assert_eq!(resp.status_code, 204);
        assert!(resp.body.is_empty());
    }

    #[test]
    fn test_extract_retry_configs_and_update() {
        use rustls::internal::msgs::codec::Reader;

        // 验证非 ECH 错误返回 None
        let normal_err = std::io::Error::new(std::io::ErrorKind::TimedOut, "timed out");
        assert!(extract_retry_configs(&normal_err).is_none());

        // 验证被拒绝但未提供 retry_configs 时返回 Some(None)
        let rejected_none = rustls::Error::PeerIncompatible(
            rustls::PeerIncompatible::ServerRejectedEncryptedClientHello(None),
        );
        let io_rejected_none = std::io::Error::new(std::io::ErrorKind::InvalidData, rejected_none);
        assert_eq!(extract_retry_configs(&io_rejected_none), Some(None));

        // 模拟构建一份有效的 EchConfigPayload 列表（仅用作测试载荷）
        let test_ech_b64 = "AEX+DQBBXQAgACAMpYldYzQ9l7qOXBLrrdhR4BcdHHeNfu4qhqehUSG4NQAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA=";
        let raw_ech_bytes = base64::engine::general_purpose::STANDARD
            .decode(test_ech_b64)
            .unwrap();
        let payload_list = Vec::<EchConfigPayload>::read(&mut Reader::init(&raw_ech_bytes)).unwrap();

        // 验证包含 retry_configs 时正确提取并能动态热更新
        let rejected_with_configs = rustls::Error::PeerIncompatible(
            rustls::PeerIncompatible::ServerRejectedEncryptedClientHello(Some(payload_list.clone())),
        );
        let io_rejected_configs = std::io::Error::new(std::io::ErrorKind::InvalidData, rejected_with_configs);
        let extracted = extract_retry_configs(&io_rejected_configs);
        assert_eq!(extracted, Some(Some(payload_list.clone())));

        // 验证 EchHttpClient 动态更新与热替换逻辑
        let client = EchHttpClient::new(None).unwrap();
        let updated_b64 = client.update_ech_from_retry_configs(&payload_list);
        assert!(updated_b64.is_some());
    }
}
