# 2026-03-22 Model Registry Foundation

## Planned Work

- Add a model-layer foundation closer to the official ADK surface:
  `BaseLlm`, model registry, and provider-resolution helpers.
- Extend the Kotlin request model so provider adapters receive explicit
  `model` and generation config metadata rather than only an already-shaped
  prompt payload.
- Add agent-level `generateContentConfig` support so model configuration can be
  declared on `Agent` and passed through to `ModelRequest`.
- Add tests for registry resolution and request config propagation.

## Why This Work

- `adk-kotlin` currently stops at a single `LanguageModel` function interface,
  which is too thin for official-style provider modules.
- Both `adk-python` and `adk-java` have a real model layer with a base LLM
  abstraction, request/response types, and a registry for model resolution.
- Without this foundation, later work on providers, events, plugins, and richer
  tools would have to invent Kotlin-only seams instead of using an official-like
  runtime shape.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Model.kt`
- `src/main/kotlin/dev/adk/kotlin/Agent.kt`
- `src/main/kotlin/dev/adk/kotlin/Dsl.kt`
- `src/main/kotlin/dev/adk/kotlin/PromptAssembler.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/test/kotlin/dev/adk/kotlin/AgentDslTest.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- New model-layer files for registry/base abstractions

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added `GenerateContentConfig` plus Kotlin DSL support so model generation
  settings can be declared on `Agent` and propagated to requests.
- Extended `ModelRequest` with explicit `model` and `config` fields, which
  makes provider adapters consume a more official ADK-like request shape.
- Added `BaseLlm`, `BaseLlmConnection`, `LlmRegistry`, `LLMRegistry` alias
  property, `LlmFactory`, and `RegistryBackedLanguageModel` so Kotlin now has a
  real model/provider foundation instead of only a raw callback interface.
- Updated prompt assembly to pass agent model metadata and merged generation
  config, including native structured-output MIME type when applicable.
- Updated runner capability checks so registry-backed providers can expose
  model-specific structured-output capabilities.
- Added tests for:
  agent-level config storage,
  request config propagation,
  registry resolution,
  and runner execution through a registered `BaseLlm`.
- Updated `README.md` with the new model registry path and request-config
  examples.

## Why These Changes

- This gives Kotlin a provider-ready substrate closer to both `adk-python` and
  `adk-java`, which is necessary before implementing real model modules.
- Carrying model/config metadata in `ModelRequest` prevents future providers
  from having to reverse-engineer agent state out of already-assembled prompts.
- Registry-backed resolution is the bridge from the current narrow runner to an
  official-style provider ecosystem with per-model capability logic.

## Verification

- `./gradlew test`
