package com.infinitezerone.minibgm.core.data.crash

import android.content.Context
import android.os.Build
import com.infinitezerone.minibgm.core.common.TimeUtils
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** 崩溃日志目录名（应用私有 files 目录下）。记录器与 [FileCrashLogRepository] 共用 */
internal const val CRASH_LOG_DIR = "crash"

/** 保留的崩溃日志份数上限 */
private const val MAX_CRASH_LOGS = 5

/** 崩溃日志目录；不保证存在 */
internal fun crashLogDir(context: Context): File = File(context.filesDir, CRASH_LOG_DIR)

/**
 * 未捕获异常落盘器。
 *
 * release 包经 R8 混淆，堆栈里只有 `a.b.c`——没有 mapping 就完全读不了；而本项目没有接入任何
 * 崩溃收集 SDK，也没有导出日志的入口。于是"崩溃时把堆栈留在本地、由用户在设置页导出"是唯一
 * 能让堆栈到达开发者手里的通道（见 [CrashLogRepository]）。
 *
 * **绝不吞崩溃**：写完文件仍把异常交回给原有处理器，系统的崩溃行为（重启、ANR 日志）一切照旧。
 * 记录本身也绝不允许成为二次崩溃的源头——所有失败都被吞掉。
 */
object CrashLogRecorder {
    @Volatile
    private var installed = false

    /**
     * 安装全局未捕获异常处理器。幂等，重复调用无副作用。
     *
     * 必须在 `Application.onCreate` 里尽早调用——启动阶段的崩溃同样要能记录。
     *
     * @param appVersion 形如 `0.3.0 (30000)`；由 app 层传入，避免在这里碰已弃用的
     *   `PackageManager.getPackageInfo(String, Int)`。
     */
    fun install(
        context: Context,
        appVersion: String,
    ) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashLog(appContext, appVersion, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(
        context: Context,
        appVersion: String,
        thread: Thread,
        throwable: Throwable,
    ) {
        val dir = crashLogDir(context)
        if (!dir.isDirectory && !dir.mkdirs()) return
        val report = buildReport(appVersion, thread, throwable)
        // 文件名即时间戳：仓库按文件名倒序取最近一条，不必解析文件内容
        File(dir, "crash-${TimeUtils.nowEpochMillis()}.txt").writeText(report)
        pruneOldLogs(dir)
    }

    /** 只留最近 [MAX_CRASH_LOGS] 份：这是给用户手动导出的，堆多了既没人看又占空间 */
    private fun pruneOldLogs(dir: File) {
        dir
            .listFiles()
            ?.sortedByDescending { it.name }
            ?.drop(MAX_CRASH_LOGS)
            ?.forEach { it.delete() }
    }

    /**
     * 渲染成用户能直接贴进 Issue 的文本：机型与版本在前（定位问题的第一手信息），堆栈在后。
     *
     * 刻意只采集机型/系统版本这类非标识性信息，不碰设备 ID、账号、使用数据。
     */
    private fun buildReport(
        appVersion: String,
        thread: Thread,
        throwable: Throwable,
    ): String {
        val stackTrace =
            StringWriter().also { writer -> throwable.printStackTrace(PrintWriter(writer)) }.toString()
        return buildString {
            appendLine("MiniBgm 崩溃报告")
            appendLine("时间: ${TimeUtils.isoUtcFromEpochMillis(TimeUtils.nowEpochMillis())}")
            appendLine("版本: $appVersion")
            appendLine(
                "机型: ${Build.MANUFACTURER} ${Build.MODEL} " +
                    "(Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})",
            )
            appendLine("线程: ${thread.name}")
            appendLine()
            append(stackTrace)
        }
    }
}
