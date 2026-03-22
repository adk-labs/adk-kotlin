# 2026-03-22 Artifact Instruction Alignment

## Planned Work

- Add artifact-aware instruction interpolation support to the Kotlin runtime path,
  not only to the prompt assembler in isolation.
- Align the runner default with the official ADK behavior by wiring an in-memory
  artifact service by default.
- Add tests that verify `{artifact.*}` placeholders are resolved through the
  runner/prompt path and that optional placeholders degrade correctly.
- Update the top-level documentation so the Kotlin-first API still makes the
  official ADK prompt/runtime alignment explicit.

## Why This Work

- The previous unit aligned global instruction, identity, transfer, and output
  schema system prompts, but artifact placeholders were still a gap relative to
  the official ADK runtime.
- In the official libraries, instruction interpolation can read artifacts through
  the invocation context and the runner owns a default in-memory artifact
  service. Kotlin should preserve that default behavior even with a Kotlin-first
  surface.
- Without runner-level wiring and tests, `{artifact.filename}` support would be
  partial and easy to regress even if the prompt assembler can resolve it in
  isolation.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/test/kotlin/dev/adk/kotlin/PromptAssemblerTest.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`
- `src/main/kotlin/dev/adk/kotlin/ArtifactService.kt`

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added `ArtifactService`, `Artifact`, and `InMemoryArtifactService` so
  instruction interpolation can read artifacts through a dedicated runtime
  boundary instead of hard-coding prompt data into session state.
- Extended `PromptAssembler` to resolve `{artifact.filename}` and
  `{artifact.filename?}` placeholders using the artifact service while keeping
  the existing session-state placeholder semantics unchanged.
- Wired `Runner` to own a default `InMemoryArtifactService`, matching the
  official ADK default runner behavior more closely and ensuring artifact-aware
  instructions work in the normal execution path.
- Added prompt-level tests for artifact interpolation, optional artifact
  behavior, and failure when an artifact service is absent.
- Added runner-level coverage to verify the artifact-backed system instruction
  reaches the model request, not only the prompt assembler in isolation.
- Updated `README.md` to document the new default artifact service and the
  official-style `{artifact.filename}` instruction path.

## Why These Changes

- Artifact placeholders are part of the official ADK instruction semantics, so
  leaving them out would keep the Kotlin runtime materially behind the
  reference behavior even if the core DSL was already in place.
- Putting the default artifact service in `Runner` preserves the official
  runtime shape while still allowing Kotlin callers to replace the service when
  they need persistence or custom storage.
- Adding both prompt-level and runner-level tests reduces the chance that later
  runtime refactors silently break official prompt assembly semantics.

## Verification

- `./gradlew test`
