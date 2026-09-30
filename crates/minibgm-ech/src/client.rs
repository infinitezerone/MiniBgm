use std::io::{Read, Write};
use std::net::{TcpStream, ToSocketAddrs};
use std::sync::Arc;
use std::time::Duration;
use base64::Engine;
use rustls::client::{EchConfig, EchMode, EchStatus};
use rustls::pki_types::{EchConfigListBytes, ServerName};
use rustls::{ClientConfig, RootCertStore, StreamOwned};
use url::Url;

// Cloudflare 全局通用的 ECH 配置（Base64，对应 Outer SNI: cloudflare-ech.com）
const CLOUDFLARE_ECH_CONFIG_B64: &str =
    "AEX+DQBBNAAgACBEVvV6qv+2EGSHksMVtzMtBb0W4uDonEFEC5F+QK8lNAAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA=";

pub struct HttpResponse {
    pub status_code: u16,
    pub headers: Vec<(String, String)>,
    pub body: Vec<u8>,
    pub ech_accepted: bool,
    pub connected_addr: Option<String>,
}

pub struct EchHttpClient {
    tls_config_with_ech: Arc<ClientConfig>,
    tls_config_standard: Arc<ClientConfig>,
}

impl EchHttpClient {
    pub fn new() -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        let root_store = RootCertStore {
            roots: webpki_roots::TLS_SERVER_ROOTS.into(),
        };

        // 1. 初始化 ECH 配置 (RFC 8744 / Cloudflare ECH)
        let ech_bytes = base64::engine::general_purpose::STANDARD
            .decode(CLOUDFLARE_ECH_CONFIG_B64)
            .map_err(|e| format!("Base64 decode ECH config failed: {}", e))?;
        let ech_config_list = EchConfigListBytes::from(ech_bytes);

        let ech_config = EchConfig::new(
            ech_config_list,
            rustls::crypto::aws_lc_rs::hpke::ALL_SUPPORTED_SUITES,
        )
        .map_err(|e| format!("Init EchConfig failed: {:?}", e))?;

        let ech_mode = EchMode::from(ech_config);

        // 2. 带 ECH 的 TLS 1.3 配置
        let config_ech = ClientConfig::builder_with_provider(Arc::new(
            rustls::crypto::aws_lc_rs::default_provider(),
        ))
        .with_ech(ech_mode)
        .map_err(|e| format!("Configure with_ech failed: {:?}", e))?
        .with_root_certificates(root_store.clone())
        .with_no_client_auth();

        // 3. 常规 TLS 配置
        let config_std = ClientConfig::builder_with_provider(Arc::new(
            rustls::crypto::aws_lc_rs::default_provider(),
        ))
        .with_safe_default_protocol_versions()
        .map_err(|e| format!("Configure safe protocol versions failed: {:?}", e))?
        .with_root_certificates(root_store)
        .with_no_client_auth();

        Ok(Self {
            tls_config_with_ech: Arc::new(config_ech),
            tls_config_standard: Arc::new(config_std),
        })
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

        // 收集待尝试的目标地址（若外部传入了候选目标地址列表则优先依次尝试；否则走标准 DNS 解析）
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

        let inner_sni: ServerName<'static> = host.to_string().try_into()?;
        let tls_config = if enable_ech {
            self.tls_config_with_ech.clone()
        } else {
            self.tls_config_standard.clone()
        };

        let mut last_error: Option<Box<dyn std::error::Error + Send + Sync>> = None;

        for addr_str in addrs_to_try.iter() {
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
                } else if !has_content_length && (method.eq_ignore_ascii_case("POST") || method.eq_ignore_ascii_case("PUT")) {
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
}
