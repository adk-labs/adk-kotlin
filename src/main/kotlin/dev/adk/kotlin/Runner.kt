package dev.adk.kotlin

import java.time.Instant

data class ToolExecution(
    val call: ToolCall,
    val output: ToolOutput,
)

data class RunResult(
    val session: AgentSession,
    val finalMessage: String,
    val toolExecutions: List<ToolExecution>,
)

class Runner(
    private val app: AdkApp,
    private val model: LanguageModel,
    private val sessionStore: SessionStore = InMemorySessionStore(),
) {
    suspend fun run(
        userId: String,
        input: String,
        sessionId: String? = null,
    ): RunResult {
        require(userId.isNotBlank()) { "userId cannot be blank." }
        require(input.isNotBlank()) { "input cannot be blank." }

        val agent = app.rootAgent
        val baseSession = sessionStore.getOrCreate(app.name, userId, sessionId)
        val workingState = baseSession.state.toMutableMap()
        val toolExecutions = mutableListOf<ToolExecution>()
        var transcript: List<Message> = baseSession.transcript + UserMessage(input)

        repeat(agent.maxIterations) {
            val workingSession =
                baseSession.copy(
                    state = workingState.toMap(),
                    transcript = transcript,
                    updatedAt = Instant.now(),
                )

            val response =
                model.generate(
                    ModelRequest(
                        appName = app.name,
                        session = workingSession,
                        agent = agent,
                        availableTools = agent.tools.map { tool -> tool.definition },
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
                        toolExecutions = toolExecutions.toList(),
                    )
                }

                is ModelResponse.ToolCalls -> {
                    require(response.calls.isNotEmpty()) {
                        "Model returned an empty tool call list."
                    }

                    response.calls.forEach { call ->
                        val tool =
                            agent.tools.find { it.definition.name == call.toolName }
                                ?: error("Unknown tool requested: ${call.toolName}")

                        val context =
                            ToolContext(
                                appName = app.name,
                                agent = agent,
                                session = workingSession,
                                workingState = workingState,
                            )
                        val output = tool.execute(call, context)

                        toolExecutions += ToolExecution(call = call, output = output)
                        transcript = transcript + ToolMessage(toolName = call.toolName, text = output.content)
                    }
                }
            }
        }

        error("Agent ${agent.name} exceeded maxIterations=${agent.maxIterations}.")
    }
}
