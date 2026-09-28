# MiniBgm 个人中心架构迁移设计 (Profile Hub Redesign)

> **状态：v3 设计 + P1 已实施（2026-09-28）。** §5 标注 ✅ 的项已落地并过 `tools/jgate`。
> **v2 修订（2026-09-28）**：v1 的数据面结论基于仓库既有假设与二手资料，**存在错误**——尤其"社交数字无端点"。v2 改为逐条核对两份**官方 OpenAPI 规范原文**，已在 §2.4 列出全部更正。
> **v3 修订（2026-09-28，决策）**：私有 API 能力虽已核实存在，但**决定不引入依赖**——成就带走 v0-only，不扩展凭据层承载 CookieSession，原生发评论不做。v2 中两处以私有 API 为数据源的表述同时作废，见 §2.5。
> **实施修订（2026-09-28）**：原 P1「追番成就带」与 P3「评分叙事」**合并为「评分成就区」**并落地——纯数字行与已落地的头部统计条同形，合并后才有形态区分（§3 形态纪律）。
> 关联文档：[ROADMAP.md §7 个人中心板块](../ROADMAP.md)、[UX_REMEDIATION.md](../UX_REMEDIATION.md)

---

## 0. 结论速览

主流社区个人页是**四层骨架**：身份头部 → 数字带 → 分区 Tab → 内容流。第二层「数字带」是平台分叉点——社交型（微博/小红书）用关注粉丝获赞，数据型（AniList/Steam）用累计数据。

**Bangumi 的实际情况比 v1 判断的宽松得多**：官方 v0 API 确实没有社交端点，但项目**已在依赖**的私有 API（`next.bgm.tv/p1`）**有完整的好友 / 关注者 / 时间线 / 用户统计**，且官方自己就定义了个人主页板块模型（「时光机」）。

**但能力存在不等于要用。v3 决策：私有 API 收口——本轮设计与实现只依赖 v0（Bearer），不引入任何新的 `/p1` 依赖。** 落点：

- 「不做社交数字」的性质从**被迫**改为**主动选择**（定位决策，非能力约束）；可得性已论证完，将来若要重启社交方向不必再查
- 成就带改走 **v0-only**：计数用**已接线**的 `api.bgm.tv/user/{u}/collections/status`，入站年数用 `/v0/me` 的 `reg_time`，评分明细用 `/v0/users/{u}/collections`
- 因而不扩展凭据层、不承载 CookieSession，不触碰 §7 的「凭据只存在于 `AuthTokensDataSource`」红线
- `UserHomepage` 板块模型仍作为**结构参考**（§5-P6），但不依赖 `GET /p1/home` 拉取
- **已落地（2026-09-28）**：② 评分成就区。落点与形态纪律见 §3、§5-P1

---

## 1. 现状（已落地，2026-09）

| 能力 | 实现位置 | 数据来源 |
|---|---|---|
| 统计并入身份头部 | `feature/user/.../UserProfileCards.kt` → `TrackingStatsRow` | Room 本地聚合 |
| 在追动态行式化 | `feature/user/.../UserSubjectActivityCard.kt` + `UserViewModel.subjectActivityFlow` | `/p1/subjects/{id}/topics` |
| 收藏总览叙事化 | `feature/user/.../UserCollectionOverviewCard.kt` | v0 legacy `/user/{u}/collections/status` |
| 头部骨架几何对齐 | `feature/user/.../UserScreenSkeleton.kt` | — |
| **评分成就区**（均分 + 1–10 分布柱图） | `feature/user/.../UserRatingInsightsCard.kt` + `CollectionRepository.fetchRatingInsights` | v0 `/v0/users/{u}/collections`（`rate`） |
| 入站年限（头部次要文字） | `core:model/UserProfile.regTime` + `registeredYear` | v0 `/v0/me` 的 `reg_time` |
| 评分成就区骨架几何 | `feature/user/.../UserScreenSkeleton.kt` → `RatingInsightsSkeleton` | — |

本地聚合口径见 `CollectionRepository.observeTrackingFootprint()`；评分聚合为远端拉取 + 内存 TTL 缓存（**不建表**）。DB 为 `version = 9`，**至今无 schema 迁移**。

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

