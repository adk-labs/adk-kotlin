package dev.adk.kotlin

fun interface GlobalInstructionProvider {
    suspend fun resolve(callbackContext: CallbackContext): String?
}

class GlobalInstructionPlugin : BasePlugin {
    private val instructionTemplate: InstructionTemplate?
    private val instructionProvider: GlobalInstructionProvider?

    constructor(
        globalInstruction: String = "",
        name: String = "global_instruction",
        bypassStateInjection: Boolean = false,
    ) : super(name) {
        instructionTemplate =
            globalInstruction
                .trim()
                .takeIf(String::isNotEmpty)
                ?.let { instruction ->
                    InstructionTemplate(
                        text = instruction,
                        bypassStateInjection = bypassStateInjection,
                    )
                }
        instructionProvider = null
    }

    constructor(
        instructionProvider: GlobalInstructionProvider,
        name: String = "global_instruction",
    ) : super(name) {
        this.instructionTemplate = null
        this.instructionProvider = instructionProvider
    }

    override suspend fun processLlmRequest(
        callbackContext: CallbackContext,
        llmRequest: LlmRequest,
    ): LlmRequest {
        val resolvedInstruction = resolveGlobalInstruction(callbackContext)?.trim().orEmpty()
        if (resolvedInstruction.isEmpty()) {
            return llmRequest
        }

        return llmRequest.copy(
            systemInstructions = listOf(resolvedInstruction) + llmRequest.systemInstructions,
        )
    }

    private suspend fun resolveGlobalInstruction(callbackContext: CallbackContext): String? {
        instructionProvider?.let { provider ->
            return provider.resolve(callbackContext)
        }

        val template = instructionTemplate ?: return null
        return PromptAssembler.resolveInstruction(
            instruction = template,
            appName = callbackContext.app.name,
            session = callbackContext.session,
            artifactService = callbackContext.artifactService,
        )
    }
}

fun globalInstructionPlugin(
    globalInstruction: String,
    name: String = "global_instruction",
    bypassStateInjection: Boolean = false,
): GlobalInstructionPlugin =
    GlobalInstructionPlugin(
        globalInstruction = globalInstruction,
        name = name,
        bypassStateInjection = bypassStateInjection,
    )

fun globalInstructionPlugin(
    name: String = "global_instruction",
    instructionProvider: GlobalInstructionProvider,
): GlobalInstructionPlugin =
    GlobalInstructionPlugin(
        instructionProvider = instructionProvider,
        name = name,
    )
