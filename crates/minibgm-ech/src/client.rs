use std::io::{Read, Write};
use std::net::{TcpStream, ToSocketAddrs};
use std::sync::Arc;
use std::time::Duration;
use base64::Engine;
use rustls::client::{EchConfig, EchMode, EchStatus};
use rustls::pki_types::{EchConfigListBytes, ServerName};
use rustls::{ClientConfig, RootCertStore, StreamOwned};
use url::Url;

// Cloudflare 全局通用的 ECH 配置（Base64）
// Outer SNI 统一为 cloudflare-ech.com
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
                // 单个 IP 握手超时设为 1200ms，故障快速切到下一候选地址
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

                // 构建 HTTP 请求报文
                let mut req_bytes = Vec::new();
                req_bytes.extend_from_slice(format!("{} {} HTTP/1.1\r\n", method.to_uppercase(), path).as_bytes());
                req_bytes.extend_from_slice(format!("Host: {}\r\n", host).as_bytes());
                req_bytes.extend_from_slice(b"Connection: close\r\n");

                let mut has_content_length = false;
                for (k, v) in headers {
                    if k.eq_ignore_ascii_case("content-length") {
                        has_content_length = true;
                    }
                    req_bytes.extend_from_slice(format!("{}: {}\r\n", k, v).as_bytes());
                }

                if let Some(b) = body {
                    if !has_content_length {
                        req_bytes.extend_from_slice(format!("Content-Length: {}\r\n", b.len()).as_bytes());
                    }
                } else if !has_content_length && (method.eq_ignore_ascii_case("POST") || method.eq_ignore_ascii_case("PUT")) {
                    req_bytes.extend_from_slice(b"Content-Length: 0\r\n");
                }

                req_bytes.extend_from_slice(b"\r\n");
                if let Some(b) = body {
                    req_bytes.extend_from_slice(b);
                }

                if let Err(e) = tls.write_all(&req_bytes) {
                    last_error = Some(Box::new(e));
                    continue;
                }
                let _ = tls.flush();

                let ech_accepted = tls.conn.ech_status() == EchStatus::Accepted;

                // 读取响应数据：达到完整响应帧即刻返回
                let mut raw_response = Vec::new();
                let mut buf = [0u8; 8192];
                loop {
                    match tls.read(&mut buf) {
                        Ok(0) => break,
                        Ok(n) => {
                            raw_response.extend_from_slice(&buf[..n]);
                            if is_http_response_complete(&raw_response) {
                                break;
                            }
                        }
                        Err(e) if e.kind() == std::io::ErrorKind::WouldBlock || e.kind() == std::io::ErrorKind::TimedOut => {
                            if is_http_response_complete(&raw_response) {
                                break;
                            }
                            if raw_response.is_empty() {
                                last_error = Some(Box::new(e));
                            }
                            break;
                        }
                        Err(e) => {
                            if raw_response.is_empty() {
                                last_error = Some(Box::new(e));
                            }
                            break;
                        }
                    }
                }

                if raw_response.is_empty() {
                    continue;
                }

                // 解析 HTTP 响应
                if let Some(resp) = parse_http_response(&raw_response, ech_accepted, Some(addr_str.clone())) {
                    return Ok(resp);
                }
            }
        }

        Err(last_error.unwrap_or_else(|| "All candidate connections failed".into()))
    }
}

/// 极简鲁棒的 HTTP/1.1 响应解析（支持 Chunked 还原与 Header 提取）
fn parse_http_response(raw: &[u8], ech_accepted: bool, connected_addr: Option<String>) -> Option<HttpResponse> {
    let header_end = raw.windows(4).position(|w| w == b"\r\n\r\n")?;
    let header_bytes = &raw[..header_end];
    let body_bytes = &raw[header_end + 4..];

    let header_str = String::from_utf8_lossy(header_bytes);
    let mut lines = header_str.lines();

    // 状态行：HTTP/1.1 200 OK
    let status_line = lines.next()?;
    let mut status_parts = status_line.split_whitespace();
    let _proto = status_parts.next()?;
    let status_code: u16 = status_parts.next()?.parse().ok()?;

    let mut headers = Vec::new();
    let mut is_chunked = false;

    for line in lines {
        if let Some((k, v)) = line.split_once(':') {
            let key = k.trim().to_string();
            let value = v.trim().to_string();
            if key.eq_ignore_ascii_case("transfer-encoding") && value.to_ascii_lowercase().contains("chunked") {
                is_chunked = true;
            }
            headers.push((key, value));
        }
    }

    let final_body = if is_chunked {
        decode_chunked(body_bytes)
    } else {
        body_bytes.to_vec()
    };

    Some(HttpResponse {
        status_code,
        headers,
        body: final_body,
        ech_accepted,
        connected_addr,
    })
}

