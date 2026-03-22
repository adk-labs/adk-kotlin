package dev.adk.kotlin.cli.plugins

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.adk.kotlin.AttachmentScope
import dev.adk.kotlin.Event
import dev.adk.kotlin.EventActions
import dev.adk.kotlin.Message
import dev.adk.kotlin.MessageAttachment
import dev.adk.kotlin.ModelRequest
import dev.adk.kotlin.ModelResponse
import dev.adk.kotlin.OutputSchema
import dev.adk.kotlin.ToolCall
import dev.adk.kotlin.ToolDefinition
import dev.adk.kotlin.ToolMessage
import dev.adk.kotlin.ToolOutput
import dev.adk.kotlin.AgentSession

const val RECORDINGS_CONFIG_KEY = "_adk_recordings_config"

internal val recordingsJson: Gson =
    GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

data class RecordingsConfig(
    val testCasePath: String,
    val userMessageIndex: Int,
    val streamingMode: String = "none",
) {
    init {
        require(testCasePath.isNotBlank()) { "testCasePath cannot be blank." }
        require(userMessageIndex >= 0) { "userMessageIndex must be non-negative." }
    }
}

fun RecordingsConfig.toStateValue(): String = recordingsJson.toJson(this)

internal fun decodeRecordingsConfig(serialized: String): RecordingsConfig =
    recordingsJson.fromJson(serialized, RecordingsConfig::class.java)
        ?: error("Failed to decode recordings config.")

data class Recordings(
    val recordings: List<Recording> = emptyList(),
)

data class Recording(
    val agentName: String,
    val userMessageIndex: Int,
    val llmRecording: LlmRecording? = null,
    val toolRecording: ToolRecording? = null,
)

data class LlmRecording(
    val llmRequest: LlmRequestRecording,
    val llmResponses: MutableList<LlmResponseRecording> = mutableListOf(),
)

data class ToolRecording(
    val toolCall: ToolCallRecording,
    var toolResponse: ToolOutputRecording? = null,
)

data class LlmRequestRecording(
    val model: String,
    val systemInstructions: List<String>,
    val conversation: List<MessageRecording>,
    val availableTools: List<ToolDefinitionRecording>,
    val outputSchema: OutputSchemaRecording? = null,
)

data class LlmResponseRecording(
    val kind: String,
    val message: String? = null,
    val structuredResponse: String? = null,
    val toolCalls: List<ToolCallRecording> = emptyList(),
)

data class ToolCallRecording(
    val toolName: String,
    val arguments: Map<String, Any?> = emptyMap(),
)

data class ToolOutputRecording(
    val content: String,
    val attachments: List<AttachmentRecording>,
    val metadata: Map<String, String>,
    val skipSummarization: Boolean,
)

data class SessionRecording(
    val id: String,
    val userId: String,
    val state: Map<String, String>,
    val transcript: List<MessageRecording>,
    val events: List<EventRecording>,
    val updatedAt: String,
)

data class EventRecording(
    val id: String,
    val invocationId: String,
    val author: String,
    val content: MessageRecording? = null,
    val actions: EventActionsRecording,
    val branch: String? = null,
    val timestamp: String,
    val partial: Boolean,
    val turnComplete: Boolean,
)

data class EventActionsRecording(
    val skipSummarization: Boolean? = null,
    val stateDelta: Map<String, String?> = emptyMap(),
    val artifactDelta: Map<String, Int> = emptyMap(),
    val transferToAgent: String? = null,
    val escalate: Boolean? = null,
    val endOfAgent: Boolean? = null,
    val agentState: Map<String, String>? = null,
)

data class MessageRecording(
    val role: String,
    val text: String,
    val attachments: List<AttachmentRecording>,
    val timestamp: String,
    val toolName: String? = null,
)

data class AttachmentRecording(
    val filename: String,
    val content: String,
    val mimeType: String,
    val scope: String,
)

data class ToolDefinitionRecording(
    val name: String,
    val description: String,
    val isLongRunning: Boolean,
    val requiresConfirmation: Boolean,
)

data class OutputSchemaRecording(
    val fields: List<String>,
)

internal fun ModelRequest.toRecording(): LlmRequestRecording =
    LlmRequestRecording(
        model = model,
        systemInstructions = systemInstructions,
        conversation = conversation.map(Message::toRecording),
        availableTools = availableTools.map(ToolDefinition::toRecording),
        outputSchema = outputSchema?.toRecording(),
    )

internal fun ModelResponse.toRecording(): LlmResponseRecording =
    when (this) {
        is ModelResponse.Final ->
            LlmResponseRecording(
                kind = "final",
                message = message,
                structuredResponse = structuredResponse?.toString(),
            )

        is ModelResponse.ToolCalls ->
            LlmResponseRecording(
                kind = "tool_calls",
                toolCalls = calls.map(ToolCall::toRecording),
            )
    }

internal fun ToolCall.toRecording(): ToolCallRecording =
    ToolCallRecording(
        toolName = toolName,
        arguments = arguments,
    )

internal fun ToolOutput.toRecording(): ToolOutputRecording =
    ToolOutputRecording(
        content = content,
        attachments = attachments.map(MessageAttachment::toRecording),
        metadata = metadata,
        skipSummarization = skipSummarization,
    )

internal fun AgentSession.toRecording(): SessionRecording =
    SessionRecording(
        id = id,
        userId = userId,
        state = state,
        transcript = transcript.map(Message::toRecording),
        events = events.map(Event::toRecording),
        updatedAt = updatedAt.toString(),
    )

internal fun Event.toRecording(): EventRecording =
    EventRecording(
        id = id,
        invocationId = invocationId,
        author = author,
        content = content?.toRecording(),
        actions = actions.toRecording(),
        branch = branch,
        timestamp = timestamp.toString(),
        partial = partial,
        turnComplete = turnComplete,
    )

internal fun EventActions.toRecording(): EventActionsRecording =
    EventActionsRecording(
        skipSummarization = skipSummarization,
        stateDelta = stateDelta,
        artifactDelta = artifactDelta,
        transferToAgent = transferToAgent,
        escalate = escalate,
        endOfAgent = endOfAgent,
        agentState = agentState,
    )

internal fun Message.toRecording(): MessageRecording =
    MessageRecording(
        role = role.name,
        text = text,
        attachments = attachments.map(MessageAttachment::toRecording),
        timestamp = timestamp.toString(),
        toolName = (this as? ToolMessage)?.toolName,
    )

internal fun MessageAttachment.toRecording(): AttachmentRecording =
    AttachmentRecording(
        filename = filename,
        content = content,
        mimeType = mimeType,
        scope = scope.name,
    )

internal fun ToolDefinition.toRecording(): ToolDefinitionRecording =
    ToolDefinitionRecording(
        name = name,
        description = description,
        isLongRunning = isLongRunning,
        requiresConfirmation = requiresConfirmation,
    )

internal fun OutputSchema.toRecording(): OutputSchemaRecording =
    OutputSchemaRecording(
        fields = fields.map { it.name },
    )
