package dev.adk.kotlin

import java.io.Serializable
import java.time.Instant

enum class MessageRole {
    USER,
    MODEL,
    TOOL,
}

sealed interface Message : Serializable {
    val role: MessageRole
    val text: String
    val timestamp: Instant
}

data class UserMessage(
    override val text: String,
    override val timestamp: Instant = Instant.now(),
) : Message {
    override val role: MessageRole = MessageRole.USER
}

data class ModelMessage(
    override val text: String,
    override val timestamp: Instant = Instant.now(),
) : Message {
    override val role: MessageRole = MessageRole.MODEL
}

data class ToolMessage(
    val toolName: String,
    override val text: String,
    override val timestamp: Instant = Instant.now(),
) : Message {
    override val role: MessageRole = MessageRole.TOOL
}
