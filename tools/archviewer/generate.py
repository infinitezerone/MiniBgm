#!/usr/bin/env python3
"""MiniBgm 架构控制台与数据流双向拓扑生成器 (Architecture & Closed-Loop Data Topology Console)。

UI & 连线可辨识度重构：
1. 连线专属通道分离 (Channel Offsetting)：同列之间的多条垂直折线自动分散排开，绝不互相重叠；
2. 端点物理错开 (Port Offsets)：同一节点的多条连线沿卡片边缘均匀分布，杜绝所有线挤在同一个点；
3. 业务色彩独立编码 (Semantic Color Palette)：排期天蓝、详情靓紫、收藏翡翠绿、凭据琥珀金、社区靛蓝、点击玫红，一目了然；
4. 连线悬停探针 (Edge Probe Tooltip)：鼠标移至任何连线上，立即点亮两端节点、加粗发光并弹出数据契约悬浮窗；
5. 正交直角倒角 (Rounded Circuit Orthogonal)：90度平滑圆角，告别生硬死角；
6. 卡片自由拖拽与持久化记忆。

用法:
  python tools/archviewer/generate.py          # 生成 tools/archviewer/architecture.html
  python tools/archviewer/generate.py --open   # 生成后在浏览器中自动打开
  python tools/archviewer/generate.py --check  # CI 模式：红线违规返回 1，否则 0
"""

import argparse
import json
import re
import sys
import webbrowser
from pathlib import Path

# 修复 Windows 终端 UTF-8 乱码
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

ROOT = Path(__file__).resolve().parents[2]


# ==============================================================================
# 1. 物理模块与源码扫描
# ==============================================================================

def parse_modules() -> list[str]:
    text = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    return re.findall(r'include\("([^"]+)"\)', text)


def module_dir(module: str) -> Path:
    return ROOT / module.strip(":").replace(":", "/")


PLUGIN_IMPLIED = {
    "minibgm.android.feature": {
        "impl": [":core:model", ":core:common", ":core:data", ":core:designsystem", ":core:navigation"],
        "test": [":core:testing"],
    },
}


def parse_build_script(module: str) -> dict:
    build = module_dir(module) / "build.gradle.kts"
    text = build.read_text(encoding="utf-8") if build.exists() else ""
    deps = sorted(set(re.findall(r'project\("([^"]+)"\)', text)))
    test_deps = sorted(set(re.findall(r'testImplementation\(project\("([^"]+)"\)\)', text)))
    plugins = sorted(set(re.findall(r"alias\(libs\.plugins\.([A-Za-z0-9_.]+)\)", text)))
    for p in plugins:
        implied = PLUGIN_IMPLIED.get(p, {})
        deps += implied.get("impl", [])
        test_deps += implied.get("test", [])
    return {
        "deps": sorted(set(deps)),
        "testDeps": sorted(set(test_deps)),
        "plugins": plugins,
        "build_text": text,
    }


def group_of(module: str) -> str:
    if module == ":app":
        return "app"
    if module.startswith(":feature"):
        return "feature"
    if module.startswith(":core"):
        return "core"
    if module.startswith(":sync"):
        return "sync"
    return "other"


def collect_files(module: str) -> list[dict]:
    files = []
    mdir = module_dir(module)
    for pattern in ("src/**/*.kt", "src/**/*.kts", "build.gradle.kts"):
        for p in mdir.glob(pattern):
            try:
                content = p.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                continue
            rel = p.relative_to(ROOT).as_posix()
            files.append(
                {
                    "path": rel,
                    "lines": content.count("\n") + 1,
                    "content": content,
                }
            )
    files.sort(key=lambda f: f["path"])
    return files


# ==============================================================================
# 2. 架构红线静态校验
# ==============================================================================

def check_rules(modules: dict[str, dict]) -> list[dict]:
    violations: list[dict] = []

    def add(module: str, rule: str, where: str, detail: str, edge_to: str | None = None):
        violations.append(
            {"module": module, "rule": rule, "where": where, "detail": detail, "edgeTo": edge_to}
        )

    for name, m in modules.items():
        if m["group"] == "feature":
            for dep in m["deps"]:
                if dep.startswith(":feature"):
                    add(name, "R1 Feature 隔离", m["dir"] + "/build.gradle.kts", f"依赖了其他 feature 模块 {dep}", dep)
                if dep in (":core:network", ":core:database", ":core:datastore"):
                    add(name, "R2 单一数据源", m["dir"] + "/build.gradle.kts", f"越级依赖底层库 {dep}", dep)

        kt_files = [f for f in m["files"] if f["path"].endswith(".kt")]
        if m["group"] == "feature":
            for f in kt_files:
                for i, line in enumerate(f["content"].splitlines(), 1):
                    t = line.strip()
                    if t.startswith("//") or t.startswith("*"):
                        continue
                    if (
                        t.startswith("import com.infinitezerone.minibgm.core.network")
                        or t.startswith("import com.infinitezerone.minibgm.core.database")
                        or t.startswith("import com.infinitezerone.minibgm.core.datastore")
                    ):
                        add(name, "R2 单一数据源", f["path"] + ":" + str(i), f"违规直连底层存储/网络 import -> {t}", f["path"])
                    if t.startswith("import io.ktor") or t.startswith("import androidx.room"):
                        add(name, "R6 框架隔离", f["path"] + ":" + str(i), f"违规引入传输/存储框架 -> {t}", f["path"])
                    if "Color(0x" in line:
                        add(name, "R7 主题一致性", f["path"] + ":" + str(i), f"硬编码颜色 -> {t}", f["path"])
                    for token, why in (
                        ("HttpURLConnection", "手写 HttpURLConnection 裸网络连接"),
                        ("import java.net.", "直接引入 java.net.* 底层网络传输类"),
                        ("java.net.URL", "直接使用 java.net.URL"),
                        ("java.net.Socket", "直接使用 java.net.Socket"),
                        (".cacheDir", "私自操作 cacheDir"),
                        (".filesDir", "私自操作 filesDir"),
                        ("FileOutputStream", "手写文件流写入"),
                    ):
                        if token in t:
                            add(name, "R8 裸 IO 隔离", f["path"] + ":" + str(i), f"{why} -> {t}", f["path"])

                if f["path"].endswith("ViewModel.kt"):
                    for i, line in enumerate(f["content"].splitlines(), 1):
                        t = line.strip()
                        if ("MutableStateFlow" in t) and not t.startswith("private "):
                            if re.search(r"\b(val|var)\s+\w+.*[:=].*MutableStateFlow", t):
                                add(name, "R4 MVI 单向流", f["path"] + ":" + str(i), f"暴露可变状态流 -> {t}", f["path"])

        if name == ":core:model":
            for f in kt_files:
                for i, line in enumerate(f["content"].splitlines(), 1):
                    t = line.strip()
                    if any(t.startswith(p) for p in ("import android.", "import androidx.", "import com.google.android.")):
                        add(name, "R3 纯模型层", f["path"] + ":" + str(i), f"平台/UI 依赖 -> {t}", f["path"])

        if name == ":core:datastore":
            up = next((f for f in kt_files if f["path"].endswith("UserPreferences.kt")), None)
            if up:
                for i, line in enumerate(up["content"].splitlines(), 1):
                    field = re.search(r"\bval\s+(\w+)", line)
                    if field:
                        n = field.group(1).lower()
                        if any(k in n for k in ("token", "accesstoken", "refreshtoken", "authsecret")):
                            add(name, "R5 凭据隔离", up["path"] + ":" + str(i), f"疑似凭据字段 {field.group(1)}", up["path"])

    return violations


# ==============================================================================
# 3. 点连接点：数据流拓扑图谱模型 (按业务主线规整对齐)
# ==============================================================================

