# 2026-03-22 Controlled IO Alignment

## Planned Work

- Add official-style `includeContents` control to `LlmAgent`.
- Add official-style `outputKey` support so agent outputs can be written back to
  session state.
- Teach request assembly to respect `includeContents = none` by sending only the
  current user turn instead of the full transcript.
- Teach `Runner` to persist final agent output into `session.state[outputKey]`.
- Add tests covering transcript filtering and output-state persistence.

## Why This Work

- Planner support is more useful when agents can also control how much prior
  transcript they see and where final outputs are persisted.
- These are part of the official `LlmAgent` surface in Python and materially
  affect orchestration semantics between agents.
- The Kotlin runtime already has the right seams: prompt assembly owns
  transcript selection and the runner owns final state persistence.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Agent.kt`
- `src/main/kotlin/dev/adk/kotlin/Dsl.kt`
- `src/main/kotlin/dev/adk/kotlin/PromptAssembler.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/test/kotlin/dev/adk/kotlin/AgentDslTest.kt`
- `src/test/kotlin/dev/adk/kotlin/PromptAssemblerTest.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added `includeContents` and `outputKey` to `LlmAgent` as official-style
  controlled I/O settings.
- Extended the DSL so Kotlin agents can declare transcript inclusion behavior
  and output-state persistence directly.
- Taught `PromptAssembler` to respect `includeContents = NONE` by sending only
  the current user batch to the model.
- Taught `Runner` to write final agent outputs into
  `session.state[outputKey]` before emitting the final event.
- Added tests covering DSL wiring, transcript filtering, and output-key
  persistence.
- Updated `README.md` to document controlled I/O semantics.

## Why The Final Shape

- Transcript inclusion belongs in prompt assembly, not in the model adapter,
  because it changes the semantic contents of the request rather than the
  transport.
- `outputKey` belongs in the runner because the final output is only known once
  the runtime resolves structured output, planner cleanup, and transfer/loop
  behavior.
- Restricting these fields to `LlmAgent` keeps workflow shells aligned with the
  official model that orchestration agents do not carry LLM-specific I/O
  behavior.

## Verification Results

- `./gradlew test` passed successfully.
- Work unit is ready for commit and push.
