# 2026-03-22 Save Files As Artifacts Plugin Foundation

## Planned Work

- Extend `UserMessage` with a Kotlin-friendly attachment surface so uploaded
  files can enter the runtime.
- Add a `Runner.run(...)` overload that accepts `UserMessage` directly instead
  of only raw text.
- Add a packaged `SaveFilesAsArtifactsPlugin` that persists uploaded files into
  the artifact service and rewrites the visible user message with artifact
  placeholders.
- Add tests covering session-scoped and user-scoped artifact persistence plus
  user-event artifact deltas.

## Why This Work

- Official ADK has a packaged `save_files_as_artifacts_plugin`, but the current
  Kotlin message model is still text-only.
- Without an attachment surface, artifact upload flows and web-style file
  handoff cannot be represented at the SDK level.
- This creates the minimum multimodal/file-upload foundation needed for later
  CLI or web-server parity.

## Files Changed

- `src/main/kotlin/dev/adk/kotlin/Messages.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/main/kotlin/dev/adk/kotlin/SaveFilesAsArtifactsPlugin.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Completed Work

- Extended `UserMessage` with `attachments` and user-event `artifactDelta`
  metadata while keeping text-only usage intact.
- Added a `Runner.run(...)` overload that accepts a full `UserMessage`, so
  attachment-aware inputs can enter the runtime directly.
- Added packaged `SaveFilesAsArtifactsPlugin` that persists uploaded files to
  the artifact service, rewrites the visible user message with uploaded-artifact
  placeholders, and supports both session-scoped and user-scoped persistence.
- Added tests for session uploads, user-scoped uploads, and emitted user-event
  artifact deltas.

## Verification Result

- Run `./gradlew test`
- Result: passed
