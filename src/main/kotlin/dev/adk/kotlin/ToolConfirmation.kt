package dev.adk.kotlin

import java.io.Serializable

data class ToolConfirmation(
    val hint: String = "",
    val confirmed: Boolean = false,
    val payload: Any? = null,
) : Serializable

data class ToolConfirmationRequest(
    val callId: String,
    val agentName: String,
    val tool: ToolDefinition,
    val toolCall: ToolCall,
    val session: AgentSession,
    val suggestedConfirmation: ToolConfirmation,
)

fun interface ToolConfirmationHandler {
    suspend fun confirm(request: ToolConfirmationRequest): ToolConfirmation
}
