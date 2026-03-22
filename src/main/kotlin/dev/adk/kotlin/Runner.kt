package dev.adk.kotlin

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

private data class ExecutionOutcome(
    val finalEvent: Event,
    val finalAgentName: String,
    val structuredResponse: Any? = null,
    val shouldEscalate: Boolean = false,
)

private data class ParallelBranchResult(
    val outcome: ExecutionOutcome,
    val newTranscript: List<Message>,
    val newEvents: List<Event>,
    val toolExecutions: List<ToolExecution>,
    val completedAt: Instant,
)

class Runner(
    private val app: AdkApp,
    private val model: LanguageModel,
    private val sessionStore: SessionStore = InMemorySessionStore(),
    val artifactService: ArtifactService = InMemoryArtifactService(),
    plugins: List<Plugin> = emptyList(),
    val memoryService: MemoryService? = null,
    val credentialService: CredentialService? = null,
    private val toolConfirmationHandler: ToolConfirmationHandler? = null,
) {
    private val pluginManager = PluginManager(plugins)

    internal companion object {
        const val TRANSFER_TO_AGENT_TOOL = "transfer_to_agent"
        const val SET_MODEL_RESPONSE_TOOL = "set_model_response"
        const val EXIT_LOOP_TOOL = "exit_loop"
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
        val transcript = baseSession.transcript.toMutableList()
        val invocationContext =
            InvocationContext(
                app = app,
                userId = userId,
                invocationId = invocationId,
                rootAgent = rootAgent,
            ) {
                baseSession.copy(
                    state = workingState.toMap(),
                    transcript = transcript.toList(),
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
            transcript = transcript,
        )

        pluginManager.runBeforeRunCallback(invocationContext)?.let { earlyEvent ->
            val emittedEvent =
                emitEvent(
                    invocationContext = invocationContext,
                    events = events,
                    event = earlyEvent,
                    transcript = transcript,
                )
            val earlyResult =
                saveAndCreateRunResult(
                    session = invocationContext.session,
                    workingState = workingState,
                    transcript = transcript.toList(),
                    events = events,
                    finalEvent = emittedEvent,
                    structuredResponse = null,
                    toolExecutions = toolExecutions,
                    fallbackAgentName = rootAgent.name,
                )
            pluginManager.runAfterRunCallback(invocationContext, earlyResult)
            return earlyResult
        }

        val outcome =
            runAgent(
                agent = rootAgent,
                baseSession = baseSession,
                workingState = workingState,
                transcript = transcript,
                events = events,
                invocationContext = invocationContext,
                toolExecutions = toolExecutions,
                allowExitLoop = false,
            )

        val runResult =
            saveAndCreateRunResult(
                session = baseSession,
                workingState = workingState,
                transcript = transcript.toList(),
                events = events,
                finalEvent = outcome.finalEvent,
                structuredResponse = outcome.structuredResponse,
                toolExecutions = toolExecutions,
                fallbackAgentName = outcome.finalAgentName,
            )
        pluginManager.runAfterRunCallback(invocationContext, runResult)
        return runResult
    }

    suspend fun close() {
        pluginManager.close()
        closeToolsets(app.rootAgent)
    }

    private suspend fun runAgent(
        agent: LlmAgent,
        baseSession: AgentSession,
        workingState: MutableMap<String, String>,
        transcript: MutableList<Message>,
        events: MutableList<Event>,
        invocationContext: InvocationContext,
        toolExecutions: MutableList<ToolExecution>,
        allowExitLoop: Boolean,
    ): ExecutionOutcome =
        when (agent.executionKind) {
            AgentExecutionKind.LLM ->
                runLlmAgent(
                    agent = agent,
                    baseSession = baseSession,
                    workingState = workingState,
                    transcript = transcript,
                    events = events,
                    invocationContext = invocationContext,
                    toolExecutions = toolExecutions,
                    allowExitLoop = allowExitLoop,
                )

            AgentExecutionKind.SEQUENTIAL ->
                runSequentialAgent(
                    agent = agent,
                    baseSession = baseSession,
                    workingState = workingState,
                    transcript = transcript,
                    events = events,
                    invocationContext = invocationContext,
                    toolExecutions = toolExecutions,
                    allowExitLoop = allowExitLoop,
                )

            AgentExecutionKind.LOOP ->
                runLoopAgent(
                    agent = agent,
                    baseSession = baseSession,
                    workingState = workingState,
                    transcript = transcript,
                    events = events,
                    invocationContext = invocationContext,
                    toolExecutions = toolExecutions,
                )

            AgentExecutionKind.PARALLEL ->
                runParallelAgent(
                    agent = agent,
                    baseSession = baseSession,
                    workingState = workingState,
                    transcript = transcript,
                    events = events,
                    invocationContext = invocationContext,
                    toolExecutions = toolExecutions,
                    allowExitLoop = allowExitLoop,
                )
        }

    private suspend fun runSequentialAgent(
        agent: LlmAgent,
        baseSession: AgentSession,
        workingState: MutableMap<String, String>,
        transcript: MutableList<Message>,
        events: MutableList<Event>,
        invocationContext: InvocationContext,
        toolExecutions: MutableList<ToolExecution>,
        allowExitLoop: Boolean,
    ): ExecutionOutcome {
        var lastOutcome: ExecutionOutcome? = null
        agent.subAgents.forEach { subAgent ->
            lastOutcome =
                runAgent(
                    agent = subAgent,
                    baseSession = baseSession,
                    workingState = workingState,
                    transcript = transcript,
                    events = events,
                    invocationContext = invocationContext,
                    toolExecutions = toolExecutions,
                    allowExitLoop = allowExitLoop,
                )
            if (lastOutcome?.shouldEscalate == true) {
                return lastOutcome as ExecutionOutcome
            }
        }
        return requireNotNull(lastOutcome) { "SequentialAgent '${agent.name}' must produce at least one outcome." }
    }

    private suspend fun runLoopAgent(
        agent: LlmAgent,
        baseSession: AgentSession,
        workingState: MutableMap<String, String>,
        transcript: MutableList<Message>,
        events: MutableList<Event>,
        invocationContext: InvocationContext,
        toolExecutions: MutableList<ToolExecution>,
    ): ExecutionOutcome {
        val maxIterations = agent.loopMaxIterations ?: Int.MAX_VALUE
        var lastMeaningfulOutcome: ExecutionOutcome? = null

        repeat(maxIterations) {
            agent.subAgents.forEach { subAgent ->
                val outcome =
                    runAgent(
                        agent = subAgent,
                        baseSession = baseSession,
                        workingState = workingState,
                        transcript = transcript,
                        events = events,
                        invocationContext = invocationContext,
                        toolExecutions = toolExecutions,
                        allowExitLoop = true,
                    )

                if (!outcome.shouldEscalate) {
                    lastMeaningfulOutcome = outcome
                }

                if (outcome.shouldEscalate) {
                    return lastMeaningfulOutcome ?: outcome
                }
            }
        }

        return requireNotNull(lastMeaningfulOutcome) {
            "LoopAgent '${agent.name}' completed without producing a meaningful outcome."
        }
    }

    private suspend fun runParallelAgent(
        agent: LlmAgent,
        baseSession: AgentSession,
        workingState: MutableMap<String, String>,
        transcript: MutableList<Message>,
        events: MutableList<Event>,
        invocationContext: InvocationContext,
        toolExecutions: MutableList<ToolExecution>,
        allowExitLoop: Boolean,
    ): ExecutionOutcome =
        coroutineScope {
            val baselineState = workingState.toMap()
            val baselineTranscript = transcript.toList()
            val baselineEvents = events.toList()

            val branchResults =
                agent.subAgents
                    .map { subAgent ->
                        async {
                            val branchState = baselineState.toMutableMap()
                            val branchTranscript = baselineTranscript.toMutableList()
                            val branchEvents = baselineEvents.toMutableList()
                            val branchToolExecutions = mutableListOf<ToolExecution>()

                            val outcome =
                                runAgent(
                                    agent = subAgent,
                                    baseSession = baseSession,
                                    workingState = branchState,
                                    transcript = branchTranscript,
                                    events = branchEvents,
                                    invocationContext = invocationContext,
                                    toolExecutions = branchToolExecutions,
                                    allowExitLoop = allowExitLoop,
                                )

                            ParallelBranchResult(
                                outcome = outcome,
                                newTranscript = branchTranscript.drop(baselineTranscript.size),
                                newEvents = branchEvents.drop(baselineEvents.size),
                                toolExecutions = branchToolExecutions.toList(),
                                completedAt = Instant.now(),
                            )
                        }
                    }.awaitAll()
                    .sortedBy { it.completedAt }

            var lastMeaningfulOutcome: ExecutionOutcome? = null
            var lastEscalatingOutcome: ExecutionOutcome? = null

            branchResults.forEach { branchResult ->
                transcript += branchResult.newTranscript
                events += branchResult.newEvents
                toolExecutions += branchResult.toolExecutions

                if (branchResult.outcome.shouldEscalate) {
                    lastEscalatingOutcome = branchResult.outcome
                } else {
                    lastMeaningfulOutcome = branchResult.outcome
                }
            }

            lastMeaningfulOutcome ?: lastEscalatingOutcome
            ?: error("ParallelAgent '${agent.name}' must produce at least one outcome.")
        }

    private suspend fun runLlmAgent(
        agent: LlmAgent,
        baseSession: AgentSession,
        workingState: MutableMap<String, String>,
        transcript: MutableList<Message>,
        events: MutableList<Event>,
        invocationContext: InvocationContext,
        toolExecutions: MutableList<ToolExecution>,
        allowExitLoop: Boolean,
    ): ExecutionOutcome {
        var consecutiveCodeExecutionErrors = 0

        repeat(agent.maxIterations) {
            val workingSession = snapshotSession(baseSession, workingState, transcript, events)
            val resolvedTools = resolveTools(agent, workingSession)
            val callbackContext = CallbackContext(invocationContext = invocationContext, agent = agent)
            val request =
                PromptAssembler.createRequest(
                    app = app,
                    agent = agent,
                    session = workingSession,
                    transcript = transcript.toList(),
                    resolvedTools = resolvedTools,
                    artifactService = artifactService,
                    includeOutputSchemaWorkaround = shouldUseOutputSchemaWorkaround(agent),
                    includeExitLoopTool = allowExitLoop,
                )
            val preprocessedRequest =
                preprocessLlmRequest(
                    agent = agent,
                    session = workingSession,
                    workingState = workingState,
                    resolvedTools = resolvedTools,
                    llmRequest = request,
                )
            val plannedRequest = agent.planner?.prepareRequest(workingSession, preprocessedRequest) ?: preprocessedRequest
            val finalRequest = agent.codeExecutor?.processLlmRequest(plannedRequest) ?: plannedRequest

            val response =
                pluginManager.runBeforeModelCallback(callbackContext, finalRequest)
                    ?: try {
                        model.generate(finalRequest)
                    } catch (error: Throwable) {
                        pluginManager.runOnModelErrorCallback(callbackContext, finalRequest, error) ?: throw error
                    }
            val plannerProcessedResponse = agent.planner?.processPlanningResponse(response) ?: response
            val responseAfterPlugins =
                pluginManager.runAfterModelCallback(callbackContext, plannerProcessedResponse)
                    ?: plannerProcessedResponse
            val finalModelResponse =
                agent.planner?.processPlanningResponse(responseAfterPlugins) ?: responseAfterPlugins

            when (finalModelResponse) {
                is ModelResponse.Final -> {
                    maybeExecuteCodeBlock(
                        agent = agent,
                        response = finalModelResponse,
                        workingState = workingState,
                        transcript = transcript,
                        events = events,
                        invocationContext = invocationContext,
                        consecutiveCodeExecutionErrors = consecutiveCodeExecutionErrors,
                    )?.let { nextErrorCount ->
                        consecutiveCodeExecutionErrors = nextErrorCount
                        return@repeat
                    }

                    consecutiveCodeExecutionErrors = 0
                    val structuredResponse =
                        validateStructuredResponse(
                            agent = agent,
                            structuredResponse = finalModelResponse.structuredResponse,
                        )
                    val finalMessage =
                        finalModelResponse.message.ifBlank {
                            structuredResponse?.toString().orEmpty()
                        }
                    persistOutput(agent, finalMessage, workingState)
                    val emittedEvent =
                        emitEvent(
                            invocationContext = invocationContext,
                            events = events,
                            event =
                                Event(
                                    invocationId = invocationContext.invocationId,
                                    author = agent.name,
                                    content = ModelMessage(finalMessage),
                                    actions =
                                        EventActions(
                                            endOfAgent = true,
                                            agentState = workingState.toMap(),
                                        ),
                                    branch = app.branchOf(agent),
                                    turnComplete = true,
                                ),
                            transcript = transcript,
                        )
                    return ExecutionOutcome(
                        finalEvent = emittedEvent,
                        finalAgentName = agent.name,
                        structuredResponse = structuredResponse,
                    )
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
                                .transferTargetsOf(agent)
                                .firstOrNull { it.name == targetAgentName }
                                ?: error("Unknown transfer target: $targetAgentName")

                        val output = ToolOutput("Transferred to agent $targetAgentName.")
                        toolExecutions +=
                            ToolExecution(
                                agentName = agent.name,
                                call = transferCall,
                                output = output,
                            )
                        emitEvent(
                            invocationContext = invocationContext,
                            events = events,
                            event =
                                Event(
                                    invocationId = invocationContext.invocationId,
                                    author = agent.name,
                                    content =
                                        ToolMessage(
                                            toolName = TRANSFER_TO_AGENT_TOOL,
                                            text = output.content,
                                        ),
                                    actions =
                                        EventActions(
                                            transferToAgent = targetAgentName,
                                        ),
                                    branch = app.branchOf(agent),
                                ),
                            transcript = transcript,
                        )
                        return runAgent(
                            agent = nextAgent,
                            baseSession = baseSession,
                            workingState = workingState,
                            transcript = transcript,
                            events = events,
                            invocationContext = invocationContext,
                            toolExecutions = toolExecutions,
                            allowExitLoop = allowExitLoop,
                        )
                    }

                    val exitLoopCall = finalModelResponse.calls.singleOrNull { it.toolName == EXIT_LOOP_TOOL }
                    if (exitLoopCall != null) {
                        require(allowExitLoop) { "exit_loop is only valid inside LoopAgent execution." }
                        require(finalModelResponse.calls.size == 1) {
                            "exit_loop must be the only tool call in a turn."
                        }

                        val output = ToolOutput("Loop exit requested.")
                        toolExecutions +=
                            ToolExecution(
                                agentName = agent.name,
                                call = exitLoopCall,
                                output = output,
                            )
                        val emittedEvent =
                            emitEvent(
                                invocationContext = invocationContext,
                                events = events,
                                event =
                                    Event(
                                        invocationId = invocationContext.invocationId,
                                        author = agent.name,
                                        content = ToolMessage(toolName = EXIT_LOOP_TOOL, text = output.content),
                                        actions =
                                            EventActions(
                                                escalate = true,
                                            ),
                                        branch = app.branchOf(agent),
                                    ),
                                transcript = transcript,
                            )
                        return ExecutionOutcome(
                            finalEvent = emittedEvent,
                            finalAgentName = agent.name,
                            shouldEscalate = true,
                        )
                    }

                    val setModelResponseCall = finalModelResponse.calls.singleOrNull { it.toolName == SET_MODEL_RESPONSE_TOOL }
                    if (setModelResponseCall != null) {
                        require(finalModelResponse.calls.size == 1) {
                            "set_model_response must be the only tool call in a turn."
                        }
                        val outputSchema =
                            agent.outputSchema
                                ?: error("set_model_response is only valid when outputSchema is configured.")
                        val structuredResponse = outputSchema.validate(setModelResponseCall.arguments)
                        val finalMessage = structuredResponse.toString()
                        persistOutput(agent, finalMessage, workingState)
                        val output = ToolOutput(finalMessage)

                        toolExecutions +=
                            ToolExecution(
                                agentName = agent.name,
                                call = setModelResponseCall,
                                output = output,
                            )
                        val emittedEvent =
                            emitEvent(
                                invocationContext = invocationContext,
                                events = events,
                                event =
                                    Event(
                                        invocationId = invocationContext.invocationId,
                                        author = agent.name,
                                        content = ModelMessage(finalMessage),
                                        actions =
                                            EventActions(
                                                endOfAgent = true,
                                                agentState = workingState.toMap(),
                                            ),
                                        branch = app.branchOf(agent),
                                        turnComplete = true,
                                    ),
                                transcript = transcript,
                            )

                        return ExecutionOutcome(
                            finalEvent = emittedEvent,
                            finalAgentName = agent.name,
                            structuredResponse = structuredResponse,
                        )
                    }

                    finalModelResponse.calls.forEach { call ->
                        val tool =
                            resolvedTools.find { it.definition.name == call.toolName }
                                ?: error("Unknown tool requested: ${call.toolName}")
                        val callId = UUID.randomUUID().toString()
                        val confirmation =
                            maybeConfirmToolCall(
                                callId = callId,
                                agent = agent,
                                call = call,
                                tool = tool,
                                workingSession = workingSession,
                                invocationContext = invocationContext,
                                events = events,
                                transcript = transcript,
                                toolExecutions = toolExecutions,
                            )
                        if (confirmation?.confirmed == false) {
                            return@forEach
                        }

                        val context =
                            createToolContext(
                                agent = agent,
                                session = workingSession,
                                workingState = workingState,
                                functionCallId = callId,
                                toolConfirmation = confirmation,
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
                                        invocationId = invocationContext.invocationId,
                                        author = agent.name,
                                        content = ToolMessage(toolName = call.toolName, text = finalToolOutput.content),
                                        actions =
                                            EventActions(
                                                skipSummarization = finalToolOutput.skipSummarization.takeIf { it },
                                                stateDelta = computeStateDelta(stateBeforeTool, workingState),
                                                artifactDelta = context.recordedArtifactDelta(),
                                                requestedAuthConfigs = context.recordedRequestedAuthConfigs(),
                                                requestedToolConfirmations = context.recordedRequestedToolConfirmations(),
                                            ),
                                        branch = app.branchOf(agent),
                                    ),
                                transcript = transcript,
                            )

                        toolExecutions +=
                            ToolExecution(
                                agentName = agent.name,
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

        error("Agent ${agent.name} exceeded maxIterations=${agent.maxIterations}.")
    }

    private suspend fun maybeConfirmToolCall(
        callId: String,
        agent: LlmAgent,
        call: ToolCall,
        tool: Tool,
        workingSession: AgentSession,
        invocationContext: InvocationContext,
        events: MutableList<Event>,
        transcript: MutableList<Message>,
        toolExecutions: MutableList<ToolExecution>,
    ): ToolConfirmation? {
        if (!tool.definition.requiresConfirmation) {
            return null
        }

        val suggestedConfirmation =
            ToolConfirmation(
                hint = tool.definition.confirmationHint,
                payload = call.arguments.takeIf { arguments -> arguments.isNotEmpty() },
            )
        val resolvedConfirmation =
            toolConfirmationHandler?.confirm(
                ToolConfirmationRequest(
                    callId = callId,
                    agentName = agent.name,
                    tool = tool.definition,
                    toolCall = call,
                    session = workingSession,
                    suggestedConfirmation = suggestedConfirmation,
                ),
            ) ?: suggestedConfirmation

        val confirmationMessage =
            when {
                resolvedConfirmation.confirmed -> "Tool ${tool.definition.name} confirmed."
                toolConfirmationHandler != null -> "Tool ${tool.definition.name} was not approved."
                else -> "Tool ${tool.definition.name} requires confirmation."
            }
        emitEvent(
            invocationContext = invocationContext,
            events = events,
            event =
                Event(
                    invocationId = invocationContext.invocationId,
                    author = agent.name,
                    content =
                        ToolMessage(
                            toolName = tool.definition.name,
                            text = confirmationMessage,
                        ),
                    actions =
                        EventActions(
                            requestedToolConfirmations = mapOf(callId to resolvedConfirmation),
                        ),
                    branch = app.branchOf(agent),
                ),
            transcript = transcript,
        )

        if (!resolvedConfirmation.confirmed) {
            toolExecutions +=
                ToolExecution(
                    agentName = agent.name,
                    call = call,
                    output =
                        ToolOutput(
                            content = confirmationMessage,
                            metadata =
                                mapOf(
                                    "confirmation" to if (toolConfirmationHandler != null) "denied" else "pending",
                                ),
                        ),
                )
        }

        return resolvedConfirmation
    }

    private suspend fun maybeExecuteCodeBlock(
        agent: LlmAgent,
        response: ModelResponse.Final,
        workingState: MutableMap<String, String>,
        transcript: MutableList<Message>,
        events: MutableList<Event>,
        invocationContext: InvocationContext,
        consecutiveCodeExecutionErrors: Int,
    ): Int? {
        if (response.structuredResponse != null) {
            return null
        }

        val codeExecutor = agent.codeExecutor ?: return null
        if (codeExecutor is BuiltInCodeExecutor) {
            return null
        }
        if (consecutiveCodeExecutionErrors >= codeExecutor.errorRetryAttempts) {
            return null
        }

        val extractedCode = codeExecutor.extractCodeAndTruncateContent(response.message) ?: return null
        if (extractedCode.prefix.isNotBlank()) {
            emitEvent(
                invocationContext = invocationContext,
                events = events,
                event =
                    Event(
                        invocationId = invocationContext.invocationId,
                        author = agent.name,
                        content = ModelMessage(extractedCode.prefix),
                        branch = app.branchOf(agent),
                    ),
                transcript = transcript,
            )
        }

        val codeExecutionResult =
            try {
                codeExecutor.executeCode(
                    invocationContext = invocationContext,
                    codeExecutionInput =
                        CodeExecutionInput(
                            code = extractedCode.code,
                            executionId = invocationContext.session.id.takeIf { codeExecutor.stateful },
                        ),
                )
            } catch (error: Throwable) {
                CodeExecutionResult(
                    stderr = error.message ?: "Code execution failed.",
                )
            }

        codeExecutionResult.stateDelta.forEach { (key, value) ->
            if (value == null) {
                workingState.remove(key)
            } else {
                workingState[key] = value
            }
        }

        val artifactDelta =
            persistCodeExecutionArtifacts(
                invocationContext = invocationContext,
                result = codeExecutionResult,
            )
        emitEvent(
            invocationContext = invocationContext,
            events = events,
            event =
                Event(
                    invocationId = invocationContext.invocationId,
                    author = agent.name,
                    content = ModelMessage(codeExecutor.formatExecutionResult(codeExecutionResult)),
                    actions =
                        EventActions(
                            stateDelta = codeExecutionResult.stateDelta,
                            artifactDelta = artifactDelta,
                        ),
                    branch = app.branchOf(agent),
                ),
            transcript = transcript,
        )

        return if (codeExecutionResult.hasError) {
            consecutiveCodeExecutionErrors + 1
        } else {
            0
        }
    }

    private suspend fun emitEvent(
        invocationContext: InvocationContext,
        events: MutableList<Event>,
        event: Event,
        transcript: MutableList<Message>,
    ): Event {
        val emittedEvent = pluginManager.runOnEventCallback(invocationContext, event) ?: event
        emittedEvent.content?.let(transcript::add)
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

    private fun snapshotSession(
        baseSession: AgentSession,
        workingState: MutableMap<String, String>,
        transcript: MutableList<Message>,
        events: MutableList<Event>,
    ): AgentSession =
        baseSession.copy(
            state = workingState.toMap(),
            transcript = transcript.toList(),
            events = events.toList(),
            updatedAt = Instant.now(),
        )

    private suspend fun preprocessLlmRequest(
        agent: LlmAgent,
        session: AgentSession,
        workingState: MutableMap<String, String>,
        resolvedTools: List<Tool>,
        llmRequest: LlmRequest,
    ): LlmRequest {
        val preprocessingContext =
            createToolContext(
                agent = agent,
                session = session,
                workingState = workingState.toMutableMap(),
            )
        var currentRequest = llmRequest

        resolvedTools.forEach { tool ->
            currentRequest = tool.processLlmRequest(preprocessingContext, currentRequest)
        }
        agent.toolsets.forEach { toolset ->
            currentRequest = toolset.processLlmRequest(preprocessingContext, currentRequest)
        }

        return currentRequest
    }

    private fun createToolContext(
        agent: LlmAgent,
        session: AgentSession,
        workingState: MutableMap<String, String>,
        functionCallId: String? = null,
        toolConfirmation: ToolConfirmation? = null,
    ): ToolContext =
        ToolContext(
            appName = app.name,
            agent = agent,
            session = session,
            workingState = workingState,
            artifactService = artifactService,
            memoryService = memoryService,
            credentialService = credentialService,
            functionCallId = functionCallId,
            toolConfirmation = toolConfirmation,
            agentToolExecutor = { toolContext, toolAgent, arguments, skipSummarization, includePlugins ->
                executeAgentTool(
                    toolContext = toolContext,
                    toolAgent = toolAgent,
                    arguments = arguments,
                    skipSummarization = skipSummarization,
                    includePlugins = includePlugins,
                )
            },
        )

    private suspend fun resolveTools(
        agent: LlmAgent,
        session: AgentSession,
    ): List<Tool> {
        val resolved = mutableListOf<Tool>()
        resolved += agent.tools

        val readonlyContext =
            ReadonlyContext(
                appName = app.name,
                agent = agent,
                session = session,
            )
        agent.toolsets.forEach { toolset ->
            resolved += toolset.getToolsWithPrefix(readonlyContext)
        }

        return resolved
    }

    private fun closeToolsets(agent: LlmAgent) {
        agent.toolsets.forEach { toolset -> toolset.close() }
        agent.subAgents.forEach(::closeToolsets)
    }

    private fun shouldUseOutputSchemaWorkaround(agent: LlmAgent): Boolean {
        if (agent.outputSchema == null) {
            return false
        }

        if (agent.tools.isEmpty() && agent.toolsets.isEmpty()) {
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

    private fun persistOutput(
        agent: LlmAgent,
        finalMessage: String,
        workingState: MutableMap<String, String>,
    ) {
        agent.outputKey?.takeIf { it.isNotBlank() }?.let { outputKey ->
            workingState[outputKey] = finalMessage
        }
    }

    private suspend fun persistCodeExecutionArtifacts(
        invocationContext: InvocationContext,
        result: CodeExecutionResult,
    ): Map<String, Int> {
        if (result.outputFiles.isEmpty()) {
            return emptyMap()
        }

        val artifactDelta = linkedMapOf<String, Int>()
        result.outputFiles.forEach { outputFile ->
            val version =
                artifactService.saveArtifact(
                    appName = app.name,
                    userId = invocationContext.userId,
                    sessionId = invocationContext.session.id,
                    filename = outputFile.name,
                    artifact = Artifact(outputFile.content),
                )
            artifactDelta[outputFile.name] = version
        }
        return artifactDelta
    }

    private suspend fun executeAgentTool(
        toolContext: ToolContext,
        toolAgent: LlmAgent,
        arguments: Map<String, Any?>,
        skipSummarization: Boolean,
        includePlugins: Boolean,
    ): ToolOutput {
        val childSessionStore = InMemorySessionStore()
        val childSession =
            AgentSession(
                id = toolContext.session.id,
                userId = toolContext.session.userId,
                state = toolContext.snapshot(),
            )
        childSessionStore.save(toolContext.appName, childSession)

        val childRunner =
            Runner(
                app =
                    AdkApp(
                        name = toolContext.appName,
                        rootAgent = toolAgent,
                    ),
                model = model,
                sessionStore = childSessionStore,
                artifactService = artifactService,
                plugins = if (includePlugins) pluginManager.snapshotPlugins() else emptyList(),
                memoryService = memoryService,
                credentialService = credentialService,
                toolConfirmationHandler = toolConfirmationHandler,
            )

        val result =
            childRunner.run(
                userId = toolContext.session.userId,
                sessionId = toolContext.session.id,
                input = agentToolInputText(toolAgent, arguments),
            )

        computeStateDelta(childSession.state, result.session.state).forEach { (key, value) ->
            toolContext.remember(key, value)
        }

        val outputText = result.structuredResponse?.toString() ?: result.finalMessage
        return ToolOutput(
            content = outputText,
            skipSummarization = skipSummarization,
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
