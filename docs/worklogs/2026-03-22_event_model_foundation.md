# 2026-03-22 Event Model Foundation

## Planned Work

- Add first-class `Event` and `EventActions` models aligned with the official
  ADK runtime shape.
- Extend sessions and run results so event history is stored and returned as a
  first-class artifact of execution, not only as transcript strings.
- Teach `Runner` to emit events for user input, tool results, agent transfer,
  and final responses.
- Capture official-style action deltas for state mutation, artifact saves,
  transfer targets, and end-of-agent completion.
- Add tests for event emission, action deltas, and persisted session events.

## Why This Work

- `adk-python` and `adk-java` both treat events as the primary runtime record.
  `adk-kotlin` currently only preserves a lightweight transcript, which makes
  later work on plugins, streaming, checkpoints, and richer tools harder.
- The next layers in the gap matrix, especially plugins and session backends,
  need an event surface to attach behavior to.
- Adding this now keeps Kotlin aligned on official naming and runtime semantics
  before broader infrastructure is layered on top.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Agent.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/main/kotlin/dev/adk/kotlin/Session.kt`
- `src/main/kotlin/dev/adk/kotlin/Tool.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`
- New event model file(s)

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added first-class `Event` and `EventActions` models with official-style
  fields for `stateDelta`, `artifactDelta`, `transferToAgent`, and
  `endOfAgent`.
- Extended `AgentSession` and `RunResult` so events are persisted and returned
  alongside the existing transcript surface.
- Updated `AdkApp` with branch resolution so emitted events now carry the same
  agent-tree branch semantics that official ADK uses for nested agents.
- Updated `ToolContext` to record artifact version deltas, and updated
  `Runner` to emit events for:
  user input,
  tool execution,
  agent transfer,
  and final agent completion.
- Added state-delta computation around tool execution so direct `state[...] =`
  mutations are reflected in event actions without forcing a Kotlin-only API.
- Added runner tests covering:
  persisted session events,
  shared invocation ids,
  transfer actions,
  tool state deltas,
  artifact deltas,
  and final completion events.
- Updated `README.md` to document the new event surface and how callers can
  read `result.events`.

## Why These Changes

- This moves `adk-kotlin` closer to the official runtime contract where events
  are the primary execution record rather than an afterthought.
- Plugins, streaming, and persistent session work now have a stable event
  substrate to build on instead of having to infer behavior from transcript
  strings.
- State diffing around tool execution preserves Kotlin ergonomics while still
  exposing the official action-delta semantics expected by downstream runtime
  features.

## Verification

- `./gradlew test`
