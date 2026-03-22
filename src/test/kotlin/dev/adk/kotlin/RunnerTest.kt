package dev.adk.kotlin

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RunnerTest {
    @Test
    fun `runner executes tools before returning the final answer`() =
        runTest {
            var modelCalls = 0

            val weatherTool =
                tool(
                    name = "lookup_weather",
                    description = "Resolve current weather for a city.",
                ) { call ->
                    val city = call.requireArgument("city")
                    remember("last_city", city)
                    ToolOutput("$city is sunny")
                }

            val app =
                adkApp("trip-planner") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        maxIterations = 4
                        instruction("Use tools before answering.")
                        tool(weatherTool)
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1

                    when (modelCalls) {
                        1 -> {
                            assertTrue(request.session.state.isEmpty())
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall(
                                        toolName = "lookup_weather",
                                        arguments = mapOf("city" to "Seoul"),
                                    ),
                                ),
                            )
                        }

                        2 -> {
                            assertEquals("Seoul", request.session.state["last_city"])
                            assertTrue(request.session.transcript.last() is ToolMessage)
                            ModelResponse.Final("Seoul is sunny today.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val sessionStore = InMemorySessionStore()
            val runner = Runner(app = app, model = fakeModel, sessionStore = sessionStore)

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "What is the weather in Seoul?",
                )

            assertEquals("Seoul is sunny today.", result.finalMessage)
            assertEquals(1, result.toolExecutions.size)
            assertEquals("Seoul", result.session.state["last_city"])

            val stored = sessionStore.get("trip-planner", "user-1", "session-1")
            assertNotNull(stored)
            assertEquals(3, stored.transcript.size)
            assertTrue(stored.transcript[0] is UserMessage)
            assertTrue(stored.transcript[1] is ToolMessage)
            assertTrue(stored.transcript[2] is ModelMessage)
        }
}
