use std::io::{Read, Write};
use std::net::{TcpStream, ToSocketAddrs};
use std::sync::atomic::{AtomicUsize, Ordering};
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

// Cloudflare 官方 Anycast 节点候选列表（用于在本地 DNS 被污染时免代理直连）
const DEFAULT_CANDIDATE_IPS: &[&str] = &[
    "104.26.8.23:443",
    "104.26.9.23:443",
    "172.67.73.67:443",
    "104.18.10.118:443",
    "104.18.11.118:443",
];

static LAST_SUCCESS_IP_INDEX: AtomicUsize = AtomicUsize::new(0);

pub struct HttpResponse {
    pub status_code: u16,
    pub headers: Vec<(String, String)>,
    pub body: Vec<u8>,
    pub ech_accepted: bool,
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

        // 1. 尝试初始化 ECH 配置
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

        // 3. 常规 TLS 配置（用于非 Cloudflare 域名或无需 ECH 的请求）
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

    pub fn fetch(
        &self,
        url_str: &str,
        method: &str,
        headers: &[(String, String)],
        body: Option<&[u8]>,
        timeout_ms: u64,
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

        // 判断是否为 Bangumi / Cloudflare 托管站点（开启 ECH）
        let is_bgm_domain = host.ends_with("bgm.tv")
            || host.ends_with("bangumi.tv")
            || host.ends_with("chii.in");

        let timeout = Duration::from_millis(timeout_ms.max(5000));

        // 整理连接目标地址：若是 bgm 域名，优先尝试候选 Anycast IP，同时包含域名直接解析出的 IP
        let mut addrs_to_try: Vec<String> = Vec::new();
        if is_bgm_domain {
            let last_idx = LAST_SUCCESS_IP_INDEX.load(Ordering::Relaxed) % DEFAULT_CANDIDATE_IPS.len();
            // 先尝试上次成功的 IP
            addrs_to_try.push(DEFAULT_CANDIDATE_IPS[last_idx].to_string());
            // 依次塞入其他预置 Anycast IP
            for (i, ip) in DEFAULT_CANDIDATE_IPS.iter().enumerate() {
                if i != last_idx {
                    addrs_to_try.push(ip.to_string());
                }
            }
        } else {
            addrs_to_try.push(format!("{}:{}", host, port));
        }

        let inner_sni: ServerName<'static> = host.to_string().try_into()?;
        let tls_config = if is_bgm_domain {
            self.tls_config_with_ech.clone()
        } else {
            self.tls_config_standard.clone()
        };

        let mut last_error: Option<Box<dyn std::error::Error + Send + Sync>> = None;

        for (attempt_idx, addr_str) in addrs_to_try.iter().enumerate() {
            let resolved_addrs: Vec<_> = match addr_str.to_socket_addrs() {
                Ok(iter) => iter.collect(),
                Err(e) => {
                    last_error = Some(Box::new(e));
                    continue;
                }
            };

            for sock_addr in resolved_addrs {
                let sock = match TcpStream::connect_timeout(&sock_addr, Duration::from_millis(4000.min(timeout_ms))) {
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

                // 读取响应数据
                let mut raw_response = Vec::new();
                let mut buf = [0u8; 8192];
                loop {
                    match tls.read(&mut buf) {
                        Ok(0) => break,
                        Ok(n) => raw_response.extend_from_slice(&buf[..n]),
                        Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => continue,
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
                if let Some(resp) = parse_http_response(&raw_response, ech_accepted) {
                    // 记录成功命中节点索引
                    if is_bgm_domain && attempt_idx < DEFAULT_CANDIDATE_IPS.len() {
                        LAST_SUCCESS_IP_INDEX.store(attempt_idx, Ordering::Relaxed);
                    }
                    return Ok(resp);
                }
            }
        }

        Err(last_error.unwrap_or_else(|| "All candidate connections failed".into()))
    }
}

/// 极简鲁棒的 HTTP/1.1 响应解析（支持 Chunked 还原与 Header 提取）
fn parse_http_response(raw: &[u8], ech_accepted: bool) -> Option<HttpResponse> {
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
