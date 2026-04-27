# 2026-04-27 kotlin-first provider factories

## What I worked on

- Reviewed provider/model construction after the conservative registry change.
- Identified that public examples and tests still used Java-style `builder()` calls as the primary path.
- Selected this work unit to add Kotlin-first factory functions while keeping official-style builders as compatibility surface.

## Why this work was needed

- `adk-kotlin` should stay a pure Kotlin port, not a Java API transcription.
- Official names such as `Gemini`, `Claude`, `ApigeeLlm`, `Model`, and `VertexCredentials` are useful for parity, but Kotlin callers should not have to use Java-style builders for normal construction.
- Making Kotlin-first factory functions the primary path keeps official naming while making the SDK idiomatic.

## Planned result for this work unit

- Add `gemini(...)`, `claude(...)`, `apigeeLlm(...)`, `vertexCredentials(...)`, and `model(...)` factory functions.
- Update internal DSL/registry code to use the Kotlin-first helpers where appropriate.
- Update tests and README so the primary usage path no longer teaches builder-first construction.

## Result

- Added Kotlin-first provider and model factory functions.
- Updated `LlmAgentDsl` string model conversion to use the `model(...)` helper.
- Updated explicit registry helper implementations to construct providers through `gemini(...)`, `claude(...)`, and `apigeeLlm(...)`.
- Updated provider tests and README examples to use factory-first construction.
- Kept builder APIs available for official-style compatibility, but moved normal Kotlin usage away from builder-first examples.

## Verification

- Ran `./gradlew test` to verify the factory helpers, registry helpers, DSL model binding, and existing runtime behavior.
