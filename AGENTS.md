# MiniBgm AI Agent & Developer Guidelines

MiniBgm：Bangumi（bgm.tv）追番排期与收藏管理客户端。模块化 Clean Architecture，仿 Google *Now in Android*。本文只写"删了会改变行为"的内容；细节一律以代码、测试与钩子为准。

## 版本控制

仓库与 jj colocate，历史操作用 `jj commit` / `jj git push`；提交摘要建议遵循 Conventional Commits（如 `feat(core:ai): xxx` / `chore: xxx`）。git 只读（log/diff/show/ls-remote/tag/clone）——git 写子命令由 VCS 层的 `tools/githooks`（pre-commit / pre-push 硬拒）拦下，新 clone 跑一次 `tools/githooks/setup.sh` 接线，`tools/jgate` 会自愈补配；jj 不运行 git 钩子，与合法路径零冲突。

- 一个提交一个目的；同一文件别混两个目的——事后 `jj split` 拆不出可编译的中间态。
- 同一时刻只允许一条 Gradle 命令在跑（长构建后台 + 足够超时，避免 daemon 互踩）。
- OAuth 令牌交换/刷新代理在独立的私有 Cloudflare Workers 项目，本仓库搜不到；业务 API 直连 api.bgm.tv。

## 构建与验证

提交前唯一入口（取代手工四件套，含"绿≠测过"的 XML 真实性校验）：

```bash
bash tools/jgate    # 素材体积 → Rust 门禁（触及 crates/ 时）→ spotlessApply → 架构红线 → 触及模块测试（含 :app 单测）→ :app:assembleDebug → 结果校验
```

- 验证目标是"改动"而非"全仓库"，按改动自动推断：工作副本 `@` 有改动 → 验证工作副本；`@` 干净则退到最近一次提交 `@-`（`jj commit` 之后复跑）；改动触及基础数据层（`:core:model`、`:core:common`、`:core:data`、`:core:network`、`:core:database`、`:core:datastore`）、`build-logic/`、`gradle/`、根构建脚本或版本目录 → 具有向下扩散性，自动提升为全量。都推断不出来时**明确报错**——既不静默放行（假绿），也不静默全量（慢）。
- `bash tools/jgate --plan` 只打印本次的验证目标、模块与 gradle/Rust 任务（零副作用）；`--all` 强制全量（自动追加 `:app:assembleRelease` 与 `:crapCheck`，与 CI 标准完全闭环）；显式传模块（`bash tools/jgate feature/user sync/work`）则跳过推断。

