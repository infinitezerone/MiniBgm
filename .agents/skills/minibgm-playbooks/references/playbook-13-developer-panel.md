## 剧本 13：现代内置开发面板与运行时调试收敛规范（Developer Panel & Runtime Debugging）
* **规范先例**：`DeveloperSettings.kt`、`SettingsRepository.kt` 与 `DeveloperOptionsBottomSheet.kt`
* **根因警示（来自静态 BuildConfig 与散落 FLAG_DEBUGGABLE 复盘）**：
  * 将 AI 助手、内置播放器等业务特性开关写死在编译期 `BuildConfig`，导致测试人员在 Dogfooding 或内部体验包中无法自由切换功能，排查偶现 Bug 必须反复重新构建全量 APK；
  * 各业务页随意读取 `FLAG_DEBUGGABLE` 内联展示调试按钮，破坏了界面的整洁度，且无法在非 Debuggable 环境复现底层原生异常。
* **物理防线与操作规范**：
  1. **触发与激活机制（Easter Egg & Persistence）**：
     - 在设置页版本号连续点击 7 次激活开发者模式，并持久化到 `UserPreferences`；
     - 激活后在设置页展现统一的「开发者选项」Row，点击唤起 `DeveloperOptionsBottomSheet`；
     - 提供一键停用开发者模式与重置所有覆写的功能。
  2. **状态响应式派生（Reactive StateFlow）**：
     - 严禁在 Feature / NavHost 业务代码中直接硬编码 `BuildConfig.ENABLE_*` 进行静态短路；
     - 必须通过 `SettingsRepository.isAiAssistantEnabled` / `isInternalPlayerEnabled` 等响应式只读流获取真值，优先消费开发者动态覆写值，未显式覆写时平滑回退到默认编译期配置。
  3. **调试能力统一收敛（Unified Drawer）**：
     - 特性开关覆写（AI / 播放器 / 详细网络与引擎日志）；
     - 原生流化组件模拟（模拟 BT 扩展未安装、重置并清理已下载的动态 `.so`）；
     - 加速镜像前缀覆写（GitHub Release 镜像代理动态调整）；
     - 系统与硬件诊断信息一键导出（机型、ABI 集合、系统版本、Package Name、Version Code）；
  4. **开关必须有真实消费方（No Decorative Toggles）**：
     - 面板里能拨的每个开关都必须在生产源码里至少有一个消费方；只能写设置、只能读设置却没人拿它做事，就是「装饰性开关」——用户拨完以为配置生效、行为毫无变化、全程无提示；
     - 复盘（一次审查连中两例）：`customMirrorPrefixOverride`（自定义加速镜像前缀）此前从未被任何下载路径读取，更新包与 BT 原生扩展照样走默认公共镜像——现已接到 `AndroidAssetDownloader(customMirrorPrefixProvider = ...)`，在 `download()` 冷流内按次读取（`developerSettings.first()`），显式入参优先，并同时覆盖更新包与 `.so` 下载；`verboseLoggingEnabled`（详细网络与引擎日志）此前门限只在 `MiniBgmApp.onCreate` 按 `BuildConfig` 定死一次，Release 包里打开开关依旧只有 Warn 以上日志——现由 `MiniBgmApp` 收集 `developerSettings.verboseLoggingEnabled`（`distinctUntilChanged`）动态调整 `Logger.setMinSeverity`；
     - **反转警示**：`simulateTorrentUninstalled` 的持久化值是 session-scoped 开关的遗留物——引擎侧 `setDebugSimulateUninstalled(true)` 会**删除整个动态 `.so` 目录**，因此严禁在启动期按持久化值回放；该值重启后不再有意义，面板状态以引擎内存态为准。
     - 红线：`ArchitectureRulesTest#developer_panel_settings_must_have_a_real_consumer`（前缀读取器 + DI 接线 + 日志门限 + `distinctUntilChanged`）与 `#downloaded_resource_actions_must_stay_honest`（已落盘禁用「离线下载」+ `completedTaskFor` 排除 `isFileMissing`）。
     - 最近崩溃日志（`CrashLog`）按需查看、复制、系统分享与清理。
