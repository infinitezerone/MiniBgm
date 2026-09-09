package com.infinitezerone.minibgm.core.common

import co.touchlab.kermit.Logger

/**
 * 获取带有指定 Tag 的 Kermit Logger 实例。
 * 建议 Tag 遵循统一命名分层（例如 "Bgm/Network"、"Bgm/Worker/Sync"、"Bgm/Repo/Collection" 等）。
 */
fun bgmLogger(tag: String): Logger = Logger.withTag(tag)
