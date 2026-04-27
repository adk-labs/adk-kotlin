package dev.adk.kotlin.web

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.adk.kotlin.AgentSession
import dev.adk.kotlin.AttachmentScope
import dev.adk.kotlin.AuthConfig
import dev.adk.kotlin.Event
import dev.adk.kotlin.EventActions
import dev.adk.kotlin.EventCompaction
import dev.adk.kotlin.EventTranscription
import dev.adk.kotlin.EventUsageMetadata
import dev.adk.kotlin.Message
import dev.adk.kotlin.MessageAttachment
import dev.adk.kotlin.ToolConfirmation
import dev.adk.kotlin.ToolMessage
import dev.adk.kotlin.UiWidget
import dev.adk.kotlin.UserMessage
import dev.adk.kotlin.cli.AgentLoader
import dev.adk.kotlin.cli.LoadedApp
import dev.adk.kotlin.Runner
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class AgentRunRequest(
    val appName: String? = null,
    val userId: String? = null,
    val sessionId: String? = null,
    val newMessage: WebMessageRequest? = null,
    val streaming: Boolean = false,
    val stateDelta: Map<String, Any?>? = null,
)

data class WebMessageRequest(
    val text: String = "",
    val attachments: List<WebAttachmentRequest> = emptyList(),
)

data class WebAttachmentRequest(
    val filename: String,
    val content: String,
    val mimeType: String = "text/plain",
    val scope: String = AttachmentScope.SESSION.name,
)

data class SessionRequest(
    val state: Map<String, Any?> = emptyMap(),
)

data class AppsResponse(
    val apps: List<String>,
)

data class SessionPayload(
    val appName: String,
    val id: String,
    val userId: String,
    val state: Map<String, String>,
    val transcript: List<MessagePayload>,
    val events: List<EventPayload>,
    val updatedAt: String,
)

data class MessagePayload(
    val role: String,
    val text: String,
    val attachments: List<AttachmentPayload>,
    val timestamp: String,
    val toolName: String? = null,
)

data class AttachmentPayload(
    val filename: String,
    val content: String,
    val mimeType: String,
    val scope: String,
)

data class EventPayload(
    val id: String,
    val invocationId: String,
    val author: String,
    val content: MessagePayload? = null,
    val actions: EventActionsPayload,
    val longRunningToolIds: Set<String>? = null,
    val branch: String? = null,
    val timestamp: String,
    val partial: Boolean,
    val turnComplete: Boolean,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val finishReason: String? = null,
    val usageMetadata: EventUsageMetadata? = null,
    val avgLogprobs: Double? = null,
    val interrupted: Boolean? = null,
    val groundingMetadata: Map<String, Any?>? = null,
    val customMetadata: Map<String, Any?>? = null,
    val modelVersion: String? = null,
    val inputTranscription: EventTranscription? = null,
    val outputTranscription: EventTranscription? = null,
)

data class EventActionsPayload(
    val skipSummarization: Boolean? = null,
    val stateDelta: Map<String, Any?> = emptyMap(),
    val artifactDelta: Map<String, Int> = emptyMap(),
    val deletedArtifactIds: Set<String> = emptySet(),
    val requestedAuthConfigs: Map<String, Any?> = emptyMap(),
    val requestedToolConfirmations: Map<String, Any?> = emptyMap(),
    val transferToAgent: String? = null,
    val escalate: Boolean? = null,
    val compaction: EventCompactionPayload? = null,
    val endOfAgent: Boolean? = null,
    val agentState: Map<String, Any?>? = null,
    val rewindBeforeInvocationId: String? = null,
    val renderUiWidgets: List<UiWidget>? = null,
)

data class EventCompactionPayload(
    val startTimestamp: String,
    val endTimestamp: String,
    val compactedContent: MessagePayload,
)

