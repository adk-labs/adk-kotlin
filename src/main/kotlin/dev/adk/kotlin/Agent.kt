package dev.adk.kotlin

private val AGENT_NAME_PATTERN = Regex("^_?[a-zA-Z0-9]*([. _-][a-zA-Z0-9]+)*$")

enum class AgentExecutionKind {
    LLM,
    SEQUENTIAL,
    LOOP,
    PARALLEL,
}

data class AdkApp(
    val name: String,
    val rootAgent: LlmAgent,
    val globalInstruction: InstructionTemplate? = null,
) {
    init {
        require(name.isNotBlank()) { "App name cannot be blank." }
        validateAgentTree(rootAgent)
    }

    fun findAgent(name: String): LlmAgent? = findAgent(rootAgent, name)

    internal fun parentOf(agent: LlmAgent): LlmAgent? = parentOf(rootAgent, agent.name, parent = null)

    internal fun branchOf(agent: LlmAgent): String = requireNotNull(branchOf(rootAgent, agent.name, emptyList())) {
        "Unknown agent '${agent.name}' in app '${name}'."
    }

    internal fun transferTargetsOf(agent: LlmAgent): List<LlmAgent> {
        val targets = mutableListOf<LlmAgent>()
        targets += agent.subAgents

        val parent = parentOf(agent) ?: return targets

        if (!agent.disallowTransferToParent) {
            targets += parent
        }

        if (!agent.disallowTransferToPeers) {
            targets += parent.subAgents.filterNot { it.name == agent.name }
        }

        return targets.filter { it.executionKind == AgentExecutionKind.LLM }
    }

    private fun validateAgentTree(agent: LlmAgent) {
        val names = linkedSetOf<String>()
        collectNames(agent, names)
    }

    private fun collectNames(
        agent: LlmAgent,
        names: MutableSet<String>,
    ) {
        check(names.add(agent.name)) { "Agent names must be unique within the app tree: ${agent.name}" }
        agent.subAgents.forEach { child -> collectNames(child, names) }
    }

    private fun findAgent(
        current: LlmAgent,
        name: String,
    ): LlmAgent? {
        if (current.name == name) {
            return current
        }

        current.subAgents.forEach { child ->
            val found = findAgent(child, name)
            if (found != null) {
                return found
            }
        }

        return null
    }

    private fun parentOf(
        current: LlmAgent,
        targetName: String,
        parent: LlmAgent?,
    ): LlmAgent? {
        if (current.name == targetName) {
            return parent
        }

        current.subAgents.forEach { child ->
            val found = parentOf(child, targetName, current)
            if (found != null) {
                return found
            }
        }

        return null
    }

    private fun branchOf(
        current: LlmAgent,
        targetName: String,
        path: List<String>,
    ): String? {
        val nextPath = path + current.name
        if (current.name == targetName) {
            return nextPath.joinToString(".")
        }

        current.subAgents.forEach { child ->
            val found = branchOf(child, targetName, nextPath)
            if (found != null) {
                return found
            }
        }

        return null
    }
}

typealias SequentialAgent = LlmAgent
typealias LoopAgent = LlmAgent
typealias ParallelAgent = LlmAgent

data class LlmAgent(
    val name: String,
    val model: String = "",
    val description: String = "",
    val instruction: InstructionTemplate? = null,
    val staticInstruction: String? = null,
    val generateContentConfig: GenerateContentConfig? = null,
    val outputSchema: OutputSchema? = null,
    val planner: BasePlanner? = null,
    val tools: List<Tool> = emptyList(),
    val subAgents: List<LlmAgent> = emptyList(),
    val maxIterations: Int = 8,
    val loopMaxIterations: Int? = null,
    val disallowTransferToParent: Boolean = false,
    val disallowTransferToPeers: Boolean = false,
    val executionKind: AgentExecutionKind = AgentExecutionKind.LLM,
) {
    init {
        require(name.isNotBlank()) { "Agent name cannot be blank." }
        require(AGENT_NAME_PATTERN.matches(name)) {
            "Agent name '$name' does not match the official ADK identifier pattern."
        }
        require(name != "user") { "Agent name cannot be 'user'; reserved for end-user input." }
        require(maxIterations > 0) { "maxIterations must be positive." }
        require(subAgents.map { it.name }.toSet().size == subAgents.size) {
            "Sub-agents must have unique names under parent agent '$name'."
        }

        when (executionKind) {
            AgentExecutionKind.LLM -> {
                require(model.isNotBlank()) { "Agent model cannot be blank." }
                require(loopMaxIterations == null) { "LlmAgent '$name' cannot declare loopMaxIterations." }
            }

            AgentExecutionKind.SEQUENTIAL -> {
                require(model.isBlank()) { "SequentialAgent '$name' must not declare a model." }
                require(subAgents.isNotEmpty()) { "SequentialAgent '$name' must declare at least one sub-agent." }
                require(loopMaxIterations == null) { "SequentialAgent '$name' cannot declare loopMaxIterations." }
                require(instruction == null) { "SequentialAgent '$name' cannot declare instruction." }
                require(staticInstruction == null) { "SequentialAgent '$name' cannot declare staticInstruction." }
                require(generateContentConfig == null) { "SequentialAgent '$name' cannot declare generateContentConfig." }
                require(outputSchema == null) { "SequentialAgent '$name' cannot declare outputSchema." }
                require(planner == null) { "SequentialAgent '$name' cannot declare planner." }
                require(tools.isEmpty()) { "SequentialAgent '$name' cannot declare tools." }
            }

            AgentExecutionKind.LOOP -> {
                require(model.isBlank()) { "LoopAgent '$name' must not declare a model." }
                require(subAgents.isNotEmpty()) { "LoopAgent '$name' must declare at least one sub-agent." }
                require(loopMaxIterations == null || loopMaxIterations > 0) {
                    "LoopAgent '$name' must declare a positive loopMaxIterations."
                }
                require(instruction == null) { "LoopAgent '$name' cannot declare instruction." }
                require(staticInstruction == null) { "LoopAgent '$name' cannot declare staticInstruction." }
                require(generateContentConfig == null) { "LoopAgent '$name' cannot declare generateContentConfig." }
                require(outputSchema == null) { "LoopAgent '$name' cannot declare outputSchema." }
                require(planner == null) { "LoopAgent '$name' cannot declare planner." }
                require(tools.isEmpty()) { "LoopAgent '$name' cannot declare tools." }
            }

            AgentExecutionKind.PARALLEL -> {
                require(model.isBlank()) { "ParallelAgent '$name' must not declare a model." }
                require(subAgents.isNotEmpty()) { "ParallelAgent '$name' must declare at least one sub-agent." }
                require(loopMaxIterations == null) { "ParallelAgent '$name' cannot declare loopMaxIterations." }
                require(instruction == null) { "ParallelAgent '$name' cannot declare instruction." }
                require(staticInstruction == null) { "ParallelAgent '$name' cannot declare staticInstruction." }
                require(generateContentConfig == null) {
                    "ParallelAgent '$name' cannot declare generateContentConfig."
                }
                require(outputSchema == null) { "ParallelAgent '$name' cannot declare outputSchema." }
                require(planner == null) { "ParallelAgent '$name' cannot declare planner." }
                require(tools.isEmpty()) { "ParallelAgent '$name' cannot declare tools." }
            }
        }
    }
}