**Bangumi 官方自己就定义了个人主页的板块模型**——左右两栏、10 种可选板块。这意味着设计方向不是"把微博/小红书的架构搬过来"，而是**对齐官方时光机模型**：主流四层骨架里的"第 3 层分区"在 Bangumi 语境下天然是"板块"，而不是"Tab"。

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

**`GET /p1/me` 的处置**：它是全部 165 个端点里**唯一只声明 `CookiesSession`、不接受 `HTTPBearer`** 的端点。要让它可用，需在 `AuthTokensDataSource` 新增 cookie 槽位并注入 `chiiNextSessionID`——这是一次凭据层扩展。**决定：不做。** 依赖它的只有 `stats.friend/group/blog` 与 `joinedAt`，两者对「评分成就区」都非必须，且 `joinedAt` 已有 v0 替代（`reg_time`）。

**连带结论**：§2.2 列出的社交 / 时间线端点虽技术可用（Bearer 即可，与已落地的 `/p1/.../like` 同源），本轮一律**不使用**——理由见 §3.1。

---

## 3. 目标结构

对齐官方时光机板块模型，五区块自上而下：

| # | 区块 | 状态 | 数据源 | 请求成本 |
|---|---|---|---|---|
| ① | 身份头部 + 统计条 | ✅ 已落地 | 本地聚合 | 0 |
| ② | **评分成就区**（均分大字 + 1–10 分布柱图） | ✅ 已落地 | v0 `/v0/users/{u}/collections`（`subject_type=2&type=2`，含 `rate`） | ceil(N/50)，页数封顶 20 |
| ③ | **继续观看行** | 🆕 | 本地续播 DataStore（需补映射，见 §5-P2） | 0 |
| ④ | 收藏总览 | ✅ 已落地 | v0 legacy `/user/{u}/collections/status`（**维持现状，不迁移**） | 0（TTL 缓存内） |
| ⑤ | 在追动态 | ✅ 已落地 | `/p1/subjects/{id}/topics`（已落地，唯一存量 `/p1` 读） | N（在追前 3） |
| ⑤′ | **友邻在看** | ✗ 本轮不做 | 私有 API `/p1/me/friends/subject-collections` | — |

**形态纪律（2026-09-28 实施时确定）**：第二层只放**一种**数字形态。

- 「数字行」全页**只有一排**——即已落地的 `TrackingStatsRow`（在看 / 累计追集 / 本月打卡），不再叠加第二排同形数字格
- 成就叙事改用**图表形态**区分：均分大字 + 1–10 分布柱图（`RatingInsightsCard`），与数字行形成「读数字 / 看形状」的分工
- **注册时间不占格子**：降级为头部一行次要文字（`$year 年加入 · 已 N 年`），与 GitHub / Twitter 头部的 "Joined …" 同构；`regTime` 缺失时整行不渲染
- 原设计里的「入站年数 / 均分 / 看过」三格数字行**已废弃**——它与统计条同形，会形成两排重复数字

### 3.1 为什么仍然不做社交数字带

v1 的第一条理由是"数据不存在"——**这条已被推翻**。修正后的理由只剩两条，但依然成立：

1. **定位决策**：ROADMAP §7 已把定位写成"安静的追番工具个人主页，不向社区 App 的社交数字看齐"。数据可得 ≠ 应该展示。
2. **范式验证**：AniList / Steam 用累计数据填第二层，叙事强度不弱于社交数字，且不产生社交压力。
3. **依赖收口**：私有 API 无兼容性保证，本轮不新增 `/p1` 依赖（§2.5）。这条同时否掉 ⑤′ 友邻在看与 P6 的 `GET /p1/home`。

但**结论的强度变了**：这是**产品选择**，不是**能力被迫**。若将来要重启社交方向，`/p1/friends`、`/p1/followers` 的 `total` 字段可以直接支撑"好友 N · 关注者 M"，不必再论证可行性。

---

## 4. 关键技术决策：Watch Insights 要不要 Room 迁移

`rate` / `comment` **远端本来就有**，迁移不是必要条件。三条路径：

### A. Room 迁移
`user_collections` 加 `rating` / `comment` 列 + 逐集进度表 + `Migration(9, 10)`。
- ✅ 离线可用、SQL 聚合快
- ❌ **真正的成本不在加两列**：当前本地只同步 DOING（type=3），要支持"看过"分布得把同步面扩到全部收藏（上千条），本地库体积与首次同步耗时显著上升

