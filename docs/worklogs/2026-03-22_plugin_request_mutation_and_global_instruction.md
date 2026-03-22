# 2026-03-22 Plugin Request Mutation And Global Instruction

## Planned Work

- Extend the plugin runtime so plugins can rewrite outgoing `LlmRequest`
  instances before model generation.
- Add an official-style `GlobalInstructionPlugin` on top of that request
  mutation seam.
- Keep existing plugin short-circuit behavior intact while allowing
  request-shaping plugins to compose in order.
- Add tests covering plugin request mutation and global instruction prepending.

## Why This Work

- The current plugin runtime can short-circuit model execution, but it cannot
  safely modify immutable Kotlin `LlmRequest` objects the way official ADK
  plugins do.
- Official plugins such as `global_instruction_plugin` depend on before-model
  request mutation, so this is a prerequisite for broader plugin parity.
- This keeps the Kotlin runtime aligned with official behavior without giving
  up immutable request models.

## Files Changed

- `src/main/kotlin/dev/adk/kotlin/Plugin.kt`
- `src/main/kotlin/dev/adk/kotlin/PluginManager.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/main/kotlin/dev/adk/kotlin/InvocationContext.kt`
- `src/main/kotlin/dev/adk/kotlin/GlobalInstructionPlugin.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Completed Work

- Added plugin-level `processLlmRequest(...)` so plugins can rewrite immutable
  Kotlin `LlmRequest` objects before model execution.
- Wired `PluginManager` and `Runner` to fold request mutations before the
  existing `beforeModelCallback` short-circuit hook.
- Extended `InvocationContext` and `CallbackContext` with artifact-service
  access so instruction-resolving plugins can use the same injection path as
  prompt assembly.
- Added packaged `GlobalInstructionPlugin` plus `globalInstructionPlugin(...)`
  helpers for string- and provider-based instruction prepending.
- Added tests for ordered plugin request rewriting and session-state-injected
  global instruction prepending.

## Verification Result

- Run `./gradlew test`
- Result: passed
