## 剧本 15：播放源订阅与规则导入完整性规范（Playback Subscription & Rules Ingestion Integrity）
* **规范先例**：`CommunitySubscriptionService.kt` 与 `SettingsRepository.kt`
* **根因警示（来自订阅丢源与 403 误判存活复盘）**：
  * **连通性探测非硬拦截（测试丢弃反模式）**：订阅源连通性测试（`testSourceConnectivity`）纯用于 UI 延迟排序与诊断展示。MacCMS 源根路径普遍返回 HTTP 403（阻断爬虫/目录遍历，但实际 `/api.php/provide/vod` 接口可用），且移动蜂窝网络下也可能发生瞬时丢包；若在订阅同步落库（`refreshPlaybackSubscription`）时按 `isAlive` 过滤丢弃规则，会导致合法源被大量静默剔除（历史上 5 个可用 MacCMS 源被误删 4 个，仅剩 1 个）；
  * **导入协议单一与脏状态自愈缺失**：规则导入（`importRulesFromJson`）若仅支持裸 `List<PlaybackSourceRule>`，用户粘贴包含 `{"version": 1, "sources": [...]}` 的订阅包 JSON 会直接抛 `MissingFieldException` 导入失败；当订阅 URL 已配置但规则列表因历史版本异常缩减（`<= 1`）时，若无自动自愈机制，用户即使重启应用也无法恢复缺失的规则。
* **物理防线与操作规范**：
  1. **探测状态不污染订阅真值（Save All Ingested Sources）**：
     * `refreshPlaybackSubscription()` 必须将解析出的全部规则（`report.sources`）完整落库持久化，严禁使用 `.filter { it.isAlive }` 做破坏性过滤；
     * 存活探测探针（`CommunitySubscriptionService`）必须将 HTTP 200..499（排除 404 与 408）判定为主机存活（`isAlive = true`），宽容对待 API 服务器根目录的 403 阻断；网络超时门限合理设为 4000ms+。
  2. **多协议 JSON 兼容导入**：
     * `importRulesFromJson` 必须支持多协议分级解析：首选解析裸 `List<PlaybackSourceRule>`，失败时平滑回退解析 `CommunitySubscriptionPackage`（提取 `sources`）、TVBox 配置（`TvBoxConfig`）以及 `DiscoveredSource` 数组，杜绝格式不兼容阻碍用户直接导入；
     * 导入完成后同样执行全量持久化，绝不因未测试连通性而丢弃规则。
  3. **数据流入口自动自愈（Flow Auto-Healing）**：
     * `SettingsRepository.playbackRules` 流必须在启动时通过 `.onStart { triggerSubscriptionHealingIfMissing() }` 守卫订阅完整性：若配置了 `playbackSubscriptionUrl` 但当前规则数量 `<= 1`，后台自愈拉取全量订阅规则并补齐落库，无需用户手动折腾重置。
  4. **红线拦截**：
     * `ArchitectureRulesTest#subscription_sources_must_never_be_discarded_by_probe_status` 静态闭锁 `SettingsRepository` 禁按 `isAlive` 过滤，并校验探测存活判据。
  5. **JS 爬虫沙盒隔离与执行配额保护**：
     * TVBox `type: 3` (Spider) JS 脚本必须经由具备内存配额（<= 16MB）与执行超时看门狗（<= 15s）的沙盒执行，所有网络请求必须经由 `PageFetchService` 宿主注入，严禁直接使用底层 Socket。
     * 站点 `ext` 必须按原始 JSON 从 `TvBoxSite` 透传到 Spider `init(ext)`；`req` 保持同步返回值以兼容 `req(...).content`；`pdfh` / `pdfa` / `pd` 只由宿主 Jsoup 解析已取回正文，禁止把 HTML/CSS 选择器引擎或第二套 DOM 实现搬进 QuickJS。
  6. **TVBox 聚合解析链 (parses) 与嗅探提速规范**：
     * `TvBoxParseResolver` 必须声明超时看门狗保护（<= 8000ms），按顺序依次尝试 `type: 1` (JSON 接口)、`type: 0/2` (网页/嗅探) 与 `type: 3` (JS 脚本)，单解析失败时优雅回退下一项；
     * 静态 HTML 播放器配置提取器（DPlayer / MacCMS `player_aaaa` / Base64 / Iframe `?url=`）优先于无头 WebView 执行，实现静态毫秒级秒提；
     * 无头 WebView 嗅探在捕获到首个可用 `.m3u8` 媒体流时触发 300ms 早停结算，并在 `shouldInterceptRequest` 中阻断图片、字体与广告等重型资源（`tvbox_parses_must_enforce_timeout_and_route_through_page_fetch_or_webview`）。
