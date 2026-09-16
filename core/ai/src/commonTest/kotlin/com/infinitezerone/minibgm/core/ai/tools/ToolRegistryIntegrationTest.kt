package com.infinitezerone.minibgm.core.ai.tools

import ai.koog.agents.core.tools.ToolRegistry
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSearchRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeSubjectRepository
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolRegistryIntegrationTest {
    @Test
    fun toolRegistry_registers_all_tools_from_toolsets() {
        val scheduleTools = ScheduleTools(FakeScheduleRepository())
        val subjectTools = SubjectTools(FakeSearchRepository(), FakeSubjectRepository())
        val collectionTools = CollectionTools(FakeCollectionRepository())

        val registry =
            ToolRegistry {
                tools(scheduleTools)
                tools(subjectTools)
                tools(collectionTools)
            }

        val toolNames = registry.tools.map { it.name }
        // Schedule tools
        assertTrue(toolNames.contains("getSchedule"))
        assertTrue(toolNames.contains("getNextEpisodeAiring"))

        // Subject tools
        assertTrue(toolNames.contains("searchAnime"))
        assertTrue(toolNames.contains("getSubjectDetail"))
        assertTrue(toolNames.contains("getSubjectEpisodes"))

        // Collection tools
        assertTrue(toolNames.contains("getCollection"))
        assertTrue(toolNames.contains("getWatchingList"))
        assertTrue(toolNames.contains("proposeUpdateCollection"))
        assertTrue(toolNames.contains("proposeUpdateEpisodeProgress"))

        val getScheduleDescriptor = registry.getTool("getSchedule").descriptor
        assertNotNull(getScheduleDescriptor)
        assertTrue(getScheduleDescriptor.description.contains("broadcast schedule"))
    }
}
