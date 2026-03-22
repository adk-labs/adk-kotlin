package dev.adk.kotlin

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val appName: String,
    val userId: String,
    val sessionId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Instant = Instant.now(),
)

data class SearchMemoryResponse(
    val memories: List<MemoryEntry> = emptyList(),
)

interface MemoryService {
    suspend fun addSession(
        appName: String,
        session: AgentSession,
    )

    suspend fun addEvents(
        appName: String,
        userId: String,
        events: List<Event>,
        sessionId: String? = null,
        metadata: Map<String, String> = emptyMap(),
    )

    suspend fun addMemory(
        appName: String,
        userId: String,
        memories: List<MemoryEntry>,
    )

    suspend fun searchMemory(
        appName: String,
        userId: String,
        query: String,
    ): SearchMemoryResponse
}

class InMemoryMemoryService : MemoryService {
    private val storage = ConcurrentHashMap<MemoryScope, MutableList<MemoryEntry>>()

    override suspend fun addSession(
        appName: String,
        session: AgentSession,
    ) {
        addEvents(
            appName = appName,
            userId = session.userId,
            events = session.events,
            sessionId = session.id,
        )
    }

    override suspend fun addEvents(
        appName: String,
        userId: String,
        events: List<Event>,
        sessionId: String?,
        metadata: Map<String, String>,
    ) {
        if (events.isEmpty()) {
            return
        }

        val entries =
            events
                .mapNotNull { event ->
                    val text = event.content?.text?.trim().orEmpty()
                    if (text.isBlank()) {
                        null
                    } else {
                        MemoryEntry(
                            text = "${event.author}: $text",
                            appName = appName,
                            userId = userId,
                            sessionId = sessionId,
                            metadata = metadata,
                        )
                    }
                }
        addMemory(appName, userId, entries)
    }

    override suspend fun addMemory(
        appName: String,
        userId: String,
        memories: List<MemoryEntry>,
    ) {
        if (memories.isEmpty()) {
            return
        }

        val bucket = storage.computeIfAbsent(MemoryScope(appName, userId)) { mutableListOf() }
        bucket += memories
    }

    override suspend fun searchMemory(
        appName: String,
        userId: String,
        query: String,
    ): SearchMemoryResponse {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) {
            return SearchMemoryResponse()
        }

        val results =
            storage[MemoryScope(appName, userId)]
                .orEmpty()
                .mapNotNull { entry ->
                    val haystack = entry.text.lowercase()
                    val occurrences = haystack.windowed(normalizedQuery.length, 1).count { it == normalizedQuery }
                    if (occurrences == 0) {
                        null
                    } else {
                        entry to occurrences
                    }
                }.sortedWith(
                    compareByDescending<Pair<MemoryEntry, Int>> { it.second }
                        .thenByDescending { it.first.createdAt },
                ).map { it.first }

        return SearchMemoryResponse(memories = results)
    }

    private data class MemoryScope(
        val appName: String,
        val userId: String,
    )
}
