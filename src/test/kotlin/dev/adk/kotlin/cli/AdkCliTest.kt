package dev.adk.kotlin.cli

import dev.adk.kotlin.LanguageModel
import dev.adk.kotlin.ModelResponse
import dev.adk.kotlin.ToolCall
import dev.adk.kotlin.ToolOutput
import dev.adk.kotlin.UserMessage
import dev.adk.kotlin.adkApp
import dev.adk.kotlin.tool
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdkCliTest {
    @Test
    fun `cli run streams current invocation events`() =
        runTest {
            var modelCalls = 0
            val lines = mutableListOf<String>()

            val weatherTool =
                tool(
                    name = "lookup_weather",
                    description = "Lookup weather for a city.",
                ) {
                    ToolOutput("Seoul is sunny")
                }

            val app =
                adkApp("trip-planner") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        tool(weatherTool)
                    }
                }

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

            val cli =
                AdkCli(
                    agentLoader =
                        StaticAgentLoader(
                            mapOf(
                                "trip-planner" to LoadedApp(app = app, model = fakeModel),
                            ),
                        ),
                    output = { line -> lines += line },
                )

            val result =
                cli.run(
                    CliRunRequest(
                        appName = "trip-planner",
                        userId = "user-1",
                        sessionId = "session-1",
                        userMessage = UserMessage("What is the weather in Seoul?"),
                    ),
                )

            assertEquals("Seoul is sunny today.", result.finalMessage)
            assertEquals(
                listOf(
                    "[user]: What is the weather in Seoul?",
                    "[planner]: Seoul is sunny",
                    "[planner]: Seoul is sunny today.",
                ),
                lines,
            )
        }

    @Test
    fun `cli interactive mode reuses the same session across turns`() =
        runTest {
            val lines = mutableListOf<String>()
            val inputs = ArrayDeque(listOf("First question.", "Second question.", "exit"))
            var modelCalls = 0

            val app =
                adkApp("qa-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1
                    when (modelCalls) {
                        1 -> {
                            assertEquals(listOf("First question."), request.conversation.map { it.text })
                            ModelResponse.Final("First answer.")
                        }

                        2 -> {
                            assertEquals(
                                listOf("First question.", "First answer.", "Second question."),
                                request.conversation.map { it.text },
                            )
                            ModelResponse.Final("Second answer.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val loadedApp = LoadedApp(app = app, model = fakeModel)
            val cli =
                AdkCli(
                    agentLoader = StaticAgentLoader(mapOf("qa-app" to loadedApp)),
                    input = { inputs.removeFirstOrNull() },
                    output = { line -> lines += line },
                )

            val session =
                cli.runInteractive(
                    CliInteractiveRequest(
                        appName = "qa-app",
                        userId = "user-1",
                        sessionId = "session-1",
                    ),
                )

            assertEquals("session-1", session.id)
            assertEquals(
                listOf("First question.", "First answer.", "Second question.", "Second answer."),
                session.transcript.map { it.text },
            )
            assertEquals("Running agent planner, type exit to exit.", lines.first())
            assertTrue(lines.contains("[planner]: First answer."))
            assertTrue(lines.contains("[planner]: Second answer."))
        }
}
