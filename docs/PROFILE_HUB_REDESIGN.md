# MiniBgm 个人中心架构迁移设计 (Profile Hub Redesign)

> **状态：v4；四层结构已落地（2026-09-28）。** 见 §1 与 §3。
> **v2 修订（2026-09-28）**：v1 的数据面结论基于仓库既有假设与二手资料，**存在错误**——尤其"社交数字无端点"。v2 改为逐条核对两份**官方 OpenAPI 规范原文**，已在 §2.4 列出全部更正。
> **v3 修订（2026-09-28，决策）**：私有 API 能力虽已核实存在，但**决定不引入依赖**——成就带走 v0-only，不扩展凭据层承载 CookieSession，原生发评论不做。v2 中两处以私有 API 为数据源的表述同时作废，见 §2.5。
> **实施修订（2026-09-28）**：原 P1「追番成就带」与 P3「评分叙事」曾合并为「评分成就区」落地，同日按新增的**高频页面请求纪律**（§7）回退——该功能进一次页面要发 `ceil(看过数/50)` 次串行分页，属"用多次请求拼装一个功能"。**结论：个人页不做它，直到数据能本地化。**
> **实施修订 ②（2026-09-28）**：P0 的「在追动态卡」已**移除**（`61cd3ee5`）。它是个人页唯一需要在页面现场拉取的区块，且选材口径与卡片语义都不成立。见 §5-P0。
> **实施修订 ③（2026-09-28）**：第三、四层缺失的问题被指出并修正——此前落地的是**卡片仪表盘**（身份卡 + 账号卡 + 收藏总览卡），不是主流个人页结构。本轮完成「Hero 头部 → 通栏数字带 → 吸顶分区 Tab → 内容流」四层迁移，收藏列表就地渲染。**结构参照不是 §2.3 的官方时光机板块模型，而是仓库内已存在的 `SubjectDetailScreen` 范式**（Hero → 数据卡 → `stickyHeader` Tab → Tab 内容）与主流移动端个人页（见 §3.0）。详见 §3 / §5-P0。
> 关联文档：[ROADMAP.md §7 个人中心板块](../ROADMAP.md)、[UX_REMEDIATION.md](../UX_REMEDIATION.md)

---

## 0. 结论速览

主流社区个人页是**四层骨架**：身份头部 → 数字带 → 分区 Tab → 内容流。第二层「数字带」是平台分叉点——社交型（微博/小红书）用关注粉丝获赞，数据型（AniList/Steam）用累计数据。

**Bangumi 的实际情况比 v1 判断的宽松得多**：官方 v0 API 确实没有社交端点，但项目**已在依赖**的私有 API（`next.bgm.tv/p1`）**有完整的好友 / 关注者 / 时间线 / 用户统计**，且官方自己就定义了个人主页板块模型（「时光机」）。

**但能力存在不等于要用。v3 决策：私有 API 收口——本轮设计与实现只依赖 v0（Bearer），不引入任何新的 `/p1` 依赖。** 落点：

- 「不做社交数字」的性质从**被迫**改为**主动选择**（定位决策，非能力约束）；可得性已论证完，将来若要重启社交方向不必再查
- 成就叙事原计划走 **v0-only**：计数用**已接线**的 `api.bgm.tv/user/{u}/collections/status`，入站年数用 `/v0/me` 的 `reg_time`，评分明细用 `/v0/users/{u}/collections`——**但评分明细部分已回退**（请求扇出，见 §5-P1）；计数与入站年限保留
- 因而不扩展凭据层、不承载 CookieSession，不触碰 §7 的「凭据只存在于 `AuthTokensDataSource`」红线
- `UserHomepage` 板块模型仍作为**结构参考**（§5-P6），但不依赖 `GET /p1/home` 拉取
- **新增硬约束（§7）**：高频页面只读本地与内存缓存，跨全量数据的聚合一律交给后台 Worker 或用户主动刷新

---

## 1. 现状（四层结构已落地，2026-09-28）

| 层 | 能力 | 实现位置 | 数据来源 | 请求 |
|---|---|---|---|---|
| ① | 沉浸式身份头部（头像 88dp / 昵称 / @用户名·UID / 入站年限 / 徽章 / 签名） | `feature/user/.../UserProfileCards.kt` → `UserProfileHero` | 本地 profile + v0 `/v0/me` 的 `reg_time` | 0 |
| ② | 通栏数字带（在看 / 累计追集 / 本月打卡 + 最近打卡副标） | 同上 → `TrackingStatsRow` | Room 本地聚合 `observeTrackingFootprint()` | 0 |
| ③ | 吸顶分区 Tab（在看 / 想看 / 看过 / 搁置 / 抛弃，带计数） | `feature/user/.../UserCollectionListParts.kt` → `CollectionTypeTabs` | 计数走 `collectionCounts`（TTL 缓存） | 0（缓存内） |
| ④ | 当前分区的收藏内容流（就地渲染 + 触底分页 + 条目类型筛选） | `feature/user/.../UserCollectionListParts.kt` + `UserCollectionsViewModel` | v0 `/v0/users/{u}/collections` | 进入分区 1 次 |
| — | 骨架几何对齐四层 | `feature/user/.../UserScreenSkeleton.kt` | — | — |

