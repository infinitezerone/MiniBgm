package com.infinitezerone.minibgm.core.data.repository.resolver.spider

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.AsyncFunctionBinding
import com.dokar.quickjs.binding.FunctionBinding
import com.dokar.quickjs.quickJs
import com.infinitezerone.minibgm.core.common.bgmLogger
import com.infinitezerone.minibgm.core.network.PageFetchService
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
                AsyncFunctionBinding { args ->
                    val url = args.getOrNull(0)?.toString().orEmpty()
                    val optionsJson = args.getOrNull(1)?.toString().orEmpty()
                    handleNativeFetch(url, optionsJson)
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
            val headersMap = mutableMapOf<String, String>()
            options?.get("headers")?.jsonObject?.forEach { (k, v) ->
                v.jsonPrimitive.contentOrNull?.let { headersMap[k] = it }
            }

            val fetched =
                if (method == "POST") {
                    val dataObj = options?.get("data") ?: options?.get("body")
                    val formData = mutableMapOf<String, String>()
                    when (dataObj) {
                        is JsonObject -> dataObj.forEach { (k, v) -> v.jsonPrimitive.contentOrNull?.let { formData[k] = it } }
                        else -> {}
                    }
                    pageFetchService.postForm(url, formData, headersMap)
                } else {
                    pageFetchService.fetchHtml(url, headersMap)
                }

            val body = fetched?.html.orEmpty()
            val escapedBody = Json.encodeToString(kotlinx.serialization.serializer(), body)
            val escapedHeaders = Json.encodeToString(kotlinx.serialization.serializer(), fetched?.responseHeaders.orEmpty())
            """{"status":${if (fetched != null) 200 else 404},"body":$escapedBody,"headers":$escapedHeaders}"""
        } catch (e: Exception) {
            logger.w(e) { "Spider fetch failed for $url" }
            """{"status":500,"body":"","headers":{}}"""
        }
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
            globalThis.req = async function(url, options) {
                var optStr = JSON.stringify(options || {});
                var resStr = await _nativeFetch(url, optStr);
                var resObj = JSON.parse(resStr || "{}");
                var bodyStr = resObj.body || "";
                return {
                    content: bodyStr,
                    body: bodyStr,
                    headers: resObj.headers || {},
                    status: resObj.status || 200,
                    code: resObj.status || 200,
                    toString: function() { return this.content; }
                };
            };
            globalThis.request = globalThis.req;
            globalThis.md5 = function(s) { return _nativeMd5(s || ""); };
            globalThis.base64Encode = function(s) { return _nativeBase64Encode(s || ""); };
            globalThis.base64Decode = function(s) { return _nativeBase64Decode(s || ""); };
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
