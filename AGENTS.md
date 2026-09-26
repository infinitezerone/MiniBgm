# MiniBgm AI Agent & Developer Guidelines

MiniBgm：Bangumi（bgm.tv）追番排期与收藏管理客户端。模块化 Clean Architecture，仿 Google *Now in Android*。本文只写"删了会改变行为"的内容；细节一律以代码、测试与钩子为准。

## 版本控制

仓库与 jj colocate：**历史操作只用 jj**；git 只读（log/diff/show/ls-remote/tag/clone）。git 写子命令与不规范的 jj commit 摘要会被仓库的命令门禁（`tools/hooks/project-gate.mjs`）直接拦截；git 层另有 pre-commit/pre-push 硬拒（新 clone 跑一次 `tools/githooks/setup.sh`，jgate 会自愈）——执法在 VCS/CI 层，工具钩子只是快速反馈。规则在门禁里，本文件不复述。

- 一个提交一个目的；同一文件别混两个目的——事后 `jj split` 拆不出可编译的中间态。
- 同一时刻只允许一条 Gradle 命令在跑（长构建后台 + 足够超时，避免 daemon 互踩）。
- push 由人执行或逐次授权；push 侧有独立的安全扫描与 CI 重验，是最后的人工检查点。
- OAuth 令牌交换/刷新代理在独立的私有 Cloudflare Workers 项目，本仓库搜不到；业务 API 直连 api.bgm.tv。

## 构建与验证

提交前唯一入口（取代手工四件套，含"绿≠测过"的 XML 真实性校验）：

```bash
bash tools/jgate    # spotlessApply → 架构红线 → 触及模块测试 → :app:assembleDebug → 结果校验
```

- KMP 模块测试源集是 `androidHostTest` / `androidDeviceTest` 且 host 测试 opt-in；Android-only 模块（`:app`、`:feature:*`、`:sync:work`、`:core:designsystem`、`:core:navigation`）只有 `testDebugUnitTest`。命名错误的源集会静默空跑——jgate 的 XML 校验（`tests > 0 && failures == 0`）负责拦。
- 全量 `allTests testDebugUnitTest` 仅用于跨切面改动（build-logic / 版本目录 / `:core:model` / `:core:common`）及 PR 前；Android-only 模块没有 allTests。
- 模块依赖边变化后跑 `./gradlew graphUpdate` 重建各 README 依赖图。
- Android Lint 增量门禁：基线在各模块 `lint-baseline.xml`，CI 独立 job 只拦新增；修复代码后基线残留无害，勿手工编辑基线。

## 硬红线

机械执行：源语义由 `:core:testing` 的 ArchitectureRulesTest，依赖图由 build-logic 的 ModuleBoundaryConventionPlugin（配置期断言）。此处只留索引，细节看测试：

1. feature 互不依赖；UI 只经 `:core:data` 仓库；`:core:model` 纯 Kotlin；feature 无原始 IO、无颜色字面量、不外露 MutableStateFlow；路由密封于 `BgmRoute` 并声明堆叠层。
2. AI 只存在于助手会话；可播地址只来自 `findPlayableSources`；一切写操作止于 PENDING_CONFIRMATION 提案卡，用户确认才落库。
3. 凭据只存在于 `AuthTokensDataSource`；不绕过/削弱 `BgmPkce`；不剥离或覆盖 `User-Agent`；关键写操作包 `withContext(NonCancellable)`。

## 领域规则

- 装饰性功能 fail-open：光晕/动效/横幅等内部异常一律 `runCatching` 降级为"没有装饰"，绝不崩宿主页面；读硬件位图像素前先 `copy` 成软件位图。
- 排期真值只来自 AniList/Bilibili 已验证播出事件；禁止算术预测与合成剧集；bangumi-data 仅作元数据与平台映射，不得污染官方播出时间。
- 复用 `BgmHttpClient.jsonConfig`，不手写 `Json {}`（模块内白名单见 ArchitectureRulesTest）。
- Ktor 401 自动刷新；刷新彻底失败自动登出——调用方永远不会看到 401。

## 规范先例

新模式的唯一入口，不适配就停下提决策，勿发明新模式：只读特性 → `:feature:schedule`；带参详情 → `:feature:subject`；用户触发写操作 → `CollectionRepository`；认证 → `AuthRepository` + `BgmPkce`；测试形态 → 对应 `*ViewModelTest`；新红线 → 先写 ArchitectureRulesTest。

## 发布

tag 是版本唯一真源：推 `vX.Y.Z` 经 `-Pminibgm.versionName/-Pminibgm.versionCode` 注入构建；发布门禁在 release.yml（缺签名密钥直接失败）。勿为发版手改 build-logic 的默认版本——那是 v0.2.8 事故的根源。
