# 片源嗅探与规则机制调研

> **用途**：回答"从第三方播放站点拿到可播直链这件事，成熟项目到底怎么做、我们该走哪条路"。
> 与分工相关的约定：约束与红线在 [AGENTS.md](../AGENTS.md)，未来待做在 [ROADMAP.md](../ROADMAP.md)，
> 踩坑复盘在 [PROBLEM_LOG.md](PROBLEM_LOG.md)；本文只放**外部方案调研与选型结论**。
>
> **合规声明**：本文与仓库其它内容一样，不包含任何真实播放站点名、域名、URL 或针对某站的定制描述
> （AGENTS.md 红线 9 同源约束）。凡引用第三方项目，只指向其主仓库/官方文档根路径。
>
> **标注约定**：【事实】= 已查证原文；【推断】= 由机制常识与二手资料推得，未逐行验证。

## 结论先说

1. 所有"主仓库不含站点知识 + 更新不发版"的项目，最终都收敛到同一分工：**声明式规则做浅层，脚本/字节码兜底深层**。
2. 声明式 DSL 的天花板是**控制流**。一旦需要"从一页候选里挑出正确条目"或循环展开，各家做法不是给 DSL 加
   循环语法，而是**把阶段做成引擎内建语义**（搜索/详情/分集/播放各自的规则，引擎负责逐条展开与择优）。
3. 我们的"AI 生成规则 + 用户确认卡"这条形态，在检索范围内**没有同构开源先例**（最接近的 AI 抓取器产出的是
   代码）。这是差异化优势，前提是补上"生成成功 ≠ 能播"的验证门。
4. 播放失效的检测与归因**没有可抄的先例**（各家靠 issue 人肉上报 + 维护者重发规则）。我们已有失败归因存储，
   这条能做成我们的能力，不必找参照。

## 一、成熟嗅探的四条通道

四条不是备选关系，成熟客户端是叠着用的，按"覆盖率 / 成本"递增：

### 1. 被动网络层嗅探（覆盖率最高）

宿主起一个 WebView 加载播放页，让页面自己的 JS 去算签名与 token，宿主只挂在请求回调上抓真实 URL。

- 【事实】TVBox/Cat 系的配置里这一层是显式字段：`rules`（按 host 命中 + 正则从真实请求中抽直链）、
  `sniff`（关键字黑名单、超时、并发数）、`click`/`clickSelector`（需要模拟点击才发请求的站点由框架点）。
- 【推断】浏览器扩展类嗅探器同理：判定依据不是"页面源码里有没有 mp4 字样"，而是"实际发出的请求里哪个像媒体"
  ——后缀 + `Content-Type` + 是否支持 `Range` + 首包体积/magic bytes，并在多个候选里挑存活最久/最大的那条。
- **对我们的意义**：我们已有 WebView 捕获通道，但缺"媒体判定表 + 多候选打分 + 超时兜底"。这是**投入最小、覆盖提升
  最大**的一步，因为它绕过了"表达站点逻辑"这件事本身。

### 2. 主动声明式抽取

- 【事实】Kazumi 用 XPath 规则，并把规则按阶段分层（搜索 / 订阅 / 详情 / 播放各有字段），规则数据放独立仓库，
  App 只做渲染与执行。
- 【事实】TVBox 用 `playerUrl`/`ext`/`type` 加 `spider` 组合；简单站靠声明式，复杂站转脚本（见第 3 条）。
- 【事实】CloudStream 3 除编译插件外还有 script provider：用户导入 `.js`/`.json`/`.zip`，跑在 QuickJS 上，
  JS 里能 fetch、解析 DOM、正则。
- **对我们的意义**：我们的 `PipelineStep`（FETCH / EXTRACT_VARIABLE / EXTRACT_STREAM）已经在做这件事，缺的是
  **列表语义**：让一步返回多条候选，由引擎按片名+话数选、失败换下一条。补法要克制——加排序/条件/循环等于
  重造语言，那正是别人撞墙转 JS 的位置。

### 3. 外部解析服务

客户端把播放页地址交给一个解析接口，换回直链 + 请求头。【事实】TVBox 的 `parses`（`type:0` 嗅探、`type:1` JSON
解析）就是这一层；【事实】Piped/Invidious 更进一步：抽取与流媒体代理都在服务端，客户端只拿结果。

- **对我们的意义**：我们已经有跑在 Workers 上的 token 代理，把抽取放服务端在基础设施上是对称的。
- **代价要说清**：一旦服务端做抽取，站点知识与 DMCA 责任主体从"公开仓库里的字符串"变成"你在运营的接口"，
  风险等级高于现状。**不建议默认采用**，需要单独决策。

### 4. 播放器侧交接

- 【事实】Media3 的 `DefaultHttpDataSource.setDefaultRequestProperties` 会把头带到每个分片请求（我们已是这么做的），
  副作用是头变化需重建播放器实例、重新 prepare。
- 【推断】mpv 生态的常见替代是本地反向代理：播放器只拿 `127.0.0.1` 的一次性 URL，真实 Referer/Cookie 留在代理里
  （等价于 `--http-header-fields`/`--cookie-file` 的通用化）。
- **对我们的意义**：除了减少播放器重建，还顺手消掉"会话 Cookie 作为导航参数进入 saved-state"这一路隐患。

## 二、同类项目对照

