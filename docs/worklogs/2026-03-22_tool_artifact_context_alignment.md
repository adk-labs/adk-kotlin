# 2026-03-22 Tool Artifact Context Alignment

## Planned Work

- Expose artifact operations through `ToolContext` so Kotlin tools can save and
  load artifacts through the same runtime service used by instruction
  interpolation.
- Extend the artifact service with artifact listing support for session-scoped
  tools.
- Wire the runner so tool executions and prompt assembly share the same
  artifact service instance during a run.
- Add tests that prove a tool can create an artifact and a later model turn can
  read it through `{artifact.filename}` interpolation.

## Why This Work

- Artifact-aware instructions are now supported, but tools still cannot write to
  the artifact service, so the official ADK loop between tools and later prompt
  resolution is incomplete.
- Official ADK exposes artifact operations through runtime callback/context
  objects. Kotlin needs an equivalent capability on `ToolContext` to avoid
  forcing direct runner/service plumbing into every tool implementation.
- Without a test that spans tool execution and prompt assembly, artifact support
  remains only partially integrated.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/ArtifactService.kt`
- `src/main/kotlin/dev/adk/kotlin/Tool.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Extended `ArtifactService` with artifact listing so runtime code can inspect
  session-scoped artifacts in addition to saving and loading them.
- Added `saveArtifact`, `loadArtifact`, and `listArtifacts` to `ToolContext`,
  giving Kotlin tools the same core artifact lifecycle access that official ADK
  callback/context objects expose.
- Wired `Runner` so tool execution receives the same artifact service instance
  used later by prompt assembly, which closes the loop between tool side effects
  and later `{artifact.filename}` interpolation.
- Added a runner integration test that proves:
  a tool saves an artifact in one turn,
  the next model turn sees the artifact content inside the system instruction,
  and the artifact is persisted in the shared runner service.
- Updated `README.md` with a Kotlin tool example that saves an artifact through
  `ToolContext`.

## Why These Changes

- Artifact interpolation is only fully useful if tools can populate the same
  storage that prompt assembly reads from.
- Putting artifact operations on `ToolContext` keeps the Kotlin-first API small
  and ergonomic while preserving the official ADK runtime semantics.
- Testing the full tool-to-prompt path protects against regressions that unit
  tests for prompt assembly or artifact services alone would miss.

## Verification

- `./gradlew test`
