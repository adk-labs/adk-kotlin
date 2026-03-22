package dev.adk.kotlin

data class ReadonlyContext(
    val appName: String,
    val agent: LlmAgent,
    val session: AgentSession,
)

typealias Toolset = BaseToolset

abstract class BaseToolset(
    private val toolFilter: List<String>? = null,
    private val toolNamePrefix: String? = null,
) : AutoCloseable {
    abstract suspend fun getTools(readonlyContext: ReadonlyContext? = null): List<Tool>

    suspend fun getToolsWithPrefix(readonlyContext: ReadonlyContext? = null): List<Tool> {
        val tools =
            getTools(readonlyContext).filter { tool ->
                toolFilter?.contains(tool.definition.name) ?: true
            }

        val prefix = toolNamePrefix ?: return tools
        return tools.map { tool -> PrefixedTool(tool = tool, prefix = prefix) }
    }

    open suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest = llmRequest

    open fun getAuthConfig(): AuthConfig? = null

    override fun close() {}
}

private class PrefixedTool(
    private val tool: Tool,
    prefix: String,
) : Tool {
    override val definition: ToolDefinition = tool.definition.copy(name = "${prefix}_${tool.definition.name}")

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest = tool.processLlmRequest(toolContext, llmRequest)

    override suspend fun execute(
        call: ToolCall,
        context: ToolContext,
    ): ToolOutput = tool.execute(call, context)
}