### B. 零迁移 · 按需拉取 + 内存 TTL 缓存 ← **推荐，已实施（2026-09-28）**
新增 `fetchRatingInsights(username, force)`：分页拉 v0 collections（`subject_type=2&type=2`）聚合直方图/均分，按 `fetchCollectionCounts` 的既有模式缓存。
- ✅ **实施结果**：`CollectionRepositoryImpl.fetchRatingInsights()` 走**串行分页**（`RATING_PAGE_SIZE = 50`、`MAX_RATING_PAGES = 20`），复用 `countsMutex` + `CachedRatingInsights` TTL 缓存；`DB version` 仍为 9，**零迁移**
- ✅ 零迁移、不膨胀本地库、数据新鲜
- ✅ **复用仓库既有先例**：`CollectionRepositoryImpl.fetchCollectionCounts()`（`:242`）已是"网络拉取 + `CachedCounts` TTL 缓存、不建表"
- ⚠️ 成本（**已按真实上限修正**）：`ceil(N/50)` 请求；1000 部 ≈ 20 请求。实施取**串行**而非并发——服务端无限流提示，但 20 次串行分页的固定开销远小于触发风控的风险，页数另封顶 20 兜住 `total` 异常
- ❌ 首次进页面有等待（骨架屏覆盖）；离线 fail-open 隐藏

### C. 轻量聚合缓存表
只存聚合结果（直方图/均分/快照时间）一行 JSON。
- ✅ 离线可读、体积小
- ❌ 仍要迁移

**推荐 B 起步，量级或离线需求上升后升级 C。** 另外**计数类数据的既有实现已足够**——v0 legacy `/user/{u}/collections/status` + TTL 缓存，既不需要迁移，也不需要改走私有 API。

---

## 5. 分期

### P0 · 已落地
身份头部 + 统计条、收藏总览、在追动态、骨架几何对齐。

### P1 · 评分成就区（✅ 已落地 2026-09-28，v0-only、零迁移、零新依赖）

原 P1「追番成就带」与 P3「评分叙事」**合并**为本项——纯数字行与已落地的统计条同形，合并后才有形态区分（见 §3 形态纪律）。

- **数据源**（全部 v0 + Bearer）：
  - 均分 / 1–10 分布 → `/v0/users/{username}/collections`（`subject_type=2&type=2`，`rate` 为 required；`rate = 0` 视为未评分，从均分与分布中排除）
  - 入站年限 → `/v0/me` 的 `reg_time`（**已补映射**），仅作头部次要文字
  - 收藏计数 → 复用**已接线**的 `api.bgm.tv/user/{username}/collections/status`（`fetchCollectionCounts`，TTL 缓存命中即 0 请求），本轮**未新增调用**
- **实现落点**：
  - `core:model`：`UserProfile.regTime`（`@SerialName("reg_time")`，可空）+ `registeredYear`（提取 4 位年份并做合理性区间校验，格式不符返回 null）
  - `core:data`：`CollectionRepository.fetchRatingInsights(username, force)` + `RatingInsights{ratedCount, averageRate, distribution, truncated}`；串行分页聚合，复用 `countsMutex` + `CachedRatingInsights` TTL 缓存（与 `CachedCounts` 同模式），写操作经 `clearCachedAggregates()` 统一失效
  - `feature:user`：新增 `RatingInsightsCard`（均分大字 + 1–10 柱图，空分档留极细基底）；`UserProfileCards` 头部加入站年限行；`UserScreen` 接线；`UserScreenSkeleton` 补 `RatingInsightsSkeleton` 等几何
  - `core:testing`：`FakeCollectionRepository` 增 `fetchRatingInsights` 支持
- **降级与边界**：未评分（`ratedCount == 0`）或拉取失败 → 整卡不渲染；加载中且无历史数据 → 等几何骨架；超过 20 页 → `truncated = true`，统计只覆盖已拉取部分
- **不做**：`stats.friend/group/blog`（社交数字，§3.1 主动不做）；`/p1/users/{username}` 与 `/p1/me`（私有 API，本轮收口）

