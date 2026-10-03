package com.infinitezerone.minibgm.core.model

/** 分集类型分组枚举 */
enum class EpisodeGroup(
    val label: String,
) {
    MAIN("本篇"),
    SP("特别篇"),
    OP_ED("OP/ED"),
    OTHER("其他"),
    ;

    companion object {
        fun fromType(type: Int): EpisodeGroup =
            when (type) {
                0 -> MAIN
                1 -> SP
                2, 3 -> OP_ED
                else -> OTHER
            }
    }
}