**已删除**：`UserCollectionOverviewCard.kt`（数字瓦片 + 占比条 → 并入 ③ 的 Tab 标签）、`MultiAccountQuickCard`（入口已在顶栏 + ① 的「N 个账号」胶囊 + 账号 BottomSheet）、`UserCollectionsScreen.kt` 与 `UserCollectionsRoute`（④ 内联后无入口）。

DB 为 `version = 9`，**至今无 schema 迁移**。宽屏双栏（`isWide` 分支）随本次迁移移除：新的内容主体是一条纵向内容流，左 Hero/右看板的双栏切分不再成立；排版改为与详情页一致的单栏滚动。宽屏骨架/双栏相关几何同步删除。

进一次个人页的网络请求：**① ② ③ 为本地或 TTL 缓存，④ 在进入分区时 1 次**。首轮改造中的两项外延功能——在追动态卡、评分成就区——均已移除（理由见 §5-P0 / §5-P1）。请求面的如实口径见 §7.1。

---

## 2. 数据面核实

两套 API 的 base URL（均已在项目中使用）：

| 名称 | Base | 客户端 | 规范来源 |
|---|---|---|---|
| v0（官方公开） | `https://api.bgm.tv` | `BangumiApiService` | `bangumi/server` → `openapi/v0.yaml`（46 端点） |
| 私有 / next | `https://next.bgm.tv` | `BangumiCommunityService` | `bangumi/server-private` → `bangumi/dev-docs/api.json`（165 端点） |

> 核实方式：下载上述两份 OpenAPI 规范原文逐条比对**（规范级核实，非实测响应）**。未实测项见 §9。

### 2.1 v0 API：46 端点，无任何社交端点

全部路径中与用户相关的仅 9 条：

```
/v0/users/{username}                     /v0/users/{username}/avatar
/v0/me                                   /v0/users/{username}/collections
/v0/users/{username}/collections/{id}    /v0/users/-/collections/{id}
/v0/users/-/collections/{id}/episodes    /v0/users/{username}/collections/-/{characters|persons}
```

全规范检索 `follow` / `friend` / `follower` / `timeline` / `fans` —— **零命中**。

`User` schema（`user.yaml`）只有：`id, username, nickname, user_group, avatar, sign`（另有文档说明可能返回未声明的 `url`，明确要求不要依赖）。

`UserSubjectCollection` schema：`subject_id, subject_type, **rate**(required), type, comment, tags, ep_status, vol_status, updated_at, private, subject`。

**两个必须记住的坑：**

1. **`updated_at` 官方声明不可靠**，原文：
   > "本时间并不代表条目的收藏时间。修改评分，评价，章节观看状态等收藏信息时未更新此时间是一个 bug。请不要依赖此特性"
   → **用远端 `updated_at` 做"按月观看趋势"是不成立的**。而本地 `user_collections.updatedAt` 由 App 在自己打卡时写入，可靠——这也是现有「本月打卡」能成立的原因。
2. **分页上限很小**：`limit` 默认 **30**、最大 **50**（`default_query_limit`：`minimum:1, maximum:50, default:30`）；参数只有 `subject_type / type / limit / offset`，**无 sort、无时间过滤**。
   → v1 文档里写的 `limit=100` 无效。1000 条收藏 = **20 次请求**。

### 2.2 私有 API：165 端点，社交与时间线完备

与个人主页直接相关的端点：

| 端点 | 用途 |
|---|---|
| `GET /p1/users/{username}` | 用户资料，**含 `stats`** |
| `GET /p1/me` | 当前用户 `Profile`（含 `joinedAt`） |
| `GET /p1/friends` / `/p1/followers` | 我的好友 / 关注者，返回 `data[] + total` |
| `GET /p1/friendlist` | 好友 ID 列表 |
| `GET /p1/users/{username}/friends` `/followers` | 他人好友 / 关注者 + `total` |
| `GET /p1/me/friends/subject-collections?subjectType=` | **友邻最近条目收藏动态** |
| `GET /p1/timeline?mode&limit&until` | 时间线 |
| `GET /p1/users/{username}/timeline` | 用户时间胶囊 |
| `GET /p1/users/{username}/collections/{subjects,characters,persons,indexes}` | 用户各维度收藏 |
| `GET /p1/wiki/users/{username}/contributions/{subjects,persons,characters}` | **wiki 贡献记录 + total** |
| `GET /p1/users/{username}/groups` `/blogs` `/indexes` | 小组 / 日志 / 目录 |

**关键 schema：**

