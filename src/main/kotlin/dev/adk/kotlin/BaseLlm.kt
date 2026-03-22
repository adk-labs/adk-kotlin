package dev.adk.kotlin

abstract class BaseLlm(
    val model: String,
) : LanguageModel, SupportsModelCapabilities {
    open override val modelCapabilities: ModelCapabilities = ModelCapabilities()

    final override suspend fun generate(request: ModelRequest): ModelResponse =
        generateContent(request = request, stream = false)

    abstract suspend fun generateContent(
        request: ModelRequest,
        stream: Boolean = false,
    ): ModelResponse

    open fun connect(request: ModelRequest): BaseLlmConnection =
        throw UnsupportedOperationException("Live connection is not supported for $model.")
}

interface BaseLlmConnection
