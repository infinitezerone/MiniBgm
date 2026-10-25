package com.infinitezerone.minibgm.eval

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 场景 cold_start_nav：冷启动 + 底部三 Tab 遍历冒烟。
 *
 * 通过判据（机械部分）：应用 20s 内出首帧、三个顶层 Tab（文案与 core/navigation strings 同源）
 * 均可寻址且可切换、全程目标进程无 FATAL EXCEPTION。
 * 判据之外的信息（页面是否真的渲染了内容、骨架是否闪空态等）不在此断言——
 * 每步证据由 [EvidenceCapturer] 落盘，交由离线评估器对照验收契约判卷。
 *
 * 单跑：`-Pandroid.testInstrumentationRunnerArguments.scenario=cold_start_nav`。
 */
class ColdStartNavSmokeTest {
    private companion object {
        const val SCENARIO = "cold_start_nav"

        /** 文案 → 步骤目录名（目录名保持 ASCII，artifact 路径不依赖文件系统编码） */
        val TABS = listOf("放送" to "schedule", "探索" to "explore", "我的" to "user")
    }

    private val device: UiDevice get() = EvidenceCapturer.device

    @Test
    fun coldStart_and_tabTraversal() {
        assumeScenario()
        device.pressHome()

        val launch =
            EvidenceCapturer.targetContext.packageManager
                .getLaunchIntentForPackage(EvidenceCapturer.targetContext.packageName)
        assertTrue("找不到 ${EvidenceCapturer.targetContext.packageName} 的启动 Intent", launch != null)
        launch!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        EvidenceCapturer.targetContext.startActivity(launch)
        assertTrue("20s 内应用未完成首帧", device.wait(Until.hasObject(By.pkg(packageName()).depth(0)), 20_000))
        device.waitForIdle(5_000)
        EvidenceCapturer.capture(SCENARIO, "01_cold_launch")

        TABS.forEachIndexed { index, (label, key) ->
            assertTrue("10s 内未出现 Tab「$label」", device.wait(Until.hasObject(By.text(label)), 10_000))
            // 同名文案可能同时出现在页面标题与底栏，取树序最后一个（底栏位于屏幕底部）。
            // 不检查 isClickable：Compose 把 clickable 挂在父容器，Text 语义节点本身不带标记，
            // 按坐标点击依然有效——检查它只会制造假失败。
            val tab = device.findObjects(By.text(label)).lastOrNull()
            assertTrue("底栏找不到 Tab「$label」", tab != null)
            tab!!.click()
            device.waitForIdle(3_000)
            EvidenceCapturer.capture(SCENARIO, "%02d_tab_%s".format(index + 2, key))
        }

        val fatal = EvidenceCapturer.scanFatal()
        assertTrue("目标进程出现 FATAL EXCEPTION：\n${fatal.joinToString("\n")}", fatal.isEmpty())
    }

    private fun packageName(): String = EvidenceCapturer.targetContext.packageName

    /** 支持 scenario 参数单跑（CI workflow_dispatch 传入）；缺省 all 全跑 */
    private fun assumeScenario() {
        val selected = InstrumentationRegistry.getArguments().getString("scenario") ?: "all"
        assumeTrue("scenario=$selected 未选中 $SCENARIO", selected == "all" || selected == SCENARIO)
    }
}
