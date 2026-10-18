package com.infinitezerone.minibgm.core.testing

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 网络层契约红线：把这一轮修掉的四个"会静默坏掉"的行为固化成源码级断言。
 *
 * 这些不是风格检查，而是曾经的故障现场：
 * 1. 未列入白名单的 4xx/5xx 被当成成功，写操作"确认了但没落库"；
 * 2. 下载用 requestTimeoutMillis = null 想关超时，结果被回填成默认值；
 * 3. 自建 HttpClient(CIO) 给回环流打补丁，而不是收敛到 RoutingEngine；
 * 4. 抓取/订阅接受任意 URL，且响应体无上限。
 */
class NetworkContractRulesTest {
    private val projectRoot: File by lazy {
        var current: File? = File(".").canonicalFile
        while (current != null && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile
        }
        current ?: error("无法定位工程根目录（settings.gradle.kts 未找到）")
    }

    private fun read(relativePath: String): String {
        val file = File(projectRoot, relativePath)
        assertTrue(file.isFile, "文件未找到: " + relativePath)
        return KotlinSourceScanner.sanitize(file.readText())
    }

    @Test
    fun network_policy_contracts_must_not_regress() {
        val violations = mutableListOf<String>()

        val bgmHttpClient =
            read("core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/BgmHttpClient.kt")
        if (!bgmHttpClient.contains("RequestRejected") ||
            !bgmHttpClient.contains("400..499") ||
            !bgmHttpClient.contains("500..599")
        ) {
            violations.add("BgmHttpClient.kt 丢失 4xx/5xx 全量映射：未列入白名单的失败会再次被当成成功")
        }
        if (bgmHttpClient.contains("install(HttpCache)")) {
            violations.add("BgmHttpClient.kt 重新安装了无策略 HttpCache：内存无界且缓存键不含 Authorization")
        }

        val assetDownload =
            read("core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/AssetDownloadService.kt")
        if (!assetDownload.contains("INFINITE_TIMEOUT_MS")) {
            violations.add("AssetDownloadService.kt 未使用 INFINITE_TIMEOUT_MS：null 会被回填成默认预算")
        }
        if (assetDownload.contains("HttpClient(CIO)")) {
            violations.add("AssetDownloadService.kt 自建 CIO client：回环分流必须收敛到 RoutingEngine")
        }

        val pageFetch =
            read("core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/PageFetchService.kt")
        if (!pageFetch.contains("UrlSafety.isAllowedRemoteUrl")) {
            violations.add("PageFetchService.kt 未校验远端 URL：规则页抓取可被诱导访问本机/私网")
        }
        if (!pageFetch.contains("resolveRedirect") || !pageFetch.contains("untrustedClient")) {
            violations.add("PageFetchService.kt 未逐跳校验 30x：私网跳转会在复核前被发出")
        }

        val subscription =
            read("core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/CommunitySubscriptionService.kt")
        if (!subscription.contains("UrlSafety.isAllowedRemoteUrl")) {
            violations.add("CommunitySubscriptionService.kt 未校验订阅 URL")
        }
        if (!subscription.contains("readLimitedText")) {
            violations.add("CommunitySubscriptionService.kt 未限制订阅响应体体积")
        }
        if (!subscription.contains("resolveRedirect")) {
            violations.add("CommunitySubscriptionService.kt 未逐跳校验 30x")
        }

        val networkModule =
            read("core/network/src/commonMain/kotlin/com/infinitezerone/minibgm/core/network/di/NetworkModule.kt")
        if (!networkModule.contains("followRedirects = false")) {
            violations.add("NetworkModule.kt 未为第三方抓取提供禁止自动跟随的 untrusted client")
        }

        val networkRoot = File(projectRoot, "core/network/src")
        networkRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot {
                it.invariantSeparatorsPath.contains("/commonTest/") ||
                    it.invariantSeparatorsPath.contains("/androidHostTest/") ||
                    it.invariantSeparatorsPath.contains("/build/")
            }.forEach { file ->
                val code = KotlinSourceScanner.sanitize(file.readText())
                if (code.contains("requestTimeoutMillis = null")) {
                    violations.add(file.relativeTo(projectRoot).path + " 使用 requestTimeoutMillis = null 关闭超时（会被回填成默认值）")
                }
            }

        assertTrue(
            violations.isEmpty(),
            "网络层契约红线被破坏：\n" + violations.joinToString("\n"),
        )
    }
}
