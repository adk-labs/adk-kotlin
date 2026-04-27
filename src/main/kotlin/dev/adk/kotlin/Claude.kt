package dev.adk.kotlin

fun claude(
    modelName: String,
    apiKey: String? = null,
    maxTokens: Int = Claude.DEFAULT_MAX_TOKENS,
    transport: LlmTransport? = null,
    connectionFactory: LlmConnectionFactory? = null,
    modelCapabilities: ModelCapabilities = Claude.DEFAULT_MODEL_CAPABILITIES,
): Claude =
    Claude.builder()
        .modelName(modelName)
        .apiKey(apiKey)
        .maxTokens(maxTokens)
        .transport(transport)
        .connectionFactory(connectionFactory)
        .modelCapabilities(modelCapabilities)
        .build()

class Claude private constructor(
    modelName: String,
    val apiKey: String? = null,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    transport: LlmTransport? = null,
    connectionFactory: LlmConnectionFactory? = null,
    modelCapabilities: ModelCapabilities = DEFAULT_MODEL_CAPABILITIES,
) : TransportBackedLlm(
        model = modelName,
        providerName = "Claude",
        modelCapabilities = modelCapabilities,
        transport = transport,
        connectionFactory = connectionFactory,
    ) {
    companion object {
        const val DEFAULT_MAX_TOKENS = 8192

        val DEFAULT_MODEL_CAPABILITIES = ModelCapabilities()

        @JvmStatic
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var modelName: String? = null
        private var apiKey: String? = null
        private var maxTokens: Int = DEFAULT_MAX_TOKENS
        private var transport: LlmTransport? = null
        private var connectionFactory: LlmConnectionFactory? = null
        private var modelCapabilities: ModelCapabilities = DEFAULT_MODEL_CAPABILITIES

        fun modelName(value: String): Builder =
            apply {
                modelName = value.trim()
            }

        fun apiKey(value: String?): Builder =
            apply {
                apiKey = value?.trim()?.takeIf { it.isNotEmpty() }
            }

        fun maxTokens(value: Int): Builder =
            apply {
                require(value > 0) { "maxTokens must be positive." }
                maxTokens = value
            }

        fun transport(value: LlmTransport?): Builder =
            apply {
                transport = value
            }

        fun connectionFactory(value: LlmConnectionFactory?): Builder =
            apply {
                connectionFactory = value
            }

        fun modelCapabilities(value: ModelCapabilities): Builder =
            apply {
                modelCapabilities = value
            }

        fun build(): Claude =
            Claude(
                modelName = requireNotNull(modelName) { "modelName must be set." },
                apiKey = apiKey,
                maxTokens = maxTokens,
                transport = transport,
                connectionFactory = connectionFactory,
                modelCapabilities = modelCapabilities,
            )
    }
}
