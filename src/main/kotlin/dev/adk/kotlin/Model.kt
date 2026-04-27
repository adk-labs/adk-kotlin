package dev.adk.kotlin

data class Model(
    val modelName: String? = null,
    val model: BaseLlm? = null,
) {
    init {
        val hasModelName = !modelName.isNullOrBlank()
        val hasModel = model != null
        require(hasModelName.xor(hasModel)) {
            "Model must declare exactly one of modelName or model."
        }
    }

    fun resolvedModelName(): String = modelName ?: requireNotNull(model).model

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var modelName: String? = null
        private var model: BaseLlm? = null

        fun modelName(value: String): Builder =
            apply {
                modelName = value.trim()
            }

        fun model(value: BaseLlm): Builder =
            apply {
                model = value
            }

        fun build(): Model =
            Model(
                modelName = modelName?.takeIf { it.isNotBlank() },
                model = model,
            )
    }
}

fun model(modelName: String): Model =
    Model(
        modelName = modelName.trim().takeIf { it.isNotEmpty() },
    )

fun model(model: BaseLlm): Model =
    Model(
        model = model,
    )

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
