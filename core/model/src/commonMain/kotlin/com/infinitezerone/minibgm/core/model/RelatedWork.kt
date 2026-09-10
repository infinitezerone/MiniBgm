package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 角色出演或人物参与制作的关联条目作品（对应 /v0/characters/{id}/subjects 与 /v0/persons/{id}/subjects）
 */
@Serializable
data class RelatedWork(
    val id: Long,
    val name: String,
    @SerialName("name_cn") val nameCn: String = "",
    val staff: String = "",
    val image: String = "",
    val type: Int = 2,
) {
    val displayName: String
        get() = nameCn.ifBlank { name }

    val coverImage: String
        get() = image.replace("http://", "https://")
}

/**
 * 聚合相同条目的作品记录：同一人物/角色在同一作品中身兼多职时，合并为一个作品条目，职位以「 / 」分隔。
 * 避免相同作品在列表中重复渲染，并彻底杜绝 Compose LazyRow/LazyColumn 的 Key 重复冲突。
 */
fun List<RelatedWork>.aggregateBySubject(): List<RelatedWork> {
    if (isEmpty()) return emptyList()
    return groupBy { it.id }.values.map { works ->
        val first = works.first()
        val combinedStaff =
            works
                .map { it.staff.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(" / ")
        first.copy(staff = combinedStaff)
    }
}
