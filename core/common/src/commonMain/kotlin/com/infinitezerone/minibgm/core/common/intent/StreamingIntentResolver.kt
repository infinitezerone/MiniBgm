package com.infinitezerone.minibgm.core.common.intent

/**
 * 识别后的官方流媒体应用分发目标
 */
data class StreamingAppTarget(
    val siteName: String,
    val appName: String,
    val packageNames: List<String>,
    val deepLinkUri: String?,
    val webFallbackUrl: String,
)

/**
 * 播放源 URL 原生 Deep Link 识别与解析分发器
 */
object StreamingIntentResolver {
    private val BILI_SEASON_REGEX = Regex("""(?:bilibili\.com/bangumi/play|b23\.tv)/ss(\d+)""")
    private val BILI_EP_REGEX = Regex("""(?:bilibili\.com/bangumi/play|b23\.tv)/ep(\d+)""")
    private val BILI_VIDEO_BV_REGEX = Regex("""(?:bilibili\.com/video|b23\.tv)/(BV[a-zA-Z0-9]+)""")
    private val BILI_VIDEO_AV_REGEX = Regex("""(?:bilibili\.com/video|b23\.tv)/av(\d+)""")
    private val BILI_GENERAL_REGEX = Regex("""(bilibili\.com|b23\.tv)""")

    private val GAMER_SN_REGEX = Regex("""gamer\.com\.tw/anime(?:Video|Ref)\.php\?(?:.*&)?sn=(\d+)""")
    private val GAMER_GENERAL_REGEX = Regex("""gamer\.com\.tw""")

    private val IQIYI_REGEX = Regex("""iqiyi\.com""")
    private val TENCENT_REGEX = Regex("""v\.qq\.com""")
    private val YOUKU_REGEX = Regex("""youku\.com""")

    fun resolve(url: String): StreamingAppTarget? {
        if (url.isBlank()) return null

        // 1. 哔哩哔哩 (Bilibili)
        val seasonMatch = BILI_SEASON_REGEX.find(url)
        if (seasonMatch != null) {
            val seasonId = seasonMatch.groupValues[1]
            return StreamingAppTarget(
                siteName = "bilibili",
                appName = "哔哩哔哩",
                packageNames = listOf("tv.danmaku.bili", "com.bilibili.app.in"),
                deepLinkUri = "bilibili://bangumi/season/$seasonId",
                webFallbackUrl = url,
            )
        }
        val epMatch = BILI_EP_REGEX.find(url)
        if (epMatch != null) {
            val epId = epMatch.groupValues[1]
            return StreamingAppTarget(
                siteName = "bilibili",
                appName = "哔哩哔哩",
                packageNames = listOf("tv.danmaku.bili", "com.bilibili.app.in"),
                deepLinkUri = "bilibili://bangumi/season/ep/$epId",
                webFallbackUrl = url,
            )
        }
        val bvMatch = BILI_VIDEO_BV_REGEX.find(url)
        if (bvMatch != null) {
            val bvid = bvMatch.groupValues[1]
            return StreamingAppTarget(
                siteName = "bilibili",
                appName = "哔哩哔哩",
                packageNames = listOf("tv.danmaku.bili", "com.bilibili.app.in"),
                deepLinkUri = "bilibili://video/$bvid",
                webFallbackUrl = url,
            )
        }
        val avMatch = BILI_VIDEO_AV_REGEX.find(url)
        if (avMatch != null) {
            val avid = avMatch.groupValues[1]
            return StreamingAppTarget(
                siteName = "bilibili",
                appName = "哔哩哔哩",
                packageNames = listOf("tv.danmaku.bili", "com.bilibili.app.in"),
                deepLinkUri = "bilibili://video/av$avid",
                webFallbackUrl = url,
            )
        }
        if (BILI_GENERAL_REGEX.containsMatchIn(url)) {
            return StreamingAppTarget(
                siteName = "bilibili",
                appName = "哔哩哔哩",
                packageNames = listOf("tv.danmaku.bili", "com.bilibili.app.in"),
                deepLinkUri = null,
                webFallbackUrl = url,
            )
        }

        // 2. 巴哈姆特動畫瘋 (Bahamut Anime Crazy)
        val gamerSnMatch = GAMER_SN_REGEX.find(url)
        if (gamerSnMatch != null) {
            val sn = gamerSnMatch.groupValues[1]
            return StreamingAppTarget(
                siteName = "gamer",
                appName = "巴哈姆特動畫瘋",
                packageNames = listOf("tw.com.gamer.android.animad"),
                deepLinkUri = "animegamer://animedetail?sn=$sn",
                webFallbackUrl = url,
            )
        }
        if (GAMER_GENERAL_REGEX.containsMatchIn(url)) {
            return StreamingAppTarget(
                siteName = "gamer",
                appName = "巴哈姆特動畫瘋",
                packageNames = listOf("tw.com.gamer.android.animad"),
                deepLinkUri = null,
                webFallbackUrl = url,
            )
        }

        // 3. 爱奇艺 (iQIYI)
        if (IQIYI_REGEX.containsMatchIn(url)) {
            return StreamingAppTarget(
                siteName = "iqiyi",
                appName = "爱奇艺",
                packageNames = listOf("com.qiyi.video"),
                deepLinkUri = null,
                webFallbackUrl = url,
            )
        }

        // 4. 腾讯视频 (Tencent Video)
        if (TENCENT_REGEX.containsMatchIn(url)) {
            return StreamingAppTarget(
                siteName = "qq",
                appName = "腾讯视频",
                packageNames = listOf("com.tencent.qqlive"),
                deepLinkUri = null,
                webFallbackUrl = url,
            )
        }

        // 5. 优酷视频 (Youku)
        if (YOUKU_REGEX.containsMatchIn(url)) {
            return StreamingAppTarget(
                siteName = "youku",
                appName = "优酷视频",
                packageNames = listOf("com.youku.phone"),
                deepLinkUri = null,
                webFallbackUrl = url,
            )
        }

        return null
    }
}
