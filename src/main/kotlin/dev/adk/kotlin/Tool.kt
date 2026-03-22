package dev.adk.kotlin

data class ToolParameter(
    val name: String,
    val description: String = "",
    val allowedValues: List<String> = emptyList(),
    val required: Boolean = true,
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: String? = null,
    val jsonSchema: ToolSchema? = null,
    val parameters: List<ToolParameter> = emptyList(),
    val isLongRunning: Boolean = false,
    val requiresConfirmation: Boolean = false,
    val confirmationHint: String = "",
    val customMetadata: Map<String, Any?> = emptyMap(),
)

data class ToolOutput(
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
    val skipSummarization: Boolean = false,
) {
    val text: String
        get() = content
}

val ToolDefinition.effectiveJsonSchema: ToolSchema?
    get() = jsonSchema ?: parameters.toToolSchema()

fun List<ToolParameter>.toToolSchema(): ToolSchema? {
    if (isEmpty()) {
        return null
    }

    return toolSchema {
        this@toToolSchema.forEach { parameter ->
            string(
                name = parameter.name,
                description = parameter.description,
                required = parameter.required,
                enumValues = parameter.allowedValues,
            )
        }
    }
}

interface Tool {
    val definition: ToolDefinition

    suspend fun execute(call: ToolCall, context: ToolContext): ToolOutput
}

typealias Context = ToolContext

class ToolContext internal constructor(
    val appName: String,
    val agent: LlmAgent,
    val session: AgentSession,
    private val workingState: MutableMap<String, String>,
    private val artifactService: ArtifactService,
    private val memoryService: MemoryService? = null,
    private val credentialService: CredentialService? = null,
    private val functionCallId: String? = null,
    val toolConfirmation: ToolConfirmation? = null,
    private val agentToolExecutor: suspend (ToolContext, LlmAgent, Map<String, Any?>, Boolean, Boolean) -> ToolOutput,
) {
    private val artifactDelta = linkedMapOf<String, Int>()
    private val requestedAuthConfigs = linkedMapOf<String, AuthConfig>()
    private val requestedToolConfirmations = linkedMapOf<String, ToolConfirmation>()

    val state: MutableMap<String, String>
        get() = workingState

    fun remember(key: String, value: String?) {
        if (value == null) {
            workingState.remove(key)
        } else {
            workingState[key] = value
        }
    }

    fun recall(key: String): String? = workingState[key]

    fun snapshot(): Map<String, String> = workingState.toMap()

    suspend fun saveArtifact(
        filename: String,
        artifact: Artifact,
    ): Int {
        val version =
            artifactService.saveArtifact(
            appName = appName,
            userId = session.userId,
            sessionId = session.id,
            filename = filename,
            artifact = artifact,
        )
        artifactDelta[filename] = version
        return version
    }

    suspend fun loadArtifact(
        filename: String,
        version: Int? = null,
    ): Artifact? =
        artifactService.loadArtifact(
            appName = appName,
            userId = session.userId,
            sessionId = session.id,
            filename = filename,
            version = version,
        )

    suspend fun listArtifacts(): List<String> =
        artifactService.listArtifactKeys(
            appName = appName,
            userId = session.userId,
            sessionId = session.id,
        )

    suspend fun searchMemory(query: String): SearchMemoryResponse {
        val service = memoryService ?: error("Memory service is not initialized.")
        return service.searchMemory(
            appName = appName,
            userId = session.userId,
            query = query,
        )
    }

    suspend fun saveCredential(
        authConfig: AuthConfig,
        credential: AuthCredential,
    ) {
        val service = credentialService ?: error("Credential service is not initialized.")
        service.saveCredential(
            authConfig = authConfig,
            appName = appName,
            userId = session.userId,
            credential = credential,
        )
    }

    suspend fun loadCredential(authConfig: AuthConfig): AuthCredential? {
        val service = credentialService ?: error("Credential service is not initialized.")
        return service.loadCredential(
            authConfig = authConfig,
            appName = appName,
            userId = session.userId,
        )
    }

    fun getAuthResponse(authConfig: AuthConfig): AuthCredential? = AuthHandler(authConfig).getAuthResponse(workingState)

    fun requestCredential(authConfig: AuthConfig) {
        val callId = requireNotNull(functionCallId) { "requestCredential requires a function call id." }
        requestedAuthConfigs[callId] = AuthHandler(authConfig).generateAuthRequest()
    }

    fun requestConfirmation(
        hint: String? = null,
        payload: Any? = null,
    ) {
        val callId = requireNotNull(functionCallId) { "requestConfirmation requires a function call id." }
        requestedToolConfirmations[callId] =
            ToolConfirmation(
                hint = hint.orEmpty(),
                payload = payload,
            )
    }

    fun requestConfirmation(hint: String) {
        requestConfirmation(hint = hint, payload = null)
    }

    fun requestConfirmation() {
        requestConfirmation(hint = null, payload = null)
    }

    suspend fun runAgentTool(
        agent: LlmAgent,
        arguments: Map<String, Any?>,
        skipSummarization: Boolean = false,
        includePlugins: Boolean = true,
    ): ToolOutput = agentToolExecutor(this, agent, arguments, skipSummarization, includePlugins)

    internal fun recordedArtifactDelta(): Map<String, Int> = artifactDelta.toMap()

    internal fun recordedRequestedAuthConfigs(): Map<String, AuthConfig> = requestedAuthConfigs.toMap()

    internal fun recordedRequestedToolConfirmations(): Map<String, ToolConfirmation> = requestedToolConfirmations.toMap()
}

