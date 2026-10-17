## 剧本 9：依赖注入闭环与真实容器校验（Dependency Injection Fidelity）
* **规范先例**：`AppModuleTest` 与 `DataModule.android.kt`
* **根因警示（来自真机 NoDefinitionFoundException 复盘）**：
  * Koin 为运行时服务定位器，编译器与未跑真实模块的假单元测试无法静态发现缺失的单例绑定；
  * 若为类构造器增加底层依赖（如 `UpdateRepositoryImpl` 新增 `ApkInstaller`），但在对应 Koin 模块（如 `platformDataModule`）中遗漏了 `single<ApkInstaller>` 注册，真机首次运行注入时将瞬间抛出 `NoDefinitionFoundException` 导致 App 强退。
* **物理防线与操作规范**：
  1. **构造器与模块原子同步**：任何时候给生产类构造函数新增依赖参数，第一步必须检查所有提供该类的 Koin 模块，确保新依赖在相应模块中有显式单例声明；
  2. **红线物理兜底**：`:core:testing:ArchitectureRulesTest` 的 `platform_data_module_declares_all_referenced_services`（硬红线 20）对 `platformDataModule` 的核心服务声明进行静态词法闭锁，防止漏绑或误删；
  3. **拒绝虚假假模块自欺欺人**：严禁在单测中为了“图省事”只写手工 Fake 模块测试 ViewModel；每次新增依赖或修改 `platformDataModule` / `userModule`，必须在 `AppModuleTest` 中执行真实模块容器解析断言（`verifyRealPlatformDataModuleResolution` 与 `verifyRealUserModuleResolutionWithPlatformDataModule`），由真实的 Koin 容器对每一项注入进行深度验证。
  4. **门禁范围不得漏掉装配测试**：`AppModuleTest` 横跨全部模块，因此 `tools/jgate` 只要检出任何模块代码改动就必须一并跑 `:app:testDebugUnitTest`（只按"改动落在哪个模块"推导会漏掉它）；同理 `AppModuleTest` 的 Stub Context 必须跟随生产代码实际调用的 Context API 扩展（`AndroidTorrentRepository.resolveDownloadDir()` 用 `getExternalFilesDir(null)`，缺这个重写会抛 "Method getExternalFilesDir in android.content.ContextWrapper not mocked"，让下载页在构造期直接击穿装配红线——`TorrentRepositoryTest` 的每个 Stub Context 早已按此约定重写，`AppModuleTest` 曾漏配）。
  5. **手工 `viewModel { XxxViewModel(...) }` 块必须显式传全「可选依赖」**：构造器上带 `= null` 默认值的参数是可选依赖，手工块漏传时**三层防线同时失效**——编译器因默认值不报错、Koin 被默认值兜住（不像漏绑单例会抛 `NoDefinitionFoundException`）、单测又自己传 fake；结果只在真机上表现为整条链路静默退化成「没这个功能」。`SubjectModule` 漏传 `playbackHistoryRepository` 时，播放历史不落库、换集/换源的「历史兜底续播」全废且全程无提示。新增/改动手工 DI 块时逐个 ctor 参数点名核对，红线 `manual_view_model_blocks_must_inject_every_optional_dependency`（`ArchitectureRulesTest` 硬红线 21）会静态比对「ctor 里 `= null` 的参数」与「块里命名实参」，漏一个即失败；非 null 默认值（`autoStart: Boolean = true`、`clock: () -> Long = { ... }`）是有意的可选项，不受此约束。
