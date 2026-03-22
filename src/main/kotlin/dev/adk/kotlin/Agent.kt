package dev.adk.kotlin

data class AdkApp(
    val name: String,
    val rootAgent: LlmAgent,
)

data class LlmAgent(
    val name: String,
    val model: String,
    val description: String = "",
    val instructions: List<String> = emptyList(),
    val tools: List<Tool> = emptyList(),
    val subAgents: List<LlmAgent> = emptyList(),
    val maxIterations: Int = 8,
) {
    init {
        require(name.isNotBlank()) { "Agent name cannot be blank." }
        require(model.isNotBlank()) { "Agent model cannot be blank." }
        require(maxIterations > 0) { "maxIterations must be positive." }
    }

    fun renderSystemInstruction(): String = buildString {
        appendLine("Agent: $name")

        if (description.isNotBlank()) {
            appendLine("Description: $description")
        }

        if (instructions.isNotEmpty()) {
            appendLine("Instructions:")
            instructions.forEach { appendLine("- $it") }
        }

        if (tools.isNotEmpty()) {
            appendLine("Tools:")
            tools.forEach { tool ->
                appendLine("- Tool: ${tool.definition.name} :: ${tool.definition.description}")
            }
        }

        if (subAgents.isNotEmpty()) {
            appendLine("Sub-agents:")
            subAgents.forEach { appendLine("- ${it.name}") }
        }
    }.trim()
}
