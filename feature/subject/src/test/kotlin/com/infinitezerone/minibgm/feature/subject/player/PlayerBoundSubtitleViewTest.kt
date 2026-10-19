package com.infinitezerone.minibgm.feature.subject.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * 软字幕呈现组件 [PlayerBoundSubtitleView] 行为与生命周期单测：
 * 验证横竖屏切换（窗口附着/脱离）、关闭字幕拦截与实时分发。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerBoundSubtitleViewTest {
    private lateinit var context: Context
    private lateinit var stubPlayer: StubPlayer
    private lateinit var player: Player

    private class StubPlayer : InvocationHandler {
        val listeners = mutableListOf<Player.Listener>()
        var params: TrackSelectionParameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
        var cues: CueGroup = CueGroup(emptyList(), 0L)

        fun create(): Player =
            Proxy.newProxyInstance(
                Player::class.java.classLoader,
                arrayOf(Player::class.java),
                this,
            ) as Player

        fun fireCues(newCues: List<Cue>) {
            val group = CueGroup(newCues, 0L)
            cues = group
            listeners.toList().forEach { it.onCues(group) }
        }

        fun updateParameters(newParams: TrackSelectionParameters) {
            params = newParams
            listeners.toList().forEach { it.onTrackSelectionParametersChanged(newParams) }
        }

        fun fireTracksChanged() {
            listeners.toList().forEach { it.onTracksChanged(Tracks.EMPTY) }
        }

        fun firePositionDiscontinuity() {
            val pos =
                Player.PositionInfo(
                    null,
                    0,
                    null,
                    null,
                    0,
                    0L,
                    0L,
                    0,
                    0,
                )
            listeners.toList().forEach {
                it.onPositionDiscontinuity(pos, pos, Player.DISCONTINUITY_REASON_SEEK)
            }
        }

        override fun invoke(
            proxy: Any?,
            method: Method,
            args: Array<out Any?>?,
        ): Any? =
            when (method.name) {
                "addListener" -> {
                    listeners.add(args!![0] as Player.Listener)
                    null
                }
                "removeListener" -> {
                    listeners.remove(args!![0] as Player.Listener)
                    null
                }
                "getTrackSelectionParameters" -> params
                "getCurrentCues" -> cues
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                "toString" -> "StubPlayer"
                else ->
                    when (method.returnType) {
                        Boolean::class.javaPrimitiveType -> false
                        Int::class.javaPrimitiveType -> 0
                        Long::class.javaPrimitiveType -> 0L
                        Float::class.javaPrimitiveType -> 0f
                        Double::class.javaPrimitiveType -> 0.0
                        else -> null
                    }
            }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        stubPlayer = StubPlayer()
        player = stubPlayer.create()
    }

    @Test
    fun attachAndDetach_registersAndClearsListenerDirectly() {
        val view = PlayerBoundSubtitleView(context, player)

        // 初始未附着到窗口，不泄漏 listener
        assertTrue(stubPlayer.listeners.isEmpty())

        // 模拟附着到窗口（如竖屏首次挂载）
        view.onViewAttachedToWindow(view)
        assertEquals(1, stubPlayer.listeners.size)

        // 分发字幕帧
        val cueA = Cue.Builder().setText("第一句字幕").build()
        stubPlayer.fireCues(listOf(cueA))
        assertEquals(listOf(cueA), view.renderedCues)

        // 模拟横竖屏切换：脱离旧窗口
        view.onViewDetachedFromWindow(view)
        assertTrue("脱离窗口后必须注销监听以防泄漏", stubPlayer.listeners.isEmpty())
        assertTrue("脱离窗口后必须清空字幕防止幽灵残影", view.renderedCues.isEmpty())

        // 模拟横屏重挂载：附着到新窗口
        val cueB = Cue.Builder().setText("第二句字幕（切横屏时）").build()
        stubPlayer.cues = CueGroup(listOf(cueB), 0L)
        view.onViewAttachedToWindow(view)

        assertEquals("重挂载后应立刻重新监听", 1, stubPlayer.listeners.size)
        assertEquals("重挂载后应立刻同步最新分集字幕", listOf(cueB), view.renderedCues)

        // 彻底释放
        view.release()
        assertTrue(stubPlayer.listeners.isEmpty())
        assertTrue(view.renderedCues.isEmpty())
    }

    @Test
    fun disableSubtitles_immediatelyClearsViewAndBlocksFurtherCues() {
        val view = PlayerBoundSubtitleView(context, player)
        view.onViewAttachedToWindow(view)

        // 正常播放下有字幕
        val cue = Cue.Builder().setText("正常显示中的字幕").build()
        stubPlayer.fireCues(listOf(cue))
        assertEquals(listOf(cue), view.renderedCues)

        // 用户点击「关闭字幕」：更新 trackSelectionParameters 禁用 TEXT 轨道
        val disabledParams =
            stubPlayer.params
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        stubPlayer.updateParameters(disabledParams)

        assertTrue(
            "禁用字幕轨后无论 onCues 是否到达，视图都必须当场清空",
            view.renderedCues.isEmpty(),
        )

        // 即使底层解码器滞后推送了新字幕，也必须被拦截
        val staleCue = Cue.Builder().setText("解码器残余字幕").build()
        stubPlayer.fireCues(listOf(staleCue))
        assertTrue(
            "禁用状态下后续任何 onCues 必须被拦截清空，严禁卡死上屏",
            view.renderedCues.isEmpty(),
        )

        // 用户重新开启字幕
        val enabledParams =
            stubPlayer.params
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        stubPlayer.updateParameters(enabledParams)

        val freshCue = Cue.Builder().setText("重新开启后的新字幕").build()
        stubPlayer.fireCues(listOf(freshCue))
        assertEquals(listOf(freshCue), view.renderedCues)
    }

    @Test
    fun seekAndTrackChanges_resyncsCurrentCues() {
        val view = PlayerBoundSubtitleView(context, player)
        view.onViewAttachedToWindow(view)

        val cueAtNewPos = Cue.Builder().setText("Seek 后位置的字幕").build()
        stubPlayer.cues = CueGroup(listOf(cueAtNewPos), 0L)

        stubPlayer.firePositionDiscontinuity()
        assertEquals(listOf(cueAtNewPos), view.renderedCues)

        val cueAfterTrackSwitch = Cue.Builder().setText("切换音轨/字幕后的字幕").build()
        stubPlayer.cues = CueGroup(listOf(cueAfterTrackSwitch), 0L)

        stubPlayer.fireTracksChanged()
        assertEquals(listOf(cueAfterTrackSwitch), view.renderedCues)
    }
}
