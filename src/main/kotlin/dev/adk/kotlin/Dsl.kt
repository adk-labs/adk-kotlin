package dev.adk.kotlin

@DslMarker
annotation class AdkDsl

typealias App = AdkApp
typealias Agent = LlmAgent

fun app(name: String, block: AppDsl.() -> Unit): App =
    AppDsl(name).apply(block).build()

fun adkApp(name: String, block: AppDsl.() -> Unit): AdkApp =
    app(name, block)

fun agent(name: String, block: LlmAgentDsl.() -> Unit): Agent =
    LlmAgentDsl(name).apply(block).build()

fun llmAgent(name: String, block: LlmAgentDsl.() -> Unit): LlmAgent =
    agent(name, block)

fun sequentialAgent(name: String, block: SequentialAgentDsl.() -> Unit): SequentialAgent =
    SequentialAgentDsl(name).apply(block).build()

fun loopAgent(name: String, block: LoopAgentDsl.() -> Unit): LoopAgent =
    LoopAgentDsl(name).apply(block).build()

fun parallelAgent(name: String, block: ParallelAgentDsl.() -> Unit): ParallelAgent =
    ParallelAgentDsl(name).apply(block).build()

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

    fun rootAgent(agent: LlmAgent) {
        check(rootAgent == null) { "Only one root agent can be defined." }
        rootAgent = agent
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
    var generateContentConfig: GenerateContentConfig? = null
    var includeContents: IncludeContents = IncludeContents.DEFAULT
    var outputKey: String? = null
    var planner: BasePlanner? = null
    var maxIterations: Int = 8
    var disallowTransferToParent: Boolean = false
    var disallowTransferToPeers: Boolean = false

    private val instructionLines = mutableListOf<String>()
    private val tools = mutableListOf<Tool>()
    private val subAgents = mutableListOf<LlmAgent>()
    private var instructionBypassesStateInjection: Boolean = false
    private var staticInstructionText: String? = null
    private var inputSchema: ToolSchema? = null
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

    fun inputSchema(block: ToolSchemaBuilder.() -> Unit) {
        inputSchema = toolSchema(block)
    }

    fun planner(planner: BasePlanner) {
        this.planner = planner
    }

    fun tool(tool: Tool) {
        tools += tool
    }

    fun tools(vararg tools: Tool) {
        this.tools += tools
    }

    fun subAgent(name: String, block: LlmAgentDsl.() -> Unit) {
        subAgents += llmAgent(name, block)
    }

    fun subAgents(vararg agents: LlmAgent) {
        subAgents += agents
    }

    fun sequentialAgent(name: String, block: SequentialAgentDsl.() -> Unit) {
        subAgents += dev.adk.kotlin.sequentialAgent(name, block)
    }

    fun loopAgent(name: String, block: LoopAgentDsl.() -> Unit) {
        subAgents += dev.adk.kotlin.loopAgent(name, block)
    }

    fun parallelAgent(name: String, block: ParallelAgentDsl.() -> Unit) {
        subAgents += dev.adk.kotlin.parallelAgent(name, block)
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
            generateContentConfig = generateContentConfig,
            includeContents = includeContents,
            inputSchema = inputSchema,
            outputSchema = outputSchema,
            outputKey = outputKey?.trim()?.takeIf { it.isNotEmpty() },
            planner = planner,
            tools = tools.toList(),
            subAgents = subAgents.toList(),
            maxIterations = maxIterations,
            disallowTransferToParent = disallowTransferToParent,
            disallowTransferToPeers = disallowTransferToPeers,
            executionKind = AgentExecutionKind.LLM,
        )
}

@AdkDsl
class SequentialAgentDsl internal constructor(
    private val name: String,
) {
    var description: String = ""

    private val subAgents = mutableListOf<LlmAgent>()

    fun subAgent(name: String, block: LlmAgentDsl.() -> Unit) {
        subAgents += llmAgent(name, block)
    }

    fun sequentialAgent(name: String, block: SequentialAgentDsl.() -> Unit) {
        subAgents += dev.adk.kotlin.sequentialAgent(name, block)
    }

    fun loopAgent(name: String, block: LoopAgentDsl.() -> Unit) {
        subAgents += dev.adk.kotlin.loopAgent(name, block)
    }

    fun parallelAgent(name: String, block: ParallelAgentDsl.() -> Unit) {
        subAgents += dev.adk.kotlin.parallelAgent(name, block)
    }

    fun subAgents(vararg agents: LlmAgent) {
        subAgents += agents
    }

    internal fun build(): SequentialAgent =
        LlmAgent(
            name = name,
            description = description.trim(),
            subAgents = subAgents.toList(),
            executionKind = AgentExecutionKind.SEQUENTIAL,
        )
}

@AdkDsl
class LoopAgentDsl internal constructor(
    private val name: String,
) {
    var description: String = ""
    var maxIterations: Int? = null

    private val subAgents = mutableListOf<LlmAgent>()

    fun subAgent(name: String, block: LlmAgentDsl.() -> Unit) {
        subAgents += llmAgent(name, block)
    }

    fun sequentialAgent(name: String, block: SequentialAgentDsl.() -> Unit) {
        subAgents += dev.adk.kotlin.sequentialAgent(name, block)
    }

    fun loopAgent(name: String, block: LoopAgentDsl.() -> Unit) {
        subAgents += dev.adk.kotlin.loopAgent(name, block)
    }

    fun parallelAgent(name: String, block: ParallelAgentDsl.() -> Unit) {
        subAgents += dev.adk.kotlin.parallelAgent(name, block)
    }

    fun subAgents(vararg agents: LlmAgent) {
        subAgents += agents
    }

    internal fun build(): LoopAgent =
        LlmAgent(
            name = name,
            description = description.trim(),
            subAgents = subAgents.toList(),
            loopMaxIterations = maxIterations,
            executionKind = AgentExecutionKind.LOOP,
        )
}

@AdkDsl
class ParallelAgentDsl internal constructor(
    private val name: String,
) {
    var description: String = ""

    private val subAgents = mutableListOf<LlmAgent>()

    fun subAgent(name: String, block: LlmAgentDsl.() -> Unit) {
        subAgents += llmAgent(name, block)
    }

    fun sequentialAgent(name: String, block: SequentialAgentDsl.() -> Unit) {
        subAgents += dev.adk.kotlin.sequentialAgent(name, block)
    }

    fun loopAgent(name: String, block: LoopAgentDsl.() -> Unit) {
        subAgents += dev.adk.kotlin.loopAgent(name, block)
    }

    fun parallelAgent(name: String, block: ParallelAgentDsl.() -> Unit) {
        subAgents += dev.adk.kotlin.parallelAgent(name, block)
    }

    fun subAgents(vararg agents: LlmAgent) {
        subAgents += agents
    }

    internal fun build(): ParallelAgent =
        LlmAgent(
            name = name,
            description = description.trim(),
            subAgents = subAgents.toList(),
            executionKind = AgentExecutionKind.PARALLEL,
        )
}
