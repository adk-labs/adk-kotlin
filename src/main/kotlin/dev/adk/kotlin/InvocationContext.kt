package dev.adk.kotlin

class InvocationContext internal constructor(
    val app: AdkApp,
    val userId: String,
    val invocationId: String,
    val rootAgent: LlmAgent,
    val artifactService: ArtifactService,
    private val temporaryStorage: MutableMap<String, Any?>,
    private val eventSink: suspend (Event) -> Unit,
    private val sessionProvider: () -> AgentSession,
) {
    val session: AgentSession
        get() = sessionProvider()

    internal suspend fun publishEvent(event: Event) {
        eventSink(event)
    }

    internal fun putTemporaryValue(
        key: String,
        value: Any?,
    ) {
        if (value == null) {
            temporaryStorage.remove(key)
        } else {
            temporaryStorage[key] = value
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T> getTemporaryValue(key: String): T? = temporaryStorage[key] as? T

    internal fun removeTemporaryValue(key: String): Any? = temporaryStorage.remove(key)

    internal fun temporaryStorage(): MutableMap<String, Any?> = temporaryStorage
}

class CallbackContext internal constructor(
    val invocationContext: InvocationContext,
    val agent: LlmAgent,
) {
    val app: AdkApp
        get() = invocationContext.app

    val artifactService: ArtifactService
        get() = invocationContext.artifactService

    val invocationId: String
        get() = invocationContext.invocationId

    val session: AgentSession
        get() = invocationContext.session

    val branch: String
        get() = invocationContext.app.branchOf(agent)
}