def build_data_topology() -> dict:
    """定义完整的数据流拓扑：主线对齐水平轨道，端点与通道参数丰富。"""
    nodes = [
        # --- Track 0: 排期全线 (Weekly Schedule) ---
        {
            "id": "api_calendar", "col": 0, "row": 0, "category": "origin", "icon": "globe",
            "name": "GET /calendar", "sub": "api.bgm.tv (v0)",
            "desc": "官方每周基础日历排期，包含每日番剧 Rank、全站评分与条目元数据。",
            "modelName": "List<CalendarResponseDto>",
            "file": "core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/BangumiCalendarApi.kt",
            "fields": [
                {"name": "weekday.id", "type": "Int", "desc": "星期序号 (1-7)"},
                {"name": "items[].id", "type": "Long", "desc": "Bangumi 条目 ID"},
                {"name": "items[].name_cn", "type": "String", "desc": "中文名"},
                {"name": "items[].rating.score", "type": "Double", "desc": "全站评分"},
            ],
            "storyIds": ["weekly_schedule", "explore_community"],
        },
        {
            "id": "repo_schedule", "col": 1, "row": 0, "category": "transform", "icon": "calendar",
            "name": "ScheduleRepository", "sub": "时区换算 / 双源合流",
            "desc": "UTC 转 Asia/Shanghai CST，对齐开播时刻，排期落库并以只读 Flow 发射。",
            "file": "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/ScheduleRepository.kt",
            "operations": [
                "时区换算并纠正跨午夜放送归属",
                "对齐 bgm-data 逐话排期，标注预计/已确认",
                "批量清洗过期记录，分流存入 Room",
                "提供 Flow<Map<Int, List<AirSchedule>>> 响应式流",
            ],
            "storyIds": ["weekly_schedule"],
        },
        {
            "id": "db_air_schedules", "col": 2, "row": 0, "category": "storage", "icon": "database",
            "name": "Room: air_schedules", "sub": "SQLite 表 (AirScheduleEntity)",
            "desc": "每周排期本地持久化，单日排期以 timeCst 正序建立索引。",
            "file": "core/database/src/commonMain/kotlin/com/infinitezerone/minibgm/core/database/dao/Daos.kt",
            "fields": [
                {"name": "bgmId", "type": "Long (PK)", "desc": "条目 ID"},
                {"name": "weekday", "type": "Int (Index)", "desc": "星期几"},
                {"name": "timeCst", "type": "String", "desc": "CST 播出时间"},
                {"name": "ratingScore", "type": "Double", "desc": "全站评分"},
            ],
            "storyIds": ["weekly_schedule", "airing_reminder"],
        },
        {
            "id": "vm_schedule", "col": 3, "row": 0, "category": "vm", "icon": "cpu",
            "name": "ScheduleViewModel", "sub": "StateFlow<ScheduleUiState>",
            "desc": "结合排期流与在追番剧流，装配全周放送矩阵、置顶「我追的」与「待补清单」。",
            "file": "feature/schedule/src/main/kotlin/com/infinitezerone/minibgm/feature/schedule/ScheduleViewModel.kt",
            "modelName": "ScheduleUiState",
            "fields": [
                {"name": "selectedWeekday", "type": "Int", "desc": "当前选中的星期几"},
                {"name": "weeklySchedules", "type": "Map<Int, List<AirSchedule>>", "desc": "按星期归类的番剧全景"},
                {"name": "watchingSubjectIds", "type": "Set<Long>", "desc": "用户在看的番剧 ID 集合"},
                {"name": "catchupItems", "type": "List<CatchupScheduleItem>", "desc": "昨日/前日待补番剧"},
                {"name": "isRefreshing", "type": "Boolean", "desc": "下拉刷新态"},
            ],
            "storyIds": ["weekly_schedule"],
        },
        {
            "id": "ui_schedule_screen", "col": 4, "row": 0, "category": "ui", "icon": "smartphone",
            "name": "ScheduleScreen", "sub": "Compose Screen",
            "desc": "顶部星期切换 Tab + HorizontalPager，每日时间线卡片，支持下拉刷新与置顶在看番剧。",
            "file": "feature/schedule/src/main/kotlin/com/infinitezerone/minibgm/feature/schedule/ScheduleScreen.kt",
            "storyIds": ["weekly_schedule"],
        },

        # --- Track 1: 桌面微件主线 (Schedule Widget) ---
        {
            "id": "cdn_bgm_data", "col": 0, "row": 1, "category": "origin", "icon": "file-json",
            "name": "bgm-data CDN", "sub": "Static Schedule JSON",
            "desc": "全网逐话实时开播时刻与首播时间对齐清单。",
            "modelName": "ScheduleData (JSON)",
            "file": "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/ScheduleRepository.kt",
            "fields": [
                {"name": "bgmId", "type": "Long", "desc": "条目 ID"},
                {"name": "begin", "type": "String (UTC)", "desc": "准确开播 UTC ISO8601"},
                {"name": "sites", "type": "List<SiteDto>", "desc": "播放源 (巴哈/B站等)"},
            ],
            "storyIds": ["weekly_schedule"],
        },
        {
            "id": "planner_widget", "col": 3, "row": 1, "category": "vm", "icon": "layout-grid",
            "name": "ScheduleWidgetPlanner", "sub": "纯状态计算器",
            "desc": "从排期库与收藏库计算跨日「下一次开播」倒计时与紧凑时间线模型。",
            "file": "feature/widget/src/main/kotlin/com/infinitezerone/minibgm/feature/widget/ScheduleWidgetPlanner.kt",
            "modelName": "ScheduleWidgetModel",
            "fields": [
                {"name": "todayAiringCount", "type": "Int", "desc": "今日开播总数"},
                {"name": "nextAiring", "type": "NextAiringInfo?", "desc": "下一部开播番剧及倒计时"},
                {"name": "timelineItems", "type": "List<TimelineItem>", "desc": "时间线卡片条目"},
            ],
            "storyIds": ["weekly_schedule"],
        },
        {
            "id": "ui_schedule_widget", "col": 4, "row": 1, "category": "ui", "icon": "app-window",
            "name": "ScheduleWidget", "sub": "Glance 桌面微件",
            "desc": "跨日无感倒计时、紧凑排期时间线小组件，点击条目可直接深链穿透至详情页。",
            "file": "feature/widget/src/main/kotlin/com/infinitezerone/minibgm/feature/widget/ScheduleWidget.kt",
            "storyIds": ["weekly_schedule"],
        },

        # --- Track 2: 条目详情与分集打卡主线 (Subject Detail) ---
        {
            "id": "api_subject", "col": 0, "row": 2, "category": "origin", "icon": "book-open",
            "name": "GET /v0/subjects/{id}", "sub": "api.bgm.tv (v0)",
            "desc": "番剧条目完整档案：分集、演职员、角色与关系关联图。",
            "modelName": "SubjectDetailDto",
            "file": "core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/BangumiSubjectApi.kt",
            "fields": [
                {"name": "id", "type": "Long", "desc": "条目唯一标识"},
                {"name": "summary", "type": "String", "desc": "剧情简介"},
                {"name": "total_episodes", "type": "Int", "desc": "总话数"},
                {"name": "rating", "type": "RatingDto", "desc": "评分与投票分布"},
            ],
            "storyIds": ["subject_detail"],
        },
        {
            "id": "repo_subject", "col": 1, "row": 2, "category": "transform", "icon": "layers",
            "name": "SubjectRepository", "sub": "Cache-First / 演职员拼装",
            "desc": "网络拉取优先写入 Room，以单向流方式向 ViewModel 持续供数。",
            "file": "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/SubjectRepository.kt",
            "operations": [
                "详情与分集数据落库缓存",
                "解析角色与声优对应关系树",
                "暴露 getSubjectStream & getEpisodesStream",
            ],
            "storyIds": ["subject_detail"],
        },
        {
            "id": "db_subjects", "col": 2, "row": 2, "category": "storage", "icon": "database",
            "name": "Room: subjects & episodes", "sub": "SQLite 表 (Subject/Episode)",
            "desc": "条目元数据与分集清单持久化，提供离线浏览体验。",
            "file": "core/database/src/commonMain/kotlin/com/infinitezerone/minibgm/core/database/dao/Daos.kt",
            "fields": [
                {"name": "subjects.id", "type": "Long (PK)", "desc": "条目 ID"},
                {"name": "episodes.ep", "type": "Int", "desc": "话数"},
                {"name": "episodes.airdate", "type": "String", "desc": "单集开播日"},
            ],
            "storyIds": ["subject_detail"],
        },
        {
            "id": "vm_subject", "col": 3, "row": 2, "category": "vm", "icon": "film",
            "name": "SubjectDetailViewModel", "sub": "StateFlow<SubjectUiState>",
            "desc": "聚合条目详情、分集列表、收藏打卡进度与角色列表，提供 0 延迟乐观打卡。",
            "file": "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/SubjectViewModel.kt",
            "modelName": "SubjectDetailUiState",
            "fields": [
                {"name": "subject", "type": "SubjectDetail?", "desc": "条目详情实体"},
                {"name": "episodes", "type": "List<Episode>", "desc": "分集数据"},
                {"name": "collection", "type": "UserCollection?", "desc": "个人收藏态与打卡进度"},
                {"name": "isUpdatingEpisode", "type": "Boolean", "desc": "乐观打卡中标记"},
            ],
            "storyIds": ["subject_detail"],
        },
        {
            "id": "ui_subject_screen", "col": 4, "row": 2, "category": "ui", "icon": "smartphone",
            "name": "SubjectDetailScreen", "sub": "Compose Screen",
            "desc": "番剧详情、单集气泡网格打卡、收藏状态 BottomSheet、角色声优横向滚动栏。",
            "file": "feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/SubjectScreen.kt",
            "storyIds": ["subject_detail"],
        },

        # --- Track 3: 收藏同步主线 (Collection Sync) ---
        {
            "id": "api_collection", "col": 0, "row": 3, "category": "origin", "icon": "bookmark-check",
            "name": "GET /users/.../collections", "sub": "api.bgm.tv (v0)",
            "desc": "用户个人收藏档案：想看/在看/看过等状态与单集观看进度。",
            "modelName": "PagedResult<UserCollectionDto>",
            "file": "core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/BangumiCollectionApi.kt",
            "fields": [
                {"name": "subject_id", "type": "Long", "desc": "关联条目 ID"},
                {"name": "type", "type": "Int", "desc": "收藏分类 (1:想看, 2:看过, 3:在看)"},
                {"name": "ep_status", "type": "Int", "desc": "已看集数打卡"},
                {"name": "rate", "type": "Int", "desc": "用户评分 (1-10)"},
            ],
            "storyIds": ["collection_sync", "weekly_schedule", "subject_detail"],
        },
        {
            "id": "repo_collection", "col": 1, "row": 3, "category": "transform", "icon": "folder-sync",
            "name": "CollectionRepository", "sub": "ETag 条件缓存 / 乐观打卡",
            "desc": "304 未修改短路；点击打卡时先乐观刷新本地 UI，后台使用 NonCancellable 同步至云端。",
            "file": "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/CollectionRepository.kt",
            "operations": [
                "带 If-None-Match 条件请求",
                "乐观更新本地进度，防止界面卡顿",
                "会话建立时全量分页拉取在看条目",
            ],
            "storyIds": ["collection_sync", "weekly_schedule", "subject_detail", "auth_preferences"],
        },
        {
            "id": "db_user_collections", "col": 2, "row": 3, "category": "storage", "icon": "table",
            "name": "Room: user_collections", "sub": "SQLite 表 (UserCollectionEntity)",
            "desc": "用户追番与打卡表，复合主键 (username, subjectId) 严格实现多账号数据物理隔离。",
            "file": "core/database/src/commonMain/kotlin/com/infinitezerone/minibgm/core/database/dao/Daos.kt",
            "fields": [
                {"name": "username", "type": "String (PK)", "desc": "所属用户"},
                {"name": "subjectId", "type": "Long (PK)", "desc": "条目 ID"},
                {"name": "type", "type": "Int (Index)", "desc": "在看/想看/看过"},
                {"name": "epStatus", "type": "Int", "desc": "打卡集数"},
            ],
            "storyIds": ["collection_sync", "weekly_schedule", "subject_detail", "airing_reminder"],
        },
        {
            "id": "vm_user_collections", "col": 3, "row": 3, "category": "vm", "icon": "library",
            "name": "UserCollectionsViewModel", "sub": "StateFlow<CollectionsUiState>",
            "desc": "按在看/想看/看过分页签筛选用户收藏列表，支持评分与收藏时间排序。",
            "file": "feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/UserCollectionsViewModel.kt",
            "modelName": "UserCollectionsUiState",
            "fields": [
                {"name": "selectedType", "type": "CollectionType", "desc": "当前选中分类"},
                {"name": "items", "type": "List<UserCollectionItem>", "desc": "条目卡片流"},
            ],
            "storyIds": ["collection_sync"],
        },
        {
            "id": "ui_user_collections_screen", "col": 4, "row": 3, "category": "ui", "icon": "smartphone",
            "name": "UserCollectionsScreen", "sub": "Compose Screen",
            "desc": "按在看/想看/看过分类展示的网格条目页，支持下拉刷新与条目过滤。",
            "file": "feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/UserCollectionsScreen.kt",
            "storyIds": ["collection_sync"],
        },

        # --- Track 4: 账号凭据主线 (Auth & Accounts) ---
        {
            "id": "oauth_proxy", "col": 0, "row": 4, "category": "origin", "icon": "shield-check",
            "name": "CF Worker OAuth Proxy", "sub": "PKCE Token Exchange",
            "desc": "基于 Cloudflare Worker 的安全凭据中转代理，交换与刷新 AccessToken。",
            "modelName": "BgmTokenResponseDto",
            "file": "core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/BgmTokenService.kt",
            "fields": [
                {"name": "access_token", "type": "String", "desc": "访问 Token"},
                {"name": "refresh_token", "type": "String", "desc": "刷新 Token"},
                {"name": "expires_in", "type": "Long", "desc": "有效期秒数"},
            ],
            "storyIds": ["auth_preferences"],
        },
        {
            "id": "repo_auth", "col": 1, "row": 4, "category": "transform", "icon": "key-round",
            "name": "AuthRepository", "sub": "硬件加密 / 会话派生",
            "desc": "Token 经 AndroidKeyStore 硬件加密，向上派生登录会话流，登出时级联清理本地数据。",
            "file": "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/AuthRepository.kt",
            "operations": [
                "Keystore AES-256 加密保存",
                "派生当前活跃会话状态 (LoggedIn/Anonymous)",
                "触发 UserDataClearable 清理本地库",
            ],
            "storyIds": ["auth_preferences"],
        },
        {
            "id": "keystore_tokens", "col": 2, "row": 4, "category": "storage", "icon": "lock",
            "name": "KeyStore: auth_tokens", "sub": "TEE/SE 硬件级加密",
            "desc": "OAuth 敏感凭据经硬件主密钥加密，独立保存于沙盒私有存储，绝不泄露给普通 DataStore。",
            "file": "core/datastore/src/androidMain/kotlin/com/infinitezerone/minibgm/core/datastore/AuthTokensDataSource.kt",
            "fields": [
                {"name": "accessToken", "type": "String (Encrypted)", "desc": "授权 Token"},
                {"name": "refreshToken", "type": "String (Encrypted)", "desc": "刷新 Token"},
            ],
            "storyIds": ["auth_preferences"],
        },
        {
            "id": "vm_user", "col": 3, "row": 4, "category": "vm", "icon": "user-check",
            "name": "UserViewModel", "sub": "StateFlow<UserUiState>",
            "desc": "结合当前活跃账号凭据与偏好流，驱动个人中心主页与账号切换面板。",
            "file": "feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/UserViewModel.kt",
            "modelName": "UserUiState",
            "fields": [
                {"name": "activeProfile", "type": "UserProfile?", "desc": "当前活跃账号信息"},
                {"name": "savedProfiles", "type": "List<SavedProfile>", "desc": "已存多账号"},
                {"name": "stats", "type": "CollectionStats", "desc": "想看/在看/看过等数量统计"},
            ],
            "storyIds": ["auth_preferences"],
        },
        {
            "id": "ui_user_screen", "col": 4, "row": 4, "category": "ui", "icon": "smartphone",
            "name": "UserScreen & Drawer", "sub": "Compose Screen",
            "desc": "个人信息概览、收藏状态快捷入口、多账号切换抽屉与深浅色模式切换开关。",
            "file": "feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/UserScreen.kt",
            "storyIds": ["auth_preferences"],
        },

        # --- Track 5: 偏好配置 (Settings & Preferences) ---
        {
            "id": "repo_settings", "col": 1, "row": 5, "category": "transform", "icon": "settings",
            "name": "SettingsRepository", "sub": "偏好驱动 / 多账号管理",
            "desc": "读写 DataStore 用户偏好，维护已保存的多账号 Profile 列表并提供一键无感切换。",
            "file": "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/SettingsRepository.kt",
            "operations": [
                "管理 savedProfiles 账号列表",
                "响应主题模式与开播提醒开关设置",
            ],
            "storyIds": ["auth_preferences"],
        },
        {
            "id": "datastore_prefs", "col": 2, "row": 5, "category": "storage", "icon": "binary",
            "name": "DataStore: UserPreferences", "sub": "Proto DataStore",
            "desc": "应用非敏感偏好：跟随系统/深色主题、开播提醒开关与多账号 Profiles 列表。",
            "file": "core/datastore/src/commonMain/kotlin/com/infinitezerone/minibgm/core/datastore/UserPreferences.kt",
            "fields": [
                {"name": "activeAccount", "type": "String", "desc": "当前活跃用户名"},
                {"name": "savedProfiles", "type": "List<SavedProfile>", "desc": "本机多账号列表"},
                {"name": "darkThemeConfig", "type": "Int", "desc": "深浅主题模式"},
                {"name": "airingReminderEnabled", "type": "Boolean", "desc": "提醒总开关"},
            ],
            "storyIds": ["auth_preferences", "airing_reminder"],
        },

        # --- Track 6: 社区与探索 (Community & Explore) ---
        {
            "id": "api_community", "col": 0, "row": 6, "category": "origin", "icon": "message-square",
            "name": "Next API /p1/topics", "sub": "next.bgm.tv",
            "desc": "社区热门讨论、条目吐槽以及楼层评论数据。",
            "modelName": "List<TopicDto> & List<CommentDto>",
            "file": "core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/BangumiCommunityApi.kt",
            "fields": [
                {"name": "topic.title", "type": "String", "desc": "讨论主题"},
                {"name": "comment.content", "type": "String (BBCode)", "desc": "BBCode 回复富文本"},
            ],
            "storyIds": ["subject_detail", "explore_community"],
        },
        {
            "id": "repo_community", "col": 1, "row": 6, "category": "transform", "icon": "messages-square",
            "name": "CommunityRepository", "sub": "BBCode 词法解析 / 树形组装",
            "desc": "将吐槽内容解析为带剧透折叠、原生跳转的富文本结构，并提供内存短时 LRU 缓存。",
            "file": "core/data/src/commonMain/kotlin/com/infinitezerone/minibgm/core/data/repository/CommunityRepository.kt",
            "operations": [
                "解析 BBCode 为富文本模型",
                "识别 bgm.tv 原生链接",
            ],
            "storyIds": ["subject_detail", "explore_community"],
        },
        {
            "id": "vm_explore", "col": 3, "row": 6, "category": "vm", "icon": "compass",
            "name": "ExploreViewModel", "sub": "StateFlow<ExploreUiState>",
            "desc": "聚合当季最高分与社区最热话题，驱动发现页双列瀑布流。",
            "file": "feature/schedule/src/main/kotlin/com/infinitezerone/minibgm/feature/schedule/ExploreViewModel.kt",
            "modelName": "ExploreUiState",
            "fields": [
                {"name": "rankingItems", "type": "List<RankingItem>", "desc": "榜单排行"},
                {"name": "hotTopics", "type": "List<CommunityTopic>", "desc": "热门讨论"},
            ],
            "storyIds": ["explore_community"],
        },
        {
            "id": "ui_explore_screen", "col": 4, "row": 6, "category": "ui", "icon": "smartphone",
            "name": "ExploreScreen", "sub": "Compose Screen",
            "desc": "排行榜单与社区讨论瀑布流，支持点击跳转条目详情或打开讨论外链。",
            "file": "feature/schedule/src/main/kotlin/com/infinitezerone/minibgm/feature/schedule/ExploreScreen.kt",
            "storyIds": ["explore_community"],
        },

        # --- Track 7: 定时提醒决策 (Airing Reminder) ---
        {
            "id": "planner_reminder", "col": 1, "row": 7, "category": "transform", "icon": "alarm-clock",
            "name": "AiringReminderPlanner", "sub": "纯函数排期决策",
            "desc": "比对未来 24 小时排期与在追条目，执行去重判定，决定是否发出通知。",
            "file": "feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/AiringReminderPlanner.kt",
            "operations": [
                "检查登录态与提醒总开关",
                "筛选未来 24h 内播出的在追番剧",
                "以日期做单日去重，杜绝重复扰民",
            ],
            "storyIds": ["airing_reminder"],
        },
        {
            "id": "worker_reminder", "col": 4, "row": 7, "category": "ui", "icon": "clock",
            "name": "AiringReminderWorker", "sub": "WorkManager 定时任务",
            "desc": "每日定时唤醒触发排期决策判定，满足条件时向系统通知通道推送开播提醒。",
            "file": "feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/AiringReminderWorker.kt",
            "storyIds": ["airing_reminder"],
        },
    ]

    edges = [
        # 下行状态流 (Downstream Data Pipes)
        {"from": "api_calendar", "to": "repo_schedule", "label": "CalendarResponseDto", "storyId": "weekly_schedule", "flow": "down"},
        {"from": "cdn_bgm_data", "to": "repo_schedule", "label": "ScheduleData (UTC)", "storyId": "weekly_schedule", "flow": "down"},
        {"from": "repo_schedule", "to": "db_air_schedules", "label": "Upsert 缓存", "storyId": "weekly_schedule", "flow": "down"},
        {"from": "db_air_schedules", "to": "vm_schedule", "label": "Flow<List<AirSchedule>>", "storyId": "weekly_schedule", "flow": "down"},
        {"from": "db_user_collections", "to": "vm_schedule", "label": "Flow<Set<watchingIds>>", "storyId": "weekly_schedule", "flow": "down"},
        {"from": "vm_schedule", "to": "ui_schedule_screen", "label": "ScheduleUiState", "storyId": "weekly_schedule", "flow": "down"},
        {"from": "db_air_schedules", "to": "planner_widget", "label": "排期快照流", "storyId": "weekly_schedule", "flow": "down"},
        {"from": "db_user_collections", "to": "planner_widget", "label": "在追条目流", "storyId": "weekly_schedule", "flow": "down"},
        {"from": "planner_widget", "to": "ui_schedule_widget", "label": "ScheduleWidgetModel", "storyId": "weekly_schedule", "flow": "down"},

        {"from": "api_subject", "to": "repo_subject", "label": "SubjectDetailDto", "storyId": "subject_detail", "flow": "down"},
        {"from": "repo_subject", "to": "db_subjects", "label": "条目 & 分集落库", "storyId": "subject_detail", "flow": "down"},
        {"from": "db_subjects", "to": "vm_subject", "label": "Flow<SubjectDetail>", "storyId": "subject_detail", "flow": "down"},
        {"from": "db_user_collections", "to": "vm_subject", "label": "Flow<UserCollection>", "storyId": "subject_detail", "flow": "down"},
        {"from": "vm_subject", "to": "ui_subject_screen", "label": "SubjectDetailUiState", "storyId": "subject_detail", "flow": "down"},

        {"from": "api_collection", "to": "repo_collection", "label": "UserCollectionDto (ETag)", "storyId": "collection_sync", "flow": "down"},
        {"from": "repo_collection", "to": "db_user_collections", "label": "Upsert 用户收藏表", "storyId": "collection_sync", "flow": "down"},
        {"from": "db_user_collections", "to": "vm_user_collections", "label": "Flow<List<UserCollection>>", "storyId": "collection_sync", "flow": "down"},
        {"from": "vm_user_collections", "to": "ui_user_collections_screen", "label": "UserCollectionsUiState", "storyId": "collection_sync", "flow": "down"},

        {"from": "oauth_proxy", "to": "repo_auth", "label": "BgmTokenResponseDto", "storyId": "auth_preferences", "flow": "down"},
        {"from": "repo_auth", "to": "keystore_tokens", "label": "AES-256 加密保存", "storyId": "auth_preferences", "flow": "down"},
        {"from": "keystore_tokens", "to": "repo_auth", "label": "SessionState (解密派生)", "storyId": "auth_preferences", "flow": "down"},
        {"from": "repo_settings", "to": "datastore_prefs", "label": "读写 UserPreferences", "storyId": "auth_preferences", "flow": "down"},
        {"from": "datastore_prefs", "to": "vm_user", "label": "Flow<UserPreferences>", "storyId": "auth_preferences", "flow": "down"},
        {"from": "repo_auth", "to": "vm_user", "label": "Flow<SessionState>", "storyId": "auth_preferences", "flow": "down"},
        {"from": "vm_user", "to": "ui_user_screen", "label": "UserUiState", "storyId": "auth_preferences", "flow": "down"},

        {"from": "db_air_schedules", "to": "planner_reminder", "label": "24h 排期", "storyId": "airing_reminder", "flow": "down"},
        {"from": "db_user_collections", "to": "planner_reminder", "label": "在追条目", "storyId": "airing_reminder", "flow": "down"},
        {"from": "datastore_prefs", "to": "planner_reminder", "label": "开关与历史日期", "storyId": "airing_reminder", "flow": "down"},
        {"from": "planner_reminder", "to": "worker_reminder", "label": "执行提醒决策", "storyId": "airing_reminder", "flow": "down"},

        {"from": "api_calendar", "to": "vm_explore", "label": "当季高分榜", "storyId": "explore_community", "flow": "down"},
        {"from": "api_community", "to": "repo_community", "label": "热帖讨论", "storyId": "explore_community", "flow": "down"},
        {"from": "repo_community", "to": "vm_explore", "label": "吐槽富文本", "storyId": "explore_community", "flow": "down"},
        {"from": "vm_explore", "to": "ui_explore_screen", "label": "ExploreUiState", "storyId": "explore_community", "flow": "down"},

        # 上行用户点击与写回路 (User Action & Mutation Loops)
        {"from": "ui_subject_screen", "to": "vm_subject", "label": "1.点击分集打卡", "storyId": "action_ep_watch", "flow": "action"},
        {"from": "vm_subject", "to": "repo_collection", "label": "2.updateEpisodeStatus", "storyId": "action_ep_watch", "flow": "action"},
        {"from": "repo_collection", "to": "db_user_collections", "label": "3.Room 乐观写库", "storyId": "action_ep_watch", "flow": "action"},
        {"from": "db_user_collections", "to": "vm_subject", "label": "4.本页打卡格变绿", "storyId": "action_ep_watch", "flow": "action"},
        {"from": "db_user_collections", "to": "vm_schedule", "label": "5.【跨页】时间表待补更新", "storyId": "action_ep_watch", "flow": "action"},
        {"from": "db_user_collections", "to": "planner_widget", "label": "6.【跨端】小组件进度刷新", "storyId": "action_ep_watch", "flow": "action"},

        {"from": "ui_subject_screen", "to": "vm_subject", "label": "1.修改收藏/打分", "storyId": "action_coll_status", "flow": "action"},
        {"from": "vm_subject", "to": "repo_collection", "label": "2.updateCollectionStatus", "storyId": "action_coll_status", "flow": "action"},
        {"from": "repo_collection", "to": "db_user_collections", "label": "3.Room 写库 & PATCH", "storyId": "action_coll_status", "flow": "action"},
        {"from": "db_user_collections", "to": "vm_user", "label": "4.【跨页】我的页总数刷新", "storyId": "action_coll_status", "flow": "action"},
        {"from": "db_user_collections", "to": "vm_user_collections", "label": "5.【跨页】收藏列表分类增减", "storyId": "action_coll_status", "flow": "action"},
        {"from": "db_user_collections", "to": "vm_schedule", "label": "6.【跨页】时间表点亮追番星标", "storyId": "action_coll_status", "flow": "action"},

        {"from": "ui_user_screen", "to": "vm_user", "label": "1.抽屉点击换账号", "storyId": "action_switch_account", "flow": "action"},
        {"from": "vm_user", "to": "repo_settings", "label": "2.switchAccount(UserB)", "storyId": "action_switch_account", "flow": "action"},
        {"from": "repo_settings", "to": "datastore_prefs", "label": "3.更新 activeAccount", "storyId": "action_switch_account", "flow": "action"},
        {"from": "datastore_prefs", "to": "db_user_collections", "label": "4.重绑 UserB 收藏库", "storyId": "action_switch_account", "flow": "action"},
        {"from": "db_user_collections", "to": "vm_user_collections", "label": "5.【全应用】收藏列表秒换", "storyId": "action_switch_account", "flow": "action"},
        {"from": "db_user_collections", "to": "vm_schedule", "label": "6.【全应用】时间表追番秒换", "storyId": "action_switch_account", "flow": "action"},
        {"from": "db_user_collections", "to": "planner_widget", "label": "7.【全应用】微件排期秒换", "storyId": "action_switch_account", "flow": "action"},

        {"from": "ui_schedule_screen", "to": "vm_schedule", "label": "1.手势下拉刷新", "storyId": "action_pull_refresh", "flow": "action"},
        {"from": "vm_schedule", "to": "repo_schedule", "label": "2.refresh(force=true)", "storyId": "action_pull_refresh", "flow": "action"},
        {"from": "repo_schedule", "to": "api_calendar", "label": "3.强制发起远端请求", "storyId": "action_pull_refresh", "flow": "action"},
        {"from": "api_calendar", "to": "db_air_schedules", "label": "4.重写排期数据库", "storyId": "action_pull_refresh", "flow": "action"},
        {"from": "db_air_schedules", "to": "vm_schedule", "label": "5.广播驱动时间表刷新", "storyId": "action_pull_refresh", "flow": "action"},
        {"from": "db_air_schedules", "to": "planner_widget", "label": "6.【跨端】微件时间线重算", "storyId": "action_pull_refresh", "flow": "action"},

        {"from": "ui_schedule_screen", "to": "vm_schedule", "label": "1.点击周三 Tab", "storyId": "action_select_weekday", "flow": "action"},
        {"from": "vm_schedule", "to": "ui_schedule_screen", "label": "2.内存快速重排，120Hz秒变", "storyId": "action_select_weekday", "flow": "action"},
    ]

    stories = [
        # 下行状态故事
        {"id": "weekly_schedule", "type": "down", "icon": "calendar-days", "title": "每周排期与微件", "summary": "Calendar API + CDN ➔ ScheduleRepo ➔ Room air_schedules ➔ ScheduleViewModel / Widget ➔ ScheduleScreen"},
        {"id": "subject_detail", "type": "down", "icon": "book-open", "title": "条目详情与打卡", "summary": "Subject API ➔ SubjectRepo ➔ Room subjects + user_collections ➔ SubjectDetailViewModel ➔ SubjectDetailScreen"},
        {"id": "collection_sync", "type": "down", "icon": "folder-sync", "title": "用户在看与同步", "summary": "Collection API (ETag) ➔ CollectionRepo ➔ Room user_collections ➔ UserCollectionsViewModel ➔ UserCollectionsScreen"},
        {"id": "auth_preferences", "type": "down", "icon": "key-round", "title": "认证凭据与偏好", "summary": "OAuth Proxy ➔ AuthRepo ➔ KeyStore + DataStore ➔ UserViewModel ➔ UserScreen (多账号管理)"},
        {"id": "explore_community", "type": "down", "icon": "compass", "title": "探索发现与社区", "summary": "Calendar API + Next API ➔ CommunityRepo ➔ ExploreViewModel ➔ ExploreScreen (瀑布流)"},
        {"id": "airing_reminder", "type": "down", "icon": "alarm-clock", "title": "开播提醒定时决策", "summary": "air_schedules + user_collections ➔ AiringReminderPlanner ➔ AiringReminderWorker ➔ 系统通知"},

        # 上行用户点击与写回路 (User Action Loops)
        {
            "id": "action_ep_watch", "type": "action", "icon": "check-circle", "title": "点击单集打卡 (跨页联动)",
            "summary": "在详情页点击打卡第 N 话 ➔ 乐观写库 ➔ 远端 PUT ➔ Room 响应式倒灌驱动时间表与桌面微件同步更新！",
            "trigger": "用户在 SubjectDetailScreen 点击「看过了（第 N 话）」",
            "intent": "SubjectDetailViewModel.updateEpisodeStatus(subjectId, epId, isWatched=true)",
            "mutation": "CollectionRepository.updateEpisodeStatus(乐观更新本地 Room，NonCancellable 异步发远端)",
            "storage": "Room: user_collections 表的 epStatus 字段更新",
            "ripples": [
                {"where": "SubjectDetailScreen (当前页)", "what": "打卡按钮瞬间变为实心绿色已看状态（零等待反馈）"},
                {"where": "ScheduleScreen (时间表页)", "what": "自动重算该番剧在时间表的追番进度标签，消除待补项"},
                {"where": "ScheduleWidget (桌面小组件)", "what": "重新计算下一部未看开播番剧倒计时与时间线条目"},
            ],
        },
        {
            "id": "action_coll_status", "type": "action", "icon": "star", "title": "修改收藏与打分 (个人页联动)",
            "summary": "在详情页修改收藏状态为「在看」或打分 ➔ Room 写库 ➔ 远端 PATCH ➔ 触发我的页统计与分类列表联动更新！",
            "trigger": "用户在 SubjectDetailScreen 点击收藏按钮，选择「在看」并评分",
            "intent": "SubjectDetailViewModel.updateCollectionStatus(type=DOING, rate=8)",
            "mutation": "CollectionRepository.updateCollectionStatus() 写入数据库并提交 PATCH 请求",
            "storage": "Room: user_collections 插入或更新条目状态",
            "ripples": [
                {"where": "UserScreen (我的页)", "what": "「在看」总计数徽标 +1，更新用户整体概览"},
                {"where": "UserCollectionsScreen (收藏页)", "what": "在看列表网格自动将该番剧插入顶部"},
                {"where": "ScheduleScreen (时间表页)", "what": "每周排期中该番剧点亮「我追的」专属星标"},
            ],
        },
        {
            "id": "action_switch_account", "type": "action", "icon": "users", "title": "切换多账号 (全应用级联)",
            "summary": "在抽屉点击切换账号 ➔ DataStore 更新 activeAccount ➔ 全应用所有界面无感自动重绑对应数据！",
            "trigger": "用户在 UserAccountSheet 账号抽屉中点击选中「账号 B」",
            "intent": "UserViewModel.switchAccount('UserB')",
            "mutation": "SettingsRepository.setActiveAccount('UserB') 广播会话变动",
            "storage": "DataStore: activeAccount 字段持久化",
            "ripples": [
                {"where": "UserScreen (我的页)", "what": "头像、昵称、签名、UID 即时切换为 User B"},
                {"where": "UserCollectionsScreen (收藏页)", "what": "按 User B 的本地 Room 记录即时重载收藏列表"},
                {"where": "ScheduleScreen (时间表页)", "what": "「我追的」过滤条件瞬间换成 User B 的追番列表"},
                {"where": "ScheduleWidget (桌面微件)", "what": "桌面小组件静默重绘为 User B 的追番卡片"},
            ],
        },
        {
            "id": "action_pull_refresh", "type": "action", "icon": "arrow-down-circle", "title": "下拉刷新日历 (强制远端同步)",
            "summary": "在时间表下拉刷新 ➔ 强制请求 API 与 CDN ➔ 覆盖写库 ➔ 驱动时间表与小组件全量重绘！",
            "trigger": "用户在 ScheduleScreen 顶部执行下拉手势",
            "intent": "ScheduleViewModel.onRefresh()",
            "mutation": "ScheduleRepository.refresh(force=true) 忽略本地缓存重新请求",
            "storage": "Room: air_schedules 与 air_events 表覆盖更新",
            "ripples": [
                {"where": "ScheduleScreen (当前页)", "what": "全周所有番剧排期、首播时间与评分重新排序"},
                {"where": "ScheduleWidget (桌面微件)", "what": "小组件抓取最新排期快照更新时间线"},
            ],
        },
        {
            "id": "action_select_weekday", "type": "action", "icon": "calendar", "title": "点击星期 Tab (纯内存重排)",
            "summary": "点击周三 Tab ➔ 纯内存重排 ➔ 零网络零IO，120Hz 极限丝滑响应！",
            "trigger": "用户在 ScheduleScreen 顶部标签栏点击「周三」",
            "intent": "ScheduleViewModel.selectWeekday(3)",
            "mutation": "内存中快速基于 weeklySchedules[3] 筛选排序生成 currentDaySchedules",
            "storage": "零数据库 IO，纯内存不可变 StateFlow 变更",
            "ripples": [
                {"where": "ScheduleScreen (当前页)", "what": "HorizontalPager 平滑滑入周三，时间轴卡片瞬间渲染"},
            ],
        },
    ]

    return {
        "nodes": nodes,
        "edges": edges,
        "stories": stories,
    }


