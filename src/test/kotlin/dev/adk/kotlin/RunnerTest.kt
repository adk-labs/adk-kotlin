package dev.adk.kotlin

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
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

    @Test
    fun `runner executes sequential agents in order`() =
        runTest {
            var modelCalls = 0

            val researcher =
                agent("researcher") {
                    model = "gemini-2.5-flash"
                    instruction("Research the request.")
                }

            val reviewer =
                agent("reviewer") {
                    model = "gemini-2.5-pro"
                    instruction("Review the result.")
                }

            val app =
                adkApp("research-app") {
                    rootAgent(
                        sequentialAgent("pipeline") {
                            description = "Runs specialists in order."
                            subAgents(researcher, reviewer)
                        },
                    )
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1
                    when (modelCalls) {
                        1 -> {
                            assertEquals("researcher", request.agent.name)
                            assertEquals(1, request.conversation.size)
                            ModelResponse.Final("Research draft.")
                        }

                        2 -> {
                            assertEquals("reviewer", request.agent.name)
                            assertEquals("Research draft.", request.session.transcript.last().text)
                            ModelResponse.Final("Reviewed final answer.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner = Runner(app = app, model = fakeModel)

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Research and review this itinerary.",
                )

            assertEquals("Reviewed final answer.", result.finalMessage)
            assertEquals("reviewer", result.finalAgentName)
            assertEquals(3, result.session.transcript.size)
            assertEquals(
                listOf("user", "researcher", "reviewer"),
                result.events.map { it.author },
            )
        }

    @Test
    fun `runner executes loop agents until exit_loop and keeps the last meaningful result`() =
        runTest {
            var modelCalls = 0

            val worker =
                agent("worker") {
                    model = "gemini-2.5-flash"
                    instruction("Iterate until the task is complete.")
                }

            val app =
                adkApp("loop-app") {
                    rootAgent(
                        loopAgent("orchestrator") {
                            description = "Repeats work until complete."
                            maxIterations = 3
                            subAgents(worker)
                        },
                    )
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1
                    when (modelCalls) {
                        1 -> {
                            assertEquals("worker", request.agent.name)
                            assertTrue(request.availableTools.any { it.name == Runner.EXIT_LOOP_TOOL })
                            assertTrue(
                                request.systemInstruction
                                    .orEmpty()
                                    .contains(PromptAssembler.EXIT_LOOP_INSTRUCTION),
                            )
                            ModelResponse.Final("Draft result.")
                        }

                        2 -> {
                            assertEquals("worker", request.agent.name)
                            assertEquals("Draft result.", request.session.transcript.last().text)
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall(Runner.EXIT_LOOP_TOOL),
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
                    input = "Loop until done.",
                )

            assertEquals("Draft result.", result.finalMessage)
            assertEquals("worker", result.finalAgentName)
            assertEquals(Runner.EXIT_LOOP_TOOL, result.toolExecutions.last().call.toolName)
            assertEquals(true, result.events.last().actions.escalate)
        }

    @Test
    fun `runner executes code blocks through unsafe local code executor and continues the loop`() =
        runTest {
            var modelCalls = 0

            val app =
                adkApp("code-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        codeExecutor = unsafeLocalCodeExecutor(timeoutSeconds = 5)
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1
                    when (modelCalls) {
                        1 -> {
                            assertEquals(1, request.conversation.size)
                            ModelResponse.Final(
                                """
                                Let me calculate that.
                                ```python
                                print(2 + 2)
                                ```
                                """.trimIndent(),
                            )
                        }

                        2 -> {
                            assertEquals("```tool_output\nCode execution result:\n4\n```", request.session.transcript.last().text)
                            ModelResponse.Final("The answer is 4.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner = Runner(app = app, model = fakeModel)

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "What is 2 + 2?",
                )

            assertEquals("The answer is 4.", result.finalMessage)
            assertEquals(4, result.events.size)
            assertEquals("Let me calculate that.", result.events[1].content?.text)
            assertEquals("```tool_output\nCode execution result:\n4\n```", result.events[2].content?.text)
            assertTrue(result.toolExecutions.isEmpty())
        }

    @Test
    fun `runner retries after code execution failure and exposes built in code execution markers`() =
        runTest {
            var localModelCalls = 0

            val localApp =
                adkApp("code-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        codeExecutor =
                            unsafeLocalCodeExecutor(
                                timeoutSeconds = 5,
                                errorRetryAttempts = 2,
                            )
                    }
                }

            val localModel =
                LanguageModel { request ->
                    localModelCalls += 1
                    when (localModelCalls) {
                        1 ->
                            ModelResponse.Final(
                                """
                                ```python
                                raise ValueError("boom")
                                ```
                                """.trimIndent(),
                            )

                        2 -> {
                            assertTrue(request.session.transcript.last().text.contains("boom"))
                            ModelResponse.Final(
                                """
                                ```python
                                print(6 * 7)
                                ```
                                """.trimIndent(),
                            )
                        }

                        3 -> {
                            assertTrue(request.session.transcript.last().text.contains("42"))
                            ModelResponse.Final("Recovered with 42.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val localRunner = Runner(app = localApp, model = localModel)
            val localResult =
                localRunner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Recover from a failing script.",
                )

            assertEquals("Recovered with 42.", localResult.finalMessage)
            assertEquals(4, localResult.events.size)
            assertTrue(localResult.events[1].content?.text.orEmpty().contains("boom"))
            assertTrue(localResult.events[2].content?.text.orEmpty().contains("42"))

            val builtInApp =
                adkApp("built-in-code-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        codeExecutor = builtInCodeExecutor()
                    }
                }

            val builtInModel =
                LanguageModel { request ->
                    assertTrue(request.availableTools.any { tool -> tool.name == BaseCodeExecutor.BUILT_IN_TOOL_NAME })
                    ModelResponse.Final("Built-in executor request prepared.")
                }

            val builtInRunner = Runner(app = builtInApp, model = builtInModel)
            val builtInResult =
                builtInRunner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Use native code execution.",
                )

            assertEquals("Built-in executor request prepared.", builtInResult.finalMessage)
        }

    @Test
    fun `runner requests confirmation for protected tools and skips execution without approval`() =
        runTest {
            var modelCalls = 0
            var toolRuns = 0

            val dangerousTool =
                tool(
                    name = "delete_files",
                    description = "Deletes files from disk.",
                    requiresConfirmation = true,
                    confirmationHint = "Confirm file deletion before continuing.",
                ) {
                    toolRuns += 1
                    remember("deleted", "true")
                    ToolOutput("deleted")
                }

            val app =
                adkApp("confirm-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        tool(dangerousTool)
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1
                    when (modelCalls) {
                        1 ->
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall("delete_files"),
                                ),
                            )

                        2 -> {
                            assertEquals("Tool delete_files requires confirmation.", request.session.transcript.last().text)
                            ModelResponse.Final("Waiting for confirmation.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner = Runner(app = app, model = fakeModel)
            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Delete the files.",
                )

            assertEquals(0, toolRuns)
            assertEquals("Waiting for confirmation.", result.finalMessage)
            assertEquals("pending", result.toolExecutions.single().output.metadata["confirmation"])
            assertEquals(
                "Confirm file deletion before continuing.",
                result.events[1].actions.requestedToolConfirmations.values.single().hint,
            )
            assertEquals(false, result.events[1].actions.requestedToolConfirmations.values.single().confirmed)
        }

    @Test
    fun `runner executes confirmation-protected tools when handler approves`() =
        runTest {
            var modelCalls = 0

            val dangerousTool =
                tool(
                    name = "delete_files",
                    description = "Deletes files from disk.",
                    requiresConfirmation = true,
                    confirmationHint = "Confirm file deletion before continuing.",
                ) {
                    assertEquals(true, toolConfirmation?.confirmed)
                    remember("deleted", "true")
                    ToolOutput("deleted")
                }

            val app =
                adkApp("confirm-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        tool(dangerousTool)
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1
                    when (modelCalls) {
                        1 ->
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall("delete_files"),
                                ),
                            )

                        2 -> {
                            assertEquals("true", request.session.state["deleted"])
                            ModelResponse.Final("Deletion completed.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    toolConfirmationHandler =
                        ToolConfirmationHandler { request ->
                            request.suggestedConfirmation.copy(
                                confirmed = true,
                                payload = mapOf("approvedBy" to "system"),
                            )
                        },
                )

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Delete the files.",
                )

            assertEquals("Deletion completed.", result.finalMessage)
            assertEquals(4, result.events.size)
            assertEquals(true, result.events[1].actions.requestedToolConfirmations.values.single().confirmed)
            assertEquals("deleted", result.toolExecutions.single().output.content)
        }

    @Test
    fun `tool context can request credentials and emit auth configs on events`() =
        runTest {
            var modelCalls = 0

            val authConfig =
                AuthConfig(
                    authScheme = "api_key",
                    rawAuthCredential = AuthCredential(apiKey = ""),
                    credentialKey = "maps_api",
                )

            val authTool =
                tool(
                    name = "call_maps_api",
                    description = "Calls an authenticated maps API.",
                ) {
                    requestCredential(authConfig)
                    ToolOutput("Authentication required.")
                }

            val app =
                adkApp("auth-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        tool(authTool)
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1
                    when (modelCalls) {
                        1 ->
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall("call_maps_api"),
                                ),
                            )

                        2 -> {
                            assertEquals("Authentication required.", request.session.transcript.last().text)
                            ModelResponse.Final("Waiting for auth.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner = Runner(app = app, model = fakeModel)
            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Call the maps API.",
                )

            assertEquals("Waiting for auth.", result.finalMessage)
            assertEquals("maps_api", result.events[1].actions.requestedAuthConfigs.values.single().credentialKey)
            assertEquals("api_key", result.events[1].actions.requestedAuthConfigs.values.single().authScheme)
        }

    @Test
    fun `tool context can load stored credentials and auth responses`() =
        runTest {
            var modelCalls = 0

            val authConfig =
                AuthConfig(
                    authScheme = "api_key",
                    credentialKey = "maps_api",
                )
            val credentialService = InMemoryCredentialService()
            credentialService.saveCredential(
                authConfig = authConfig,
                appName = "auth-app",
                userId = "user-1",
                credential = AuthCredential(apiKey = "secret-key"),
            )

            val sessionStore = InMemorySessionStore()
            sessionStore.save(
                "auth-app",
                AgentSession(
                    id = "session-1",
                    userId = "user-1",
                    state =
                        mapOf(
                            "temp:maps_api" to AuthCredential(accessToken = "token-123").encodeToState(),
                        ),
                ),
            )

            val authTool =
                tool(
                    name = "use_maps_api",
                    description = "Uses stored credentials.",
                ) {
                    val storedCredential = loadCredential(authConfig)
                    val authResponse = getAuthResponse(authConfig)
                    remember("loaded_api_key", storedCredential?.apiKey)
                    remember("loaded_access_token", authResponse?.accessToken)
                    ToolOutput("${storedCredential?.apiKey}:${authResponse?.accessToken}")
                }

            val app =
                adkApp("auth-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        tool(authTool)
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1
                    when (modelCalls) {
                        1 ->
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall("use_maps_api"),
                                ),
                            )

                        2 -> {
                            assertEquals("secret-key", request.session.state["loaded_api_key"])
                            assertEquals("token-123", request.session.state["loaded_access_token"])
                            ModelResponse.Final("Loaded credentials.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    sessionStore = sessionStore,
                    credentialService = credentialService,
                )
            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Use stored auth.",
                )

            assertEquals("Loaded credentials.", result.finalMessage)
            assertEquals("secret-key:token-123", result.events[1].content?.text)
        }

    @Test
    fun `tool context can request confirmation directly during tool execution`() =
        runTest {
            var modelCalls = 0

            val toolThatRequestsConfirmation =
                tool(
                    name = "publish_report",
                    description = "Publishes a report to users.",
                ) {
                    requestConfirmation(
                        hint = "Confirm publishing the report to all users.",
                        payload = mapOf("audience" to "all"),
                    )
                    ToolOutput("Publish confirmation requested.")
                }

            val app =
                adkApp("tool-confirmation-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        tool(toolThatRequestsConfirmation)
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1
                    when (modelCalls) {
                        1 ->
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall("publish_report"),
                                ),
                            )

                        2 -> {
                            assertEquals("Publish confirmation requested.", request.session.transcript.last().text)
                            ModelResponse.Final("Waiting on publish confirmation.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner = Runner(app = app, model = fakeModel)
            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Publish the report.",
                )

            assertEquals("Waiting on publish confirmation.", result.finalMessage)
            assertEquals(
                "Confirm publishing the report to all users.",
                result.events[1].actions.requestedToolConfirmations.values.single().hint,
            )
            assertEquals(
                false,
                result.events[1].actions.requestedToolConfirmations.values.single().confirmed,
            )
        }

    @Test
    fun `authenticated tools request credentials until auth is available`() =
        runTest {
            var modelCalls = 0

            val authConfig =
                AuthConfig(
                    authScheme = "api_key",
                    credentialKey = "weather_api",
                )

            val weatherTool =
                authenticatedTool(
                    name = "lookup_weather",
                    description = "Calls an authenticated weather API.",
                    authConfig = authConfig,
                    responseForAuthRequired = "Pending User Authorization.",
                ) { _, credential ->
                    remember("last_api_key", credential?.apiKey)
                    ToolOutput("authorized:${credential?.apiKey}")
                }

            val app =
                adkApp("auth-tool-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        tool(weatherTool)
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1
                    when (modelCalls) {
                        1 ->
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall("lookup_weather"),
                                ),
                            )

                        2 -> {
                            assertEquals("Pending User Authorization.", request.session.transcript.last().text)
                            ModelResponse.Final("Waiting for auth.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner = Runner(app = app, model = fakeModel)
            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Check the weather.",
                )

            assertEquals("Waiting for auth.", result.finalMessage)
            assertEquals("weather_api", result.events[1].actions.requestedAuthConfigs.values.single().credentialKey)
        }

    @Test
    fun `authenticated tools can run with loaded credentials and toolsets expand into requests`() =
        runTest {
            var modelCalls = 0

            val authConfig =
                AuthConfig(
                    authScheme = "api_key",
                    credentialKey = "weather_api",
                )
            val credentialService = InMemoryCredentialService()
            credentialService.saveCredential(
                authConfig = authConfig,
                appName = "toolset-app",
                userId = "user-1",
                credential = AuthCredential(apiKey = "secret-weather-key"),
            )

            val authenticatedWeatherTool =
                authenticatedTool(
                    name = "lookup_weather",
                    description = "Calls an authenticated weather API.",
                    authConfig = authConfig,
                ) { _, credential ->
                    remember("toolset_api_key", credential?.apiKey)
                    ToolOutput("authorized:${credential?.apiKey}")
                }

            val demoToolset =
                object : BaseToolset(toolNamePrefix = "demo") {
                    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<Tool> =
                        listOf(authenticatedWeatherTool)
                }

            val app =
                adkApp("toolset-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        toolset(demoToolset)
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1
                    when (modelCalls) {
                        1 -> {
                            assertTrue(request.availableTools.any { tool -> tool.name == "demo_lookup_weather" })
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall("demo_lookup_weather"),
                                ),
                            )
                        }

                        2 -> {
                            assertEquals("secret-weather-key", request.session.state["toolset_api_key"])
                            ModelResponse.Final("Toolset weather lookup completed.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    credentialService = credentialService,
                )
            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Check the weather.",
                )

            assertEquals("Toolset weather lookup completed.", result.finalMessage)
            assertEquals("authorized:secret-weather-key", result.events[1].content?.text)
        }

    @Test
    fun `runner executes parallel agents in isolated branches and merges by completion order`() =
        runTest {
            val callCounts = mutableMapOf<String, Int>()

            val rememberNote =
                tool(
                    name = "remember_note",
                    description = "Remember a branch-local note.",
                ) { call ->
                    val value = call.requireArgument("value")
                    state["note"] = value
                    ToolOutput("remembered $value")
                }

            val fast =
                agent("fast") {
                    model = "gemini-2.5-flash"
                    instruction("Produce a fast attempt.")
                    tool(rememberNote)
                }

            val slow =
                agent("slow") {
                    model = "gemini-2.5-pro"
                    instruction("Produce a slower attempt.")
                    tool(rememberNote)
                }

            val app =
                adkApp("parallel-app") {
                    rootAgent(
                        parallelAgent("fan_out") {
                            description = "Runs workers in parallel."
                            subAgents(fast, slow)
                        },
                    )
                }

            val fakeModel =
                LanguageModel { request ->
                    val count = callCounts.merge(request.agent.name, 1, Int::plus) ?: 1

                    when (request.agent.name to count) {
                        "fast" to 1 -> {
                            assertEquals(1, request.conversation.size)
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall(
                                        toolName = "remember_note",
                                        arguments = mapOf("value" to "fast"),
                                    ),
                                ),
                            )
                        }

                        "fast" to 2 -> {
                            assertEquals("fast", request.session.state["note"])
                            ModelResponse.Final("Fast done.")
                        }

                        "slow" to 1 -> {
                            delay(50)
                            assertEquals(1, request.conversation.size)
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall(
                                        toolName = "remember_note",
                                        arguments = mapOf("value" to "slow"),
                                    ),
                                ),
                            )
                        }

                        "slow" to 2 -> {
                            assertEquals("slow", request.session.state["note"])
                            ModelResponse.Final("Slow done.")
                        }

                        else -> error("Unexpected model invocation for ${request.agent.name}#${count}.")
                    }
                }

            val runner = Runner(app = app, model = fakeModel)

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Run both strategies.",
                )

            assertEquals("Slow done.", result.finalMessage)
            assertEquals("slow", result.finalAgentName)
            assertEquals(emptyMap(), result.session.state)
            assertEquals(
                listOf("user", "fast", "fast", "slow", "slow"),
                result.events.map { it.author },
            )
            assertEquals(
                listOf("remember_note", "remember_note"),
                result.toolExecutions.map { it.call.toolName },
            )
            assertEquals(
                listOf("fan_out.fast", "fan_out.fast", "fan_out.slow", "fan_out.slow"),
                result.events.drop(1).map { it.branch },
            )
        }

    @Test
    fun `runner applies built in planner thinking config over generate content config`() =
        runTest {
            val app =
                adkApp("planner-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        generateContentConfig =
                            generateContentConfig {
                                temperature = 0.2
                                thinkingConfig {
                                    includeThoughts = false
                                    thinkingBudget = 16
                                }
                            }
                        planner =
                            builtInPlanner {
                                includeThoughts = true
                                thinkingBudget = 128
                            }
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    assertEquals(0.2, request.config?.temperature)
                    assertEquals(true, request.config?.thinkingConfig?.includeThoughts)
                    assertEquals(128, request.config?.thinkingConfig?.thinkingBudget)
                    ModelResponse.Final("Planned with model-native thinking.")
                }

            val runner = Runner(app = app, model = fakeModel)

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Think before answering.",
                )

            assertEquals("Planned with model-native thinking.", result.finalMessage)
        }

    @Test
    fun `runner injects plan react instructions and extracts final answer tags`() =
        runTest {
            val app =
                adkApp("planner-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        planner = planReActPlanner()
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    assertTrue(
                        request.systemInstruction
                            .orEmpty()
                            .contains(PlanReActPlanner.PLANNING_TAG),
                    )
                    assertTrue(
                        request.systemInstruction
                            .orEmpty()
                            .contains(PlanReActPlanner.FINAL_ANSWER_TAG),
                    )
                    ModelResponse.Final(
                        """
                        ${PlanReActPlanner.PLANNING_TAG}
                        1. Gather context.

                        ${PlanReActPlanner.FINAL_ANSWER_TAG}
                        Final concise answer.
                        """.trimIndent(),
                    )
                }

            val runner = Runner(app = app, model = fakeModel)

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Plan then answer.",
                )

            assertEquals("Final concise answer.", result.finalMessage)
        }

    @Test
    fun `runner persists final output into output key`() =
        runTest {
            val app =
                adkApp("planner-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        outputKey = "planner_output"
                    }
                }

            val fakeModel =
                LanguageModel {
                    ModelResponse.Final("Persist me.")
                }

            val runner = Runner(app = app, model = fakeModel)

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Answer and persist.",
                )

            assertEquals("Persist me.", result.finalMessage)
            assertEquals("Persist me.", result.session.state["planner_output"])
            assertEquals("Persist me.", result.events.last().actions.agentState?.get("planner_output"))
        }

    @Test
    fun `runner executes wrapped agent tools with schema and state forwarding`() =
        runTest {
            val callCounts = mutableMapOf<String, Int>()

            val researcher =
                agent("researcher") {
                    model = "gemini-2.5-flash"
                    description = "Research specialist."
                    inputSchema {
                        string("topic", description = "Topic to research")
                    }
                    outputKey = "last_topic_summary"
                }

            val app =
                adkApp("research-app") {
                    rootAgent("coordinator") {
                        model = "gemini-2.5-pro"
                        tool(agentTool(researcher))
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    val count = callCounts.merge(request.agent.name, 1, Int::plus) ?: 1

                    when (request.agent.name to count) {
                        "coordinator" to 1 -> {
                            val wrappedTool = request.availableTools.single()
                            assertEquals("researcher", wrappedTool.name)
                            assertEquals(ToolSchemaType.OBJECT, wrappedTool.effectiveJsonSchema?.type)
                            assertEquals(listOf("topic"), wrappedTool.effectiveJsonSchema?.required)
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall(
                                        toolName = "researcher",
                                        arguments = mapOf("topic" to "Seoul"),
                                    ),
                                ),
                            )
                        }

                        "researcher" to 1 -> {
                            assertEquals("""{"topic":"Seoul"}""", request.conversation.single().text)
                            ModelResponse.Final("Research summary.")
                        }

                        "coordinator" to 2 -> {
                            assertEquals("Research summary.", request.session.transcript.last().text)
                            assertEquals("Research summary.", request.session.state["last_topic_summary"])
                            ModelResponse.Final("Coordinator done.")
                        }

                        else -> error("Unexpected model invocation for ${request.agent.name}#${count}.")
                    }
                }

            val runner = Runner(app = app, model = fakeModel)

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Research Seoul.",
                )

            assertEquals("Coordinator done.", result.finalMessage)
            assertEquals("Research summary.", result.session.state["last_topic_summary"])
            assertEquals("researcher", result.toolExecutions.single().call.toolName)
            assertEquals("Research summary.", result.toolExecutions.single().output.content)
        }

    @Test
    fun `load memory tool appends instructions and exposes matching memories`() =
        runTest {
            val memoryService = InMemoryMemoryService()
            memoryService.addMemory(
                appName = "memory-app",
                userId = "user-1",
                memories =
                    listOf(
                        MemoryEntry(
                            text = "User likes ramen in Seoul.",
                            appName = "memory-app",
                            userId = "user-1",
                        ),
                    ),
            )

            var modelCalls = 0
            val app =
                adkApp("memory-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        tool(loadMemory)
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1
                    when (modelCalls) {
                        1 -> {
                            assertTrue(request.availableTools.any { tool -> tool.name == "load_memory" })
                            assertTrue(
                                request.systemInstruction
                                    .orEmpty()
                                    .contains("call load_memory function with a query"),
                            )
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall(
                                        toolName = "load_memory",
                                        arguments = mapOf("query" to "ramen"),
                                    ),
                                ),
                            )
                        }

                        2 -> {
                            assertTrue(request.session.transcript.last().text.contains("User likes ramen in Seoul."))
                            ModelResponse.Final("Used loaded memory.")
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
                    input = "Find my food preference.",
                )

            assertEquals("Used loaded memory.", result.finalMessage)
            assertEquals("load_memory", result.toolExecutions.single().call.toolName)
            assertTrue(result.toolExecutions.single().output.content.contains("User likes ramen in Seoul."))
        }

    @Test
    fun `preload memory tool injects past conversations and stays hidden from the model`() =
        runTest {
            val memoryService = InMemoryMemoryService()
            memoryService.addMemory(
                appName = "memory-app",
                userId = "user-1",
                memories =
                    listOf(
                        MemoryEntry(
                            text = "User prefers aisle seats on long flights.",
                            appName = "memory-app",
                            userId = "user-1",
                        ),
                    ),
            )

            val app =
                adkApp("memory-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        tool(preloadMemory)
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    assertTrue(request.availableTools.none { tool -> tool.name == "preload_memory" })
                    assertTrue(request.systemInstruction.orEmpty().contains("<PAST_CONVERSATIONS>"))
                    assertTrue(request.systemInstruction.orEmpty().contains("User prefers aisle seats on long flights."))
                    ModelResponse.Final("Used preloaded memory.")
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
                    input = "aisle seats",
                )

            assertEquals("Used preloaded memory.", result.finalMessage)
        }

    @Test
    fun `load artifacts tool advertises artifacts and loads user scoped content`() =
        runTest {
            val artifactService = InMemoryArtifactService()
            artifactService.saveArtifact(
                appName = "artifact-app",
                userId = "user-1",
                sessionId = null,
                filename = "report.txt",
                artifact = Artifact("Quarterly summary", mimeType = "application/pdf"),
            )

            var modelCalls = 0
            val app =
                adkApp("artifact-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        tool(loadArtifacts)
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1
                    when (modelCalls) {
                        1 -> {
                            assertTrue(request.availableTools.any { tool -> tool.name == "load_artifacts" })
                            assertTrue(request.systemInstruction.orEmpty().contains("You have a list of artifacts:"))
                            assertTrue(request.systemInstruction.orEmpty().contains("report.txt"))
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall(
                                        toolName = "load_artifacts",
                                        arguments = mapOf("artifact_names" to listOf("report.txt")),
                                    ),
                                ),
                            )
                        }

                        2 -> {
                            assertTrue(request.session.transcript.last().text.contains("Artifact report.txt is:"))
                            assertTrue(request.session.transcript.last().text.contains("Quarterly summary"))
                            assertEquals(1, request.session.transcript.last().attachments.size)
                            assertEquals("report.txt", request.session.transcript.last().attachments.single().filename)
                            assertEquals("application/pdf", request.session.transcript.last().attachments.single().mimeType)
                            ModelResponse.Final("Loaded artifact content.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    artifactService = artifactService,
                )

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Open the report.",
                )

            assertEquals("Loaded artifact content.", result.finalMessage)
            assertEquals("load_artifacts", result.toolExecutions.single().call.toolName)
            assertTrue(result.toolExecutions.single().output.content.contains("Quarterly summary"))
            assertEquals(1, result.toolExecutions.single().output.attachments.size)
            assertEquals("application/pdf", result.toolExecutions.single().output.attachments.single().mimeType)
        }

    @Test
    fun `plugins can rewrite llm requests before model execution`() =
        runTest {
            val firstPlugin =
                object : BasePlugin("first_plugin") {
                    override suspend fun processLlmRequest(
                        callbackContext: CallbackContext,
                        llmRequest: LlmRequest,
                    ): LlmRequest =
                        llmRequest.copy(
                            systemInstructions = llmRequest.systemInstructions + "first_plugin_instruction",
                        )
                }

            val secondPlugin =
                object : BasePlugin("second_plugin") {
                    override suspend fun processLlmRequest(
                        callbackContext: CallbackContext,
                        llmRequest: LlmRequest,
                    ): LlmRequest =
                        llmRequest.copy(
                            systemInstructions = llmRequest.systemInstructions + "second_plugin_instruction",
                        )
                }

            val app =
                adkApp("plugin-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    assertEquals(
                        listOf("first_plugin_instruction", "second_plugin_instruction"),
                        request.systemInstructions.takeLast(2),
                    )
                    ModelResponse.Final("Mutated request.")
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    plugins = listOf(firstPlugin, secondPlugin),
                )

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Mutate this request.",
                )

            assertEquals("Mutated request.", result.finalMessage)
        }

    @Test
    fun `global instruction plugin prepends injected instructions`() =
        runTest {
            val sessionStore = InMemorySessionStore()
            sessionStore.save(
                "plugin-app",
                AgentSession(
                    id = "session-1",
                    userId = "user-1",
                    state = mapOf("user:name" to "Jaichang"),
                ),
            )

            val app =
                adkApp("plugin-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    assertEquals("Speak to Jaichang politely.", request.systemInstructions.first())
                    assertTrue(
                        request.systemInstruction
                            .orEmpty()
                            .contains("Your internal name is \"planner\""),
                    )
                    ModelResponse.Final("Used plugin global instruction.")
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    sessionStore = sessionStore,
                    plugins =
                        listOf(
                            globalInstructionPlugin("Speak to {user:name} politely."),
                        ),
                )

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Use the plugin instruction.",
                )

            assertEquals("Used plugin global instruction.", result.finalMessage)
        }

    @Test
    fun `context filter plugin keeps only the most recent invocation`() =
        runTest {
            var modelCalls = 0
            val sessionStore = InMemorySessionStore()

            val app =
                adkApp("context-filter-app") {
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
                            assertEquals(listOf("Second question."), request.conversation.map { it.text })
                            ModelResponse.Final("Second answer.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    sessionStore = sessionStore,
                    plugins =
                        listOf(
                            contextFilterPlugin(numInvocationsToKeep = 1),
                        ),
                )

            runner.run(
                userId = "user-1",
                sessionId = "session-1",
                input = "First question.",
            )
            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Second question.",
                )

            assertEquals("Second answer.", result.finalMessage)
        }

    @Test
    fun `debug logging plugin writes invocation traces to disk`() =
        runTest {
            val outputPath = Files.createTempDirectory("adk-debug").resolve("adk_debug.yaml")

            val app =
                adkApp("debug-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }

            val fakeModel =
                LanguageModel {
                    ModelResponse.Final("Debug final response.")
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    plugins =
                        listOf(
                            DebugLoggingPlugin(outputPath = outputPath),
                        ),
                )

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Debug this run.",
                )

            val debugLog = Files.readString(outputPath)

            assertEquals("Debug final response.", result.finalMessage)
            assertTrue(debugLog.contains("invocation_id"))
            assertTrue(debugLog.contains("Debug this run."))
            assertTrue(debugLog.contains("Debug final response."))
        }

    @Test
    fun `logging plugin records model and tool lifecycle lines`() =
        runTest {
            val lines = mutableListOf<String>()
            var modelCalls = 0

            val weatherTool =
                tool(
                    name = "lookup_weather",
                    description = "Resolve current weather for a city.",
                ) {
                    ToolOutput("Seoul is sunny")
                }

            val app =
                adkApp("logging-app") {
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

                        2 -> ModelResponse.Final("Logged final answer.")
                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    plugins =
                        listOf(
                            loggingPlugin(sink = lines::add),
                        ),
                )

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Log this run.",
                )

            assertEquals("Logged final answer.", result.finalMessage)
            assertTrue(lines.any { it.contains("USER MESSAGE RECEIVED") })
            assertTrue(lines.any { it.contains("LLM REQUEST") })
            assertTrue(lines.any { it.contains("TOOL STARTING") })
            assertTrue(lines.any { it.contains("TOOL COMPLETED") })
            assertTrue(lines.any { it.contains("INVOCATION COMPLETED") })
            assertTrue(lines.any { it.contains("lookup_weather") })
        }

    @Test
    fun `save files as artifacts plugin persists session uploads and rewrites user text`() =
        runTest {
            val artifactService = InMemoryArtifactService()

            val app =
                adkApp("upload-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    assertTrue(request.conversation.single().text.contains("[Uploaded Artifact: \"notes.txt\"]"))
                    ModelResponse.Final("Upload acknowledged.")
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    artifactService = artifactService,
                    plugins =
                        listOf(
                            saveFilesAsArtifactsPlugin(),
                        ),
                )

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    userMessage =
                        UserMessage(
                            text = "Please review the upload.",
                            attachments =
                                listOf(
                                    MessageAttachment(
                                        filename = "notes.txt",
                                        content = "Quarterly plan",
                                        mimeType = "application/pdf",
                                    ),
                                ),
                        ),
                )

            assertEquals("Upload acknowledged.", result.finalMessage)
            assertEquals(
                "Quarterly plan",
                artifactService.loadArtifact("upload-app", "user-1", "session-1", "notes.txt")?.content,
            )
            assertEquals(
                "application/pdf",
                artifactService.loadArtifact("upload-app", "user-1", "session-1", "notes.txt")?.mimeType,
            )
            assertEquals(
                mapOf("notes.txt" to 0),
                result.events.first().actions.artifactDelta,
            )
        }

    @Test
    fun `save files as artifacts plugin supports user scoped persistence`() =
        runTest {
            val artifactService = InMemoryArtifactService()

            val app =
                adkApp("upload-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    assertTrue(request.conversation.single().text.contains("[Uploaded Artifact: \"shared.txt\"]"))
                    ModelResponse.Final("User artifact saved.")
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    artifactService = artifactService,
                    plugins =
                        listOf(
                            saveFilesAsArtifactsPlugin(),
                        ),
                )

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    userMessage =
                        UserMessage(
                            text = "",
                            attachments =
                                listOf(
                                    MessageAttachment(
                                        filename = "shared.txt",
                                        content = "Shared profile",
                                        scope = AttachmentScope.USER,
                                    ),
                                ),
                        ),
                )

            assertEquals("User artifact saved.", result.finalMessage)
            assertEquals(
                "Shared profile",
                artifactService.loadArtifact("upload-app", "user-1", null, "user:shared.txt")?.content,
            )
            assertEquals(
                mapOf("user:shared.txt" to 0),
                result.events.first().actions.artifactDelta,
            )
        }

    @Test
    fun `runner streams invocation events through callback overload`() =
        runTest {
            var modelCalls = 0
            val streamedEvents = mutableListOf<Event>()

            val weatherTool =
                tool(
                    name = "lookup_weather",
                    description = "Resolve current weather for a city.",
                ) {
                    ToolOutput("Seoul is sunny")
                }

            val app =
                adkApp("streaming-app") {
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

            val runner = Runner(app = app, model = fakeModel)
            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "What is the weather in Seoul?",
                    onEvent = { streamedEvents += it },
                )

            assertEquals("Seoul is sunny today.", result.finalMessage)
            assertEquals(listOf("user", "planner", "planner"), streamedEvents.map { it.author })
            assertEquals(
                listOf(
                    "What is the weather in Seoul?",
                    "Seoul is sunny",
                    "Seoul is sunny today.",
                ),
                streamedEvents.map { it.content?.text },
            )
            assertEquals(streamedEvents, result.events)
            assertTrue(streamedEvents.last().turnComplete)
        }

    @Test
    fun `runner exposes flow based event stream`() =
        runTest {
            val sessionStore = InMemorySessionStore()

            val app =
                adkApp("streaming-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    assertEquals("Stream this run.", request.conversation.single().text)
                    ModelResponse.Final("Flow final response.")
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    sessionStore = sessionStore,
                )

            val events =
                runner
                    .stream(
                        userId = "user-1",
                        sessionId = "session-1",
                        input = "Stream this run.",
                    ).toList()
            val savedSession = sessionStore.getOrCreate("streaming-app", "user-1", "session-1")

            assertEquals(listOf("user", "planner"), events.map { it.author })
            assertEquals(
                listOf("Stream this run.", "Flow final response."),
                events.map { it.content?.text },
            )
            assertTrue(events.last().turnComplete)
            assertEquals(events, savedSession.events)
        }

    @Test
    fun `multimodal tool results plugin merges tool attachments into the next model request`() =
        runTest {
            var modelCalls = 0

            val firstImageTool =
                tool(
                    name = "fetch_first_image",
                    description = "Fetch the first image.",
                ) {
                    ToolOutput(
                        content = "First image ready.",
                        attachments =
                            listOf(
                                MessageAttachment(
                                    filename = "first.png",
                                    content = "first-image",
                                    mimeType = "image/png",
                                ),
                            ),
                    )
                }

            val secondImageTool =
                tool(
                    name = "fetch_second_image",
                    description = "Fetch the second image.",
                ) {
                    ToolOutput(
                        content = "Second image ready.",
                        attachments =
                            listOf(
                                MessageAttachment(
                                    filename = "second.png",
                                    content = "second-image",
                                    mimeType = "image/png",
                                ),
                            ),
                    )
                }

            val app =
                adkApp("multimodal-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        tool(firstImageTool)
                        tool(secondImageTool)
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    modelCalls += 1
                    when (modelCalls) {
                        1 ->
                            ModelResponse.ToolCalls(
                                listOf(
                                    ToolCall("fetch_first_image"),
                                    ToolCall("fetch_second_image"),
                                ),
                            )

                        2 -> {
                            val lastMessage = request.conversation.last()
                            assertEquals("Second image ready.", lastMessage.text)
                            assertEquals(
                                listOf("first.png", "second.png"),
                                lastMessage.attachments.map { it.filename }.sorted(),
                            )
                            ModelResponse.Final("Both images received.")
                        }

                        else -> error("Unexpected model invocation.")
                    }
                }

            val runner =
                Runner(
                    app = app,
                    model = fakeModel,
                    plugins = listOf(multimodalToolResultsPlugin()),
                )

            val result =
                runner.run(
                    userId = "user-1",
                    sessionId = "session-1",
                    input = "Describe both images.",
                )

            assertEquals("Both images received.", result.finalMessage)
            assertEquals(
                listOf("first.png"),
                (result.events[1].content as ToolMessage).attachments.map { it.filename },
            )
            assertEquals(
                listOf("second.png"),
                (result.events[2].content as ToolMessage).attachments.map { it.filename },
            )
        }
}
