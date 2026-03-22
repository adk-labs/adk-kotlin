# 2026-03-22 Logging Plugin Foundation

## Planned Work

- Add a packaged `LoggingPlugin` for console-oriented callback tracing.
- Expose the minimal callback/tool context metadata needed for logging, such as
  branch and function-call identifiers.
- Keep the plugin aligned with the existing Kotlin message, request, response,
  and event model.
- Add tests that verify the plugin records model and tool lifecycle entries.

## Why This Work

- Official ADK ships a ready-to-use logging plugin, and the Kotlin SDK still
  lacks that packaged debugging utility.
- The plugin runtime already exposes enough lifecycle hooks; the remaining gap
  is a small amount of trace metadata and a packaged implementation.
- This improves observability without introducing heavier telemetry or external
  dependencies.

## Files Changed

- `src/main/kotlin/dev/adk/kotlin/InvocationContext.kt`
- `src/main/kotlin/dev/adk/kotlin/Tool.kt`
- `src/main/kotlin/dev/adk/kotlin/LoggingPlugin.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Completed Work

- Added packaged `LoggingPlugin` with console-oriented lifecycle logging for
  user messages, runs, model requests/responses, tool execution, events, and
  errors.
- Added `CallbackContext.branch` and exposed `ToolContext.functionCallId` so
  packaged plugins can log the same tracing details the official SDK surfaces.
- Added a sink-injection option so logging can be tested or redirected without
  introducing an external logging dependency.
- Added a runner test that verifies model/tool lifecycle entries are emitted.

## Verification Result

- Run `./gradlew test`
- Result: passed
