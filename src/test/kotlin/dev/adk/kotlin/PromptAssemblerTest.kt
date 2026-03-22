package dev.adk.kotlin

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PromptAssemblerTest {
    @Test
    fun `assembles instructions in official order and injects session state`() =
        runTest {
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
            assertEquals(ToolSchemaType.OBJECT, transferTool.effectiveJsonSchema?.type)
            assertEquals(
                listOf("researcher"),
                transferTool.effectiveJsonSchema?.properties?.getValue("agent_name")?.enumValues,
            )
        }

    @Test
    fun `inserts dynamic instruction into conversation when static instruction exists`() =
        runTest {
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
    fun `adds set_model_response workaround when output schema and tools are combined`() =
        runTest {
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
            assertEquals(ToolSchemaType.OBJECT, setModelResponseTool.effectiveJsonSchema?.type)
            assertEquals(listOf("city", "summary"), setModelResponseTool.effectiveJsonSchema?.required)
            assertEquals(null, request.outputSchema)
            assertEquals(null, request.responseMimeType)
        }

    @Test
    fun `adds native output schema metadata when workaround is not needed`() =
        runTest {
            val app =
                adkApp("structured-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        outputSchema {
                            field("city", "Resolved city name")
                            field("summary", "Final weather summary")
                        }
                    }
                }

            val request =
                PromptAssembler.createRequest(
                    app = app,
                    agent = app.rootAgent,
                    session = AgentSession(id = "session-1", userId = "user-1"),
                    transcript = listOf(UserMessage("Summarize the weather.")),
                )

            assertEquals("gemini-2.5-pro", request.model)
            assertEquals(app.rootAgent.outputSchema, request.outputSchema)
            assertEquals(ModelRequest.JSON_RESPONSE_MIME_TYPE, request.responseMimeType)
        }

    @Test
    fun `propagates generate content config into the model request`() =
        runTest {
            val app =
                adkApp("configured-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                        generateContentConfig =
                            generateContentConfig {
                                temperature = 0.2
                                topP = 0.9
                                maxOutputTokens = 256
                                stopSequences("DONE")
                            }
                    }
                }

            val request =
                PromptAssembler.createRequest(
                    app = app,
                    agent = app.rootAgent,
                    session = AgentSession(id = "session-1", userId = "user-1"),
                    transcript = listOf(UserMessage("Summarize the weather.")),
                )

            assertEquals("gemini-2.5-pro", request.model)
            assertEquals(0.2, request.config?.temperature)
            assertEquals(0.9, request.config?.topP)
            assertEquals(256, request.config?.maxOutputTokens)
            assertEquals(listOf("DONE"), request.config?.stopSequences)
        }

    @Test
    fun `resolves artifact placeholders from artifact service`() =
        runTest {
            val artifactService = InMemoryArtifactService()
            artifactService.saveArtifact(
                appName = "knowledge-app",
                userId = "user-1",
                sessionId = "session-1",
                filename = "knowledge.txt",
                artifact = Artifact("This is my artifact content."),
            )

            val app =
                adkApp("knowledge-app") {
                    globalInstruction("Knowledge: {artifact.knowledge.txt}")
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }

            val request =
                PromptAssembler.createRequest(
                    app = app,
                    agent = app.rootAgent,
                    session = AgentSession(id = "session-1", userId = "user-1"),
                    transcript = listOf(UserMessage("Use the knowledge.")),
                    artifactService = artifactService,
                )

            assertEquals("Knowledge: This is my artifact content.", request.systemInstructions[0])
        }

    @Test
    fun `replaces optional missing artifact placeholders with empty string`() =
        runTest {
            val app =
                adkApp("knowledge-app") {
                    globalInstruction("Optional knowledge: {artifact.missing.txt?}")
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }

            val request =
                PromptAssembler.createRequest(
                    app = app,
                    agent = app.rootAgent,
                    session = AgentSession(id = "session-1", userId = "user-1"),
                    transcript = listOf(UserMessage("Use the knowledge.")),
                    artifactService = InMemoryArtifactService(),
                )

            assertEquals("Optional knowledge: ", request.systemInstructions[0])
        }

    @Test
    fun `fails when artifact placeholders are used without an artifact service`() =
        runTest {
            val app =
                adkApp("knowledge-app") {
                    globalInstruction("Knowledge: {artifact.knowledge.txt}")
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }

            val error =
                assertFailsWith<IllegalStateException> {
                    PromptAssembler.createRequest(
                        app = app,
                        agent = app.rootAgent,
                        session = AgentSession(id = "session-1", userId = "user-1"),
                        transcript = listOf(UserMessage("Use the knowledge.")),
                    )
                }

            assertEquals("Artifact service is not initialized.", error.message)
        }
}
