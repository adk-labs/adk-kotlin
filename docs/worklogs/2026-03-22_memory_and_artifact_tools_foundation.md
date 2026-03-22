# 2026-03-22 Memory And Artifact Tools Foundation

## Planned Work

- Add official-style built-in utility tools for `load_memory`,
  `preload_memory`, and `load_artifacts`.
- Extend the Kotlin `Tool` surface with request-preprocessing hooks so tools
  can influence the outgoing model request before generation.
- Wire runner request preprocessing so built-in tools can inject instructions or
  preload contextual data.
- Add tests covering memory preload/load behavior and artifact loading.

## Why This Work

- The current runtime already has memory and artifact services, but without the
  official utility tools those capabilities are harder to reach from agents.
- Request preprocessing is required for these tools because official ADK does
  not model them as plain call-and-return utilities only.
- This creates a clean bridge from the foundation work already done into more
  recognizable official tool modules.

## Files Changed

- `src/main/kotlin/dev/adk/kotlin/Tool.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/main/kotlin/dev/adk/kotlin/PromptAssembler.kt`
- `src/main/kotlin/dev/adk/kotlin/BuiltInTools.kt`
- `src/main/kotlin/dev/adk/kotlin/Toolset.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Completed Work

- Added `LoadMemoryTool`, `PreloadMemoryTool`, and `LoadArtifactsTool` with
  official-style runtime names and Kotlin-friendly top-level exports
  `loadMemory`, `preloadMemory`, and `loadArtifacts`.
- Extended `Tool` with `processLlmRequest(...)` so tools can modify outgoing
  requests before the model runs.
- Wired `Runner` to preprocess each request through resolved tools and
  toolsets before planner/code-executor handling.
- Hid preprocess-only tools such as `preload_memory` from the visible tool
  declaration list while still running their request hook.
- Let `ToolContext.loadArtifact(...)` fall back from session scope to user
  scope so `load_artifacts` can resolve artifacts returned by
  `listArtifacts()`.
- Updated the toolset prefix wrapper to delegate `processLlmRequest(...)` so
  preprocess-capable tools keep working when they come from a toolset.
- Added runner tests for `load_memory`, hidden `preload_memory`, and
  user-scoped `load_artifacts`.

## Verification Result

- Run `./gradlew test`
- Result: passed
