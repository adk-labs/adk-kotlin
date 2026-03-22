# 2026-03-22 Official API Naming Alignment

## Planned Work

- Add official ADK naming aliases so Kotlin callers can use `App`, `Agent`,
  and `Context` terminology directly.
- Add official-style entry points such as `app(...)` and `agent(...)` while
  keeping existing Kotlin-first helpers for compatibility.
- Add plural DSL methods like `tools(...)` and `subAgents(...)`, plus
  `rootAgent(agent)` assignment, to better match official ADK field and builder
  names.
- Expose `state` on the tool context so tools can mutate session state through
  the same name used in the official libraries.

## Why This Work

- The user requirement is that public function names and API names should align
  across `adk-python`, `adk-java`, and Kotlin wherever possible.
- The current Kotlin API is functional, but it still exposes Kotlin-specific
  names like `adkApp`, `subAgent`, and `remember/recall` as the primary public
  surface.
- Adding compatibility names is the lowest-risk way to converge naming without
  breaking the current Kotlin-first DSL or the work completed so far.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Dsl.kt`
- `src/main/kotlin/dev/adk/kotlin/Tool.kt`
- `src/test/kotlin/dev/adk/kotlin/AgentDslTest.kt`
- `README.md`

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added public naming aliases so Kotlin callers can use `App`, `Agent`, and
  `Context` directly instead of only `AdkApp`, `LlmAgent`, and `ToolContext`.
- Added official-style entry points `app(...)` and `agent(...)` while keeping
  `adkApp(...)` and `llmAgent(...)` as compatibility helpers.
- Added `rootAgent(agent)`, `tools(...)`, and `subAgents(...)` to the DSL so
  callers can use field and builder names that line up more closely with the
  official ADK surface.
- Exposed `state` on `Context` so tool implementations can mutate session state
  via the same primary API name used in the official libraries, while still
  keeping `remember` and `recall` for backward compatibility.
- Added test coverage for the official naming path and updated the README to
  show the aligned surface explicitly.

## Why These Changes

- This keeps the Kotlin-first SDK ergonomic without forcing naming drift away
  from the official libraries.
- Compatibility aliases reduce migration cost and let shared examples,
  documentation, and mental models transfer more directly across Python, Java,
  and Kotlin.
- Adding aligned names before the surface grows further is cheaper than trying
  to rename a larger public API later.

## Verification

- `./gradlew test`