- `User`（私有）比 v0 丰富得多：`id, username, nickname, avatar, group, **joinedAt**, sign, site, location, bio, networkServices, homepage, **stats**, isFriend`
- `UserStats`：`subject`（嵌套计数 map）、`mono{character, person}`、`blog:int`、**`friend:int`**、**`group:int`**、`index{create, collect}`
- `UserSubjectCollectionStats`：`additionalProperties: { string: { string: int } }` —— 形如 `{subjectType: {collectionType: count}}`，**一次请求给全部收藏计数**
- `FriendSubjectCollectionActivity`：`user, subject, collectionType, updatedAt`
- `Timeline`：`id, uid, user, cat, type, memo, batch, source, replies, createdAt, reactions`
- `TimelineCat` 9 类：1 日常 / 2 维基 / 3 收藏条目 / 4 收视进度 / 5 状态 / 6 日志 / 7 目录 / 8 人物 / 9 天窗

**鉴权**：`/p1` 端点接受 `HTTPBearer`——**项目现有 OAuth Bearer 直接可用**（与已落地的 `/p1/.../like` 同一套），无需新认证机制。**唯一例外是 `GET /p1/me`：只声明 `CookiesSession`**（见 §2.5）。

### 2.3 一个决定性的结构发现：`UserHomepage` = 官方「时光机」

```
UserHomepage { left: [UserHomepageSection], right: [UserHomepageSection] }
UserHomepageSection = anime | game | book | music | real | mono | blog | friend | group | index
```

**Bangumi 官方自己就定义了个人主页的板块模型**——左右两栏、10 种可选板块。v2 由此得出"设计方向是对齐官方时光机模型：第 3 层分区在 Bangumi 语境下天然是**板块**而不是 **Tab**"。

> **该结论已被 v4 部分推翻（2026-09-28）**：`UserHomepage` 是**桌面端**的左右栏板块模型，不是移动端主流个人页形态；移动端个人页的通行做法是**单一纵向流 + 吸顶 Tab**。本轮的落点是后者（见 §3.0），`UserHomepage` 退为设计参照之一。教训与 §2.4 一致：**能力/schema 存在不等于它就是对的形态**——形态判断要看目标端与目标平台的主流，不能拿服务端模型直接推 UI。

### 2.4 更正表（v1 → v2）

| 项 | v1 结论 | 核实后结论 | 依据 |
|---|---|---|---|
| 关注/粉丝数 | ❌ 无端点 | ⚠️ **v0 无，私有 API 有**（`/p1/followers`、`/p1/friends`，直接给 `total`） | 私有 spec |
| 好友数 | ❌ 无 | ✅ `UserStats.friend`（`/p1/users/{u}` 一次请求） | 私有 spec |
| timeline | ❌ 无端点 | ⚠️ **v0 无，私有 API 有**（`/p1/timeline`、`/p1/users/{u}/timeline`） | 私有 spec |
| 友邻在看 | 未评估 | ✅ `FriendSubjectCollectionActivity` 现成 | 私有 spec |
| 入站年数 | 需映射 v0 `reg_time` | ⚠️ v2 结论已作废（§2.5）：改回 v0 `reg_time` | 私有 spec |
| 收藏计数 | 需调 v0 legacy `/user/{u}/collections/status` | ⚠️ v2 结论已作废（§2.5）：维持 v0 legacy，且**仓库早已接线** | 私有 spec |
| 评分明细 | ✅ v0 可用 | ✅ 仍只走 v0（见下） | 两 spec 对比 |
| 观看时长 | ❌ 无数据 | ❌ 维持（仍无用例） | — |
| 分页上限 | `limit=100`（**错误**） | ⚠️ **最大 50** | v0 spec |
| 按月趋势 | 用远端 `updated_at` | ❌ **不成立**，官方声明该字段不可靠 | v0 spec |

**评分明细为何仍必须走 v0**：私有 API 的 `/p1/users/{username}/collections/subjects` 返回 `data: [SlimSubject]`——**只有条目，不含 `rate`**。评分类叙事（分布图 / 均分）只能走 v0 `/v0/users/{username}/collections`（`rate` 是 required 字段）。

→ **职责分工**：**计数走 v0 legacy（已接线，TTL 缓存内 0 请求），评分明细走 v0 `/v0/users/{u}/collections`（ceil(N/50) 请求）**。

### 2.5 v3 决策：私有 API 收口（2026-09-28）

私有 API 的读端点接受现有 OAuth Bearer，能力也已逐条核实；但本轮**决定不引入新依赖**。同时修正 v2 里两处需要作废的表述：

| 项 | v2 表述 | v3 修正 |
|---|---|---|
| 收藏计数来源 | "私有 `UserStats.subject` 一次拿全、省一次调用" | **v0 legacy `api.bgm.tv/user/{u}/collections/status`**（带 `app_id` 参数）——**仓库早已接线**：`BangumiApiServiceImpl.getUserCollectionStats()` → `CollectionRepositoryImpl.fetchCollectionCounts()`（`CachedCounts` TTL 缓存）。私有方案并不比现状更优，v2 的"顺带省一次调用"因此不成立 |
| 入站年数来源 | "私有 `User.joinedAt` 直接给" | **v0 `/v0/me` 的 `reg_time`**（spec 中为 required、`date-time`）——只需把它映射进 `UserProfile`（当前被丢弃）。Bearer 即可，无需 CookieSession |

