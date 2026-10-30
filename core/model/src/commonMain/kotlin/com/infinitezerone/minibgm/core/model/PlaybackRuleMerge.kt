package com.infinitezerone.minibgm.core.model

/**
 * 把 [incoming] 合并进 [current]：按 id → 名称 → URL 模板三级身份判定「同一条规则」。
 *
 * 命中即原位更新，并保留本地既有 id 与开关状态——用户手动关掉的源不该因为一次刷新或重导被偷偷打开；
 * 未命中才追加。订阅一键刷新与所有导入入口共用这一份语义，避免出现「刷新按名称去重、
 * 对话框导入只认随机 id」这种同一件事两种结果的局面。
 */
fun mergeImportedRules(
    current: List<PlaybackSourceRule>,
    incoming: List<PlaybackSourceRule>,
): List<PlaybackSourceRule> {
    val merged = current.toMutableList()
    incoming.forEach { rule ->
        val index = merged.indexOfFirst { existing -> isSameSource(existing, rule) }
        if (index >= 0) {
            val existing = merged[index]
            merged[index] = rule.copy(id = existing.id, isEnabled = existing.isEnabled)
        } else {
            merged.add(rule)
        }
    }
    return merged
}

/** 两条规则是否指向同一来源：id 相同，或名称/URL 模板任一相同（不同入口给的 id 不一定一致） */
internal fun isSameSource(
    a: PlaybackSourceRule,
    b: PlaybackSourceRule,
): Boolean = a.id == b.id || a.name.equals(b.name, ignoreCase = true) || a.urlTemplate == b.urlTemplate
