package dev.adk.kotlin

fun apigeeLlm(
    modelName: String,
    proxyUrl: String? = null,
    customHeaders: Map<String, String> = emptyMap(),
    transport: LlmTransport? = null,
    connectionFactory: LlmConnectionFactory? = null,
    modelCapabilities: ModelCapabilities = Gemini.DEFAULT_MODEL_CAPABILITIES,
): ApigeeLlm =
    ApigeeLlm.builder()
        .modelName(modelName)
        .proxyUrl(proxyUrl)
        .customHeaders(customHeaders)
        .transport(transport)
        .connectionFactory(connectionFactory)
        .modelCapabilities(modelCapabilities)
        .build()

class ApigeeLlm private constructor(
    modelName: String,
    val proxyUrl: String? = null,
    val customHeaders: Map<String, String> = emptyMap(),
    transport: LlmTransport? = null,
    connectionFactory: LlmConnectionFactory? = null,
    modelCapabilities: ModelCapabilities = Gemini.DEFAULT_MODEL_CAPABILITIES,
) : TransportBackedLlm(
        model = modelName,
        providerName = "ApigeeLlm",
        modelCapabilities = modelCapabilities,
        transport = transport,
        connectionFactory = connectionFactory,
    ) {
    init {
        require(isValidModelName(modelName)) {
            "Invalid model string, expected apigee/[<provider>/][<version>/]<model_id>: $modelName"
        }
    }

    val effectiveProxyUrl: String?
        get() = proxyUrl ?: System.getenv(APIGEE_PROXY_URL_ENV_VARIABLE_NAME)?.takeIf { it.isNotBlank() }

    val usesVertexAi: Boolean
        get() = isVertexAiModel(model)

    val apiVersion: String?
        get() = identifyApiVersion(model)

    companion object {
        const val GOOGLE_GENAI_USE_VERTEXAI_ENV_VARIABLE_NAME = "GOOGLE_GENAI_USE_VERTEXAI"
        const val APIGEE_PROXY_URL_ENV_VARIABLE_NAME = "APIGEE_PROXY_URL"

        @JvmStatic
        fun builder(): Builder = Builder()

        fun isValidModelName(modelName: String): Boolean {
            if (!modelName.startsWith("apigee/")) {
                return false
            }

            val components = modelName.removePrefix("apigee/").split('/')
            if (components.any { it.isBlank() }) {
                return false
            }

            return when (components.size) {
                1 -> true
                2 ->
                    (components[0].isProvider() && !components[1].matchesApiVersion()) ||
                        components[0].matchesApiVersion()
                3 -> components[0].isProvider() && components[1].matchesApiVersion()
                else -> false
            }
        }

        fun isVertexAiModel(modelName: String): Boolean =
            !modelName.startsWith("apigee/gemini/") &&
                (modelName.startsWith("apigee/vertex_ai/") || isEnvEnabled(GOOGLE_GENAI_USE_VERTEXAI_ENV_VARIABLE_NAME))

        fun identifyApiVersion(modelName: String): String? {
            val components = modelName.removePrefix("apigee/").split('/')
            return when (components.size) {
                2 -> components[0].takeIf { it.matchesApiVersion() }
                3 -> components[1].takeIf { it.matchesApiVersion() }
                else -> null
            }
        }

        private fun String.isProvider(): Boolean = this == "vertex_ai" || this == "gemini"

        private fun String.matchesApiVersion(): Boolean = API_VERSION_PATTERN.matches(this)

        private fun isEnvEnabled(name: String): Boolean =
            when (System.getenv(name)?.trim()?.lowercase()) {
                "1", "true", "yes", "on" -> true
                else -> false
            }

        private val API_VERSION_PATTERN = Regex("^v[0-9].*")
    }

    class Builder {
        private var modelName: String? = null
        private var proxyUrl: String? = null
        private var customHeaders: Map<String, String> = emptyMap()
        private var transport: LlmTransport? = null
        private var connectionFactory: LlmConnectionFactory? = null
        private var modelCapabilities: ModelCapabilities = Gemini.DEFAULT_MODEL_CAPABILITIES

        fun modelName(value: String): Builder =
            apply {
                modelName = value.trim()
            }

        fun proxyUrl(value: String?): Builder =
            apply {
                proxyUrl = value?.trim()?.takeIf { it.isNotEmpty() }
            }

        fun customHeaders(value: Map<String, String>): Builder =
            apply {
                customHeaders =
                    value
                        .mapNotNull { (key, headerValue) ->
                            key.trim().takeIf { it.isNotEmpty() }?.let { normalizedKey ->
                                normalizedKey to headerValue
                            }
                        }.toMap()
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

        fun build(): ApigeeLlm =
            ApigeeLlm(
                modelName = requireNotNull(modelName) { "modelName must be set." },
                proxyUrl = proxyUrl,
                customHeaders = customHeaders,
                transport = transport,
                connectionFactory = connectionFactory,
                modelCapabilities = modelCapabilities,
            )
    }
}