### P2 · 继续观看行
- **现状障碍**（不变）：续播持久化键是 `streamUrl → positionMs`（`SettingsRepository.MAX_PLAYBACK_POSITIONS = 50`），**无 subjectId / 封面**
- **前提**：播放时额外写入 `subjectId / episodeId / 封面URL`（轻量 DataStore）
- 纯客户端，不依赖远端

### P3 · 评分叙事 —— **已并入 P1（2026-09-28）**
- `fetchRatingInsights` + TTL 缓存 + fail-open 已按 §4-B 落地，见 P1
- **未做**：`comment`（短评）虽在同一个响应里，短文叙事本轮不做

### P4 · 友邻在看 —— **本轮不做（2026-09-28 决策）**
- 技术上现成：`/p1/me/friends/subject-collections?subjectType=2` → `FriendSubjectCollectionActivity{user, subject, collectionType, updatedAt}`，Bearer 即可，1 请求
- **不做的两条理由**：它是唯一会引入"社交感"的区块（与"安静追番工具"定位冲突）；同时属新增私有 API 依赖（§2.5 收口）
- 保留为**已论证可行**的候选：将来若重启社交方向，可行性无需再查

### P5 · 年度 Wrapped
依赖 P1 聚合；年度 Top / 最狠评分月 / 追番节奏；图片导出分享。
**注意**：不能用远端 `updated_at` 排期（官方声明不可靠），年度归属需基于本地写入时间或另行设计。

### P6 · 板块化 / Tab（结构参考官方时光机）
- 触发条件：区块 ≥ 5 或单页滚动 > 2.5 屏
- **结构上参考官方 `UserHomepage.left/right` 板块模型**（anime/game/book/music/real/mono/blog/friend/group/index），而非自创 Tab——但这是**设计参照，不是运行时依赖**（不调 `GET /p1/home`）
- 官方模型给了"用户自选板块"的天然扩展位，与"仪表盘一眼全览"不冲突

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
- **无限瀑布流** — 不做；定长区块 + 展开入口即终态

---

## 7. 验收口径

- 每项通过 `bash tools/jgate`
- 新增 UI 区块**必须同时更新 skeleton 几何**（先例 `7a48a777`）
- 不新增架构红线；引入新远端只读聚合前，先在 `ArchitectureRulesTest` 留约束
- 远端失败一律 fail-open（区块不显示，不崩宿主页面）
- **私有 API 不再新增依赖**：本轮 PR 不得引入新的 `/p1` 调用（除已落地的点赞）。若将来确需引入，须在代码注释写明"私有 API，无兼容性保证"并保证失败可降级，且单独一个提交

---

## 8. 未核实项（规范级核实之外）

规范级核实不等于运行时核实。P1 已按**容错**方式落地，因此以下均不构成开工前提，仅作首次真机验证时的核对清单：

1. ~~`/p1/users/{username}` 的 `stats.subject` 实际 JSON 结构~~ → **本轮不需要**（收口决策，§2.5）
2. ~~`/p1` 端点用现有 OAuth Bearer 是否全部放行~~ → **已核实（2026-09-28）**：165 个端点里**仅 `GET /p1/me` 只声明 `CookiesSession`**，其余 204 个（含全部社交 / 时间线读端点）Bearer 均可用。因本轮不引入 `/p1` 依赖，该空位无需填补
3. ~~`/p1/timeline` 的 `mode` 参数取值~~ → **本轮不需要**（时间线押后）
4. v0 `/v0/users/{username}/collections` 在**未登录**状态下能否读到自己（`OptionalHTTPBearer`）以及私有收藏的可见性
5. ~~`rate = 0` 的实际语义（未评分 vs 评 0 分）~~ → **已按「未评分」处理并落地**（`rate in 1..10` 才算有效评分，`0` 与越界值一并排除）。若将来实测证明 `rate = 0` 是"评 0 分"，需回头改口径——但 1–10 分制下该解读不成立，风险低
6. **`/v0/me` 的 `reg_time` 实测响应体** —— **已容错落地，不再阻塞**：`UserProfile.regTime` 可空，`registeredYear` 只做「提取 4 位数字 + 合理性区间」而不依赖具体日期格式，字段缺失或格式不符即返回 null、头部整行不渲染（fail-open）。仍建议首次真机验证时顺带核对字段名，但不构成开工前提
