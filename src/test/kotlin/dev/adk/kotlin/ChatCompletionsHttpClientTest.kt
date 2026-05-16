package dev.adk.kotlin

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChatCompletionsHttpClientTest {
    private val servers = mutableListOf<HttpServer>()

    @AfterTest
    fun stopServers() {
        servers.forEach { server -> server.stop(0) }
        servers.clear()
    }

    @Test
    fun `complete sends non-streaming payload and parses final response`() =
        runTest {
            val capture = CapturedRequest()
            val server =
                startServer(capture) {
                    """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "Hi"
                          },
                          "finish_reason": "stop"
                        }
                      ]
                    }
                    """.trimIndent()
                }
            val client = ChatCompletionsHttpClient(HttpOptions(baseUrl = server.baseUrl))
            val response =
                client.complete(
                    minimalRequest(
                        config =
                            GenerateContentConfig(
                                temperature = 0.25,
                                topP = 0.9,
                                maxOutputTokens = 64,
                                stopSequences = listOf("END"),
                            ),
                    ),
                    stream = false,
                )

            assertEquals(ModelResponse.Final("Hi"), response)
            assertEquals("/chat/completions", capture.path)
            assertEquals("POST", capture.method)
            assertTrue(capture.contentType.orEmpty().contains("application/json"))

            val json = JsonParser.parseString(capture.body).asJsonObject
            assertEquals("gpt-4", json["model"].asString)
            assertEquals(false, json["stream"].asBoolean)
            assertEquals(0.25, json["temperature"].asDouble)
            assertEquals(0.9, json["top_p"].asDouble)
            assertEquals(64, json["max_tokens"].asInt)
            assertEquals("system", json.getAsJsonArray("messages")[0].asJsonObject["role"].asString)
            assertEquals("user", json.getAsJsonArray("messages")[1].asJsonObject["role"].asString)
            assertEquals("hello", json.getAsJsonArray("messages")[1].asJsonObject["content"].asString)
        }

    @Test
    fun `complete sends custom headers and overrides content type`() =
        runTest {
            val capture = CapturedRequest()
            val server = startServer(capture) { """{"choices":[{"message":{"content":"ok"}}]}""" }
            val client =
                ChatCompletionsHttpClient(
                    HttpOptions(
                        baseUrl = server.baseUrl,
                        headers =
                            mapOf(
                                "Authorization" to "Bearer test-token",
                                "Content-Type" to "text/plain",
                            ),
                    ),
                )

            client.complete(minimalRequest(), stream = false)

            assertEquals("Bearer test-token", capture.authorization)
            assertTrue(capture.contentType.orEmpty().contains("application/json"))
        }

    @Test
    fun `complete handles base url without trailing slash and existing path`() =
        runTest {
            val capture = CapturedRequest()
            val server = startServer(capture) { """{"choices":[{"message":{"content":"ok"}}]}""" }
            val client = ChatCompletionsHttpClient(HttpOptions(baseUrl = server.baseUrl.trimEnd('/')))

            client.complete(minimalRequest(), stream = false)

            assertEquals("/chat/completions", capture.path)
        }

    @Test
    fun `complete parses tool calls`() =
        runTest {
            val capture = CapturedRequest()
            val server =
                startServer(capture) {
                    """
                    {
                      "choices": [
                        {
                          "message": {
                            "tool_calls": [
                              {
                                "id": "call-1",
                                "type": "function",
                                "function": {
                                  "name": "lookup",
                                  "arguments": "{\"city\":\"Seoul\",\"days\":2}"
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                }
            val client = ChatCompletionsHttpClient(HttpOptions(baseUrl = server.baseUrl))
            val response =
                client.complete(
                    minimalRequest(
                        tools =
                            listOf(
                                ToolDefinition(
                                    name = "lookup",
                                    description = "Lookup city data.",
                                    jsonSchema =
                                        toolSchema {
                                            string("city")
                                            integer("days")
                                        },
                                ),
                            ),
                    ),
                    stream = false,
                )

            val toolCalls = response as ModelResponse.ToolCalls
            assertEquals("lookup", toolCalls.calls.single().toolName)
            assertEquals("Seoul", toolCalls.calls.single().arguments["city"])
            assertEquals(2, toolCalls.calls.single().arguments["days"])

            val requestJson = JsonParser.parseString(capture.body).asJsonObject
            val function = requestJson.getAsJsonArray("tools")[0].asJsonObject.getAsJsonObject("function")
            assertEquals("lookup", function["name"].asString)
            assertNotNull(function.getAsJsonObject("parameters").getAsJsonObject("properties")["city"])
        }

    @Test
    fun `complete preserves refusal prefix`() =
        runTest {
            val capture = CapturedRequest()
            val server =
                startServer(capture) {
                    """{"choices":[{"message":{"content":"Cannot answer.","refusal":"unsafe"}}]}"""
                }
            val client = ChatCompletionsHttpClient(HttpOptions(baseUrl = server.baseUrl))

            val response = client.complete(minimalRequest(), stream = false)

            assertEquals(
                ModelResponse.Final("Cannot answer.\n${ChatCompletionsHttpClient.REFUSAL_PREFIX}unsafe"),
                response,
            )
        }

    @Test
    fun `complete propagates http errors and empty bodies`() =
        runTest {
            val errorServer =
                startServer(CapturedRequest(), status = 500) {
                    """{"error":"server exploded"}"""
                }
            val errorClient = ChatCompletionsHttpClient(HttpOptions(baseUrl = errorServer.baseUrl))
            val httpError =
                assertFailsWith<java.io.IOException> {
                    errorClient.complete(minimalRequest(), stream = false)
                }
            assertTrue(httpError.message.orEmpty().contains("server exploded"))

            val emptyServer = startServer(CapturedRequest()) { "" }
            val emptyClient = ChatCompletionsHttpClient(HttpOptions(baseUrl = emptyServer.baseUrl))
            assertFailsWith<java.io.IOException> {
                emptyClient.complete(minimalRequest(), stream = false)
            }
        }

    @Test
    fun `constructor validates base url and timeout policy`() {
        assertFailsWith<IllegalArgumentException> {
            ChatCompletionsHttpClient(HttpOptions(baseUrl = "not a url"))
        }
        assertFailsWith<IllegalArgumentException> {
            HttpOptions(baseUrl = "https://example.com", timeoutMillis = -1)
        }

        assertEquals(
            ChatCompletionsHttpClient.DEFAULT_CALL_TIMEOUT_MILLIS,
            ChatCompletionsHttpClient(HttpOptions(baseUrl = "https://example.com")).callTimeout.toMillis(),
        )
        assertEquals(
            0,
            ChatCompletionsHttpClient(HttpOptions(baseUrl = "https://example.com", timeoutMillis = 0)).callTimeout.toMillis(),
        )
        assertEquals(
            10_000,
            ChatCompletionsHttpClient(HttpOptions(baseUrl = "https://example.com", timeoutMillis = 10_000)).callTimeout.toMillis(),
        )
    }

    @Test
    fun `streaming is explicitly unsupported for now`() =
        runTest {
            val client = ChatCompletionsHttpClient(HttpOptions(baseUrl = "https://example.com"))

            assertFailsWith<UnsupportedOperationException> {
                client.complete(minimalRequest(), stream = true)
            }
        }

    private fun minimalRequest(
        config: GenerateContentConfig? = null,
        tools: List<ToolDefinition> = emptyList(),
    ): LlmRequest =
        ModelRequest(
            model = "gpt-4",
            appName = "test-app",
            session = AgentSession(id = "session-1", userId = "user-1"),
            agent =
                agent("planner") {
                    model = "gpt-4"
                },
            systemInstructions = listOf("You are concise."),
            conversation = listOf(UserMessage("hello")),
            availableTools = tools,
            config = config,
        )

    private fun startServer(
        capture: CapturedRequest,
        status: Int = 200,
        responseBody: () -> String,
    ): TestServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            capture.update(exchange)
            val body = responseBody().toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { output -> output.write(body) }
        }
        server.executor = Executors.newSingleThreadExecutor()
        server.start()
        servers += server
        return TestServer(server)
    }

    private data class TestServer(
        private val server: HttpServer,
    ) {
        val baseUrl: String
            get() = URI("http", null, "127.0.0.1", server.address.port, "/", null, null).toString()
    }

    private class CapturedRequest {
        var method: String? = null
            private set
        var path: String? = null
            private set
        var body: String = ""
            private set
        var contentType: String? = null
            private set
        var authorization: String? = null
            private set

        fun update(exchange: HttpExchange) {
            method = exchange.requestMethod
            path = exchange.requestURI.path
            body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            contentType = exchange.requestHeaders.getFirst("Content-Type")
            authorization = exchange.requestHeaders.getFirst("Authorization")
        }
    }
}
