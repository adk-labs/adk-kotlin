# 2026-03-22 Parallel Agent Foundation

## Planned Work

- Add an official-style `ParallelAgent` surface to `adk-kotlin`.
- Extend the DSL with `parallelAgent(...)` and nested workflow composition.
- Teach `Runner` to execute parallel shell agents in isolated branch-local
  contexts and merge the resulting event streams by completion order.
- Preserve official workflow-agent constraints so `ParallelAgent` does not
  expose `model`, `instruction`, or `tools`.
- Add tests covering DSL construction, branch isolation, and merged result
  ordering.

## Why This Work

- `SequentialAgent` and `LoopAgent` cover serial orchestration, but official
  ADK also exposes `ParallelAgent` for fan-out workflows.
- The Python and Java references both treat `ParallelAgent` as isolated branch
  execution with a merged event stream, so Kotlin needs the same shell-agent
  concept to stay semantically aligned.
- This is the smallest orchestration step after loop support and is a direct
  prerequisite for richer planner/evaluator workflows.

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

- Added `ParallelAgent` as an official-style workflow shell alongside
  `SequentialAgent` and `LoopAgent`.
- Extended the DSL with `parallelAgent(...)` and nested workflow composition
  from LLM, sequential, loop, and parallel shells.
- Taught `Runner` to execute sub-agents in parallel using branch-local copies of
  state, transcript, events, and tool execution logs.
- Merged branch outputs back into the parent run in completion order, matching
  the official `ParallelAgent` expectation that faster branches surface events
  first.
- Kept branch state isolated from sibling and parent state so fan-out execution
  does not leak mutable session state across branches.
- Added DSL and runner tests covering official naming, isolated branch state,
  and completion-order merge behavior.
- Updated `README.md` to document parallel orchestration semantics.

## Why The Final Shape

- The Java and Python references both frame `ParallelAgent` as isolated branch
  execution with a merged event stream, so Kotlin now mirrors that semantic
  contract instead of treating parallelism as shared mutable orchestration.
- Branch-local copies are the safest fit for the current Kotlin runner because
  they preserve existing event/tool semantics without introducing cross-branch
  state races.
- Merging by branch completion order is the highest-signal foundation step
  before a future streaming/event scheduler adds finer-grained interleaving.

## Verification Results

- `./gradlew test` passed successfully.
- Work unit is ready for commit and push.
