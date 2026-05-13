package dev.adk.kotlin

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventModelTest {
    @Test
    fun `event actions support official deltas compaction and ui widget metadata`() {
        val compaction =
            EventCompaction(
                startTimestamp = Instant.parse("2026-04-27T00:00:00Z"),
                endTimestamp = Instant.parse("2026-04-27T00:01:00Z"),
                compactedContent = ModelMessage("Compacted context."),
            )
        val uiWidget =
            UiWidget(
                id = "widget-1",
                provider = "mcp",
                payload = mapOf("resource_uri" to "ui://widget"),
            )

        val current =
            EventActions(
                stateDelta =
                    mapOf(
                        "profile" to mapOf("name" to "Alice"),
                        "city" to "Seoul",
                    ),
                artifactDelta = mapOf("notes.txt" to 1),
            )
        val incoming =
            EventActions(
                stateDelta =
                    mapOf(
                        "profile" to mapOf("tier" to "gold"),
                        "city" to "Busan",
                    ),
                artifactDelta = mapOf("notes.txt" to 2),
                deletedArtifactIds = setOf("old.txt"),
                transferToAgent = "researcher",
                compaction = compaction,
                renderUiWidgets = listOf(uiWidget),
            )

        val merged = current.merge(incoming)
        val profile = merged.stateDelta.getValue("profile") as Map<*, *>

        assertEquals("Alice", profile["name"])
        assertEquals("gold", profile["tier"])
        assertEquals("Busan", merged.stateDelta["city"])
        assertEquals(mapOf("notes.txt" to 2), merged.artifactDelta)
        assertEquals(setOf("old.txt"), merged.deletedArtifactIds)
        assertEquals("researcher", merged.transferToAgent)
        assertEquals(compaction, merged.compaction)
        assertEquals(listOf(uiWidget), merged.renderUiWidgets)
    }

    @Test
    fun `event actions can mark state keys as removed`() {
        val actions = EventActions().removeStateByKey("temp:draft")

        assertEquals(STATE_REMOVED, actions.stateDelta["temp:draft"])
    }

    @Test
    fun `event actions normalize null state deltas to removed sentinel`() {
        val actions =
            EventActions(
                stateDelta =
                    mapOf(
                        "temp:draft" to null,
                        "profile" to mapOf("name" to "Alice"),
                    ),
            )

        assertEquals(STATE_REMOVED, actions.stateDelta["temp:draft"])
        assertEquals(mapOf("name" to "Alice"), actions.stateDelta["profile"])
    }

    @Test
    fun `event actions merge null state deltas as removed sentinel`() {
        val merged =
            EventActions(
                stateDelta = mapOf("temp:draft" to "old"),
            ).merge(
                EventActions(
                    stateDelta = mapOf("temp:draft" to null),
                ),
            )

        assertEquals(STATE_REMOVED, merged.stateDelta["temp:draft"])
    }

    @Test
    fun `event final response follows official skip long-running and model content semantics`() {
        assertTrue(
            Event(
                invocationId = "invocation-1",
                author = "planner",
                content = ModelMessage("Final answer."),
            ).isFinalResponse(),
        )
        assertFalse(
            Event(
                invocationId = "invocation-1",
                author = "planner",
                content = ModelMessage("Partial answer."),
                partial = true,
            ).isFinalResponse(),
        )
        assertTrue(
            Event(
                invocationId = "invocation-1",
                author = "planner",
                content = ToolMessage("lookup", "pending"),
                longRunningToolIds = setOf("call-1"),
            ).isFinalResponse(),
        )
        assertTrue(
            Event(
                invocationId = "invocation-1",
                author = "planner",
                content = ToolMessage("load_memory", "loaded"),
                actions = EventActions(skipSummarization = true),
            ).isFinalResponse(),
        )
    }

    @Test
    fun `event preserves official streaming error and model metadata fields`() {
        val event =
            Event(
                invocationId = "invocation-1",
                author = "planner",
                errorCode = "SAFETY",
                errorMessage = "Blocked.",
                finishReason = "SAFETY",
                usageMetadata = EventUsageMetadata(promptTokenCount = 10, candidatesTokenCount = 3, totalTokenCount = 13),
                avgLogprobs = -0.1,
                interrupted = true,
                groundingMetadata = mapOf("source" to "search"),
                customMetadata = mapOf("trace" to "abc"),
                modelVersion = "gemini-test",
                inputTranscription = EventTranscription("hello"),
                outputTranscription = EventTranscription("world", finished = true),
            )

        assertEquals("SAFETY", event.errorCode)
        assertEquals("Blocked.", event.errorMessage)
        assertEquals("gemini-test", event.modelVersion)
        assertEquals("hello", event.inputTranscription?.text)
        assertEquals(true, event.outputTranscription?.finished)
        assertEquals(13, event.usageMetadata?.totalTokenCount)
    }
}
