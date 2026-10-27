package com.infinitezerone.minibgm.core.data.playback

import com.infinitezerone.minibgm.core.common.bgmLogger

/**
 * HLS 媒体播放列表（.m3u8）广告切片清洗器。
 *
 * 核心针对切片采集站常见的片头、片中（中插）、片尾硬广切片，
 * 在内存中透明过滤掉广告段，为底层播放器（ExoPlayer）提供纯净的正片切片列表。
 *
 * 严格遵循 Fail-Open 准则：
 * 任何解析异常、未知格式或非切片列表均原样返回原始文本，绝不阻断媒体起播。
 */
object M3u8AdSanitizer {
    /** 判定为广告段落的最大累计时长阈值（秒）。通常切片站广告插片为 5~25 秒 */
    const val DEFAULT_MAX_AD_DURATION_SECONDS: Double = 30.0

    private val logger = bgmLogger("Bgm/AdSanitizer")

    private val EXTINF_DURATION_REGEX = Regex("""#EXTINF:([0-9.]+)(?:,.*)?""")
    private val MEDIA_SEQUENCE_REGEX = Regex("""#EXT-X-MEDIA-SEQUENCE:(\d+)""")

    /**
     * 清洗 m3u8 播放列表文本，剥离片头、片中及片尾广告切片段落。
     *
     * @param content 原始 m3u8 文本
     * @param customRules 自定义 TVBox 正则过滤规则列表（可选）
     * @param maxAdDurationSec 单段广告最大时长阈值（秒）
     * @return 清洗后的纯净 m3u8 文本；若无广告或格式不匹配则安全返回原文本
     */
    fun sanitize(
        content: String,
        customRules: List<String> = emptyList(),
        maxAdDurationSec: Double = DEFAULT_MAX_AD_DURATION_SECONDS,
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

            // 2. 启发式结构化广告切片剥离（全面覆盖片头、片中、片尾）
            stripHeuristicAdSegments(processed, maxAdDurationSec)
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

    private data class SegmentBlock(
        val lines: MutableList<String> = mutableListOf(),
        var duration: Double = 0.0,
        var segmentCount: Int = 0,
        var hasDiscontinuityPrefix: Boolean = false,
    )

    /**
     * 针对 MacCMS / TVBox 切片站典型的硬广切片（片头、片中、片尾）进行结构化剥离
     */
    private fun stripHeuristicAdSegments(
        content: String,
        maxAdDurationSec: Double,
    ): String {
        val lines = content.lines()
        if (lines.isEmpty()) return content

        val headerLines = mutableListOf<String>()
        val blocks = mutableListOf<SegmentBlock>()
        var currentBlock: SegmentBlock? = null
        var isReadingHeader = true
        var endListLine: String? = null

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine == "#EXT-X-ENDLIST") {
                endListLine = line
                continue
            }

            if (isReadingHeader) {
                if (trimmedLine == "#EXT-X-DISCONTINUITY" || trimmedLine.startsWith("#EXTINF")) {
                    isReadingHeader = false
                    currentBlock = SegmentBlock(hasDiscontinuityPrefix = (trimmedLine == "#EXT-X-DISCONTINUITY"))
                    blocks.add(currentBlock)
                    if (trimmedLine != "#EXT-X-DISCONTINUITY") {
                        currentBlock.lines.add(line)
                        inspectSegmentLine(trimmedLine, currentBlock)
                    }
                } else {
                    headerLines.add(line)
                }
            } else {
                if (trimmedLine == "#EXT-X-DISCONTINUITY") {
                    currentBlock = SegmentBlock(hasDiscontinuityPrefix = true)
                    blocks.add(currentBlock)
                } else {
                    currentBlock?.let { b ->
                        b.lines.add(line)
                        inspectSegmentLine(trimmedLine, b)
                    }
                }
            }
        }

        // 仅保留有效切片段落
        val validBlocks = blocks.filter { it.segmentCount > 0 }
        if (validBlocks.size < 2) return content

        // 识别属于广告的切片段落
        val adCandidateIndices = mutableSetOf<Int>()
        for (i in validBlocks.indices) {
            val block = validBlocks[i]
            if (block.duration <= maxAdDurationSec) {
                val isHeadAd = (i == 0) && (block.hasDiscontinuityPrefix || validBlocks.getOrNull(1)?.hasDiscontinuityPrefix == true)
                val isTailAd = (i == validBlocks.lastIndex) && block.hasDiscontinuityPrefix
                val isMidAd = (i > 0 && i < validBlocks.lastIndex) && block.hasDiscontinuityPrefix

                if (isHeadAd || isTailAd || isMidAd) {
                    adCandidateIndices.add(i)
                }
            }
        }

        if (adCandidateIndices.isEmpty()) return content

        val keptBlocks = validBlocks.filterIndexed { index, _ -> index !in adCandidateIndices }
        // 守卫：必须保留至少一段有实质内容的正片（总时长需超过广告阈值），防止在超短预览片上发生误判
        if (keptBlocks.isEmpty() || keptBlocks.sumOf { it.duration } <= maxAdDurationSec) {
            return content
        }

        // 统计剔除数据用于日志与序号更新
        var headRemovedSegments = 0
        if (0 in adCandidateIndices) {
            headRemovedSegments = validBlocks[0].segmentCount
        }

        val updatedHeaderLines =
            if (headRemovedSegments > 0) {
                headerLines.map { line ->
                    val match = MEDIA_SEQUENCE_REGEX.matchEntire(line.trim())
                    if (match != null) {
                        val currentSeq = match.groupValues[1].toLongOrNull() ?: 0L
                        "#EXT-X-MEDIA-SEQUENCE:${currentSeq + headRemovedSegments}"
                    } else {
                        line
                    }
                }
            } else {
                headerLines
            }

        val resultLines = mutableListOf<String>()
        resultLines.addAll(updatedHeaderLines)

        for (i in keptBlocks.indices) {
            val block = keptBlocks[i]
            // 正片分块之间保留一个 #EXT-X-DISCONTINUITY，以保证播放器在跨过广告切片后正确重置解码器与时间戳
            if (i > 0) {
                resultLines.add("#EXT-X-DISCONTINUITY")
            }
            resultLines.addAll(block.lines)
        }

        if (endListLine != null) {
            resultLines.add(endListLine)
        }

        val strippedDuration = validBlocks.filterIndexed { idx, _ -> idx in adCandidateIndices }.sumOf { it.duration }
        val strippedCount = validBlocks.filterIndexed { idx, _ -> idx in adCandidateIndices }.sumOf { it.segmentCount }
        logger.i {
            "剥离广告成功: 剔除 ${adCandidateIndices.size} 处广告段落, 共 $strippedCount 个切片, 累计时长 %.2fs".format(strippedDuration)
        }

        return resultLines.joinToString("\n")
    }

    private fun inspectSegmentLine(trimmed: String, block: SegmentBlock) {
        if (trimmed.startsWith("#EXTINF:")) {
            val match = EXTINF_DURATION_REGEX.matchEntire(trimmed)
            val dur = match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            block.duration += dur
            block.segmentCount++
        }
    }
}
