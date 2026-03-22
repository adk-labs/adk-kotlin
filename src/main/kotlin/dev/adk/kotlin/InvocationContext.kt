package dev.adk.kotlin

class InvocationContext internal constructor(
    val app: AdkApp,
    val userId: String,
    val invocationId: String,
    val rootAgent: LlmAgent,
    val artifactService: ArtifactService,
    private val sessionProvider: () -> AgentSession,
) {
    val session: AgentSession
        get() = sessionProvider()
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
