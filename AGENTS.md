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
bash tools/jgate    # 素材体积 → Rust 门禁（触及 crates/ 时）→ spotlessApply → 架构红线 → 触及模块测试（含 :app 单测）→ :app:assembleDebug → 结果校验
```

- 验证目标是"改动"而非"全仓库"，按改动自动推断：`@` 有改动验工作副本，`@` 干净退到 `@-`（`jj commit` 之后复跑）；触及基础数据层（`:core:model/common/data/network/database/datastore`）、`build-logic/`、`gradle/`、根构建脚本或版本目录 → 自动提升为全模块单测 + Debug 装配；都推不出来时**明确报错**——不静默放行，也不静默全量。
- `--plan` 只打印验证计划（零副作用）；`--all` 显式全量（追加 `:app:assembleRelease` 与 `:core:testing:crapCheck`，用于 PR 前与发版）；显式传模块（`bash tools/jgate feature/user sync/work`）则跳过推断。日常提交与 `jj commit` / `jj git push` 均不跑 release。
- KMP 模块：纯 Kotlin 逻辑测试放 `commonTest`，依赖 Android 运行时/Robolectric 的放 `androidHostTest`；Android-only 模块（`:app`、`:feature:*`、`:sync:work`、`:core:designsystem`、`:core:navigation`、`:core:webview`）用 `testDebugUnitTest` 且没有 allTests。NO-SOURCE 空跑由 jgate 的源码与 XML 双向校验拦下。全量 `allTests testDebugUnitTest` 用于底层跨切面改动与 PR 前。
- 素材体积门：改动涉及的图片单张 > 200 KB 或合计 > 1 MB 直接拦下。截图一律压成 800px 宽的 WebP 再提交，转完删原图。**不要指望 git pre-commit 钩子**——jj 不运行钩子。
- 模块依赖边变化后跑 `./gradlew graphUpdate` 重建各 README 依赖图。
- Android Lint 增量门禁：基线在各模块 `lint-baseline.xml`，CI 只拦新增；勿手工编辑基线。
- Rust ECH 原生库（`:core:network` + `crates/minibgm-ech`）：任何 Android 打包都需要 Rust 工具链 + NDK；`libminibgm_ech.so` 是构建产物，**缺库一律硬失败**，禁止静默回退 CIO。环境要求、NDK 解析与排障见 `crates/minibgm-ech/README.md`。

## 硬红线

机械执行：源语义由 `:core:testing` 的 ArchitectureRulesTest，依赖图由 build-logic 的 ModuleBoundaryConventionPlugin（配置期断言）。此处只留索引，细节看测试：

1. feature 互不依赖；UI 只经 `:core:data` 仓库；`:core:model` 纯 Kotlin；feature 无原始 IO、无颜色字面量、不外露可变状态（MutableStateFlow 一律禁止；对外只暴露只读 StateFlow，或 Compose State 持有者的只读类型——多字段原子更新用 `Snapshot.withMutableSnapshot`，不用 `update { it.copy(…) }`）；路由密封于 `BgmRoute` 并声明堆叠层。
2. 瞬态交互收敛：UI 确认/交互浮层统一由 `BgmOverlayHost` + `overlayHostState.await(...)` 挂起承接，严禁在 feature 中写散落的 `var showDialog` 状态机；通用登录提示收口于 `BgmLoginPromptDialog`；ViewModel 一次性事件（Channel(BUFFERED)）严禁裸 `collect`，必须经 `ObserveAsEvents` 绑定生命周期（STARTED）。
3. AI 只存在于助手会话；可播地址只来自 `findPlayableSources`；一切写操作止于 PENDING_CONFIRMATION 提案卡，用户确认才落库。
4. 凭据只存在于 `AuthTokensDataSource`；不绕过/削弱 `BgmPkce`；不剥离或覆盖 `User-Agent`；关键写操作包 `withContext(NonCancellable)`。

## 领域规则

- 装饰性功能 fail-open：光晕/动效/横幅等内部异常一律 `runCatching` 降级为"没有装饰"，绝不崩宿主页面；读硬件位图像素前先 `copy` 成软件位图。
- 排期真值只来自 AniList 已验证播出事件；禁止算术预测与合成剧集；bangumi-data 仅作元数据与平台映射，不得污染官方播出时间。
- 复用 `BgmHttpClient.jsonConfig`，不手写 `Json {}`（模块内白名单见 ArchitectureRulesTest）。
- Ktor 401 自动刷新；刷新彻底失败自动登出——调用方永远不会看到 401。

## 规范先例

新模式的唯一入口，不适配就停下提决策，勿发明新模式：只读特性 → `:feature:schedule`；带参详情 → `:feature:subject`；用户触发写操作 → `CollectionRepository`；认证 → `AuthRepository` + `BgmPkce`；测试形态 → 对应 `*ViewModelTest`；新红线 → 先写 ArchitectureRulesTest。

## 发布

tag 是版本唯一真源：推 `vX.Y.Z` 经 `-Pminibgm.versionName/-Pminibgm.versionCode` 注入构建；发布门禁在 release.yml（缺签名密钥直接失败）。勿为发版手改 build-logic 的默认版本——那是 v0.2.8 事故的根源。
