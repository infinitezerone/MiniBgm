package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.common.TimeUtils
import com.infinitezerone.minibgm.core.common.toBgmCdnUrl
import com.infinitezerone.minibgm.core.common.unescapeHtmlEntities
import com.infinitezerone.minibgm.core.database.entity.AirScheduleEntity
import com.infinitezerone.minibgm.core.database.entity.AniListBgmMappingEntity
import com.infinitezerone.minibgm.core.model.AirEventKind
import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.Rating
import com.infinitezerone.minibgm.core.model.SiteLink
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectImages
import com.infinitezerone.minibgm.core.model.Tag
import com.infinitezerone.minibgm.core.network.AniListWeeklyScheduleItem
import com.infinitezerone.minibgm.core.network.ScheduleSnapshotItemDto
import com.infinitezerone.minibgm.core.network.SeasonSnapshotItemDto
import kotlinx.serialization.json.Json

/**
 * 排期相关的实体↔模型映射与快照条目转换（自 [ScheduleRepositoryImpl] 提升为顶层，
 * 均为不读类状态的纯映射逻辑）。
 *
 * 时间常量原为 ScheduleRepository.kt 的文件级私有常量，随纯辅助函数提升为 internal——
 * private companion 对外部顶层声明（如 isStalePrunableSchedule）不可见，类内与同包引用照常解析。
 */
internal const val HOUR_MILLIS = 60L * 60 * 1000
internal const val DAY_MILLIS = 24L * HOUR_MILLIS
internal const val WEEK_MILLIS = 7L * DAY_MILLIS

internal fun mergeSites(
    existing: String,
    incoming: String,
): String = if (incoming.isNotBlank() && incoming != "[]") incoming else existing

/** 快照条目的首播日：优先 `airDate`；缺失时用 `startYear/startMonth` 兜一个当月 1 日 */
internal fun snapshotAirDateOf(sItem: ScheduleSnapshotItemDto?): String {
    val explicit = sItem?.airDate?.ifBlank { null }
    if (explicit != null) return explicit
    val year = sItem?.startYear ?: 0
    val month = sItem?.startMonth ?: 0
    if (year <= 0 || month !in 1..12) return ""
    return "$year-${month.toString().padStart(2, '0')}-01"
}

internal fun resolveSiteLink(
    site: String,
    id: String,
    customUrl: String = "",
): SiteLink? {
    val siteKey = site.lowercase()
    val resolver = SITE_RESOLVERS[siteKey] ?: return null
    val url = customUrl.ifBlank { resolver.second(id) }
    if (url.isBlank()) return null
    return SiteLink(
        siteName = siteKey,
        displayName = resolver.first,
        playUrl = url,
    )
}

private fun buildBilibiliUrl(id: String): String =
    when {
        id.startsWith("http") -> id
        id.startsWith("md") -> "https://www.bilibili.com/bangumi/media/$id"
        id.startsWith("ss") -> "https://www.bilibili.com/bangumi/play/$id"
        id.startsWith("ep") -> "https://www.bilibili.com/bangumi/play/$id"
        else -> "https://www.bilibili.com/bangumi/media/md$id"
    }

private val SITE_RESOLVERS: Map<String, Pair<String, (String) -> String>> =
    mapOf(
        "bilibili" to ("哔哩哔哩" to ::buildBilibiliUrl),
        "gamer" to ("巴哈姆特" to { "https://ani.gamer.com.tw/animeVideo.php?sn=$it" }),
        "gamer_hk" to ("巴哈姆特" to { "https://ani.gamer.com.tw/animeVideo.php?sn=$it" }),
        "iqiyi" to ("爱奇艺" to { "https://www.iqiyi.com/v_$it.html" }),
        "qq" to ("腾讯视频" to { "https://v.qq.com/x/cover/$it.html" }),
        "youku" to ("优酷" to { "https://v.youku.com/v_show/id_$it.html" }),
        "netflix" to ("Netflix" to { "https://www.netflix.com/title/$it" }),
        "danime" to ("d动画" to { "https://animestore.docomo.ne.jp/animestore/ci_pc?workId=$it" }),
        "abema" to ("ABEMA" to { "https://abema.tv/channels/$it" }),
        "unext" to ("U-NEXT" to { "https://video.unext.jp/title/$it" }),
        "prime" to ("Prime Video" to { "https://www.amazon.co.jp/dp/$it" }),
        "disneyplus" to ("Disney+" to { "https://www.disneyplus.com/series/$it" }),
        "crunchyroll" to ("Crunchyroll" to { "https://www.crunchyroll.com/series/$it" }),
        "muse_tw" to ("木棉花" to { "https://www.youtube.com/playlist?list=$it" }),
        "muse_hk" to ("木棉花" to { "https://www.youtube.com/playlist?list=$it" }),
        "ani_one" to ("羚邦" to { "https://www.youtube.com/playlist?list=$it" }),
        "ani_one_asia" to ("羚邦" to { "https://www.youtube.com/playlist?list=$it" }),
        "nicovideo" to ("NicoNico" to { "https://ch.nicovideo.jp/$it" }),
        "mikan" to ("蜜柑计划" to { "https://mikanani.me/Home/Bangumi/$it" }),
    )

