package com.infinitezerone.minibgm.core.data.repository.resolver.spider

import com.dokar.quickjs.QuickJs
import com.infinitezerone.minibgm.core.common.TitleSanitizer
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.data.repository.resolver.episodeNumberFromLabel
import com.infinitezerone.minibgm.core.data.repository.resolver.titleMatches
import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.model.PlaybackSourceRule
import com.infinitezerone.minibgm.core.model.PlaylistEntryKind
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * TVBox / CatVod 规范的 JavaScript 爬虫执行与取源解析器。
 *
 * 流水线遵循标准 CatVod 状态机：
 * 1. [init]：初始化爬虫环境（若存在）；
 * 2. [search]：搜索番剧，借助 [TitleSanitizer] 与 [titleMatches] 智能命中目标条目；
 * 3. [detail]：获取剧集播放列表，按 [epNumber] 精准锁定目标分集参数；
 * 4. [play]：调用播放解析方法产出真实的媒体直链与请求头。
 */
class TvBoxSpiderResolver(
    private val sandbox: JsSpiderSandbox,
    private val pageFetchService: PageFetchService,
) {
    private val logger = bgmLogger("Bgm/TvBoxSpiderResolver")
    private val scriptCache = mutableMapOf<String, String>()

    suspend fun resolveSpider(
        rule: PlaybackSourceRule,
        title: String,
        epNumber: Float,
    ): List<PlayableSource> {
        val scriptContent = loadSpiderScript(rule.urlTemplate)
        if (scriptContent.isBlank()) {
            logger.w { "Spider script is empty or unreachable: ${rule.urlTemplate}" }
            return emptyList()
        }

        return try {
            sandbox.runInSandbox(scriptContent) { qjs ->
                // 1. 初始化爬虫
                try {
                    qjs.evaluate<Any?>("await __bgm_call('init', ''); void 0;")
                } catch (_: Throwable) {
                    // 部分爬虫无 init 或不需要初始化
                }

                // 2. 搜索番剧
                val vodItem = searchVodItem(qjs, title)
                if (vodItem == null) {
                    logger.d { "No matching vod item found for title: $title in rule: ${rule.name}" }
                    return@runInSandbox emptyList()
                }

                val vodId = vodItem.first
                val vodName = vodItem.second

                // 3. 详情获取播放列表
                val candidateRoutes = fetchEpisodeRoutes(qjs, vodId, epNumber)
                if (candidateRoutes.isEmpty()) {
                    logger.d { "No matching episode $epNumber found in vod: $vodName ($vodId)" }
                    return@runInSandbox emptyList()
                }

                // 4. 解析各线路直链
                val sources = mutableListOf<PlayableSource>()
                for ((flag, playParam) in candidateRoutes) {
                    val stream = resolvePlayStream(qjs, flag, playParam) ?: continue
                    val label = if (epNumber > 0f) "第 ${epNumber.toInt()} 话" else "正片"
                    val siteNameWithFlag = if (flag.isNotBlank()) "${rule.name} - $flag" else rule.name
                    sources.add(
                        PlayableSource(
                            url = stream.url,
                            kind = stream.kind,
                            label = label,
                            episodeSort = epNumber,
                            siteName = siteNameWithFlag,
                            pageUrl = rule.urlTemplate,
                            headers = stream.headers + rule.headers,
                            matchedVodName = vodName,
                        ),
                    )
                }
                sources
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed executing TVBox spider for rule ${rule.name} and title: $title" }
            emptyList()
        }
    }

    private suspend fun loadSpiderScript(template: String): String {
        val trimmed = template.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            scriptCache[trimmed]?.let { return it }
            val fetched = pageFetchService.fetchHtml(trimmed) ?: return ""
            if (fetched.html.isNotBlank()) {
                scriptCache[trimmed] = fetched.html
            }
            return fetched.html
        }
        return trimmed
    }

    private suspend fun searchVodItem(
        qjs: QuickJs,
        title: String,
    ): Pair<String, String>? {
        val searchKeywords =
            buildList {
                add(title)
                addAll(TitleSanitizer.extractDerivatives(title))
            }.distinct().filter { it.isNotBlank() }

        for (kw in searchKeywords) {
            val encodedKw = Json.encodeToString(kotlinx.serialization.serializer(), kw)
            val jsonStr =
                try {
                    qjs.evaluate<String>("JSON.stringify(await __bgm_call('search', $encodedKw))")
                } catch (t: Throwable) {
                    null
                } ?: continue

            val list = parseVodList(jsonStr)
            val matched =
                list.firstOrNull { item ->
                    val vName = item.second
                    titleMatches(vName, title) || titleMatches(vName, kw)
                } ?: list.firstOrNull()

            if (matched != null) return matched
        }
        return null
    }

    private fun parseVodList(jsonStr: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        val element = runCatching { Json.parseToJsonElement(jsonStr) }.getOrNull() ?: return emptyList()

        val items: JsonArray =
            when (element) {
                is JsonArray -> element
                is JsonObject -> element["list"]?.let { if (it is JsonArray) it else null } ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }

        for (item in items) {
            if (item !is JsonObject) continue
            val id = item["vod_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val name = item["vod_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (id.isNotBlank()) {
                results.add(id to name)
            }
        }
        return results
    }

    private suspend fun fetchEpisodeRoutes(
        qjs: QuickJs,
        vodId: String,
        epNumber: Float,
    ): List<Pair<String, String>> {
        val encodedId = Json.encodeToString(kotlinx.serialization.serializer(), vodId)
        val jsonStr =
            try {
                qjs.evaluate<String>("JSON.stringify(await __bgm_call('detail', $encodedId))")
            } catch (t: Throwable) {
                null
            } ?: return emptyList()

        val element = runCatching { Json.parseToJsonElement(jsonStr) }.getOrNull() ?: return emptyList()
        val vodObj: JsonObject =
            when (element) {
                is JsonObject -> {
                    val list = element["list"]
                    if (list is JsonArray && list.isNotEmpty() && list[0] is JsonObject) {
                        list[0].jsonObject
                    } else {
                        element
                    }
                }
                is JsonArray -> {
                    if (element.isNotEmpty() && element[0] is JsonObject) element[0].jsonObject else return emptyList()
                }
                else -> return emptyList()
            }

        val playFromStr = vodObj["vod_play_from"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val playUrlStr = vodObj["vod_play_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (playUrlStr.isBlank()) return emptyList()

        val flags = playFromStr.split("$$$").map { it.trim() }
        val routes = playUrlStr.split("$$$")
        val candidateRoutes = mutableListOf<Pair<String, String>>()

        for (i in routes.indices) {
            val flag = flags.getOrNull(i).orEmpty()
            val routeContent = routes[i]
            val episodes = routeContent.split('#')

            for (ep in episodes) {
                val parts = ep.split('$', limit = 2)
                if (parts.size < 2) continue
                val epLabel = parts[0].trim()
                val playParam = parts[1].trim()

                if (isEpisodeMatched(epLabel, epNumber)) {
                    candidateRoutes.add(flag to playParam)
                    break
                }
            }
        }
        return candidateRoutes
    }

    private fun isEpisodeMatched(
        label: String,
        targetEp: Float,
    ): Boolean {
        if (targetEp <= 0f) return true
        val parsedNum = episodeNumberFromLabel(label)
        if (parsedNum != null && parsedNum == targetEp) return true
        val intVal = targetEp.toInt()
        val token = "$intVal"
        val paddedToken = if (intVal < 10) "0$intVal" else token
        return label.contains(paddedToken) || label.contains("第$token") || label.contains("EP$token", ignoreCase = true)
    }

    private suspend fun resolvePlayStream(
        qjs: QuickJs,
        flag: String,
        playParam: String,
    ): ResolvedStream? {
        val encFlag = Json.encodeToString(kotlinx.serialization.serializer(), flag)
        val encParam = Json.encodeToString(kotlinx.serialization.serializer(), playParam)
        val jsonStr =
            try {
                qjs.evaluate<String>("JSON.stringify(await __bgm_call('play', $encFlag, $encParam))")
            } catch (t: Throwable) {
                null
            } ?: return null

        val element = runCatching { Json.parseToJsonElement(jsonStr) }.getOrNull() ?: return null
        return when (element) {
            is JsonObject -> {
                val url = element["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (url.isBlank()) return null
                val parseFlag = element["parse"]?.jsonPrimitive?.intOrNull ?: 0
                val headersMap = mutableMapOf<String, String>()
                val headerObj = element["header"] ?: element["headers"]
                if (headerObj is JsonObject) {
                    headerObj.forEach { (k, v) ->
                        v.jsonPrimitive.contentOrNull?.let { headersMap[k] = it }
                    }
                }
                ResolvedStream(
                    url = url,
                    kind = if (parseFlag == 1) PlaylistEntryKind.PAGE else PlaylistEntryKind.DIRECT,
                    headers = headersMap,
                )
            }
            else -> {
                val url = element.jsonPrimitive.contentOrNull.orEmpty()
                if (url.isNotBlank()) {
                    ResolvedStream(url = url, kind = PlaylistEntryKind.DIRECT)
                } else {
                    null
                }
            }
        }
    }

    private data class ResolvedStream(
        val url: String,
        val kind: PlaylistEntryKind,
        val headers: Map<String, String> = emptyMap(),
    )
}
