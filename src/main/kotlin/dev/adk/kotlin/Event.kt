package dev.adk.kotlin

import java.time.Instant
import java.util.UUID

data class EventActions(
    val skipSummarization: Boolean? = null,
    val stateDelta: Map<String, String?> = emptyMap(),
    val artifactDelta: Map<String, Int> = emptyMap(),
    val transferToAgent: String? = null,
    val escalate: Boolean? = null,
    val endOfAgent: Boolean? = null,
    val agentState: Map<String, String>? = null,
)

data class Event(
    val invocationId: String,
    val author: String,
    val content: Message? = null,
    val actions: EventActions = EventActions(),
    val branch: String? = null,
    val id: String = newId(),
    val timestamp: Instant = Instant.now(),
    val partial: Boolean = false,
    val turnComplete: Boolean = false,
) {
    fun isFinalResponse(): Boolean = actions.endOfAgent == true

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
