# 2026-03-22 Multimodal Artifact Result Foundation

## Planned Work

- Extend `Artifact`, `Message`, and `ToolOutput` so attachment-like data can
  flow through artifact storage, tool execution, and model requests.
- Update `load_artifacts` to return real attachments alongside its textual
  status/result content.
- Preserve attachment metadata such as MIME type for uploaded files stored by
  `SaveFilesAsArtifactsPlugin`.
- Add tests covering attachment persistence and attachment-bearing
  `load_artifacts` results.

## Why This Work

- The current upload foundation persists files, but later turns still only see
  text placeholders or flattened artifact text.
- Official ADK supports multimodal artifact handoff patterns where tool results
  can re-enter the model context as non-text content.
- This is the minimal data-model change needed before provider-native
  multimodal request handling or multimodal tool-result plugins.

## Files Changed

- `src/main/kotlin/dev/adk/kotlin/ArtifactService.kt`
- `src/main/kotlin/dev/adk/kotlin/Messages.kt`
- `src/main/kotlin/dev/adk/kotlin/Tool.kt`
- `src/main/kotlin/dev/adk/kotlin/BuiltInTools.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/main/kotlin/dev/adk/kotlin/SaveFilesAsArtifactsPlugin.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `src/test/kotlin/dev/adk/kotlin/StorageAndMemoryTest.kt`
- `README.md`

## Completed Work

- Extended `Artifact` with `mimeType` so uploaded or generated artifacts retain
  content-type metadata.
- Extended `Message` and `ToolOutput` with `attachments`, allowing
  attachment-bearing tool results to persist into transcript and later model
  requests.
- Updated runner tool-event emission so `ToolMessage` and `toolExecutions`
  preserve attachment-bearing outputs.
- Updated `SaveFilesAsArtifactsPlugin` to persist uploaded MIME type metadata.
- Updated `load_artifacts` to return both textual artifact summaries and real
  `MessageAttachment` entries for loaded artifacts.
- Added tests for attachment persistence through artifact services and
  attachment-bearing `load_artifacts` results.

## Verification Result

- Run `./gradlew test`
- Result: passed
