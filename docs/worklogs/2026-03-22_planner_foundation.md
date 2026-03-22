# 2026-03-22 Planner Foundation

## Planned Work

- Add official-style planner surfaces: `BasePlanner`, `BuiltInPlanner`, and
  `PlanReActPlanner`.
- Extend `LlmAgent` and the DSL so agents can declare a planner without naming
  drift from the official SDKs.
- Add request-time planner processing for planning instruction injection and
  built-in thinking config override.
- Add response-time planner post-processing for Plan-Re-Act style final-answer
  extraction.
- Add tests covering planner DSL wiring, request mutation, and response
  handling.

## Why This Work

- After workflow shells, planner support is the next missing control layer
  between raw prompting and full provider integrations.
- The official Python ADK exposes both model-native thinking planners and
  natural-language planning planners; Kotlin needs the same public affordances
  even if the first runtime pass is intentionally compact.
- Adding planner seams now keeps future provider modules from hardcoding
  planning behavior inside model adapters.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Agent.kt`
- `src/main/kotlin/dev/adk/kotlin/Dsl.kt`
- `src/main/kotlin/dev/adk/kotlin/GenerateContentConfig.kt`
- `src/main/kotlin/dev/adk/kotlin/PromptAssembler.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/main/kotlin/dev/adk/kotlin/Planner.kt`
- `src/test/kotlin/dev/adk/kotlin/AgentDslTest.kt`
- `src/test/kotlin/dev/adk/kotlin/PromptAssemblerTest.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added official-style planner surfaces: `BasePlanner`, `BuiltInPlanner`, and
  `PlanReActPlanner`.
- Added `ThinkingConfig` to `GenerateContentConfig` so planner-driven model
  thinking settings can flow through the same request config surface.
- Extended `LlmAgent` and the DSL with a first-class `planner` field while
  preserving workflow-agent restrictions.
- Taught `Runner` to let planners mutate outgoing requests and post-process
  final responses.
- Implemented `BuiltInPlanner` request-time thinking-config override.
- Implemented `PlanReActPlanner` planning-instruction injection and
  `/*FINAL_ANSWER*/` extraction for final text responses.
- Added tests covering planner wiring, config override precedence, and
  plan/react response cleanup.
- Updated `README.md` to document planner support.

## Why The Final Shape

- Splitting planner behavior into request mutation and response post-processing
  matches the official ADK model closely enough to keep future provider modules
  decoupled from planning logic.
- `BuiltInPlanner` uses the same config surface as base generation settings,
  which keeps the Kotlin API small while still preserving the official
  precedence rule that planner thinking config overrides agent config.
- `PlanReActPlanner` currently focuses on instruction injection and final-answer
  extraction because the Kotlin core runtime does not yet expose mixed
  text-plus-tool-call responses; this is the cleanest compatible foundation.

## Verification Results

- `./gradlew test` passed successfully.
- Work unit is ready for commit and push.
