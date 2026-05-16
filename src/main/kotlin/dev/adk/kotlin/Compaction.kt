package dev.adk.kotlin

import java.time.Instant

fun interface BaseEventsSummarizer {
    suspend fun maybeSummarizeEvents(events: List<Event>): Event?
}

data class ResumabilityConfig(
    val isResumable: Boolean = false,
)

data class EventsCompactionConfig(
    val summarizer: BaseEventsSummarizer? = null,
    val compactionInterval: Int,
    val overlapSize: Int,
    val tokenThreshold: Int? = null,
    val eventRetentionSize: Int? = null,
) {
    init {
        require(compactionInterval > 0) { "compactionInterval must be positive." }
        require(overlapSize >= 0) { "overlapSize must be non-negative." }
        require((tokenThreshold == null) == (eventRetentionSize == null)) {
            "tokenThreshold and eventRetentionSize must be set together."
        }
        require(tokenThreshold == null || tokenThreshold > 0) {
            "tokenThreshold must be positive when provided."
        }
        require(eventRetentionSize == null || eventRetentionSize >= 0) {
            "eventRetentionSize must be non-negative when provided."
        }
    }
}

suspend fun runCompactionForSlidingWindow(
    app: AdkApp,
    session: AgentSession,
    sessionStore: SessionStore,
    skipTokenCompaction: Boolean = false,
): AgentSession {
    val config = app.eventsCompactionConfig ?: return session
    if (session.events.isEmpty()) {
        return session
    }

    if (!skipTokenCompaction && config.hasTokenThresholdConfig()) {
        val compacted = runCompactionForTokenThresholdConfig(config, app, session, sessionStore)
        if (compacted !== session) {
            return compacted
        }
    }

    val eventsToCompact = eventsToCompactForSlidingWindow(session.events, config)
    if (eventsToCompact.isEmpty()) {
        return session
    }

    val compactionEvent = config.summarizer?.maybeSummarizeEvents(eventsToCompact) ?: return session
    return appendCompactionEvent(app, session, sessionStore, compactionEvent)
}

suspend fun runCompactionForTokenThreshold(
    app: AdkApp,
    session: AgentSession,
    sessionStore: SessionStore,
): AgentSession {
    val config = app.eventsCompactionConfig ?: return session
    return runCompactionForTokenThresholdConfig(config, app, session, sessionStore)
}

internal fun latestPromptTokenCount(events: List<Event>): Int? {
    events.asReversed().forEach { event ->
        event.usageMetadata?.promptTokenCount?.let { return it }
    }

    val totalChars =
        effectiveEventsAfterCompaction(events)
            .sumOf { event -> event.stringifyContent().length }
    if (totalChars <= 0) {
        return null
    }
    return totalChars / 4
}

internal fun latestCompactionEvent(events: List<Event>): Event? {
    val compactions =
        events.mapIndexedNotNull { index, event ->
            val compaction = event.actions.compaction ?: return@mapIndexedNotNull null
            CompactionRange(
                index = index,
                startTimestamp = compaction.startTimestamp,
                endTimestamp = compaction.endTimestamp,
                event = event,
            )
        }

    return compactions
        .filterNot { candidate -> compactions.any { other -> other.subsumes(candidate) } }
        .maxByOrNull { it.index }
        ?.event
}

internal fun eventsToCompactForSlidingWindow(
    events: List<Event>,
    config: EventsCompactionConfig,
): List<Event> {
    val lastCompactedEndTimestamp =
        latestCompactionEvent(events)
            ?.actions
            ?.compaction
            ?.endTimestamp
            ?: Instant.EPOCH

    val invocationLatestTimestamps = linkedMapOf<String, Instant>()
    events
        .filterNot { event -> event.actions.compaction != null }
        .forEach { event ->
            val current = invocationLatestTimestamps[event.invocationId]
            if (current == null || event.timestamp > current) {
                invocationLatestTimestamps[event.invocationId] = event.timestamp
            }
        }

    val uniqueInvocationIds = invocationLatestTimestamps.keys.toList()
    val newInvocationIds =
        uniqueInvocationIds.filter { invocationId ->
            requireNotNull(invocationLatestTimestamps[invocationId]) > lastCompactedEndTimestamp
        }
    if (newInvocationIds.size < config.compactionInterval) {
        return emptyList()
    }

    val firstNewInvocationIndex = uniqueInvocationIds.indexOf(newInvocationIds.first())
    val startInvocationId = uniqueInvocationIds[(firstNewInvocationIndex - config.overlapSize).coerceAtLeast(0)]
    val endInvocationId = newInvocationIds.last()
    val firstEventIndex = events.indexOfFirst { event -> event.invocationId == startInvocationId }
    val lastEventIndex = events.indexOfLast { event -> event.invocationId == endInvocationId }
    if (firstEventIndex < 0 || lastEventIndex < firstEventIndex) {
        return emptyList()
    }

    return truncateBeforePendingHitl(
        events.subList(firstEventIndex, lastEventIndex + 1)
            .filterNot { event -> event.actions.compaction != null },
    )
}

