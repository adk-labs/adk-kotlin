package dev.adk.kotlin

private const val ATTACHMENTS_RETURNED_BY_TOOLS_ID = "temp:ATTACHMENTS_RETURNED_BY_TOOLS_ID"

class MultimodalToolResultsPlugin(
    name: String = "multimodal_tool_results_plugin",
) : BasePlugin(name) {
    override suspend fun afterToolCallback(
        tool: Tool,
        toolCall: ToolCall,
        toolContext: ToolContext,
        result: ToolOutput,
    ): ToolOutput? {
        if (result.attachments.isEmpty()) {
            return result
        }

        val savedAttachments =
            toolContext.getTemporaryValue<List<MessageAttachment>>(ATTACHMENTS_RETURNED_BY_TOOLS_ID).orEmpty()
        toolContext.putTemporaryValue(
            ATTACHMENTS_RETURNED_BY_TOOLS_ID,
            savedAttachments + result.attachments,
        )
        return result
    }

    override suspend fun processLlmRequest(
        callbackContext: CallbackContext,
        llmRequest: LlmRequest,
    ): LlmRequest {
        val savedAttachments =
            callbackContext.invocationContext.removeTemporaryValue(ATTACHMENTS_RETURNED_BY_TOOLS_ID)
                as? List<*>
                ?: return llmRequest
        val multimodalAttachments = savedAttachments.filterIsInstance<MessageAttachment>()
        if (multimodalAttachments.isEmpty() || llmRequest.conversation.isEmpty()) {
            return llmRequest
        }

        val updatedConversation = llmRequest.conversation.toMutableList()
        val lastMessage = updatedConversation.removeAt(updatedConversation.lastIndex)
        updatedConversation +=
            lastMessage.withAttachments((lastMessage.attachments + multimodalAttachments).distinct())

        return llmRequest.copy(conversation = updatedConversation)
    }
}

fun multimodalToolResultsPlugin(
    name: String = "multimodal_tool_results_plugin",
): MultimodalToolResultsPlugin = MultimodalToolResultsPlugin(name = name)
