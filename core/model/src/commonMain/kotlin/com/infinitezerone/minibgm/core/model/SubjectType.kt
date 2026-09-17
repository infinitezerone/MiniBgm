package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * Bangumi 条目大类（SubjectType）
 * 1: 书籍 (Book)
 * 2: 动画 (Anime)
 * 3: 音乐 (Music)
 * 4: 游戏 (Game)
 * 6: 三次元 (Real)
 */
@Serializable
enum class SubjectType(
    val value: Int,
    val label: String,
    val iconEmoji: String,
    val actionWish: String,
    val actionDoing: String,
    val actionCollect: String,
    val unitName: String,
    val releaseVerb: String,
    val progressVerb: String,
) {
    BOOK(
        value = 1,
        label = "书籍",
        iconEmoji = "📚",
        actionWish = "想读",
        actionDoing = "在读",
        actionCollect = "读过",
        unitName = "卷",
        releaseVerb = "出版",
        progressVerb = "读",
    ),
    ANIME(
        value = 2,
        label = "动画",
        iconEmoji = "📺",
        actionWish = "想看",
        actionDoing = "在看",
        actionCollect = "看过",
        unitName = "话",
        releaseVerb = "首播",
        progressVerb = "看",
    ),
    MUSIC(
        value = 3,
        label = "音乐",
        iconEmoji = "🎵",
        actionWish = "想听",
        actionDoing = "在听",
        actionCollect = "听过",
        unitName = "首",
        releaseVerb = "发行",
        progressVerb = "听",
    ),
    GAME(
        value = 4,
        label = "游戏",
        iconEmoji = "🎮",
        actionWish = "想玩",
        actionDoing = "在玩",
        actionCollect = "玩过",
        unitName = "章",
        releaseVerb = "发售",
        progressVerb = "玩",
    ),
    REAL(
        value = 6,
        label = "三次元",
        iconEmoji = "🎬",
        actionWish = "想看",
        actionDoing = "在看",
        actionCollect = "看过",
        unitName = "集",
        releaseVerb = "首映",
        progressVerb = "看",
    ),
    ;

    companion object {
        fun fromValue(value: Int): SubjectType = entries.firstOrNull { it.value == value } ?: ANIME
    }
}
