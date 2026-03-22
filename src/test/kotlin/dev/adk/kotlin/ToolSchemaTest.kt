package dev.adk.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ToolSchemaTest {
    @Test
    fun `tool schema builder creates structured object schema`() {
        val schema =
            toolSchema {
                string("city", description = "Resolved city")
                integer("days", description = "Trip duration", required = false)
                array(
                    name = "tags",
                    description = "Travel tags",
                    items = ToolSchema.string(),
                    required = false,
                )
                obj("filters", description = "Optional filters", required = false) {
                    boolean("familyFriendly", required = false)
                }
            }

        assertEquals(ToolSchemaType.OBJECT, schema.type)
        assertEquals(listOf("city"), schema.required)
        assertEquals(ToolSchemaType.STRING, schema.properties.getValue("city").type)
        assertEquals(ToolSchemaType.INTEGER, schema.properties.getValue("days").type)
        assertEquals(ToolSchemaType.ARRAY, schema.properties.getValue("tags").type)
        assertEquals(ToolSchemaType.STRING, schema.properties.getValue("tags").items?.type)
        assertEquals(ToolSchemaType.OBJECT, schema.properties.getValue("filters").type)
    }

    @Test
    fun `legacy tool parameters are promoted to structured schema`() {
        val definition =
            ToolDefinition(
                name = "lookup_weather",
                description = "Resolve current weather for a city.",
                parameters =
                    listOf(
                        ToolParameter(
                            name = "city",
                            description = "Target city",
                            allowedValues = listOf("Seoul", "Busan"),
                        ),
                    ),
            )

        val schema = definition.effectiveJsonSchema

        assertNotNull(schema)
        assertEquals(ToolSchemaType.OBJECT, schema.type)
        assertEquals(listOf("city"), schema.required)
        assertEquals(listOf("Seoul", "Busan"), schema.properties.getValue("city").enumValues)
    }
}
