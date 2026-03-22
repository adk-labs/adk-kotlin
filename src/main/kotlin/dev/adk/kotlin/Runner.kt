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
    val toolExecutions: List<ToolExecution>,
)

class Runner(
    private val app: AdkApp,
    private val model: LanguageModel,
    private val sessionStore: SessionStore = InMemorySessionStore(),
) {
    internal companion object {
        const val TRANSFER_TO_AGENT_TOOL = "transfer_to_agent"
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
                    ),
                )

            when (response) {
                is ModelResponse.Final -> {
                    transcript = transcript + ModelMessage(response.message)
                    val savedSession =
                        workingSession.copy(
                            state = workingState.toMap(),
                            transcript = transcript,
                            updatedAt = Instant.now(),
                        )
                    sessionStore.save(app.name, savedSession)
                    return RunResult(
                        session = savedSession,
                        finalMessage = response.message,
                        finalAgentName = activeAgent.name,
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
}
