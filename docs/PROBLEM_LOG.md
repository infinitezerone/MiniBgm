# MiniBgm 问题档案（假设 → 证据 → 结论）

> **用途**：记录项目中遇到过的非平凡问题——当时的假设、验证证据、最终结论与决策理由。目的是防止重复踩坑、避免已定决策被反复重新讨论。
>
> **维护约定**：
> - 何时追加：事故复盘结束时、重要决策定案时。不记流水账（"做了什么"看 `jj log`），只记"为什么"。
> - 与其他文档的分工：[ROADMAP.md](../ROADMAP.md) 记未来要做什么（含 §6 决策记录）；[AGENTS.md](../AGENTS.md) 记约束与红线（从教训中提炼的规则直接固化在那里，此处只放案例）；本文档记"过去踩了什么坑、怎么定位的"。
> - 每条保持"背景 → 假设 → 证据 → 结论"结构，已闭环的标 ✅，受阻/未解决的标 ⏳。

---

## ✅ 已闭环

### P-001 · 时间表刷新时列表多次跳动（2026-09）

- **背景**：下拉刷新/进页时，放送时刻表列表先出"裸数据"卡片，再逐次补上播放源、评分，视觉上跳好几下。
- **假设**：多数据源分步写库，每写一次 Room 流就推一次 UI。
- **证据**：`ScheduleRepository.refreshSchedules()`（官方日历）与 `syncBangumiData()`（播放源/网播番）各自 `insertSchedules()`；`weeklySchedulesFlow` 直接订阅 DAO 流，任何写库立即外发。
- **结论与决策**：仓库层加同步闸门——`refreshAllSchedules()` 管线依次拉齐官方日历（含逐话事件）与 bangumi-data，期间 `scheduleHoldGate` 扣住对外流，完成后经 `flatMapLatest` 重新订阅、以最终态**只发一次**；错误聚合不互相中断。用例证明中间态从未外发。
- **状态**：✅ 已解决（`feat(feature:schedule,data)` 提交）。

### P-002 · 真机点击时间表卡片进详情必闪退（2026-09，工作流引入）

- **背景**：工作流并行实现"封面微光"后，真机点卡片进详情 100% 崩溃；JVM 单测与全套门禁全绿。
- **假设 1**：Coil 依赖可见性问题（core:designsystem 引不到 coil-core）→ **否定**：编译通过，崩溃栈不在类解析。
- **真因证据**：logcat `FATAL EXCEPTION ... IllegalStateException: unable to getPixels(), pixel access is not supported on Config#HARDWARE bitmaps`，栈指向 `AmbientGlowKt.toDownsampledPixels:178` ← `CoverImage.kt:77`（Coil onSuccess 回调）。
- **结论**：真机 Coil 默认解码为 `Config#HARDWARE`（仅存 GPU，禁止 `getPixels`）；JVM 单测里位图都是软件位图，**该路径单测原理上测不到**。
- **修复**：采样前 `config == HARDWARE` 则 `copy(ARGB_8888)`（copy 对硬件位图合法）；整段提取 `runCatching` 兜底——光晕是纯装饰，任何异常降级为"无光晕"。
- **衍生决策**：AGENTS.md 固化原则「纯装饰特性必须 fail-open；访问 Bitmap 一律假设 HARDWARE」。
- **状态**：✅ 已解决（真机复验 0 FATAL）。

### P-003 · 未登录时第一话恒被标"在看"（2026-09）

- **背景**：任意条目分集列表，退出登录后第 1 话固定显示"在看"徽标。
- **假设**："下一话待看"判定 `集数 == 已看话数 + 1` 在没有进度数据时被误满足。
- **证据**：`collection?.epStatus ?: 0` 把"无收藏记录"折叠成"已看 0 话"，于是 `1 == 0 + 1` 恒成立。
- **结论与决策**：状态断言必须有进度上下文——`isEpisodeNextToWatch` 增加 `hasProgress` 参数，`collection == null` 时直接 false；登录/想看等有依据场景行为不变。
- **状态**：✅ 已解决（含回归用例）。

### P-004 · 找源正则对 JS 渲染站点无解（结构性）

- **背景**：找源第 4 档靠正则抽静态 HTML；B 站等播放站流地址由 JS 向签名接口请求，静态页里不存在。
- **假设 1**：无记录来源页时兜底抓 B 站搜索页能兜住 → **否定**：正则必然 0 命中，纯耗预算 → 已移除该兜底。
- **假设 2**：照搬 Kazumi（WebView 运行时捕获）→ **部分采纳**：其能力上限其实低于 yt-dlp（API 逆向），但 yt-dlp 本地打包丢时效性、服务端不自架，均判不做。
- **结论与决策**：确立六层能力瀑布（直连→片单→源协议→正则→WebView 捕获→AI 自愈），按"确定性递减、成本递增"短路；第 5 层（WebView 抓包，3a）已落地。
- **状态**：✅ 3a 已落地；3b 见未解决清单。

### P-005 · 社区回帖无法原生（2026-09 查证）

