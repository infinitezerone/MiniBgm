package com.infinitezerone.minibgm.core.data.repository

import com.infinitezerone.minibgm.core.model.PlayableSource
import com.infinitezerone.minibgm.core.network.PageFetchService
import com.infinitezerone.minibgm.core.network.StreamProbe

/** 直链可播性断言结论 */
sealed interface StreamVerification {
    /** 首包 2xx/206 且 Content-Type 像媒体 */
    data object Playable : StreamVerification

    /** 明确不能播；[reason] 会回给模型与用户，让他们知道该改哪儿 */
    data class NotPlayable(
        val reason: String,
    ) : StreamVerification

    /** 当前环境探测不了（PageFetchService 实现不支持），不下结论 */
    data object Unverified : StreamVerification
}

/**
 * 直链首包断言门：**抽到地址不等于能播**。
 *
 * 规则解析只证明"从页面里抽得出地址"，证明不了"这个地址真能播"——失效的直链、
 * 需要额外请求头的中转页、改版后指向 HTML 的地址，都会让"解析成功"退化成"点了才知道播不了"。
 * 这里用一次首包请求把结论提前到导入之前。
 */
interface PlaybackSourceVerifier {
    suspend fun verify(source: PlayableSource): StreamVerification
}

class PlaybackSourceVerifierImpl(
    private val pageFetchService: PageFetchService,
) : PlaybackSourceVerifier {
    override suspend fun verify(source: PlayableSource): StreamVerification =
        when (val probe = pageFetchService.probeStream(source.url, source.headers)) {
            StreamProbe.Unsupported -> StreamVerification.Unverified
            is StreamProbe.Failed -> StreamVerification.NotPlayable("首包请求失败：${probe.reason}")
            is StreamProbe.Responded -> judge(probe)
        }

    private fun judge(probe: StreamProbe.Responded): StreamVerification {
        if (probe.status !in 200..299) {
            return StreamVerification.NotPlayable("首包返回 HTTP ${probe.status}")
        }
        val type = probe.contentType?.lowercase().orEmpty()
        if (type.isEmpty()) {
            return StreamVerification.NotPlayable("响应没有 Content-Type，无法确认是媒体")
        }
        if (MEDIA_CONTENT_TYPES.any { type.startsWith(it) }) return StreamVerification.Playable
        if (type.startsWith("text/")) {
            return StreamVerification.NotPlayable(
                "拿到的是文本页而不是媒体流（站点可能改版，或该地址需要额外的请求头）",
            )
        }
        return StreamVerification.NotPlayable("Content-Type 不是媒体：$type")
    }

    private companion object {
        /**
         * `application/octet-stream` 也算媒体：大量 CDN 给 mp4 与 HLS 分片回的就是这个通用类型，
         * 首包已经 2xx 的情况下，交给播放器兜底比在这里判死更划算。
         */
        private val MEDIA_CONTENT_TYPES =
            listOf(
                "video/",
                "audio/",
                "application/vnd.apple.mpegurl",
                "application/x-mpegurl",
                "application/octet-stream",
            )
    }
}
