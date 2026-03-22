package dev.adk.kotlin

class LoadMemoryTool : Tool {
    override val definition: ToolDefinition =
        ToolDefinition(
            name = "load_memory",
            description = "Loads the memory for the current user.",
            jsonSchema =
                toolSchema {
                    string(
                        name = "query",
                        description = "The query to load the memory for.",
                    )
                },
            parameters =
                listOf(
                    ToolParameter(
                        name = "query",
                        description = "The query to load the memory for.",
                    ),
                ),
        )

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest =
        llmRequest.appendInstruction(
            """
            You have memory. You can use it to answer questions. If any questions need
            you to look up the memory, you should call load_memory function with a query.
            """.trimIndent(),
        )

    override suspend fun execute(
        call: ToolCall,
        context: ToolContext,
    ): ToolOutput {
        val query = call.requireArgument("query")
        val memories = context.searchMemory(query).memories
        if (memories.isEmpty()) {
            return ToolOutput("No matching memory found.")
        }

        return ToolOutput(
            memories.joinToString(separator = "\n\n", transform = ::renderMemoryEntry),
        )
    }
}

class PreloadMemoryTool : Tool {
    override val definition: ToolDefinition =
        ToolDefinition(
            name = "preload_memory",
            description = "preload_memory",
            customMetadata = mapOf("hidden" to true),
        )

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest {
        val userQuery =
            llmRequest.conversation
                .lastOrNull { message -> message.role == MessageRole.USER }
                ?.text
                ?.trim()
                .orEmpty()
        if (userQuery.isBlank()) {
            return llmRequest
        }

        val response =
            runCatching { toolContext.searchMemory(userQuery) }
                .getOrElse { return llmRequest }
        if (response.memories.isEmpty()) {
            return llmRequest
        }

        val fullMemoryText =
            response.memories
                .map(::renderMemoryEntry)
                .filter(String::isNotBlank)
                .joinToString("\n")
        if (fullMemoryText.isBlank()) {
            return llmRequest
        }

        return llmRequest.appendInstruction(
            """
            The following content is from your previous conversations with the user.
            They may be useful for answering the user's current query.
            <PAST_CONVERSATIONS>
            $fullMemoryText
            </PAST_CONVERSATIONS>
            """.trimIndent(),
        )
    }

    override suspend fun execute(
        call: ToolCall,
        context: ToolContext,
    ): ToolOutput = ToolOutput("preload_memory is automatic.")
}

class LoadArtifactsTool : Tool {
    override val definition: ToolDefinition =
        ToolDefinition(
            name = "load_artifacts",
            description =
                """
                Loads artifacts into the session for this request.

                NOTE: Call when you need access to artifacts (for example, uploads saved by the
                web UI).
                """.trimIndent(),
            jsonSchema =
                toolSchema {
                    array(
                        name = "artifact_names",
                        items = ToolSchema.string(),
                        description = "Names of the artifacts to load.",
                        required = false,
                    )
                },
            parameters =
                listOf(
                    ToolParameter(
                        name = "artifact_names",
                        description = "Names of the artifacts to load.",
                        required = false,
                    ),
                ),
        )

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest {
        val artifactNames = toolContext.listArtifacts()
        if (artifactNames.isEmpty()) {
            return llmRequest
        }

        return llmRequest.appendInstruction(
            """
            You have a list of artifacts:
              ${renderJsonStringArray(artifactNames)}

            When the user asks questions about any of the artifacts, you should call the
            `load_artifacts` function to load the artifact. Always call load_artifacts
            before answering questions related to the artifacts, regardless of whether the
            artifacts have been loaded before. Do not depend on prior answers about the
            artifacts.
            """.trimIndent(),
        )
    }

    override suspend fun execute(
        call: ToolCall,
        context: ToolContext,
    ): ToolOutput {
        val artifactNames = call.stringListArgument("artifact_names")
        if (artifactNames.isEmpty()) {
            return ToolOutput("No artifacts requested.")
        }

        val renderedArtifacts =
            artifactNames.map { artifactName ->
                val artifact = context.loadArtifact(artifactName)
                if (artifact == null) {
                    "Artifact $artifactName was not found."
                } else {
                    "Artifact $artifactName is:\n${artifact.content}"
                }
            }

        return ToolOutput(
            content = renderedArtifacts.joinToString("\n\n"),
            skipSummarization = true,
        )
    }
}

val loadMemory: Tool = LoadMemoryTool()
val preloadMemory: Tool = PreloadMemoryTool()
val loadArtifacts: Tool = LoadArtifactsTool()

private fun renderMemoryEntry(memory: MemoryEntry): String =
    buildString {
        append("Time: ")
        append(memory.createdAt)
        append('\n')
        append(memory.text)
    }

private fun renderJsonStringArray(values: List<String>): String =
    values.joinToString(
        prefix = "[",
        postfix = "]",
        separator = ",",
    ) { value ->
        buildString {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }
    }

private fun LlmRequest.appendInstruction(instruction: String): LlmRequest {
    if (instruction.isBlank()) {
        return this
    }

    return copy(
        systemInstructions = systemInstructions + instruction,
    )
}
