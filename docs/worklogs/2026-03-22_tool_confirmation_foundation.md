# 2026-03-22 Tool Confirmation Foundation

## Planned Work

- Add official-style tool confirmation types so runtime approval can exist as a
  first-class concept instead of a passive flag on `ToolDefinition`.
- Extend `Runner` with a confirmation handler seam and confirmation-result
  events before executing tools that declare `requiresConfirmation = true`.
- Surface enough metadata in events/results so downstream UIs or higher-level
  apps can inspect confirmation decisions.
- Add tests covering approved and denied tool execution paths.

## Why This Work

- `requiresConfirmation` already exists in the Kotlin tool definition surface,
  but today it is inert, which leaves a visible gap against the official ADK
  tool runtime.
- Confirmation is a core prerequisite for richer built-in tools, auth-backed
  tools, and eventually browser/dev-server style UX.
- This work stays inside the existing runner/tool architecture, so it advances
  official parity without forcing a separate product-layer module first.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Tool.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/main/kotlin/dev/adk/kotlin/Event.kt`
- `src/main/kotlin/dev/adk/kotlin/ToolConfirmation.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added official-style `ToolConfirmation` plus Kotlin runtime helpers
  `ToolConfirmationRequest` and `ToolConfirmationHandler`.
- Extended `EventActions` so confirmation requests and approval decisions are
  recorded on emitted runtime events.
- Added runner-level confirmation handling for tools that declare
  `requiresConfirmation = true`, including explicit approved/denied/pending
  behavior before tool execution.
- Exposed the resolved confirmation on `ToolContext` so approved tools can
  inspect confirmation payloads during execution.
- Added tests for both pending and approved confirmation flows.
- Updated `README.md` to document the confirmation surface.

## Why The Final Shape

- Confirmation is most useful as part of the core runner loop, because that is
  where the SDK can consistently emit event metadata and decide whether tool
  execution should proceed.
- A dedicated handler seam keeps Kotlin usable for headless/server-side
  integrations instead of assuming an interactive browser UI exists.
- Recording confirmation objects directly on events matches the official ADK
  data model more closely than burying approval state inside ad-hoc tool text.

## Verification Results

- `./gradlew test` passed successfully.
- Work unit is ready for commit and push.
