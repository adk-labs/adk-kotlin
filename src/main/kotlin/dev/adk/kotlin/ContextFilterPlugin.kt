package dev.adk.kotlin

typealias ContextMessageFilter = (List<Message>) -> List<Message>

class ContextFilterPlugin(
    private val numInvocationsToKeep: Int? = null,
    private val customFilter: ContextMessageFilter? = null,
    name: String = "context_filter_plugin",
) : BasePlugin(name) {
    init {
        require(numInvocationsToKeep == null || numInvocationsToKeep > 0) {
            "numInvocationsToKeep must be positive when provided."
        }
    }

    override suspend fun processLlmRequest(
        callbackContext: CallbackContext,
        llmRequest: LlmRequest,
    ): LlmRequest {
        var conversation = llmRequest.conversation
        if (conversation.isEmpty()) {
            return llmRequest
        }

        numInvocationsToKeep?.let { invocationsToKeep ->
            conversation = trimByInvocations(conversation, invocationsToKeep)
        }
        customFilter?.let { filter ->
            conversation = filter(conversation)
        }

        return llmRequest.copy(conversation = conversation)
    }

    private fun trimByInvocations(
        conversation: List<Message>,
        invocationsToKeep: Int,
    ): List<Message> {
        val invocationStartIndices = mutableListOf<Int>()
        var previousWasUser = false

        conversation.forEachIndexed { index, message ->
            val isUser = message.role == MessageRole.USER
            if (isUser && !previousWasUser) {
                invocationStartIndices += index
            }
            previousWasUser = isUser
        }

        if (invocationStartIndices.size <= invocationsToKeep) {
            return conversation
        }

        val startIndex = invocationStartIndices[invocationStartIndices.size - invocationsToKeep]
        return conversation.subList(startIndex, conversation.size)
    }
}

fun contextFilterPlugin(
    numInvocationsToKeep: Int? = null,
    customFilter: ContextMessageFilter? = null,
    name: String = "context_filter_plugin",
): ContextFilterPlugin =
    ContextFilterPlugin(
        numInvocationsToKeep = numInvocationsToKeep,
        customFilter = customFilter,
        name = name,
    )
