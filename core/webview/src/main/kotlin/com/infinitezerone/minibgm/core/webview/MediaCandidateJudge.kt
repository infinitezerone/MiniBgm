package com.infinitezerone.minibgm.core.webview

/**
 * 捕获层媒体候选判定表：后缀强信号 + Range 启发式 + 广告统计黑名单 + 候选排序。
 *
 * 纯函数、无 Android 依赖——`shouldInterceptRequest` 的回调线程上只做廉价字符串判断，
 * 任何异常都会炸进程，所以这里不碰正则回溯灾难（全部是线性匹配）。
 *
 * 动机：只靠媒体后缀判定会漏掉"直链无后缀、靠 Range 头拉流"的 JS 站形态，
 * 而视频/音频元素拉流必带 `Range: bytes=`，脚本与样式从不带——这是后缀之外
 * 唯一便宜的确定性信号（Kazumi 式启发式）。
 */
internal object MediaCandidateJudge {
    private val MEDIA_SUFFIX = Regex("""\.(m3u8|mp4|mkv|flv|webm|ts)(?:[?#].*)?$""")
    private val PLAYLIST_SUFFIX = Regex("""\.m3u8(?:[?#].*)?$""")
    private val DIRECT_VIDEO_SUFFIX = Regex("""\.(mp4|mkv|flv|webm)(?:[?#].*)?$""")
    private val SEGMENT_SUFFIX = Regex("""\.ts(?:[?#].*)?$""")
    private val STATIC_RESOURCE_SUFFIX =
        Regex("""\.(js|css|png|jpe?g|gif|webp|avif|svg|ico|woff2?|ttf|eot|html?|json|xml|txt|vtt|srt|ass)(?:[?#].*)?$""")

    /** 广告与统计端点关键字：带 Range 的也只可能是误报，直接拒绝 */
    private val AD_KEYWORDS =
        listOf(
            "doubleclick",
            "googlesyndication",
            "googleads",
            "googletagmanager",
            "google-analytics",
            "adservice",
            "adview",
            "/adserver",
            "/ads/",
            "/analytics",
            "analytics.",
            "umeng",
            "appsflyer",
            "cnzz",
            "taboola",
            "outbrain",
            "criteo",
        )

    /**
     * 该请求是否按媒体候选采纳。
     *
     * @param url 请求地址
     * @param rangeHeaderValue `Range` 请求头的值（无该头传 null）；`bytes=` 开头视为拉流信号
     */
    fun isMediaCandidate(
        url: String,
        rangeHeaderValue: String?,
    ): Boolean {
        val lower = url.lowercase()
        if (MEDIA_SUFFIX.containsMatchIn(lower)) return true
        if (rangeHeaderValue.isNullOrBlank()) return false
        if (!rangeHeaderValue.trim().startsWith("bytes=", ignoreCase = true)) return false
        if (STATIC_RESOURCE_SUFFIX.containsMatchIn(lower)) return false
        if (AD_KEYWORDS.any { it in lower }) return false
        return true
    }

    /**
     * 候选优先级：越小越优先。清单与直连视频最优先，Range 推断次之，
     * 分片（.ts）沉底——候选数截断时先丢分片，保住真正的播放清单。
     */
    fun rank(url: String): Int {
        val lower = url.lowercase()
        return when {
            PLAYLIST_SUFFIX.containsMatchIn(lower) -> 0
            DIRECT_VIDEO_SUFFIX.containsMatchIn(lower) -> 1
            SEGMENT_SUFFIX.containsMatchIn(lower) -> 3
            else -> 2
        }
    }
}