class AdkWebServer(
    private val agentLoader: AgentLoader,
    host: String = "127.0.0.1",
    port: Int = 0,
) : Closeable {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val server: HttpServer = HttpServer.create(InetSocketAddress(host, port), 0)
    private val executor: ExecutorService = Executors.newCachedThreadPool()

    init {
        server.executor = executor
        server.createContext("/apps", ::handleApps)
        server.createContext("/run", ::handleRun)
        server.createContext("/run_sse", ::handleRunSse)
    }

    val port: Int
        get() = server.address.port

    val baseUrl: String
        get() = "http://${server.address.hostString}:$port"

    fun start(): AdkWebServer {
        server.start()
        return this
    }

    fun stop(delaySeconds: Int = 0) {
        server.stop(delaySeconds)
        executor.shutdownNow()
    }

    override fun close() {
        stop()
    }

    private fun handleApps(exchange: HttpExchange) {
        if (handleCors(exchange)) {
            return
        }

        val segments = pathSegments(exchange)
        try {
            when {
                exchange.requestMethod == "GET" && segments == listOf("apps") -> {
                    respondJson(exchange, 200, AppsResponse(agentLoader.listAgents()))
                }

                segments.size == 5 &&
                    segments[0] == "apps" &&
                    segments[2] == "users" &&
                    segments[4] == "sessions" -> {
                    val appName = segments[1]
                    val userId = segments[3]
                    handleSessionCollection(exchange, appName, userId)
                }

                segments.size == 6 &&
                    segments[0] == "apps" &&
                    segments[2] == "users" &&
                    segments[4] == "sessions" -> {
                    val appName = segments[1]
                    val userId = segments[3]
                    val sessionId = segments[5]
                    handleSessionItem(exchange, appName, userId, sessionId)
                }

                else -> respondJson(exchange, 404, mapOf("error" to "Not found."))
            }
        } catch (error: Throwable) {
            respondJson(exchange, 500, mapOf("error" to (error.message ?: "Internal server error.")))
        }
    }

    private fun handleSessionCollection(
        exchange: HttpExchange,
        appName: String,
        userId: String,
    ) {
        val loadedApp = loadApp(appName)
        when (exchange.requestMethod) {
            "GET" -> {
                val sessions =
                    runBlocking {
                        loadedApp.sessionStore.list(loadedApp.app.name, userId).map { it.toPayload(loadedApp.app.name) }
                    }
                respondJson(exchange, 200, sessions)
            }

            "POST" -> {
                val request = readJsonBody(exchange, SessionRequest::class.java) ?: SessionRequest()
                val created =
                    runBlocking {
                        val session =
                            loadedApp.sessionStore.getOrCreate(
                                appName = loadedApp.app.name,
                                userId = userId,
                                sessionId = null,
                            )
                        val createdSession =
                            session.copy(
                                state = normalizeState(request.state),
                                updatedAt = Instant.now(),
                            )
                        loadedApp.sessionStore.save(loadedApp.app.name, createdSession)
                        createdSession
                    }
                respondJson(exchange, 201, created.toPayload(loadedApp.app.name))
            }

            else -> respondJson(exchange, 405, mapOf("error" to "Method not allowed."))
        }
    }

    private fun handleSessionItem(
        exchange: HttpExchange,
        appName: String,
        userId: String,
        sessionId: String,
    ) {
        val loadedApp = loadApp(appName)
        when (exchange.requestMethod) {
            "GET" -> {
                val session =
                    runBlocking {
                        loadedApp.sessionStore.get(loadedApp.app.name, userId, sessionId)
                    } ?: return respondJson(exchange, 404, mapOf("error" to "Session not found."))
                respondJson(exchange, 200, session.toPayload(loadedApp.app.name))
            }

            "POST" -> {
                val existing =
                    runBlocking {
                        loadedApp.sessionStore.get(loadedApp.app.name, userId, sessionId)
                    }
                if (existing != null) {
                    return respondJson(exchange, 400, mapOf("error" to "Session already exists."))
                }

                val request = readJsonBody(exchange, SessionRequest::class.java) ?: SessionRequest()
                val session =
                    AgentSession(
                        id = sessionId,
                        userId = userId,
                        state = normalizeState(request.state),
                        updatedAt = Instant.now(),
                    )
                runBlocking {
                    loadedApp.sessionStore.save(loadedApp.app.name, session)
                }
                respondJson(exchange, 201, session.toPayload(loadedApp.app.name))
            }

            "DELETE" -> {
                val deleted =
                    runBlocking {
                        val session = loadedApp.sessionStore.get(loadedApp.app.name, userId, sessionId)
                        if (session != null) {
                            loadedApp.sessionStore.delete(loadedApp.app.name, userId, sessionId)
                        }
                        session != null
                    }
                if (!deleted) {
                    return respondJson(exchange, 404, mapOf("error" to "Session not found."))
                }
                respondEmpty(exchange, 204)
            }

            else -> respondJson(exchange, 405, mapOf("error" to "Method not allowed."))
        }
    }

    private fun handleRun(exchange: HttpExchange) {
        if (handleCors(exchange)) {
            return
        }
        if (exchange.requestMethod != "POST") {
            return respondJson(exchange, 405, mapOf("error" to "Method not allowed."))
        }

        try {
            val request = requireRunRequest(exchange)
            val loadedApp = loadApp(requireNotNull(request.appName))
            val userMessage = requireNotNull(request.newMessage).toUserMessage()
            applyStateDelta(loadedApp, request)

            val runner = loadedApp.createRunner()
            val result =
                try {
                    runBlocking {
                        runner.run(
                            userId = requireNotNull(request.userId),
                            userMessage = userMessage,
                            sessionId = requireNotNull(request.sessionId),
                        )
                    }
                } finally {
                    runBlocking { runner.close() }
                }

            val invocationId = result.events.lastOrNull()?.invocationId
            val currentEvents =
                result.events
                    .filter { event -> invocationId == null || event.invocationId == invocationId }
                    .map(Event::toPayload)
            respondJson(exchange, 200, currentEvents)
        } catch (error: Throwable) {
            respondJson(exchange, 400, mapOf("error" to (error.message ?: "Invalid request.")))
        }
    }

    private fun handleRunSse(exchange: HttpExchange) {
        if (handleCors(exchange)) {
            return
        }
        if (exchange.requestMethod != "POST") {
            return respondJson(exchange, 405, mapOf("error" to "Method not allowed."))
        }

        try {
            val request = requireRunRequest(exchange)
            val loadedApp = loadApp(requireNotNull(request.appName))
            val userMessage = requireNotNull(request.newMessage).toUserMessage()
            applyStateDelta(loadedApp, request)
            val runner = loadedApp.createRunner()

            exchange.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
            exchange.responseHeaders.add("Cache-Control", "no-cache")
            exchange.responseHeaders.add("Connection", "keep-alive")
            addCorsHeaders(exchange)
            exchange.sendResponseHeaders(200, 0)

            OutputStreamWriter(exchange.responseBody, UTF_8).use { writer ->
                try {
                    runBlocking {
                        runner.run(
                            userId = requireNotNull(request.userId),
                            userMessage = userMessage,
                            sessionId = requireNotNull(request.sessionId),
                            onEvent = { event ->
                                writer.write("data: ${gson.toJson(event.toPayload())}\n\n")
                                writer.flush()
                            },
                        )
                    }
                } finally {
                    runBlocking { runner.close() }
                }
            }
        } catch (error: Throwable) {
            respondJson(exchange, 400, mapOf("error" to (error.message ?: "Invalid request.")))
        }
    }

    private fun requireRunRequest(exchange: HttpExchange): AgentRunRequest {
        val request =
            readJsonBody(exchange, AgentRunRequest::class.java)
                ?: error("Request body is required.")
        require(!request.appName.isNullOrBlank()) { "appName cannot be blank." }
        require(!request.userId.isNullOrBlank()) { "userId cannot be blank." }
        require(!request.sessionId.isNullOrBlank()) { "sessionId cannot be blank." }
        require(request.newMessage != null) { "newMessage is required." }
        return request
    }

    private fun applyStateDelta(
        loadedApp: LoadedApp,
        request: AgentRunRequest,
    ) {
        val stateDelta = request.stateDelta ?: return
        runBlocking {
            val session =
                loadedApp.sessionStore.getOrCreate(
                    appName = loadedApp.app.name,
                    userId = requireNotNull(request.userId),
                    sessionId = requireNotNull(request.sessionId),
                )
            val updatedState = session.state.toMutableMap()
            stateDelta.forEach { (key, value) ->
                if (value == null) {
                    updatedState.remove(key)
                } else {
                    updatedState[key] = value.toString()
                }
            }
            loadedApp.sessionStore.save(
                loadedApp.app.name,
                session.copy(
                    state = updatedState.toMap(),
                    updatedAt = Instant.now(),
                ),
            )
        }
    }

    private fun loadApp(appName: String): LoadedApp =
        try {
            agentLoader.loadAgent(appName)
        } catch (error: Throwable) {
            error("Unknown app: $appName")
        }

    private fun <T> readJsonBody(
        exchange: HttpExchange,
        clazz: Class<T>,
    ): T? {
        val body = exchange.requestBody.bufferedReader(UTF_8).use { it.readText() }.trim()
        if (body.isEmpty()) {
            return null
        }
        return gson.fromJson(body, clazz)
    }

    private fun normalizeState(state: Map<String, Any?>): Map<String, String> =
        state.mapNotNull { (key, value) ->
            value?.toString()?.let { normalizedValue -> key to normalizedValue }
        }.toMap()

    private fun pathSegments(exchange: HttpExchange): List<String> =
        exchange.requestURI.path.split('/').filter { it.isNotBlank() }

    private fun handleCors(exchange: HttpExchange): Boolean {
        addCorsHeaders(exchange)
        if (exchange.requestMethod == "OPTIONS") {
            respondEmpty(exchange, 204)
            return true
        }
        return false
    }

    private fun addCorsHeaders(exchange: HttpExchange) {
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type")
        exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS")
    }

    private fun respondJson(
        exchange: HttpExchange,
        status: Int,
        payload: Any,
    ) {
        val responseBytes = gson.toJson(payload).toByteArray(UTF_8)
        addCorsHeaders(exchange)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, responseBytes.size.toLong())
        exchange.responseBody.use { body ->
            body.write(responseBytes)
        }
    }

    private fun respondEmpty(
        exchange: HttpExchange,
        status: Int,
    ) {
        addCorsHeaders(exchange)
        exchange.sendResponseHeaders(status, -1)
        exchange.close()
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

private fun WebMessageRequest.toUserMessage(): UserMessage =
    UserMessage(
        text = text,
        attachments =
            attachments.map { attachment ->
                MessageAttachment(
                    filename = attachment.filename,
                    content = attachment.content,
                    mimeType = attachment.mimeType,
                    scope = runCatching { AttachmentScope.valueOf(attachment.scope.uppercase()) }.getOrDefault(AttachmentScope.SESSION),
                )
            },
    )

private fun AgentSession.toPayload(appName: String): SessionPayload =
    SessionPayload(
        appName = appName,
        id = id,
        userId = userId,
        state = state,
        transcript = transcript.map(Message::toPayload),
        events = events.map(Event::toPayload),
        updatedAt = updatedAt.toString(),
    )

private fun Message.toPayload(): MessagePayload =
    MessagePayload(
        role = role.name,
        text = text,
        attachments = attachments.map(MessageAttachment::toPayload),
        timestamp = timestamp.toString(),
        toolName = (this as? ToolMessage)?.toolName,
    )

private fun MessageAttachment.toPayload(): AttachmentPayload =
    AttachmentPayload(
        filename = filename,
        content = content,
        mimeType = mimeType,
        scope = scope.name,
    )

private fun Event.toPayload(): EventPayload =
    EventPayload(
        id = id,
        invocationId = invocationId,
        author = author,
        content = content?.toPayload(),
        actions = actions.toPayload(),
        longRunningToolIds = longRunningToolIds,
        branch = branch,
        timestamp = timestamp.toString(),
        partial = partial,
        turnComplete = turnComplete,
        errorCode = errorCode,
        errorMessage = errorMessage,
        finishReason = finishReason,
        usageMetadata = usageMetadata,
        avgLogprobs = avgLogprobs,
        interrupted = interrupted,
        groundingMetadata = groundingMetadata,
        customMetadata = customMetadata,
        modelVersion = modelVersion,
        inputTranscription = inputTranscription,
        outputTranscription = outputTranscription,
    )

private fun EventActions.toPayload(): EventActionsPayload =
    EventActionsPayload(
        skipSummarization = skipSummarization,
        stateDelta = stateDelta,
        artifactDelta = artifactDelta,
        deletedArtifactIds = deletedArtifactIds,
        requestedAuthConfigs = requestedAuthConfigs.mapValues { (_, value) -> value.toPayload() },
        requestedToolConfirmations = requestedToolConfirmations.mapValues { (_, value) -> value.toPayload() },
        transferToAgent = transferToAgent,
        escalate = escalate,
        compaction = compaction?.toPayload(),
        endOfAgent = endOfAgent,
        agentState = agentState,
        rewindBeforeInvocationId = rewindBeforeInvocationId,
        renderUiWidgets = renderUiWidgets,
    )

private fun EventCompaction.toPayload(): EventCompactionPayload =
    EventCompactionPayload(
        startTimestamp = startTimestamp.toString(),
        endTimestamp = endTimestamp.toString(),
        compactedContent = compactedContent.toPayload(),
    )

private fun AuthConfig.toPayload(): Map<String, Any?> =
    mapOf(
        "authScheme" to authScheme,
        "credentialKey" to credentialKey,
        "metadata" to metadata,
    )

private fun ToolConfirmation.toPayload(): Map<String, Any?> =
    mapOf(
        "hint" to hint,
        "payload" to payload,
        "confirmed" to confirmed,
    )
