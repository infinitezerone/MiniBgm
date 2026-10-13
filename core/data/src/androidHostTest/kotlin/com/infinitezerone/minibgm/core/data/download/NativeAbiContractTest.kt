package com.infinitezerone.minibgm.core.data.download

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 原生库 ABI 契约门禁。
 *
 * [AndroidTorrentStreamEngine.SUPPORTED_ABIS] 是客户端侧「愿意接受哪些架构的 BT 扩展 .so」的白名单，
 * 而构建侧的真源是仓库根 `native/abis.txt`（`tools/build-native` 与 release workflow 同一份）。
 * 两者漂移会产生静默缺档：构建侧新增架构时客户端拒绝下载，或客户端白名单超集时下载 404。
 */
class NativeAbiContractTest {
    @Test
    fun supportedAbis_matchesNativeAbiManifest() {
        val manifestAbis = readNativeAbiManifest()
        assertEquals(
            manifestAbis,
            AndroidTorrentStreamEngine.SUPPORTED_ABIS,
            "SUPPORTED_ABIS 与 native/abis.txt 不一致：改 ABI 时两者必须同步（真源为 native/abis.txt）。",
        )
    }

    private fun readNativeAbiManifest(): Set<String> =
        findFromRepoRoot("native/abis.txt")
            .readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    /** 单测工作目录随 Gradle 与 IDE 而异，向上逐层探测，避免硬编码相对层级。 */
    private fun findFromRepoRoot(relativePath: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val current = dir ?: return@repeat
            val candidate = File(current, relativePath)
            if (candidate.isFile) return candidate
            dir = current.parentFile
        }
        error("未能从 ${System.getProperty("user.dir")} 向上定位 $relativePath（仓库根真源文件缺失？）")
    }
}
