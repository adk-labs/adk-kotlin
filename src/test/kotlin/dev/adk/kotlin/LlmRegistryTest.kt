package dev.adk.kotlin

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LlmRegistryTest {
    @AfterTest
    fun tearDown() {
        LlmRegistry.resetToDefaults()
    }

    @Test
    fun `registry backed language model resolves registered llms`() =
        runTest {
            LlmRegistry.clearForTests()

            class FakeRegisteredLlm(
                modelName: String,
            ) : BaseLlm(modelName) {
                override val modelCapabilities =
                    ModelCapabilities(
                        supportsOutputSchemaWithTools = true,
                    )

                override suspend fun generateContent(
                    request: ModelRequest,
                    stream: Boolean,
                ): ModelResponse {
                    assertEquals("gemini-test", request.model)
                    return ModelResponse.Final("Resolved by registry.")
                }
            }

            LlmRegistry.registerLlm("gemini-test", LlmFactory { modelName -> FakeRegisteredLlm(modelName) })

            val response =
                RegistryBackedLanguageModel().generate(
                    ModelRequest(
                        model = "gemini-test",
                        appName = "test-app",
                        session = AgentSession(id = "session-1", userId = "user-1"),
                        agent =
                            agent("planner") {
                                model = "gemini-test"
                            },
                        systemInstructions = emptyList(),
                        conversation = listOf(UserMessage("Hello")),
                        availableTools = emptyList(),
                    ),
                )

            assertEquals(ModelResponse.Final("Resolved by registry."), response)
            assertTrue(
                RegistryBackedLanguageModel()
                    .modelCapabilities("gemini-test")
                    .supportsOutputSchemaWithTools,
            )
        }

    @Test
    fun `registry does not advertise providers without transport`() {
        LlmRegistry.resetToDefaults()

        assertFailsWith<IllegalStateException> {
            LlmRegistry.getLlm("gemini-2.5-pro")
        }
        assertFailsWith<IllegalStateException> {
            LlmRegistry.getLlm("claude-3-7-sonnet")
        }
        assertFailsWith<IllegalStateException> {
            LlmRegistry.getLlm("apigee/vertex_ai/v1beta/gemini-2.5-flash")
        }
    }

    @Test
    fun `registry installs official provider helpers only with explicit transport`() =
        runTest {
            LlmRegistry.resetToDefaults()

            LlmRegistry.registerGemini(
                transport =
                    LlmTransport { request, _ ->
                        ModelResponse.Final("gemini:${request.model}")
                    },
            )
            LlmRegistry.registerClaude(
                transport =
                    LlmTransport { request, _ ->
                        ModelResponse.Final("claude:${request.model}")
                    },
            )
            LlmRegistry.registerApigee(
                transport =
                    LlmTransport { request, _ ->
                        ModelResponse.Final("apigee:${request.model}")
                    },
            )

            assertTrue(LlmRegistry.getLlm("gemini-2.5-pro") is Gemini)
            assertTrue(LlmRegistry.getLlm("claude-3-7-sonnet") is Claude)

            val apigee = LlmRegistry.getLlm("apigee/vertex_ai/v1beta/gemini-2.5-flash")
            assertTrue(apigee is ApigeeLlm)
            assertEquals("v1beta", (apigee as ApigeeLlm).apiVersion)
            assertTrue(apigee.usesVertexAi)

            val geminiResponse =
                RegistryBackedLanguageModel().generate(
                    ModelRequest(
                        model = "gemini-2.5-pro",
                        appName = "test-app",
                        session = AgentSession(id = "session-1", userId = "user-1"),
                        agent =
                            agent("planner") {
                                model = "gemini-2.5-pro"
                            },
                        systemInstructions = emptyList(),
                        conversation = listOf(UserMessage("Hello")),
                        availableTools = emptyList(),
                    ),
                )
            assertEquals(ModelResponse.Final("gemini:gemini-2.5-pro"), geminiResponse)
        }
}
