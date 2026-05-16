package dev.adk.kotlin

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillToolsetTest {
    @Test
    fun `skill toolset exposes official tool names`() =
        runTest {
            val toolset = SkillToolset(testSkillSource())

            val tools = toolset.getTools()

            assertEquals(
                listOf("list_skills", "load_skill", "load_skill_resource"),
                tools.map { tool -> tool.definition.name },
            )
        }

    @Test
    fun `skill toolset injects official skill instruction and available skills`() =
        runTest {
            val toolset = SkillToolset(testSkillSource())
            val request = minimalRequest()

            val updated =
                toolset.processLlmRequest(
                    toolContext = testToolContext(),
                    llmRequest = request,
                )

            val instruction = updated.systemInstructions.last()
            assertTrue(instruction.contains("You can use specialized 'skills'"))
            assertTrue(instruction.contains("<available_skills>"))
            assertTrue(instruction.contains("travel-planner"))
        }

    @Test
    fun `list skills tool returns XML frontmatter`() =
        runTest {
            val output =
                ListSkillsTool(SkillToolset(testSkillSource()))
                    .execute(ToolCall("list_skills"), testToolContext())

            assertTrue(output.content.contains("<available_skills>"))
            assertTrue(output.content.contains("travel-planner"))
            assertTrue(output.content.contains("Plan travel itineraries."))
        }

    @Test
    fun `load skill tool returns instructions and records activation in state`() =
        runTest {
            val context = testToolContext()
            val output =
                LoadSkillTool(SkillToolset(testSkillSource()))
                    .execute(
                        ToolCall("load_skill", mapOf("skill_name" to "travel-planner")),
                        context,
                    )

            assertTrue(output.content.contains("Use this skill to plan trips."))
            assertTrue(output.content.contains("\"name\": \"travel-planner\""))
            assertEquals("travel-planner", context.recall("_adk_activated_skill_planner"))
        }

    @Test
    fun `load skill and resource tools return structured errors`() =
        runTest {
            val toolset = SkillToolset(testSkillSource())

            val missingSkill =
                LoadSkillTool(toolset)
                    .execute(
                        ToolCall("load_skill", mapOf("skill_name" to "missing")),
                        testToolContext(),
                    )
            val missingResource =
                LoadSkillResourceTool(toolset)
                    .execute(
                        ToolCall(
                            "load_skill_resource",
                            mapOf("skill_name" to "travel-planner", "resource_path" to "missing.md"),
                        ),
                        testToolContext(),
                    )

            assertTrue(missingSkill.content.contains("SKILL_NOT_FOUND"))
            assertTrue(missingResource.content.contains("RESOURCE_NOT_FOUND"))
        }

    @Test
    fun `load skill resource tool returns resource content`() =
        runTest {
            val output =
                LoadSkillResourceTool(SkillToolset(testSkillSource()))
                    .execute(
                        ToolCall(
                            "load_skill_resource",
                            mapOf("skill_name" to "travel-planner", "resource_path" to "references/example.md"),
                        ),
                        testToolContext(),
                    )

            assertEquals("Example reference", output.content)
        }

    private fun testSkillSource(): SkillSource =
        InMemorySkillSource
            .builder()
            .skill("travel-planner")
            .frontmatter(
                Frontmatter
                    .builder()
                    .name("travel-planner")
                    .description("Plan travel itineraries.")
                    .build(),
            )
            .instructions("Use this skill to plan trips.")
            .addResource("references/example.md", "Example reference")
            .build()

    private fun minimalRequest(): LlmRequest =
        ModelRequest(
            model = "gemini-2.5-pro",
            appName = "travel-app",
            session = AgentSession(id = "session-1", userId = "user-1"),
            agent =
                agent("planner") {
                    model = "gemini-2.5-pro"
                },
            systemInstructions = emptyList(),
            conversation = listOf(UserMessage("hello")),
            availableTools = emptyList(),
        )

    private fun testToolContext(): ToolContext =
        ToolContext(
            appName = "travel-app",
            agent =
                agent("planner") {
                    model = "gemini-2.5-pro"
                },
            session = AgentSession(id = "session-1", userId = "user-1"),
            workingState = linkedMapOf(),
            temporaryStorage = linkedMapOf(),
            artifactService = InMemoryArtifactService(),
            agentToolExecutor = { _, _, _, _, _ -> ToolOutput("") },
        )
}
