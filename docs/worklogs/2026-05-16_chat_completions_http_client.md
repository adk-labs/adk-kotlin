# 2026-05-16 Chat Completions HTTP Client

## Context

The latest `adk-java` reference added
`ChatCompletionsHttpClient`, a client for OpenAI-compatible
`/chat/completions` endpoints. `adk-kotlin` had model/provider abstractions and
transport injection points, but no built-in HTTP transport for this official
wire protocol.

## Work

- Add a pure Kotlin `ChatCompletionsHttpClient` backed by JDK `HttpClient`.
- Add `HttpOptions` with official-style `baseUrl`, `headers`, and `timeout`
  semantics.
- Convert `LlmRequest` into OpenAI-compatible chat-completion JSON messages,
  options, and function tool declarations.
- Parse non-streaming chat-completion responses into `ModelResponse.Final` or
  `ModelResponse.ToolCalls`.
- Preserve the official refusal sentinel behavior using `[[REFUSAL]]:`.
- Expose `asTransport()` and `chatCompletionsTransport(...)` so the client can
  plug into existing Kotlin-first provider models without wrapping Java.
- Add local HTTP server tests for request payloads, headers, URL handling,
  errors, tool calls, and unsupported streaming.

## Reason

This closes the newest `adk-java` model-layer gap while fitting the existing
`adk-kotlin` transport architecture. It gives providers such as Apigee/OpenAI
compatible endpoints a concrete runtime path instead of requiring callers to
provide their own test transport.
