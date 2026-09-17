# Codebase State Canvas

把「代码结构 / 变更过程 / 验证结果」三层事实画到同一张可缩放画布上，并且**每一条结论都带证据与可信度标注**。

它不是又一个无限画布代码地图。差异化在五点：

1. **看得见隐式依赖**。`:feature:*` 的 build 文件里没有任何依赖声明，5 个 core 依赖是
   `build-logic/convention/AndroidFeatureConventionPlugin.kt:15-19` 注入的。只解析模块自己的
   `build.gradle.kts` 会得出「feature 模块互不相干」的错误结论。
2. **区分生产作用域与测试作用域**。KMP/AGP-KMP 模块把依赖写在
   `commonMain.dependencies {}` / `matching { it.name == "androidHostTest" }.configureEach { dependencies {} }`
   这类嵌套块里。同一行 `implementation(project(...))` 在不同块里含义完全不同——不区分就会把测试依赖
   算成生产依赖，还会凭空造出一个 `:core:data → :core:testing → :core:data` 的假依赖环。
3. **影响面按可信度分档**：构建级直接（确定）/ 构建级传递（间接）/ 符号级（推断），绝不混着说。
4. **验证结果接的是 Gradle 的真实回答**。区分 `EXECUTED` 与 `UP-TO-DATE`：后者意味着 Gradle
   根本没重跑。「构建成功」不等于「这次验证过」。
5. **做不到的层留空并写清要接什么**。运行状态层与 Agent 层不编数据。

## 快速开始

```bash
# 1. 扫描（仓库根目录，零依赖，CPU 约 1 秒）
python3 tools/codebase-canvas/scan.py .

# 2. 起本地服务（源码查看需要 HTTP，file:// 下浏览器读不到本地文件）
python3 -m http.server 8765

# 3. 打开
#    http://localhost:8765/tools/codebase-canvas/index.html
```

跑一次 Gradle 验证，让它进画布：

```bash
./tools/codebase-canvas/verify.sh :core:testing:testAndroid
./tools/codebase-canvas/verify.sh spotlessCheck :core:testing:testAndroid
./tools/codebase-canvas/verify.sh :feature:assistant:testDebugUnitTest --rerun
python3 tools/codebase-canvas/scan.py .        # 刷新画布数据
```

数据质量自检（34 个用例，含两个历史事故的回归）：

```bash
python3 tools/codebase-canvas/test_scan.py            # 全量，约 80 秒
python3 tools/codebase-canvas/test_scan.py --fast     # 只跑纯解析用例，0.1 秒
```

`test_scan.py` 不是测功能，是测**数据的可信度**：声明行号是否落在真实文件范围内、隐式依赖边是否
仍然被抽出来、测试作用域是否仍被正确区分、影响面跳数分档是否正确、变更行号是否真的来自 git hunk。

## 变更 → 影响 → 验证

点顶栏「变更影响」（或按 `V`、`[` `]`、点底部时间轴），画布进入变更视图：

- **图上三档着色**：改动模块（红）/ 构建级直接受影影响（橙，确定）/ 传递受影响（黄，间接）/ 其余淡出
- **右侧三段**：
  - ① 修改了什么 —— 文件清单 + `git diff` hunk 范围与声明行号的交集（带 `file:line`）
  - ② 影响了什么 —— 构建级直接（确定，带依赖声明与来源行）/ 传递（间接）/ 符号级受影响测试（推断）
  - ③ 验证了什么 —— 变更之后哪些模块真的被 Gradle 验证过、哪些没有

变更集同时覆盖**工作区未提交**与**最近若干提交**：提交掉之后就再也看不到「这次提交影响了什么」，
所以两者都要有。

### 影响面为什么分三档

| 档位 | 依据 | 可信度 |
| --- | --- | --- |
| 构建级直接受影响（1 跳） | 依赖来自构建脚本（含 convention 注入） | 确定。Gradle 会真的重跑这些模块的任务 |
| 构建级传递受影响（≥2 跳） | 反向依赖闭包 | 间接。`implementation` 不传递到消费方的编译类路径，只能算「可能受影响」 |
| 符号级受影响测试 | 函数体标识符与全局声明名取交集 | 推断。同名遮蔽、扩展函数、反射会漏 |

**不会**因为「测试文件名里含被改模块名」就说它受影响。

### 验证覆盖的判定规则

只有一条，且只用实测数据：**验证记录的时间戳 ≥ 变更的时间戳**，且这次验证里确实出现了受影响模块的任务。

- 工作区变更的时间戳 = 被改动文件里最新的 mtime（文件真的被写过）
- 提交的时间戳 = git committer time

时间对不上就是「没验证过」，不拿「代码能编译」当验证。任务名按优先级选取：
命令行请求的验证任务 > 公认验证任务（`testDebugUnitTest` / `testAndroid` / `spotlessCheck` / `crapCheck` / `assembleDebug`…）> 构建流水线内部任务。
只有流水线任务时会明确标注「仅构建流水线」，而不是把它当成验证。

