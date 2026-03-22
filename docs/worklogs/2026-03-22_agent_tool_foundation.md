# 2026-03-22 Agent Tool Foundation

## Planned Work

- Add official-style `inputSchema` to `LlmAgent`.
- Add `AgentTool` so an agent can be exposed as a tool.
- Derive the agent-tool input schema from the wrapped agent, following the
  official "first sub-agent for input, last sub-agent for output" pattern.
- Execute wrapped agents through an in-memory child runner and forward state
  deltas back to the parent tool context.
- Add tests covering DSL wiring, tool declaration shape, and agent-tool
  execution.

## Why This Work

- `AgentTool` is one of the most visible official ADK tool abstractions and
  sits directly on top of the runtime pieces already built in Kotlin.
- `inputSchema` becomes genuinely useful once agents can be surfaced as tools.
- This closes a meaningful gap without needing to build the larger code
  executor or CLI layers first.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Agent.kt`
- `src/main/kotlin/dev/adk/kotlin/Dsl.kt`
- `src/main/kotlin/dev/adk/kotlin/Tool.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/main/kotlin/dev/adk/kotlin/OutputSchema.kt`
- `src/test/kotlin/dev/adk/kotlin/AgentDslTest.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added official-style `inputSchema` to `LlmAgent`.
- Added `AgentTool` and `agentTool(...)` so agents can be exposed directly as
  tools.
- Derived wrapped tool declarations from the wrapped agent's `inputSchema`, or
  from the first sub-agent for workflow shells.
- Added child-runner execution for wrapped agents using an isolated in-memory
  session and the same model/runtime services.
- Forwarded child state deltas back into the parent tool context so wrapped
  agents can participate in downstream orchestration.
- Added tests covering DSL wiring, schema-derived tool declarations, JSON input
  serialization, and parent-state forwarding.
- Updated `README.md` to document agent-as-tool support.

## Why The Final Shape

- `AgentTool` is most useful when it reuses the existing runner instead of
  inventing a parallel execution path, because that preserves transfer,
  planner, prompt, and output semantics automatically.
- Reusing `ToolSchema` for `inputSchema` keeps the public surface compact while
  still aligning with the official agent-tool declaration shape.
- Forwarding only state deltas from the child run matches the official pattern
  more closely than sharing the whole session object directly.

## Verification Results

- `./gradlew test` passed successfully.
- Work unit is ready for commit and push.
