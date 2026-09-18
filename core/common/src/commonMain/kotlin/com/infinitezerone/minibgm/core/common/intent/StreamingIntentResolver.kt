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
    val isSearch: Boolean = false,
)

/**
 * 播放源 URL 原生 Deep Link 识别与解析分发器
 */
object StreamingIntentResolver {
    val BILIBILI_PACKAGE_NAMES =
        listOf(
            "tv.danmaku.bili",
            "com.bilibili.app.in",
            "tv.danmaku.bilibilihd",
            "com.bilibili.app.blue",
        )

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

    private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()

    /**
     * 纯 Kotlin 实现的 RFC 3986 查询参数百分号编码（避免跨平台及 Feature 层直接依赖 java.net.*）
     */
    fun encodeQueryParameter(value: String): String {
        if (value.isEmpty()) return ""
        val bytes = value.encodeToByteArray()
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val u = b.toInt() and 0xFF
            if (u in 0x30..0x39 || u in 0x41..0x5A || u in 0x61..0x7A || u == 0x2D || u == 0x5F || u == 0x2E || u == 0x7E) {
                sb.append(u.toChar())
            } else {
                sb.append('%')
                sb.append(HEX_DIGITS[u ushr 4])
                sb.append(HEX_DIGITS[u and 0x0F])
            }
        }
        return sb.toString()
    }

    /**
     * 安全提取 URL 中的特定 query parameter
     */
    private fun extractQueryParameter(
        url: String,
        key: String,
    ): String? {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            val eqIdx = pair.indexOf('=')
            val currentKey = if (eqIdx >= 0) pair.substring(0, eqIdx) else pair
            if (currentKey == key) {
                return if (eqIdx >= 0) pair.substring(eqIdx + 1) else ""
            }
        }
        return null
    }

    /**
     * 构建哔哩哔哩官方客户端搜索唤起目标及网页端搜索降级地址
     */
    fun buildBilibiliSearchTarget(keyword: String): StreamingAppTarget {
        val encoded = encodeQueryParameter(keyword.trim())
        val deepLink = if (encoded.isEmpty()) "bilibili://search" else "bilibili://search?keyword=$encoded"
        val webFallback = if (encoded.isEmpty()) "https://search.bilibili.com" else "https://search.bilibili.com/all?keyword=$encoded"
        return StreamingAppTarget(
            siteName = "bilibili",
            appName = "哔哩哔哩",
            packageNames = BILIBILI_PACKAGE_NAMES,
            deepLinkUri = deepLink,
            webFallbackUrl = webFallback,
            isSearch = true,
        )
    }

    /**
     * 构建蜜柑计划详情页或搜索页跳转地址（精准 ID 优先直达番剧页，无 ID 则降级搜索熟肉/BT）
     */
    fun buildMikanUrl(
        mikanId: String? = null,
        keyword: String? = null,
    ): String {
        val trimmedId = mikanId?.trim().orEmpty()
        if (trimmedId.isNotBlank()) {
            return if (trimmedId.startsWith("http")) trimmedId else "https://mikanani.me/Home/Bangumi/$trimmedId"
        }
        val query = keyword?.trim().orEmpty()
        val encoded = encodeQueryParameter(query)
        return "https://mikanani.me/Home/Search?searchstr=$encoded"
    }

    fun resolve(url: String): StreamingAppTarget? {
        if (url.isBlank()) return null
        return resolveBilibili(url)
            ?: resolveGamer(url)
            ?: resolveOtherPlatforms(url)
    }

    private fun resolveBilibili(url: String): StreamingAppTarget? {
        if (url.startsWith("bilibili://search")) {
            val keyword = extractQueryParameter(url, "keyword")
            val fallback =
                if (keyword.isNullOrBlank()) {
                    "https://search.bilibili.com"
                } else {
                    "https://search.bilibili.com/all?keyword=$keyword"
                }
            return StreamingAppTarget(
                siteName = "bilibili",
                appName = "哔哩哔哩",
                packageNames = BILIBILI_PACKAGE_NAMES,
                deepLinkUri = url,
                webFallbackUrl = fallback,
                isSearch = true,
            )
        }
        if (url.contains("search.bilibili.com") || url.contains("bilibili.com/search")) {
            val keyword = extractQueryParameter(url, "keyword")
            val deepLink =
                if (keyword.isNullOrBlank()) {
                    "bilibili://search"
                } else {
                    "bilibili://search?keyword=$keyword"
                }
            return StreamingAppTarget(
                siteName = "bilibili",
                appName = "哔哩哔哩",
                packageNames = BILIBILI_PACKAGE_NAMES,
                deepLinkUri = deepLink,
                webFallbackUrl = url,
                isSearch = true,
            )
        }
        BILI_SEASON_REGEX.find(url)?.let {
            val seasonId = it.groupValues[1]
            return StreamingAppTarget(
                siteName = "bilibili",
                appName = "哔哩哔哩",
                packageNames = BILIBILI_PACKAGE_NAMES,
                deepLinkUri = "bilibili://bangumi/season/$seasonId",
                webFallbackUrl = url,
            )
        }
        BILI_EP_REGEX.find(url)?.let {
            val epId = it.groupValues[1]
            return StreamingAppTarget(
                siteName = "bilibili",
                appName = "哔哩哔哩",
                packageNames = BILIBILI_PACKAGE_NAMES,
                deepLinkUri = "bilibili://bangumi/season/ep/$epId",
                webFallbackUrl = url,
            )
        }
        BILI_VIDEO_BV_REGEX.find(url)?.let {
            val bvid = it.groupValues[1]
            return StreamingAppTarget(
                siteName = "bilibili",
                appName = "哔哩哔哩",
                packageNames = BILIBILI_PACKAGE_NAMES,
                deepLinkUri = "bilibili://video/$bvid",
                webFallbackUrl = url,
            )
        }
        BILI_VIDEO_AV_REGEX.find(url)?.let {
            val avid = it.groupValues[1]
            return StreamingAppTarget(
                siteName = "bilibili",
                appName = "哔哩哔哩",
                packageNames = BILIBILI_PACKAGE_NAMES,
                deepLinkUri = "bilibili://video/av$avid",
                webFallbackUrl = url,
            )
        }
        if (url.startsWith("bilibili://")) {
            return StreamingAppTarget(
                siteName = "bilibili",
                appName = "哔哩哔哩",
                packageNames = BILIBILI_PACKAGE_NAMES,
                deepLinkUri = url,
                webFallbackUrl = "https://www.bilibili.com",
            )
        }
        if (BILI_GENERAL_REGEX.containsMatchIn(url)) {
            return StreamingAppTarget(
                siteName = "bilibili",
                appName = "哔哩哔哩",
                packageNames = BILIBILI_PACKAGE_NAMES,
                deepLinkUri = null,
                webFallbackUrl = url,
            )
        }
        return null
    }

    private fun resolveGamer(url: String): StreamingAppTarget? {
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
        return null
    }

    private fun resolveOtherPlatforms(url: String): StreamingAppTarget? {
        if (IQIYI_REGEX.containsMatchIn(url)) {
            return StreamingAppTarget(
                siteName = "iqiyi",
                appName = "爱奇艺",
                packageNames = listOf("com.qiyi.video"),
                deepLinkUri = null,
                webFallbackUrl = url,
            )
        }
        if (TENCENT_REGEX.containsMatchIn(url)) {
            return StreamingAppTarget(
                siteName = "qq",
                appName = "腾讯视频",
                packageNames = listOf("com.tencent.qqlive"),
                deepLinkUri = null,
                webFallbackUrl = url,
            )
        }
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
