package dev.adk.kotlin

import java.time.Instant

data class AgentSession(
    val id: String,
    val userId: String,
    val state: Map<String, String> = emptyMap(),
    val transcript: List<Message> = emptyList(),
    val updatedAt: Instant = Instant.now(),
)
