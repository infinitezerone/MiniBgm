package com.infinitezerone.minibgm.eval

import android.content.Context
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

/**
 * 评估器证据采集器：每一步固定落三样证据（截图 / UI 树 / 目标进程 logcat 切片），
 * 写入应用外部私有目录 `files/eval-evidence/<scenario>/<step>/`，CI 端 adb pull 后归档为 artifact。
 *
 * 证据是一等公民：**成功与失败共用同一份采集**——离线判卷（会话侧评估器对照验收契约逐条裁决）
 * 判的是成功判据，"失败才留证据"会让 pass 无从复核。
 */
object EvidenceCapturer {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    val device: UiDevice get() = UiDevice.getInstance(instrumentation)
    val targetContext: Context get() = instrumentation.targetContext

    fun scenarioRoot(scenario: String): File = File(targetContext.getExternalFilesDir(null), "eval-evidence/$scenario").apply { mkdirs() }

    fun capture(
        scenario: String,
        step: String,
    ) {
        val dir = File(scenarioRoot(scenario), step).apply { mkdirs() }
        // 采集自身绝不能弄死被测应用：任一证据失败都降级为"缺这份证据"
        runCatching { device.takeScreenshot(File(dir, "screen.png")) }
        runCatching { device.dumpWindowHierarchy(File(dir, "ui.xml")) }
        runCatching { dumpLogcat(File(dir, "logcat.txt")) }
    }

    /** 崩溃零容忍：目标进程 logcat 出现 FATAL EXCEPTION 即返回命中行（空列表 = 干净） */
    fun scanFatal(): List<String> = targetLogcat().filter { it.contains("FATAL EXCEPTION") }

    /** 只看目标进程：instrumentation 默认与被测应用同进程，myPid 即应用 pid；-d 即刻返回不阻塞 */
    private fun dumpLogcat(out: File) {
        targetLogcatAsStream().use { input -> out.outputStream().use(input::copyTo) }
    }

    private fun targetLogcat(): List<String> = targetLogcatAsStream().bufferedReader().readLines()

    private fun targetLogcatAsStream() =
        ProcessBuilder("logcat", "-d", "-v", "time", "--pid=${Process.myPid()}")
            .start()
            .inputStream
}
