package dev.adk.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentDslTest {
    @Test
    fun `builds an immutable app from the Kotlin DSL`() {
        val lookupWeather =
            tool(
                name = "lookup_weather",
                description = "Resolve current weather for a city.",
            ) {
                ToolOutput("sunny")
            }

        val app =
            adkApp("travel-assistant") {
                globalInstruction("Always respond for {user:name}.")
                rootAgent("coordinator") {
                    model = "gemini-2.5-pro"
                    description = "Coordinates trip planning."
                    instructions(
                        "Ask for clarification only when the request is ambiguous.",
                        "Prefer tool results over guessing.",
                    )
                    tool(lookupWeather)
                    subAgent("greeter") {
                        model = "gemini-2.5-flash"
                        instruction("Open with a concise greeting.")
                    }
                }
            }

        val agent = app.rootAgent

        assertEquals("travel-assistant", app.name)
        assertEquals("coordinator", agent.name)
        assertEquals("gemini-2.5-pro", agent.modelName)
        assertEquals("Always respond for {user:name}.", app.globalInstruction?.text)
        assertEquals(
            "Ask for clarification only when the request is ambiguous.\nPrefer tool results over guessing.",
            agent.instruction?.text,
        )
        assertEquals(listOf("lookup_weather"), agent.tools.map { it.definition.name })
        assertEquals(listOf("greeter"), agent.subAgents.map { it.name })
        assertTrue(app.transferTargetsOf(agent).map { it.name }.contains("greeter"))
    }

    @Test
    fun `stores generate content config on agent`() {
        val app =
            adkApp("travel-assistant") {
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

        assertEquals(0.2, app.rootAgent.generateContentConfig?.temperature)
        assertEquals(0.9, app.rootAgent.generateContentConfig?.topP)
        assertEquals(256, app.rootAgent.generateContentConfig?.maxOutputTokens)
        assertEquals(listOf("DONE"), app.rootAgent.generateContentConfig?.stopSequences)
    }

    @Test
    fun `supports official naming aliases and plural DSL methods`() {
        val lookupWeather =
            tool(
                name = "lookup_weather",
                description = "Resolve current weather for a city.",
            ) {
                state["last_city"] = "Seoul"
                ToolOutput("sunny")
            }

        val greeter: Agent =
            agent("greeter") {
                model = "gemini-2.5-flash"
                instruction("Open with a concise greeting.")
            }

        val coordinator: Agent =
            agent("coordinator") {
                model = "gemini-2.5-pro"
                description = "Coordinates trip planning."
                tools(lookupWeather)
                subAgents(greeter)
            }

        val officialApp: App =
            app("travel_assistant") {
                globalInstruction("Always respond for {user:name}.")
                rootAgent(coordinator)
            }

        assertEquals("travel_assistant", officialApp.name)
        assertEquals("coordinator", officialApp.rootAgent.name)
        assertEquals(listOf("lookup_weather"), officialApp.rootAgent.tools.map { it.definition.name })
        assertEquals(listOf("greeter"), officialApp.rootAgent.subAgents.map { it.name })
    }

    @Test
    fun `builds sequential agents with official naming`() {
        val researcher =
            agent("researcher") {
                model = "gemini-2.5-flash"
                instruction("Research the request.")
            }

        val reviewer =
            agent("reviewer") {
                model = "gemini-2.5-pro"
                instruction("Review the result.")
            }

        val pipeline: SequentialAgent =
            sequentialAgent("pipeline") {
                description = "Runs specialists in sequence."
                subAgents(researcher, reviewer)
            }

        val app =
            app("travel_assistant") {
                rootAgent(pipeline)
            }

        assertEquals(AgentExecutionKind.SEQUENTIAL, app.rootAgent.executionKind)
        assertEquals("", app.rootAgent.modelName)
        assertEquals(null, app.rootAgent.model)
        assertEquals(listOf("researcher", "reviewer"), app.rootAgent.subAgents.map { it.name })
    }

    @Test
    fun `builds loop agents with official naming`() {
        val worker =
            agent("worker") {
                model = "gemini-2.5-flash"
                instruction("Iterate until the task is complete.")
            }

        val orchestrator: LoopAgent =
            loopAgent("orchestrator") {
                description = "Repeats work until exit."
                maxIterations = 3
                subAgents(worker)
            }

        assertEquals(AgentExecutionKind.LOOP, orchestrator.executionKind)
        assertEquals(3, orchestrator.loopMaxIterations)
        assertEquals(listOf("worker"), orchestrator.subAgents.map { it.name })
    }

    @Test
    fun `builds parallel agents with official naming`() {
        val fast =
            agent("fast") {
                model = "gemini-2.5-flash"
                instruction("Produce a fast attempt.")
            }

        val slow =
            agent("slow") {
                model = "gemini-2.5-pro"
                instruction("Produce a slower attempt.")
            }

        val fanOut: ParallelAgent =
            parallelAgent("fan_out") {
                description = "Runs workers in parallel."
                subAgents(fast, slow)
            }

        assertEquals(AgentExecutionKind.PARALLEL, fanOut.executionKind)
        assertEquals("", fanOut.modelName)
        assertEquals(null, fanOut.model)
        assertEquals(listOf("fast", "slow"), fanOut.subAgents.map { it.name })
    }

    @Test
    fun `supports direct BaseLlm instances in the Kotlin DSL`() {
        val gemini =
            Gemini.builder()
                .modelName("gemini-2.5-pro")
                .transport { _, _ -> ModelResponse.Final("ok") }
                .build()

        val built =
            agent("planner") {
                model(gemini)
                instruction("Plan the request.")
            }

        assertEquals("gemini-2.5-pro", built.modelName)
        assertEquals(gemini, built.baseLlm)
        assertEquals(gemini.modelCapabilities, built.baseLlm?.modelCapabilities)
    }

    @Test
    fun `stores planner configuration on llm agents`() {
        val planner = builtInPlanner { thinkingBudget = 128 }
        val codeExecutor = unsafeLocalCodeExecutor(timeoutSeconds = 5)
        val sampleToolset =
            object : BaseToolset(toolNamePrefix = "sample") {
                override suspend fun getTools(readonlyContext: ReadonlyContext?): List<Tool> =
                    listOf(
                        tool(
                            name = "lookup",
                            description = "Lookup something.",
                        ) {
                            ToolOutput("done")
                        },
                    )
            }

        val app =
            adkApp("planner-app") {
                rootAgent("planner") {
                    model = "gemini-2.5-pro"
                    this.planner = planner
                    includeContents = IncludeContents.NONE
                    this.codeExecutor = codeExecutor
                    inputSchema {
                        string("topic", description = "Topic to analyze")
                    }
                    outputKey = "planner_output"
                    toolset(sampleToolset)
                }
            }

        assertEquals(planner, app.rootAgent.planner)
        assertEquals(IncludeContents.NONE, app.rootAgent.includeContents)
        assertEquals(codeExecutor, app.rootAgent.codeExecutor)
        assertEquals(ToolSchemaType.OBJECT, app.rootAgent.inputSchema?.type)
        assertEquals(listOf("topic"), app.rootAgent.inputSchema?.required)
        assertEquals("planner_output", app.rootAgent.outputKey)
        assertEquals(sampleToolset, app.rootAgent.toolsets.single())
    }
}
