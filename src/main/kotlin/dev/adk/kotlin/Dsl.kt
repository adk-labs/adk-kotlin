package dev.adk.kotlin

@DslMarker
annotation class AdkDsl

fun adkApp(name: String, block: AppDsl.() -> Unit): AdkApp =
    AppDsl(name).apply(block).build()

fun llmAgent(name: String, block: LlmAgentDsl.() -> Unit): LlmAgent =
    LlmAgentDsl(name).apply(block).build()

@AdkDsl
class AppDsl internal constructor(
    private val name: String,
) {
    private var rootAgent: LlmAgent? = null
    private var globalInstruction: InstructionTemplate? = null

    fun globalInstruction(
        text: String,
        bypassStateInjection: Boolean = false,
    ) {
        globalInstruction = InstructionTemplate(text = text.trim(), bypassStateInjection = bypassStateInjection)
    }

    fun rootAgent(name: String, block: LlmAgentDsl.() -> Unit) {
        check(rootAgent == null) { "Only one root agent can be defined." }
        rootAgent = llmAgent(name, block)
    }

    internal fun build(): AdkApp =
        AdkApp(
            name = name,
            rootAgent = requireNotNull(rootAgent) { "A root agent must be defined." },
            globalInstruction = globalInstruction,
        )
}

@AdkDsl
class LlmAgentDsl internal constructor(
    private val name: String,
) {
    var model: String? = null
    var description: String = ""
    var maxIterations: Int = 8
    var disallowTransferToParent: Boolean = false
    var disallowTransferToPeers: Boolean = false

    private val instructionLines = mutableListOf<String>()
    private val tools = mutableListOf<Tool>()
    private val subAgents = mutableListOf<LlmAgent>()
    private var instructionBypassesStateInjection: Boolean = false
    private var staticInstructionText: String? = null
    private var outputSchema: OutputSchema? = null

    fun instruction(
        line: String,
        bypassStateInjection: Boolean = false,
    ) {
        instructionBypassesStateInjection = bypassStateInjection
        val normalized = line.trim()
        if (normalized.isNotEmpty()) {
            instructionLines += normalized
        }
    }

    fun instructions(vararg lines: String) {
        lines.forEach { line -> instruction(line) }
    }

    fun staticInstruction(text: String) {
        staticInstructionText = text.trim()
    }

    fun outputSchema(block: OutputSchemaDsl.() -> Unit) {
        outputSchema = OutputSchemaDsl().apply(block).build()
    }

    fun tool(tool: Tool) {
        tools += tool
    }

    fun subAgent(name: String, block: LlmAgentDsl.() -> Unit) {
        subAgents += llmAgent(name, block)
    }

    internal fun build(): LlmAgent =
        LlmAgent(
            name = name,
            model = requireNotNull(model) { "model must be provided for agent $name." }.trim(),
            description = description.trim(),
            instruction =
                instructionLines
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString("\n")
                    ?.let { text ->
                        InstructionTemplate(
                            text = text,
                            bypassStateInjection = instructionBypassesStateInjection,
                        )
                    },
            staticInstruction = staticInstructionText,
            outputSchema = outputSchema,
            tools = tools.toList(),
            subAgents = subAgents.toList(),
            maxIterations = maxIterations,
            disallowTransferToParent = disallowTransferToParent,
            disallowTransferToPeers = disallowTransferToPeers,
        )
}
