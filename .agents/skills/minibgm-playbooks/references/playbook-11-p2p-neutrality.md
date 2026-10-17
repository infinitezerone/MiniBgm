## 剧本 11：P2P 种子协议中立性与起播抽屉分层规范（P2P Neutrality & Playback Sources Panel）
* **规范先例**：`SubjectSourcesBottomSheet.kt`、`TorrentFeedRepository.kt` 与 `TorrentRssParser.kt`
* **根因警示（来自合规审计与站点解耦复盘）**：
  * 应用若硬编码未授权抓取特定第三方种子站点，将使核心客户端背负版权与合规风险，且难以应对镜像站切换与结构调整；
  * 核心客户端必须维持类似 Tachiyomi/Kodi 的 100% 协议与架构中立性。
* **物理防线与操作规范**：
  1. **100% 协议中立化与规则驱动**：
     * 核心应用仅保留底层 P2P 流播与下载能力及通用的 RSS/XML 种子提取器，严禁在底层数据层与详情层写死特定域名；
     * P2P 种子资源发现由用户配置的通用 `PlaybackRuleKind.TORRENT`（`RuleParserType.RSS_TORRENT`）规则驱动；未启用种子源时零网络消耗；
     * `:core:testing:ArchitectureRulesTest` 设物理红线断言，拦截核心层硬编码特定域名。
     * **RSS 提取出的 URL 必须还原标记实体（`TorrentRssParser` 复盘）**：`link` / `enclosure` 普遍把 `&` 写成 `&amp;`，不还原则磁链自带的 Tracker 声明（`&amp;tr=...`）会被下游当无效参数丢弃、种子直链带着 `&amp;` 去请求必然取不到——与 `MacCmsParser` / `ResolverMediaUrl` 同一教训。本类缺陷无法用词法红线表达（文件里存在 `unescapeXml` 不代表 URL 出站前真的调用过它，这正是本 bug 逃逸的原因），由 `TorrentRssParserTest` 的实体还原用例（精确断言还原后的磁链与直链）守住。
  2. **详情页起播抽屉（SubjectSourcesBottomSheet）现代分层布局**：
     * **顶部 Header**：分集标题与状态；
     * **主起播入口**：“立即起播”Hero 卡片；
     * **在线播放线路**：在线流媒体规则线路；
     * **P2P 原画与字幕组资源**：按字幕组归类的种子资源卡片，支持“边下边播”与“离线缓存”；未配置规则时引导用户配置；
     * **辅助工具与外部跳转**：AI 找源、外部跳转与规则管理入口。

    * **单实现优先：共享语义必须收敛到唯一真源（重复造轮子复盘）**：`infoHash` 解析曾在引擎 / 仓库 / 缓存 / UI 四处各写一遍，且降级语义还不一致（引擎返回 `"unknown_hash"` 哨兵、其余处返回空串）——同一语义两套降级正是"未知磁力互相误判为同一源"的温床，必须收敛到 `:core:model` 的 `magnetInfoHash`（解析不出统一返回**空串**，调用方一律 `isBlank()` 判定）；速率与体积格式化必须收敛到 `formatTransferSpeed` / `DownloadStorageUsage.formatBytes`，页面内手搓 `KB/s` 分支与无引用的 `formattedSpeed` 死代码属性一并清除（副本漂移会让同一数值两处显示不一致）；种子缓存键的直链指纹必须是 SHA-256 截断摘要，`Integer.toHexString(url.hashCode())` 这类 32 位弱哈希会碰撞，而载荷校验只验 bencode 结构不验身份，于是静默接受"合法但错误"的种子。红线：`ArchitectureRulesTest#torrent_implementation_consolidation_guards_are_enforced`（原始文本扫描全仓 `substringAfter("xt=urn:btih:")` 只允许出现在真源文件——此处不可用 `KotlinSourceScanner.sanitize()`，违规形态必然落在字符串字面量里会被脱敏抹掉；另闭锁哨兵字符串绝迹、`toHexString(` 禁用、`DownloadScreen` 必须消费真源）。
    * **「停止起播」必须真停底层任务 + 源同一性须含 torrentUrl**：`startStreamDownload` 必须经 `onTaskQueued(taskId)` 在任务落库瞬间把身份透出给 UI，UI 的停止动作必须 `pauseDownload(taskId)`（选**暂停**而非删除：该任务可能正是用户此前显式确认过的离线下载，删除会连半成品一起清掉）后再清忙态——只 `busyJob?.cancel()` 会让任务继续占着单实例 P2P 队列，并在下载页留下用户从未主动创建过的条目；起播进度与停止入口严禁包在「展开态」里（折叠后入口消失但任务仍在跑）；单实例会话期间所有资源动作必须一并禁用（可见的诚实禁用，而不是按钮亮着却因忙态守卫点了没反应）；既有任务比对必须同时含分集身份键与源同一性（`infoHash` 优先 → `magnetUrl` 原文 → `torrentUrl` 兜底），只比 `magnetUrl` 时 torrentUrl-only 资源两侧都是空串，`"" == ""` 会互相误命中并复用错误的流地址或 `file://` 落盘路径。
