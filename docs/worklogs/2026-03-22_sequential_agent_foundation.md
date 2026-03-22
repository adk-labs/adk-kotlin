# 2026-03-22 Sequential Agent Foundation

## Planned Work

- Add an official-style sequential agent surface to `adk-kotlin`.
- Extend the DSL so apps can declare `sequentialAgent(...)` in addition to
  `agent(...)`.
- Teach `Runner` to execute sequential shell agents by running sub-agents in
  order and returning the final outcome from the sequence.
- Add tests covering sequential orchestration and mixed sequential/llm trees.

## Why This Work

- The current Kotlin runtime only supports direct LLM agents plus transfer.
- Official ADK exposes composite agents as first-class runtime concepts, and
  `SequentialAgent` is the lowest-risk orchestration shell to add first.
- This creates the agent-execution abstraction needed for later `LoopAgent`,
  `ParallelAgent`, and planners without forcing a larger rewrite now.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Agent.kt`
- `src/main/kotlin/dev/adk/kotlin/Dsl.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/test/kotlin/dev/adk/kotlin/AgentDslTest.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added `AgentExecutionKind` plus `SequentialAgent` typealias support so
  `adk-kotlin` now has a first official-style composite agent shell.
- Added `sequentialAgent(...)` and `SequentialAgentDsl`, including nested use
  from the existing agent DSL through `subAgents(...)` and
  `sequentialAgent(...)`.
- Extended validation so sequential shell agents do not accept LLM-only fields
  like `model`, `instruction`, `tools`, or `outputSchema`.
- Refactored `Runner` around an agent-execution abstraction so it can execute
  shell agents recursively instead of assuming every agent is directly backed
  by a model call.
- Implemented sequential execution semantics by running sub-agents in order and
  returning the final outcome of the sequence while preserving the same event,
  plugin, storage, and transcript surfaces.
- Updated transfer target resolution so transfer continues to target LLM agents
  only, which avoids invalid handoff into shell agents during this foundation
  phase.
- Added tests covering:
  sequential DSL construction,
  and ordered sequential execution through the runner.
- Updated `README.md` to document sequential shell-agent orchestration.

## Why These Changes

- This establishes the runtime seam needed for later `LoopAgent`,
  `ParallelAgent`, and planner-driven orchestration without forcing a larger
  agent-type rewrite first.
- Keeping sequential agents on the same underlying Kotlin model preserves most
  of the current public surface while still aligning the API name and runtime
  behavior with official ADK concepts.
- Refactoring the runner now reduces the amount of rework required when the
  remaining orchestration shells are added.

## Verification

- `./gradlew test`
