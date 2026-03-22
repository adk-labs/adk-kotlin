package dev.adk.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PromptAssemblerTest {
    @Test
    fun `assembles instructions in official order and injects session state`() {
        val app =
            adkApp("travel-assistant") {
                globalInstruction("Global policy for {user:name}.")
                rootAgent("coordinator") {
                    model = "gemini-2.5-pro"
                    description = "Coordinates trip planning."
                    instruction("Prefer tool results for {user:name}.")
                    subAgent("researcher") {
                        model = "gemini-2.5-flash"
                        description = "Researches complex travel questions."
                    }
                }
            }

        val request =
            PromptAssembler.createRequest(
                app = app,
                agent = app.rootAgent,
                session =
                    AgentSession(
                        id = "session-1",
                        userId = "user-1",
                        state = mapOf("user:name" to "Alice"),
                    ),
                transcript = listOf(UserMessage("Plan a trip to Seoul.")),
            )

        assertEquals("Global policy for Alice.", request.systemInstructions[0])
        assertEquals("Prefer tool results for Alice.", request.systemInstructions[1])
        assertEquals(
            "You are an agent. Your internal name is \"coordinator\". The description about you is \"Coordinates trip planning.\".",
            request.systemInstructions[2],
        )
        assertEquals(
            "\nYou have a list of other agents to transfer to:\n\n" +
                "\nAgent name: researcher\n" +
                "Agent description: Researches complex travel questions.\n\n" +
                "\nIf you are the best to answer the question according to your description, you\n" +
                "can answer it.\n\n" +
                "If another agent is better for answering the question according to its\n" +
                "description, call `transfer_to_agent` function to transfer the\n" +
                "question to that agent. When transferring, do not generate any text other than\n" +
                "the function call.\n\n" +
                "**NOTE**: the only available agents for `transfer_to_agent` function are `researcher`.\n",
            request.systemInstructions[3],
        )

        val transferTool = request.availableTools.firstOrNull { it.name == Runner.TRANSFER_TO_AGENT_TOOL }
        assertNotNull(transferTool)
        assertEquals(listOf("researcher"), transferTool.parameters.single().allowedValues)
    }

    @Test
    fun `inserts dynamic instruction into conversation when static instruction exists`() {
        val app =
            adkApp("planner-app") {
                rootAgent("planner") {
                    model = "gemini-2.5-pro"
                    staticInstruction("Static safety policy.")
                    instruction("Dynamic greeting for {user:name}.")
                }
            }

        val request =
            PromptAssembler.createRequest(
                app = app,
                agent = app.rootAgent,
                session =
                    AgentSession(
                        id = "session-1",
                        userId = "user-1",
                        state = mapOf("user:name" to "Alice"),
                    ),
                transcript =
                    listOf(
                        ModelMessage("How can I help?"),
                        UserMessage("Tell me about flights."),
                    ),
            )

        assertEquals("Static safety policy.", request.systemInstructions[0])
        assertEquals(
            "You are an agent. Your internal name is \"planner\".",
            request.systemInstructions[1],
        )
        assertEquals(3, request.conversation.size)
        assertEquals("Dynamic greeting for Alice.", request.conversation[1].text)
        assertTrue(request.conversation[1] is UserMessage)
    }

    @Test
    fun `adds set_model_response workaround when output schema and tools are combined`() {
        val weatherTool =
            tool(
                name = "lookup_weather",
                description = "Resolve current weather for a city.",
            ) {
                ToolOutput("sunny")
            }

        val app =
            adkApp("structured-app") {
                rootAgent("planner") {
                    model = "gemini-2.5-pro"
                    instruction("Use tools before answering.")
                    outputSchema {
                        field("city", "Resolved city name")
                        field("summary", "Final weather summary")
                    }
                    tool(weatherTool)
                }
            }

        val request =
            PromptAssembler.createRequest(
                app = app,
                agent = app.rootAgent,
                session = AgentSession(id = "session-1", userId = "user-1"),
                transcript = listOf(UserMessage("Summarize the weather.")),
                includeOutputSchemaWorkaround = true,
            )

        assertEquals(PromptAssembler.SET_MODEL_RESPONSE_INSTRUCTION, request.systemInstructions[2])
        val setModelResponseTool = request.availableTools.firstOrNull { it.name == Runner.SET_MODEL_RESPONSE_TOOL }
        assertNotNull(setModelResponseTool)
        assertEquals(listOf("city", "summary"), setModelResponseTool.parameters.map { it.name })
    }
}
