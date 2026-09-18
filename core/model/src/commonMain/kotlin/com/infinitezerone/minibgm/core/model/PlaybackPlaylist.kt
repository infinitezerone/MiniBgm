package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/** 播放列表条目类型：DIRECT = 可直连播放的媒体地址；PAGE = 需外部打开的页面链接 */
@Serializable
enum class PlaylistEntryKind {
    DIRECT,
    PAGE,
}

/**
 * 用户自备播放列表中的一条分集条目。
 *
 * [label] 为集数标识（如 "12"、"OVA2"），仅做展示与人工对齐，不做算术推算；
 * [kind] 显式声明条目形态，取代扩展名/关键词猜测。
 */
@Serializable
data class PlaylistEntry(
    val label: String,
    val url: String,
    val kind: PlaylistEntryKind = PlaylistEntryKind.DIRECT,
    val title: String = "",
    val siteName: String = "",
    val headers: Map<String, String> = emptyMap(),
)

/**
 * 一份绑定 Bangumi 条目的自备播放列表（[bgmSubjectId] 为 0 表示未绑定条目）。
 */
@Serializable
data class PlaybackPlaylist(
    val id: String,
    val name: String,
    val bgmSubjectId: Long = 0L,
    val entries: List<PlaylistEntry> = emptyList(),
)

/** 导入文件/粘贴文本的信封格式 */
@Serializable
data class PlaybackPlaylistDocument(
    val schemaVersion: Int = PlaybackPlaylistSchema.CURRENT_SCHEMA_VERSION,
    val playlists: List<PlaybackPlaylist> = emptyList(),
)

/** 导入校验产物：可入库的合法列表 + 逐条问题说明 */
data class PlaylistDocumentValidation(
    val validPlaylists: List<PlaybackPlaylist>,
    val issues: List<String>,
)

/** 一次导入的结果摘要 */
data class PlaylistImportSummary(
    val addedCount: Int,
    val replacedCount: Int,
    val issues: List<String>,
)

/**
 * 播放列表 JSON 的模式版本、规模上限与导入前校验（纯函数，供 :core:data 与测试复用）。
 */
object PlaybackPlaylistSchema {
    const val CURRENT_SCHEMA_VERSION = 1
    const val MAX_PLAYLISTS = 100
    const val MAX_ENTRIES_PER_PLAYLIST = 500
    const val MAX_HEADERS_PER_ENTRY = 20
    private const val MAX_URL_LENGTH = 4096
    private const val MAX_LABEL_LENGTH = 100

    /** App 内展示的导入模板示例 */
    const val TEMPLATE_EXAMPLE_JSON =
        """
        {
          "schemaVersion": 1,
          "playlists": [
            {
              "id": "my-source-01",
              "name": "我的片源 · 样例番剧",
              "bgmSubjectId": 123456,
              "entries": [
                {
                  "label": "01",
                  "url": "https://cdn.example.com/anime/ep01.m3u8",
                  "kind": "DIRECT",
                  "siteName": "example",
                  "headers": { "Referer": "https://example.com/" }
                },
                {
                  "label": "02",
                  "url": "https://example.com/watch/ep02",
                  "kind": "PAGE",
                  "title": "第 2 话页面"
                }
              ]
            }
          ]
        }
        """

    /**
     * 校验导入文档：整体版本非法则全部拒绝；单条问题只丢弃该条目/列表并记录原因，
     * 允许部分成功导入。
     */
    fun validate(document: PlaybackPlaylistDocument): PlaylistDocumentValidation {
        val issues = mutableListOf<String>()
        if (document.schemaVersion !in 1..CURRENT_SCHEMA_VERSION) {
            return PlaylistDocumentValidation(
                validPlaylists = emptyList(),
                issues = listOf("不支持的 schemaVersion：${document.schemaVersion}（当前支持 1..$CURRENT_SCHEMA_VERSION）"),
            )
        }
        if (document.playlists.isEmpty()) {
            return PlaylistDocumentValidation(emptyList(), listOf("文档中没有播放列表"))
        }
        if (document.playlists.size > MAX_PLAYLISTS) {
            issues += "播放列表超过 $MAX_PLAYLISTS 份上限，仅校验前 $MAX_PLAYLISTS 份"
        }

        val seenIds = mutableSetOf<String>()
        val valid =
            document.playlists.take(MAX_PLAYLISTS).mapIndexedNotNull { index, playlist ->
                val name = playlist.name.ifBlank { "第${index + 1}份" }
                if (playlist.id.isBlank()) {
                    issues += "《$name》缺少 id，已跳过"
                    return@mapIndexedNotNull null
                }
                if (!seenIds.add(playlist.id)) {
                    issues += "《$name》id「${playlist.id}」在文档内重复，已跳过"
                    return@mapIndexedNotNull null
                }
                if (playlist.entries.isEmpty()) {
                    issues += "《$name》没有任何分集条目，已跳过"
                    return@mapIndexedNotNull null
                }
                if (playlist.bgmSubjectId < 0) {
                    issues += "《$name》的 bgmSubjectId 非法，已跳过"
                    return@mapIndexedNotNull null
                }

                val entries =
                    playlist.entries.take(MAX_ENTRIES_PER_PLAYLIST).mapIndexedNotNull { entryIndex, entry ->
                        val label = entry.label.trim()
                        val where = "《$name》第${entryIndex + 1}条"
                        when {
                            label.isBlank() -> {
                                issues += "$where 缺少分集标识 label，已跳过"
                                null
                            }

                            label.length > MAX_LABEL_LENGTH -> {
                                issues += "$where（$label）标识超过 $MAX_LABEL_LENGTH 字符，已跳过"
                                null
                            }

                            !isHttpUrl(entry.url) -> {
                                issues += "$where（$label）地址不是 http/https，已跳过"
                                null
                            }

                            else -> {
                                val cleanHeaders =
                                    entry.headers
                                        .filter { (key, value) ->
                                            key.isNotBlank() &&
                                                !key.contains(':') &&
                                                value.isNotBlank() &&
                                                !value.contains('\n')
                                        }.entries
                                        .take(MAX_HEADERS_PER_ENTRY)
                                        .associate { it.key to it.value }
                                if (cleanHeaders.size < entry.headers.size) {
                                    issues += "$where（$label）丢弃了 ${entry.headers.size - cleanHeaders.size} 个非法请求头"
                                }
                                entry.copy(
                                    label = label,
                                    url = entry.url.trim(),
                                    headers = cleanHeaders,
                                )
                            }
                        }
                    }
                if (playlist.entries.size > MAX_ENTRIES_PER_PLAYLIST) {
                    issues += "《$name》条目超过 $MAX_ENTRIES_PER_PLAYLIST 集上限，仅保留前 $MAX_ENTRIES_PER_PLAYLIST 条"
                }
                if (entries.isEmpty()) {
                    issues += "《$name》没有合法条目，已跳过"
                    return@mapIndexedNotNull null
                }
                playlist.copy(name = playlist.name.ifBlank { name }, entries = entries)
            }

        if (valid.isEmpty() && issues.isEmpty()) {
            issues += "没有可导入的合法播放列表"
        }
        return PlaylistDocumentValidation(valid, issues)
    }

