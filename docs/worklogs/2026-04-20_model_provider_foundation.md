# 2026-04-20 model provider foundation

## What I worked on

- Reviewed the current `adk-kotlin` model layer against `ref/adk-java/core/src/main/java/com/google/adk/models`.
- Selected the next implementation unit as official-style provider/model foundation work.
- Added Kotlin equivalents for the missing official provider surface instead of keeping the model layer as a bare registry plus abstract base class.

## Why this work was needed

- The latest Java gap review identified provider implementations as the highest-impact remaining gap.
- `adk-kotlin` currently has `BaseLlm`, `ModelRequest`, `ModelResponse`, and `LlmRegistry`, but not the official provider-named classes such as `Gemini`, `Claude`, `ApigeeLlm`, or `VertexCredentials`.
- Without an official-style provider surface, the Kotlin SDK stays structurally behind `adk-java` even if the runner and prompt/runtime semantics are already credible.

## Planned result for this work unit

- Add official-style provider/model classes with Kotlin-first builders.
- Add default registry wiring for the official model name patterns.
- Add tests covering the new provider classes and registry defaults.
- Update README usage so provider registration examples reflect the new foundation.

## Result

- Added `Gemini`, `Claude`, `ApigeeLlm`, `VertexCredentials`, and `LlmCallsLimitExceededException`.
- Added a `Model` value object with official-style `builder()` support.
- Added `LlmTransport`, `LlmConnectionFactory`, and `TransportBackedLlm` so provider classes can stay Kotlin-first while keeping official naming.
- Updated `LlmRegistry` to ship default mappings for `gemini-.*`, `claude-.*`, and `apigee/.*`.
- Added provider-focused tests and updated registry teardown behavior so default mappings are restored after tests.
- Updated README examples to show the new provider surface.
