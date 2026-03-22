package dev.adk.kotlin.cli.plugins

import dev.adk.kotlin.BasePlugin
import dev.adk.kotlin.CallbackContext
import dev.adk.kotlin.Event
import dev.adk.kotlin.InvocationContext
import dev.adk.kotlin.LlmRequest
import dev.adk.kotlin.LlmResponse
import dev.adk.kotlin.RunResult
import dev.adk.kotlin.Tool
import dev.adk.kotlin.ToolCall
import dev.adk.kotlin.ToolContext
import dev.adk.kotlin.ToolOutput
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private data class InvocationRecordingState(
    val config: RecordingsConfig,
) {
    private val pendingLlmRecordings = linkedMapOf<String, Recording>()
    private val pendingToolRecordings = linkedMapOf<String, Recording>()
    private val pendingRecordingsOrder = mutableListOf<Recording>()

    @Synchronized
    fun createPendingLlmRecording(
        agentName: String,
        llmRequest: LlmRequestRecording,
    ) {
        val pendingRecording =
            Recording(
                agentName = agentName,
                userMessageIndex = config.userMessageIndex,
                llmRecording =
                    LlmRecording(
                        llmRequest = llmRequest,
                    ),
            )
        pendingLlmRecordings[agentName] = pendingRecording
        pendingRecordingsOrder += pendingRecording
    }

    @Synchronized
    fun completePendingLlmRecording(
        agentName: String,
        llmResponse: LlmResponseRecording,
    ) {
        pendingLlmRecordings.remove(agentName)?.llmRecording?.llmResponses?.add(llmResponse)
    }

    @Synchronized
    fun createPendingToolRecording(
        agentName: String,
        functionCallId: String,
        toolCall: ToolCallRecording,
    ) {
        val pendingRecording =
            Recording(
                agentName = agentName,
                userMessageIndex = config.userMessageIndex,
                toolRecording =
                    ToolRecording(
                        toolCall = toolCall,
                    ),
            )
        pendingToolRecordings[functionCallId] = pendingRecording
        pendingRecordingsOrder += pendingRecording
    }

    @Synchronized
    fun completePendingToolRecording(
        functionCallId: String,
        toolOutput: ToolOutputRecording,
    ) {
        pendingToolRecordings.remove(functionCallId)?.toolRecording?.toolResponse = toolOutput
    }

    @Synchronized
    fun completedRecordings(): Recordings =
        Recordings(
            recordings =
                pendingRecordingsOrder.filter { recording ->
                    when {
                        recording.llmRecording != null -> recording.llmRecording.llmResponses.isNotEmpty()
                        recording.toolRecording != null -> recording.toolRecording.toolResponse != null
                        else -> false
                    }
                },
        )
}

class RecordingsPlugin(
    name: String = "adk_recordings",
) : BasePlugin(name) {
    private val states = ConcurrentHashMap<String, InvocationRecordingState>()

    override suspend fun beforeRunCallback(invocationContext: InvocationContext): Event? {
        val configValue = invocationContext.session.state[RECORDINGS_CONFIG_KEY] ?: return null
        states[invocationContext.invocationId] = InvocationRecordingState(decodeRecordingsConfig(configValue))
        return null
    }

    override suspend fun beforeModelCallback(
        callbackContext: CallbackContext,
        llmRequest: LlmRequest,
    ): LlmResponse? {
        stateFor(callbackContext.invocationId)?.createPendingLlmRecording(
            agentName = callbackContext.agent.name,
            llmRequest = llmRequest.toRecording(),
        )
        return null
    }

    override suspend fun afterModelCallback(
        callbackContext: CallbackContext,
        llmResponse: LlmResponse,
    ): LlmResponse? {
        stateFor(callbackContext.invocationId)?.completePendingLlmRecording(
            agentName = callbackContext.agent.name,
            llmResponse = llmResponse.toRecording(),
        )
        return null
    }

    override suspend fun beforeToolCallback(
        tool: Tool,
        toolCall: ToolCall,
        toolContext: ToolContext,
    ): ToolOutput? {
        val functionCallId = toolContext.functionCallId ?: return null
        stateFor(toolContext.invocationId)?.createPendingToolRecording(
            agentName = toolContext.agent.name,
            functionCallId = functionCallId,
            toolCall = toolCall.toRecording(),
        )
        return null
    }

    override suspend fun afterToolCallback(
        tool: Tool,
        toolCall: ToolCall,
        toolContext: ToolContext,
        result: ToolOutput,
    ): ToolOutput? {
        val functionCallId = toolContext.functionCallId ?: return result
        stateFor(toolContext.invocationId)?.completePendingToolRecording(
            functionCallId = functionCallId,
            toolOutput = result.toRecording(),
        )
        return result
    }

    override suspend fun afterRunCallback(
        invocationContext: InvocationContext,
        runResult: RunResult,
    ) {
        val state = states.remove(invocationContext.invocationId) ?: return
        persistRecordings(
            state = state,
            runResult = runResult,
        )
    }

    private fun persistRecordings(
        state: InvocationRecordingState,
        runResult: RunResult,
    ) {
        val caseDir = Path.of(state.config.testCasePath)
        Files.createDirectories(caseDir)

        val suffix = if (state.config.streamingMode.equals("sse", ignoreCase = true)) "-sse" else ""
        val recordingsFile = caseDir.resolve("generated-recordings$suffix.json")
        val sessionFile = caseDir.resolve("generated-session$suffix.json")

        Files.writeString(recordingsFile, recordingsJson.toJson(state.completedRecordings()))
        Files.writeString(sessionFile, recordingsJson.toJson(runResult.session.toRecording()))
    }

    private fun stateFor(invocationId: String?): InvocationRecordingState? {
        if (invocationId == null) {
            return null
        }
        return states[invocationId]
    }
}

fun recordingsPlugin(
    name: String = "adk_recordings",
): RecordingsPlugin = RecordingsPlugin(name = name)
