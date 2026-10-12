# MiniBgm AI Agent & Developer Guidelines

MiniBgm：Bangumi（bgm.tv）追番排期与收藏管理客户端。模块化 Clean Architecture，仿 Google *Now in Android*。本文只写"删了会改变行为"的内容；细节一律以代码、测试与钩子为准。

## 版本控制

仓库与 jj colocate，历史操作用 `jj commit` / `jj git push`；提交摘要遵循 Conventional Commits（如 `feat(core:ai): xxx` / `chore: xxx`）。git 只读（log/diff/show/ls-remote/tag/clone）——git 写子命令被 VCS 层的 `tools/githooks` 硬拒，新 clone 跑一次 `tools/githooks/setup.sh` 接线，`tools/jgate` 会自愈补配；jj 不运行 git 钩子，与合法路径零冲突。

- 一个提交一个目的；同一文件别混两个目的——事后 `jj split` 拆不出可编译的中间态。
- 同一时刻只允许一条 Gradle 命令在跑（长构建后台 + 足够超时，避免 daemon 互踩）。
- OAuth 令牌交换/刷新代理在独立的私有 Cloudflare Workers 项目，本仓库搜不到；业务 API 直连 api.bgm.tv。

## 构建与验证

提交前唯一入口（含"绿≠测过"的 XML 真实性校验）：

```bash
# Windows: .\tools\jgate.ps1  |  Linux/CI: bash tools/jgate
# 流程：素材体积 → Rust 门禁 → spotlessApply → 架构红线 → 触及模块测试 → :app:assembleDebug → 结果校验
```

- 目标按改动自动推断（工作副本 `@` → 最近提交 `@-`）；触及底层基础层（`:core:model/data/network/database/datastore`、`build-logic`、根配置）自动提升为全量。日常提交不跑 release；编码中间态秒级红线自检使用 `.\tools\quick-check.ps1`。
- 素材体积门：图片单张 > 200 KB 或合计 > 1 MB 硬拦（必须压为 800px WebP）。jj 不运行 git 钩子，门禁由 jgate 兜底。
- 模块依赖变化后跑 `./gradlew graphUpdate` 重建依赖图；Android Lint 增量基线在各模块 `lint-baseline.xml`。
- Rust ECH 原生库（`:core:network` + `crates/minibgm-ech`）：Android 打包强制需要 Rust 工具链 + NDK，缺库硬失败，禁止静默回退 CIO。

## 硬红线

机械执行：源语义由 `:core:testing` 的 ArchitectureRulesTest，依赖图由 build-logic 的 ModuleBoundaryConventionPlugin（配置期断言）。此处只留索引，细节看测试：

1. feature 互不依赖；UI 只经 `:core:data` 仓库；`:core:model` 纯 Kotlin；feature 无原始 IO、无颜色字面量、不外露可变状态（MutableStateFlow 一律禁止；对外只暴露只读 StateFlow，或 Compose State 持有者的只读类型——多字段原子更新用 `Snapshot.withMutableSnapshot`，不用 `update { it.copy(…) }`）；路由密封于 `BgmRoute` 并声明堆叠层。
2. 瞬态交互收敛：UI 确认/交互浮层统一由 `BgmOverlayHost` + `overlayHostState.await(...)` 挂起承接，严禁在 feature 中写散落的 `var showDialog` 状态机；通用登录提示收口于 `BgmLoginPromptDialog`；ViewModel 一次性事件（Channel(BUFFERED)）严禁裸 `collect`，必须经 `ObserveAsEvents` 绑定生命周期（STARTED）。一次性事件的用户可见文案必须以 `@StringRes` + 格式化参数下发、UI 侧解析（服务端运行时消息经 message 字段透传），严禁 VM 内联文案绕开资源体系。
3. 可播地址只来自 `findPlayableSources`；一切写操作止于 PENDING_CONFIRMATION 提案卡，用户确认才落库。
4. 凭据只存在于 `AuthTokensDataSource`；不绕过/削弱 `BgmPkce`；不剥离或覆盖 `User-Agent`；关键写操作包 `withContext(NonCancellable)`。

## 领域规则

- 装饰性功能 fail-open：光晕/动效/横幅等内部异常一律 `runCatching` 降级为"没有装饰"，绝不崩宿主页面；读硬件位图像素前先 `copy` 成软件位图。
- 排期真值只来自 AniList 已验证播出事件；禁止算术预测与合成剧集；bangumi-data 仅作元数据与平台映射，不得污染官方播出时间。
- 复用 `BgmHttpClient.jsonConfig`，不手写 `Json {}`（模块内白名单见 ArchitectureRulesTest）。
- Ktor 401 自动刷新；刷新彻底失败自动登出——调用方永远不会看到 401。
- 流媒体发流真实性：流引擎严禁派发未就绪的空 Socket 伪地址；扩展下载与后台大任务严禁静默触发，必须经 `BgmOverlayHost` 显式确认。

## 规范先例与协同流

新模式的唯一入口，不适配就停下提决策，勿发明新模式：只读特性 → `:feature:schedule`；带参详情 → `:feature:subject`；写操作 → `CollectionRepository`；认证 → `AuthRepository` + `BgmPkce`；新红线 → 先写 ArchitectureRulesTest。详细实现剧本参见 `.agents/skills/minibgm-playbooks/SKILL.md`。

AI 协同流水线（机械执行，严禁甩锅排错）：

1. **前置反诘（Spec 对齐与发射架）**：编码前反查硬红线与先例，输出 Mini-Spec 与改动栈；多 Agent 接力或跨切面任务统一暂存至根目录 `.task.md`（发射架机制：受 .gitignore 保护不入库，完工且门禁通过后即删），用户确认后开工。
2. **原子切片（Task Stacks）**：自底向上分层实现（model/data → ViewModel → UI），单次提交单一目的；中间态用 `.\tools\quick-check.ps1` 秒级自查红线。
3. **自愈验证（Autonomous Loop）**：编码完成自主运行 `.\tools\jgate.ps1`，若未通过必须自行抓取日志修复直至全绿，严禁让用户试跑。
4. **经验固化（Flywheel 飞轮）**：攻克非直观隐蔽缺陷后执行《MiniBgm 飞轮协议》（Encode lessons into structure，直接固化入 `ArchitectureRulesTest`、`AGENTS.md` 或 `.agents/skills/minibgm-playbooks`，不留纯文本日记，详见 `.agents/skills/minibgm-flywheel/SKILL.md`）。

## 发布

tag 是版本唯一真源：推 `vX.Y.Z` 经 `-Pminibgm.versionName/-Pminibgm.versionCode` 注入构建；发布门禁在 release.yml（缺签名密钥直接失败）。勿为发版手改 build-logic 的默认版本——那是 v0.2.8 事故的根源。