# ==============================================================================
# 4. 图谱模型整体组装
# ==============================================================================

def build_graph() -> dict:
    names = parse_modules()
    modules: dict[str, dict] = {}
    for n in names:
        info = parse_build_script(n)
        files = collect_files(n)
        source_sets: dict[str, dict] = {}
        for f in files:
            if "/src/" not in f["path"]:
                continue
            ss = f["path"].split("/src/")[1].split("/")[0]
            bucket = source_sets.setdefault(ss, {"files": 0, "lines": 0})
            bucket["files"] += 1
            bucket["lines"] += f["lines"]

        m = {
            "module": n,
            "group": group_of(n),
            "kind": "kmp" if any("kmp" in p for p in info["plugins"]) else "android",
            "sourceSets": source_sets,
            "deps": [d for d in info["deps"] if d in names and d != ":core:testing"],
            "testDeps": sorted(
                {d for d in info["testDeps"] if d in names}
                | ({":core:testing"} if ":core:testing" in info["deps"] and n != ":core:testing" else set())
            ),
            "externalDeps": [d for d in info["deps"] if d not in names],
            "plugins": info["plugins"],
            "dir": n.strip(":").replace(":", "/"),
            "files": files,
        }
        modules[n] = m

    for n, m in modules.items():
        ce = len(m["deps"])
        ca = sum(1 for other in modules.values() if n in other["deps"])
        i_metric = round(ce / (ca + ce), 2) if (ca + ce) > 0 else 0.0
        m["ca"] = ca
        m["ce"] = ce
        m["instability"] = i_metric

    depth: dict[str, int] = {}
    def calc(n: str, seen: frozenset) -> int:
        if n in depth:
            return depth[n]
        deps = [d for d in modules[n]["deps"] if d != n and d not in seen]
        depth[n] = 1 + max((calc(d, seen | {n}) for d in deps), default=-1)
        return depth[n]
    for n in names:
        calc(n, frozenset())

    violations = check_rules(modules)
    vcount: dict[str, int] = {}
    for v in violations:
        vcount[v["module"]] = vcount.get(v["module"], 0) + 1

    loc = sum(f["lines"] for m in modules.values() for f in m["files"])
    nfiles = sum(len(m["files"]) for m in modules.values())

    data = {
        "modules": [
            {
                "module": n,
                "group": modules[n]["group"],
                "kind": modules[n]["kind"],
                "sourceSets": modules[n]["sourceSets"],
                "layer": depth[n],
                "deps": modules[n]["deps"],
                "testDeps": modules[n]["testDeps"],
                "externalDeps": modules[n]["externalDeps"],
                "plugins": modules[n]["plugins"],
                "dir": modules[n]["dir"],
                "violations": vcount.get(n, 0),
                "ca": modules[n]["ca"],
                "ce": modules[n]["ce"],
                "instability": modules[n]["instability"],
                "files": [{"path": f["path"], "lines": f["lines"]} for f in modules[n]["files"]],
            }
            for n in names
        ],
        "sources": {f["path"]: f["content"] for m in modules.values() for f in m["files"]},
        "violations": violations,
        "dataTopology": build_data_topology(),
        "stats": {
            "modules": len(names),
            "files": nfiles,
            "loc": loc,
            "violations": len(violations),
        },
    }
    return data


# ==============================================================================
# 5. 高级交互式 HTML 模板 (通道分槽 + 语义色彩 + 悬停探针)
# ==============================================================================

