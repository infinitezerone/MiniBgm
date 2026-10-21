package com.infinitezerone.minibgm.core.data.repository.resolver.spider

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.FunctionBinding
import com.dokar.quickjs.quickJs
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.net.URI
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 运行第三方 TVBox / CatVod JavaScript 爬虫的轻量安全沙盒。
 *
 * 物理安全边界：
 * 1. 内存硬上限 16MB，单次执行超时上限 15 秒；
 * 2. 外部不可信脚本无法访问本地文件与系统底层，所有网络请求必须经由宿主 [PageFetchService] 校验；
 * 3. 随调随起，使用完毕后自动释放原生内存。
 */
class JsSpiderSandbox(
    private val pageFetchService: PageFetchService,
) {
    private val logger = bgmLogger("Bgm/JsSpider")

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun <T> runInSandbox(
        spiderScript: String,
        block: suspend (QuickJs) -> T,
    ): T =
        quickJs(Dispatchers.Default) {
            memoryLimit = 16 * 1024 * 1024L
            evaluationTimeoutMillis = 15_000L

            // 1. 注入宿主网络适配层
            defineBinding(
                "_nativeFetch",
                FunctionBinding { args ->
                    val url = args.getOrNull(0)?.toString().orEmpty()
                    val optionsJson = args.getOrNull(1)?.toString().orEmpty()
                    // CatVod 的 req 是同步 API；QuickJS 回调里阻塞等待宿主网络，
                    // 既兼容 `await req(...)`，也兼容直接 `req(...).content`。
                    runBlocking { handleNativeFetch(url, optionsJson) }
                },
            )

            defineBinding(
                "_nativePdfh",
                FunctionBinding { args ->
                    selectHtml(
                        html = args.getOrNull(0)?.toString().orEmpty(),
                        selector = args.getOrNull(1)?.toString().orEmpty(),
                        baseUrl = args.getOrNull(2)?.toString().orEmpty(),
                    ).firstOrNull().orEmpty()
                },
            )

            defineBinding(
                "_nativePdfa",
                FunctionBinding { args ->
                    val values =
                        selectHtml(
                            html = args.getOrNull(0)?.toString().orEmpty(),
                            selector = args.getOrNull(1)?.toString().orEmpty(),
                            baseUrl = args.getOrNull(2)?.toString().orEmpty(),
                        )
                    Json.encodeToString(ListSerializer(String.serializer()), values)
                },
            )

            defineBinding(
                "_nativeJoin",
                FunctionBinding { args ->
                    joinUrl(
                        baseUrl = args.getOrNull(0)?.toString().orEmpty(),
                        target = args.getOrNull(1)?.toString().orEmpty(),
                    )
                },
            )

            // 2. 注入加解密与工具函数
            defineBinding(
                "_nativeMd5",
                FunctionBinding { args ->
                    val input = args.getOrNull(0)?.toString().orEmpty()
                    computeMd5(input)
                },
            )

            defineBinding(
                "_nativeBase64Encode",
                FunctionBinding { args ->
                    val input = args.getOrNull(0)?.toString().orEmpty()
                    Base64.Default.encode(input.encodeToByteArray())
                },
            )

            defineBinding(
                "_nativeBase64Decode",
                FunctionBinding { args ->
                    val input = args.getOrNull(0)?.toString().orEmpty()
                    runCatching { Base64.Default.decode(input).decodeToString() }.getOrDefault(input)
                },
            )

            defineBinding(
                "_nativeLog",
                FunctionBinding { args ->
                    val msg = args.joinToString(" ") { it?.toString().orEmpty() }
                    logger.d { "[SpiderLog] $msg" }
                    null
                },
            )

            // 3. 执行垫片 Polyfill
            evaluate<Any?>(POLYFILL_PRELUDE)

            // 4. 加载爬虫脚本
            evaluate<Any?>("$spiderScript\n;void 0;")

            // 5. 执行调用
            block(this)
        }

    private suspend fun handleNativeFetch(
        url: String,
        optionsJson: String,
    ): String {
        if (url.isBlank()) return "{}"
        return try {
            val options = runCatching { Json.parseToJsonElement(optionsJson).jsonObject }.getOrNull()
            val method =
                options
                    ?.get("method")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.uppercase() ?: "GET"
            val headersMap = parseHeaders(options)
            val contentType = headersMap.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value

            val fetched =
                if (method == "POST") {
                    requestPost(url, options?.get("data") ?: options?.get("body"), contentType, headersMap)
                } else {
                    pageFetchService.fetchHtml(url, headersMap)
                }

            val body = fetched?.html.orEmpty()
            val escapedBody = Json.encodeToString(kotlinx.serialization.serializer(), body)
            val escapedHeaders = Json.encodeToString(kotlinx.serialization.serializer(), fetched?.responseHeaders.orEmpty())
            val escapedUrl = Json.encodeToString(kotlinx.serialization.serializer(), fetched?.url ?: url)
            """{"status":${if (fetched != null) 200 else 404},"body":$escapedBody,"headers":$escapedHeaders,"url":$escapedUrl}"""
        } catch (e: Exception) {
            logger.w(e) { "Spider fetch failed for $url" }
            """{"status":500,"body":"","headers":{}}"""
        }
    }

    private fun parseHeaders(options: JsonObject?): MutableMap<String, String> {
        val headersMap = mutableMapOf<String, String>()
        options?.get("headers")?.jsonObject?.forEach { (k, v) ->
            if (v is JsonPrimitive) {
                v.contentOrNull?.let { headersMap[k] = it }
            }
        }
        return headersMap
    }

    private suspend fun requestPost(
        url: String,
        data: JsonElement?,
        contentType: String?,
        headers: Map<String, String>,
    ) = when (data) {
        null, JsonNull -> pageFetchService.postBody(url, "", contentType, headers)
        is JsonObject ->
            if (isJsonContentType(contentType)) {
                pageFetchService.postBody(url, encodeJsonElement(data), contentType, headers)
            } else {
                pageFetchService.postForm(url, data.toFormData(), headers)
            }
        is JsonArray -> pageFetchService.postBody(url, encodeJsonElement(data), contentType ?: "application/json", headers)
        is JsonPrimitive -> {
            val body = data.content
            val resolvedContentType =
                contentType ?: if (looksLikeJson(body)) "application/json" else "application/x-www-form-urlencoded; charset=UTF-8"
            pageFetchService.postBody(url, body, resolvedContentType, headers)
        }
    }

    private fun JsonObject.toFormData(): Map<String, String> =
        entries
            .mapNotNull { (key, value) ->
                when (value) {
                    is JsonPrimitive -> value.contentOrNull?.let { key to it }
                    is JsonObject, is JsonArray -> key to encodeJsonElement(value)
                }
            }.toMap()

    private fun isJsonContentType(contentType: String?): Boolean = contentType?.contains("json", ignoreCase = true) == true

    private fun looksLikeJson(body: String): Boolean {
        val trimmed = body.trim()
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
    }

    private fun encodeJsonElement(element: JsonElement): String = Json.encodeToString(JsonElement.serializer(), element)

    private fun joinUrl(
        baseUrl: String,
        target: String,
    ): String {
        if (target.isBlank()) return baseUrl
        if (baseUrl.isBlank()) return target
        return runCatching { URI(baseUrl).resolve(target).toString() }.getOrDefault(target)
    }

    private fun selectHtml(
        html: String,
        selector: String,
        baseUrl: String,
    ): List<String> {
        if (html.isBlank() || selector.isBlank()) return emptyList()
        return runCatching {
            val doc = Jsoup.parse(html, baseUrl.ifBlank { "https://spider.invalid/" })
            val segments = selector.split("&&").map { it.trim() }.filter { it.isNotEmpty() }
            if (segments.isEmpty()) return@runCatching emptyList()
            var elements = doc.select(segments.first()).toList()
            if (elements.isEmpty()) return@runCatching emptyList()

            for (segment in segments.drop(1)) {
                when (segment.lowercase()) {
                    "text" -> return@runCatching elements.map { it.text() }
                    "owntext" -> return@runCatching elements.map { it.ownText() }
                    "html", "outerhtml" -> return@runCatching elements.map { it.outerHtml() }
                }
                if (segment.startsWith("@")) {
                    val attrName = segment.removePrefix("@")
                    return@runCatching elements.map { it.attr(attrName) }.filter { it.isNotBlank() }
                }
                val nested = elements.flatMap { element -> element.select(segment) }
                if (nested.isNotEmpty()) {
                    elements = nested
                    continue
                }
                val attrs = elements.map { it.attr(segment) }.filter { it.isNotBlank() }
                if (attrs.isNotEmpty()) return@runCatching attrs
                return@runCatching emptyList()
            }
            elements.map { it.text() }
        }.getOrDefault(emptyList())
    }

    private fun computeMd5(input: String): String =
        try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.encodeToByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }

    companion object {
        private val POLYFILL_PRELUDE =
            """
            globalThis.req = function(url, options) {
                var optStr = JSON.stringify(options || {});
                var resStr = _nativeFetch(url, optStr);
                var resObj = JSON.parse(resStr || "{}");
                var bodyStr = resObj.body || "";
                return {
                    content: bodyStr,
                    body: bodyStr,
                    headers: resObj.headers || {},
                    status: resObj.status || 200,
                    code: resObj.status || 200,
                    url: resObj.url || url,
                    toString: function() { return this.content; }
                };
            };
            globalThis.request = globalThis.req;
            globalThis.fetch = function(url, options) {
                var res = globalThis.req(url, options);
                return Promise.resolve({
                    ok: res.status >= 200 && res.status < 300,
                    status: res.status,
                    url: res.url,
                    headers: res.headers,
                    text: function() { return Promise.resolve(res.content); },
                    json: function() { return Promise.resolve(JSON.parse(res.content || "{}")); }
                });
            };
            globalThis.md5 = function(s) { return _nativeMd5(s || ""); };
            globalThis.base64Encode = function(s) { return _nativeBase64Encode(s || ""); };
            globalThis.base64Decode = function(s) { return _nativeBase64Decode(s || ""); };
            globalThis.btoa = globalThis.base64Encode;
            globalThis.atob = globalThis.base64Decode;
            globalThis.join = function(base, target) { return _nativeJoin(base || "", target || ""); };
            globalThis.crypto = globalThis.crypto || {};
            globalThis.crypto.md5 = globalThis.md5;

            globalThis.__bgm_base_url = function(options) {
                if (typeof options === "string") return options;
                if (!options) return "";
                return options.baseUrl || options.base || options.BASE || options.url || "";
            };
            globalThis.__bgm_abs = function(value, baseUrl) {
                if (!value || !baseUrl) return value;
                var text = String(value);
                if (/^(https?:)?\/\//i.test(text) || text.charAt(0) === "/" || text.indexOf("./") === 0) {
                    return _nativeJoin(baseUrl, text);
                }
                return value;
            };
            globalThis.pdfh = function(html, selector, options) {
                var baseUrl = globalThis.__bgm_base_url(options);
                var value = _nativePdfh(html || "", selector || "", baseUrl);
                if (options && (options.HOST || options.host)) value = globalThis.__bgm_abs(value, baseUrl);
                return value;
            };
            globalThis.pdfa = function(html, selector, options) {
                var baseUrl = globalThis.__bgm_base_url(options);
                var values = JSON.parse(_nativePdfa(html || "", selector || "", baseUrl));
                if (options && (options.HOST || options.host)) {
                    return values.map(function(value) { return globalThis.__bgm_abs(value, baseUrl); });
                }
                return values;
            };
            globalThis.pd = globalThis.pdfa;

            var __bgmLocalStore = Object.create(null);
            globalThis.local = {
                get: function(key, fallback) {
                    var value = __bgmLocalStore[String(key)];
                    return value === undefined ? (fallback === undefined ? null : fallback) : value;
                },
                set: function(key, value) { __bgmLocalStore[String(key)] = String(value); return value; },
                delete: function(key) { delete __bgmLocalStore[String(key)]; },
                clear: function() { __bgmLocalStore = Object.create(null); }
            };
            if (!globalThis.console) globalThis.console = {};
            globalThis.console.log = function(...args) {
                _nativeLog(args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(" "));
            };

            globalThis.__bgm_call = async function(method, ...args) {
                if (typeof spider !== 'undefined' && typeof spider[method] === 'function') {
                    return await spider[method](...args);
                }
                if (typeof globalThis[method] === 'function') {
                    return await globalThis[method](...args);
                }
                if (typeof __jsEvalReturn !== 'undefined' && typeof __jsEvalReturn[method] === 'function') {
                    return await __jsEvalReturn[method](...args);
                }
                throw new Error("Spider method not found: " + method);
            };
            """.trimIndent()
    }
}
