package dev.adk.kotlin

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageAndMemoryTest {
    @Test
    fun `file session store persists lists and deletes sessions`() =
        runTest {
            val rootDir = Files.createTempDirectory("adk-kotlin-sessions")
            val store = FileSessionStore(rootDir)

            val savedSession =
                AgentSession(
                    id = "session-1",
                    userId = "user-1",
                    state = mapOf("user:name" to "Alice"),
                    transcript = listOf(UserMessage("Hello")),
                    events =
                        listOf(
                            Event(
                                invocationId = "inv-1",
                                author = "user",
                                content = UserMessage("Hello"),
                            ),
                        ),
                )

            store.save("travel-app", savedSession)

            val loaded = store.get("travel-app", "user-1", "session-1")
            val listed = store.list("travel-app", "user-1")

            assertEquals(savedSession, loaded)
            assertEquals(listOf(savedSession), listed)

            store.delete("travel-app", "user-1", "session-1")

            assertNull(store.get("travel-app", "user-1", "session-1"))
        }

    @Test
    fun `file artifact service persists versions and merges user plus session scopes`() =
        runTest {
            val rootDir = Files.createTempDirectory("adk-kotlin-artifacts")
            val service = FileArtifactService(rootDir)

            service.saveArtifact(
                appName = "travel-app",
                userId = "user-1",
                filename = "shared.txt",
                artifact = Artifact("Shared context"),
            )
            service.saveArtifact(
                appName = "travel-app",
                userId = "user-1",
                sessionId = "session-1",
                filename = "notes.txt",
                artifact = Artifact("Version 0"),
            )
            service.saveArtifact(
                appName = "travel-app",
                userId = "user-1",
                sessionId = "session-1",
                filename = "notes.txt",
                artifact = Artifact("Version 1"),
            )

            assertEquals(
                listOf("notes.txt", "shared.txt"),
                service.listArtifactKeys("travel-app", "user-1", "session-1"),
            )
            assertEquals(
                listOf(0, 1),
                service.listVersions(
                    appName = "travel-app",
                    userId = "user-1",
                    sessionId = "session-1",
                    filename = "notes.txt",
                ),
            )
            assertEquals(
                Artifact("Version 1", version = 1),
                service.loadArtifact(
                    appName = "travel-app",
                    userId = "user-1",
                    sessionId = "session-1",
                    filename = "notes.txt",
                ),
            )

            service.deleteArtifact(
                appName = "travel-app",
                userId = "user-1",
                sessionId = "session-1",
                filename = "notes.txt",
            )

            assertNull(
                service.loadArtifact(
                    appName = "travel-app",
                    userId = "user-1",
                    sessionId = "session-1",
                    filename = "notes.txt",
                ),
            )
        }

    @Test
    fun `tools can query memory through tool context`() =
        runTest {
            val memoryService = InMemoryMemoryService()
            memoryService.addMemory(
                appName = "travel-app",
                userId = "user-1",
                memories =
                    listOf(
                        MemoryEntry(
                            text = "planner: Seoul has many cafe streets.",
                            appName = "travel-app",
                            userId = "user-1",
                        ),
                    ),
            )

            var modelCalls = 0
            val memoryTool =
                tool(
                    name = "search_memory",
                    description = "Search user memory for relevant snippets.",
                ) { call ->
                    val response = searchMemory(call.requireArgument("query"))
                    ToolOutput(response.memories.joinToString { it.text })
                }

            val app =
                adkApp("travel-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        tool(memoryTool)
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1
                    when (modelCalls) {
                        1 ->
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall(
                                        toolName = "search_memory",
                                        arguments = mapOf("query" to "Seoul"),
                                    ),
                                ),
                            )

                        2 -> {
                            assertTrue(request.session.transcript.last().text.contains("Seoul has many cafe streets"))
                            ModelResponse.Final("Used memory.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    memoryService = memoryService,
                )

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Recall my Seoul notes.",
                )

            assertEquals("Used memory.", result.finalMessage)
            assertEquals("search_memory", result.toolExecutions.single().call.toolName)
            assertTrue(result.toolExecutions.single().output.content.contains("Seoul has many cafe streets"))
            assertNotNull(result.session.events.lastOrNull())
        }
}
