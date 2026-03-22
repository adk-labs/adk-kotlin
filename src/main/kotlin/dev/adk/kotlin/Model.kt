package dev.adk.kotlin

fun interface LanguageModel {
    suspend fun generate(request: ModelRequest): ModelResponse
}

data class ModelRequest(
    val model: String,
    val appName: String,
    val session: AgentSession,
    val agent: LlmAgent,
    val systemInstructions: List<String>,
    val conversation: List<Message>,
    val availableTools: List<ToolDefinition>,
    val config: GenerateContentConfig? = null,
    val outputSchema: OutputSchema? = null,
) {
    companion object {
        const val JSON_RESPONSE_MIME_TYPE = "application/json"
    }

    val responseMimeType: String? = config?.responseMimeType

    val systemInstruction: String? =
        systemInstructions
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n\n")
}

typealias LlmRequest = ModelRequest
typealias LlmResponse = ModelResponse

sealed interface ModelResponse {
    data class Final(
        val message: String,
        val structuredResponse: Any? = null,
    ) : ModelResponse

    data class ToolCalls(
        val calls: List<ToolCall>,
    ) : ModelResponse
}

data class ToolCall(
    val toolName: String,
    val arguments: Map<String, Any?> = emptyMap(),
)
