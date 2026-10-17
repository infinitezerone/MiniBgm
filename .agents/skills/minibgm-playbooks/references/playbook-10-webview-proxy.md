## 剧本 10：应用内 WebView 与本地代理闭环规范（In-App WebView & Proxy Fidelity）
* **规范先例**：`InAppWebView.kt` 与 `OAuthHttpCodec.kt`
* **根因警示（来自真机 CSP 阻断与盲测复盘）**：
  * GFW 对外部公共 CDN（如 jsdelivr）存在严重 SNI 阻断。若代理侧仅在 HTML 文本中将脚本 URL 替换为镜像节点（如 `fastly.jsdelivr.net`），而遗漏了响应头 `Content-Security-Policy`（CSP）的同步改写，浏览器安全策略将硬性拦截外部脚本执行，导致页面 JS 整体瘫痪、表单退化为未拦截的原生 GET 提交；
  * `WebChromeClient` 若未实现 `onConsoleMessage`，所有前端 CSP 报错、语法错误与网络异常在 Logcat 中完全隐形，使排查沦为凭空猜测。
* **物理防线与操作规范**：
  1. **可观测性闭环**：生产代码中声明 `WebChromeClient` 必须重写 `onConsoleMessage`，并将消息根据级别分别以 `proxyLogger.e/w/i` 输出到 Logcat；在 `onPageFinished` 中必须注入全局 `unhandledrejection` 与 `error` 诊断监听（`:core:testing:ArchitectureRulesTest` 机械红线拦截）；
  2. **协议头与内容改写一致性**：环回反代若对响应体中的外部域名进行重写，必须在出站过滤器（`filterOutboundHeaders`）中同步改写 `Content-Security-Policy` 响应头，放行对应镜像源；
  3. **全域全路径 Cookie 注入**：会话鉴权 Cookie 必须在页面加载前，针对目标主机与根路径（`Path=/`）全面写入（如 `setCookie("http://$host", "$cookie; Path=/")`），确保后续同源 AJAX（如 `/p1/login` POST）能完整携带令牌通过代理校验。
