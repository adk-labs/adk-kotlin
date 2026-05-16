package dev.adk.kotlin

import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompactionTest {
    @Test
    fun `events compaction config validates official token field pairing`() {
        val config =
            EventsCompactionConfig(
                compactionInterval = 2,
                overlapSize = 1,
                tokenThreshold = 50,
                eventRetentionSize = 2,
            )

        assertEquals(2, config.compactionInterval)
        assertEquals(1, config.overlapSize)
        assertEquals(50, config.tokenThreshold)
        assertEquals(2, config.eventRetentionSize)

        assertFailsWith<IllegalArgumentException> {
            EventsCompactionConfig(compactionInterval = 2, overlapSize = 1, tokenThreshold = 50)
        }
        assertFailsWith<IllegalArgumentException> {
            EventsCompactionConfig(compactionInterval = 2, overlapSize = -1)
        }
    }

    @Test
    fun `sliding window compacts when enough new invocations exist`() =
        runTest {
            val summarizer =
                CapturingSummarizer(
                    compactedEvent = compactedEvent("2026-05-16T00:00:01Z", "2026-05-16T00:00:04Z", "summary"),
                )
            val app = compactionApp(summarizer = summarizer, interval = 2, overlap = 1)
            val session =
                AgentSession(
                    id = "session-1",
                    userId = "user-1",
                    events =
                        listOf(
                            event("inv1", "2026-05-16T00:00:01Z", "e1"),
                            event("inv2", "2026-05-16T00:00:02Z", "e2"),
                            event("inv3", "2026-05-16T00:00:03Z", "e3"),
                            event("inv4", "2026-05-16T00:00:04Z", "e4"),
                        ),
                )
            val store = InMemorySessionStore()
            store.save(app.name, session)

            val updated = runCompactionForSlidingWindow(app, session, store)

            assertEquals(listOf("inv1", "inv2", "inv3", "inv4"), summarizer.received.single().map { it.invocationId })
            assertEquals(5, updated.events.size)
            assertEquals("summary", updated.events.last().content?.text)
            assertEquals("summary", store.get(app.name, "user-1", "session-1")?.events?.last()?.content?.text)
        }

    @Test
    fun `sliding window includes configured overlap after prior compaction`() =
        runTest {
            val summarizer =
                CapturingSummarizer(
                    compactedEvent = compactedEvent("2026-05-16T00:00:02Z", "2026-05-16T00:00:05Z", "summary-2"),
                )
            val app = compactionApp(summarizer = summarizer, interval = 2, overlap = 1)
            val previousCompaction = compactedEvent("2026-05-16T00:00:01Z", "2026-05-16T00:00:02Z", "summary-1")
            val session =
                AgentSession(
                    id = "session-1",
                    userId = "user-1",
                    events =
                        listOf(
                            event("inv1", "2026-05-16T00:00:01Z", "e1"),
                            event("inv2", "2026-05-16T00:00:02Z", "e2"),
                            previousCompaction,
                            event("inv3", "2026-05-16T00:00:03Z", "e3"),
                            event("inv4", "2026-05-16T00:00:04Z", "e4"),
                            event("inv5", "2026-05-16T00:00:05Z", "e5"),
                        ),
                )

            runCompactionForSlidingWindow(app, session, InMemorySessionStore())

            assertEquals(listOf("inv2", "inv3", "inv4", "inv5"), summarizer.received.single().map { it.invocationId })
        }

    @Test
    fun `token threshold compaction keeps retention tail`() =
        runTest {
            val summarizer =
                CapturingSummarizer(
                    compactedEvent = compactedEvent("2026-05-16T00:00:01Z", "2026-05-16T00:00:03Z", "summary"),
                )
            val app =
                compactionApp(
                    summarizer = summarizer,
                    interval = 999,
                    overlap = 0,
                    tokenThreshold = 50,
                    eventRetentionSize = 2,
                )
            val session =
                AgentSession(
                    id = "session-1",
                    userId = "user-1",
                    events =
                        listOf(
                            event("inv1", "2026-05-16T00:00:01Z", "e1"),
                            event("inv2", "2026-05-16T00:00:02Z", "e2"),
                            event("inv3", "2026-05-16T00:00:03Z", "e3"),
                            event("inv4", "2026-05-16T00:00:04Z", "e4"),
                            event("inv5", "2026-05-16T00:00:05Z", "e5", promptTokens = 100),
                        ),
                )

            runCompactionForSlidingWindow(app, session, InMemorySessionStore())

            assertEquals(listOf("inv1", "inv2", "inv3"), summarizer.received.single().map { it.invocationId })
        }

    @Test
    fun `latest prompt token count estimates from effective post-compaction content`() {
        val events =
            listOf(
                event("inv1", "2026-05-16T00:00:01Z", "a".repeat(40)),
                event("inv2", "2026-05-16T00:00:02Z", "b".repeat(40)),
                compactedEvent("2026-05-16T00:00:01Z", "2026-05-16T00:00:02Z", "S"),
                event("inv3", "2026-05-16T00:00:03Z", "c".repeat(20)),
            )

        assertEquals(21 / 4, latestPromptTokenCount(events))
    }

    @Test
    fun `runner persists post-run compaction event without requiring caller to invoke helper`() =
        runTest {
            val summarizer =
                CapturingSummarizer(
                    compactedEvent = compactedEvent("2026-05-16T00:00:01Z", "2026-05-16T00:00:04Z", "runtime-summary"),
                )
            val app =
                app("runtime_compaction_app") {
                    eventsCompactionConfig(
                        summarizer = summarizer,
                        compactionInterval = 1,
                        overlapSize = 0,
                    )
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }
            val store = InMemorySessionStore()
            val runner =
                Runner(
                    app = app,
                    model = LanguageModel { ModelResponse.Final("done") },
                    sessionStore = store,
                )

            val result = runner.run(userId = "user-1", sessionId = "session-1", input = "hello")

            assertTrue(summarizer.received.single().isNotEmpty())
            assertEquals("runtime-summary", result.session.events.last().content?.text)
            assertEquals("runtime-summary", store.get(app.name, "user-1", "session-1")?.events?.last()?.content?.text)
        }

    @Test
    fun `compaction does nothing when summarizer is absent or threshold not met`() =
        runTest {
            val app = compactionApp(summarizer = null, interval = 2, overlap = 0)
            val session =
                AgentSession(
                    id = "session-1",
                    userId = "user-1",
                    events = listOf(event("inv1", "2026-05-16T00:00:01Z", "e1")),
                )

            val updated = runCompactionForSlidingWindow(app, session, InMemorySessionStore())

            assertEquals(session, updated)
            assertNull(latestPromptTokenCount(emptyList()))
        }

    private fun compactionApp(
        summarizer: BaseEventsSummarizer?,
        interval: Int,
        overlap: Int,
        tokenThreshold: Int? = null,
        eventRetentionSize: Int? = null,
    ): AdkApp =
        app("compaction_app") {
            eventsCompactionConfig(
                summarizer = summarizer,
                compactionInterval = interval,
                overlapSize = overlap,
                tokenThreshold = tokenThreshold,
                eventRetentionSize = eventRetentionSize,
            )
            rootAgent("planner") {
                model = "gemini-2.5-pro"
            }
        }

    private fun event(
        invocationId: String,
        timestamp: String,
        text: String,
        promptTokens: Int? = null,
    ): Event =
        Event(
            invocationId = invocationId,
            author = "user",
            content = UserMessage(text),
            timestamp = Instant.parse(timestamp),
            usageMetadata = EventUsageMetadata(promptTokenCount = promptTokens),
        )

    private fun compactedEvent(
        start: String,
        end: String,
        text: String,
    ): Event {
        val compaction =
            EventCompaction(
                startTimestamp = Instant.parse(start),
                endTimestamp = Instant.parse(end),
                compactedContent = ModelMessage(text),
            )
        return Event(
            invocationId = Event.newId(),
            author = "compactor",
            content = compaction.compactedContent,
            actions = EventActions(compaction = compaction),
            timestamp = compaction.endTimestamp,
        )
    }

    private class CapturingSummarizer(
        private val compactedEvent: Event?,
    ) : BaseEventsSummarizer {
        val received = mutableListOf<List<Event>>()

        override suspend fun maybeSummarizeEvents(events: List<Event>): Event? {
            received += events
            return compactedEvent
        }
    }
}
