package dev.adk.kotlin

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HttpOptions(
    val baseUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val timeoutMillis: Long? = null,
) {
    init {
        require(baseUrl.isNotBlank()) { "httpOptions.baseUrl() must be set" }
        require(timeoutMillis == null || timeoutMillis >= 0) {
            "httpOptions.timeout() must be non-negative"
        }
    }
}

class ChatCompletionsHttpClient(
    httpOptions: HttpOptions,
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
) {
    val completionsUri: URI = resolveCompletionsUri(httpOptions.baseUrl)
    val headers: Map<String, String> = httpOptions.headers.toMap()
    val callTimeout: Duration = resolveCallTimeout(httpOptions.timeoutMillis)

    suspend fun complete(
        llmRequest: LlmRequest,
        stream: Boolean,
    ): LlmResponse {
        if (stream) {
            throw UnsupportedOperationException("Streaming is not yet implemented in this client.")
        }

        val payload = GSON.toJson(ChatCompletionRequest.fromLlmRequest(llmRequest, stream))
        val requestBuilder =
            HttpRequest
                .newBuilder(completionsUri)
                .POST(HttpRequest.BodyPublishers.ofString(payload))

        headers.forEach { (key, value) -> requestBuilder.header(key, value) }
        requestBuilder.setHeader("Content-Type", JSON_CONTENT_TYPE)
        if (!callTimeout.isZero) {
            requestBuilder.timeout(callTimeout)
        }

        val response =
            withContext(Dispatchers.IO) {
                httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
            }

        if (response.statusCode() !in 200..299) {
            throw IOException("Unexpected code ${response.statusCode()} - body: ${response.body()}")
        }
        if (response.body().isNullOrBlank()) {
            throw IOException("Empty response body")
        }

        return ChatCompletionResponse.parse(response.body())
    }

    fun asTransport(): LlmTransport =
        LlmTransport { request, stream ->
            complete(request, stream)
        }

    companion object {
        const val REFUSAL_PREFIX = "[[REFUSAL]]: "
        const val DEFAULT_CALL_TIMEOUT_MILLIS = 300_000L
        private const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
        private val GSON = Gson()

        private fun resolveCallTimeout(timeoutMillis: Long?): Duration =
            when (timeoutMillis) {
                null -> Duration.ofMillis(DEFAULT_CALL_TIMEOUT_MILLIS)
                0L -> Duration.ZERO
                else -> Duration.ofMillis(timeoutMillis)
            }

        private fun resolveCompletionsUri(baseUrl: String): URI {
            val baseUri =
                try {
                    URI(baseUrl.trim())
                } catch (error: Exception) {
                    throw IllegalArgumentException("httpOptions.baseUrl() is not a valid HTTP(S) URL: $baseUrl", error)
                }
            require(baseUri.scheme == "http" || baseUri.scheme == "https") {
                "httpOptions.baseUrl() is not a valid HTTP(S) URL: $baseUrl"
            }
            require(!baseUri.host.isNullOrBlank()) {
                "httpOptions.baseUrl() is not a valid HTTP(S) URL: $baseUrl"
            }

            val basePath = baseUri.rawPath.orEmpty().trimEnd('/')
            val completionsPath =
                buildString {
                    if (basePath.isBlank()) {
                        append('/')
                    } else {
                        append(basePath).append('/')
                    }
                    append("chat/completions")
                }
            return URI(baseUri.scheme, baseUri.rawAuthority, completionsPath, null, null)
        }
    }
}

fun chatCompletionsTransport(httpOptions: HttpOptions): LlmTransport =
    ChatCompletionsHttpClient(httpOptions).asTransport()

private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean,
    val tools: List<ChatTool>? = null,
    val temperature: Double? = null,
    @SerializedName("top_p")
    val topP: Double? = null,
    @SerializedName("max_tokens")
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
    @SerializedName("response_format")
    val responseFormat: Map<String, String>? = null,
) {
    companion object {
        fun fromLlmRequest(
            request: LlmRequest,
            stream: Boolean,
        ): ChatCompletionRequest {
            val config = request.config
            return ChatCompletionRequest(
                model = request.model,
                messages = request.toChatMessages(),
                stream = stream,
                tools = request.availableTools.toChatTools().takeIf { it.isNotEmpty() },
                temperature = config?.temperature,
                topP = config?.topP,
                maxTokens = config?.maxOutputTokens,
                stop = config?.stopSequences?.takeIf { it.isNotEmpty() },
                responseFormat =
                    if (config?.responseMimeType == ModelRequest.JSON_RESPONSE_MIME_TYPE) {
                        mapOf("type" to "json_object")
                    } else {
                        null
                    },
            )
        }
    }
}

private data class ChatMessage(
    val role: String,
    val content: String,
)

private data class ChatTool(
    val type: String = "function",
    val function: ChatToolFunction,
)