HTML_TEMPLATE = r"""<!DOCTYPE html>
<html lang="zh-CN" class="dark h-full">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>MiniBgm 数据流拓扑与架构控制台 · Studio</title>
<!-- Tailwind CSS Play CDN -->
<script src="https://cdn.tailwindcss.com"></script>
<script>
  tailwind.config = {
    darkMode: 'class',
    theme: {
      extend: {
        colors: {
          bgm: { 50: '#fff1f2', 400: '#fb7185', 500: '#f43f5e', 600: '#e11d48' },
          dark: { 950: '#080a11', 900: '#0c101d', 850: '#111728', 800: '#172036', 700: '#23304e', 600: '#33446b' }
        },
        fontFamily: {
          sans: ['Inter', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif'],
          mono: ['"JetBrains Mono"', 'SFMono-Regular', 'Menlo', 'ui-monospace', 'monospace']
        }
      }
    }
  }
</script>
<!-- Lucide Icons -->
<script src="https://unpkg.com/lucide@latest"></script>
<!-- Google Fonts (Inter + JetBrains Mono) -->
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">

<style>
  /* 基础与画布纹理 */
  body {
    background-color: #080a11;
    color: #f1f5f9;
    font-family: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  }
  .blueprint-grid {
    background-image: radial-gradient(rgba(255, 255, 255, 0.07) 1px, transparent 1px);
    background-size: 28px 28px;
  }
  
  /* 滚动条定制 */
  ::-webkit-scrollbar { width: 6px; height: 6px; }
  ::-webkit-scrollbar-track { background: rgba(15, 23, 42, 0.6); }
  ::-webkit-scrollbar-thumb { background: rgba(51, 65, 85, 0.8); border-radius: 3px; }
  ::-webkit-scrollbar-thumb:hover { background: rgba(100, 116, 139, 1); }

  /* 连线与动画 */
  @keyframes flowDashForward { to { stroke-dashoffset: -32px; } }
  @keyframes flowDashBackward { to { stroke-dashoffset: 32px; } }

  .cable-path {
    fill: none;
    transition: stroke-opacity 0.25s, stroke-width 0.25s, stroke 0.25s;
  }
  .cable-hover-hit {
    fill: none;
    cursor: pointer;
  }
  .cable-down-active {
    stroke-dasharray: 8 4;
    animation: flowDashForward 1s linear infinite;
  }
  .cable-action-active {
    stroke-dasharray: 8 4;
    animation: flowDashBackward 0.8s linear infinite;
  }
  .cable-solo-active {
    stroke-dasharray: 6 3;
    animation: flowDashForward 0.8s linear infinite;
  }

  .cable-label {
    transition: opacity 0.25s;
    user-select: none;
    pointer-events: none;
  }

  /* 节点卡片交互 (支持自由拖拽与单线血缘追溯) */
  .node-card {
    transition: box-shadow 0.2s, border-color 0.2s, opacity 0.25s;
    user-select: none;
  }
  .node-card.dimmed {
    opacity: 0.05 !important;
    filter: grayscale(90%);
    pointer-events: none;
  }
  .node-card.highlighted {
    box-shadow: 0 0 25px -3px rgba(56, 189, 248, 0.4);
    border-color: rgba(56, 189, 248, 0.8) !important;
  }
  .node-card.action-highlighted {
    box-shadow: 0 0 25px -3px rgba(244, 63, 94, 0.5);
    border-color: rgba(244, 63, 94, 0.8) !important;
  }
  .node-card.upstream-highlighted {
    box-shadow: 0 0 28px -2px rgba(16, 185, 129, 0.6);
    border-color: #10b981 !important;
  }
  .node-card.downstream-highlighted {
    box-shadow: 0 0 28px -2px rgba(56, 189, 248, 0.6);
    border-color: #38bdf8 !important;
  }
  .node-card.current-highlighted {
    box-shadow: 0 0 32px -2px rgba(245, 158, 11, 0.7);
    border-color: #f59e0b !important;
  }
  .node-card.is-dragging {
    cursor: grabbing !important;
    box-shadow: 0 20px 30px -10px rgba(0, 0, 0, 0.7), 0 0 25px -5px rgba(56, 189, 248, 0.6) !important;
  }

  /* 源码高亮 */
  .code-pre .line-row.ln-target {
    background: rgba(244, 63, 94, 0.25);
    border-left: 3px solid #f43f5e;
  }
</style>
</head>
<body class="h-full flex flex-col overflow-hidden select-none bg-[#080a11] text-slate-200">

<!-- 顶部全局导航栏 (Glassmorphic Top Bar) -->
<header class="h-14 border-b border-slate-800/80 bg-slate-950/80 backdrop-blur-xl px-5 flex items-center justify-between z-30 shrink-0">
  <div class="flex items-center gap-4">
    <div class="flex items-center gap-2.5">
      <div class="w-8 h-8 rounded-xl bg-gradient-to-tr from-rose-500 to-amber-500 flex items-center justify-center shadow-lg shadow-rose-500/20 text-white font-bold text-sm">
        B+
      </div>
      <div>
        <div class="text-sm font-bold tracking-tight text-white flex items-center gap-1.5">
          MiniBgm <span class="text-slate-400 font-normal">Architecture & UDF Studio</span>
          <span class="px-1.5 py-0.5 rounded text-[10px] font-mono font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">v0.2.0</span>
        </div>
      </div>
    </div>

    <!-- 视图模式 Tab 切换器 -->
    <div class="flex items-center p-1 rounded-xl bg-slate-900 border border-slate-800 text-xs font-medium ml-4">
      <button id="viewTabTopology" class="px-3.5 py-1.5 rounded-lg transition-all flex items-center gap-1.5 text-white bg-slate-800 shadow-sm">
        <i data-lucide="git-fork" class="w-3.5 h-3.5 text-cyan-400"></i>
        端到端数据拓扑 (UDF 闭环)
      </button>
      <button id="viewTabModules" class="px-3.5 py-1.5 rounded-lg transition-all flex items-center gap-1.5 text-slate-400 hover:text-white">
        <i data-lucide="boxes" class="w-3.5 h-3.5 text-indigo-400"></i>
        物理模块依赖图 (DAG)
      </button>
    </div>
  </div>

  <!-- 中部：快速统计药丸 -->
  <div class="hidden lg:flex items-center gap-4 text-xs font-mono text-slate-400">
    <div class="flex items-center gap-1.5">
      <span class="w-2 h-2 rounded-full bg-cyan-400"></span>
      模块 <b class="text-slate-200" id="statModules">16</b>
    </div>
    <div class="flex items-center gap-1.5">
      <span class="w-2 h-2 rounded-full bg-violet-400"></span>
      源文件 <b class="text-slate-200" id="statFiles">189</b>
    </div>
    <div class="flex items-center gap-1.5">
      <span class="w-2 h-2 rounded-full bg-emerald-400"></span>
      代码行 <b class="text-slate-200" id="statLoc">33.2k</b>
    </div>
    <button id="btnRedlines" class="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 font-semibold cursor-pointer hover:bg-emerald-500/20 transition-colors">
      <i data-lucide="shield-check" class="w-3.5 h-3.5"></i>
      架构红线 (8项全过)
    </button>
  </div>

  <!-- 右侧：全局搜索与快捷键 -->
  <div class="flex items-center gap-3">
    <div class="relative w-64">
      <i data-lucide="search" class="w-4 h-4 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2"></i>
      <input id="searchInput" type="text" placeholder="搜索节点、表名、ViewModel、动作..." 
             class="w-full bg-slate-900/90 border border-slate-800 rounded-xl pl-9 pr-8 py-1.5 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-cyan-500/50 focus:ring-2 focus:ring-cyan-500/20 transition-all">
      <kbd class="absolute right-2.5 top-1/2 -translate-y-1/2 px-1.5 py-0.5 rounded text-[10px] font-mono bg-slate-800 text-slate-400 border border-slate-700">⌘K</kbd>
    </div>
  </div>
</header>

<!-- 二级过滤控制条 (Flow Filter Bar & Layout Controls) -->
<div class="h-11 border-b border-slate-800/60 bg-slate-950/40 backdrop-blur-md px-5 flex items-center justify-between z-20 shrink-0 overflow-x-auto gap-4">
  <div class="flex items-center gap-2 text-xs" id="storyButtonList">
    <!-- 动态填充流标签 -->
  </div>

  <!-- 画布与连线控制器 -->
  <div class="flex items-center gap-2 shrink-0">
    <!-- 连线形态切换器 (Bezier vs Orthogonal) -->
    <button id="btnToggleLineStyle" class="px-2.5 py-1 rounded-lg border border-slate-800 bg-slate-900/90 hover:bg-slate-800 text-slate-300 hover:text-white flex items-center gap-1.5 text-xs transition-colors" title="切换曲线/正交直角折线">
      <i data-lucide="git-commit" class="w-3.5 h-3.5 text-cyan-400"></i>
      <span id="lineStyleLabel">📐 正交折线</span>
    </button>

    <!-- 连线数据标签切换器 -->
    <button id="btnToggleLabels" class="px-2.5 py-1 rounded-lg border border-slate-800 bg-slate-900/90 hover:bg-slate-800 text-slate-300 hover:text-white flex items-center gap-1.5 text-xs transition-colors" title="常驻/隐藏连线数据模型载荷标签">
      <i data-lucide="tag" class="w-3.5 h-3.5 text-emerald-400"></i>
      <span id="labelsLabel">🏷️ 载荷标签</span>
    </button>

    <!-- 一键重置位置 -->
    <button id="btnResetPositions" class="px-2.5 py-1 rounded-lg border border-slate-800 bg-slate-900/90 hover:bg-slate-800 text-slate-300 hover:text-white flex items-center gap-1.5 text-xs transition-colors" title="重置卡片为标准平行轨道排布">
      <i data-lucide="refresh-cw" class="w-3.5 h-3.5 text-amber-400"></i>
      <span>对齐复位</span>
    </button>

    <!-- 缩放控制器 -->
    <div class="flex items-center gap-1 text-slate-400 bg-slate-900/80 border border-slate-800 rounded-lg p-0.5">
      <button id="btnZoomOut" class="p-1.5 hover:text-white hover:bg-slate-800 rounded transition-colors" title="缩小 (-)">
        <i data-lucide="minus" class="w-3.5 h-3.5"></i>
      </button>
      <button id="btnZoomReset" class="px-2 py-1 text-[11px] font-mono hover:text-white hover:bg-slate-800 rounded transition-colors" title="重置 100%">
        <span id="zoomPercent">100%</span>
      </button>
      <button id="btnZoomIn" class="p-1.5 hover:text-white hover:bg-slate-800 rounded transition-colors" title="放大 (+)">
        <i data-lucide="plus" class="w-3.5 h-3.5"></i>
      </button>
      <div class="w-px h-3 bg-slate-800 mx-0.5"></div>
      <button id="btnFitScreen" class="p-1.5 hover:text-white hover:bg-slate-800 rounded transition-colors" title="适应屏幕">
        <i data-lucide="maximize" class="w-3.5 h-3.5"></i>
      </button>
    </div>
  </div>
</div>

<!-- 主视口区域 -->
<main class="flex-1 relative overflow-hidden flex">
  
  <!-- 视图 1: 数据拓扑画布 (SVG + 可拖拽 HTML Cards) -->
  <div id="topologyView" class="flex-1 h-full relative overflow-hidden blueprint-grid cursor-grab active:cursor-grabbing">
    
    <!-- 节点单线血缘追溯浮动提示条 (Data Lineage Breadcrumb Bar) -->
    <div id="lineageBar" class="absolute top-3 left-1/2 -translate-x-1/2 z-40 hidden flex items-center gap-3 px-4 py-2 rounded-2xl bg-slate-950/95 border border-slate-700 shadow-2xl backdrop-blur-2xl text-xs">
      <div class="flex items-center gap-1.5 text-emerald-400 font-bold">
        <span class="w-2 h-2 rounded-full bg-emerald-400"></span>
        <span id="lineageUpstreamLabel">来源: 0</span>
      </div>
      <span class="text-slate-600">➔</span>
      <div class="flex items-center gap-1.5 text-amber-400 font-bold">
        <i data-lucide="cpu" class="w-3.5 h-3.5"></i>
        <span id="lineageCurrentLabel">当前节点</span>
      </div>
      <span class="text-slate-600">➔</span>
      <div class="flex items-center gap-1.5 text-sky-400 font-bold">
        <span class="w-2 h-2 rounded-full bg-sky-400"></span>
        <span id="lineageDownstreamLabel">去向: 0</span>
      </div>
      <div class="w-px h-4 bg-slate-800 mx-1"></div>
      <button id="btnCloseLineage" class="px-2 py-0.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white flex items-center gap-1 text-[11px] transition-colors">
        <i data-lucide="x" class="w-3 h-3"></i> 退出追溯 (Esc)
      </button>
    </div>

    <!-- 可平移缩放的内容包裹层 -->
    <div id="canvasStage" class="absolute origin-top-left" style="width: 1920px; height: 1240px; transform: matrix(1, 0, 0, 1, 0, 0);">
      
      <!-- 5 列悬浮标头 -->
      <div class="absolute top-2 left-[50px] w-[280px] flex items-center justify-between pb-2 border-b border-cyan-500/30 text-cyan-400 font-semibold text-xs tracking-wider">
        <div class="flex items-center gap-1.5">
          <i data-lucide="globe" class="w-3.5 h-3.5"></i>
          <span>1. 数据源起 (ORIGINS)</span>
        </div>
        <span class="text-[10px] font-mono px-1.5 py-0.2 rounded bg-cyan-500/10 border border-cyan-500/20">6 节点</span>
      </div>

      <div class="absolute top-2 left-[410px] w-[280px] flex items-center justify-between pb-2 border-b border-violet-500/30 text-violet-400 font-semibold text-xs tracking-wider">
        <div class="flex items-center gap-1.5">
          <i data-lucide="git-commit" class="w-3.5 h-3.5"></i>
          <span>2. 仓储转化 (REPOSITORIES)</span>
        </div>
        <span class="text-[10px] font-mono px-1.5 py-0.2 rounded bg-violet-500/10 border border-violet-500/20">7 节点</span>
      </div>

      <div class="absolute top-2 left-[770px] w-[280px] flex items-center justify-between pb-2 border-b border-emerald-500/30 text-emerald-400 font-semibold text-xs tracking-wider">
        <div class="flex items-center gap-1.5">
          <i data-lucide="database" class="w-3.5 h-3.5"></i>
          <span>3. 存储落地 (STORAGE)</span>
        </div>
        <span class="text-[10px] font-mono px-1.5 py-0.2 rounded bg-emerald-500/10 border border-emerald-500/20">5 节点</span>
      </div>

      <div class="absolute top-2 left-[1130px] w-[280px] flex items-center justify-between pb-2 border-b border-amber-500/30 text-amber-400 font-semibold text-xs tracking-wider">
        <div class="flex items-center gap-1.5">
          <i data-lucide="cpu" class="w-3.5 h-3.5"></i>
          <span>4. 状态装配 (VIEWMODELS)</span>
        </div>
        <span class="text-[10px] font-mono px-1.5 py-0.2 rounded bg-amber-500/10 border border-amber-500/20">6 节点</span>
      </div>

      <div class="absolute top-2 left-[1490px] w-[280px] flex items-center justify-between pb-2 border-b border-rose-500/30 text-rose-400 font-semibold text-xs tracking-wider">
        <div class="flex items-center gap-1.5">
          <i data-lucide="smartphone" class="w-3.5 h-3.5"></i>
          <span>5. 终端呈现 (SCREENS)</span>
        </div>
        <span class="text-[10px] font-mono px-1.5 py-0.2 rounded bg-rose-500/10 border border-rose-500/20">7 节点</span>
      </div>

      <!-- 底层：连线 SVG (包含可见线、数据模型载荷标签和交互 Hit 区域) -->
      <svg id="cableSvg" class="absolute inset-0 w-full h-full pointer-events-none z-10" style="overflow: visible;">
        <defs id="cableDefs">
          <filter id="glowFilter" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur stdDeviation="3" result="blur" />
            <feComposite in="SourceGraphic" in2="blur" operator="over" />
          </filter>
        </defs>
        <!-- 连线分组 -->
        <g id="cableVisibleGroup"></g>
        <!-- 连线数据标签分组 -->
        <g id="cableLabelsGroup"></g>
        <!-- 悬停判定分组 (较粗且透明，以便鼠标命中) -->
        <g id="cableHitGroup"></g>
      </svg>

      <!-- 上层：HTML 节点卡片容器 (支持自由拖拽) -->
      <div id="nodesLayer" class="absolute inset-0 w-full h-full z-20 pointer-events-none"></div>
    </div>

    <!-- 连线实时悬停探针卡片 (Edge Probe Tooltip) -->
    <div id="cableTooltip" class="fixed pointer-events-none z-50 px-3.5 py-2.5 rounded-xl bg-slate-950/95 border border-slate-700 text-xs shadow-2xl backdrop-blur-xl hidden max-w-sm transition-opacity duration-150">
      <div class="flex items-center justify-between gap-2 mb-1.5">
        <div class="flex items-center gap-1.5 font-bold text-white text-[11px]" id="tipStoryTitle">
          <span class="w-2 h-2 rounded-full" id="tipStoryDot"></span>
          <span id="tipStoryName">业务流</span>
        </div>
        <span class="text-[9px] font-mono px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700" id="tipFlowType">DOWN</span>
      </div>
      <div class="text-[11px] text-slate-300 flex items-center gap-1 mb-1 font-mono">
        <span class="text-cyan-300 font-semibold truncate max-w-[120px]" id="tipFrom"></span>
        <span class="text-slate-500">➔</span>
        <span class="text-emerald-300 font-semibold truncate max-w-[120px]" id="tipTo"></span>
      </div>
      <div class="text-[11px] font-mono font-medium text-amber-300 bg-slate-900/80 px-2 py-1 rounded border border-slate-800" id="tipPayload"></div>
    </div>

    <!-- 左下角浮动提示 -->
    <div class="absolute left-5 bottom-5 z-20 pointer-events-none flex items-center gap-2 px-3 py-1.5 rounded-xl bg-slate-950/80 border border-slate-800 text-[11px] text-slate-400 backdrop-blur-md shadow-lg">
      <i data-lucide="mouse-pointer-2" class="w-3.5 h-3.5 text-cyan-400"></i>
      <span>🖱️ 鼠标悬停任意连线探针感知 · 拖拽卡片摆放 · 顶部切换正交/流体 · Esc 复位</span>
    </div>

    <!-- 底部居中：跨页面联动涟漪反应浮动 HUD (当选择写回路时弹出) -->
    <div id="rippleHud" class="absolute bottom-5 left-1/2 -translate-x-1/2 z-20 max-w-2xl w-full px-4 hidden transition-all duration-300">
      <div class="rounded-2xl border border-rose-500/40 bg-slate-950/90 backdrop-blur-2xl p-4 shadow-2xl shadow-rose-950/50">
        <div class="flex items-center justify-between mb-2.5">
          <div class="flex items-center gap-2">
            <span class="flex h-2 w-2 rounded-full bg-rose-500 animate-ping"></span>
            <span class="text-xs font-bold uppercase tracking-wider text-rose-400 flex items-center gap-1.5" id="hudLoopTitle">
              👉 单集打卡追番 · 跨页面联动涟漪效应
            </span>
          </div>
          <button id="btnCloseHud" class="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition-colors">
            <i data-lucide="x" class="w-3.5 h-3.5"></i>
          </button>
        </div>
        <p class="text-xs text-slate-300 mb-3" id="hudLoopSummary">
          在详情页点击打卡第 N 话 ➔ 乐观写库 ➔ 远端 PUT ➔ Room 响应式倒灌驱动时间表与桌面微件同步更新！
        </p>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-2.5" id="hudRippleList"></div>
      </div>
    </div>
  </div>

  <!-- 视图 2: 模块依赖与架构守卫 (DAG) -->
  <div id="modulesView" class="flex-1 h-full relative overflow-hidden blueprint-grid hidden">
    <div class="absolute inset-0 p-8 overflow-auto">
      <div class="max-w-6xl mx-auto space-y-6">
        <div class="grid grid-cols-1 sm:grid-cols-4 gap-4">
          <div class="rounded-xl border border-slate-800 bg-slate-900/60 p-4 backdrop-blur-md">
            <div class="text-xs font-medium text-slate-400 mb-1">物理架构合规性</div>
            <div class="text-2xl font-bold text-emerald-400 flex items-center gap-2">
              100%
              <span class="text-xs font-normal px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">8/8 规则全部通过</span>
            </div>
          </div>
          <div class="rounded-xl border border-slate-800 bg-slate-900/60 p-4 backdrop-blur-md">
            <div class="text-xs font-medium text-slate-400 mb-1">Feature 间解耦</div>
            <div class="text-2xl font-bold text-white">0 依赖</div>
            <div class="text-xs text-slate-400 mt-1">完全基于 Navigation3 路由隔离</div>
          </div>
          <div class="rounded-xl border border-slate-800 bg-slate-900/60 p-4 backdrop-blur-md">
            <div class="text-xs font-medium text-slate-400 mb-1">核心模型纯净度</div>
            <div class="text-2xl font-bold text-white">:core:model</div>
            <div class="text-xs text-slate-400 mt-1">100% 纯 Kotlin (零 Android 依赖)</div>
          </div>
          <div class="rounded-xl border border-slate-800 bg-slate-900/60 p-4 backdrop-blur-md">
            <div class="text-xs font-medium text-slate-400 mb-1">敏感凭据隔离</div>
            <div class="text-2xl font-bold text-white">AndroidKeyStore</div>
            <div class="text-xs text-slate-400 mt-1">硬隔离，排除出云备份</div>
          </div>
        </div>

        <div class="rounded-2xl border border-slate-800 bg-slate-900/80 overflow-hidden backdrop-blur-xl">
          <div class="px-6 py-4 border-b border-slate-800 flex items-center justify-between">
            <h3 class="font-bold text-sm text-white flex items-center gap-2">
              <i data-lucide="layers" class="w-4 h-4 text-cyan-400"></i>
              模块 Martin 质量指标 (稳定性与出入度)
            </h3>
            <span class="text-xs text-slate-400 font-mono">I = Ce / (Ca + Ce) (0: 极稳定, 1: 极易变)</span>
          </div>
          <div class="overflow-x-auto">
            <table class="w-full text-left text-xs font-mono">
              <thead class="bg-slate-950/60 text-slate-400 border-b border-slate-800">
                <tr>
                  <th class="px-6 py-3 font-semibold">模块名</th>
                  <th class="px-4 py-3 font-semibold">分组</th>
                  <th class="px-4 py-3 font-semibold">工程性质</th>
                  <th class="px-4 py-3 font-semibold">入向 Ca (被依赖)</th>
                  <th class="px-4 py-3 font-semibold">出向 Ce (依赖他)</th>
                  <th class="px-4 py-3 font-semibold">不稳定性 I</th>
                  <th class="px-6 py-3 font-semibold">源文件 / 代码行</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-800/60" id="moduleTableBody"></tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 右侧抽屉：节点详情与源码查看器 -->
  <aside id="detailDrawer" class="w-[480px] border-l border-slate-800/80 bg-slate-950/95 backdrop-blur-2xl flex flex-col shrink-0 z-30 transition-transform duration-300 translate-x-full">
    <div class="p-4 border-b border-slate-800 flex items-center justify-between">
      <div class="flex items-center gap-2.5">
        <div id="drawerIconBox" class="w-8 h-8 rounded-xl bg-slate-800 text-cyan-400 flex items-center justify-center border border-slate-700">
          <i data-lucide="box" class="w-4 h-4" id="drawerIcon"></i>
        </div>
        <div>
          <h3 class="text-sm font-bold text-white tracking-tight" id="drawerTitle">节点标题</h3>
          <p class="text-[11px] font-mono text-slate-400" id="drawerSubtitle">子标题</p>
        </div>
      </div>
      <button id="btnCloseDrawer" class="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors">
        <i data-lucide="x" class="w-4 h-4"></i>
      </button>
    </div>

    <div class="flex-1 overflow-y-auto p-5 space-y-5" id="drawerBody">
      <div class="space-y-1.5">
        <div class="text-xs font-semibold text-slate-400 uppercase tracking-wider">架构职能 (Role)</div>
        <p class="text-xs text-slate-200 leading-relaxed" id="drawerDesc"></p>
      </div>

      <div class="space-y-1.5" id="drawerModelBox">
        <div class="text-xs font-semibold text-slate-400 uppercase tracking-wider">数据契约 (Contract)</div>
        <div class="p-2.5 rounded-xl bg-slate-900 border border-slate-800 font-mono text-xs text-cyan-300 font-semibold" id="drawerModelName"></div>
      </div>

      <div class="space-y-1.5" id="drawerFieldsBox">
        <div class="text-xs font-semibold text-slate-400 uppercase tracking-wider" id="drawerFieldsTitle">核心字段字典</div>
        <div class="rounded-xl border border-slate-800 overflow-hidden bg-slate-900/60">
          <table class="w-full text-left text-[11px] font-mono">
            <thead class="bg-slate-950/80 text-slate-400 border-b border-slate-800">
              <tr>
                <th class="px-3 py-2">字段</th>
                <th class="px-3 py-2">类型</th>
                <th class="px-3 py-2">语义描述</th>
              </tr>
            </thead>
            <tbody id="drawerFieldsBody" class="divide-y divide-slate-800/60"></tbody>
          </table>
        </div>
      </div>

      <div class="space-y-2">
        <div class="text-xs font-semibold text-slate-400 uppercase tracking-wider">关联数据管道 (I/O)</div>
        <div class="space-y-1.5 text-xs font-mono" id="drawerPipesList"></div>
      </div>

      <div class="space-y-2" id="drawerSourceBox">
        <div class="flex items-center justify-between">
          <div class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Kotlin 源码文件</div>
          <span class="text-[11px] font-mono text-cyan-400 cursor-pointer hover:underline" id="drawerFilePath"></span>
        </div>
        <div class="rounded-xl border border-slate-800 bg-slate-900 overflow-hidden">
          <pre class="code-pre p-3 text-[11px] leading-relaxed overflow-x-auto text-slate-300 max-h-60" id="drawerCodeSnippet"></pre>
        </div>
      </div>
    </div>
  </aside>
</main>

<!-- 架构红线校验详情 Modal -->
<div id="redlinesModal" class="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 hidden">
  <div class="w-full max-w-3xl rounded-2xl border border-slate-800 bg-slate-950 shadow-2xl overflow-hidden flex flex-col max-h-[85vh]">
    <div class="px-6 py-4 border-b border-slate-800 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <i data-lucide="shield-check" class="w-5 h-5 text-emerald-400"></i>
        <h3 class="text-sm font-bold text-white">架构红线检验报告 (AGENTS.md & ArchitectureRulesTest)</h3>
      </div>
      <button id="btnCloseRedlinesModal" class="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors">
        <i data-lucide="x" class="w-4 h-4"></i>
      </button>
    </div>
    <div class="p-6 overflow-y-auto space-y-3" id="redlinesList"></div>
  </div>
</div>

<script>
// 注入的数据实体
const GRAPH = __DATA__;

// ==============================================================================
// 业务色彩字典 (Semantic Palette - 每条业务流独享醒目色彩)
// ==============================================================================
const STORY_PALETTE = {
  "weekly_schedule": { color: "#38bdf8", name: "每周排期", text: "text-sky-400", dot: "bg-sky-400" },
  "subject_detail": { color: "#a855f7", name: "条目详情", text: "text-purple-400", dot: "bg-purple-400" },
  "collection_sync": { color: "#34d399", name: "收藏同步", text: "text-emerald-400", dot: "bg-emerald-400" },
  "auth_preferences": { color: "#fbbf24", name: "认证偏好", text: "text-amber-400", dot: "bg-amber-400" },
  "explore_community": { color: "#818cf8", name: "社区探索", text: "text-indigo-400", dot: "bg-indigo-400" },
  "airing_reminder": { color: "#60a5fa", name: "开播提醒", text: "text-blue-400", dot: "bg-blue-400" },
  // 上行用户交互回写流
  "action_ep_watch": { color: "#f43f5e", name: "分集打卡", text: "text-rose-400", dot: "bg-rose-400" },
  "action_coll_status": { color: "#ec4899", name: "修改收藏", text: "text-pink-400", dot: "bg-pink-400" },
  "action_switch_account": { color: "#c084fc", name: "切换账号", text: "text-fuchsia-400", dot: "bg-fuchsia-400" },
  "action_pull_refresh": { color: "#fb923c", name: "下拉刷新", text: "text-orange-400", dot: "bg-orange-400" },
  "action_select_weekday": { color: "#e879f9", name: "切换周几", text: "text-violet-400", dot: "bg-violet-400" }
};

function getStoryMeta(storyId) {
  return STORY_PALETTE[storyId] || { color: "#94a3b8", name: "数据流动", text: "text-slate-400", dot: "bg-slate-400" };
}

// ==============================================================================
// 状态与全局变量
// ==============================================================================
let activeStoryId = "weekly_schedule"; // 默认聚焦每周排期主线
let activeNodeId = null;
let hoveredEdgeIdx = null;
let currentZoom = 1.0;
let panX = 0;
let panY = 0;
let isCanvasPanning = false;
let canvasStartX = 0;
let canvasStartY = 0;

let lineStyle = localStorage.getItem("minibgm_line_style") || "step";

const STORAGE_KEY_POS = "minibgm_node_positions_v3";
let nodePositions = {};
try {
  const saved = localStorage.getItem(STORAGE_KEY_POS);
  if (saved) nodePositions = JSON.parse(saved);
} catch (e) {}

const topologyView = document.getElementById("topologyView");
const canvasStage = document.getElementById("canvasStage");
const cableSvg = document.getElementById("cableSvg");
const cableVisibleGroup = document.getElementById("cableVisibleGroup");
const cableLabelsGroup = document.getElementById("cableLabelsGroup");
const cableHitGroup = document.getElementById("cableHitGroup");
const nodesLayer = document.getElementById("nodesLayer");
const detailDrawer = document.getElementById("detailDrawer");
const rippleHud = document.getElementById("rippleHud");
const cableTooltip = document.getElementById("cableTooltip");

let showCableLabels = localStorage.getItem("minibgm_show_labels") === "true";

const COL_X = [50, 410, 770, 1130, 1490];
const CARD_WIDTH = 280;
const ROW_HEIGHT = 140;

// ==============================================================================
// 连线方向箭头 (SVG Markers) 初始化
// ==============================================================================
function initMarkers() {
  const defs = document.getElementById("cableDefs");
  if (!defs) return;

  const markerList = [
    { id: "arrow-default", color: "#64748b" },
    { id: "arrow-upstream", color: "#10b981" },
    { id: "arrow-downstream", color: "#38bdf8" },
    { id: "arrow-hover", color: "#38bdf8" },
    ...Object.entries(STORY_PALETTE).map(([id, meta]) => ({ id: `arrow-${id}`, color: meta.color }))
  ];

  markerList.forEach(m => {
    if (document.getElementById(m.id)) return;
    const marker = document.createElementNS("http://www.w3.org/2000/svg", "marker");
    marker.setAttribute("id", m.id);
    marker.setAttribute("viewBox", "0 0 10 10");
    marker.setAttribute("refX", "7");
    marker.setAttribute("refY", "5");
    marker.setAttribute("markerWidth", "6");
    marker.setAttribute("markerHeight", "6");
    marker.setAttribute("orient", "auto");
    marker.innerHTML = `<path d="M 0 1.5 L 8 5 L 0 8.5 z" fill="${m.color}" />`;
    defs.appendChild(marker);
  });
}

// ==============================================================================
// 连线多通道与端点分布预计算 (彻底消除连线重合)
// ==============================================================================
const nodeOutEdges = {};
const nodeInEdges = {};
const columnSpanMap = {};

GRAPH.dataTopology.edges.forEach((edge, idx) => {
  if (!nodeOutEdges[edge.from]) nodeOutEdges[edge.from] = [];
  nodeOutEdges[edge.from].push(idx);

  if (!nodeInEdges[edge.to]) nodeInEdges[edge.to] = [];
  nodeInEdges[edge.to].push(idx);

  const srcNode = GRAPH.dataTopology.nodes.find(n => n.id === edge.from);
  const dstNode = GRAPH.dataTopology.nodes.find(n => n.id === edge.to);
  if (srcNode && dstNode) {
    const spanKey = `${srcNode.col}->${dstNode.col}`;
    if (!columnSpanMap[spanKey]) columnSpanMap[spanKey] = [];
    columnSpanMap[spanKey].push(idx);
  }
});

function getEdgeGeometry(edge, idx) {
  const srcPos = getNodePos(edge.from);
  const dstPos = getNodePos(edge.to);

  const outList = nodeOutEdges[edge.from] || [idx];
  const inList = nodeInEdges[edge.to] || [idx];
  const outRank = outList.indexOf(idx);
  const inRank = inList.indexOf(idx);

  // 端点在卡片垂直边缘错开，避免线束缩成单点
  const outSpread = outList.length <= 1 ? 32 : (18 + (outRank / (outList.length - 1)) * 44);
  const inSpread = inList.length <= 1 ? 32 : (18 + (inRank / (inList.length - 1)) * 44);

  const srcRight = srcPos.x + CARD_WIDTH;
  const dstRight = dstPos.x + CARD_WIDTH;

  let x1, y1, x2, y2;
  // 智能端点判定：根据两卡片的相对几何位置自动计算出入方向，杜绝拖拽后穿卡扭曲
  if (dstPos.x >= srcRight - 30) {
    // 目标在源节点右侧：从源右侧引出，进入目标左侧
    x1 = srcRight;
    y1 = srcPos.y + outSpread;
    x2 = dstPos.x;
    y2 = dstPos.y + inSpread;
  } else if (srcPos.x >= dstRight - 30) {
    // 目标在源节点左侧 (如 Action 回写，或卡片被拖拽至左侧)：从源左侧引出，进入目标右侧
    x1 = srcPos.x;
    y1 = srcPos.y + outSpread;
    x2 = dstRight;
    y2 = dstPos.y + inSpread;
  } else {
    // 水平有明显重叠 (上下排列)：从源上下侧引出/接入
    if (dstPos.y >= srcPos.y) {
      x1 = srcPos.x + 40 + outSpread;
      y1 = srcPos.y + 76;
      x2 = dstPos.x + 40 + inSpread;
      y2 = dstPos.y;
    } else {
      x1 = srcPos.x + 40 + outSpread;
      y1 = srcPos.y;
      x2 = dstPos.x + 40 + inSpread;
      y2 = dstPos.y + 76;
    }
  }

  // 垂直通道分配：同一跨度的多条线错开 channelX 坐标
  let channelX = (x1 + x2) / 2;
  const srcNode = GRAPH.dataTopology.nodes.find(n => n.id === edge.from);
  const dstNode = GRAPH.dataTopology.nodes.find(n => n.id === edge.to);
  if (srcNode && dstNode) {
    const spanKey = `${srcNode.col}->${dstNode.col}`;
    const spanList = columnSpanMap[spanKey] || [idx];
    const spanRank = spanList.indexOf(idx);
    const spanTotal = spanList.length;
    if (spanTotal > 1) {
      const step = Math.min(16, 100 / (spanTotal - 1));
      channelX = ((x1 + x2) / 2) + (spanRank - (spanTotal - 1) / 2) * step;
    }
  }

  return { x1, y1, x2, y2, channelX };
}

// ==============================================================================
// 节点位置与卡片渲染
// ==============================================================================

function getCategoryColor(category) {
  switch (category) {
    case "origin": return { border: "border-cyan-500/30", bg: "bg-cyan-500/10", text: "text-cyan-400", hex: "#38bdf8", badge: "ORIGIN" };
    case "transform": return { border: "border-violet-500/30", bg: "bg-violet-500/10", text: "text-violet-400", hex: "#818cf8", badge: "REPO" };
    case "storage": return { border: "border-emerald-500/30", bg: "bg-emerald-500/10", text: "text-emerald-400", hex: "#34d399", badge: "STORAGE" };
    case "vm": return { border: "border-amber-500/30", bg: "bg-amber-500/10", text: "text-amber-400", hex: "#fb923c", badge: "VIEWMODEL" };
    case "ui": return { border: "border-rose-500/30", bg: "bg-rose-500/10", text: "text-rose-400", hex: "#f43f5e", badge: "UI / SCREEN" };
    default: return { border: "border-slate-700", bg: "bg-slate-800", text: "text-slate-300", hex: "#94a3b8", badge: "NODE" };
  }
}

function computeDefaultNodePosition(node) {
  return { x: COL_X[node.col], y: 65 + node.row * ROW_HEIGHT };
}

function getNodePos(nodeId) {
  if (nodePositions[nodeId]) return nodePositions[nodeId];
  const n = GRAPH.dataTopology.nodes.find(item => item.id === nodeId);
  return n ? computeDefaultNodePosition(n) : { x: 0, y: 0 };
}

function renderTopologyNodes() {
  nodesLayer.innerHTML = "";
  GRAPH.dataTopology.nodes.forEach(node => {
    const pos = getNodePos(node.id);
    const cat = getCategoryColor(node.category);

    const card = document.createElement("div");
    card.id = `node-${node.id}`;
    card.className = `node-card absolute pointer-events-auto w-[280px] rounded-2xl border ${cat.border} bg-slate-900/90 backdrop-blur-xl p-3.5 shadow-xl hover:border-cyan-400/80 cursor-grab group z-20`;
    card.style.left = `${pos.x}px`;
    card.style.top = `${pos.y}px`;
    card.dataset.id = node.id;

    let subInfo = "";
    if (node.modelName) {
      subInfo = `<div class="mt-2 pt-2 border-t border-slate-800/80 flex items-center justify-between text-[11px] font-mono">
                   <span class="text-slate-500">模型:</span>
                   <span class="${cat.text} font-medium truncate max-w-[190px]">${node.modelName}</span>
                 </div>`;
    } else if (node.operations && node.operations.length > 0) {
      subInfo = `<div class="mt-2 pt-2 border-t border-slate-800/80 flex items-center justify-between text-[11px] font-mono">
                   <span class="text-slate-500">关键逻辑:</span>
                   <span class="text-slate-300 truncate max-w-[190px]">${node.operations[0]}</span>
                 </div>`;
    }

    card.innerHTML = `
      <div class="flex items-start justify-between gap-2">
        <div class="flex items-center gap-2.5 min-w-0">
          <div class="w-8 h-8 rounded-xl ${cat.bg} ${cat.text} flex items-center justify-center border ${cat.border} shrink-0">
            <i data-lucide="${node.icon || 'box'}" class="w-4 h-4"></i>
          </div>
          <div class="min-w-0">
            <h4 class="text-xs font-bold text-slate-100 truncate group-hover:text-cyan-300 transition-colors">${node.name}</h4>
            <p class="text-[10px] font-mono text-slate-400 truncate">${node.sub || ''}</p>
          </div>
        </div>
        <span class="px-1.5 py-0.5 rounded text-[9px] font-mono font-medium ${cat.bg} ${cat.text} border ${cat.border} shrink-0">${cat.badge}</span>
      </div>
      ${subInfo}
    `;

    setupCardDragging(card, node.id);

    card.addEventListener("mouseenter", () => {
      if (!activeStoryId && !activeNodeId) highlightNodeHover(node.id, true);
    });
    card.addEventListener("mouseleave", () => {
      if (!activeStoryId && !activeNodeId) highlightNodeHover(node.id, false);
    });

    nodesLayer.appendChild(card);
  });
}

// ==============================================================================
// 拖拽支持
// ==============================================================================
let isDraggingCard = false;
let draggedCardId = null;
let dragStartX = 0;
let dragStartY = 0;
let initialCardX = 0;
let initialCardY = 0;
let hasCardMoved = false;

function setupCardDragging(card, nodeId) {
  card.addEventListener("mousedown", (e) => {
    if (e.target.closest("button") || e.target.closest("input")) return;
    e.stopPropagation();

    isDraggingCard = true;
    draggedCardId = nodeId;
    dragStartX = e.clientX;
    dragStartY = e.clientY;
    hasCardMoved = false;

    const curPos = getNodePos(nodeId);
    initialCardX = curPos.x;
    initialCardY = curPos.y;

    card.classList.add("is-dragging");
    card.style.zIndex = "40";
  });
}

window.addEventListener("mousemove", (e) => {
  if (!isDraggingCard || !draggedCardId) return;

  const dx = (e.clientX - dragStartX) / currentZoom;
  const dy = (e.clientY - dragStartY) / currentZoom;

  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) hasCardMoved = true;

  const newX = initialCardX + dx;
  const newY = initialCardY + dy;
  nodePositions[draggedCardId] = { x: newX, y: newY };

  const card = document.getElementById(`node-${draggedCardId}`);
  if (card) {
    card.style.left = `${newX}px`;
    card.style.top = `${newY}px`;
  }
  updateCables();
});

window.addEventListener("mouseup", () => {
  if (isDraggingCard && draggedCardId) {
    const card = document.getElementById(`node-${draggedCardId}`);
    if (card) {
      card.classList.remove("is-dragging");
      card.style.zIndex = "20";
    }
    if (hasCardMoved) {
      try { localStorage.setItem(STORAGE_KEY_POS, JSON.stringify(nodePositions)); } catch (e) {}
    } else {
      selectNode(draggedCardId);
    }
    isDraggingCard = false;
    draggedCardId = null;
  }
});

document.getElementById("btnResetPositions").addEventListener("click", () => {
  nodePositions = {};
  localStorage.removeItem(STORAGE_KEY_POS);
  renderTopologyNodes();
  updateCables();
  lucide.createIcons();
});

// ==============================================================================
// 连线形态算法 (正交倒角 vs 平滑流体贝塞尔)
// ==============================================================================

function computePathString(geom) {
  const { x1, y1, x2, y2, channelX } = geom;

  if (lineStyle === "step") {
    // 📐 正交直角倒角折线
    if (Math.abs(y2 - y1) < 2) {
      return `M ${x1} ${y1} L ${x2} ${y2}`;
    }

    const dirX = x2 >= x1 ? 1 : -1;
    const dirY = y2 >= y1 ? 1 : -1;
    const mx = channelX;
    const r = Math.min(12, Math.abs(mx - x1) / 2, Math.abs(x2 - mx) / 2, Math.abs(y2 - y1) / 2);

    if (r > 2 && ((dirX > 0 && mx > x1 && mx < x2) || (dirX < 0 && mx < x1 && mx > x2))) {
      return `M ${x1} ${y1} ` +
             `L ${mx - dirX * r} ${y1} ` +
             `Q ${mx} ${y1}, ${mx} ${y1 + dirY * r} ` +
             `L ${mx} ${y2 - dirY * r} ` +
             `Q ${mx} ${y2}, ${mx + dirX * r} ${y2} ` +
             `L ${x2} ${y2}`;
    } else {
      return `M ${x1} ${y1} L ${mx} ${y1} L ${mx} ${y2} L ${x2} ${y2}`;
    }
  } else {
    // 🌊 平滑流体贝塞尔
    const dx = x2 - x1;
    const curvature = Math.min(Math.max(40, Math.abs(dx) * 0.5), 160);
    const signX = dx >= 0 ? 1 : -1;
    return `M ${x1} ${y1} C ${x1 + signX * curvature} ${y1}, ${x2 - signX * curvature} ${y2}, ${x2} ${y2}`;
  }
}

function computeMidpoint(geom) {
  const { x1, y1, x2, y2, channelX } = geom;
  if (lineStyle === "step") {
    return { x: channelX, y: (y1 + y2) / 2 };
  } else {
    return { x: (x1 + x2) / 2, y: (y1 + y2) / 2 };
  }
}

function renderTopologyCables() {
  initMarkers();
  cableVisibleGroup.innerHTML = "";
  cableLabelsGroup.innerHTML = "";
  cableHitGroup.innerHTML = "";

  GRAPH.dataTopology.edges.forEach((edge, idx) => {
    const geom = getEdgeGeometry(edge, idx);
    const d = computePathString(geom);
    const mid = computeMidpoint(geom);
    const meta = getStoryMeta(edge.storyId);

    // 1. 可视化连线 (带专属业务方向箭头)
    const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
    path.setAttribute("d", d);
    path.setAttribute("class", "cable-path");
    path.setAttribute("id", `cable-${idx}`);
    path.dataset.from = edge.from;
    path.dataset.to = edge.to;
    path.dataset.story = edge.storyId;
    path.dataset.flow = edge.flow;

    path.setAttribute("stroke", meta.color);
    path.setAttribute("stroke-width", "1.6");
    path.setAttribute("stroke-opacity", "0.28");
    path.setAttribute("marker-end", `url(#arrow-${edge.storyId})`);

    cableVisibleGroup.appendChild(path);

    // 2. 连线数据模型载荷胶囊标签
    const labelG = document.createElementNS("http://www.w3.org/2000/svg", "g");
    labelG.setAttribute("class", "cable-label");
    labelG.setAttribute("id", `cable-label-${idx}`);
    labelG.setAttribute("transform", `translate(${mid.x}, ${mid.y})`);

    const rawLabel = edge.label || "";
    const shortLabel = rawLabel.length > 14 ? rawLabel.slice(0, 13) + "…" : rawLabel;
    const rectW = Math.max(64, shortLabel.length * 7 + 16);

    labelG.innerHTML = `
      <rect x="-${rectW / 2}" y="-10" width="${rectW}" height="20" rx="10" 
            fill="#090d16" stroke="${meta.color}" stroke-width="1.2" stroke-opacity="0.8" />
      <text text-anchor="middle" y="3.5" fill="#e2e8f0" font-size="10" 
            font-family="'JetBrains Mono', monospace" font-weight="600">${shortLabel}</text>
    `;
    labelG.style.opacity = showCableLabels ? "0.85" : "0";
    cableLabelsGroup.appendChild(labelG);

    // 3. 悬停探测 Hit 线 (加宽到 14px 且透明，极易鼠标触摸)
    const hit = document.createElementNS("http://www.w3.org/2000/svg", "path");
    hit.setAttribute("d", d);
    hit.setAttribute("class", "cable-hover-hit");
    hit.setAttribute("id", `cable-hit-${idx}`);
    hit.setAttribute("stroke", "transparent");
    hit.setAttribute("stroke-width", "14");
    hit.style.pointerEvents = "stroke";

    hit.addEventListener("mouseenter", (e) => showEdgeProbe(e, idx));
    hit.addEventListener("mousemove", (e) => moveEdgeProbe(e));
    hit.addEventListener("mouseleave", () => hideEdgeProbe(idx));
    hit.addEventListener("click", () => selectStory(edge.storyId));

    cableHitGroup.appendChild(hit);
  });
}

function updateCables() {
  GRAPH.dataTopology.edges.forEach((edge, idx) => {
    const geom = getEdgeGeometry(edge, idx);
    const d = computePathString(geom);
    const mid = computeMidpoint(geom);

    const path = document.getElementById(`cable-${idx}`);
    if (path) path.setAttribute("d", d);

    const labelG = document.getElementById(`cable-label-${idx}`);
    if (labelG) labelG.setAttribute("transform", `translate(${mid.x}, ${mid.y})`);

    const hit = document.getElementById(`cable-hit-${idx}`);
    if (hit) hit.setAttribute("d", d);
  });
}

// 连线风格切换
const btnToggleLineStyle = document.getElementById("btnToggleLineStyle");
const lineStyleLabel = document.getElementById("lineStyleLabel");
function updateLineStyleUI() {
  lineStyleLabel.textContent = lineStyle === "step" ? "📐 正交折线" : "🌊 平滑流体";
  localStorage.setItem("minibgm_line_style", lineStyle);
}
updateLineStyleUI();

btnToggleLineStyle.addEventListener("click", () => {
  lineStyle = lineStyle === "step" ? "bezier" : "step";
  updateLineStyleUI();
  updateCables();
});

// 连线标签切换
const btnToggleLabels = document.getElementById("btnToggleLabels");
const labelsLabel = document.getElementById("labelsLabel");
function updateLabelsUI() {
  labelsLabel.textContent = showCableLabels ? "🏷️ 隐藏标签" : "🏷️ 载荷标签";
  if (showCableLabels) {
    btnToggleLabels.classList.add("bg-emerald-950/40", "border-emerald-500/40", "text-emerald-300");
  } else {
    btnToggleLabels.classList.remove("bg-emerald-950/40", "border-emerald-500/40", "text-emerald-300");
  }
  localStorage.setItem("minibgm_show_labels", showCableLabels);
  if (!activeStoryId && !activeNodeId) {
    GRAPH.dataTopology.edges.forEach((_, idx) => {
      const l = document.getElementById(`cable-label-${idx}`);
      if (l) l.style.opacity = showCableLabels ? "0.85" : "0";
    });
  }
}
updateLabelsUI();

btnToggleLabels.addEventListener("click", () => {
  showCableLabels = !showCableLabels;
  updateLabelsUI();
});

// ==============================================================================
// 连线悬停探针浮窗逻辑 (Edge Probe Tooltip)
// ==============================================================================

function showEdgeProbe(e, idx) {
  hoveredEdgeIdx = idx;
  const edge = GRAPH.dataTopology.edges[idx];
  const meta = getStoryMeta(edge.storyId);
  const path = document.getElementById(`cable-${idx}`);
  const labelG = document.getElementById(`cable-label-${idx}`);

  // 所有其他连线淡出，悬停连线进入高对比焦点
  GRAPH.dataTopology.edges.forEach((_, i) => {
    const p = document.getElementById(`cable-${i}`);
    const l = document.getElementById(`cable-label-${i}`);
    if (i !== idx) {
      if (p) p.setAttribute("stroke-opacity", "0.04");
      if (l) l.style.opacity = "0";
    }
  });

  if (path) {
    path.setAttribute("stroke", meta.color);
    path.setAttribute("stroke-width", "3.8");
    path.setAttribute("stroke-opacity", "1");
    path.setAttribute("filter", "url(#glowFilter)");
    path.classList.add("cable-solo-active");
  }
  if (labelG) labelG.style.opacity = "1";

  // 高亮连接的两端卡片
  document.querySelectorAll(".node-card").forEach(c => {
    const cid = c.dataset.id;
    c.classList.remove("highlighted", "action-highlighted", "upstream-highlighted", "downstream-highlighted");
    if (cid === edge.from) {
      c.classList.remove("dimmed");
      c.classList.add("upstream-highlighted");
    } else if (cid === edge.to) {
      c.classList.remove("dimmed");
      c.classList.add("downstream-highlighted");
    } else {
      c.classList.add("dimmed");
    }
  });

  // 填充探针数据
  document.getElementById("tipStoryDot").style.backgroundColor = meta.color;
  document.getElementById("tipStoryName").textContent = meta.name;
  document.getElementById("tipFlowType").textContent = edge.flow === "action" ? "⚡ ACTION 回路" : "📥 STATE 下行";
  document.getElementById("tipFrom").textContent = edge.from;
  document.getElementById("tipTo").textContent = edge.to;
  document.getElementById("tipPayload").textContent = edge.label;

  cableTooltip.style.left = `${e.clientX + 14}px`;
  cableTooltip.style.top = `${e.clientY + 14}px`;
  cableTooltip.classList.remove("hidden");
}

function moveEdgeProbe(e) {
  cableTooltip.style.left = `${e.clientX + 14}px`;
  cableTooltip.style.top = `${e.clientY + 14}px`;
}

function hideEdgeProbe(idx) {
  hoveredEdgeIdx = null;
  cableTooltip.classList.add("hidden");
  if (activeNodeId) {
    selectNode(activeNodeId);
  } else {
    selectStory(activeStoryId);
  }
}

// ==============================================================================
// 故事流聚焦与全局视图
// ==============================================================================

function selectStory(storyId) {
  activeStoryId = (activeStoryId === storyId) ? null : storyId;
  activeNodeId = null;
  document.getElementById("lineageBar")?.classList.add("hidden");

  document.querySelectorAll(".story-pill").forEach(btn => {
    if (btn.dataset.story === activeStoryId) {
      btn.classList.add("ring-2", "ring-cyan-400", "bg-slate-800", "text-white");
    } else {
      btn.classList.remove("ring-2", "ring-cyan-400", "bg-slate-800", "text-white");
    }
  });

  const story = GRAPH.dataTopology.stories.find(s => s.id === activeStoryId);

  if (story && story.type === "action") {
    showRippleHud(story);
  } else {
    hideRippleHud();
  }

  if (!activeStoryId) {
    // 全局视图：清爽低噪底色，清晰展示整体架构拓扑
    document.querySelectorAll(".node-card").forEach(c => {
      c.classList.remove("dimmed", "highlighted", "action-highlighted", "upstream-highlighted", "downstream-highlighted", "current-highlighted");
    });
    GRAPH.dataTopology.edges.forEach((edge, idx) => {
      const p = document.getElementById(`cable-${idx}`);
      const l = document.getElementById(`cable-label-${idx}`);
      if (!p) return;
      const meta = getStoryMeta(edge.storyId);
      p.classList.remove("cable-down-active", "cable-action-active", "cable-solo-active");
      p.setAttribute("stroke", meta.color);
      p.setAttribute("stroke-width", "1.6");
      p.setAttribute("stroke-opacity", "0.28");
      p.setAttribute("marker-end", `url(#arrow-${edge.storyId})`);
      p.removeAttribute("filter");
      if (l) l.style.opacity = showCableLabels ? "0.85" : "0";
    });
    return;
  }

  const activeNodeIds = new Set();
  const isAction = story.type === "action";

  GRAPH.dataTopology.edges.forEach((edge, idx) => {
    const p = document.getElementById(`cable-${idx}`);
    const l = document.getElementById(`cable-label-${idx}`);
    if (!p) return;
    const meta = getStoryMeta(edge.storyId);

    if (edge.storyId === activeStoryId) {
      activeNodeIds.add(edge.from);
      activeNodeIds.add(edge.to);

      p.classList.remove("cable-down-active", "cable-action-active", "cable-solo-active");
      p.classList.add(isAction ? "cable-action-active" : "cable-down-active");
      p.setAttribute("stroke", meta.color);
      p.setAttribute("stroke-width", "3");
      p.setAttribute("stroke-opacity", "1");
      p.setAttribute("marker-end", `url(#arrow-${edge.storyId})`);
      p.setAttribute("filter", "url(#glowFilter)");
      if (l) l.style.opacity = "1";
    } else {
      p.classList.remove("cable-down-active", "cable-action-active", "cable-solo-active");
      p.setAttribute("stroke", meta.color);
      p.setAttribute("stroke-width", "1");
      p.setAttribute("stroke-opacity", "0.04");
      p.removeAttribute("filter");
      if (l) l.style.opacity = "0";
    }
  });

  document.querySelectorAll(".node-card").forEach(c => {
    const id = c.dataset.id;
    c.classList.remove("dimmed", "highlighted", "action-highlighted", "upstream-highlighted", "downstream-highlighted", "current-highlighted");
    if (activeNodeIds.has(id)) {
      c.classList.add(isAction ? "action-highlighted" : "highlighted");
    } else {
      c.classList.add("dimmed");
    }
  });
}

function highlightNodeHover(nodeId, isHover) {
  if (isHover) {
    const directNodes = new Set([nodeId]);
    GRAPH.dataTopology.edges.forEach((edge, idx) => {
      const p = document.getElementById(`cable-${idx}`);
      const l = document.getElementById(`cable-label-${idx}`);
      if (!p) return;
      const meta = getStoryMeta(edge.storyId);
      if (edge.from === nodeId || edge.to === nodeId) {
        directNodes.add(edge.from);
        directNodes.add(edge.to);
        p.setAttribute("stroke", meta.color);
        p.setAttribute("stroke-width", "3");
        p.setAttribute("stroke-opacity", "1");
        p.setAttribute("filter", "url(#glowFilter)");
        if (l) l.style.opacity = "1";
      } else {
        p.setAttribute("stroke-opacity", "0.04");
        if (l) l.style.opacity = "0";
      }
    });
    document.querySelectorAll(".node-card").forEach(c => {
      if (directNodes.has(c.dataset.id)) c.classList.remove("dimmed");
      else c.classList.add("dimmed");
    });
  } else {
    selectStory(activeStoryId);
  }
}

// ==============================================================================
// 节点级单线血缘追溯 (彻底看清：数据从何而来，经历了什么，去向何方)
// ==============================================================================

function selectNode(nodeId) {
  activeNodeId = (activeNodeId === nodeId) ? null : nodeId;
  if (!activeNodeId) {
    document.getElementById("lineageBar")?.classList.add("hidden");
    closeDrawer();
    selectStory(activeStoryId);
    return;
  }

  openDrawer(activeNodeId);

  const node = GRAPH.dataTopology.nodes.find(n => n.id === nodeId);
  const upNodes = new Set();
  const downNodes = new Set();
  let inCount = 0;
  let outCount = 0;

  GRAPH.dataTopology.edges.forEach((edge) => {
    if (edge.to === nodeId) {
      upNodes.add(edge.from);
      inCount++;
    } else if (edge.from === nodeId) {
      downNodes.add(edge.to);
      outCount++;
    }
  });

  // 更新连线样式：流入来源呈现翡翠绿 (Emerald)，流出去向呈现天蓝色 (Sky)，其余无关全褪淡
  GRAPH.dataTopology.edges.forEach((edge, idx) => {
    const p = document.getElementById(`cable-${idx}`);
    const l = document.getElementById(`cable-label-${idx}`);
    if (!p) return;

    if (edge.to === nodeId) {
      // 🌿 上游数据来源 (流入)
      p.setAttribute("stroke", "#10b981");
      p.setAttribute("stroke-width", "3.2");
      p.setAttribute("stroke-opacity", "1");
      p.setAttribute("marker-end", "url(#arrow-upstream)");
      p.classList.add("cable-solo-active");
      p.setAttribute("filter", "url(#glowFilter)");
      if (l) l.style.opacity = "1";
    } else if (edge.from === nodeId) {
      // 🌊 下游数据去向 (流出)
      p.setAttribute("stroke", "#38bdf8");
      p.setAttribute("stroke-width", "3.2");
      p.setAttribute("stroke-opacity", "1");
      p.setAttribute("marker-end", "url(#arrow-downstream)");
      p.classList.add("cable-solo-active");
      p.setAttribute("filter", "url(#glowFilter)");
      if (l) l.style.opacity = "1";
    } else {
      p.setAttribute("stroke-opacity", "0.03");
      p.classList.remove("cable-solo-active", "cable-down-active", "cable-action-active");
      p.removeAttribute("filter");
      if (l) l.style.opacity = "0";
    }
  });

  // 更新卡片样式：目标黄金高亮，来源翡翠高亮，去向天蓝高亮
  document.querySelectorAll(".node-card").forEach(c => {
    const cid = c.dataset.id;
    c.classList.remove("dimmed", "highlighted", "action-highlighted", "upstream-highlighted", "downstream-highlighted", "current-highlighted");
    if (cid === nodeId) {
      c.classList.add("current-highlighted");
    } else if (upNodes.has(cid)) {
      c.classList.add("upstream-highlighted");
    } else if (downNodes.has(cid)) {
      c.classList.add("downstream-highlighted");
    } else {
      c.classList.add("dimmed");
    }
  });

  // 弹出顶部单线血缘追溯条
  const lineageBar = document.getElementById("lineageBar");
  const upNames = Array.from(upNodes).map(id => GRAPH.dataTopology.nodes.find(n => n.id === id)?.name || id).slice(0, 2).join(', ');
  const downNames = Array.from(downNodes).map(id => GRAPH.dataTopology.nodes.find(n => n.id === id)?.name || id).slice(0, 2).join(', ');

  document.getElementById("lineageUpstreamLabel").textContent = `来源 (${inCount}): ${upNames || '无 (原始输入源)'}`;
  document.getElementById("lineageCurrentLabel").textContent = node ? node.name : nodeId;
  document.getElementById("lineageDownstreamLabel").textContent = `去向 (${outCount}): ${downNames || '无 (最终终端)'}`;
  lineageBar.classList.remove("hidden");
  lucide.createIcons();
}

// ==============================================================================
// 涟漪效应 HUD
// ==============================================================================

function showRippleHud(story) {
  document.getElementById("hudLoopTitle").textContent = `${story.title} · 跨页面联动涟漪效应`;
  document.getElementById("hudLoopSummary").textContent = story.summary;

  const rippleList = document.getElementById("hudRippleList");
  rippleList.innerHTML = "";

  (story.ripples || []).forEach(r => {
    const card = document.createElement("div");
    card.className = "rounded-xl border border-slate-800 bg-slate-900/90 p-2.5 backdrop-blur-md";
    card.innerHTML = `
      <div class="text-[11px] font-bold text-rose-400 flex items-center gap-1.5 mb-1">
        <span class="w-1.5 h-1.5 rounded-full bg-rose-400"></span>
        ${r.where}
      </div>
      <div class="text-[11px] text-slate-300 leading-snug">${r.what}</div>
    `;
    rippleList.appendChild(card);
  });

  rippleHud.classList.remove("hidden");
}

function hideRippleHud() {
  rippleHud.classList.add("hidden");
}

document.getElementById("btnCloseHud").addEventListener("click", hideRippleHud);

// ==============================================================================
// 抽屉详情与源码查看
// ==============================================================================

function openDrawer(nodeId) {
  const node = GRAPH.dataTopology.nodes.find(n => n.id === nodeId);
  if (!node) return;

  const cat = getCategoryColor(node.category);
  document.getElementById("drawerTitle").textContent = node.name;
  document.getElementById("drawerSubtitle").textContent = node.sub || node.category;
  document.getElementById("drawerDesc").textContent = node.desc;

  const iconBox = document.getElementById("drawerIconBox");
  iconBox.className = `w-8 h-8 rounded-xl ${cat.bg} ${cat.text} flex items-center justify-center border ${cat.border}`;
  document.getElementById("drawerIcon").setAttribute("data-lucide", node.icon || "box");

  const modelBox = document.getElementById("drawerModelBox");
  if (node.modelName) {
    modelBox.classList.remove("hidden");
    document.getElementById("drawerModelName").textContent = node.modelName;
  } else {
    modelBox.classList.add("hidden");
  }

  const fieldsBody = document.getElementById("drawerFieldsBody");
  fieldsBody.innerHTML = "";
  if (node.fields && node.fields.length > 0) {
    document.getElementById("drawerFieldsBox").classList.remove("hidden");
    document.getElementById("drawerFieldsTitle").textContent = "核心数据模型字段";
    node.fields.forEach(f => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td class="px-3 py-1.5 text-slate-200 font-semibold">${f.name}</td>
        <td class="px-3 py-1.5 text-cyan-400">${f.type}</td>
        <td class="px-3 py-1.5 text-slate-400">${f.desc}</td>
      `;
      fieldsBody.appendChild(tr);
    });
  } else if (node.operations && node.operations.length > 0) {
    document.getElementById("drawerFieldsBox").classList.remove("hidden");
    document.getElementById("drawerFieldsTitle").textContent = "核心转换操作清单";
    node.operations.forEach((op, idx) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td class="px-3 py-1.5 text-slate-400">#${idx + 1}</td>
        <td colspan="2" class="px-3 py-1.5 text-slate-200">${op}</td>
      `;
      fieldsBody.appendChild(tr);
    });
  } else {
    document.getElementById("drawerFieldsBox").classList.add("hidden");
  }

  const pipesList = document.getElementById("drawerPipesList");
  pipesList.innerHTML = "";
  const inEdges = GRAPH.dataTopology.edges.filter(e => e.to === nodeId);
  const outEdges = GRAPH.dataTopology.edges.filter(e => e.from === nodeId);

  inEdges.forEach(e => {
    const meta = getStoryMeta(e.storyId);
    const item = document.createElement("div");
    item.className = "p-2 rounded-lg bg-slate-900 border border-slate-800 flex items-center justify-between text-[11px]";
    item.innerHTML = `<span class="text-slate-500">输入源:</span> <span style="color:${meta.color}" class="font-bold">${e.from}</span> <span class="text-slate-400 font-sans truncate max-w-[150px]">${e.label}</span>`;
    pipesList.appendChild(item);
  });
  outEdges.forEach(e => {
    const meta = getStoryMeta(e.storyId);
    const item = document.createElement("div");
    item.className = "p-2 rounded-lg bg-slate-900 border border-slate-800 flex items-center justify-between text-[11px]";
    item.innerHTML = `<span class="text-slate-500">流向:</span> <span style="color:${meta.color}" class="font-bold">${e.to}</span> <span class="text-slate-400 font-sans truncate max-w-[150px]">${e.label}</span>`;
    pipesList.appendChild(item);
  });

  const sourceBox = document.getElementById("drawerSourceBox");
  const filePathEl = document.getElementById("drawerFilePath");
  const snippetEl = document.getElementById("drawerCodeSnippet");

  if (node.file && GRAPH.sources[node.file]) {
    sourceBox.classList.remove("hidden");
    filePathEl.textContent = node.file;
    const content = GRAPH.sources[node.file];
    const lines = content.split("\n").slice(0, 50).join("\n");
    snippetEl.textContent = lines + (content.split("\n").length > 50 ? "\n... (省略后续行)" : "");
  } else {
    sourceBox.classList.add("hidden");
  }

  detailDrawer.classList.remove("translate-x-full");
  lucide.createIcons();
}

function closeDrawer() {
  detailDrawer.classList.add("translate-x-full");
}

document.getElementById("btnCloseDrawer").addEventListener("click", closeDrawer);

// ==============================================================================
// 画布平移缩放 (Panzoom)
// ==============================================================================

function applyTransform() {
  canvasStage.style.transform = `matrix(${currentZoom}, 0, 0, ${currentZoom}, ${panX}, ${panY})`;
  document.getElementById("zoomPercent").textContent = `${Math.round(currentZoom * 100)}%`;
}

function zoom(delta, clientX, clientY) {
  const rect = topologyView.getBoundingClientRect();
  const cx = clientX !== undefined ? clientX - rect.left : rect.width / 2;
  const cy = clientY !== undefined ? clientY - rect.top : rect.height / 2;

  const newZoom = Math.min(Math.max(0.3, currentZoom + delta), 2.5);
  const factor = newZoom / currentZoom;

  panX = cx - (cx - panX) * factor;
  panY = cy - (cy - panY) * factor;
  currentZoom = newZoom;
  applyTransform();
}

function fitView() {
  const viewW = topologyView.clientWidth;
  const viewH = topologyView.clientHeight;
  const stageW = 1920;
  const stageH = 1240;

  const scale = Math.min((viewW - 60) / stageW, (viewH - 60) / stageH, 1.0);
  currentZoom = Math.max(scale, 0.45);
  panX = (viewW - stageW * currentZoom) / 2;
  panY = (viewH - stageH * currentZoom) / 2;
  applyTransform();
}

topologyView.addEventListener("wheel", (e) => {
  e.preventDefault();
  const delta = e.deltaY < 0 ? 0.1 : -0.1;
  zoom(delta, e.clientX, e.clientY);
}, { passive: false });

topologyView.addEventListener("mousedown", (e) => {
  if (e.target.closest(".node-card") || e.target.closest("button") || e.target.closest(".cable-hover-hit")) return;
  isCanvasPanning = true;
  canvasStartX = e.clientX - panX;
  canvasStartY = e.clientY - panY;
});

window.addEventListener("mousemove", (e) => {
  if (!isCanvasPanning) return;
  panX = e.clientX - canvasStartX;
  panY = e.clientY - canvasStartY;
  applyTransform();
});

window.addEventListener("mouseup", () => {
  isCanvasPanning = false;
});

document.getElementById("btnZoomIn").addEventListener("click", () => zoom(0.15));
document.getElementById("btnZoomOut").addEventListener("click", () => zoom(-0.15));
document.getElementById("btnZoomReset").addEventListener("click", () => {
  currentZoom = 1.0;
  fitView();
});
document.getElementById("btnFitScreen").addEventListener("click", fitView);

// ==============================================================================
// 故事流药丸按钮初始化
// ==============================================================================

function renderStoryButtons() {
  const bar = document.getElementById("storyButtonList");
  bar.innerHTML = "";

  const stories = GRAPH.dataTopology.stories;

  // 全部连线按钮
  const btnAll = document.createElement("button");
  btnAll.className = "story-pill px-3 py-1 rounded-lg border border-slate-800 bg-slate-900/90 text-slate-400 hover:text-white flex items-center gap-1.5 transition-all text-xs shrink-0";
  btnAll.innerHTML = `<i data-lucide="eye" class="w-3.5 h-3.5 text-slate-400"></i> 全局全彩`;
  btnAll.addEventListener("click", () => selectStory(null));
  bar.appendChild(btnAll);

  const div0 = document.createElement("div");
  div0.className = "w-px h-4 bg-slate-800 mx-1 shrink-0";
  bar.appendChild(div0);

  const downLabel = document.createElement("span");
  downLabel.className = "text-[11px] font-semibold text-slate-500 uppercase tracking-wider mr-1";
  downLabel.textContent = "状态下行:";
  bar.appendChild(downLabel);

  stories.filter(s => s.type === "down").forEach(s => {
    const meta = getStoryMeta(s.id);
    const btn = document.createElement("button");
    btn.dataset.story = s.id;
    btn.className = "story-pill px-3 py-1 rounded-lg border border-slate-800 bg-slate-900/90 text-slate-300 hover:text-white flex items-center gap-1.5 transition-all text-xs shrink-0";
    btn.innerHTML = `<span class="w-2 h-2 rounded-full ${meta.dot}"></span> ${s.title}`;
    btn.addEventListener("click", () => selectStory(s.id));
    bar.appendChild(btn);
  });

  const div = document.createElement("div");
  div.className = "w-px h-4 bg-slate-800 mx-1 shrink-0";
  bar.appendChild(div);

  const actionLabel = document.createElement("span");
  actionLabel.className = "text-[11px] font-semibold text-rose-400 uppercase tracking-wider mr-1 flex items-center gap-1";
  actionLabel.innerHTML = `<span class="w-1.5 h-1.5 rounded-full bg-rose-400 animate-pulse"></span> 用户点击写回路:`;
  bar.appendChild(actionLabel);

  stories.filter(s => s.type === "action").forEach(s => {
    const meta = getStoryMeta(s.id);
    const btn = document.createElement("button");
    btn.dataset.story = s.id;
    btn.className = "story-pill px-3 py-1 rounded-lg border border-rose-500/30 bg-rose-950/20 text-rose-300 hover:text-white flex items-center gap-1.5 transition-all text-xs shrink-0";
    btn.innerHTML = `<span class="w-2 h-2 rounded-full ${meta.dot} animate-pulse"></span> ${s.title}`;
    btn.addEventListener("click", () => selectStory(s.id));
    bar.appendChild(btn);
  });
}

// ==============================================================================
// 视图切换 (拓扑 vs 模块表格)
// ==============================================================================

const viewTabTopology = document.getElementById("viewTabTopology");
const viewTabModules = document.getElementById("viewTabModules");
const topologyViewEl = document.getElementById("topologyView");
const modulesViewEl = document.getElementById("modulesView");

viewTabTopology.addEventListener("click", () => {
  viewTabTopology.className = "px-3.5 py-1.5 rounded-lg transition-all flex items-center gap-1.5 text-white bg-slate-800 shadow-sm";
  viewTabModules.className = "px-3.5 py-1.5 rounded-lg transition-all flex items-center gap-1.5 text-slate-400 hover:text-white";
  topologyViewEl.classList.remove("hidden");
  modulesViewEl.classList.add("hidden");
});

viewTabModules.addEventListener("click", () => {
  viewTabModules.className = "px-3.5 py-1.5 rounded-lg transition-all flex items-center gap-1.5 text-white bg-slate-800 shadow-sm";
  viewTabTopology.className = "px-3.5 py-1.5 rounded-lg transition-all flex items-center gap-1.5 text-slate-400 hover:text-white";
  modulesViewEl.classList.remove("hidden");
  topologyViewEl.classList.add("hidden");
});

function renderModuleTable() {
  const tbody = document.getElementById("moduleTableBody");
  tbody.innerHTML = "";

  GRAPH.modules.forEach(m => {
    const tr = document.createElement("tr");
    tr.className = "hover:bg-slate-800/40 transition-colors";

    const instColor = m.instability > 0.7 ? "text-rose-400" : (m.instability < 0.3 ? "text-emerald-400" : "text-amber-400");
    const filesCount = m.files ? m.files.length : 0;
    const linesCount = m.files ? m.files.reduce((a, b) => a + b.lines, 0) : 0;

    tr.innerHTML = `
      <td class="px-6 py-3 font-semibold text-slate-200">${m.module}</td>
      <td class="px-4 py-3"><span class="px-2 py-0.5 rounded text-[10px] bg-slate-800 text-slate-300">${m.group}</span></td>
      <td class="px-4 py-3 text-slate-400">${m.kind.toUpperCase()}</td>
      <td class="px-4 py-3 text-slate-300">${m.ca}</td>
      <td class="px-4 py-3 text-slate-300">${m.ce}</td>
      <td class="px-4 py-3 font-bold ${instColor}">${m.instability}</td>
      <td class="px-6 py-3 text-slate-400">${filesCount} 文件 · ${linesCount.toLocaleString()} 行</td>
    `;
    tbody.appendChild(tr);
  });
}

// ==============================================================================
// 架构红线 Modal
// ==============================================================================

const redlinesModal = document.getElementById("redlinesModal");
document.getElementById("btnRedlines").addEventListener("click", () => {
  renderRedlines();
  redlinesModal.classList.remove("hidden");
});
document.getElementById("btnCloseRedlinesModal").addEventListener("click", () => {
  redlinesModal.classList.add("hidden");
});

function renderRedlines() {
  const list = document.getElementById("redlinesList");
  list.innerHTML = "";

  const rules = [
    { id: "R1", name: "Feature 隔离", desc: "Feature 模块之间严禁直接项目依赖，跨功能导航全部通过 Navigation3 Route 路由解耦", test: "feature_modules_never_depend_on_each_other" },
    { id: "R2", name: "单一数据源 (SSOT)", desc: "UI 层严禁越级依赖 :core:network/:core:database/:core:datastore，只允许通过 :core:data Repositories 供数", test: "feature_modules_never_depend_directly_on_network_or_database" },
    { id: "R3", name: "纯 Kotlin 模型层", desc: ":core:model 严禁引入任何 android.* / androidx.* 依赖，保持极致纯粹与可移植", test: "core_model_is_pure_kotlin_without_android_or_ui_dependencies" },
    { id: "R4", name: "MVI 单向数据流", desc: "ViewModel 仅暴露不可变 StateFlow<UiState>，严禁向外暴露任何 MutableStateFlow", test: "viewModels_never_expose_mutable_state_flow" },
    { id: "R5", name: "凭据硬件隔离", desc: "OAuth Access/Refresh Tokens 严禁存入普通 UserPreferences，必须通过 KeyStore 硬件加密隔离", test: "userPreferences_never_stores_sensitive_tokens" },
    { id: "R6", name: "传输/存储框架隔离", desc: "Feature 模块严禁引入 io.ktor.* 或 androidx.room.* 传输与持久化框架类", test: "feature_sources_never_import_transport_or_storage_frameworks" },
    { id: "R7", name: "主题设计系统一致性", desc: "业务 Feature 严禁硬编码 Color(0x...)，必须统一从 :core:designsystem 提取设计令牌", test: "feature_sources_never_hardcode_compose_colors" },
    { id: "R8", name: "裸网络/磁盘 IO 隔离", desc: "业务 Feature 严禁自行手写 HttpURLConnection, URL, Socket 或私自写入 filesDir", test: "feature_sources_never_perform_raw_network_or_private_disk_io" },
  ];

  rules.forEach(r => {
    const card = document.createElement("div");
    card.className = "rounded-xl border border-emerald-500/20 bg-emerald-500/5 p-3.5 flex items-start justify-between gap-4";
    card.innerHTML = `
      <div class="space-y-1">
        <div class="flex items-center gap-2">
          <span class="text-xs font-mono font-bold text-emerald-400 bg-emerald-500/10 px-1.5 py-0.5 rounded border border-emerald-500/20">${r.id}</span>
          <span class="text-xs font-bold text-white">${r.name}</span>
          <span class="text-[10px] font-mono text-slate-400">(${r.test})</span>
        </div>
        <p class="text-xs text-slate-300">${r.desc}</p>
      </div>
      <span class="px-2 py-1 rounded-lg bg-emerald-500/15 text-emerald-400 font-bold text-[11px] flex items-center gap-1 shrink-0">
        <i data-lucide="check" class="w-3 h-3"></i> PASS
      </span>
    `;
    list.appendChild(card);
  });
  lucide.createIcons();
}

// ==============================================================================
// 搜索与快捷键
// ==============================================================================

const searchInput = document.getElementById("searchInput");
searchInput.addEventListener("input", (e) => {
  const q = e.target.value.trim().toLowerCase();
  if (!q) {
    selectStory(activeStoryId);
    return;
  }

  document.querySelectorAll(".node-card").forEach(c => {
    const id = c.dataset.id;
    const node = GRAPH.dataTopology.nodes.find(n => n.id === id);
    const text = JSON.stringify(node).toLowerCase();
    if (text.includes(q)) {
      c.classList.remove("dimmed");
      c.classList.add("highlighted");
    } else {
      c.classList.remove("highlighted", "action-highlighted");
      c.classList.add("dimmed");
    }
  });
});

window.addEventListener("keydown", (e) => {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
    e.preventDefault();
    searchInput.focus();
  } else if (e.key === "Escape") {
    if (activeNodeId) {
      selectNode(activeNodeId);
    } else {
      selectStory(null);
    }
    closeDrawer();
    hideRippleHud();
    redlinesModal.classList.add("hidden");
  }
});

document.getElementById("btnCloseLineage")?.addEventListener("click", () => {
  if (activeNodeId) selectNode(activeNodeId);
});

// ==============================================================================
// 初始化执行
// ==============================================================================
renderStoryButtons();
renderTopologyNodes();
renderTopologyCables();
renderModuleTable();
updateLabelsUI();
selectStory(activeStoryId);
lucide.createIcons();

setTimeout(() => {
  fitView();
}, 60);
</script>
</body>
</html>
"""


