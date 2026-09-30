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

// Cloudflare 全局通用的 ECH 配置（Base64，对应 Outer SNI: cloudflare-ech.com）
const CLOUDFLARE_ECH_CONFIG_B64: &str =
    "AEX+DQBBXQAgACAMpYldYzQ9l7qOXBLrrdhR4BcdHHeNfu4qhqehUSG4NQAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA=";

pub struct HttpResponse {
    pub status_code: u16,
    pub headers: Vec<(String, String)>,
    pub body: Vec<u8>,
    pub ech_accepted: bool,
    pub connected_addr: Option<String>,
}

pub struct EchHttpClient {
    root_store: RootCertStore,
    tls_config_with_ech: RwLock<Arc<ClientConfig>>,
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
    pub fn new() -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        let root_store = RootCertStore {
            roots: webpki_roots::TLS_SERVER_ROOTS.into(),
        };

        // 1. 初始化默认 ECH 配置 (RFC 8744 / Cloudflare ECH 当前 active 密钥)
        let ech_bytes = base64::engine::general_purpose::STANDARD
            .decode(CLOUDFLARE_ECH_CONFIG_B64)
            .map_err(|e| format!("Base64 decode ECH config failed: {}", e))?;
        let ech_config_list = EchConfigListBytes::from(ech_bytes);
        let config_ech = build_ech_client_config(ech_config_list, &root_store)?;

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
            tls_config_with_ech: RwLock::new(Arc::new(config_ech)),
            tls_config_standard: Arc::new(config_std),
        })
    }

    /// 从服务端下发的 retry_configs 动态重新构建并热更新 ECH ClientConfig
    pub fn update_ech_config(&self, retry_configs: &[EchConfigPayload]) -> bool {
        let mut bytes = Vec::new();
        retry_configs.to_vec().encode(&mut bytes);
        let ech_config_list = EchConfigListBytes::from(bytes);

        match build_ech_client_config(ech_config_list, &self.root_store) {
            Ok(new_config) => {
                let mut lock = match self.tls_config_with_ech.write() {
                    Ok(guard) => guard,
                    Err(poisoned) => poisoned.into_inner(),
                };
                *lock = Arc::new(new_config);
                log::info!("ECH configuration dynamically updated from server retry_configs");
                true
            }
            Err(e) => {
                log::warn!("Failed to update ECH configuration from retry_configs: {}", e);
                false
            }
        }
    }

    /// 通用 HTTP/1.1 请求（支持可选目标地址列表与 ECH 协商）
    pub fn fetch(
        &self,
        url_str: &str,
        method: &str,
        headers: &[(String, String)],
        body: Option<&[u8]>,
        timeout_ms: u64,
        target_addrs: Option<&[String]>,
        enable_ech: bool,
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

        // 尝试 ECH 请求；若服务端轮换了 ECH 密钥 (ServerRejectedEncryptedClientHello)，自动自愈重试
        let current_ech_config = {
            let guard = match self.tls_config_with_ech.read() {
                Ok(g) => g,
                Err(p) => p.into_inner(),
            };
            guard.clone()
        };

        match try_fetch_with_config(
            current_ech_config,
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
                        if self.update_ech_config(&retry_configs) {
                            let updated_ech_config = {
                                let guard = match self.tls_config_with_ech.read() {
                                    Ok(g) => g,
                                    Err(p) => p.into_inner(),
                                };
                                guard.clone()
                            };
                            return try_fetch_with_config(
                                updated_ech_config,
                                &parsed_url,
                                method,
                                headers,
                                body,
                                timeout_ms,
                                &addrs_to_try,
                            );
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
    let mut raw_chunked = initial.to_vec();
    let mut chunk = [0u8; 8192];

    while !is_chunked_complete(&raw_chunked) {
        let n = stream.read(&mut chunk)?;
        if n == 0 {
            break;
        }
        raw_chunked.extend_from_slice(&chunk[..n]);
    }

    decode_chunked(&raw_chunked)
}

/// 还原 chunked 格式的 HTTP 响应体
fn decode_chunked(mut input: &[u8]) -> Result<Vec<u8>, std::io::Error> {
    let mut output = Vec::new();
    while !input.is_empty() {
        let crlf_pos = match input.windows(2).position(|w| w == b"\r\n") {
            Some(pos) => pos,
            None => break,
        };
        let size_str = match std::str::from_utf8(&input[..crlf_pos]) {
            Ok(s) => s.trim().split(';').next().unwrap_or("").trim(),
            Err(e) => return Err(std::io::Error::new(std::io::ErrorKind::InvalidData, e)),
        };
        let chunk_size = match usize::from_str_radix(size_str, 16) {
            Ok(sz) => sz,
            Err(e) => return Err(std::io::Error::new(std::io::ErrorKind::InvalidData, e)),
        };
        if chunk_size == 0 {
            break;
        }
        let data_start = crlf_pos + 2;
        let data_end = data_start + chunk_size;
        if data_end > input.len() {
            return Err(std::io::Error::new(
                std::io::ErrorKind::UnexpectedEof,
                "Chunked payload truncated prematurely",
            ));
        }
        output.extend_from_slice(&input[data_start..data_end]);
        let next_start = data_end + 2;
        if next_start <= input.len() {
            input = &input[next_start..];
        } else {
            input = &input[data_end..];
        }
    }
    Ok(output)
}

/// 检查 chunked 编码流是否已包含终结块 (0\r\n\r\n)
fn is_chunked_complete(mut input: &[u8]) -> bool {
    while !input.is_empty() {
        if let Some(crlf_pos) = input.windows(2).position(|w| w == b"\r\n") {
            let size_str = match std::str::from_utf8(&input[..crlf_pos]) {
                Ok(s) => s.trim().split(';').next().unwrap_or("").trim(),
                Err(_) => return false,
            };
            if let Ok(chunk_size) = usize::from_str_radix(size_str, 16) {
                if chunk_size == 0 {
                    let rem = &input[crlf_pos + 2..];
                    return rem.starts_with(b"\r\n") || rem.windows(4).any(|w| w == b"\r\n\r\n");
                }
                let data_end = crlf_pos + 2 + chunk_size;
                if data_end + 2 <= input.len() {
                    input = &input[data_end + 2..];
                    continue;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
    false
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
    fn test_decode_chunked() {
        let raw = b"5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n";
        let body = decode_chunked(raw).unwrap();
        assert_eq!(body, b"hello world");
    }

    #[test]
    fn test_chunked_complete() {
        let complete = b"5\r\nhello\r\n0\r\n\r\n";
        assert!(is_chunked_complete(complete));

        let partial = b"5\r\nhello\r\n";
        assert!(!is_chunked_complete(partial));
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

        // 从 Base64 模拟构建一份有效的 EchConfigPayload 列表
        let raw_ech_bytes = base64::engine::general_purpose::STANDARD
            .decode(CLOUDFLARE_ECH_CONFIG_B64)
            .unwrap();
        let payload_list = Vec::<EchConfigPayload>::read(&mut Reader::init(&raw_ech_bytes)).unwrap();

        // 验证包含 retry_configs 时正确提取并能动态热更新
        let rejected_with_configs = rustls::Error::PeerIncompatible(
            rustls::PeerIncompatible::ServerRejectedEncryptedClientHello(Some(payload_list.clone())),
        );
        let io_rejected_configs = std::io::Error::new(std::io::ErrorKind::InvalidData, rejected_with_configs);
        let extracted = extract_retry_configs(&io_rejected_configs);
        assert_eq!(extracted, Some(Some(payload_list.clone())));

        // 验证 EchHttpClient 动态更新逻辑
        let client = EchHttpClient::new().unwrap();
        assert!(client.update_ech_config(&payload_list));
    }
}
