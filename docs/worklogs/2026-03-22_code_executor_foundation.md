# 2026-03-22 Code Executor Foundation

## Planned Work

- Add official-style `BaseCodeExecutor`, `BuiltInCodeExecutor`, and
  `UnsafeLocalCodeExecutor` surfaces.
- Extend `LlmAgent` and the DSL with a first-class `codeExecutor` field.
- Teach the runner to detect fenced code blocks in model final responses,
  execute them through non-built-in code executors, and continue the agent loop
  with the execution result in transcript.
- Emit execution results as runtime events so code execution becomes visible in
  the same event/tool trace as other runtime actions.
- Add tests covering DSL wiring and local code execution retry loop behavior.

## Why This Work

- `code_executor` is one of the main advanced execution surfaces in the
  official ADK and fits naturally after planners and agent tools.
- Kotlin already has the core seams needed to support this: mutable transcript,
  events, retries, and generation config.
- A local executor foundation keeps future provider-specific executors and
  built-in model executors from needing a brand-new runtime path.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Agent.kt`
- `src/main/kotlin/dev/adk/kotlin/Dsl.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/main/kotlin/dev/adk/kotlin/CodeExecutor.kt`
- `src/test/kotlin/dev/adk/kotlin/AgentDslTest.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added `BaseCodeExecutor`, `BuiltInCodeExecutor`, `UnsafeLocalCodeExecutor`,
  and the supporting `CodeExecutionInput` / `CodeExecutionResult` /
  `CodeExecutionFile` models.
- Extended `LlmAgent` and the Kotlin DSL with a first-class `codeExecutor`
  field while keeping workflow shell agents invalid for that property.
- Taught `Runner` to preprocess built-in code execution requests, detect fenced
  code blocks in final model messages, execute them through non-built-in code
  executors, and continue the agent loop with the execution result appended to
  transcript and events.
- Persisted code-executor output files through the existing artifact service
  and forwarded executor state deltas through `EventActions`.
- Added tests covering DSL storage, unsafe local execution success/failure
  loops, and built-in request markers.
- Updated `README.md` to document the new code execution surface.

## Why The Final Shape

- Reusing the existing runner loop keeps code execution aligned with official
  ADK flow semantics instead of creating a separate execution subsystem.
- Treating built-in executors as request preprocessors and local executors as
  response post-processors matches the reference split closely enough for the
  current Kotlin runtime.
- Keeping execution results inside normal transcript/events means planners,
  plugins, and downstream agents can observe the same state without new APIs.

## Verification Results

- `./gradlew test` passed successfully.
- Work unit is ready for commit and push.
