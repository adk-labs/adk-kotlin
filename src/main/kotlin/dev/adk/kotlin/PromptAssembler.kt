package dev.adk.kotlin

internal object PromptAssembler {
    private val placeholderPattern = Regex("\\{+[^{}]*}+")
    private val validPrefixes = setOf("app:", "user:", "temp:")

    fun createRequest(
        app: AdkApp,
        agent: LlmAgent,
        session: AgentSession,
        transcript: List<Message>,
    ): ModelRequest {
        val systemInstructions = mutableListOf<String>()

        app.globalInstruction
            ?.let { resolveInstruction(it, session.state) }
            ?.takeIf { it.isNotEmpty() }
            ?.let(systemInstructions::add)

        val dynamicInstruction =
            agent.instruction
                ?.let { resolveInstruction(it, session.state) }
                ?.takeIf { it.isNotEmpty() }

        agent.staticInstruction
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(systemInstructions::add)

        if (dynamicInstruction != null && agent.staticInstruction == null) {
            systemInstructions += dynamicInstruction
        }

        systemInstructions += identityInstruction(agent)

        val transferTargets = app.transferTargetsOf(agent)
        if (transferTargets.isNotEmpty()) {
            systemInstructions += buildTransferInstructions(agent, app, transferTargets)
        }

        return ModelRequest(
            appName = app.name,
            session = session,
            agent = agent,
            systemInstructions = systemInstructions.toList(),
            conversation =
                if (dynamicInstruction != null && agent.staticInstruction != null) {
                    insertInstructionBeforeLastUserBatch(transcript, dynamicInstruction)
                } else {
                    transcript
                },
            availableTools = buildAvailableTools(agent, transferTargets),
        )
    }

    internal fun resolveInstruction(
        instruction: InstructionTemplate,
        state: Map<String, String>,
    ): String {
        if (instruction.bypassStateInjection) {
            return instruction.text
        }

        return injectSessionState(instruction.text, state)
    }

    internal fun injectSessionState(
        template: String,
        state: Map<String, String>,
    ): String {
        if (template.isEmpty()) {
            return template
        }

        val matches = placeholderPattern.findAll(template).toList()
        if (matches.isEmpty()) {
            return template
        }

        val resolved = StringBuilder()
        var lastEnd = 0

        matches.forEach { match ->
            resolved.append(template.substring(lastEnd, match.range.first))
            resolved.append(resolveMatch(match.value, state))
            lastEnd = match.range.last + 1
        }

        resolved.append(template.substring(lastEnd))
        return resolved.toString()
    }

    internal fun identityInstruction(agent: LlmAgent): String =
        buildString {
            append("You are an agent. Your internal name is \"")
            append(agent.name)
            append("\".")
            if (agent.description.isNotEmpty()) {
                append(" The description about you is \"")
                append(agent.description)
                append("\".")
            }
        }

    internal fun buildTransferInstructions(
        agent: LlmAgent,
        app: AdkApp,
        targetAgents: List<LlmAgent>,
    ): String {
        val instruction = StringBuilder()
        instruction.append("\nYou have a list of other agents to transfer to:")
        instruction.append("\n\n")

        val agentNames = mutableListOf<String>()
        targetAgents.forEach { targetAgent ->
            agentNames += "`${targetAgent.name}`"
            instruction.append("\nAgent name: ").append(targetAgent.name)
            instruction.append("\nAgent description: ").append(targetAgent.description)
            instruction.append("\n\n")
        }

        instruction.append(
            "\nIf you are the best to answer the question according to your description, you\n" +
                "can answer it.\n\n" +
                "If another agent is better for answering the question according to its\n" +
                "description, call `transfer_to_agent` function to transfer the\n" +
                "question to that agent. When transferring, do not generate any text other than\n" +
                "the function call.\n\n" +
                "**NOTE**: the only available agents for `transfer_to_agent` function are ",
        )

        agentNames.sort()
        instruction.append(agentNames.joinToString(", "))
        instruction.append(".\n")

        val parent = app.parentOf(agent)
        if (parent != null && !agent.disallowTransferToParent) {
            instruction.append(
                "\nIf neither you nor the other agents are best for the question, transfer to your parent agent ",
            )
            instruction.append(parent.name)
            instruction.append(".\n")
        }

        return instruction.toString()
    }

    private fun resolveMatch(
        placeholder: String,
        state: Map<String, String>,
    ): String {
        var variableName =
            placeholder
                .replace(Regex("^\\{+"), "")
                .replace(Regex("\\}+$"), "")
                .trim()

        val optional = variableName.endsWith("?")
        if (optional) {
            variableName = variableName.dropLast(1)
        }

        if (variableName.startsWith("artifact.")) {
            error("Artifact-backed instruction injection is not implemented yet.")
        }

        if (!isValidStateName(variableName)) {
            return placeholder
        }

        val value = state[variableName]
        return when {
            value != null -> value
            optional -> ""
            else -> error("Context variable not found: `$variableName`.")
        }
    }

    private fun isValidStateName(variableName: String): Boolean {
        val parts = variableName.split(":", limit = 2)
        return when (parts.size) {
            1 -> isIdentifier(parts[0])
            2 -> "${parts[0]}:" in validPrefixes && isIdentifier(parts[1])
            else -> false
        }
    }

    private fun isIdentifier(value: String): Boolean {
        if (value.isEmpty()) {
            return false
        }

        if (!value[0].isJavaIdentifierStart()) {
            return false
        }

        return value.drop(1).all(Char::isJavaIdentifierPart)
    }

    private fun buildAvailableTools(
        agent: LlmAgent,
        transferTargets: List<LlmAgent>,
    ): List<ToolDefinition> {
        val tools = agent.tools.map { it.definition }.toMutableList()

        if (transferTargets.isNotEmpty()) {
            tools +=
                ToolDefinition(
                    name = Runner.TRANSFER_TO_AGENT_TOOL,
                    description =
                        """
                        Transfer the question to another agent.

                        This tool hands off control to another agent when it's more suitable to
                        answer the user's question according to the agent's description.

                        Args:
                          agent_name: the agent name to transfer to.
                        """.trimIndent(),
                    parameters =
                        listOf(
                            ToolParameter(
                                name = "agent_name",
                                allowedValues = transferTargets.map { it.name }.sorted(),
                            ),
                        ),
                )
        }

        return tools
    }

    private fun insertInstructionBeforeLastUserBatch(
        transcript: List<Message>,
        instruction: String,
    ): List<Message> {
        if (instruction.isBlank()) {
            return transcript
        }

        var insertIndex = transcript.size

        for (index in transcript.indices.reversed()) {
            val message = transcript[index]
            if (message.role != MessageRole.USER) {
                insertIndex = index + 1
                break
            }
            insertIndex = index
        }

        val mutableConversation = transcript.toMutableList()
        mutableConversation.add(insertIndex, UserMessage(instruction))
        return mutableConversation.toList()
    }
}
