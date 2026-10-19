package com.infinitezerone.minibgm.feature.subject.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拖动状态持有者：active 与 fraction 必须永远同帧一致（单字段原子快照），
 * 写入口收口为 begin / update / end / cancel 四件套。
 */
class PlayerScrubStateTest {
    @Test
    fun defaultsToInactiveAtZeroFraction() {
        val scrub = PlayerScrubState()

        assertFalse(scrub.state.active)
        assertFalse(scrub.isActive)
        assertEquals(0f, scrub.state.fraction, 0.0001f)
    }

    @Test
    fun beginLandsOnPressedPositionInsteadOfZero() {
        val scrub = PlayerScrubState()

        scrub.begin(0.42f)

        assertTrue(scrub.state.active)
        // 按下点即初值：进度条不会先归零再跳回，时间文本也不会闪一下 00:00
        assertEquals(0.42f, scrub.state.fraction, 0.0001f)
    }

    @Test
    fun updateKeepsDraggingAndOverwritesFraction() {
        val scrub = PlayerScrubState()
        scrub.begin(0.1f)

        scrub.update(0.75f)

        assertTrue(scrub.state.active)
        assertEquals(0.75f, scrub.state.fraction, 0.0001f)
    }

    @Test
    fun updateAfterEndIsIgnoredSoLateFramesCannotResurrectDragging() {
        val scrub = PlayerScrubState()
        scrub.begin(0.2f)
        scrub.end(0.6f)

        scrub.update(0.9f)

        assertFalse(scrub.state.active)
        assertEquals(0.6f, scrub.state.fraction, 0.0001f)
    }

    @Test
    fun fractionsAreClampedToUnitRange() {
        val scrub = PlayerScrubState()

        scrub.begin(-1f)
        assertEquals(0f, scrub.state.fraction, 0.0001f)

        scrub.update(2f)
        assertEquals(1f, scrub.state.fraction, 0.0001f)

        scrub.end(5f)
        assertEquals(1f, scrub.state.fraction, 0.0001f)
    }

    @Test
    fun endCommitsFinalFractionAndExitsDragging() {
        val scrub = PlayerScrubState()
        scrub.begin(0.3f)
        scrub.update(0.8f)

        scrub.end(0.8f)

        assertFalse(scrub.state.active)
        assertEquals(0.8f, scrub.state.fraction, 0.0001f)
    }

    @Test
    fun cancelExitsDraggingWithoutCarryingScrubPosition() {
        val scrub = PlayerScrubState()
        scrub.begin(0.3f)
        scrub.update(0.8f)

        scrub.cancel()

        // 取消不提交 seek，进度条回到真实播放位置（由 position 重新推导）
        assertFalse(scrub.state.active)
        assertEquals(0f, scrub.state.fraction, 0.0001f)
    }
}
