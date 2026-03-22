package dev.adk.kotlin

data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: String? = null,
)

data class ToolOutput(
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
)

interface Tool {
    val definition: ToolDefinition

    suspend fun execute(call: ToolCall, context: ToolContext): ToolOutput
}

class ToolContext internal constructor(
    val appName: String,
    val agent: LlmAgent,
    val session: AgentSession,
    private val workingState: MutableMap<String, String>,
) {
    fun remember(key: String, value: String?) {
        if (value == null) {
            workingState.remove(key)
        } else {
            workingState[key] = value
        }
    }

    fun recall(key: String): String? = workingState[key]

    fun snapshot(): Map<String, String> = workingState.toMap()
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
    block: suspend ToolContext.(ToolCall) -> ToolOutput,
): Tool =
    LambdaTool(
        definition = ToolDefinition(
            name = name,
            description = description,
            inputSchema = inputSchema,
        ),
        block = block,
    )

fun ToolCall.requireArgument(name: String): String =
    arguments[name] ?: error("Missing required tool argument: $name")
