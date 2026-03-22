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
        assertEquals(2, agent.instructions.size)
        assertEquals(listOf("lookup_weather"), agent.tools.map { it.definition.name })
        assertEquals(listOf("greeter"), agent.subAgents.map { it.name })
        assertTrue(agent.renderSystemInstruction().contains("Tool: lookup_weather"))
    }
}
