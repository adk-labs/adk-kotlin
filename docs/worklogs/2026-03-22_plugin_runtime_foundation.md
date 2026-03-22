# 2026-03-22 Plugin Runtime Foundation

## Planned Work

- Add official-style plugin abstractions and a plugin manager to `adk-kotlin`.
- Introduce invocation and callback context types so plugins receive stable
  runtime context rather than ad-hoc parameters.
- Wire plugin lifecycle hooks into `Runner` around user input, events, model
  calls, tool execution, and run completion.
- Add tests proving plugin short-circuit and event interception behavior.

## Why This Work

- Official ADK runners expose plugins as the primary global extension point.
- The newly added Kotlin event surface becomes substantially more useful once
  plugins can observe and intercept it.
- Later work on logging, telemetry, auth, and context filtering should be built
  on a real plugin pipeline instead of Kotlin-specific one-off hooks.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`
- New plugin/context model files

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added `Plugin`, `BasePlugin`, `PluginManager`, `InvocationContext`, and
  `CallbackContext` so Kotlin now has official-style plugin lifecycle types.
- Wired `Runner` to invoke plugin callbacks around:
  user message intake,
  pre-run short circuit,
  model execution,
  tool execution,
  event emission,
  and after-run cleanup/reporting.
- Added model and tool short-circuit handling through plugin callbacks, plus
  recovery hooks for model/tool errors.
- Routed every emitted event through `onEventCallback`, allowing plugins to
  replace final responses or tool events before they are persisted.
- Added `Runner.close()` so plugins now have an explicit cleanup hook surface.
- Added tests proving:
  model short-circuit before the provider call,
  event rewriting through plugins,
  tool short-circuit before tool execution,
  and after-run lifecycle execution.
- Updated `README.md` with the new plugin lifecycle surface and usage example.

## Why These Changes

- This gives `adk-kotlin` the same core global extension seam that official ADK
  runtimes use for logging, filtering, policy, telemetry, and caching.
- The event unit from the previous work becomes materially useful only once
  plugins can observe and replace emitted events.
- By adding invocation and callback context types now, later built-in plugins
  can target stable runtime objects instead of retrofitted helper parameters.

## Verification

- `./gradlew test`
