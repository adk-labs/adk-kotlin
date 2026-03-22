package dev.adk.kotlin.cli

import dev.adk.kotlin.AdkApp
import dev.adk.kotlin.ArtifactService
import dev.adk.kotlin.CredentialService
import dev.adk.kotlin.InMemoryArtifactService
import dev.adk.kotlin.InMemorySessionStore
import dev.adk.kotlin.LanguageModel
import dev.adk.kotlin.MemoryService
import dev.adk.kotlin.MessageAttachment
import dev.adk.kotlin.Plugin
import dev.adk.kotlin.RunResult
import dev.adk.kotlin.Runner
import dev.adk.kotlin.SessionStore
import dev.adk.kotlin.ToolConfirmationHandler
import dev.adk.kotlin.UserMessage
import dev.adk.kotlin.AgentSession
import dev.adk.kotlin.Event
import java.util.UUID

data class LoadedApp(
    val app: AdkApp,
    val model: LanguageModel,
    val sessionStore: SessionStore = InMemorySessionStore(),
    val artifactService: ArtifactService = InMemoryArtifactService(),
    val plugins: List<Plugin> = emptyList(),
    val memoryService: MemoryService? = null,
    val credentialService: CredentialService? = null,
    val toolConfirmationHandler: ToolConfirmationHandler? = null,
)

interface AgentLoader {
    fun loadAgent(appName: String): LoadedApp
}

class StaticAgentLoader(
    private val loadedApps: Map<String, LoadedApp>,
) : AgentLoader {
    override fun loadAgent(appName: String): LoadedApp =
        loadedApps[appName] ?: error("Unknown app: $appName")
}

data class CliRunRequest(
    val appName: String,
    val userId: String = "cli-user",
    val sessionId: String = "cli-session",
    val userMessage: UserMessage,
    val streaming: Boolean = true,
)

data class CliInteractiveRequest(
    val appName: String,
    val userId: String = "cli-user",
    val sessionId: String = UUID.randomUUID().toString(),
    val streaming: Boolean = true,
    val exitCommands: Set<String> = setOf("exit", "quit"),
)

class AdkCli(
    private val agentLoader: AgentLoader,
    private val input: () -> String? = ::readLine,
    private val output: (String) -> Unit = ::println,
) {
    suspend fun run(request: CliRunRequest): RunResult {
        val loadedApp = agentLoader.loadAgent(request.appName)
        val runner = loadedApp.createRunner()
        return try {
            executeRun(
                runner = runner,
                request = request,
            )
        } finally {
            runner.close()
        }
    }

    suspend fun run(
        appName: String,
        message: String,
        userId: String = "cli-user",
        sessionId: String = "cli-session",
        streaming: Boolean = true,
    ): RunResult =
        run(
            CliRunRequest(
                appName = appName,
                userId = userId,
                sessionId = sessionId,
                userMessage = UserMessage(message),
                streaming = streaming,
            ),
        )

    suspend fun runInteractive(request: CliInteractiveRequest): AgentSession {
        val loadedApp = agentLoader.loadAgent(request.appName)
        val runner = loadedApp.createRunner()
        output("Running agent ${loadedApp.app.rootAgent.name}, type exit to exit.")

        return try {
            while (true) {
                val query = input()?.trimEnd() ?: break
                if (query.isBlank()) {
                    continue
                }
                if (request.exitCommands.contains(query.lowercase())) {
                    break
                }

                executeRun(
                    runner = runner,
                    request =
                        CliRunRequest(
                            appName = request.appName,
                            userId = request.userId,
                            sessionId = request.sessionId,
                            userMessage = UserMessage(query),
                            streaming = request.streaming,
                        ),
                )
            }

            loadedApp.sessionStore.getOrCreate(
                appName = loadedApp.app.name,
                userId = request.userId,
                sessionId = request.sessionId,
            )
        } finally {
            runner.close()
        }
    }

    private suspend fun executeRun(
        runner: Runner,
        request: CliRunRequest,
    ): RunResult =
        if (request.streaming) {
            runner.run(
                userId = request.userId,
                userMessage = request.userMessage,
                sessionId = request.sessionId,
                onEvent = { event -> formatEvent(event)?.let(output) },
            )
        } else {
            runner.run(
                userId = request.userId,
                userMessage = request.userMessage,
                sessionId = request.sessionId,
            ).also { result ->
                currentInvocationEvents(result).mapNotNull(::formatEvent).forEach(output)
            }
        }

    private fun currentInvocationEvents(result: RunResult): List<Event> {
        val invocationId = result.events.lastOrNull()?.invocationId ?: return emptyList()
        return result.events.filter { it.invocationId == invocationId }
    }

    private fun formatEvent(event: Event): String? {
        val text = event.content?.text?.trim().orEmpty()
        val attachments = event.content?.attachments.orEmpty().mapNotNull(MessageAttachment::filename)
        if (text.isBlank() && attachments.isEmpty()) {
            return null
        }

        val payload =
            buildString {
                if (text.isNotBlank()) {
                    append(text)
                }
                if (attachments.isNotEmpty()) {
                    if (isNotEmpty()) {
                        append(" ")
                    }
                    append("[attachments: ")
                    append(attachments.joinToString())
                    append("]")
                }
            }

        return "[${event.author}]: $payload"
    }
}

private fun LoadedApp.createRunner(): Runner =
    Runner(
        app = app,
        model = model,
        sessionStore = sessionStore,
        artifactService = artifactService,
        plugins = plugins,
        memoryService = memoryService,
        credentialService = credentialService,
        toolConfirmationHandler = toolConfirmationHandler,
    )