private data class ChatToolFunction(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>,
)

private object ChatCompletionResponse {
    fun parse(json: String): ModelResponse {
        val root = JsonParser.parseString(json).asJsonObject
        val choices = root.getAsJsonArray("choices")
        if (choices == null || choices.size() == 0) {
            return ModelResponse.Final("")
        }

        val message = choices[0].asJsonObject.getAsJsonObject("message") ?: return ModelResponse.Final("")
        val toolCalls = message.getAsJsonArray("tool_calls")
        if (toolCalls != null && toolCalls.size() > 0) {
            return ModelResponse.ToolCalls(
                calls =
                    toolCalls.mapNotNull { callElement ->
                        callElement.asJsonObject.toToolCall()
                    },
            )
        }

        return ModelResponse.Final(message.toTextContent())
    }

    private fun JsonObject.toToolCall(): ToolCall? {
        val function = getAsJsonObject("function") ?: return null
        val name = function.get("name")?.asString?.takeIf { it.isNotBlank() } ?: return null
        val arguments =
            function
                .get("arguments")
                ?.asString
                ?.takeIf { it.isNotBlank() }
                ?.let(::parseArguments)
                ?: emptyMap()
        return ToolCall(toolName = name, arguments = arguments)
    }

    private fun JsonObject.toTextContent(): String {
        val content = get("content")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        val refusal = get("refusal")?.takeUnless { it.isJsonNull }?.asString
        return when {
            refusal.isNullOrEmpty() -> content
            content.isBlank() -> ChatCompletionsHttpClient.REFUSAL_PREFIX + refusal
            else -> content + "\n" + ChatCompletionsHttpClient.REFUSAL_PREFIX + refusal
        }
    }

    private fun parseArguments(json: String): Map<String, Any?> {
        val parsed = JsonParser.parseString(json)
        if (!parsed.isJsonObject) {
            return emptyMap()
        }
        @Suppress("UNCHECKED_CAST")
        return parsed.asJsonObject.toKotlinValue() as Map<String, Any?>
    }
}

private fun LlmRequest.toChatMessages(): List<ChatMessage> {
    val messages = mutableListOf<ChatMessage>()
    systemInstruction?.takeIf { it.isNotBlank() }?.let { instruction ->
        messages += ChatMessage(role = "system", content = instruction)
    }
    conversation.mapTo(messages) { message ->
        when (message) {
            is UserMessage -> ChatMessage(role = "user", content = message.text)
            is ModelMessage -> ChatMessage(role = "assistant", content = message.text)
            is ToolMessage -> ChatMessage(role = "tool", content = message.text)
        }
    }
    return messages
}

private fun List<ToolDefinition>.toChatTools(): List<ChatTool> =
    filterNot { it.isHidden }
        .map { definition ->
            ChatTool(
                function =
                    ChatToolFunction(
                        name = definition.name,
                        description = definition.description,
                        parameters =
                            definition.effectiveJsonSchema?.toJsonSchemaMap()
                                ?: mapOf("type" to "object", "properties" to emptyMap<String, Any?>()),
                    ),
            )
        }

private fun ToolSchema.toJsonSchemaMap(): Map<String, Any?> =
    buildMap {
        put("type", type.name.lowercase())
        if (description.isNotBlank()) {
            put("description", description)
        }
        if (enumValues.isNotEmpty()) {
            put("enum", enumValues)
        }
        if (nullable) {
            put("nullable", true)
        }
        if (type == ToolSchemaType.OBJECT) {
            put("properties", properties.mapValues { (_, schema) -> schema.toJsonSchemaMap() })
            if (required.isNotEmpty()) {
                put("required", required)
            }
        }
        if (type == ToolSchemaType.ARRAY) {
            put("items", requireNotNull(items) { "Array schema must declare items." }.toJsonSchemaMap())
        }
    }

private fun JsonElement.toKotlinValue(): Any? =
    when {
        isJsonNull -> null
        isJsonObject ->
            asJsonObject.entrySet().associate { (key, value) ->
                key to value.toKotlinValue()
            }
        isJsonArray -> asJsonArray.map { it.toKotlinValue() }
        isJsonPrimitive -> {
            val primitive = asJsonPrimitive
            when {
                primitive.isBoolean -> primitive.asBoolean
                primitive.isNumber -> primitive.asNumber.toBestNumber()
                primitive.isString -> primitive.asString
                else -> primitive.asString
            }
        }
        else -> toString()
    }

private fun Number.toBestNumber(): Number {
    val text = toString()
    if (text.contains('.') || text.contains('e', ignoreCase = true)) {
        return text.toDouble()
    }
    return text.toLongOrNull()?.let { value ->
        if (value in Int.MIN_VALUE..Int.MAX_VALUE) {
            value.toInt()
        } else {
            value
        }
    } ?: text.toDouble()
}
