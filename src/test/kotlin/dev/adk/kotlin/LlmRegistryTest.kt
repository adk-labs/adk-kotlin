package dev.adk.kotlin

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LlmRegistryTest {
    @AfterTest
    fun tearDown() {
        LlmRegistry.clearForTests()
    }

    @Test
    fun `registry backed language model resolves registered llms`() =
        runTest {
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
}
