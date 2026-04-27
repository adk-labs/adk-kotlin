package dev.adk.kotlin

import java.io.Serializable
import java.time.Instant
import java.util.UUID

const val STATE_REMOVED = "__ADK_SENTINEL_REMOVED__"

data class EventUsageMetadata(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null,
) : Serializable

data class EventTranscription(
    val text: String,
    val finished: Boolean? = null,
) : Serializable

data class UiWidget(
    val id: String,
    val provider: String,
    val payload: Map<String, Any?> = emptyMap(),
) : Serializable

data class EventCompaction(
    val startTimestamp: Instant,
    val endTimestamp: Instant,
    val compactedContent: Message,
) : Serializable

data class EventActions(
    val skipSummarization: Boolean? = null,
    val stateDelta: Map<String, Any?> = emptyMap(),
    val artifactDelta: Map<String, Int> = emptyMap(),
    val deletedArtifactIds: Set<String> = emptySet(),
    val requestedAuthConfigs: Map<String, AuthConfig> = emptyMap(),
    val requestedToolConfirmations: Map<String, ToolConfirmation> = emptyMap(),
    val transferToAgent: String? = null,
    val escalate: Boolean? = null,
    val compaction: EventCompaction? = null,
    val endOfAgent: Boolean? = null,
    val agentState: Map<String, Any?>? = null,
    val rewindBeforeInvocationId: String? = null,
    val renderUiWidgets: List<UiWidget>? = null,
) : Serializable {
    fun removeStateByKey(key: String): EventActions =
        copy(
            stateDelta = stateDelta + (key to STATE_REMOVED),
        )

    fun merge(other: EventActions): EventActions =
        copy(
            skipSummarization = other.skipSummarization ?: skipSummarization,
            stateDelta = mergeStateDeltas(stateDelta, other.stateDelta),
            artifactDelta = artifactDelta + other.artifactDelta,
            deletedArtifactIds = deletedArtifactIds + other.deletedArtifactIds,
            requestedAuthConfigs = requestedAuthConfigs + other.requestedAuthConfigs,
            requestedToolConfirmations = requestedToolConfirmations + other.requestedToolConfirmations,
            transferToAgent = other.transferToAgent ?: transferToAgent,
            escalate = other.escalate ?: escalate,
            compaction = other.compaction ?: compaction,
            endOfAgent = other.endOfAgent ?: endOfAgent,
            agentState = other.agentState ?: agentState,
            rewindBeforeInvocationId = other.rewindBeforeInvocationId ?: rewindBeforeInvocationId,
            renderUiWidgets = mergeUiWidgets(renderUiWidgets, other.renderUiWidgets),
        )

    private fun mergeUiWidgets(
        current: List<UiWidget>?,
        incoming: List<UiWidget>?,
    ): List<UiWidget>? =
        when {
            current == null -> incoming
            incoming == null -> current
            else -> current + incoming
        }

    private fun mergeStateDeltas(
        current: Map<String, Any?>,
        incoming: Map<String, Any?>,
    ): Map<String, Any?> {
        if (incoming.isEmpty()) {
            return current
        }

        val merged = current.toMutableMap()
        incoming.forEach { (key, value) ->
            merged[key] = deepMerge(merged[key], value)
        }
        return merged.toMap()
    }

    @Suppress("UNCHECKED_CAST")
    private fun deepMerge(
        current: Any?,
        incoming: Any?,
    ): Any? {
        if (current !is Map<*, *> || incoming !is Map<*, *>) {
            return incoming
        }

        val merged = current as Map<String, Any?>
        val incomingMap = incoming as Map<String, Any?>
        return mergeStateDeltas(merged, incomingMap)
    }
}

data class Event(
    val invocationId: String,
    val author: String,
    val content: Message? = null,
    val actions: EventActions = EventActions(),
    val longRunningToolIds: Set<String>? = null,
    val branch: String? = null,
    val id: String = newId(),
    val timestamp: Instant = Instant.now(),
    val partial: Boolean = false,
    val turnComplete: Boolean = false,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val finishReason: String? = null,
    val usageMetadata: EventUsageMetadata? = null,
    val avgLogprobs: Double? = null,
    val interrupted: Boolean? = null,
    val groundingMetadata: Map<String, Any?>? = null,
    val customMetadata: Map<String, Any?>? = null,
    val modelVersion: String? = null,
    val inputTranscription: EventTranscription? = null,
    val outputTranscription: EventTranscription? = null,
) : Serializable {
    fun isFinalResponse(): Boolean {
        if (actions.skipSummarization == true || !longRunningToolIds.isNullOrEmpty()) {
            return true
        }

        return content is ModelMessage && !partial
    }

    fun stringifyContent(): String =
        when (val message = content) {
            null -> ""
            is ToolMessage -> "Function Response: ${message.toolName}: ${message.text}"
            else -> message.text
        }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