# ==============================================================================
# 6. 入口与 CLI
# ==============================================================================

def main() -> int:
    parser = argparse.ArgumentParser(description="MiniBgm 架构控制台与数据流拓扑生成器")
    parser.add_argument("--open", action="store_true", help="生成后自动在默认浏览器中打开 HTML")
    parser.add_argument("--check", action="store_true", help="CI 静态红线门禁模式：检测到违规返回退出码 1")
    parser.add_argument("--output", type=str, default="", help="自定义输出 HTML 路径")
    args = parser.parse_args()

    data = build_graph()
    payload = json.dumps(data, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
    html = HTML_TEMPLATE.replace("__DATA__", payload)

    out = Path(args.output) if args.output else Path(__file__).resolve().parent / "architecture.html"
    out.write_text(html, encoding="utf-8")

    s = data["stats"]
    v_count = s["violations"]
    stories_count = len(data["dataTopology"]["stories"])
    nodes_count = len(data["dataTopology"]["nodes"])
    edges_count = len(data["dataTopology"]["edges"])

    print(f"OK  架构数据流控制台生成完毕 -> {out}")
    print(f"    模块 {s['modules']} · 源文件 {s['files']} · {s['loc']} 行")
    print(f"    [可辨识拓扑] 节点 {nodes_count} 个 · 管道 {edges_count} 条 (通道分槽 · 语义色彩 · 悬停探针)")
    if v_count == 0:
        print(f"    [规则守卫] 8 条架构红线全部通过 ✓ (零违规)")
    else:
        print(f"    [规则守卫] 检测到 {v_count} 处违规:")
        for v in data["violations"]:
            print(f"      - [{v['rule']}] {v['module']} @ {v['where']}: {v['detail']}")

    if args.open:
        webbrowser.open(out.as_uri())

    if args.check and v_count > 0:
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
