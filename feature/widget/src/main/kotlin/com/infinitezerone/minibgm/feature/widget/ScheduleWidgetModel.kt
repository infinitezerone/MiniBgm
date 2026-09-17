package com.infinitezerone.minibgm.feature.widget

/** 条目状态分区：已更新可看 -> 今天待播 -> 未来 7 天 */
enum class ScheduleWidgetSection {
    WATCHABLE,
    UPCOMING_TODAY,
    LATER,
}

/** 日期指代（相对今天）：昨天 / 明天 / 周几（1=周一 … 7=周日） */
sealed interface AirDay {
    data object Yesterday : AirDay

    data object Tomorrow : AirDay

    data class Weekday(
        val index: Int,
    ) : AirDay
}

/**
 * 单一状态来源的结构化表示，渲染成一个状态胶囊；文案由 UI 层按语言资源渲染，
 * Planner 只产出语义与数据（时刻字符串为 HH:mm 数据格式，非文案）。
 */
sealed interface AirStatusLine {
    /** 今天已更新，现在可看 */
    data object AiredToday : AirStatusLine

    /** lookback 窗口内更早播出（昨天 / 更早的周几），现在仍可看 */
    data class Aired(
        val day: AirDay,
        val time: String,
    ) : AirStatusLine

    /** 今天待播（本地时刻） */
    data class TodayAt(
        val time: String,
    ) : AirStatusLine

    /** 未来 7 天内待播（明天 / 周几 + 本地时刻） */
    data class Upcoming(
        val day: AirDay,
        val time: String,
    ) : AirStatusLine

    /** 追番中但无排期 */
    data object Watching : AirStatusLine

    /** 公共日历条目无时刻 */
    data object OnAir : AirStatusLine
}

/** 小组件单个条目的展示态模型 */
data class ScheduleWidgetItemUiModel(
    val subjectId: Long,
    val title: String,
    val episode: Int,
    val status: AirStatusLine,
    /** 原始时刻可信度（AirEventKind 常量），文案由 UI 映射为「表定 / 预估」 */
    val airKind: String? = null,
    /** 封面图地址，仅 4x4 hero 使用 */
    val coverUrl: String = "",
    /** 是否属于用户在看（追番） */
    val isTracked: Boolean = false,
) {
    /** 现在就可看（驱动胶囊配色：tertiary 强调） */
    val isWatchable: Boolean
        get() = status is AirStatusLine.AiredToday || status is AirStatusLine.Aired
}

/** 小组件整体展示态 */
data class ScheduleWidgetUiState(
    val isLoggedIn: Boolean,
    /** 登录用户是否在追番（DOING 非空）——用于区分「没在追」与「在追但无更新」两种空态 */
    val hasTrackedSubjects: Boolean,
    /** 规划基准日（today）的 epochDay；日期文案由 UI 格式化 */
    val todayEpochDay: Long,
    /** 今天已更新，播出时间降序（刚播的在前） */
    val watchable: List<ScheduleWidgetItemUiModel>,
    /** 今天待播，时间升序 */
    val upcomingToday: List<ScheduleWidgetItemUiModel>,
    /** 明天起 7 天内，时间升序（无排期信息的「在看」条目垫底） */
    val later: List<ScheduleWidgetItemUiModel>,
) {
    /** 今日真实总数（分区列表未截断，即真实值；UI 展示条数自行 take） */
    val totalToday: Int
        get() = watchable.size + upcomingToday.size

    /** 本周（7 天窗口内）真实总数 */
    val totalWeek: Int
        get() = totalToday + later.size

    /** 全部条目按展示优先级排列：可看 -> 今日待播 -> 之后 */
    val orderedItems: List<ScheduleWidgetItemUiModel>
        get() = watchable + upcomingToday + later
}
