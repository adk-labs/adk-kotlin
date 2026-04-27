package dev.adk.kotlin

fun gemini(
    modelName: String,
    apiKey: String? = null,
    vertexCredentials: VertexCredentials? = null,
    transport: LlmTransport? = null,
    connectionFactory: LlmConnectionFactory? = null,
    modelCapabilities: ModelCapabilities = Gemini.DEFAULT_MODEL_CAPABILITIES,
): Gemini =
    Gemini.builder()
        .modelName(modelName)
        .apiKey(apiKey)
        .vertexCredentials(vertexCredentials)
        .transport(transport)
        .connectionFactory(connectionFactory)
        .modelCapabilities(modelCapabilities)
        .build()

class Gemini private constructor(
    modelName: String,
    val apiKey: String? = null,
    val vertexCredentials: VertexCredentials? = null,
    transport: LlmTransport? = null,
    connectionFactory: LlmConnectionFactory? = null,
    modelCapabilities: ModelCapabilities = DEFAULT_MODEL_CAPABILITIES,
) : TransportBackedLlm(
        model = modelName,
        providerName = "Gemini",
        modelCapabilities = modelCapabilities,
        transport = transport,
        connectionFactory = connectionFactory,
    ) {
    companion object {
        val DEFAULT_MODEL_CAPABILITIES =
            ModelCapabilities(
                supportsOutputSchemaWithTools = true,
            )

        @JvmStatic
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var modelName: String? = null
        private var apiKey: String? = null
        private var vertexCredentials: VertexCredentials? = null
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

        fun vertexCredentials(value: VertexCredentials?): Builder =
            apply {
                vertexCredentials = value
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

        fun build(): Gemini =
            Gemini(
                modelName = requireNotNull(modelName) { "modelName must be set." },
                apiKey = apiKey,
                vertexCredentials = vertexCredentials,
                transport = transport,
                connectionFactory = connectionFactory,
                modelCapabilities = modelCapabilities,
            )
    }
}
