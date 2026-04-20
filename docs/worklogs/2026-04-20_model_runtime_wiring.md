# 2026-04-20 model runtime wiring

## What I worked on

- Reviewed how `LlmAgent`, the DSL, `PromptAssembler`, and `Runner` currently treat models.
- Identified that the newly added `Model` and provider classes are not yet part of the actual execution path.
- Selected the next implementation unit as wiring `Model` and direct `BaseLlm` instances into the real Kotlin agent/runtime flow.

## Why this work was needed

- The previous architecture review showed that `adk-kotlin` is not wrapping `adk-java`, but the model/provider layer had started to drift toward compatibility-shaped API without runtime integration.
- Right now agents still store only a string model name, which means `Model.builder().model(...)` and provider instances are mostly disconnected from the DSL and runner.
- Fixing this is the highest-priority correction because it keeps the codebase on the path of a real Kotlin port instead of a Java API transcription.

## Planned result for this work unit

- Move `LlmAgent` to hold an official-style `Model`.
- Keep Kotlin DSL ergonomics for `model = "gemini-..."` while also allowing direct `BaseLlm` or `Model`.
- Make `Runner` prefer an agent-bound `BaseLlm` when present.
- Update capability checks, prompt request assembly, tests, and documentation to reflect the new runtime wiring.

## Result

- `LlmAgent` now holds `Model?` instead of a raw string and exposes `modelName` plus `baseLlm` helpers.
- The DSL still supports `model = "gemini-..."`, but now also supports `model(baseLlm)` and `model(model)`.
- `PromptAssembler` now emits requests using the resolved model name from the bound `Model`.
- `Runner` now prefers an agent-bound `BaseLlm` over the fallback runner model and uses the bound model for capability checks.
- Added tests covering direct `BaseLlm` binding through the DSL and runtime path.
- Updated README examples so provider objects can be wired directly into an agent.
