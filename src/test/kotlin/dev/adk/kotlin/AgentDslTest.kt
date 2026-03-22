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
        assertEquals("gemini-2.5-pro", agent.model)
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
}
