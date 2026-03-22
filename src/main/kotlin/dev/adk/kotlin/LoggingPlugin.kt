package dev.adk.kotlin

class LoggingPlugin(
    name: String = "logging_plugin",
    private val sink: (String) -> Unit = ::println,
    private val maxContentLength: Int = 200,
    private val maxArgsLength: Int = 300,
) : BasePlugin(name) {
    override suspend fun onUserMessageCallback(
        invocationContext: InvocationContext,
        userMessage: UserMessage,
    ): UserMessage? {
        log("USER MESSAGE RECEIVED")
        log("Invocation ID: ${invocationContext.invocationId}")
        log("Session ID: ${invocationContext.session.id}")
        log("User ID: ${invocationContext.userId}")
        log("App Name: ${invocationContext.app.name}")
        log("Root Agent: ${invocationContext.rootAgent.name}")
        log("User Content: ${formatText(userMessage.text)}")
        return null
    }

    override suspend fun beforeRunCallback(invocationContext: InvocationContext): Event? {
        log("INVOCATION STARTING")
        log("Invocation ID: ${invocationContext.invocationId}")
        log("Starting Agent: ${invocationContext.rootAgent.name}")
        return null
    }

    override suspend fun onEventCallback(
        invocationContext: InvocationContext,
        event: Event,
    ): Event? {
        log("EVENT YIELDED")
        log("Event ID: ${event.id}")
        log("Author: ${event.author}")
        log("Content: ${formatText(event.content?.text.orEmpty())}")
        log("Final Response: ${event.isFinalResponse()}")
        return null
    }

    override suspend fun afterRunCallback(
        invocationContext: InvocationContext,
        runResult: RunResult,
    ) {
        log("INVOCATION COMPLETED")
        log("Invocation ID: ${invocationContext.invocationId}")
        log("Final Agent: ${runResult.finalAgentName}")
    }

    override suspend fun beforeModelCallback(
        callbackContext: CallbackContext,
        llmRequest: LlmRequest,
    ): LlmResponse? {
        log("LLM REQUEST")
        log("Model: ${llmRequest.model}")
        log("Agent: ${callbackContext.agent.name}")
        log("Branch: ${callbackContext.branch}")
        llmRequest.systemInstruction
            ?.takeIf(String::isNotBlank)
            ?.let { instruction ->
                log("System Instruction: ${formatText(instruction)}")
            }
        if (llmRequest.availableTools.isNotEmpty()) {
            log("Available Tools: ${llmRequest.availableTools.joinToString(",") { it.name }}")
        }
        return null
    }

    override suspend fun afterModelCallback(
        callbackContext: CallbackContext,
        llmResponse: LlmResponse,
    ): LlmResponse? {
        log("LLM RESPONSE")
        log("Agent: ${callbackContext.agent.name}")
        when (llmResponse) {
            is ModelResponse.Final -> {
                log("Response Type: final")
                log("Content: ${formatText(llmResponse.message)}")
            }

            is ModelResponse.ToolCalls -> {
                log("Response Type: tool_calls")
                log("Tool Calls: ${llmResponse.calls.joinToString(",") { it.toolName }}")
            }
        }
        return null
    }

    override suspend fun onModelErrorCallback(
        callbackContext: CallbackContext,
        llmRequest: LlmRequest,
        error: Throwable,
    ): LlmResponse? {
        log("LLM ERROR")
        log("Agent: ${callbackContext.agent.name}")
        log("Error: ${error.message.orEmpty()}")
        return null
    }

    override suspend fun beforeToolCallback(
        tool: Tool,
        toolCall: ToolCall,
        toolContext: ToolContext,
    ): ToolOutput? {
        log("TOOL STARTING")
        log("Tool Name: ${tool.definition.name}")
        log("Agent: ${toolContext.agent.name}")
        log("Function Call ID: ${toolContext.functionCallId.orEmpty()}")
        log("Arguments: ${formatArgs(toolCall.arguments)}")
        return null
    }

    override suspend fun afterToolCallback(
        tool: Tool,
        toolCall: ToolCall,
        toolContext: ToolContext,
        result: ToolOutput,
    ): ToolOutput? {
        log("TOOL COMPLETED")
        log("Tool Name: ${tool.definition.name}")
        log("Result: ${formatText(result.content)}")
        return null
    }

    override suspend fun onToolErrorCallback(
        tool: Tool,
        toolCall: ToolCall,
        toolContext: ToolContext,
        error: Throwable,
    ): ToolOutput? {
        log("TOOL ERROR")
        log("Tool Name: ${tool.definition.name}")
        log("Agent: ${toolContext.agent.name}")
        log("Error: ${error.message.orEmpty()}")
        return null
    }

    private fun log(message: String) {
        sink("[$name] $message")
    }

    private fun formatText(text: String): String {
        val normalized = text.replace('\n', ' ').trim()
        return if (normalized.length > maxContentLength) {
            normalized.take(maxContentLength) + "..."
        } else {
            normalized
        }
    }

    private fun formatArgs(arguments: Map<String, Any?>): String {
        val normalized = arguments.toString()
        return if (normalized.length > maxArgsLength) {
            normalized.take(maxArgsLength) + "..."
        } else {
            normalized
        }
    }
}

fun loggingPlugin(
    name: String = "logging_plugin",
    sink: (String) -> Unit = ::println,
): LoggingPlugin = LoggingPlugin(name = name, sink = sink)