| 项目 | 站点知识住在哪 | 表达形式 | 列表选择能力 | 改规则要发版 |
|---|---|---|---|---|
| Aniyomi / Katana | 独立扩展仓库 | 编译 Kotlin → 扩展 APK，`DexClassLoader` 加载 + 仓库签名校验【事实】 | 完整（就是代码） | 否 |
| CloudStream 3 | 编译插件 + 用户导入脚本 | 代码 / QuickJS 脚本【事实】 | 是（JS 内可搜索、挑集、循环） | 否 |
| TVBox / Cat 系 | 用户添加的订阅 JSON + 远程 jar/JS spider | 配置 + 字节码【事实】 | 简单站否，spider 是 | 否 |
| Miru（旧版 Kotlin） | 随 App 走 | 声明式选择器 + 正则【推断】 | 弱 | 是 |
| miru-app（继任 Flutter） | 独立扩展仓库 | JS 扩展【事实】 | 是 | 否 |
| Taiga | 主仓库（编译代码）+ 用户可编辑识别规则 | 代码 + 规则【事实/部分推断】 | 有限 | 是 |
| yt-dlp（反面） | 主仓库每站一个 extractor，全进 git 历史【事实】 | 代码 | 完整 | 是 |

**反面教训**【事实】：youtube-dl 因 RIAA 投诉被 GitHub 整仓下架过，后由 EFF 介入恢复。把站点抽取代码集中在公开仓库
+ 写进历史，等于把证据链固化到最难撤回的位置。这正是我们要避开的形态。

## 三、规则治理与失败闭环（可借鉴度最高的部分）

1. **导入即验证**【事实】：Kazumi 在保存规则前强制跑一次"规则测试"，断言搜索结果数量、线路数、剧集顺序，
   不通过不允许保存。→ 对应我们的缺口：`testPlaybackRule` 目前只回答"规则跑得通吗"，不回答"抽到的确实是这部剧、
   而且真能播"。
2. **版本与能力门控**【事实】：KazumiRules 由 CI 生成 index，功能按 API Level 与客户端版本门控（需要 POST 能力、
   需要 Referer、需要 HLS 去广告各属不同级别）。→ 我们的规则 JSON 现在既无 `ruleVersion` 也无 `minClientApi`，
   将来加原语会让旧客户端静默执行新语义。
3. **运行期失效检测**【事实】：各家主要靠 issue 人肉上报 + 维护者重发规则；Kazumi 有多线路并列但未见自动归因。
   → 我们已有播放失败归因存储，可以做"按规则累计连续失败 → 选源 UI 降权/标灰"，这在同类里是稀缺能力。
4. **LLM 产出防幻觉**【事实】：Google LangExtract 的核心是 grounding——每个抽取字段必须锚定原文字符区间。
   → 可用于规则提案：要求正则回显命中上下文，而不是只给一个 URL 让下游信。

## 四、能力边界（不自欺的部分）

在"仓库零站点知识 + 不引入脚本引擎"的前提下拿不到的形态：

- **运行时 JS 计算签名/token** 且算法不在 URL 里也不在响应头里（必须执行页面 JS）。当前唯一不引入引擎的解法是
  第 1 通道的 WebView 被动嗅探——用真实浏览器引擎替我们执行，代价是慢、要超时与判定。
- **需要复杂交互**（多次点击、验证码、播放器套壳多层跳转且每层需不同 header）：声明式表达不了，扩展 DSL 会滑向
  造语言；这类只能靠脚本引擎或外部解析，两者都各自带新的责任主体。
- **加密/签名播放列表内嵌套段地址**：抓到 master m3u8 也拿不到分片，除非代理改写清单。

## 五、建议落地次序

1. **规则分阶段 + 列表语义**：让步骤能返回候选列表，把"按片名与话数选条目"变成引擎固定行为（同时消掉现有
   "按话数猜号"的撞号风险）。
2. **试解析断言门**：提案卡片必须携带一次真请求的结论（首包 2xx/206、`Content-Type` 或 magic bytes 像媒体、
   候选数达标），断言不过就不出"确认导入"。
3. **WebView 嗅探补判定表与候选打分**：后缀 / 类型 / Range / 首包体积 / 存活时长，加关键字黑名单与超时。
4. **规则健康度**：连续失败计数 → 选源 UI 降权，并纳入架构门禁测试防回归。
5. **站点硬编码退场**：以上具备后，把针对具体站点的定制嗅探降级为用户可导入的一条规则并删除代码；顺序不可颠倒，
   否则用户能力先回落。

两个独立决策，不并入上面任何一步：**本地代理做凭据交接**（收益是可播性与隐私，成本是多一个组件）；
**服务端解析**（收益是覆盖率，成本是责任主体转移）。

## 六、出处

- [Kazumi 主仓库](https://github.com/Predidit/Kazumi) · [规则开发文档](https://kazumi.app/docs/rules/develop-rules) · [KazumiRules 规则仓库](https://github.com/Predidit/KazumiRules)
- [TVBox 社区配置仓库](https://github.com/qist/tvbox) · [配置 JSON 结构解析](https://blog.csdn.net/zhiyuan411/article/details/141289555) · [Java 爬虫机制说明](https://www.bilibili.com/opus/886910823939702792)
- [CloudStream 主仓库](https://github.com/recloudstream/cloudstream) · [扩展仓库机制说明](https://gist.github.com/redtrillix/17a1d8eda270b6db831b85a860c0fcd0)
- [aniyomi-extensions](https://github.com/aniyomiorg/aniyomi-extensions) · [Mihon](https://github.com/mihonapp/mihon)
- [miru-app（JS 扩展与自定义仓库）](https://github.com/miru-project/miru-app)
- [EFF：youtube-dl 的 DMCA 下架与恢复](https://www.eff.org/deeplinks/2020/11/github-reinstates-youtube-dl-after-riaas-abuse-dmca)
- [LangExtract（grounding 式抽取）](https://developers.googleblog.com/introducing-langextract-a-gemini-powered-information-extraction-library/)