private class LambdaTool(
    override val definition: ToolDefinition,
    private val block: suspend ToolContext.(ToolCall) -> ToolOutput,
) : Tool {
    override suspend fun execute(call: ToolCall, context: ToolContext): ToolOutput = context.block(call)
}

class AgentTool(
    val agent: LlmAgent,
    val skipSummarization: Boolean = false,
    val includePlugins: Boolean = true,
) : Tool {
    override val definition: ToolDefinition =
        ToolDefinition(
            name = agent.name,
            description = agent.description,
            jsonSchema = effectiveInputSchema(agent),
            parameters = effectiveInputSchema(agent)?.toToolParameters().orEmpty(),
        )

    override suspend fun execute(
        call: ToolCall,
        context: ToolContext,
    ): ToolOutput =
        context.runAgentTool(
            agent = agent,
            arguments = call.arguments,
            skipSummarization = skipSummarization,
            includePlugins = includePlugins,
        )
}

fun tool(
    name: String,
    description: String,
    inputSchema: String? = null,
    jsonSchema: ToolSchema? = null,
    parameters: List<ToolParameter> = emptyList(),
    isLongRunning: Boolean = false,
    requiresConfirmation: Boolean = false,
    confirmationHint: String = "",
    customMetadata: Map<String, Any?> = emptyMap(),
    block: suspend ToolContext.(ToolCall) -> ToolOutput,
): Tool =
    LambdaTool(
        definition = ToolDefinition(
            name = name,
            description = description,
            inputSchema = inputSchema,
            jsonSchema = jsonSchema,
            parameters = parameters,
            isLongRunning = isLongRunning,
            requiresConfirmation = requiresConfirmation,
            confirmationHint = confirmationHint,
            customMetadata = customMetadata,
        ),
        block = block,
    )

fun agentTool(
    agent: LlmAgent,
    skipSummarization: Boolean = false,
    includePlugins: Boolean = true,
): Tool = AgentTool(agent = agent, skipSummarization = skipSummarization, includePlugins = includePlugins)

fun ToolCall.requireArgument(name: String): String =
    arguments[name]?.toString() ?: error("Missing required tool argument: $name")

internal fun effectiveInputSchema(agent: LlmAgent): ToolSchema? {
    if (agent.executionKind == AgentExecutionKind.LLM) {
        return agent.inputSchema
    }

    return agent.subAgents.firstOrNull()?.let(::effectiveInputSchema)
}

internal fun effectiveOutputSchema(agent: LlmAgent): OutputSchema? {
    if (agent.executionKind == AgentExecutionKind.LLM) {
        return agent.outputSchema
    }

    return agent.subAgents.lastOrNull()?.let(::effectiveOutputSchema)
}

internal fun ToolSchema.toToolParameters(): List<ToolParameter> =
    properties.map { (name, schema) ->
        ToolParameter(
            name = name,
            description = schema.description,
            allowedValues = schema.enumValues,
            required = name in required,
        )
    }

internal fun agentToolInputText(
    agent: LlmAgent,
    arguments: Map<String, Any?>,
): String {
    val inputSchema = effectiveInputSchema(agent)
    return if (inputSchema != null) {
        serializeJsonValue(inputSchema.validateArguments(arguments))
    } else {
        arguments["request"]?.toString() ?: error("Missing required tool argument: request")
    }
}

private fun serializeJsonValue(value: Any?): String =
    when (value) {
        null -> "null"
        is String -> buildString {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }

        is Number, is Boolean -> value.toString()
        is Map<*, *> ->
            value.entries.joinToString(
                prefix = "{",
                postfix = "}",
                separator = ",",
            ) { (key, nestedValue) ->
                "${serializeJsonValue(key.toString())}:${serializeJsonValue(nestedValue)}"
            }
        is List<*> ->
            value.joinToString(
                prefix = "[",
                postfix = "]",
                separator = ",",
            ) { item ->
                serializeJsonValue(item)
            }
        else -> serializeJsonValue(value.toString())
    }
