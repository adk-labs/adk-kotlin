# 2026-03-22 Loop Agent Foundation

## Planned Work

- Add an official-style `LoopAgent` surface to `adk-kotlin`.
- Extend the DSL with `loopAgent(...)` and loop iteration settings.
- Add internal `exit_loop` semantics so LLM sub-agents can stop loop
  orchestration explicitly.
- Teach `Runner` to execute loop shell agents while preserving the last
  meaningful sub-agent outcome.
- Add tests covering loop iteration and exit behavior.

## Why This Work

- `SequentialAgent` alone is not enough to represent official ADK workflow
  orchestration.
- `LoopAgent` is the next smallest runtime shell after sequential execution and
  unlocks retry/reflection-style workflows.
- The runner refactor from the previous unit created the exact seam needed to
  add looping without another full execution rewrite.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Agent.kt`
- `src/main/kotlin/dev/adk/kotlin/Dsl.kt`
- `src/main/kotlin/dev/adk/kotlin/PromptAssembler.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/test/kotlin/dev/adk/kotlin/AgentDslTest.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added `LoopAgent` as an official-style shell-agent surface without forking
  the core public agent type away from `LlmAgent`.
- Extended the DSL with `loopAgent(...)`, nested loop composition, and
  `maxIterations` wiring for loop shells.
- Added `exit_loop` prompt/tool semantics so loop sub-agents can stop
  orchestration explicitly instead of relying only on iteration exhaustion.
- Taught `Runner` to execute loop shells, preserve the last meaningful
  non-exit outcome, and stop when a sub-agent escalates through `exit_loop`.
- Added DSL and runner tests covering loop construction, prompt injection, and
  loop-exit behavior.
- Updated `README.md` to document loop orchestration support.

## Why The Final Shape

- Keeping `LoopAgent` as an execution-kind specialization of `LlmAgent`
  preserves API naming parity with the official SDKs while avoiding an early
  type explosion in Kotlin.
- Treating `exit_loop` as an internal framework tool matches the existing
  transfer/output-schema pattern: the model gets an official-style affordance,
  while the runner owns the runtime control semantics.
- Returning the last meaningful outcome instead of the raw `exit_loop` event
  makes loop orchestration usable for retry/reflection workflows where the loop
  ends after a prior worker response is already the correct final result.

## Verification Results

- `./gradlew test` passed successfully.
- Work unit is ready for commit and push.
