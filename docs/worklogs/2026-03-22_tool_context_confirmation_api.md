# 2026-03-22 Tool Context Confirmation API

## Planned Work

- Add official-style `ToolContext.requestConfirmation(...)` overloads so tools
  can request approval directly during execution.
- Persist tool-requested confirmation metadata on emitted tool events through
  `EventActions.requestedToolConfirmations`.
- Add tests covering tool-driven confirmation requests.

## Why This Work

- Official ADK exposes confirmation not only as a runner policy but also as a
  first-class `ToolContext` API, and Kotlin should match that shape.
- This is a small but high-signal parity fix because it affects public API
  names and how tool authors write portable code.
- The current runner already carries function-call ids and confirmation event
  metadata, so the remaining work is narrowly scoped.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Tool.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added official-style `ToolContext.requestConfirmation(...)` overloads.
- Added tool-event propagation for `requestedToolConfirmations` emitted by the
  tool itself during execution.
- Added a runner test covering tool-driven confirmation requests.
- Updated `README.md` with a tool-authored confirmation example.

## Why The Final Shape

- Tool authors should not have to rely only on runner-level policy flags when
  the official SDK exposes direct confirmation APIs on the execution context.
- Reusing the existing per-call event metadata keeps confirmation requests
  visible to UIs and orchestration layers without adding another result type.

## Verification Results

- `./gradlew test` passed successfully.
- Work unit is ready for commit and push.
