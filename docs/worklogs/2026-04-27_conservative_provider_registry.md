# 2026-04-27 conservative provider registry

## What I worked on

- Reviewed `LlmRegistry` after the model/runtime wiring change.
- Found that the registry still installed default factories for `gemini-.*`, `claude-.*`, and `apigee/.*`.
- Selected this work unit to remove unsupported default provider registration and make transport-backed providers explicit.

## Why this work was needed

- `adk-kotlin` is a pure Kotlin port, not a wrapper over `adk-java`.
- The current provider classes are transport-ready, but they do not yet ship real Kotlin transport implementations.
- Default registry mappings made those provider families look executable even though a registry-backed run would fail unless a transport was injected.
- A Kotlin-first SDK should not advertise runtime support before the runtime can actually execute it.

## Planned result for this work unit

- Keep provider classes available for direct binding and explicit registration.
- Make `LlmRegistry.resetToDefaults()` clear runtime registrations instead of installing unsupported provider defaults.
- Add explicit provider registration helpers for transport-backed `Gemini`, `Claude`, and `ApigeeLlm`.
- Update tests and README to show explicit provider registration.

## Result

- Removed unsupported default provider mappings from `LlmRegistry`.
- Added `LlmTransportFactory` and explicit `registerGemini`, `registerClaude`, and `registerApigee` helpers.
- Kept provider classes available for direct agent binding through `model(provider)` and for explicit registry-backed usage.
- Updated tests so `gemini-.*`, `claude-.*`, and `apigee/.*` are not considered supported until transport is registered.
- Updated README to avoid saying the default registry can execute official providers without transport.