**`GET /p1/me` 的处置**：它是全部 165 个端点里**唯一只声明 `CookiesSession`、不接受 `HTTPBearer`** 的端点。要让它可用，需在 `AuthTokensDataSource` 新增 cookie 槽位并注入 `chiiNextSessionID`——这是一次凭据层扩展。**决定：不做。** 依赖它的只有 `stats.friend/group/blog` 与 `joinedAt`，两者对个人页叙事都非必须，且入站年限已有 v0 替代（`reg_time`）。

**连带结论**：§2.2 列出的社交 / 时间线端点虽技术可用（Bearer 即可，与已落地的 `/p1/.../like` 同源），本轮一律**不使用**——理由见 §3.2。

---

## 3. 目标结构（已落地）

### 3.0 结构参照的两处来源

1. **仓库内的既有范式**：`feature/subject/.../SubjectDetailScreen.kt:907` 早已是 `Hero 卡片 → 个人信息卡 → stickyHeader(PrimaryTabRow) → Tab 内容流`。**详情页做对了，个人页是唯一的例外**——所以本轮的做法是"让个人页对齐本仓库自己的范式"，而不是引入外部框架。
2. **主流移动端个人页**（对外核实，2026-09-28）：
   - 小红书「我」页：背景与头像层叠、上滚时标题栏渐变、**TabBar 自动吸顶**、内容区瀑布流且支持左右滑动切换（Tab = 笔记 / 收藏 / 赞过）
   - B站「我的」页：背景设置区 → 用户信息区 → 社交区 → … → TAB 栏
   - 一篇个人主页头部组件的分析给出的口径：头像居左/居中/居右；**数字用通栏（≥3 项均分整行）**；背景可选「头像模糊」或「素底微渐变」

> §2.3 的官方 `UserHomepage` 时光机板块模型（左右栏 + 10 种板块）仍是**结构参考之一**，但它是**桌面端板块模型**，不是移动端主流个人页形态；本轮不采用它，也不调 `GET /p1/home`。

### 3.1 四层结构

| 层 | 区块 | 状态 | 数据源 | 请求成本 |
|---|---|---|---|---|
| ① | 沉浸式身份头部 | ✅ 已落地 | 本地 profile + `/v0/me` 的 `reg_time` | 0 |
| ② | 通栏数字带（在看 / 累计追集 / 本月打卡 + 最近打卡副标） | ✅ 已落地 | Room 本地聚合 | 0 |
| ③ | **吸顶分区 Tab**（在看 / 想看 / 看过 / 搁置 / 抛弃，带计数） | ✅ 已落地 | 计数走 v0 legacy `/user/{u}/collections/status` + TTL 缓存 | 0（缓存内） |
| ④ | **当前分区内容流**（就地渲染 + 触底分页 + 条目类型筛选） | ✅ 已落地 | v0 `/v0/users/{u}/collections` | 进入分区 1 次 |
| — | ~~评分成就区（均分 + 1–10 分布柱图）~~ | ❌ 已实施后回退 | v0 `/v0/users/{u}/collections`（`rate`） | 曾为 ceil(N/50)、封顶 20 —— **违反 §7，故撤销** |
| — | ~~在追动态~~ | ❌ 已落地后移除 | `/p1/subjects/{id}/topics`（页面现场拉取） | 曾为 N = min(在追部数, 3) —— **违反 §7，故移除** |
| — | 继续观看行 | ✗ 不做（用户决定） | 本地续播 DataStore（需补映射，见 §5-P2） | 0 |
| — | 友邻在看 | ✗ 本轮不做 | 私有 API `/p1/me/friends/subject-collections` | — |

**形态纪律（2026-09-28）**：第二层只放**一种**数字形态。

- 「数字行」全页**只有一排**——即已落地的 `TrackingStatsRow`（在看 / 累计追集 / 本月打卡），不再叠加第二排同形数字格
- **注册时间不占格子**：降级为头部一行次要文字（`$year 年加入 · 已 N 年`），与 GitHub / Twitter 头部的 "Joined …" 同构；`regTime` 缺失时整行不渲染
- 原设计里的「入站年数 / 均分 / 看过」三格数字行**已废弃**——它与统计条同形，会形成两排重复数字
- 原设想用「图表形态」（分布柱图）与数字行区分、从而让成就区成立——**该设想随 §7 一并作废**：不是形态不好，而是它拿不到零请求的数据
- **第三层的计数不构成第二排数字**：它们是 Tab 标签的后缀（颜色、字号、位置都随 Tab 走），不是独立的数字行；原「收藏总览卡」的数字瓦片正是被它取代的重复形态

