## 剧本 17：第三方图床双轨路由与富文本媒体健壮性规范（Third-Party Image Routing & Resilient BBCode Parser）
* **规范先例**：`ImageLoaderModule.kt` 与 `BgmBbCodeParser.kt`
* **根因警示（来自海外图床白屏阻塞与畸形 BBCode 标签丢图复盘）**：
  * **主路径阻塞式探测**：请求未知第三方图床时，网络引擎在主调用链上同步阻塞等待 DoH 解析 Type 65 记录，一旦海外 DNS 抖动或超时直接导致首图卡死 3~5 秒甚至解析失败；
  * **无加载中视觉反馈**：BBCode 图片下载期间仅为空白占位，用户无法感知图片正在下载还是已经挂掉；
  * **复杂/畸形 BBCode 语法缺失容错**：论坛用户分享的带链接图片（`[url=...][img]...[/img][/url]`）或图床一键生成的双层畸形包裹（`[img][url=...][img]...[/img][/url][/img]`）会被传统简易正则截断，提取出错误 URL 或直接被当作普通文本切碎丢弃。
* **物理防线与操作规范**：
  1. **Fail-Open 快速直连与后台异步建档**：
     * 未知第三方图床立即采用 Fail-Open 策略，由通用 CIO 引擎直接发流发起连接，零阻塞立即加载；
     * 后台非阻塞调用 `EchCapabilityDetector.probeAsyncIfUnknown(host)`，探测到支持 ECH 的域名自动登入路由池，使后续请求自动升级为 ECH Anycast 优选路由。
  2. **图片加载态闭环与链接还原**：
     * `BgmBbCodeContent` 中的图片块在加载期间必须提供居中的 `CircularProgressIndicator` 反馈，直到加载成功或明确失败；
     * `BgmBbCodeParser` 必须完整支持带链接图片（`BbCodeBlock.Image` 携带 `linkUrl`），并提供 `extractCleanImageUrl` 自动清洗剥离外部畸形嵌套标签，确保无论多复杂的论坛排版都能提取出真实可用的图片直链。
  3. **红线拦截**：
     * `ArchitectureRulesTest#image_loader_routing_must_fail_open_for_unknown_third_party_hosts` 机械拦截主路径同步调用 `isEchSupported` 并断言异步探测。