    private fun isHttpUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.length <= MAX_URL_LENGTH &&
            (
                trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true)
            )
    }
}

/** 按 Bangumi 条目 ID 取绑定的播放列表（不含未绑定列表） */
fun List<PlaybackPlaylist>.forSubject(subjectId: Long): List<PlaybackPlaylist> = filter { it.bgmSubjectId == subjectId && subjectId != 0L }

/** 一条分集条目连同它所属的片单（供 UI 按片单分组展示） */
data class PlaylistEntryMatch(
    val playlist: PlaybackPlaylist,
    val entry: PlaylistEntry,
    /** false 表示该条目并非按话数命中，而是无话数片单的回退展示 */
    val matched: Boolean,
)

/**
 * 分集匹配：在绑定到 [subjectId] 的片单里挑出话数落在 [epNumber] 上的条目。
 *
 * 只做 label 上的数字边界比对（"12" 命中 "EP12"/"第 12 话"，不命中 "120"），
 * 不做任何剧集规律推算。若片单条目全部不带话数（单话片单常见形态），
 * 回退为该片单的前 [DEFAULT_FALLBACK_LIMIT] 条，交由用户自行取舍。
 */
fun List<PlaybackPlaylist>.matchesForEpisode(
    subjectId: Long,
    epNumber: Float,
    fallbackLimit: Int = DEFAULT_FALLBACK_LIMIT,
): List<PlaylistEntryMatch> {
    val tokens = episodeTokens(epNumber)
    return forSubject(subjectId).flatMap { playlist ->
        val hit =
            tokens
                .asSequence()
                .mapNotNull { token ->
                    playlist.entries.filter { entry -> containsToken(entry.label, token) }.takeIf { it.isNotEmpty() }
                }.firstOrNull()
        if (hit != null) {
            hit.map { PlaylistEntryMatch(playlist, it, matched = true) }
        } else {
            playlist.entries.take(fallbackLimit).map { PlaylistEntryMatch(playlist, it, matched = false) }
        }
    }
}

/** 无话数命中时每个片单默认回退展示的条目数 */
const val DEFAULT_FALLBACK_LIMIT = 12

/**
 * 话数在 label 中可能出现的书写形式，按精确度排序：
 * 小数原样 > 整数 > 补零（补零只作兜底，避免 "EP01" 抢占 "第 1 话"）。
 */
internal fun episodeTokens(epNumber: Float): List<String> {
    val asInt = epNumber.toInt()
    if (asInt <= 0) return emptyList()
    val raw = epNumber.toString().trimEnd('0').trimEnd('.')
    val tokens = mutableListOf<String>()
    if (raw != asInt.toString() && raw.isNotBlank()) tokens += raw
    tokens += asInt.toString()
    if (asInt < 10) tokens += "0$asInt"
    return tokens.distinct()
}

/** 数字边界包含判定：token 前后不得再是数字或小数点 */
internal fun containsToken(
    label: String,
    token: String,
): Boolean {
    if (token.isEmpty()) return false
    val haystack = label.lowercase()
    val needle = token.lowercase()
    var from = 0
    while (true) {
        val at = haystack.indexOf(needle, from)
        if (at < 0) return false
        val beforeOk = at == 0 || !haystack[at - 1].isDigit()
        val afterIndex = at + needle.length
        val afterOk =
            afterIndex >= haystack.length ||
                (!haystack[afterIndex].isDigit() && haystack[afterIndex] != '.')
        if (beforeOk && afterOk) return true
        from = at + needle.length
    }
}
