package dev.adk.kotlin

interface SessionStore {
    suspend fun getOrCreate(
        appName: String,
        userId: String,
        sessionId: String? = null,
    ): AgentSession

    suspend fun get(
        appName: String,
        userId: String,
        sessionId: String,
    ): AgentSession?

    suspend fun save(
        appName: String,
        session: AgentSession,
    )

    suspend fun list(
        appName: String,
        userId: String? = null,
    ): List<AgentSession>

    suspend fun delete(
        appName: String,
        userId: String,
        sessionId: String,
    )
}
