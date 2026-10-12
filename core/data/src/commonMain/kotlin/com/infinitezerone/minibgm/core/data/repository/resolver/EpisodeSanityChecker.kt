package com.infinitezerone.minibgm.core.data.repository.resolver

/**
 * 分集与季数健全性校验器（防错番、防串季、分集拓扑对齐）。
 *
 * 核心职责：
 * 1. 季数提取与互斥（Season Strict Guard）：若目标是第二季，而候选条目是第一季或第三季，一票否决；
 * 2. 衍生/剧场版互斥（Edition Guard）：TV 动画排除包含剧场版、电影、OVA、SP 且非目标标题的情况；
 * 3. 分集拓扑映射（Topology Normalization）：将 13~24 编号的第二季连续编号映射回第 1~12 话；
 * 4. 视频时长合理性断言（Duration Sanity Check）：基于 TV / 泡面番 / 剧场版的时长范围排查 PV / 错片。
 */
object EpisodeSanityChecker {
    private val SEASON_CHINESE_MAP =
        mapOf(
            "一" to 1,
            "二" to 2,
            "三" to 3,
            "四" to 4,
            "五" to 5,
            "六" to 6,
            "七" to 7,
            "八" to 8,
            "九" to 9,
            "十" to 10,
        )

    private val MOVIE_KEYWORDS = listOf("剧场版", "电影", "movie", "oad", "ova", "总集篇", "特别篇", "sp")

    /**
     * 从标题中提取季数（1 起），若未能识别季数特征则返回 null（默认视为常规/首季或未指定）。
     */
    fun parseSeasonNumber(title: String): Int? {
        if (title.isBlank()) return null
        val lower = title.lowercase()

        // 匹配 "第X季" 或 "第X期" (中文数字或阿拉伯数字)
        val cnMatch = Regex("""第([一二三四五六七八九十0-9]+)[季期]""").find(lower)
        if (cnMatch != null) {
            val numStr = cnMatch.groupValues[1]
            numStr.toIntOrNull()?.let { return it }
            SEASON_CHINESE_MAP[numStr]?.let { return it }
        }

        // 匹配 "season X" 或 "sX"
        val sMatch = Regex("""\b(?:season|s)\s*([0-9]{1,2})\b""").find(lower)
        if (sMatch != null) {
            sMatch.groupValues[1].toIntOrNull()?.let { return it }
        }

        // 匹配 "Part X" 或 "partX"
        val partMatch = Regex("""\bpart\s*([0-9]{1,2})\b""").find(lower)
        if (partMatch != null) {
            partMatch.groupValues[1].toIntOrNull()?.let { return it }
        }

        // 匹配尾部的阿拉伯数字，如 "无职转生 2" 或 "电磁炮 3"
        val trailingNumMatch = Regex("""[\s_\-]([2-9])(?:\s|$)""").find(lower)
        if (trailingNumMatch != null) {
            trailingNumMatch.groupValues[1].toIntOrNull()?.let { return it }
        }

        return null
    }

    /**
     * 判断目标条目标题与源站候选条目名 [candidateVodName] 的季数是否兼容。
     *
     * 规则：
     * - 如果两者都明确提取出了季数，且两者不相等，则绝对不兼容（返回 false）；
     * - 如果目标明确指定了季数 > 1（如第 2 季），而候选明确标了第 1 季，或完全是纯标题且目标有后续季，严格互斥；
     * - 特别篇/剧场版互斥：若目标不是剧场版，而候选明确带有剧场版/OVA/SP 关键字，返回 false。
     */
    fun isSeasonAndEditionCompatible(
        targetTitle: String,
        candidateVodName: String,
    ): Boolean {
        if (targetTitle.isBlank() || candidateVodName.isBlank()) return true

        val targetExplicitSeason = parseSeasonNumber(targetTitle)
        val candidateExplicitSeason = parseSeasonNumber(candidateVodName)

        // 1. 季数严格互斥：若双方都明确声明了季数且不相等，一票否决
        if (targetExplicitSeason != null && candidateExplicitSeason != null && targetExplicitSeason != candidateExplicitSeason) {
            return false
        }
        // 若目标明确为第 2 季及以上，而候选明确声明了第一季/第1期
        if (targetExplicitSeason != null &&
            targetExplicitSeason > 1 &&
            (candidateVodName.contains("第一季") || candidateVodName.contains("第1季") || candidateVodName.contains("第1期"))
        ) {
            return false
        }
        // 若目标明确声明了第一季/第1期，而候选明确声明了后续季
        if (targetExplicitSeason == 1 && candidateExplicitSeason != null && candidateExplicitSeason > 1) {
            return false
        }

        // 2. 剧场版 / OVA / 特别篇互斥
        val targetLower = targetTitle.lowercase()
        val candidateLower = candidateVodName.lowercase()
        val targetIsMovie = MOVIE_KEYWORDS.any { targetLower.contains(it) }
        val candidateIsMovie = MOVIE_KEYWORDS.any { candidateLower.contains(it) }

        if (!targetIsMovie && candidateIsMovie) {
            return false
        }

        return true
    }

    /**
     * 分集拓扑纠偏。
     *
     * 针对第二季/分割放送常见的两种编号：
     * - 模式 A：从 1 重新起编（如 1~12）；
     * - 模式 B：连续编号（如 13~24）。
     *
     * 当客户端请求的是 [requestedEp]（例如第 1 话）：
     * - 若可用的集数列表中存在精确匹配 [requestedEp]，直接返回该集；
     * - 若不存在 [requestedEp]，但所有可用集号整体发生固定偏移（例如全集最小集号为 13，且 13 对应请求的第 1 话），
     *   自动将第 1 话换算为 13 话。
     *
     * @param requestedEp 请求的分集号（如 1.0f）
     * @param availableSorts 源站此条目所有可用分集号（去重且升序）
     * @return 映射后的可用集号，若无法映射则返回 null
     */
    fun normalizeEpisodeIndex(
        requestedEp: Float,
        availableSorts: List<Float>,
    ): Float? {
        if (availableSorts.isEmpty()) return null
        val sorted = availableSorts.distinct().sorted()

        // 1. 存在精确匹配
        if (sorted.contains(requestedEp)) {
            return requestedEp
        }

        // 2. 拓扑连续偏移纠偏
        // 常见场景：求第 1 话，候选集号为 [13, 14, ..., 24] 或 [25, 26, ...]
        if (requestedEp >= 1f && sorted.first() > 1f) {
            val offset = sorted.first() - 1f
            val mappedEp = requestedEp + offset
            if (sorted.contains(mappedEp)) {
                return mappedEp
            }
        }

        return null
    }

    /**
     * 视频时长合理性断言（秒）：
     * - 常规 TV 动画：一般在 18 ~ 30 分钟（1080s ~ 1800s），含前后加长初回（如芙莉莲 1 话可能 2~4 集连播，约 5400s）；
     * - 泡面番：3 ~ 15 分钟（180s ~ 900s）；
     * - 剧场版：通常 > 4000s（约 65 分钟以上）。
     *
     * 当时长极短（< 150 秒，即 2.5 分钟以内），大概率为 PV、预告片或广告切片，不属于正片。
     */
    fun isDurationPlausibleForTvEpisode(durationSeconds: Long): Boolean {
        if (durationSeconds <= 0L) return true // 未知时长时不拦截
        // 小于 2.5 分钟极可能是 PV
        if (durationSeconds < 150L) return false
        // 大于 100 分钟且没有加长标注的 TV 分集极可能是整部剧场版
        if (durationSeconds > 6000L) return false
        return true
    }
}
