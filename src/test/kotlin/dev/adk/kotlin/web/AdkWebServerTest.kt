package dev.adk.kotlin.web

import dev.adk.kotlin.LanguageModel
import dev.adk.kotlin.ModelResponse
import dev.adk.kotlin.adkApp
import dev.adk.kotlin.cli.LoadedApp
import dev.adk.kotlin.cli.StaticAgentLoader
import kotlinx.coroutines.test.runTest
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdkWebServerTest {
    private val httpClient: HttpClient = HttpClient.newHttpClient()

    @Test
    fun `web server creates and retrieves sessions`() =
        runTest {
            val app =
                adkApp("qa-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }

            val server =
                AdkWebServer(
                    agentLoader =
                        StaticAgentLoader(
                            mapOf(
                                "qa-app" to
                                    LoadedApp(
                                        app = app,
                                        model = LanguageModel { ModelResponse.Final("unused") },
                                    ),
                            ),
                        ),
                ).start()

            try {
                val createResponse =
                    postJson(
                        "${server.baseUrl}/apps/qa-app/users/user-1/sessions/session-1",
                        """{"state":{"mode":"debug"}}""",
                    )
                val getResponse =
                    get(
                        "${server.baseUrl}/apps/qa-app/users/user-1/sessions/session-1",
                    )

                assertEquals(201, createResponse.statusCode())
                assertTrue(createResponse.body().contains("\"id\":\"session-1\""))
                assertTrue(createResponse.body().contains("\"mode\":\"debug\""))
                assertEquals(200, getResponse.statusCode())
                assertTrue(getResponse.body().contains("\"id\":\"session-1\""))
                assertTrue(getResponse.body().contains("\"mode\":\"debug\""))
            } finally {
                server.close()
            }
        }

    @Test
    fun `web server run returns current invocation events and applies state delta`() =
        runTest {
            val app =
                adkApp("trip-planner") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }

            val fakeModel =
                LanguageModel { request ->
                    assertEquals("Seoul", request.session.state["city"])
                    ModelResponse.Final("Weather for Seoul.")
                }

            val loadedApp = LoadedApp(app = app, model = fakeModel)
            val server =
                AdkWebServer(
                    agentLoader =
                        StaticAgentLoader(
                            mapOf(
                                "trip-planner" to loadedApp,
                            ),
                        ),
                ).start()

            try {
                val response =
                    postJson(
                        "${server.baseUrl}/run",
                        """
                        {
                          "appName": "trip-planner",
                          "userId": "user-1",
                          "sessionId": "session-1",
                          "newMessage": { "text": "What is the weather?" },
                          "stateDelta": { "city": "Seoul" }
                        }
                        """.trimIndent(),
                    )

                val savedSession =
                    loadedApp.sessionStore.get(
                        appName = app.name,
                        userId = "user-1",
                        sessionId = "session-1",
                    )

                assertEquals(200, response.statusCode())
                assertTrue(response.body().contains("What is the weather?"))
                assertTrue(response.body().contains("Weather for Seoul."))
                assertEquals("Seoul", savedSession?.state?.get("city"))
            } finally {
                server.close()
            }
        }

    @Test
    fun `web server run_sse streams events as server sent events`() =
        runTest {
            val app =
                adkApp("stream-app") {
                    rootAgent("planner") {
                        model = "gemini-2.5-pro"
                    }
                }

            val server =
                AdkWebServer(
                    agentLoader =
                        StaticAgentLoader(
                            mapOf(
                                "stream-app" to
                                    LoadedApp(
                                        app = app,
                                        model = LanguageModel { ModelResponse.Final("SSE answer.") },
                                    ),
                            ),
                        ),
                ).start()

            try {
                val response =
                    postJson(
                        "${server.baseUrl}/run_sse",
                        """
                        {
                          "appName": "stream-app",
                          "userId": "user-1",
                          "sessionId": "session-1",
                          "newMessage": { "text": "Stream this turn." },
                          "streaming": true
                        }
                        """.trimIndent(),
                    )

                assertEquals(200, response.statusCode())
                assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/event-stream"))
                assertTrue(response.body().contains("data:"))
                assertTrue(response.body().contains("Stream this turn."))
                assertTrue(response.body().contains("SSE answer."))
            } finally {
                server.close()
            }
        }

    private fun postJson(
        url: String,
        body: String,
    ): HttpResponse<String> =
        httpClient.send(
            HttpRequest
                .newBuilder(URI(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun get(url: String): HttpResponse<String> =
        httpClient.send(
            HttpRequest
                .newBuilder(URI(url))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
}