### 3.2 为什么仍然不做社交数字带

v1 的第一条理由是"数据不存在"——**这条已被推翻**。修正后的理由只剩两条，但依然成立：

1. **定位决策**：ROADMAP §7 已把定位写成"安静的追番工具个人主页，不向社区 App 的社交数字看齐"。数据可得 ≠ 应该展示。
2. **范式验证**：AniList / Steam 用累计数据填第二层，叙事强度不弱于社交数字，且不产生社交压力。
3. **依赖收口**：私有 API 无兼容性保证，本轮不新增 `/p1` 依赖（§2.5）。这条同时否掉 ⑤′ 友邻在看与 P6 的 `GET /p1/home`。个人页在移除 ⑤ 之后，**已无任何 `/p1` 读写依赖**——收口由此从"约定"变成"事实"。

但**结论的强度变了**：这是**产品选择**，不是**能力被迫**。若将来要重启社交方向，`/p1/friends`、`/p1/followers` 的 `total` 字段可以直接支撑"好友 N · 关注者 M"，不必再论证可行性。

---

## 4. 关键技术决策：Watch Insights 要不要 Room 迁移

`rate` / `comment` **远端本来就有**，迁移不是必要条件。三条路径：

### A. Room 迁移
`user_collections` 加 `rating` / `comment` 列 + 逐集进度表 + `Migration(9, 10)`。
- ✅ 离线可用、SQL 聚合快
- ❌ **真正的成本不在加两列**：当前本地只同步 DOING（type=3），要支持"看过"分布得把同步面扩到全部收藏（上千条），本地库体积与首次同步耗时显著上升

### B. 零迁移 · 按需拉取 + 内存 TTL 缓存 ← **曾实施，2026-09-28 已回退**
新增 `fetchRatingInsights(username, force)`：分页拉 v0 collections（`subject_type=2&type=2`）聚合直方图/均分。
- ✅ 零迁移、不膨胀本地库
- ❌ **致命项（事后才发现）**：成本是 `ceil(N/50)` 次请求、页数上限 20。这不是"一次页面渲染"，而是**用多次请求拼装一个功能**——且缓存只在内存，冷启动每次重付。违反 §7，已撤销
- 教训：判断成本不能只看"有没有缓存"，要看**缓存失效后一次交互的真实扇出**

### C. 轻量聚合快照（JSON 单行）
只存聚合结果（直方图/均分/快照时间）一行 JSON——**Room 表或 DataStore 均可**（`SettingsRepository.playbackPositions` 已有"序列化 JSON 存 DataStore"的先例，可免迁移）。
- ✅ 离线可读、体积小
- ✅ **唯一能同时满足"保留功能"与 §7 的路径**：拉取交给后台 Worker / 下拉刷新，页面只读快照
- ⚠️ 数据有滞后（需在卡片上标"截至 X"）

**当前结论：B 已废弃。若要重做评分叙事，只能走 C**——先解决"页面零请求"，再谈展示。
**计数类数据的既有实现已足够**——v0 legacy `/user/{u}/collections/status` + TTL 缓存，1 请求且长期有效。

---

## 5. 分期

### P0 · 四层结构 —— 已落地（2026-09-28）

沉浸式身份头部（`UserProfileHero`，无卡片外壳、头像 88dp、头像主色氛围光）+ 通栏数字带（`TrackingStatsRow`）+ 吸顶分区 Tab（`CollectionTypeTabs`，带五维计数）+ 当前分区收藏内容流（`UserCollectionCard` + 触底分页 + 条目类型筛选）。骨架 `UserScreenSkeleton` 同步为四层几何。

**迁移记录：从"卡片仪表盘"到"个人页"**

被替换前的结构是竖向堆叠的三张卡：身份头部卡（数字带嵌在卡片底部）→ 账号快捷切换卡（>1 账号时）→ 收藏总览卡。**第三、四层完全缺失**——分区 Tab 与内容流活在二级页 `UserCollectionsScreen` 里，个人页只有一张"导航卡"。

- **删除项与理由**：
  1. `UserCollectionOverviewCard.kt`（数字瓦片 + 五维占比条）—— 它本质是**导航入口**，点进去才有内容；主流个人页是 Tab 下直接铺内容流。五维计数改为 Tab 标签后缀，信息零丢失
  2. `MultiAccountQuickCard`（整卡）—— 账号管理入口已存在于顶栏图标、头部「N 个账号」胶囊与账号 BottomSheet 三处，整卡是第四处重复
  3. `UserCollectionsScreen.kt` + `UserCollectionsRoute` + `userCollectionsEntry` —— 内容流内联后**无任何入口**（迁移前唯一入口就是个人页那张卡）
  4. 宽屏（`isWide`）双栏分支与对应骨架几何 —— 新的主体是一条纵向内容流，左 Hero/右看板的双栏切分不再成立；改为与详情页一致的单栏滚动