/// 还原 chunked 格式的 HTTP 响应体
fn decode_chunked(mut input: &[u8]) -> Vec<u8> {
    let mut output = Vec::new();
    while !input.is_empty() {
        if let Some(crlf_pos) = input.windows(2).position(|w| w == b"\r\n") {
            let size_str = String::from_utf8_lossy(&input[..crlf_pos]);
            let size_str = size_str.trim().split(';').next().unwrap_or("").trim();
            if let Ok(chunk_size) = usize::from_str_radix(size_str, 16) {
                if chunk_size == 0 {
                    break;
                }
                let data_start = crlf_pos + 2;
                let data_end = data_start + chunk_size;
                if data_end <= input.len() {
                    output.extend_from_slice(&input[data_start..data_end]);
                    input = if data_end + 2 <= input.len() {
                        &input[data_end + 2..]
                    } else {
                        &input[data_end..]
                    };
                    continue;
                }
            }
        }
        // 如果格式不规范，直接将剩余内容作为 body 兜底输出
        output.extend_from_slice(input);
        break;
    }
    output
}

/// 检查 HTTP 响应报文是否已完整接收，避免连接持续等待服务端主动关闭导致阻塞数秒
fn is_http_response_complete(raw: &[u8]) -> bool {
    let header_end = match raw.windows(4).position(|w| w == b"\r\n\r\n") {
        Some(pos) => pos + 4,
        None => return false,
    };

    let header_bytes = &raw[..header_end];
    let body_bytes = &raw[header_end..];
    let header_str = String::from_utf8_lossy(header_bytes);

    // 检查状态码：1xx / 204 / 304 没有 Body
    if let Some(first_line) = header_str.lines().next() {
        if let Some(status_str) = first_line.split_whitespace().nth(1) {
            if let Ok(status) = status_str.parse::<u16>() {
                if (100..200).contains(&status) || status == 204 || status == 304 {
                    return true;
                }
            }
        }
    }

    // 检查 Content-Length
    for line in header_str.lines() {
        if let Some((k, v)) = line.split_once(':') {
            if k.trim().eq_ignore_ascii_case("content-length") {
                if let Ok(expected_len) = v.trim().parse::<usize>() {
                    return body_bytes.len() >= expected_len;
                }
            }
        }
    }

    // 检查 Transfer-Encoding: chunked
    let is_chunked = header_str.lines().any(|line| {
        if let Some((k, v)) = line.split_once(':') {
            k.trim().eq_ignore_ascii_case("transfer-encoding")
                && v.to_ascii_lowercase().contains("chunked")
        } else {
            false
        }
    });

    if is_chunked {
        return is_chunked_complete(body_bytes);
    }

    false
}

/// 检查 chunked 编码流是否已经到达终结块 (0\r\n\r\n)
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
    fn test_content_length_complete() {
        let resp = b"HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello";
        assert!(is_http_response_complete(resp));

        let partial = b"HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhel";
        assert!(!is_http_response_complete(partial));
    }

    #[test]
    fn test_chunked_complete() {
        let resp = b"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n0\r\n\r\n";
        assert!(is_http_response_complete(resp));

        let partial = b"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n";
        assert!(!is_http_response_complete(partial));
    }

    #[test]
    fn test_no_content_complete() {
        let resp = b"HTTP/1.1 204 No Content\r\n\r\n";
        assert!(is_http_response_complete(resp));
    }
}
