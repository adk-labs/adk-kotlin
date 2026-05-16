package dev.adk.kotlin

import java.nio.charset.StandardCharsets

class SkillToolset(
    private val skillSource: SkillSource,
    toolFilter: List<String>? = null,
    toolNamePrefix: String? = null,
) : BaseToolset(
        toolFilter = toolFilter,
        toolNamePrefix = toolNamePrefix,
    ) {
    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<Tool> =
        listOf(
            ListSkillsTool(this),
            LoadSkillTool(this),
            LoadSkillResourceTool(this),
        )

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest {
        val frontmatters =
            runCatching { skillSource.listFrontmatters().values.toList() }
                .getOrDefault(emptyList())
        val instruction =
            buildString {
                append(DEFAULT_SKILL_SYSTEM_INSTRUCTION)
                if (frontmatters.isNotEmpty()) {
                    append("\n\n")
                    append(formatSkillsAsXml(frontmatters))
                }
            }
        return llmRequest.copy(systemInstructions = llmRequest.systemInstructions + instruction)
    }

    internal suspend fun listSkills(): List<Frontmatter> =
        skillSource.listFrontmatters().values.sortedBy { frontmatter -> frontmatter.name }

    internal suspend fun loadSkill(skillName: String): LoadedSkill? =
        try {
            LoadedSkill(
                frontmatter = skillSource.loadFrontmatter(skillName),
                instructions = skillSource.loadInstructions(skillName),
            )
        } catch (_: SkillSourceException) {
            null
        }

    internal suspend fun loadResource(
        skillName: String,
        resourcePath: String,
    ): ByteArray? =
        try {
            skillSource.loadResource(skillName, resourcePath)
        } catch (_: SkillSourceException) {
            null
        }

    data class LoadedSkill(
        val frontmatter: Frontmatter,
        val instructions: String,
    )

    companion object {
        const val ACTIVATED_SKILL_STATE_PREFIX = "_adk_activated_skill_"

        val DEFAULT_SKILL_SYSTEM_INSTRUCTION =
            """
            You can use specialized 'skills' to help you with complex tasks. You MUST use the skill tools to interact with these skills.

            Skills are folders of instructions and resources that extend your capabilities for specialized tasks. Each skill folder contains:
            - **SKILL.md** (required): The main instruction file with skill metadata and detailed markdown instructions.
            - **references/** (Optional): Additional documentation or examples for skill usage.
            - **assets/** (Optional): Templates, scripts or other resources used by the skill.
            - **scripts/** (Optional): Executable scripts that can be run via bash.

            This is very important:

            1. If a skill seems relevant to the current user query, you MUST use the `load_skill` tool with `skill_name="<SKILL_NAME>"` to read its full instructions before proceeding.
            2. Once you have read the instructions, follow them exactly as documented before replying to the user. For example, If the instruction lists multiple steps, please make sure you complete all of them in order.
            3. The `load_skill_resource` tool is for viewing files within a skill's directory (e.g., `references/*`, `assets/*`, `scripts/*`). Do NOT use other tools to access these files.
            4. Use `run_skill_script` to run scripts from a skill's `scripts/` directory. Use `load_skill_resource` to view script content first if needed.
            """.trimIndent()

        fun formatSkillsAsXml(skills: Iterable<Frontmatter>): String =
            skills.joinToString(
                prefix = "<available_skills>\n",
                postfix = "\n</available_skills>",
                separator = "\n",
            ) { frontmatter -> frontmatter.toXml() }
    }
}

class ListSkillsTool(
    private val toolset: SkillToolset,
) : Tool {
    override val definition: ToolDefinition =
        ToolDefinition(
            name = "list_skills",
            description = "Lists all available skills with their names and descriptions.",
            jsonSchema = toolSchema {},
        )

    override suspend fun execute(
        call: ToolCall,
        context: ToolContext,
    ): ToolOutput =
        ToolOutput(
            SkillToolset.formatSkillsAsXml(toolset.listSkills()),
        )
}

class LoadSkillTool(
    private val toolset: SkillToolset,
) : Tool {
    override val definition: ToolDefinition =
        ToolDefinition(
            name = "load_skill",
            description = "Loads the SKILL.md instructions for a given skill.",
            jsonSchema =
                toolSchema {
                    string(
                        name = "skill_name",
                        description = "The name of the skill to load.",
                    )
                },
        )

    override suspend fun execute(
        call: ToolCall,
        context: ToolContext,
    ): ToolOutput {
        val skillName = call.arguments["skill_name"]?.toString()
            ?: return ToolOutput(errorPayload("Argument 'skill_name' is required.", "INVALID_ARGUMENTS"))
        val skill =
            toolset.loadSkill(skillName)
                ?: return ToolOutput(errorPayload("Skill '$skillName' not found.", "SKILL_NOT_FOUND"))

        context.activateSkill(skillName)
        return ToolOutput(
            """
            {
              "skill_name": "${skillName.escapeJson()}",
              "instructions": "${skill.instructions.escapeJson()}",
              "frontmatter": {
                "name": "${skill.frontmatter.name.escapeJson()}",
                "description": "${skill.frontmatter.description.escapeJson()}"
              }
            }
            """.trimIndent(),
        )
    }
}

class LoadSkillResourceTool(
    private val toolset: SkillToolset,
) : Tool {
    override val definition: ToolDefinition =
        ToolDefinition(
            name = "load_skill_resource",
            description = "Loads a resource file from a skill directory.",
            jsonSchema =
                toolSchema {
                    string(
                        name = "skill_name",
                        description = "The name of the skill.",
                    )
                    string(
                        name = "resource_path",
                        description = "The resource path relative to the skill directory.",
                    )
                },
        )

    override suspend fun execute(
        call: ToolCall,
        context: ToolContext,
    ): ToolOutput {
        val skillName = call.arguments["skill_name"]?.toString()
            ?: return ToolOutput(errorPayload("Argument 'skill_name' is required.", "INVALID_ARGUMENTS"))
        val resourcePath = call.arguments["resource_path"]?.toString()
            ?: return ToolOutput(errorPayload("Argument 'resource_path' is required.", "INVALID_ARGUMENTS"))

        val content =
            toolset.loadResource(skillName, resourcePath)
                ?: return ToolOutput(errorPayload("Resource '$resourcePath' not found for skill '$skillName'.", "RESOURCE_NOT_FOUND"))

        return ToolOutput(
            String(content, StandardCharsets.UTF_8),
        )
    }
}

private fun ToolContext.activateSkill(skillName: String) {
    val key = SkillToolset.ACTIVATED_SKILL_STATE_PREFIX + agent.name
    val current =
        recall(key)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
    if (skillName !in current) {
        remember(key, (current + skillName).joinToString(","))
    }
}

private fun errorPayload(
    message: String,
    code: String,
): String =
    """
    {
      "error": "${message.escapeJson()}",
      "error_code": "$code"
    }
    """.trimIndent()

private fun String.escapeJson(): String =
    buildString {
        this@escapeJson.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
