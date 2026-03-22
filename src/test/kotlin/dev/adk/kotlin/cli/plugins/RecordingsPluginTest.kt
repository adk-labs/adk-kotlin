package dev.adk.kotlin.cli.plugins

import dev.adk.kotlin.AgentSession
import dev.adk.kotlin.InMemorySessionStore
import dev.adk.kotlin.LanguageModel
import dev.adk.kotlin.ModelResponse
import dev.adk.kotlin.Runner
import dev.adk.kotlin.ToolCall
import dev.adk.kotlin.ToolOutput
import dev.adk.kotlin.adkApp
import dev.adk.kotlin.tool
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordingsPluginTest {
    @Test
    fun `recordings plugin writes llm and tool recordings when config is enabled`() =
        runTest {
            val tempDir = Files.createTempDirectory("adk-recordings")
            val sessionStore = InMemorySessionStore()
            var modelCalls = 0

            val weatherTool =
                tool(
                    name = "lookup_weather",
                    description = "Lookup weather for a city.",
                ) {
                    ToolOutput("Seoul is sunny")
                }

            val app =
                adkApp("recordings-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        tool(weatherTool)
                    }
                }

            sessionStore.save(
                app.name,
                AgentSession(
                    id = "session-1",
                    userId = "user-1",
                    state =
                        mapOf(
                            RECORDINGS_CONFIG_KEY to
                                RecordingsConfig(
                                    testCasePath = tempDir.toString(),
                                    userMessageIndex = 0,
                                    streamingMode = "sse",
                                ).toStateValue(),
                        ),
                ),
            )

            val fakeModel =
                LanguageModel {
                    modelCalls += 1
                    when (modelCalls) {
                        1 ->
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall(
                                        toolName = "lookup_weather",
                                        arguments = mapOf("city" to "Seoul"),
                                    ),
                                ),
                            )

                        2 -> ModelResponse.Final("Seoul is sunny today.")
                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    sessionStore = sessionStore,
                    plugins = listOf(recordingsPlugin()),
                )

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "What is the weather in Seoul?",
                )

            val recordingsFile = tempDir.resolve("generated-recordings-sse.json")
            val sessionFile = tempDir.resolve("generated-session-sse.json")
            val recordingsJson = Files.readString(recordingsFile)
            val sessionJson = Files.readString(sessionFile)

            assertEquals("Seoul is sunny today.", result.finalMessage)
            assertTrue(Files.exists(recordingsFile))
            assertTrue(Files.exists(sessionFile))
            assertTrue(recordingsJson.contains("\"lookup_weather\""))
            assertTrue(recordingsJson.contains("\"gemini-2.5-pro\""))
            assertTrue(recordingsJson.contains("\"Seoul is sunny today.\""))
            assertTrue(sessionJson.contains("\"session-1\""))
            assertTrue(sessionJson.contains("\"What is the weather in Seoul?\""))
        }

    @Test
    fun `recordings plugin uses default filenames for non streaming mode`() =
        runTest {
            val tempDir = Files.createTempDirectory("adk-recordings-none")
            val sessionStore = InMemorySessionStore()

            val app =
                adkApp("recordings-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }

            sessionStore.save(
                app.name,
                AgentSession(
                    id = "session-1",
                    userId = "user-1",
                    state =
                        mapOf(
                            RECORDINGS_CONFIG_KEY to
                                RecordingsConfig(
                                    testCasePath = tempDir.toString(),
                                    userMessageIndex = 0,
                                ).toStateValue(),
                        ),
                ),
            )

            val runner =
                Runner(
                    app = app,
                    model = LanguageModel { ModelResponse.Final("No recording.") },
                    sessionStore = sessionStore,
                    plugins = listOf(recordingsPlugin()),
                )

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Hello",
                )

            val recordingsFile = tempDir.resolve("generated-recordings.json")
            val sessionFile = tempDir.resolve("generated-session.json")

            assertEquals("No recording.", result.finalMessage)
            assertTrue(Files.exists(recordingsFile))
            assertTrue(Files.exists(sessionFile))
        }
}
