package dev.adk.kotlin

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RunnerTest {
    @AfterTest
    fun tearDown() {
        LlmRegistry.clearForTests()
    }

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
            assertEquals(3, result.events.size)
            assertEquals("user", result.events[0].author)
            assertEquals("planner", result.events[1].author)
            assertEquals("planner", result.events[2].author)
            assertEquals("Seoul", result.events[1].actions.stateDelta["last_city"])
            assertEquals(true, result.events[2].actions.endOfAgent)
            assertTrue(result.events[2].isFinalResponse())

            val stored = sessionStore.get("trip-planner", "user-1", "session-1")
            assertNotNull(stored)
            assertEquals(3, stored.transcript.size)
            assertEquals(result.events, stored.events)
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
            assertEquals(3, result.events.size)
            assertEquals(result.events[0].invocationId, result.events[1].invocationId)
            assertEquals(result.events[1].invocationId, result.events[2].invocationId)
            assertEquals("coordinator", result.events[1].author)
            assertEquals("researcher", result.events[1].actions.transferToAgent)
            assertEquals("coordinator", result.events[1].branch)
            assertEquals("researcher", result.events[2].author)
            assertEquals("coordinator.researcher", result.events[2].branch)
            assertEquals(true, result.events[2].actions.endOfAgent)
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
                            assertEquals(null, request.outputSchema)
                            assertEquals(null, request.responseMimeType)
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

    @Test
    fun `runner keeps native structured output when model supports schema with tools`() =
        runTest {
            val weatherTool =
                tool(
                    name = "lookup_weather",
                    description = "Resolve current weather for a city.",
                ) {
                    ToolOutput("Seoul is sunny")
                }

            val app =
                adkApp("structured-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        outputSchema {
                            field("city", "Resolved city name")
                            field("summary", "Final weather summary")
                        }
                        tool(weatherTool)
                    }
                }

            val fakeModel =
                object : LanguageModel, SupportsModelCapabilities {
                    override val modelCapabilities =
                        ModelCapabilities(
                            supportsOutputSchemaWithTools = true,
                        )

                    override suspend fun generate(request: ModelRequest): ModelResponse {
                        assertEquals(app.rootAgent.outputSchema, request.outputSchema)
                        assertEquals(ModelRequest.JSON_RESPONSE_MIME_TYPE, request.responseMimeType)
                        assertTrue(request.availableTools.none { it.name == Runner.SET_MODEL_RESPONSE_TOOL })
                        return ModelResponse.Final(
                            message = "Seoul is sunny today.",
                            structuredResponse =
                                mapOf(
                                    "city" to "Seoul",
                                    "summary" to "Seoul is sunny today.",
                                ),
                        )
                    }
                }

            val runner = Runner(app = app, model = fakeModel)

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Summarize the weather in Seoul.",
                )

            assertEquals("Seoul is sunny today.", result.finalMessage)
            assertEquals(
                mapOf("city" to "Seoul", "summary" to "Seoul is sunny today."),
                result.structuredResponse,
            )
            assertEquals("planner", result.finalAgentName)
        }

    @Test
    fun `runner can resolve registry-backed llms and pass generation config`() =
        runTest {
            val weatherTool =
                tool(
                    name = "lookup_weather",
                    description = "Resolve current weather for a city.",
                ) {
                    ToolOutput("Seoul is sunny")
                }

            val app =
                adkApp("structured-app") {
                    rootAgent("planner") {
                        model = "gemini-test"
                        generateContentConfig =
                            generateContentConfig {
                                temperature = 0.2
                                maxOutputTokens = 128
                            }
                        outputSchema {
                            field("city", "Resolved city name")
                            field("summary", "Final weather summary")
                        }
                        tool(weatherTool)
                    }
                }

            val expectedOutputSchema = app.rootAgent.outputSchema

            class FakeRegisteredLlm(
                modelName: String,
            ) : BaseLlm(modelName) {
                override val modelCapabilities =
                    ModelCapabilities(
                        supportsOutputSchemaWithTools = true,
                    )

                override suspend fun generateContent(
                    request: ModelRequest,
                    stream: Boolean,
                ): ModelResponse {
                    assertEquals("gemini-test", request.model)
                    assertEquals(0.2, request.config?.temperature)
                    assertEquals(128, request.config?.maxOutputTokens)
                    assertEquals(expectedOutputSchema, request.outputSchema)
                    assertEquals(ModelRequest.JSON_RESPONSE_MIME_TYPE, request.responseMimeType)
                    assertTrue(request.availableTools.none { it.name == Runner.SET_MODEL_RESPONSE_TOOL })
                    return ModelResponse.Final(
                        message = "Resolved by registered provider.",
                        structuredResponse =
                            mapOf(
                                "city" to "Seoul",
                                "summary" to "Resolved by registered provider.",
                            ),
                    )
                }
            }

            LlmRegistry.clearForTests()
            LlmRegistry.registerLlm("gemini-test", LlmFactory { modelName -> FakeRegisteredLlm(modelName) })

            val runner = Runner(app = app, model = RegistryBackedLanguageModel())

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Summarize the weather in Seoul.",
                )

            assertEquals("Resolved by registered provider.", result.finalMessage)
            assertEquals(
                mapOf("city" to "Seoul", "summary" to "Resolved by registered provider."),
                result.structuredResponse,
            )
        }

    @Test
    fun `tool-saved artifacts are available to later instruction interpolation`() =
        runTest {
            var modelCalls = 0

            val saveKnowledgeTool =
                tool(
                    name = "save_knowledge",
                    description = "Persist knowledge for later turns.",
                ) {
                    saveArtifact(
                        filename = "knowledge.txt",
                        artifact = Artifact("This is my artifact content."),
                    )
                    ToolOutput("saved")
                }

            val app =
                adkApp("knowledge-app") {
                    globalInstruction("Knowledge: {artifact.knowledge.txt?}")
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        tool(saveKnowledgeTool)
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1

                    when (modelCalls) {
                        1 -> {
                            assertTrue(
                                request.systemInstruction
                                    .orEmpty()
                                    .contains("Knowledge: "),
                            )
                            assertTrue(
                                request.systemInstruction
                                    .orEmpty()
                                    .contains("Knowledge: This is my artifact content.")
                                    .not(),
                            )
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall(
                                        toolName = "save_knowledge",
                                    ),
                                ),
                            )
                        }

                        2 -> {
                            assertTrue(
                                request.systemInstruction
                                    .orEmpty()
                                    .contains("Knowledge: This is my artifact content."),
                            )
                            ModelResponse.Final("Used saved artifact.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner = Runner(app = app, model = fakeModel)

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Save and use the knowledge.",
                )

            assertEquals("Used saved artifact.", result.finalMessage)
            assertEquals("planner", result.finalAgentName)
            assertEquals("save_knowledge", result.toolExecutions.single().call.toolName)
            assertEquals(listOf("knowledge.txt"), runner.artifactService.listArtifactKeys("knowledge-app", "user-1", "session-1"))
            assertEquals(
                Artifact("This is my artifact content.", version = 0),
                runner.artifactService.loadArtifact("knowledge-app", "user-1", "session-1", "knowledge.txt"),
            )
        }

    @Test
    fun `runner emits artifact deltas in tool events`() =
        runTest {
            var modelCalls = 0

            val persistArtifactTool =
                tool(
                    name = "persist_knowledge",
                    description = "Persist reusable context.",
                ) {
                    remember("last_saved", "knowledge.txt")
                    saveArtifact(
                        filename = "knowledge.txt",
                        artifact = Artifact("Stored context"),
                    )
                    ToolOutput("saved")
                }

            val app =
                adkApp("knowledge-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        tool(persistArtifactTool)
                    }
                }

            val fakeModel =
                LanguageModel {
                    modelCalls += 1
                    when (modelCalls) {
                        1 ->
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall("persist_knowledge"),
                                ),
                            )

                        2 -> ModelResponse.Final("Stored the context.")
                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner = Runner(app = app, model = fakeModel)

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Store this context.",
                )

            assertEquals("Stored the context.", result.finalMessage)
            assertEquals(3, result.events.size)
            assertEquals(
                mapOf("last_saved" to "knowledge.txt"),
                result.events[1].actions.stateDelta,
            )
            assertEquals(
                mapOf("knowledge.txt" to 0),
                result.events[1].actions.artifactDelta,
            )
            assertEquals(
                mapOf("last_saved" to "knowledge.txt"),
                result.events.last().actions.agentState,
            )
        }

    @Test
    fun `plugins can short-circuit model calls and rewrite final events`() =
        runTest {
            var modelCalls = 0
            var afterRunCalled = false

            val plugin =
                object : BasePlugin("cached-response") {
                    override suspend fun beforeModelCallback(
                        callbackContext: CallbackContext,
                        llmRequest: LlmRequest,
                    ): LlmResponse? = ModelResponse.Final("Cached answer.")

                    override suspend fun onEventCallback(
                        invocationContext: InvocationContext,
                        event: Event,
                    ): Event? =
                        if (event.actions.endOfAgent == true) {
                            event.copy(content = ModelMessage("Cached answer. [plugin]"))
                        } else {
                            null
                        }

                    override suspend fun afterRunCallback(
                        invocationContext: InvocationContext,
                        runResult: RunResult,
                    ) {
                        afterRunCalled = true
                    }
                }

            val app =
                adkApp("plugin-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }

            val fakeModel =
                LanguageModel {
                    modelCalls += 1
                    ModelResponse.Final("Model should not run.")
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    plugins = listOf(plugin),
                )

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Use the cache.",
                )

            assertEquals(0, modelCalls)
            assertEquals("Cached answer. [plugin]", result.finalMessage)
            assertEquals("planner", result.finalAgentName)
            assertEquals("Cached answer. [plugin]", result.events.last().content?.text)
            assertTrue(afterRunCalled)
        }

    @Test
    fun `plugins can short-circuit tool execution`() =
        runTest {
            var toolExecutions = 0
            var modelCalls = 0

            val weatherTool =
                tool(
                    name = "lookup_weather",
                    description = "Resolve current weather for a city.",
                ) {
                    toolExecutions += 1
                    ToolOutput("tool execution should be skipped")
                }

            val plugin =
                object : BasePlugin("tool-cache") {
                    override suspend fun beforeToolCallback(
                        tool: Tool,
                        toolCall: ToolCall,
                        toolContext: ToolContext,
                    ): ToolOutput? = ToolOutput("plugin supplied weather")
                }

            val app =
                adkApp("plugin-app") {
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
                                    ToolCall("lookup_weather"),
                                ),
                            )

                        2 -> ModelResponse.Final("Done.")
                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    plugins = listOf(plugin),
                )

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Use plugin tool cache.",
                )

            assertEquals(0, toolExecutions)
            assertEquals("plugin supplied weather", result.toolExecutions.single().output.content)
            assertEquals("plugin supplied weather", result.events[1].content?.text)
            assertEquals("Done.", result.finalMessage)
        }
}
