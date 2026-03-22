package dev.adk.kotlin

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.format.DateTimeFormatter

private data class DebugEntry(
    val timestamp: Instant = Instant.now(),
    val type: String,
    val agentName: String? = null,
    val data: Map<String, String> = emptyMap(),
)

private data class InvocationDebugState(
    val invocationId: String,
    val sessionId: String,
    val appName: String,
    val userId: String,
    val startTime: Instant = Instant.now(),
    val entries: MutableList<DebugEntry> = mutableListOf(),
)

class DebugLoggingPlugin(
    name: String = "debug_logging_plugin",
    private val outputPath: Path = Path.of("adk_debug.yaml"),
    private val includeSessionState: Boolean = true,
    private val includeSystemInstruction: Boolean = true,
) : BasePlugin(name) {
    private val invocationStates = linkedMapOf<String, InvocationDebugState>()

    override suspend fun beforeRunCallback(invocationContext: InvocationContext): Event? {
        ensureState(invocationContext)
        return null
    }

    override suspend fun onUserMessageCallback(
        invocationContext: InvocationContext,
        userMessage: UserMessage,
    ): UserMessage? {
        ensureState(invocationContext)
        addEntry(
            invocationId = invocationContext.invocationId,
            type = "user_message",
            data =
                mapOf(
                    "text" to userMessage.text,
                    ),
        )
        return null
    }

    override suspend fun processLlmRequest(
        callbackContext: CallbackContext,
        llmRequest: LlmRequest,
    ): LlmRequest {
        addEntry(
            invocationId = callbackContext.invocationId,
            type = "llm_request",
            agentName = callbackContext.agent.name,
            data =
                buildMap {
                    put("model", llmRequest.model)
                    put("available_tools", llmRequest.availableTools.joinToString(",") { it.name })
                    if (includeSystemInstruction) {
                        put("system_instruction", llmRequest.systemInstruction.orEmpty())
                    }
                    put("conversation", llmRequest.conversation.joinToString("\n") { "${it.role}: ${it.text}" })
                },
        )
        return llmRequest
    }

    override suspend fun afterModelCallback(
        callbackContext: CallbackContext,
        llmResponse: LlmResponse,
    ): LlmResponse? {
        addEntry(
            invocationId = callbackContext.invocationId,
            type = "llm_response",
            agentName = callbackContext.agent.name,
            data =
                when (llmResponse) {
                    is ModelResponse.Final ->
                        mapOf(
                            "kind" to "final",
                            "message" to llmResponse.message,
                            "structured_response" to llmResponse.structuredResponse.toString(),
                        )

                    is ModelResponse.ToolCalls ->
                        mapOf(
                            "kind" to "tool_calls",
                            "tool_calls" to llmResponse.calls.joinToString(",") { it.toolName },
                        )
                },
        )
        return null
    }

    override suspend fun beforeToolCallback(
        tool: Tool,
        toolCall: ToolCall,
        toolContext: ToolContext,
    ): ToolOutput? {
        addEntry(
            invocationId = toolContext.invocationId ?: toolContext.session.id,
            type = "before_tool",
            agentName = toolContext.agent.name,
            data =
                mapOf(
                    "tool_name" to tool.definition.name,
                    "arguments" to toolCall.arguments.toString(),
                ),
        )
        return null
    }

    override suspend fun afterToolCallback(
        tool: Tool,
        toolCall: ToolCall,
        toolContext: ToolContext,
        result: ToolOutput,
    ): ToolOutput? {
        addEntry(
            invocationId = toolContext.invocationId ?: toolContext.session.id,
            type = "after_tool",
            agentName = toolContext.agent.name,
            data =
                mapOf(
                    "tool_name" to tool.definition.name,
                    "result" to result.content,
                ),
        )
        return null
    }

    override suspend fun onToolErrorCallback(
        tool: Tool,
        toolCall: ToolCall,
        toolContext: ToolContext,
        error: Throwable,
    ): ToolOutput? {
        addEntry(
            invocationId = toolContext.invocationId ?: toolContext.session.id,
            type = "tool_error",
            agentName = toolContext.agent.name,
            data =
                mapOf(
                    "tool_name" to tool.definition.name,
                    "error" to error.message.orEmpty(),
                ),
        )
        return null
    }

    override suspend fun onEventCallback(
        invocationContext: InvocationContext,
        event: Event,
    ): Event? {
        addEntry(
            invocationId = invocationContext.invocationId,
            type = "event",
            agentName = event.author,
            data =
                mapOf(
                    "author" to event.author,
                    "branch" to event.branch.orEmpty(),
                    "text" to event.content?.text.orEmpty(),
                    "end_of_agent" to event.actions.endOfAgent.toString(),
                ),
        )
        return null
    }

    override suspend fun afterRunCallback(
        invocationContext: InvocationContext,
        runResult: RunResult,
    ) {
        val state = invocationStates.remove(invocationContext.invocationId) ?: return
        val document =
            buildString {
                appendLine("---")
                appendLine("invocation_id: ${quoteYaml(state.invocationId)}")
                appendLine("session_id: ${quoteYaml(state.sessionId)}")
                appendLine("app_name: ${quoteYaml(state.appName)}")
                appendLine("user_id: ${quoteYaml(state.userId)}")
                appendLine("start_time: ${quoteYaml(formatInstant(state.startTime))}")
                appendLine("entries:")
                state.entries.forEach { entry ->
                    appendLine("  - timestamp: ${quoteYaml(formatInstant(entry.timestamp))}")
                    appendLine("    type: ${quoteYaml(entry.type)}")
                    entry.agentName?.let { agentName ->
                        appendLine("    agent_name: ${quoteYaml(agentName)}")
                    }
                    appendLine("    data:")
                    entry.data.forEach { (key, value) ->
                        appendLine("      $key: ${quoteYaml(value)}")
                    }
                }
                if (includeSessionState) {
                    appendLine("final_session_state: ${quoteYaml(runResult.session.state.toString())}")
                }
                appendLine("final_message: ${quoteYaml(runResult.finalMessage)}")
            }

        outputPath.parent?.let(Files::createDirectories)
        Files.writeString(
            outputPath,
            document,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    private fun addEntry(
        invocationId: String,
        type: String,
        agentName: String? = null,
        data: Map<String, String> = emptyMap(),
    ) {
        invocationStates[invocationId]?.entries +=
            DebugEntry(
                type = type,
                agentName = agentName,
                data = data,
            )
    }

    private fun ensureState(invocationContext: InvocationContext) {
        invocationStates.computeIfAbsent(invocationContext.invocationId) {
            InvocationDebugState(
                invocationId = invocationContext.invocationId,
                sessionId = invocationContext.session.id,
                appName = invocationContext.app.name,
                userId = invocationContext.userId,
            )
        }
    }

    private fun formatInstant(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant)

    private fun quoteYaml(value: String): String =
        buildString {
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
}
