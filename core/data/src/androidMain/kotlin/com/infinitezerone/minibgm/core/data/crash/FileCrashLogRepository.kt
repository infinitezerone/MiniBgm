package com.infinitezerone.minibgm.core.data.crash

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 从 [crashDir] 读取 [CrashLogRecorder] 落盘的崩溃日志。
 *
 * 文件名是 `crash-<epochMillis>.txt`，所以"最近一条"按**文件名倒序**取即可，不必读全部内容——
 * epoch 毫秒恒为 13 位，字典序与数值序一致。
 *
 * 从未崩溃过（目录不存在）时返回 null / 清空为无操作，不抛异常：用户点开一个空列表不该看到报错。
 *
 * 刻意接收**目录**而不是 `Context`：路径由调用方（DI）用 `crashLogDir()` 算好，
 * 这里就只剩纯粹的目录读写，单测拿临时目录即可覆盖全部分支，不必上 Robolectric。
 */
class FileCrashLogRepository(
    private val crashDir: File,
) : CrashLogRepository {
    override suspend fun latest(): CrashLog? =
        withContext(Dispatchers.IO) {
            val file =
                crashDir
                    .listFiles { entry -> entry.isFile && entry.name.endsWith(LOG_SUFFIX) }
                    ?.maxByOrNull { entry -> entry.name }
                    ?: return@withContext null
            // 单个文件读失败（权限/损坏）不该让整个入口报错，当作没有这条
            val content = runCatching { file.readText() }.getOrNull() ?: return@withContext null
            CrashLog(occurredAtMillis = epochMillisOf(file.name), content = content)
        }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            crashDir.listFiles()?.forEach(File::delete)
        }
    }

    /** 文件名里的时间戳；解析不出来给 0——不因为命名异常就把整份日志判成无效 */
    private fun epochMillisOf(fileName: String): Long = fileName.removePrefix(FILE_PREFIX).removeSuffix(LOG_SUFFIX).toLongOrNull() ?: 0L

    private companion object {
        const val FILE_PREFIX = "crash-"
        const val LOG_SUFFIX = ".txt"
    }
}
