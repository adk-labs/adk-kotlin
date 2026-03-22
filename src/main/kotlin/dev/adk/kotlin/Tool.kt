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
    val parameters: List<ToolParameter> = emptyList(),
)

data class ToolOutput(
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
)

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
) {
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
    ): Int =
        artifactService.saveArtifact(
            appName = appName,
            userId = session.userId,
            sessionId = session.id,
            filename = filename,
            artifact = artifact,
        )

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
    parameters: List<ToolParameter> = emptyList(),
    block: suspend ToolContext.(ToolCall) -> ToolOutput,
): Tool =
    LambdaTool(
        definition = ToolDefinition(
            name = name,
            description = description,
            inputSchema = inputSchema,
            parameters = parameters,
        ),
        block = block,
    )

fun ToolCall.requireArgument(name: String): String =
    arguments[name]?.toString() ?: error("Missing required tool argument: $name")
