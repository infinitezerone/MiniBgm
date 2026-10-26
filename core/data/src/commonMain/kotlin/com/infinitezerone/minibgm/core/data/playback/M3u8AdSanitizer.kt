package com.infinitezerone.minibgm.core.data.playback

/**
 * HLS 媒体播放列表（.m3u8）广告切片清洗器。
 *
 * 核心针对 MacCMS / TVBox 切片采集站常见的片头荷官/菠菜硬广切片，
 * 在内存中透明过滤掉广告段，为底层播放器（ExoPlayer）提供纯净的正片切片列表。
 *
 * 严格遵循 Fail-Open 准则：
 * 任何解析异常、未知格式或非切片列表均原样返回原始文本，绝不阻断媒体起播。
 */
object M3u8AdSanitizer {
    /** 判定为片头广告段落的最大累计时长阈值（秒） */
    const val DEFAULT_MAX_HEAD_AD_DURATION_SECONDS: Double = 18.0

    private val EXTINF_DURATION_REGEX = Regex("""#EXTINF:([0-9.]+)(?:,.*)?""")
    private val MEDIA_SEQUENCE_REGEX = Regex("""#EXT-X-MEDIA-SEQUENCE:(\d+)""")

    /**
     * 清洗 m3u8 播放列表文本，剥离片头广告切片段落。
     *
     * @param content 原始 m3u8 文本
     * @param customRules 自定义 TVBox 正则过滤规则列表（可选）
     * @param maxHeadAdDurationSec 片头广告最大时长阈值（秒）
     * @return 清洗后的纯净 m3u8 文本；若无广告或格式不匹配则安全返回原文本
     */
    fun sanitize(
        content: String,
        customRules: List<String> = emptyList(),
        maxHeadAdDurationSec: Double = DEFAULT_MAX_HEAD_AD_DURATION_SECONDS,
    ): String =
        runCatching {
            val trimmed = content.trim()
            if (!trimmed.startsWith("#EXTM3U")) return content
            // 主索引（Master Playlist）只包含不同清晰度的子流地址，本身无媒体切片，直接放行
            if (trimmed.contains("#EXT-X-STREAM-INF")) return content

            var processed = trimmed

            // 1. 若配置了 TVBox 自定义正则规则，优先执行规则替换
            if (customRules.isNotEmpty()) {
                processed = applyCustomRules(processed, customRules)
            }

            // 2. 启发式结构化广告切片剥离
            stripHeuristicAdSegments(processed, maxHeadAdDurationSec)
        }.getOrDefault(content)

    /**
     * 应用 TVBox 订阅中的自定义正则规则过滤广告
     */
    private fun applyCustomRules(
        content: String,
        customRules: List<String>,
    ): String {
        var current = content
        for (rawRule in customRules) {
            val rule = rawRule.trim()
            if (rule.isBlank()) continue
            runCatching {
                val regex = Regex(rule, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
                val replaced = regex.replace(current, "")
                // 守卫：如果正则错误地删光了整个播放列表或丢掉了 EXTM3U，放弃本次替换
                if (replaced.contains("#EXTM3U") && replaced.contains("#EXTINF")) {
                    current = replaced
                }
            }
        }
        return current
    }

    /**
     * 针对 MacCMS / TVBox 典型的首部 `#EXT-X-DISCONTINUITY` 切片广告段进行结构化剥离
     */
    private fun stripHeuristicAdSegments(
        content: String,
        maxHeadAdDurationSec: Double,
    ): String {
        val lines = content.lines()
        if (lines.isEmpty()) return content

        // 切分：提取顶部元数据行与媒体段落
        val headerLines = mutableListOf<String>()
        val segmentBlocks = mutableListOf<MutableList<String>>()
        var currentBlock: MutableList<String>? = null
        var isReadingHeader = true

        for (line in lines) {
            val trimmedLine = line.trim()
            if (isReadingHeader) {
                if (trimmedLine == "#EXT-X-DISCONTINUITY" || trimmedLine.startsWith("#EXTINF")) {
                    isReadingHeader = false
                    currentBlock = mutableListOf()
                    currentBlock.add(line)
                    segmentBlocks.add(currentBlock)
                } else {
                    headerLines.add(line)
                }
            } else {
                if (trimmedLine == "#EXT-X-DISCONTINUITY") {
                    currentBlock = mutableListOf(line)
                    segmentBlocks.add(currentBlock)
                } else {
                    currentBlock?.add(line)
                }
            }
        }

        // 如果没有段落切片或只有一个整体段落且无前置不连续标记，无需修改
        if (segmentBlocks.size < 2) return content

        val firstBlock = segmentBlocks.first()
        // 判定首个段落是否为被注入的独立广告切片：
        // 1. 首个段落必须显式以 #EXT-X-DISCONTINUITY 开头（切片站标记外接片段）
        val hasDiscontinuityLead = firstBlock.any { it.trim() == "#EXT-X-DISCONTINUITY" }
        if (!hasDiscontinuityLead) return content

        // 2. 统计首个段落内的切片总时长与切片个数
        var firstBlockDuration = 0.0
        var firstBlockSegmentCount = 0
        for (line in firstBlock) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                val match = EXTINF_DURATION_REGEX.matchEntire(trimmed)
                val duration = match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                firstBlockDuration += duration
                firstBlockSegmentCount++
            }
        }

        // 如果首段切片为空，或者时长超过了设定的广告阈值（例如超过 18 秒通常就是正片内容），不进行误杀
        if (firstBlockSegmentCount == 0 || firstBlockDuration > maxHeadAdDurationSec) {
            return content
        }

        // 3. 剥离首个段落（确认存在后续至少一个正片段落）
        val remainingBlocks = segmentBlocks.drop(1)
        val hasRemainingPlayableSegments =
            remainingBlocks.any { block ->
                block.any { it.trim().startsWith("#EXTINF:") }
            }
        if (!hasRemainingPlayableSegments) return content

        // 4. 重建播放列表并修正媒体序列号（EXT-X-MEDIA-SEQUENCE）
        val updatedHeaderLines =
            headerLines.map { line ->
                val match = MEDIA_SEQUENCE_REGEX.matchEntire(line.trim())
                if (match != null) {
                    val currentSeq = match.groupValues[1].toLongOrNull() ?: 0L
                    "#EXT-X-MEDIA-SEQUENCE:${currentSeq + firstBlockSegmentCount}"
                } else {
                    line
                }
            }

        val resultLines = mutableListOf<String>()
        resultLines.addAll(updatedHeaderLines)

        // 写入剩余的段落，若后续首个段落以 DISCONTINUITY 开头，此时作为正片起点已无需该标记，平滑移除
        remainingBlocks.forEachIndexed { index, block ->
            val cleanedBlock =
                if (index == 0 && block.firstOrNull()?.trim() == "#EXT-X-DISCONTINUITY") {
                    block.drop(1)
                } else {
                    block
                }
            resultLines.addAll(cleanedBlock)
        }

        return resultLines.joinToString("\n")
    }
}