## 五个图层

| 图层 | 现在有什么 | 证据强度 |
| --- | --- | --- |
| 结构 | 18 模块 / 267 文件 / 643+ 声明 / 82 条依赖边（46 显式 + 36 隐式）、层分布、上下游 | 实测 |
| 过程 | 工作区 + 最近若干提交的变更集、每个改动定位到声明、每模块提交数与活跃度 | 实测 |
| 证据 | 每模块真实用例数、失败数、`TEST-*.xml` 路径、最近执行时间、11 条架构规则命中 | 实测 |
| 验证 | `verify.py` 记录的每次 Gradle 调用、任务级结果、覆盖判定 | 实测 |
| 运行 / Agent | 运行层只有「最近一次真实执行时间」；Agent 层读 `out/agent-trace.json`，不存在就留空 | 部分 / 未接入 |

运行层与 Agent 层刻意留空而不是编数据：要填满它们需要埋点（Kermit sink → JSONL、WorkManager 的
worker 状态、agent 侧写出 tool 调用轨迹），静态扫描回答不了。

### Agent 层接口

```json
{
  "events": [
    {
      "type": "tool", "tool": "Edit",
      "summary": "把 CollectionRepository 的 check-in 改为 NonCancellable 包裹",
      "at": "2026-09-17T14:20:00+08:00",
      "modules": [":core:data"]
    }
  ]
}
```

放到 `tools/codebase-canvas/out/agent-trace.json`，刷新即渲染，`modules` 里的模块会被套上紫色虚线框。

## 规则检查（对齐 AGENTS.md 的硬红线）

| 规则 | 内容 |
| --- | --- |
| R1 | `:feature:A` 不得依赖 `:feature:B`（生产作用域） |
| R2 | feature 不得直接依赖 `:core:network` / `:core:database` / `:core:datastore`，源码不得 import `io.ktor.*` / `androidx.room.*` |
| R3 | `:core:model` 不得出现 `android.*` / `androidx.*` import |
| R4 | ViewModel 不得暴露非 private 的 `MutableStateFlow` |
| R5 | feature 内不得硬编码 `Color(0x...)` |
| R6 | feature 内不得出现裸 IO（`HttpURLConnection` / `java.net.URL` / `context.cacheDir` / `FileOutputStream` …） |
| R7 | 测试源集不得命名为 `androidUnitTest`（会静默零用例） |
| R8 | 模块零测试文件 / 有测试文件但无执行证据 / 测试任务跑了 0 用例 |
| R9 | feature 模块未被 `:app` 接线 |
| R10 | 模块依赖存在环（只看生产作用域） |
| R11 | 生产作用域依赖了测试专用模块 |

## 诚实的边界

- **静态解析，不是编译**。声明抽取、层分类、引用解析基于正则与符号名，不调 Kotlin 编译器、不跑 Gradle。
  同名遮蔽、扩展函数分发、反射、KSP 生成代码会漏。
- **引用图不是调用图**。`它引用了` / `被引用` 来自函数体标识符与全局声明名取交集，一律标为 `推断`。
- **影响面不是「一定需要改」**。构建级直接受影响说的是「Gradle 会重跑它的任务」，不是「这段代码必须改」。
- **没有运行时**。线程、网络请求、内存、后台任务状态需要埋点，静态扫描回答不了。
- **Git 数字是局部真相**。提交数按 HEAD 可达计算，文件级归属最多回溯 600 个提交。
- **JSON 里的 hunk 列表有截断**。单个文件超过 60 个 hunk、单次变更超过 40 个文件时只展示前若干条，
  并在面板上标明。
- **只解析 `.kt`**。

## 文件结构

```
tools/codebase-canvas/
├── scan.py        # 扫描器：Gradle 结构 + Kotlin 声明 + 规则检查 + git 变更 + 影响面 + 验证覆盖
├── verify.py      # 跑一次 Gradle 验证并把真实结果写进验证层
├── verify.sh      # 上面那个的薄封装，用法 ./tools/codebase-canvas/verify.sh <任务>
├── test_scan.py   # 数据质量测试（解析坑回归 + 仓库数据不变量）
├── index.html     # 画布：自绘 Canvas 2D，零依赖单文件
├── README.md
└── out/           # 产物（已 gitignore）
    ├── canvas-data.json / canvas-data.js
    ├── validation-log.jsonl        # 每次 Gradle 验证一行
    └── validation/<时间戳>.log      # 原始 Gradle 输出
```

`scan.py` 是唯一的数据来源，`index.html` 只消费数据、不做任何推断。
`index.html` 里的 `window.canvasInternals` 是给自动化验证用的只读自省接口（返回节点屏幕坐标、
影响分档、验证记录等），不改变任何状态。
