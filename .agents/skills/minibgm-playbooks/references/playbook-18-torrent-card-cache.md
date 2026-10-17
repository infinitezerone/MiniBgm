## 剧本 18：种子资源卡片去噪与分集二级持久化缓存规范（Torrent Card Denoising & Resilient Two-Level Cache）
* **规范先例**：`SubjectSourcesBottomSheet.kt`、`TorrentFeedRepository.kt` 与 `FileTorrentFeedDiskCache.kt`
* **根因警示（来自双源伪概念误导、5 按钮横向拥挤与切集重复网络请求复盘）**：
  * **技术细节泄漏与伪概念误导**：RSS 条目同时返回 `.torrent` 与 `magnet:` 时，界面打上 `[双源可用]` 彩色徽标，容易被用户误解为“双音轨（国日双语）”或“两条不同线路”，产生严重的认知负担与多余色块；
  * **操作主次不分与一屏可见度低**：卡片底部平铺 5 个横向按钮（边下边播、离线下载、外部打开、复制磁力、更多），挤占纵向空间导致卡片过高、一屏仅能展示 2 个字幕组；
  * **无持久化导致重复网络拉取**：磁链与种子具有内容寻址的永久不变性（Immutable），但此前仅有 32 条临时内存缓存，重进应用或切分集时频繁打境外 RSS 站点，不仅加载卡顿且易触发 429 限频。
* **物理防线与操作规范**：
  1. **消灭技术细节徽标，还原纯净卡片顶栏**：
     * 严禁将底层抓取通道展示为 `[双源可用 / 种子 / 磁力]` 胶囊；底层在起播时自动择优（有种子文件直链优先秒下，无种子文件走磁力 DHT 探测），对用户完全透明；
     * 顶栏仅保留核心元数据：`[第 01 集 / 合集]` 身份徽标 + `字幕组名称`（加粗高亮） + 右侧 `文件大小` 与 `发布日期`。
  2. **主次分级的 3 元素操作栏（1 主 + 1 辅 + 1 菜单）**：
     * **主动作（权重 1f）**：`[ ▶ 播放 (边下边播) / 播放本地 ]`；
     * **辅助动作**：`[ 📥 离线下载 ]`（未下载时展示，已落盘时诚实禁用）；
     * **更多操作菜单 `⋮`**：将 `在外部应用打开 (115/网盘/播放器)`、`复制磁力链接`、`保存种子文件` 与 `复制 InfoHash` 优雅收纳进 `DropdownMenu`。
  3. **磁链二级持久化缓存（Two-Level Feed Cache）**：
     * `TorrentFeedRepository` 建立内存 LRU + 磁盘持久化 `TorrentFeedDiskCache`（`FileTorrentFeedDiskCache`）；
     * 命中缓存时 0ms 瞬间起播/展示分集资源，完结番与已查分集零重复网络开销；
     * 用户点击右上角刷新图标时（`forceRefresh = true`），同步绕过并重写双级缓存。
  4. **红线拦截**：
     * `ArchitectureRulesTest#torrent_resource_card_must_not_expose_technical_dual_source_badges_and_use_lean_actions` 机械断言严禁暴露技术徽标并具备磁盘持久化缓存契约。
