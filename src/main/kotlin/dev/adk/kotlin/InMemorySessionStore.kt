package dev.adk.kotlin

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemorySessionStore : SessionStore {
    private val sessions =
        ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, AgentSession>>>()

    override suspend fun getOrCreate(
        appName: String,
        userId: String,
        sessionId: String?,
    ): AgentSession {
        val resolvedSessionId = sessionId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

        return sessions
            .computeIfAbsent(appName) { ConcurrentHashMap() }
            .computeIfAbsent(userId) { ConcurrentHashMap() }
            .computeIfAbsent(resolvedSessionId) {
                AgentSession(
                    id = resolvedSessionId,
                    userId = userId,
                )
            }
    }

    override suspend fun get(
        appName: String,
        userId: String,
        sessionId: String,
    ): AgentSession? =
        sessions[appName]
            ?.get(userId)
            ?.get(sessionId)

    override suspend fun save(
        appName: String,
        session: AgentSession,
    ) {
        sessions
            .computeIfAbsent(appName) { ConcurrentHashMap() }
            .computeIfAbsent(session.userId) { ConcurrentHashMap() }[session.id] = session
    }
}
