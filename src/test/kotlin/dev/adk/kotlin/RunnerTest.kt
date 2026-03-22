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
            assertEquals("planner", result.finalAgentName)
            assertEquals(null, result.structuredResponse)
            assertEquals(1, result.toolExecutions.size)
            assertEquals("planner", result.toolExecutions.single().agentName)
            assertEquals("Seoul", result.session.state["last_city"])

            val stored = sessionStore.get("trip-planner", "user-1", "session-1")
            assertNotNull(stored)
            assertEquals(3, stored.transcript.size)
            assertTrue(stored.transcript[0] is UserMessage)
            assertTrue(stored.transcript[1] is ToolMessage)
            assertTrue(stored.transcript[2] is ModelMessage)
        }

    @Test
    fun `runner transfers control to a sub-agent`() =
        runTest {
            var modelCalls = 0

            val app =
                adkApp("research-app") {
                    rootAgent("coordinator") {
                        model = "gemini-2.5-pro"
                        description = "Routes work to the best specialist."
                        subAgent("researcher") {
                            model = "gemini-2.5-flash"
                            description = "Handles research-heavy questions."
                            instruction("Provide a researched answer.")
                        }
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1

                    when (modelCalls) {
                        1 -> {
                            assertEquals("coordinator", request.agent.name)
                            assertTrue(request.systemInstruction.orEmpty().contains("transfer_to_agent"))
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall(
                                        toolName = Runner.TRANSFER_TO_AGENT_TOOL,
                                        arguments = mapOf("agent_name" to "researcher"),
                                    ),
                                ),
                            )
                        }

                        2 -> {
                            assertEquals("researcher", request.agent.name)
                            assertTrue(
                                request.systemInstruction
                                    .orEmpty()
                                    .contains("Your internal name is \"researcher\""),
                            )
                            ModelResponse.Final("Handled by researcher.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner = Runner(app = app, model = fakeModel)

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Research the neighborhood.",
                )

            assertEquals("Handled by researcher.", result.finalMessage)
            assertEquals("researcher", result.finalAgentName)
            assertEquals(Runner.TRANSFER_TO_AGENT_TOOL, result.toolExecutions.single().call.toolName)
            assertEquals("coordinator", result.toolExecutions.single().agentName)
        }

    @Test
    fun `runner treats set_model_response as the final structured result`() =
        runTest {
            var modelCalls = 0

            val weatherTool =
                tool(
                    name = "lookup_weather",
                    description = "Resolve current weather for a city.",
                ) { call ->
                    val city = call.requireArgument("city")
                    ToolOutput("$city is sunny")
                }

            val app =
                adkApp("structured-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        instruction("Use tools before answering.")
                        outputSchema {
                            field("city", "Resolved city name")
                            field("summary", "Final weather summary")
                        }
                        tool(weatherTool)
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1

                    when (modelCalls) {
                        1 -> {
                            assertTrue(request.availableTools.any { it.name == Runner.SET_MODEL_RESPONSE_TOOL })
                            assertTrue(
                                request.systemInstruction
                                    .orEmpty()
                                    .contains(PromptAssembler.SET_MODEL_RESPONSE_INSTRUCTION),
                            )
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
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall(
                                        toolName = Runner.SET_MODEL_RESPONSE_TOOL,
                                        arguments =
                                            mapOf(
                                                "city" to "Seoul",
                                                "summary" to "Seoul is sunny today.",
                                            ),
                                    ),
                                ),
                            )
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner = Runner(app = app, model = fakeModel)

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Summarize the weather in Seoul.",
                )

            assertEquals("planner", result.finalAgentName)
            assertEquals(
                mapOf("city" to "Seoul", "summary" to "Seoul is sunny today."),
                result.structuredResponse,
            )
            assertEquals(
                "{city=Seoul, summary=Seoul is sunny today.}",
                result.finalMessage,
            )
            assertEquals(Runner.SET_MODEL_RESPONSE_TOOL, result.toolExecutions.last().call.toolName)
        }

    @Test
    fun `runner resolves artifact placeholders through its artifact service`() =
        runTest {
            val artifactService = InMemoryArtifactService()
            artifactService.saveArtifact(
                appName = "knowledge-app",
                userId = "user-1",
                sessionId = "session-1",
                filename = "knowledge.txt",
                artifact = Artifact("This is my artifact content."),
            )

            val app =
                adkApp("knowledge-app") {
                    globalInstruction("Knowledge: {artifact.knowledge.txt}")
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    assertTrue(
                        request.systemInstruction
                            .orEmpty()
                            .contains("Knowledge: This is my artifact content."),
                    )
                    ModelResponse.Final("Used artifact-backed instructions.")
                }

            val runner = Runner(app = app, model = fakeModel, artifactService = artifactService)

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Use the knowledge.",
                )

            assertEquals("Used artifact-backed instructions.", result.finalMessage)
            assertEquals("planner", result.finalAgentName)
        }
}
