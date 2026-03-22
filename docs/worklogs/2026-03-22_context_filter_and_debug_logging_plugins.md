# 2026-03-22 Context Filter And Debug Logging Plugins

## Planned Work

- Add a packaged `ContextFilterPlugin` for invocation-based transcript trimming
  on outgoing `LlmRequest` objects.
- Add a packaged `DebugLoggingPlugin` that writes per-invocation debug traces to
  disk.
- Keep both plugins aligned with the current immutable Kotlin message model and
  existing plugin lifecycle hooks.
- Add tests for invocation trimming and debug-log file emission.

## Why This Work

- Official ADK ships packaged plugins beyond the base plugin runtime, and the
  Kotlin SDK still lacks those ready-to-use utilities.
- `ContextFilterPlugin` is a practical parity feature for controlling prompt
  growth now that plugin request mutation exists.
- `DebugLoggingPlugin` provides a useful developer-facing artifact for debugging
  runner behavior without requiring external observability infrastructure.

## Files Changed

- `src/main/kotlin/dev/adk/kotlin/ContextFilterPlugin.kt`
- `src/main/kotlin/dev/adk/kotlin/DebugLoggingPlugin.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/main/kotlin/dev/adk/kotlin/Tool.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Completed Work

- Added packaged `ContextFilterPlugin` with invocation-based transcript
  trimming and optional custom filtering over immutable message lists.
- Added packaged `DebugLoggingPlugin` that writes per-invocation debug traces
  to a local YAML-like file, including request, response, event, and final
  session-state summaries.
- Extended `ToolContext` and runner wiring with `invocationId` so packaged
  plugins can correlate tool lifecycle entries with the active invocation.
- Added tests for recent-invocation trimming and debug-log file emission.

## Verification Result

- Run `./gradlew test`
- Result: passed
