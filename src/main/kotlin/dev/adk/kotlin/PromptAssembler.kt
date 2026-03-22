package dev.adk.kotlin

internal object PromptAssembler {
    private val placeholderPattern = Regex("\\{+[^{}]*}+")
    private val validPrefixes = setOf("app:", "user:", "temp:")
    internal const val SET_MODEL_RESPONSE_INSTRUCTION =
        "IMPORTANT: You have access to other tools, but you must provide your final response " +
            "using the set_model_response tool with the required structured format. After using " +
            "any other tools needed to complete the task, always call set_model_response with " +
            "your final answer in the specified schema format."

    suspend fun createRequest(
        app: AdkApp,
        agent: LlmAgent,
        session: AgentSession,
        transcript: List<Message>,
        artifactService: ArtifactService? = null,
        includeOutputSchemaWorkaround: Boolean = false,
    ): ModelRequest {
        val systemInstructions = mutableListOf<String>()
        val nativeOutputSchema = nativeOutputSchema(agent, includeOutputSchemaWorkaround)
        val requestConfig = buildGenerateContentConfig(agent, nativeOutputSchema)

        app.globalInstruction
            ?.let {
                resolveInstruction(
                    instruction = it,
                    appName = app.name,
                    session = session,
                    artifactService = artifactService,
                )
            }
            ?.takeIf { it.isNotEmpty() }
            ?.let(systemInstructions::add)

        val dynamicInstruction =
            agent.instruction
                ?.let {
                    resolveInstruction(
                        instruction = it,
                        appName = app.name,
                        session = session,
                        artifactService = artifactService,
                    )
                }
                ?.takeIf { it.isNotEmpty() }

        agent.staticInstruction
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(systemInstructions::add)

        if (dynamicInstruction != null && agent.staticInstruction == null) {
            systemInstructions += dynamicInstruction
        }

        systemInstructions += identityInstruction(agent)

        if (includeOutputSchemaWorkaround) {
            systemInstructions += SET_MODEL_RESPONSE_INSTRUCTION
        }

        val transferTargets = app.transferTargetsOf(agent)
        if (transferTargets.isNotEmpty()) {
            systemInstructions += buildTransferInstructions(agent, app, transferTargets)
        }

        return ModelRequest(
            model = agent.model,
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
            availableTools =
                buildAvailableTools(
                    agent = agent,
                    transferTargets = transferTargets,
                    includeOutputSchemaWorkaround = includeOutputSchemaWorkaround,
                ),
            config = requestConfig,
            outputSchema = nativeOutputSchema,
        )
    }

    internal suspend fun resolveInstruction(
        instruction: InstructionTemplate,
        appName: String,
        session: AgentSession,
        artifactService: ArtifactService?,
    ): String {
        if (instruction.bypassStateInjection) {
            return instruction.text
        }

        return injectPromptData(
            template = instruction.text,
            appName = appName,
            session = session,
            artifactService = artifactService,
        )
    }

    internal suspend fun injectPromptData(
        template: String,
        appName: String,
        session: AgentSession,
        artifactService: ArtifactService?,
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

        for (match in matches) {
            resolved.append(template.substring(lastEnd, match.range.first))
            resolved.append(resolveMatch(match.value, appName, session, artifactService))
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

    private suspend fun resolveMatch(
        placeholder: String,
        appName: String,
        session: AgentSession,
        artifactService: ArtifactService?,
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
            val artifactName = variableName.removePrefix("artifact.")
            val service = artifactService ?: error("Artifact service is not initialized.")
            val artifact =
                service.loadArtifact(
                    appName = appName,
                    userId = session.userId,
                    sessionId = session.id,
                    filename = artifactName,
                )

            return when {
                artifact != null -> artifact.toString()
                optional -> ""
                else -> error("Artifact $artifactName not found.")
            }
        }

        if (!isValidStateName(variableName)) {
            return placeholder
        }

        val value = session.state[variableName]
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

    private fun nativeOutputSchema(
        agent: LlmAgent,
        includeOutputSchemaWorkaround: Boolean,
    ): OutputSchema? = agent.outputSchema.takeUnless { includeOutputSchemaWorkaround }

    private fun buildGenerateContentConfig(
        agent: LlmAgent,
        nativeOutputSchema: OutputSchema?,
    ): GenerateContentConfig? {
        val baseConfig = agent.generateContentConfig
        if (nativeOutputSchema == null) {
            return baseConfig
        }

        return (baseConfig ?: GenerateContentConfig()).copy(
            responseMimeType = ModelRequest.JSON_RESPONSE_MIME_TYPE,
        )
    }

    private fun buildAvailableTools(
        agent: LlmAgent,
        transferTargets: List<LlmAgent>,
        includeOutputSchemaWorkaround: Boolean,
    ): List<ToolDefinition> {
        val tools = agent.tools.map { it.definition }.toMutableList()

        if (includeOutputSchemaWorkaround) {
            val outputSchema = requireNotNull(agent.outputSchema) { "outputSchema must be set when workaround is enabled." }
            tools +=
                ToolDefinition(
                    name = Runner.SET_MODEL_RESPONSE_TOOL,
                    description =
                        "Set your final response using the required output schema. " +
                            "After using any other tools needed to complete the task, always call " +
                            "set_model_response with your final answer in the specified schema format.",
                    parameters = outputSchema.fields,
                )
        }

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
