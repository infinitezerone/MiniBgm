package com.infinitezerone.minibgm.core.network.ech

import com.infinitezerone.minibgm.core.common.bgmLogger
import java.io.File

/**
 * ECH 运行时状态的原子文本文件读写。
 *
 * 写入走「同目录临时文件 + 覆盖」两步：直接 writeText 时进程若在中途被杀，会留下半个文件，
 * 下次启动读到的就是损坏内容。临时文件与目标同目录，保证替换发生在同一文件系统上。
 *
 * 读失败一律返回 null 交给调用方回退默认值——这些文件全是纯缓存，
 * 损坏不允许影响主流程（[com.infinitezerone.minibgm.core.network.ech.EchEdgePool] 会退回空池）。
 */
internal object EchAtomicFile {
    private val logger = bgmLogger("Bgm/EchStore")

    fun read(file: File): String? =
        runCatching { if (file.isFile) file.readText().trim() else null }
            .onFailure { logger.w(it) { "[ECH_STORE:READ:FAILED] ${file.name}" } }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }

    fun write(
        file: File,
        content: String,
    ) {
        runCatching {
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(content)
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }.onFailure { logger.w(it) { "[ECH_STORE:WRITE:FAILED] ${file.name}" } }
    }
}