- **连带修复**：导航属性测试 `BgmNavStatePropertyTest` 曾以 `UserCollectionsRoute` 作为"同类二级页应替换而非堆叠"的被测对象之一（另有 `SearchRoute` / `AssistantRoute` / `SeasonalGuideRoute` 覆盖同一不变式），随路由退役一并收口；`BgmNavHostTest` 的 `isTopLevelRoute` 断言同步。
- **未做**：左右滑动切换分区。主流（小红书）有，但当前单滚动容器要在保持头部随滚动移出的同时让 Pager 承载内容需要引入嵌套滚动；详情页也未做，保持一致。

**移除记录（往前）：在追动态行式化（2026-09-28 撤除，`61cd3ee5`）** —— `af0dc331` 引入，`11b774c7` 把番名改走本地以收敛扇出，同日整条撤除。

- **移除理由（三条，任一条都足够）**：
  1. **选材口径不成立**：参与方是 `getCollectionsByTypeStream(DOING)` 的**收藏 `updatedAt` 最近前 3 部**，不是「讨论活跃的在追番」。刚点在看、刚补完老番的条目会占据名额，其讨论版往往是陈帖或水帖——"看上去没用"的根因在这里，不在请求量
  2. **点击落点与卡片语义相悖**：卡片展示"讨论"，点击却落到**番剧详情**。卡内注释写的"讨论详情路由暂未开放"与实况不符：`TopicDetailRoute` + `TopicDetailScreen` + `topicDetailEntry` 早已存在，只是没接上
  3. **仍属页面现场拉取**：无跨会话缓存，离开页面 >5s 再回来即重付 N 次，与 §7.1「只读本地与内存缓存」不符（量级小≠合规）
- **连带修复**：该卡片是 `af0dc331` 给 `UserViewModel` 追加 `communityRepository` / `subjectRepository` 的唯一理由。追加后 `AppModuleTest` 的 5 参构造不再可编译，而 jgate 显式跳过 `:app` 单测、装配门也只编译主源集——破损静默存在至今，随这两个依赖移除一并恢复
- **若要复活**，三件事缺一不可：选材改用本地 `air_schedules` 判断"正在播"、落点接 `TopicDetailRoute`、按 §7.1 下沉到 Worker 快照（页面 0 请求）

### P1 · 评分成就区 —— **已实施，同日回退（2026-09-28）；结论：不做**

原 P1「追番成就带」与 P3「评分叙事」曾合并为「评分成就区」，**已实现、过门禁、入库**（`23addf89`），同日按 §7 撤销（`7688721a`）。

- **撤销理由**：进一次个人页触发 `ceil(看过数/50)` 次串行分页（上限 20 页），内存缓存进程重启即失效——冷启动每次重付。
- **为何不能改造成单请求**（已核实两套 API 均无聚合端点）：
  - v0 收藏端点只有 `limit / offset / subject_type / type`，无排序、无聚合
  - 私有 `UserStats.subject` 展开是 `{条目类型: {收藏状态: 计数}}`，**只有计数没有评分**
  - 私有 `/p1/collections/subjects` 返回 `Subject[]`，**不含 `rate`**
  - → 评分分布天然要求读到每一条评分，**无法降为单请求**
- **若要重做**：只能先落 §4-C 的快照方案（后台聚合 + 页面 0 请求），并接受数据滞后
- **保留部分**：`UserProfile.regTime` / `registeredYear` 与头部入站年限——随 `/v0/me` 一并返回，属零额外请求，不在撤销范围

### P2 · 继续观看行
- **现状障碍**（不变）：续播持久化键是 `streamUrl → positionMs`（`SettingsRepository.MAX_PLAYBACK_POSITIONS = 50`），**无 subjectId / 封面**
- **前提**：播放时额外写入 `subjectId / episodeId / 封面URL`（轻量 DataStore）
- 纯客户端，不依赖远端。**用户已决定暂不做（2026-09-28）**

### P3 · 评分叙事 —— **已并入 P1，随其一并撤销**

### P4 · 友邻在看 —— **本轮不做（2026-09-28 决策）**
- 技术上现成：`/p1/me/friends/subject-collections?subjectType=2` → `FriendSubjectCollectionActivity{user, subject, collectionType, updatedAt}`，Bearer 即可，1 请求
- **不做的两条理由**：它是唯一会引入"社交感"的区块（与"安静追番工具"定位冲突）；同时属新增私有 API 依赖（§2.5 收口）
- 保留为**已论证可行**的候选：将来若重启社交方向，可行性无需再查

### P5 · 年度 Wrapped
依赖 P1 聚合；年度 Top / 最狠评分月 / 追番节奏；图片导出分享。
**注意**：不能用远端 `updated_at` 排期（官方声明不可靠），年度归属需基于本地写入时间或另行设计。

### P6 · 板块化 / Tab —— **已落地（2026-09-28）**