- **假设 1**：Bangumi v0 API 有回帖端点 → **否定**：官方 OpenAPI（47 路径）无任何社区端点，搜索摘要误导。
- **假设 2**：私有 `/p1` API 可用（实测 `api.bgm.tv`/`next.bgm.tv` 均服务）→ **部分成立**：读端点与点赞端点可用，但 `CreateReply`/`CreateTopic` 强制 `turnstileToken`（Cloudflare Turnstile，仅能在 next.bgm.tv 域名网页上渲染获取）。
- **结论与决策**：第三方客户端无法干净取得 token，绕过（WebView 操纵官方页面）不可接受；**回帖维持跳浏览器出口**；点赞（无 Turnstile）已原生落地（楼层 + 单集吐槽 toggle）。
- **状态**：⏳ 受外部约束阻塞，待官方放开后接入。

### P-006 · 搜索体验三连（别名容错 / 滚动丢失 / 弱网降级）（2026-09）

- **滚动丢失真因**：不是没保存状态——`LaunchedEffect(排序, 分类, query)` 的"回顶"逻辑在**从详情页返回、组合重建时会重新执行**（LaunchedEffect 重新进入组合就重放，与 key 是否变化无关）。修复：重置跟随 VM 的"搜索代数"信号 + `rememberSaveable` 记录已应用代数。
- **别名容错**：`SearchAliasIndex` 归一化（小写/全角转半角/去空白与间隔符）+ 命中分级（精确>前缀>包含），索引范围为 Room 缓存窗口内条目（bangumi-data 同步窗口决定，诚实边界）。
- **弱网降级**：网络失败且本地索引有命中时，全屏错误降级为软提示条 + 离线结果 + 重试。
- **状态**：✅ 已解决（搜索页先行，其他页面待推广）。

### P-007 · 历史事故（会话前，已在 AGENTS.md 留痕，此处仅索引）

- `androidUnitTest` → `androidHostTest` 源集改名导致测试静默零用例（"绿 ≠ 测过"）——教训固化为验证规矩第 3 条（必须查 XML `tests > 0`）。
- v0.2.8 手改版本号导致 tag ≠ 构建版本、versionCode 不单调——教训固化为"tag 是版本唯一来源"。

---

## ⏳ 未解决 / 待决策

| 条目 | 卡点 | 备注 |
| --- | --- | --- |
| 3b · AI 现场推导与规则固化 | 需决策：固化产物形态（WebView 原语序列 vs 降级 SOURCE 模板）与存储位置（建议扩 `PlaybackSourceRule`） | 触发条件：3a 实际使用中失败率可观再动工 |
| BT 边下边播 | 需决策：产品定位（"纯播放壳"→"下载器"、APK +15~25MB、前台服务）；技术方案已论证（libtorrent4j + 本地 HTTP Range + Media3） | swarm 健康度接失败归因的方案已备 |
| 单集吐槽原生发帖 | 同 P-005 的 Turnstile 阻塞 | 点赞已原生 |
| 好友/全站动态时间线 | 无技术卡点，体量大（roadmap v0.6.0） | |
| Bangumi 大陆可达性 | 2026-05 起部分地区直连受阻（Kazumi 也只做了应用内代理）；实测当前网络直连全部可达 | 备选：应用内代理设置（对齐 Kazumi）/ 复用自有 Worker 做 API 反代（可超越 Kazumi）；等实际受阻再动工 |
| 播放历史 UI 入口 | 断点续播数据已在（按 URL 的位置表），缺浏览界面 | 小活 |
| low 清理 | ExploreSpotlightCard `Color.White` 红线正则盲区；AMOLED 调色板缺对比度测试；`ScheduleTimelineComponents` PREDICTED 死分支 | 半小时量级 |
| 全屏播放手势 | ROADMAP 押后（与 Compose 手势/双栏竞争） | 维持押后 |

---

## 跨问题的模式级教训（写给未来的自己与 AI 会话）

1. **"绿 ≠ 对"**：JVM 单测、模拟器、真机是三个世界。位图（HARDWARE）、包可见性（Android 11+ `<queries>`）、网络域名的差异只有真机暴露——装饰性 UI 改动合入前值得真机点一遍。
2. **装饰性代码必须 fail-open**：`runCatching` + 默认值，异常永远不该从"锦上添花"的路径炸进页面。
3. **`LaunchedEffect` 在组合重建时会重放**：需要"只执行一次"语义的副作用（回顶、一次性提示）必须自带已应用标记（`rememberSaveable`），不能依赖 key 相同。
4. **Room 一次写 = 流一次推**：UI 想要"最终态一次更新"，要么闸门扣流、要么让管线写完再让订阅者看到——分步写库 + 直订 DAO 流必然闪变。
5. **外部依赖的约束先查证再接**：官方 API 有没有端点、要不要验证码、第三方库的位图语义——搜索摘要会撒谎，OpenAPI 规范和源码不会。
6. **模型/AI 链路的信任边界**：真值只认网络层与结构化返回，模型转述的一律不采信；模型永远只拿受控原语，不拿代码执行权（详见 ROADMAP §6 硬红线）。
