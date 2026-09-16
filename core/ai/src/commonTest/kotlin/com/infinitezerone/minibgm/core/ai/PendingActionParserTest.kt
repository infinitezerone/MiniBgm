package com.infinitezerone.minibgm.core.ai

import com.infinitezerone.minibgm.core.model.ActionProposal
import com.infinitezerone.minibgm.core.model.CollectionType
import com.infinitezerone.minibgm.core.model.PendingAction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PendingActionParserTest {
    private val json = Json { prettyPrint = true }

    @Test
    fun extractPendingActions_returns_empty_when_text_is_blank() {
        val actions = PendingActionParser.extractPendingActions("   \n\t  ")
        assertTrue(actions.isEmpty())
    }

    @Test
    fun extractPendingActions_parses_direct_ActionProposal_json() {
        val proposal =
            ActionProposal(
                status = "PENDING_CONFIRMATION",
                message = "Please confirm",
                action =
                    PendingAction.UpdateEpisode(
                        actionId = "act_ep_1",
                        subjectId = 12345L,
                        subjectTitle = "葬送的芙莉莲",
                        episodeNumber = 10,
                        isWatched = true,
                        description = "Mark ep 10 as watched",
                    ),
            )
        val jsonStr = json.encodeToString(proposal)
        val extracted = PendingActionParser.extractPendingActions(jsonStr)

        assertEquals(1, extracted.size)
        assertIs<PendingAction.UpdateEpisode>(extracted[0])
        val action = extracted[0] as PendingAction.UpdateEpisode
        assertEquals("act_ep_1", action.actionId)
        assertEquals(12345L, action.subjectId)
        assertEquals(10, action.episodeNumber)
        assertEquals(true, action.isWatched)
    }

    @Test
    fun extractPendingActions_parses_embedded_json_in_markdown_text() {
        val proposal =
            ActionProposal(
                status = "PENDING_CONFIRMATION",
                message = "Proposal ready",
                action =
                    PendingAction.UpdateCollection(
                        actionId = "act_coll_1",
                        subjectId = 98765L,
                        subjectTitle = "迷宫饭",
                        collectionType = CollectionType.COLLECT,
                        rating = 9,
                        comment = "很香！",
                        isPrivate = false,
                        description = "Update collection to COLLECT",
                    ),
            )
        val proposalJson = json.encodeToString(proposal)
        val markdown =
            """
            好的，我已经为您整理好更新提案：
            ```json
            $proposalJson
            ```
            请您在下方卡片中点击确认提交。
            """.trimIndent()

        val extracted = PendingActionParser.extractPendingActions(markdown)
        assertEquals(1, extracted.size)
        assertIs<PendingAction.UpdateCollection>(extracted[0])
        val action = extracted[0] as PendingAction.UpdateCollection
        assertEquals("act_coll_1", action.actionId)
        assertEquals(98765L, action.subjectId)
        assertEquals("迷宫饭", action.subjectTitle)
        assertEquals(CollectionType.COLLECT, action.collectionType)
        assertEquals(9, action.rating)
        assertEquals("很香！", action.comment)
    }

    @Test
    fun extractPendingActions_handles_invalid_json_gracefully() {
        val corruptText = "Here is some text with broken json { status: PENDING_CONFIRMATION, action: { and nothing else"
        val extracted = PendingActionParser.extractPendingActions(corruptText)
        assertTrue(extracted.isEmpty())
    }

    @Test
    fun pendingActionStore_adds_removes_and_pops_all() {
        val store = PendingActionStore()
        val action1 =
            PendingAction.UpdateEpisode(
                actionId = "a1",
                subjectId = 1L,
                episodeNumber = 1,
                description = "Ep 1",
            )
        val action2 =
            PendingAction.UpdateCollection(
                actionId = "a2",
                subjectId = 2L,
                collectionType = CollectionType.DOING,
                description = "Doing",
            )

        store.add(action1)
        store.add(action2)
        assertEquals(2, store.actions.value.size)

        // Duplicate add is deduplicated
        store.add(action1)
        assertEquals(2, store.actions.value.size)

        store.remove("a1")
        assertEquals(1, store.actions.value.size)
        assertEquals("a2", store.actions.value[0].actionId)

        val popped = store.popAll()
        assertEquals(1, popped.size)
        assertTrue(store.actions.value.isEmpty())
    }

    @Test
    fun extractPendingActions_handles_stray_braces_and_braces_in_strings() {
        val markdown =
            """
            Some pre-text with a stray } and { unmatched brace!
            ```json
            {
              "status": "PENDING_CONFIRMATION",
              "message": "Update proposal with braces",
              "action": {
                "type": "update_collection",
                "actionId": "act_brace_1",
                "subjectId": 54321,
                "subjectTitle": "Anime {Special} Edition",
                "collectionType": "DOING",
                "comment": "Watched episode 1 } highly recommended! \"Quote with } inside\"",
                "isPrivate": false,
                "description": "Mark as watching"
              }
            }
            ```
            Trailing notes } with curly brace.
            """.trimIndent()

        val extracted = PendingActionParser.extractPendingActions(markdown)
        assertEquals(1, extracted.size)
        assertIs<PendingAction.UpdateCollection>(extracted[0])
        val action = extracted[0] as PendingAction.UpdateCollection
        assertEquals("act_brace_1", action.actionId)
        assertEquals(54321L, action.subjectId)
        assertEquals("Anime {Special} Edition", action.subjectTitle)
        assertEquals(CollectionType.DOING, action.collectionType)
        assertEquals("Watched episode 1 } highly recommended! \"Quote with } inside\"", action.comment)
    }
}
