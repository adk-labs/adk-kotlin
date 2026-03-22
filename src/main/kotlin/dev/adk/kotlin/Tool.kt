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
    val customMetadata: Map<String, Any?> = emptyMap(),
)

data class ToolOutput(
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
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
) {
    private val artifactDelta = linkedMapOf<String, Int>()

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

    internal fun recordedArtifactDelta(): Map<String, Int> = artifactDelta.toMap()
}

private class LambdaTool(
    override val definition: ToolDefinition,
    private val block: suspend ToolContext.(ToolCall) -> ToolOutput,
) : Tool {
    override suspend fun execute(call: ToolCall, context: ToolContext): ToolOutput = context.block(call)
}

fun tool(
    name: String,
    description: String,
    inputSchema: String? = null,
    jsonSchema: ToolSchema? = null,
    parameters: List<ToolParameter> = emptyList(),
    isLongRunning: Boolean = false,
    requiresConfirmation: Boolean = false,
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
            customMetadata = customMetadata,
        ),
        block = block,
    )

fun ToolCall.requireArgument(name: String): String =
    arguments[name]?.toString() ?: error("Missing required tool argument: $name")
