package dev.adk.kotlin

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProviderModelTest {
    @Test
    fun `model factory accepts either model name or model instance`() {
        val gemini = gemini("gemini-2.5-pro")

        val byName = model("gemini-2.5-pro")
        val byInstance = model(gemini)

        assertEquals("gemini-2.5-pro", byName.resolvedModelName())
        assertEquals("gemini-2.5-pro", byInstance.resolvedModelName())
        assertSame(gemini, byInstance.model)

        assertFailsWith<IllegalArgumentException> {
            Model.builder()
                .modelName("gemini-2.5-pro")
                .model(gemini)
                .build()
        }
    }

    @Test
    fun `gemini factory delegates through transport`() =
        runTest {
            var observedStream = false
            val gemini =
                gemini(
                    modelName = "gemini-2.5-pro",
                    apiKey = "test-key",
                    vertexCredentials =
                        vertexCredentials(
                            project = "project-1",
                            location = "us-central1",
                        ),
                    transport =
                        LlmTransport { request, stream ->
                            observedStream = stream
                            assertEquals("gemini-2.5-pro", request.model)
                            ModelResponse.Final("gemini:$stream")
                        },
                )

            val response =
                gemini.generate(
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
                    ),
                )

            assertEquals(ModelResponse.Final("gemini:false"), response)
            assertFalse(observedStream)
            assertTrue(gemini.modelCapabilities.supportsOutputSchemaWithTools)
            assertEquals("test-key", gemini.apiKey)
            assertEquals("project-1", gemini.vertexCredentials?.project)
        }

    @Test
    fun `claude factory keeps max tokens and custom capabilities`() {
        val claude =
            claude(
                modelName = "claude-3-7-sonnet",
                apiKey = "anthropic-key",
                maxTokens = 4096,
                modelCapabilities = ModelCapabilities(supportsOutputSchemaWithTools = true),
            )

        assertEquals("anthropic-key", claude.apiKey)
        assertEquals(4096, claude.maxTokens)
        assertTrue(claude.modelCapabilities.supportsOutputSchemaWithTools)
    }

    @Test
    fun `apigee factory validates model string and exposes derived fields`() {
        val apigee =
            apigeeLlm(
                modelName = "apigee/vertex_ai/v1/gemini-2.5-flash",
                proxyUrl = "https://apigee.example.com",
                customHeaders = mapOf("x-test" to "1"),
            )

        assertEquals("https://apigee.example.com", apigee.effectiveProxyUrl)
        assertTrue(apigee.usesVertexAi)
        assertEquals("v1", apigee.apiVersion)

        val defaultGeminiApigee =
            apigeeLlm("apigee/gemini-2.5-flash")
        assertFalse(defaultGeminiApigee.usesVertexAi)
        assertNull(defaultGeminiApigee.apiVersion)

        assertFailsWith<IllegalArgumentException> {
            apigeeLlm("apigee/vertex_ai/v1")
        }
    }
}
