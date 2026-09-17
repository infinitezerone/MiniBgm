# Codebase State Canvas

把「代码结构 / 变更过程 / 验证证据」三层事实画到同一张可缩放画布上，并且**每一条结论都带证据**。

它不是又一个无限画布代码地图。差异化在四点：

1. **看得见隐式依赖**。`:feature:*` 的 build 文件里没有任何依赖声明，5 个 core 依赖是
   `build-logic/convention/AndroidFeatureConventionPlugin.kt` 注入的。只解析模块自己的 `build.gradle.kts`
   会得出「feature 模块互不相干」的错误结论。本工具把这类边也画出来，并标注来源行。
2. **区分生产作用域与测试作用域**。KMP/AGP-KMP 模块把依赖写在
   `commonMain.dependencies {}` / `matching { it.name == "androidHostTest" }.configureEach { dependencies {} }`
   这类嵌套块里。同一行 `implementation(project(...))` 在不同块里含义完全不同——不区分就会把测试依赖算成生产依赖，
   还会凭空造出一个 `:core:data → :core:testing → :core:data` 的假依赖环。
3. **证据强度分级**。每个结论标注 `实测`（文件系统 / git 对象 / Gradle 结果 XML）或
   `推断`（符号名静态解析）。做不到的层（运行状态、AI 工作过程）**留空并写清要接什么**，不编数据。
4. **测试证据来自真实执行结果**。用例数、失败数、最近一次执行时间读自 `build/test-results/<task>/TEST-*.xml`，
   不是「有多少 `@Test` 注解」的估算。`BUILD SUCCESSFUL` 不等于测过。

## 快速开始

```bash
# 1. 扫描（在仓库根目录）
python3 tools/codebase-canvas/scan.py .

# 2. 起本地服务（源码查看需要 HTTP，file:// 下浏览器读不到本地文件）
python3 -m http.server 8765

# 3. 打开
#    http://localhost:8765/tools/codebase-canvas/index.html
```

扫描器零依赖（只用 Python 标准库），耗时约 19 秒（主要是 git 子进程），产出：

| 文件 | 说明 |
| --- | --- |
| `out/canvas-data.json` | 完整数据，可被其它工具消费 |
| `out/canvas-data.js` | 同一份数据包成 `window.CANVAS_DATA`，供画布在 `file://` 下也能加载 |

## 怎么读这张画布

- **缩放即换粒度**：L1 项目全景 → L2 模块与责任 → 双击模块进入 L3 文件/类/函数 → 选中声明进入 L4 调用链。
- **点击**模块看构成与依赖证据；**双击**下钻；**滚轮**缩放；**拖拽**平移；**Esc** 返回；**0** 适应窗口；**⌘K** 搜索。
- **顶栏五个图层开关**控制叠加哪些真实数据：

| 图层 | 现在有什么 | 证据强度 |
| --- | --- | --- |
| 结构 | 18 模块 / 643 声明 / 82 条边（46 显式 + 36 隐式）、层分布、上下游 | 实测 |
| 过程 | 每模块提交数、近 90 天活跃度、工作区未提交文件、30 个提交的时间轴与影响面 | 实测 |
| 证据 | 每模块真实用例数、失败数、`TEST-*.xml` 路径、最近执行时间、11 条架构规则的命中结果 | 实测 |
| 运行 | 只有「最近一次真实执行时间」（test-results 目录时间戳） | 部分 |
| Agent | 未接入。读 `out/agent-trace.json`，文件存在就渲染，不存在就留空 | 未接入 |

底部时间轴（过程层打开时出现）点一格，就能看到那次提交动了哪些模块、各几个文件。

## 接口：agent-trace.json

Agent 层刻意不拿 git 提交冒充 AI 工作记录。要让这一层有内容，把轨迹写成：

```json
{
  "events": [
    {
      "type": "tool",
      "tool": "Edit",
      "summary": "把 CollectionRepository 的 check-in 改为 NonCancellable 包裹",
      "at": "2026-09-17T14:20:00+08:00",
      "modules": [":core:data"]
    }
  ]
}
```

放到 `tools/codebase-canvas/out/agent-trace.json`，刷新即渲染，并且画布会给 `modules` 里列出的模块套上紫色虚线框。

## 规则检查（对齐 AGENTS.md 的硬红线）

扫描器逐条跑，命中就带 `文件:行` 与代码片段列出来：

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
| R9 | feature 模块未被 `:app` 接线（代码可能进不了产物） |
| R10 | 模块依赖存在环（只看生产作用域，测试源集不算） |
| R11 | 生产作用域依赖了测试专用模块（测试替身会被打进产物） |

当前 MiniBgm 仓库跑出 **0 条命中**——这是逐条跑完的实测结果，不是「跳过检查」。

## 诚实的边界

- **静态解析，不是编译**。声明抽取、层分类、引用解析都基于正则与符号名，不调用 Kotlin 编译器、不跑 Gradle。
  同名遮蔽、扩展函数分发、反射、KSP 生成代码会漏。
- **引用图不是调用图**。`它引用了` / `被引用` 来自函数体标识符与全局声明名取交集，标为 `推断`。
  它的价值在于回答「这个类被哪些**测试**引用」（例如 `SubjectDetailViewModel` 被
  `SubjectDetailViewModelTest` 与 `subjectModule` 引用），而不是替代真实的调用链追踪。
- **没有运行时**。线程、网络请求、内存、后台任务状态需要埋点（Kermit sink → JSONL、WorkManager 状态查询、
  `Debug.startMethodTracing`），静态扫描回答不了。
- **Git 数字是局部真相**。提交数按 HEAD 可达计算，`git log --oneline` 最多回溯 600 个提交用于文件级归属。
- **JS/TS/其它语言未支持**。当前只解析 `.kt`。

## 文件结构

```
tools/codebase-canvas/
├── scan.py          # 扫描器：Gradle 结构 + Kotlin 声明 + 规则检查 + git + 测试证据 + 引用图
├── index.html       # 画布：自绘 Canvas 2D，零依赖单文件
├── README.md
└── out/
    ├── canvas-data.json
    └── canvas-data.js
```

`scan.py` 是唯一的数据来源，`index.html` 只消费数据、不做任何推断。