- KMP 模块纯 Kotlin 领域逻辑测试位于 `commonTest`（如 `:core:model`、`:core:common`、`:core:ai`）；依赖 Android 运行时/Robolectric 的宿主测试位于 `androidHostTest`（如 `:core:data`、`:core:database`、`:core:datastore`、`:core:testing`）。Android-only 模块（`:app`、`:feature:*`、`:sync:work`、`:core:designsystem`、`:core:navigation`、`:core:webview`）使用 `testDebugUnitTest`。命名错误或未挂载导致任务 NO-SOURCE 空跑由 jgate 的测试源码与 XML 存在性双向校验拦下。
- 全量 `allTests testDebugUnitTest` 用于底层跨切面改动、PR 前及 CI 主干；Android-only 模块没有 allTests。
- **素材体积门（jgate 第 1 步）**：改动涉及的图片单张 > 200 KB 或合计 > 1 MB 直接拦下。原因是截图会**不可逆**地撑大仓库历史——git 保留每一版，一张 `screencap` 原始输出（1272×2772 PNG，280 KB～1 MB）只能靠改写历史清掉，而同样内容的 800px WebP 约 95 KB。截图一律压成 800px 宽的 WebP 再提交（README 表格里约 380px 显示宽度，2 倍图够用；转完删原图）。**不要指望 git pre-commit 钩子**——jj 不运行钩子。
- 模块依赖边变化后跑 `./gradlew graphUpdate` 重建各 README 依赖图。
- Android Lint 增量门禁：基线在各模块 `lint-baseline.xml`，CI 独立 job 只拦新增；修复代码后基线残留无害，勿手工编辑基线。
- **Rust ECH 原生库（`:core:network` + `crates/minibgm-ech`）**：`libminibgm_ech.so` 是构建产物（落在 `:core:network/build/ech-native/jniLibs`，**不再写进 `src/`**——那会撞上 `core/testing` 架构红线测试的 `core/*/src/**` 输入声明），由 `:core:network:buildEchNative` 在 jniLibs 合并前编译。因此任何 Android 打包都需要 Rust 工具链（rustup + `cargo install cargo-ndk` + NDK）；NDK 解析顺序为 `-Pminibgm.ech.ndkDir` → `ANDROID_NDK_HOME/ANDROID_NDK_ROOT` → `ANDROID_HOME/local.properties(sdk.dir)` 下最高版本。已有产物时可用 `-Pminibgm.ech.skipBuild=true` 跳过编译，但产物必须与 Rust 源码指纹一致，否则失败。**缺库一律硬失败**——静默回退 CIO 等于 ECH 在发布产物里不存在（v0.3 之前的老问题）。Rust 侧门禁：`cargo fmt --check` / `cargo clippy -D warnings` / `cargo test`（jgate 触及 `crates/` 或 `--all` 时本地执行，命令与 CI 逐字一致；另有 CI 独立 job + release 前的 Verify）。Windows 宿主需 MSVC C++ 构建工具与 NASM；若构建 aws-lc-sys 遇 `0xc0000142`（STATUS_DLL_INIT_FAILED），系目录被沙盒工具标记了低完整性级别（Low Mandatory Level），需在管理员 PowerShell 执行 `icacls . /setintegritylevel "(OI)(CI)M" /t /c /q` 恢复正常完整性级别，jgate 保持全量硬门禁。

## 硬红线

机械执行：源语义由 `:core:testing` 的 ArchitectureRulesTest，依赖图由 build-logic 的 ModuleBoundaryConventionPlugin（配置期断言）。此处只留索引，细节看测试：

1. feature 互不依赖；UI 只经 `:core:data` 仓库；`:core:model` 纯 Kotlin；feature 无原始 IO、无颜色字面量、不外露可变状态（MutableStateFlow 一律禁止；对外只暴露只读 StateFlow，或 Compose State 持有者的只读类型——多字段原子更新用 `Snapshot.withMutableSnapshot`，不用 `update { it.copy(…) }`）；路由密封于 `BgmRoute` 并声明堆叠层。
2. AI 只存在于助手会话；可播地址只来自 `findPlayableSources`；一切写操作止于 PENDING_CONFIRMATION 提案卡，用户确认才落库。
3. 凭据只存在于 `AuthTokensDataSource`；不绕过/削弱 `BgmPkce`；不剥离或覆盖 `User-Agent`；关键写操作包 `withContext(NonCancellable)`。

## 领域规则

- 装饰性功能 fail-open：光晕/动效/横幅等内部异常一律 `runCatching` 降级为"没有装饰"，绝不崩宿主页面；读硬件位图像素前先 `copy` 成软件位图。
- 排期真值只来自 AniList 已验证播出事件；禁止算术预测与合成剧集；bangumi-data 仅作元数据与平台映射，不得污染官方播出时间。
- 复用 `BgmHttpClient.jsonConfig`，不手写 `Json {}`（模块内白名单见 ArchitectureRulesTest）。
- Ktor 401 自动刷新；刷新彻底失败自动登出——调用方永远不会看到 401。

## 规范先例

新模式的唯一入口，不适配就停下提决策，勿发明新模式：只读特性 → `:feature:schedule`；带参详情 → `:feature:subject`；用户触发写操作 → `CollectionRepository`；认证 → `AuthRepository` + `BgmPkce`；测试形态 → 对应 `*ViewModelTest`；新红线 → 先写 ArchitectureRulesTest。

## 发布

tag 是版本唯一真源：推 `vX.Y.Z` 经 `-Pminibgm.versionName/-Pminibgm.versionCode` 注入构建；发布门禁在 release.yml（缺签名密钥直接失败）。勿为发版手改 build-logic 的默认版本——那是 v0.2.8 事故的根源。