- **触发条件**原写的是"区块 ≥ 5 或单页滚动 > 2.5 屏"；实际由**结构对齐**驱动提前落地——主流个人页的第 3/4 层就是"吸顶 Tab + 内容流"，本仓库详情页也早已如此，个人页是唯一例外。触发条件因此不再是判据，改列为"若将来需要给个人页增加非收藏类的独立内容区，先满足 §7.1 的请求纪律再谈加 Tab"
- **实现与 `SubjectDetailScreen` 同构**：单 `LazyColumn` + `stickyHeader(PrimaryTabRow)`，Tab 切换时替换列表项；**不用 `HorizontalPager`**（原因见 §5-P0「未做」）
- 官方 `UserHomepage.left/right` 板块模型仍是**设计参照**（它是桌面端左右栏板块模型），**不是运行时依赖**——不调 `GET /p1/home`

### 押后项 · 时间线（理由已更新）
ROADMAP §3 的押后理由是"官方 v0 无 timeline 端点"。核实后准确表述应为：

> **私有 API 有** `/p1/timeline` 与 `/p1/users/{username}/timeline`，且项目已在依赖 `next.bgm.tv`。真实押后理由是**私有 API 无兼容性保证 + 需要好友关系积累 + 与"安静工具"定位的取舍**，而非能力缺失。

v3 决策进一步固化：本轮不新增 `/p1` 依赖，时间线自然不在范围内。

---

## 6. 边界与不做

- **关注 / 粉丝 / 好友数字带** — **能力上可行**（`/p1/friends|followers` 的 `total`），但按定位**主动不做**。性质是产品选择，不是技术约束。
- **新增私有 API 依赖** — 本轮不做（§2.5）。例外仅限已落地的点赞（`/p1/.../like`）。
- **原生发评论 / 回帖** — **不做（2026-09-28 决策）**。全部评论写端点强制 `turnstileToken`（Cloudflare Turnstile，在 spec 中为 required 字段），且取令牌的 `GET /p1/turnstile` 是 redirect_uri 白名单制，第三方客户端无法干净取得；维持跳浏览器出口。详见 `PROBLEM_LOG.md` P-005。
- **观看时长统计** — 不做（两个 API 均无单集时长字段，维持 v1 判断）
- **伪造封面图** — 不做。私有 `User` 有 `site` / `location` / `bio`，但无封面图字段；氛围可用 `AmbientGlow`，不得伪装成"用户封面"
- **伪装成"无限流"的首屏自动加载** — 不做。收藏内容流有触底分页，但**只由用户滚动触发**（每页 50 条，上限 20 页 / 1000 条，与本地同步同一口径）；不接受"进页面先把前几页拉完"这类把首屏扇出放大的做法
- **跨全量收藏的聚合**（评分分布 / 均分 / 生涯档案 / 年度统计）— **本轮不做**。两个 API 均无聚合端点，实现方式只能是页面拉 N 页拼装，违反 §7。要走这条路必须先做 §4-C 的快照化
- **条目讨论聚合（在追动态）** — **不做**。讨论只有 per-subject 端点（`/p1/subjects/{id}/topics`），没有"按我的在追集聚合讨论"的单请求来源；且候选口径（收藏 `updatedAt` 前 3 部）不保证内容有用。已落地一版并于 2026-09-28 移除，见 §5-P0

---

## 7. 验收口径

### 7.1 高频页面请求纪律（**硬约束，2026-09-28 立**）

> **高频页面（首页 / 时刻表 / 个人页 / 详情页 / 播放页）只读本地与内存缓存，不得在页面内串联多次请求拼装一个功能。**
> 跨全量数据的聚合一律由后台 Worker 或用户主动刷新承担，结果以快照形式落 DataStore / Room，页面只读快照。

判据与操作化：

- **看的是"缓存失效后一次交互的真实扇出"**，不是"有没有缓存"。内存 TTL 缓存不算数——进程重启即失效，冷启动会重付
- 一次页面渲染的请求数应与其**独立数据块数量同阶**（详情页 3 个数据块 = 3 次，是正例）
- 超出的部分只有两种改法：**下沉到后台**（Worker / 用户主动刷新 + 快照），或**不做**
- 反面案例见 §5-P1（评分成就区，`ceil(N/50)` 次 / 上限 20 次）与 §5-P0（在追动态卡）。前者是"一个数据块拆成 N 页"，后者是"每次进页面重付但请求量看起来很小"，后者更隐蔽，其量级只有 1–3 次、容易被放过
- **注意区分**：在追动态卡的**移除主因是产品价值不成立**（选材口径 / 点击落点），请求面是次因；"现场拉取"本身并不足以否掉一个区块——第 ④ 层收藏流也是每分区 1 次实时请求。真正的判据始终是 §7.1 的第二句（请求数与独立数据块数量同阶），见 §7.1.1

正例参考（同一仓库，同一页面）：

