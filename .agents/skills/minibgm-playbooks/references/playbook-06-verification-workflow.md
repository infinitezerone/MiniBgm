## 剧本 6：快速反馈与最终闭环（Verification Workflow）
* **编码中间态快速自检与微验证（秒级 Micro-Eval）**：
  ```powershell
  .\tools\quick-check.ps1                        # 跑 90+ 条架构红线自动化断言
  .\tools\quick-check.ps1 -Format                # 自动执行 spotlessApply 并查红线
  .\tools\quick-check.ps1 -Modules core/network  # 跑红线 + network 模块定向单测（极速微验证）
  .\tools\quick-check.ps1 -Only core/data        # 仅测目标模块（跳过红线，极限循环）
  ```
* **提交前唯一门禁（物理锁死）**：
  ```powershell
  .\tools\jgate.ps1                              # 完整门禁（CommitLint、Rust、Spotless、红线、测试、XML校验、触及Android模块 lintDebug、assembleDebug、结构化诊断）
  .\tools\jgate.ps1 --stack                      # 验证本地变更栈（trunk()..@ 栈内逐个提交格式与全栈改动汇总）
  .\tools\jgate.ps1 --fast                       # 跳过截图测试与触及模块 lintDebug（本机减负；提交前必须复跑不带 --fast 的一次）
  .\tools\jgate.ps1 stats                        # 转速表：门禁一次通过率、分模式统计与失败阶段直方图（.jgate/history.jsonl）
  ```
  两处减负都是**自动或显式**且会在输出里标注，绝不静默：纯 `*.md`/`docs/`/`.agents/`/`.github/` 改动会自动跳过 `:app:assembleDebug`（架构红线照跑，装配门对这类改动零信息量；`native/`、`tools/` 仍跑），`--fast` 才会跳过截图测试与触及模块 lintDebug。截图测试实测占单测耗时 43%（59s/137s，15 例），省的是 Robolectric NATIVE 图形栈每个类的冷启动。门禁若有拦截，自动在 `build/reports/jgate/diagnostic.json` 产出结构化机器诊断供自愈。
* **单测配置的单一真源**：带 Robolectric/Roborazzi 的模块一律调用 build-logic 的 `configureAndroidUnitTests()`（Java 21 launcher + `-Proborazzi.record=true` 录制 + `-Pminibgm.skipScreenshots` 跳过），禁止再各自手抄 `tasks.withType<Test>` 块——三份副本曾是各自漂移的温床。
- **UI 自查无需设备（Roborazzi + Robolectric）**：改 Compose 面板/控制层后写截图测试自查，输出到 `src/test/snapshots/images/` 再读图检查。要点：模块需接 roborazzi 四件套 + `testOptions.unitTests.isIncludeAndroidResources` + Test 任务 `roborazzi.test.record=true` 与 JDK 21 launcher 由 `configureAndroidUnitTests()` 统一提供（模块侧只写这一行，实现在 `build-logic/convention/.../AndroidUnitTest.kt`；测试写法参照 `feature/subject/src/test/kotlin/com/infinitezerone/minibgm/feature/subject/player/PlayerPanelsScreenshotTest.kt`）；`@Config(qualifiers = "w420dp-h1600dp")` 控制视口尺寸（默认窗口仅 320dp 宽，横排按钮会溢出"消失"）；深色背景模拟视频底，否则白色控制图标"隐形"；LazyColumn 跨分组 items key 必须唯一。
