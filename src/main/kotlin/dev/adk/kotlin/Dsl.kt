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

    fun rootAgent(name: String, block: LlmAgentDsl.() -> Unit) {
        check(rootAgent == null) { "Only one root agent can be defined." }
        rootAgent = llmAgent(name, block)
    }

    internal fun build(): AdkApp =
        AdkApp(
            name = name,
            rootAgent = requireNotNull(rootAgent) { "A root agent must be defined." },
        )
}

@AdkDsl
class LlmAgentDsl internal constructor(
    private val name: String,
) {
    var model: String? = null
    var description: String = ""
    var maxIterations: Int = 8

    private val instructions = mutableListOf<String>()
    private val tools = mutableListOf<Tool>()
    private val subAgents = mutableListOf<LlmAgent>()

    fun instruction(line: String) {
        val normalized = line.trim()
        if (normalized.isNotEmpty()) {
            instructions += normalized
        }
    }

    fun instructions(vararg lines: String) {
        lines.forEach(::instruction)
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
            instructions = instructions.toList(),
            tools = tools.toList(),
            subAgents = subAgents.toList(),
            maxIterations = maxIterations,
        )
}
