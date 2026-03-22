package dev.adk.kotlin

interface Plugin {
    val name: String

    suspend fun onUserMessageCallback(
        invocationContext: InvocationContext,
        userMessage: UserMessage,
    ): UserMessage? = null

    suspend fun beforeRunCallback(invocationContext: InvocationContext): Event? = null

    suspend fun onEventCallback(
        invocationContext: InvocationContext,
        event: Event,
    ): Event? = null

    suspend fun afterRunCallback(
        invocationContext: InvocationContext,
        runResult: RunResult,
    ) {}

    suspend fun processLlmRequest(
        callbackContext: CallbackContext,
        llmRequest: LlmRequest,
    ): LlmRequest = llmRequest

    suspend fun beforeModelCallback(
        callbackContext: CallbackContext,
        llmRequest: LlmRequest,
    ): LlmResponse? = null

    suspend fun afterModelCallback(
        callbackContext: CallbackContext,
        llmResponse: LlmResponse,
    ): LlmResponse? = null

    suspend fun onModelErrorCallback(
        callbackContext: CallbackContext,
        llmRequest: LlmRequest,
        error: Throwable,
    ): LlmResponse? = null

    suspend fun beforeToolCallback(
        tool: Tool,
        toolCall: ToolCall,
        toolContext: ToolContext,
    ): ToolOutput? = null

    suspend fun afterToolCallback(
        tool: Tool,
        toolCall: ToolCall,
        toolContext: ToolContext,
        result: ToolOutput,
    ): ToolOutput? = null

    suspend fun onToolErrorCallback(
        tool: Tool,
        toolCall: ToolCall,
        toolContext: ToolContext,
        error: Throwable,
    ): ToolOutput? = null

    suspend fun close() {}
}

abstract class BasePlugin(
    final override val name: String,
) : Plugin
