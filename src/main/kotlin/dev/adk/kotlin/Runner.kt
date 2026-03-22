package dev.adk.kotlin

import java.time.Instant

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
)

class Runner(
    private val app: AdkApp,
    private val model: LanguageModel,
    private val sessionStore: SessionStore = InMemorySessionStore(),
    val artifactService: ArtifactService = InMemoryArtifactService(),
) {
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
        val baseSession = sessionStore.getOrCreate(app.name, userId, sessionId)
        val workingState = baseSession.state.toMutableMap()
        val toolExecutions = mutableListOf<ToolExecution>()
        var transcript: List<Message> = baseSession.transcript + UserMessage(input)
        var activeAgent = rootAgent

        repeat(rootAgent.maxIterations) {
            val workingSession =
                baseSession.copy(
                    state = workingState.toMap(),
                    transcript = transcript,
                    updatedAt = Instant.now(),
                )

            val response =
                model.generate(
                    PromptAssembler.createRequest(
                        app = app,
                        agent = activeAgent,
                        session = workingSession,
                        transcript = transcript,
                        artifactService = artifactService,
                        includeOutputSchemaWorkaround = shouldUseOutputSchemaWorkaround(activeAgent),
                    ),
                )

            when (response) {
                is ModelResponse.Final -> {
                    val structuredResponse =
                        validateStructuredResponse(
                            agent = activeAgent,
                            structuredResponse = response.structuredResponse,
                        )
                    val finalMessage =
                        response.message.ifBlank {
                            structuredResponse?.toString().orEmpty()
                        }
                    transcript = transcript + ModelMessage(finalMessage)
                    val savedSession =
                        workingSession.copy(
                            state = workingState.toMap(),
                            transcript = transcript,
                            updatedAt = Instant.now(),
                        )
                    sessionStore.save(app.name, savedSession)
                    return RunResult(
                        session = savedSession,
                        finalMessage = finalMessage,
                        finalAgentName = activeAgent.name,
                        structuredResponse = structuredResponse,
                        toolExecutions = toolExecutions.toList(),
                    )
                }

                is ModelResponse.ToolCalls -> {
                    require(response.calls.isNotEmpty()) {
                        "Model returned an empty tool call list."
                    }

                    val transferCall = response.calls.singleOrNull { it.toolName == TRANSFER_TO_AGENT_TOOL }
                    if (transferCall != null) {
                        require(response.calls.size == 1) {
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
                        transcript =
                            transcript +
                                ToolMessage(
                                    toolName = TRANSFER_TO_AGENT_TOOL,
                                    text = output.content,
                                )
                        activeAgent = nextAgent
                        return@repeat
                    }

                    val setModelResponseCall = response.calls.singleOrNull { it.toolName == SET_MODEL_RESPONSE_TOOL }
                    if (setModelResponseCall != null) {
                        require(response.calls.size == 1) {
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
                        transcript = transcript + ModelMessage(finalMessage)

                        val savedSession =
                            workingSession.copy(
                                state = workingState.toMap(),
                                transcript = transcript,
                                updatedAt = Instant.now(),
                            )
                        sessionStore.save(app.name, savedSession)
                        return RunResult(
                            session = savedSession,
                            finalMessage = finalMessage,
                            finalAgentName = activeAgent.name,
                            structuredResponse = structuredResponse,
                            toolExecutions = toolExecutions.toList(),
                        )
                    }

                    response.calls.forEach { call ->
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
                            )
                        val output = tool.execute(call, context)

                        toolExecutions +=
                            ToolExecution(
                                agentName = activeAgent.name,
                                call = call,
                                output = output,
                            )
                        transcript = transcript + ToolMessage(toolName = call.toolName, text = output.content)
                    }
                }
            }
        }

        error("Agent ${rootAgent.name} exceeded maxIterations=${rootAgent.maxIterations}.")
    }

    private fun shouldUseOutputSchemaWorkaround(agent: LlmAgent): Boolean {
        if (agent.outputSchema == null || agent.tools.isEmpty()) {
            return false
        }

        val capabilities = (model as? SupportsModelCapabilities)?.modelCapabilities
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
}