| 位置 | 请求数 | 为什么合规 |
|---|---|---|
| 详情页 `SubjectDetailViewModel.refresh` | 3（条目 / 分集 / 收藏） | 3 个独立数据块，各 1 次；社区段惰性加载且只拉一次 |
| 后台 `BgmSyncWorker` | 2–3 / 轮 | 快照走 CDN 单请求（AniList 扫描在 CI 侧完成）+ 在看列表 1–2 页；有网络/电量约束与节流 |
| 个人页头部 + 数字带 + Tab 计数 | 0（计数为 TTL 缓存） | 前两层纯本地；第三层读缓存 |
| 个人页当前分区内容流 | 1 | 一个分区 = 一个独立数据块，进入分区时才拉；切 Tab 才产生新块（用户主动） |

#### 7.1.1 必须如实记录的一条例外（2026-09-28）

> **个人页第 ④ 层不是"只读本地与内存缓存"，它是每个分区 1 次的实时请求。**

不能假装它是合规的纯读缓存。**它是 §7.1 判据第二句（"请求数与独立数据块数量同阶"）意义上的合规，不是第一句字面上的合规。** 两者冲突时以判据为准，但必须把冲突写下来，且要能解释为什么它与已移除的"在追动态卡"不是同一件事：

| 维度 | 在追动态卡（已移除） | 收藏内容流（已保留） |
|---|---|---|
| 与页面的关系 | **附加功能**——数据取自 per-subject 讨论端点，"按我的在追集聚合讨论"没有单请求来源 | **页面主体内容本身**——当前分区的列表就是第 ④ 层 |
| 请求量的决定因素 | N = min(在追部数, 3)，参与方取"收藏 `updatedAt` 最近前 3 部"——与用户实际想看什么无关 | 恒为 1 / 分区；换分区才是新块，且由用户主动触发 |
| 缓存 | 无跨会话缓存，离开 >5s 重付；且不影响"它没用"这个更根本的结论 | 同样无跨会话缓存（`collectionsByType` 随 ViewModel 销毁） |

**已评估但本轮未采用的第三条路**：把收藏列表也做成后台快照（页面纯读 Room）。不做，因为 `UserCollectionEntity` 目前只有 `userId / subjectId / subjectType / type / epStatus / updatedAt`——**没有 title / cover / rate / comment**，要做快照得先扩列，再把同步面从"仅 DOING"扩到全量收藏（上千条）；这是独立一期的工作量，且与 §5-P1 里"重做评分叙事必须先做 §4-C 快照化"是同一件事。**若将来做了这个快照，第 ④ 层应顺势改为纯读本地。**

左右滑动切换分区（小红书式）未做，原因见 §5-P0「未做」——与请求纪律无关，是单滚动容器 + 头部移出视口的嵌套滚动问题。

### 7.2 其余口径

- 每项通过 `bash tools/jgate`
- 新增 UI 区块**必须同时更新 skeleton 几何**（先例 `7a48a777`）
- 不新增架构红线；引入新远端只读聚合前，先在 `ArchitectureRulesTest` 留约束
- 远端失败一律 fail-open（区块不显示，不崩宿主页面）
- **私有 API 不再新增依赖**：本轮 PR 不得引入新的 `/p1` 调用（除已落地的点赞）。若将来确需引入，须在代码注释写明"私有 API，无兼容性保证"并保证失败可降级，且单独一个提交

---

## 8. 未核实项（规范级核实之外）

规范级核实不等于运行时核实。**评分洞察部分已随 P1 撤销，故第 4、5 项不再阻塞任何在做的功能**，仅作将来重做时的核对清单：

1. ~~`/p1/users/{username}` 的 `stats.subject` 实际 JSON 结构~~ → **本轮不需要**（收口决策，§2.5）
2. ~~`/p1` 端点用现有 OAuth Bearer 是否全部放行~~ → **已核实（2026-09-28）**：165 个端点里**仅 `GET /p1/me` 只声明 `CookiesSession`**，其余 204 个（含全部社交 / 时间线读端点）Bearer 均可用。因本轮不引入 `/p1` 依赖，该空位无需填补
3. ~~`/p1/timeline` 的 `mode` 参数取值~~ → **本轮不需要**（时间线押后）
4. v0 `/v0/users/{username}/collections` 在**未登录**状态下能否读到自己（`OptionalHTTPBearer`）以及私有收藏的可见性
5. ~~`rate = 0` 的实际语义（未评分 vs 评 0 分）~~ → **随 P1 撤销而不再相关**。曾按「未评分」处理（`rate in 1..10` 才算有效）；若将来重做评分叙事，需先确认该口径
6. **`/v0/me` 的 `reg_time` 实测响应体** —— **已容错落地，不再阻塞**：`UserProfile.regTime` 可空，`registeredYear` 只做「提取 4 位数字 + 合理性区间」而不依赖具体日期格式，字段缺失或格式不符即返回 null、头部整行不渲染（fail-open）。仍建议首次真机验证时顺带核对字段名，但不构成开工前提