internal fun eventsToCompactForTokenThreshold(
    events: List<Event>,
    eventRetentionSize: Int,
): List<Event> {
    val latestCompaction = latestCompactionEvent(events)
    val lastCompactedEndTimestamp = latestCompaction?.actions?.compaction?.endTimestamp ?: Instant.EPOCH
    val candidates =
        events.filter { event ->
            event.actions.compaction == null && event.timestamp > lastCompactedEndTimestamp
        }
    if (candidates.size <= eventRetentionSize) {
        return emptyList()
    }

    val splitIndex =
        if (eventRetentionSize == 0) {
            candidates.size
        } else {
            candidates.size - eventRetentionSize
        }
    val eventsToCompact = truncateBeforePendingHitl(candidates.take(splitIndex))
    if (eventsToCompact.isEmpty()) {
        return emptyList()
    }

    val compaction = latestCompaction?.actions?.compaction
    val seed =
        if (latestCompaction != null && compaction != null) {
            Event(
                timestamp = compaction.startTimestamp,
                invocationId = Event.newId(),
                author = "model",
                content = compaction.compactedContent,
                branch = latestCompaction.branch,
            )
        } else {
            null
        }
    return listOfNotNull(seed) + eventsToCompact
}

private suspend fun runCompactionForTokenThresholdConfig(
    config: EventsCompactionConfig,
    app: AdkApp,
    session: AgentSession,
    sessionStore: SessionStore,
): AgentSession {
    if (!config.hasTokenThresholdConfig()) {
        return session
    }

    val promptTokenCount = latestPromptTokenCount(session.events) ?: return session
    val tokenThreshold = requireNotNull(config.tokenThreshold)
    val eventRetentionSize = requireNotNull(config.eventRetentionSize)
    if (promptTokenCount < tokenThreshold) {
        return session
    }

    val eventsToCompact = eventsToCompactForTokenThreshold(session.events, eventRetentionSize)
    if (eventsToCompact.isEmpty()) {
        return session
    }

    val compactionEvent = config.summarizer?.maybeSummarizeEvents(eventsToCompact) ?: return session
    return appendCompactionEvent(app, session, sessionStore, compactionEvent)
}

private suspend fun appendCompactionEvent(
    app: AdkApp,
    session: AgentSession,
    sessionStore: SessionStore,
    compactionEvent: Event,
): AgentSession {
    val updatedSession =
        session.copy(
            events = session.events + compactionEvent,
            updatedAt = Instant.now(),
        )
    sessionStore.save(app.name, updatedSession)
    return updatedSession
}

private fun EventsCompactionConfig.hasTokenThresholdConfig(): Boolean =
    tokenThreshold != null && eventRetentionSize != null

private fun effectiveEventsAfterCompaction(events: List<Event>): List<Event> {
    val latestCompaction = latestCompactionEvent(events) ?: return events
    val compaction = latestCompaction.actions.compaction ?: return events
    return listOf(latestCompaction) +
        events.filter { event ->
            event.actions.compaction == null && event.timestamp > compaction.endTimestamp
        }
}

private fun truncateBeforePendingHitl(events: List<Event>): List<Event> {
    val resolvedCallIds = resolvedHitlCallIds(events)
    val index = events.indexOfFirst { event -> event.hasPendingHitl(resolvedCallIds) }
    return if (index < 0) events else events.take(index)
}

private fun resolvedHitlCallIds(events: List<Event>): Set<String> {
    val requestedPosition = linkedMapOf<String, Int>()
    val resolved = linkedSetOf<String>()
    events.forEachIndexed { index, event ->
        event.actions.requestedToolConfirmations.keys.forEach { callId ->
            requestedPosition.putIfAbsent(callId, index)
        }
        event.actions.requestedAuthConfigs.keys.forEach { callId ->
            requestedPosition.putIfAbsent(callId, index)
        }
        if (event.content is ToolMessage) {
            requestedPosition[event.content.toolName]?.let { requestIndex ->
                if (index > requestIndex) {
                    resolved += event.content.toolName
                }
            }
        }
    }
    return resolved
}

private fun Event.hasPendingHitl(resolvedCallIds: Set<String>): Boolean {
    val requested = actions.requestedToolConfirmations.keys + actions.requestedAuthConfigs.keys
    return requested.any { callId -> callId !in resolvedCallIds }
}

private data class CompactionRange(
    val index: Int,
    val startTimestamp: Instant,
    val endTimestamp: Instant,
    val event: Event,
) {
    fun subsumes(other: CompactionRange): Boolean {
        if (index == other.index) {
            return false
        }
        if (startTimestamp > other.startTimestamp || endTimestamp < other.endTimestamp) {
            return false
        }
        return startTimestamp < other.startTimestamp ||
            endTimestamp > other.endTimestamp ||
            index > other.index
    }
}
