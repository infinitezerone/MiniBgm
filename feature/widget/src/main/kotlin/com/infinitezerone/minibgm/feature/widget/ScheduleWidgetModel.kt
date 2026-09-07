package com.infinitezerone.minibgm.feature.widget

/** 小组件单个条目的展示态模型 */
data class ScheduleWidgetItemUiModel(
    val subjectId: Long,
    val title: String,
    val episode: Int,
    /** 格式化后的播映信息，例如 "第4集 · 今天 23:30" 或 "今天 23:30" */
    val episodeSubtitle: String,
    /** 倒计时/状态胶囊文案，例如 "已更新"、"刚刚开播"、"2小时后"、"30分钟后"、"已开播" */
    val countdownBadge: String,
    val coverUrl: String,
    /** 置信度标签："表定" / "预估" / null */
    val kindTag: String? = null,
    /** 是否属于用户在看（追番） */
    val isTracked: Boolean = false,
    /** 是否今天已经播出 */
    val isAiredToday: Boolean = false,
    /** 播出时刻是否落在今天（用于区分「今日追番」与「下一部更新」） */
    val isToday: Boolean = false,
    /** 格式化播出时刻，例如 "23:00" */
    val airTimeCst: String = "",
)

/** 小组件整体展示态 */
data class ScheduleWidgetUiState(
    val isLoggedIn: Boolean,
    val items: List<ScheduleWidgetItemUiModel>,
    val formattedUpdateTime: String,
    val headerTitle: String = "今日更新",
    val headerSubtitle: String = "",
    val hasTrackedItems: Boolean = false,
)
