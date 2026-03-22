package dev.adk.kotlin

import java.io.Serializable
import java.time.Instant

enum class MessageRole {
    USER,
    MODEL,
    TOOL,
}

enum class AttachmentScope {
    SESSION,
    USER,
}

data class MessageAttachment(
    val filename: String,
    val content: String,
    val mimeType: String = "text/plain",
    val scope: AttachmentScope = AttachmentScope.SESSION,
) : Serializable

sealed interface Message : Serializable {
    val role: MessageRole
    val text: String
    val attachments: List<MessageAttachment>
    val timestamp: Instant
}

data class UserMessage(
    override val text: String,
    override val attachments: List<MessageAttachment> = emptyList(),
    val artifactDelta: Map<String, Int> = emptyMap(),
    override val timestamp: Instant = Instant.now(),
) : Message {
    override val role: MessageRole = MessageRole.USER
}

data class ModelMessage(
    override val text: String,
    override val attachments: List<MessageAttachment> = emptyList(),
    override val timestamp: Instant = Instant.now(),
) : Message {
    override val role: MessageRole = MessageRole.MODEL
}

data class ToolMessage(
    val toolName: String,
    override val text: String,
    override val attachments: List<MessageAttachment> = emptyList(),
    override val timestamp: Instant = Instant.now(),
) : Message {
    override val role: MessageRole = MessageRole.TOOL
}

internal fun Message.withAttachments(attachments: List<MessageAttachment>): Message =
    when (this) {
        is UserMessage -> copy(attachments = attachments)
        is ModelMessage -> copy(attachments = attachments)
        is ToolMessage -> copy(attachments = attachments)
    }
