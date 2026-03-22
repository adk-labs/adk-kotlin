package dev.adk.kotlin

fun interface LanguageModel {
    suspend fun generate(request: ModelRequest): ModelResponse
}

data class ModelRequest(
    val appName: String,
    val session: AgentSession,
    val agent: LlmAgent,
    val availableTools: List<ToolDefinition>,
) {
    val systemInstruction: String = agent.renderSystemInstruction()
}

sealed interface ModelResponse {
    data class Final(
        val message: String,
    ) : ModelResponse

    data class ToolCalls(
        val calls: List<ToolCall>,
    ) : ModelResponse
}

data class ToolCall(
    val toolName: String,
    val arguments: Map<String, String> = emptyMap(),
)
