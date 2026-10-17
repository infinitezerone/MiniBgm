## 剧本 12：通用资产下载与落盘互斥规范（Asset Download & File Mutex）
* **规范先例**：`AssetDownloader.kt`、`AndroidAssetDownloader.kt` 与 `UpdateRepository.kt`
* **根因警示（来自共享单例 StateFlow 进度踩踏与 RandomAccessFile 冲突复盘）**：
  * 单例模式下若在接口暴露全局 `StateFlow<DownloadState>`，所有通过该单例并发执行的下载（如更新 APK、BT 原生扩展 .so、Torrent 种子）会互相冲刷进度状态；
  * 若提供无参数的全局 `cancelDownload()`，一处的取消调用会意外重置全局状态为 `Idle`，造成伪取消或误伤其他并发下载任务；
  * 若多个并发协程指向相同的目标落盘路径，由于缺乏路径互斥控制，多个协程会同时打开相同的 `.tmp` 中转文件并发写入，导致文件被严重损坏。
* **物理防线与操作规范**：
  1. **请求作用域冷流（Request-scoped Cold Flow）**：
     * `AssetDownloader.download(...)` 返回冷流 `Flow<DownloadState>`，调用方各取所需、天然隔离；
     * 协程生命周期驱动取消：调用方 `Job` 取消时，Flow 管道触发 `finally` 自动关闭并释放底层的网络连接与文件句柄，无需脆弱的全局无参取消方法。
  2. **路径级并发互斥锁（Path-Level Mutex）**：
     * `AndroidAssetDownloader` 维护 `pathLocks = ConcurrentHashMap<String, Mutex>()`；
     * 进入下载执行前必须获取目标规范化路径（`canonicalPath`）对应的 `Mutex.withLock`，确保相同目标文件的写入天然串行化，杜绝 `RandomAccessFile` 并发冲突。
  3. **业务层自治状态与任务**：
     * 业务门面（如 `UpdateRepositoryImpl`）根据自身业务需要维护专属的 `_downloadState` 与 `downloadJob`，对自身任务的生命周期与取消负全责，不向底层基础设施转嫁业务状态。
  4. **消费侧必须透出真实进度与取消入口（假进度复盘）**：
     * 业务门面暴露的 `downloadState` 必须被 UI 真正消费：`DownloadState.Downloading` → 整数百分比 + `formatTransferSpeed(speedBytesPerSec)`，`isIndeterminate`（服务端未给 Content-Length）时显示不带百分比的文案；**严禁**把百分比硬编码成常量——曾有整条链路（状态发射、`UpdateRepository.cancelDownload`、`UserViewModel.cancelUpdateDownload`）都已实现却无人调用，界面只弹一次「正在下载新版本… 0%」，用户既看不到进度也无法中止，只能杀进程；
     * 接口实现了但 UI 不调用，等于没有：新增后台大任务时，除了"确认弹窗 + 进度"，必须同时给出**取消入口**并在真机上验证它真的停得下来；
     * 进度发射频率（`AndroidAssetDownloader` 100ms 节流 ≈ 10Hz）不得直接当作 Compose effect 的 key，须按整数百分比去抖，否则 snackbar 每秒被重建十次；同一 `SnackbarHostState` 上的「进度」与「终态」提示必须合并为**单一** effect——分成两个 effect 时，先结束者协程被取消时的 `finally` 会清空 `currentSnackbarData`，把后结束者刚装上的「下载完成」提示一并抹掉；
     * 红线：`ArchitectureRulesTest#update_flow_must_surface_real_progress_and_cancel`（原始文本扫描"把固定百分比传给下载中文案"的字面量形态）。
