# 2026-03-22 Authenticated Tool And Toolset Foundation

## Planned Work

- Add official-style `BaseAuthenticatedTool` so tools can request or consume
  credentials without reimplementing auth boilerplate.
- Add a minimal `BaseToolset` / `Toolset` surface with tool-name prefix support
  and runtime expansion into agent tool declarations.
- Extend the DSL and runner so agents can declare toolsets alongside direct
  tools.
- Add tests covering authenticated tool behavior and toolset expansion.

## Why This Work

- The auth runtime added in the previous unit is only half useful unless tool
  authors have an official-shaped abstraction that consumes it.
- Toolsets are a major organizing primitive in the official SDKs and unlock a
  cleaner path toward MCP/OpenAPI/Google API modules later.
- This work keeps the current Kotlin runtime small while aligning important
  public API names and authoring patterns.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Tool.kt`
- `src/main/kotlin/dev/adk/kotlin/Agent.kt`
- `src/main/kotlin/dev/adk/kotlin/Dsl.kt`
- `src/main/kotlin/dev/adk/kotlin/PromptAssembler.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/main/kotlin/dev/adk/kotlin/Toolset.kt`
- `src/test/kotlin/dev/adk/kotlin/AgentDslTest.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added `BaseAuthenticatedTool` and `authenticatedTool(...)` so auth-aware
  tools can request credentials or run with loaded credentials using the same
  `ToolContext` auth helpers added earlier.
- Added `BaseToolset`, `Toolset`, `ReadonlyContext`, and tool-name prefix
  support.
- Extended `LlmAgent`, the DSL, `PromptAssembler`, and `Runner` so agents can
  declare toolsets and have them expanded into available tool declarations at
  runtime.
- Added tests covering authenticated tool auth-request behavior, successful
  credential-backed execution, and toolset expansion/prefixing.
- Updated `README.md` with authenticated-tool and toolset examples.

## Why The Final Shape

- The auth runtime becomes significantly more useful once tool authors can rely
  on an official-shaped authenticated base abstraction instead of hand-rolling
  credential lookup logic in every tool.
- Toolsets provide a stable path to larger official modules later without
  forcing Kotlin to ship those packages immediately.
- Expanding toolsets at request time keeps them context-aware and matches the
  official intent better than flattening them permanently at DSL build time.

## Verification Results

- `./gradlew test` passed successfully.
- Work unit is ready for commit and push.