internal fun buildBroadcastRule(
    isAdult: Boolean,
    format: String,
): String {
    val parts = mutableListOf<String>()
    if (format.isNotBlank()) parts.add("format=$format")
    if (isAdult) parts.add("adult=true")
    return parts.joinToString(";")
}

internal fun parseIsAdult(rule: String): Boolean = rule.contains("adult=true") || rule.contains("ADULT")

internal fun parseFormat(rule: String): String =
    rule
        .split(";")
        .firstOrNull { it.startsWith("format=") }
        ?.substringAfter("format=")
        .orEmpty()

internal fun AniListBgmMappingEntity.toAirScheduleEntity(
    item: AniListWeeklyScheduleItem,
    nowMillis: Long,
): AirScheduleEntity {
    val airMillis = item.airAtEpochSeconds * 1000
    val isoUtc = TimeUtils.isoUtcFromEpochMillis(airMillis)
    val cstTime = TimeUtils.formatToCstTime(isoUtc)
    val jstTime = TimeUtils.formatToJstTime(isoUtc)
    val kind = if (airMillis <= nowMillis) AirEventKind.ACTUAL else AirEventKind.SCHEDULED
    return AirScheduleEntity(
        bgmId = bgmId,
        title = title,
        titleCn = titleCn,
        coverUrl = item.coverUrl.orEmpty(),
        ratingScore = 0.0,
        airDate = beginIso.substringBefore("T"),
        beginAtUtc = beginIso.ifBlank { isoUtc },
        sortMinutes = TimeUtils.parseTimeToMinutes(cstTime),
        weekday = TimeUtils.cstWeekdayOfEpoch(airMillis),
        timeCst = cstTime,
        timeJst = jstTime,
        sitesJson = sitesJson,
        anilistId = anilistId,
        broadcastRule = buildBroadcastRule(item.isAdult, item.format),
        source = AirScheduleEntity.SOURCE_BGM_DATA,
        nextEpisode = item.episode,
        nextEpisodeAtUtc = isoUtc,
        nextEpisodeKind = kind,
    )
}

/** 映射不到 bgmId 时的占位条目：只进时刻表展示，bgmId 用 `-anilistId` 作哨兵值。 */
internal fun AirScheduleEntity.toAniListBgmMapping(
    anilistId: Long,
    nowMillis: Long,
): AniListBgmMappingEntity =
    AniListBgmMappingEntity(
        anilistId = anilistId,
        bgmId = bgmId,
        sitesJson = sitesJson,
        title = title,
        titleCn = titleCn,
        beginIso = beginUtc,
        endIso = "",
        monthKey = "",
        updatedAt = nowMillis,
    )

internal fun AirScheduleEntity.toModel(json: Json): AirSchedule {
    val links: List<SiteLink> =
        try {
            json.decodeFromString(sitesJson)
        } catch (_: Exception) {
            emptyList()
        }

    val calculatedEp = nextEpisode.takeIf { it > 0 } ?: 0

    return AirSchedule(
        bgmId = bgmId,
        title = title.unescapeHtmlEntities(),
        titleCn = titleCn.unescapeHtmlEntities(),
        coverUrl = coverUrl.toBgmCdnUrl(),
        ratingScore = ratingScore,
        airDate = airDate,
        beginAtUtc = beginAtUtc,
        beginUtc = beginUtc,
        weekday = weekday,
        timeCst = timeCst,
        timeJst = timeJst,
        siteLinks = links,
        nextEpisodeNumber = calculatedEp,
        nextEpisodeAtUtc = nextEpisodeAtUtc,
        nextEpisodeKind = nextEpisodeKind,
        isUnmapped = bgmId <= 0,
        isAdult = parseIsAdult(broadcastRule),
        format = parseFormat(broadcastRule),
    )
}

internal fun extractSubjectTags(
    genres: List<String>,
    tags: List<String>,
    isAdult: Boolean,
): List<Tag> {
    val rawTags = (genres + tags).distinct().toMutableList()
    if (isAdult && rawTags.none { it.equals("Hentai", ignoreCase = true) }) {
        rawTags.add("Hentai")
    }
    return rawTags.map { Tag(name = it, count = 1) }
}

internal fun toSubjectImages(coverUrl: String?): SubjectImages? {
    if (coverUrl.isNullOrBlank()) return null
    return SubjectImages(
        large = coverUrl,
        common = coverUrl,
        medium = coverUrl,
        small = coverUrl,
        grid = coverUrl,
    )
}

internal fun SeasonSnapshotItemDto.toSubject(): Subject {
    val effectiveFormat = format.ifBlank { "TV" }
    return Subject(
        id = bgmId ?: -anilistId,
        type = 2,
        name = title,
        nameCn = titleCn.orEmpty(),
        images = toSubjectImages(coverUrl),
        rating = if (ratingScore > 0.0) Rating(score = ratingScore) else null,
        airDate = airDate.orEmpty(),
        date = airDate.orEmpty(),
        eps = episodes,
        tags = extractSubjectTags(genres, tags, isAdult),
        metaTags = listOf(effectiveFormat),
        platform = effectiveFormat,
    )
}
