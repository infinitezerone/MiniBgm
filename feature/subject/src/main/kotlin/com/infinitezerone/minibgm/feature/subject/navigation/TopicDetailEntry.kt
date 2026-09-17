package com.infinitezerone.minibgm.feature.subject.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.infinitezerone.minibgm.core.navigation.TopicDetailRoute
import com.infinitezerone.minibgm.feature.subject.TopicDetailScreen

/** 讨论帖详情页面的导航条目；由 `:app` 的 BgmNavHost 聚合（NiA 模式） */
fun EntryProviderScope<NavKey>.topicDetailEntry(
    onBackClick: () -> Unit,
    onSubjectClick: (Long) -> Unit = {},
    onTopicClick: (Long, String) -> Unit = { _, _ -> },
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<TopicDetailRoute>(metadata = metadata) { route ->
        TopicDetailScreen(
            topicId = route.topicId,
            initialTitle = route.initialTitle,
            type = route.type,
            onBackClick = onBackClick,
            onSubjectClick = onSubjectClick,
            onTopicClick = onTopicClick,
        )
    }
}
