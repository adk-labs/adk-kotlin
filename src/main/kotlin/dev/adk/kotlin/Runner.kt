package dev.adk.kotlin

import java.time.Instant
import java.util.UUID

data class ToolExecution(
    val agentName: String,
    val call: ToolCall,
    val output: ToolOutput,
)

data class RunResult(
    val session: AgentSession,
    val finalMessage: String,
    val finalAgentName: String,
    val structuredResponse: Any? = null,
    val toolExecutions: List<ToolExecution>,
    val events: List<Event>,
)

class Runner(
    private val app: AdkApp,
    private val model: LanguageModel,
    private val sessionStore: SessionStore = InMemorySessionStore(),
    val artifactService: ArtifactService = InMemoryArtifactService(),
    plugins: List<Plugin> = emptyList(),
    val memoryService: MemoryService? = null,
) {
    private val pluginManager = PluginManager(plugins)

    internal companion object {
        const val TRANSFER_TO_AGENT_TOOL = "transfer_to_agent"
        const val SET_MODEL_RESPONSE_TOOL = "set_model_response"
    }

    suspend fun run(
        userId: String,
        input: String,
        sessionId: String? = null,
    ): RunResult {
        require(userId.isNotBlank()) { "userId cannot be blank." }
        require(input.isNotBlank()) { "input cannot be blank." }

        val rootAgent = app.rootAgent
        val invocationId = UUID.randomUUID().toString()
        val baseSession = sessionStore.getOrCreate(app.name, userId, sessionId)
        val workingState = baseSession.state.toMutableMap()
        val toolExecutions = mutableListOf<ToolExecution>()
        val events = baseSession.events.toMutableList()
        var transcript: List<Message> = baseSession.transcript
        var activeAgent = rootAgent
        val invocationContext =
            InvocationContext(
                app = app,
                userId = userId,
                invocationId = invocationId,
                rootAgent = rootAgent,
            ) {
                baseSession.copy(
                    state = workingState.toMap(),
                    transcript = transcript,
                    events = events.toList(),
                    updatedAt = Instant.now(),
                )
            }

        val incomingUserMessage =
            pluginManager.runOnUserMessageCallback(invocationContext, UserMessage(input))
                ?: UserMessage(input)
        emitEvent(
            invocationContext = invocationContext,
            events = events,
            event =
                Event(
                    invocationId = invocationId,
                    author = "user",
                    content = incomingUserMessage,
                    branch = app.branchOf(rootAgent),
                ),
        ) {
            transcript = transcript + it
        }

        pluginManager.runBeforeRunCallback(invocationContext)?.let { earlyEvent ->
            val emittedEvent =
                emitEvent(
                    invocationContext = invocationContext,
                    events = events,
                    event = earlyEvent,
                ) {
                    transcript = transcript + it
                }
            val earlyResult =
                saveAndCreateRunResult(
                    session = invocationContext.session,
                    workingState = workingState,
                    transcript = transcript,
                    events = events,
                    finalEvent = emittedEvent,
                    structuredResponse = null,
                    toolExecutions = toolExecutions,
                    fallbackAgentName = rootAgent.name,
                )
            pluginManager.runAfterRunCallback(invocationContext, earlyResult)
            return earlyResult
        }

        repeat(rootAgent.maxIterations) {
            val workingSession =
                baseSession.copy(
                    state = workingState.toMap(),
                    transcript = transcript,
                    events = events.toList(),
                    updatedAt = Instant.now(),
                )
            val callbackContext = CallbackContext(invocationContext = invocationContext, agent = activeAgent)
            val request =
                PromptAssembler.createRequest(
                    app = app,
                    agent = activeAgent,
                    session = workingSession,
                    transcript = transcript,
                    artifactService = artifactService,
                    includeOutputSchemaWorkaround = shouldUseOutputSchemaWorkaround(activeAgent),
                )

            val response =
                pluginManager.runBeforeModelCallback(callbackContext, request)
                    ?: try {
                        model.generate(request)
                    } catch (error: Throwable) {
                        pluginManager.runOnModelErrorCallback(callbackContext, request, error) ?: throw error
                    }
            val finalModelResponse = pluginManager.runAfterModelCallback(callbackContext, response) ?: response

            when (finalModelResponse) {
                is ModelResponse.Final -> {
                    val structuredResponse =
                        validateStructuredResponse(
                            agent = activeAgent,
                            structuredResponse = finalModelResponse.structuredResponse,
                        )
                    val finalMessage =
                        finalModelResponse.message.ifBlank {
                            structuredResponse?.toString().orEmpty()
                        }
                    val emittedEvent =
                        emitEvent(
                            invocationContext = invocationContext,
                            events = events,
                            event =
                                Event(
                                    invocationId = invocationId,
                                    author = activeAgent.name,
                                    content = ModelMessage(finalMessage),
                                    actions =
                                        EventActions(
                                            endOfAgent = true,
                                            agentState = workingState.toMap(),
                                        ),
                                    branch = app.branchOf(activeAgent),
                                    turnComplete = true,
                                ),
                        ) {
                            transcript = transcript + it
                        }
                    val runResult =
                        saveAndCreateRunResult(
                            session = workingSession,
                            workingState = workingState,
                            transcript = transcript,
                            events = events,
                            finalEvent = emittedEvent,
                            structuredResponse = structuredResponse,
                            toolExecutions = toolExecutions,
                            fallbackAgentName = activeAgent.name,
                        )
                    pluginManager.runAfterRunCallback(invocationContext, runResult)
                    return runResult
                }

                is ModelResponse.ToolCalls -> {
                    require(finalModelResponse.calls.isNotEmpty()) {
                        "Model returned an empty tool call list."
                    }

                    val transferCall = finalModelResponse.calls.singleOrNull { it.toolName == TRANSFER_TO_AGENT_TOOL }
                    if (transferCall != null) {
                        require(finalModelResponse.calls.size == 1) {
                            "transfer_to_agent must be the only tool call in a turn."
                        }

                        val targetAgentName = transferCall.requireArgument("agent_name")
                        val nextAgent =
                            app
                                .transferTargetsOf(activeAgent)
                                .firstOrNull { it.name == targetAgentName }
                                ?: error("Unknown transfer target: $targetAgentName")

                        val output = ToolOutput("Transferred to agent $targetAgentName.")
                        toolExecutions +=
                            ToolExecution(
                                agentName = activeAgent.name,
                                call = transferCall,
                                output = output,
                            )
                        emitEvent(
                            invocationContext = invocationContext,
                            events = events,
                            event =
                                Event(
                                    invocationId = invocationId,
                                    author = activeAgent.name,
                                    content =
                                        ToolMessage(
                                            toolName = TRANSFER_TO_AGENT_TOOL,
                                            text = output.content,
                                        ),
                                    actions =
                                        EventActions(
                                            transferToAgent = targetAgentName,
                                        ),
                                    branch = app.branchOf(activeAgent),
                                ),
                        ) {
                            transcript = transcript + it
                        }
                        activeAgent = nextAgent
                        return@repeat
                    }

                    val setModelResponseCall = finalModelResponse.calls.singleOrNull { it.toolName == SET_MODEL_RESPONSE_TOOL }
                    if (setModelResponseCall != null) {
                        require(finalModelResponse.calls.size == 1) {
                            "set_model_response must be the only tool call in a turn."
                        }
                        val outputSchema =
                            activeAgent.outputSchema
                                ?: error("set_model_response is only valid when outputSchema is configured.")
                        val structuredResponse = outputSchema.validate(setModelResponseCall.arguments)
                        val finalMessage = structuredResponse.toString()
                        val output = ToolOutput(finalMessage)

                        toolExecutions +=
                            ToolExecution(
                                agentName = activeAgent.name,
                                call = setModelResponseCall,
                                output = output,
                            )
                        val emittedEvent =
                            emitEvent(
                                invocationContext = invocationContext,
                                events = events,
                                event =
                                    Event(
                                        invocationId = invocationId,
                                        author = activeAgent.name,
                                        content = ModelMessage(finalMessage),
                                        actions =
                                            EventActions(
                                                endOfAgent = true,
                                                agentState = workingState.toMap(),
                                            ),
                                        branch = app.branchOf(activeAgent),
                                        turnComplete = true,
                                    ),
                            ) {
                                transcript = transcript + it
                            }

                        val runResult =
                            saveAndCreateRunResult(
                                session = workingSession,
                                workingState = workingState,
                                transcript = transcript,
                                events = events,
                                finalEvent = emittedEvent,
                                structuredResponse = structuredResponse,
                                toolExecutions = toolExecutions,
                                fallbackAgentName = activeAgent.name,
                            )
                        pluginManager.runAfterRunCallback(invocationContext, runResult)
                        return runResult
                    }

                    finalModelResponse.calls.forEach { call ->
                        val tool =
                            activeAgent.tools.find { it.definition.name == call.toolName }
                                ?: error("Unknown tool requested: ${call.toolName}")

                        val context =
                            ToolContext(
                                appName = app.name,
                                agent = activeAgent,
                                session = workingSession,
                                workingState = workingState,
                                artifactService = artifactService,
                                memoryService = memoryService,
                            )
                        val stateBeforeTool = workingState.toMap()
                        val beforeToolOutput = pluginManager.runBeforeToolCallback(tool, call, context)
                        val output =
                            beforeToolOutput
                                ?: try {
                                    tool.execute(call, context)
                                } catch (error: Throwable) {
                                    pluginManager.runOnToolErrorCallback(tool, call, context, error) ?: throw error
                                }
                        val finalToolOutput =
                            if (beforeToolOutput != null) {
                                output
                            } else {
                                pluginManager.runAfterToolCallback(tool, call, context, output) ?: output
                            }
                        val emittedEvent =
                            emitEvent(
                                invocationContext = invocationContext,
                                events = events,
                                event =
                                    Event(
                                        invocationId = invocationId,
                                        author = activeAgent.name,
                                        content = ToolMessage(toolName = call.toolName, text = finalToolOutput.content),
                                        actions =
                                            EventActions(
                                                stateDelta = computeStateDelta(stateBeforeTool, workingState),
                                                artifactDelta = context.recordedArtifactDelta(),
                                            ),
                                        branch = app.branchOf(activeAgent),
                                    ),
                            ) {
                                transcript = transcript + it
                            }

                        toolExecutions +=
                            ToolExecution(
                                agentName = activeAgent.name,
                                call = call,
                                output =
                                    ToolOutput(
                                        content = emittedEvent.content?.text.orEmpty(),
                                        metadata = finalToolOutput.metadata,
                                    ),
                            )
                    }
                }
            }
        }

        error("Agent ${rootAgent.name} exceeded maxIterations=${rootAgent.maxIterations}.")
    }

    suspend fun close() {
        pluginManager.close()
    }

    private suspend fun emitEvent(
        invocationContext: InvocationContext,
        events: MutableList<Event>,
        event: Event,
        onContent: (Message) -> Unit,
    ): Event {
        val emittedEvent = pluginManager.runOnEventCallback(invocationContext, event) ?: event
        emittedEvent.content?.let(onContent)
        events += emittedEvent
        return emittedEvent
    }

    private suspend fun saveAndCreateRunResult(
        session: AgentSession,
        workingState: MutableMap<String, String>,
        transcript: List<Message>,
        events: MutableList<Event>,
        finalEvent: Event,
        structuredResponse: Any?,
        toolExecutions: MutableList<ToolExecution>,
        fallbackAgentName: String,
    ): RunResult {
        val savedSession =
            session.copy(
                state = workingState.toMap(),
                transcript = transcript,
                events = events.toList(),
                updatedAt = Instant.now(),
            )
        sessionStore.save(app.name, savedSession)
        val finalAgentName =
            app.findAgent(finalEvent.author)?.name
                ?: fallbackAgentName
        return RunResult(
            session = savedSession,
            finalMessage = finalEvent.content?.text.orEmpty(),
            finalAgentName = finalAgentName,
            structuredResponse = structuredResponse,
            toolExecutions = toolExecutions.toList(),
            events = events.toList(),
        )
    }

    private fun shouldUseOutputSchemaWorkaround(agent: LlmAgent): Boolean {
        if (agent.outputSchema == null || agent.tools.isEmpty()) {
            return false
        }

        val capabilities =
            when (model) {
                is SupportsPerModelCapabilities -> model.modelCapabilities(agent.model)
                is SupportsModelCapabilities -> model.modelCapabilities
                else -> null
            }
        return capabilities?.supportsOutputSchemaWithTools != true
    }

    private fun validateStructuredResponse(
        agent: LlmAgent,
        structuredResponse: Any?,
    ): Any? {
        if (structuredResponse == null) {
            return null
        }

        val outputSchema = agent.outputSchema ?: return structuredResponse
        val responseMap = structuredResponse as? Map<*, *> ?: error("Structured response must be a map.")

        return outputSchema.validate(
            responseMap.entries.associate { (key, value) ->
                val stringKey = key as? String ?: error("Structured response keys must be strings.")
                stringKey to value
            },
        )
    }

    private fun computeStateDelta(
        before: Map<String, String>,
        after: Map<String, String>,
    ): Map<String, String?> =
        (before.keys + after.keys)
            .sorted()
            .mapNotNull { key ->
                val beforeValue = before[key]
                val afterValue = after[key]
                if (beforeValue == afterValue) {
                    null
                } else {
                    key to afterValue
                }
            }.toMap(linkedMapOf())
}
