# 2026-04-27 event model official parity

## What I worked on

- Reviewed the current Kotlin event model against `adk-python` and `adk-java`.
- Identified that the runtime already emits events, but the event contract is narrower than official ADK.
- Selected event metadata/action parity as the next implementation unit because plugins, sessions, web UI, recordings, streaming, and summarization all depend on the same event shape.

## Why this work was needed

- Official ADK events are not just transcript messages; they carry action deltas, long-running tool IDs, errors, streaming flags, model metadata, compaction metadata, and UI widget hints.
- Kotlin currently stores only a subset, so downstream modules cannot faithfully represent official runtime behavior.
- Expanding the event contract now reduces follow-on gaps in sessions, plugins, web payloads, and recordings.

## Planned result for this work unit

- Add official event/action fields that are missing from Kotlin.
- Keep the API Kotlin-first while preserving official field names and semantics.
- Update web and recording payloads so exported event data does not lose the new fields.
- Add regression tests for event action merge/deletion metadata and final-response semantics.

## Result

- Added official event metadata fields for long-running tool IDs, model version, errors, finish reason, usage metadata, logprobs, interruption, grounding/custom metadata, and transcriptions.
- Added official action metadata for deleted artifacts, compaction, rewind target, renderable UI widgets, and object-valued state/agent state.
- Added Kotlin-first `EventActions.merge(...)` and `removeStateByKey(...)` helpers aligned with the official event action contract.
- Updated web session/event payloads and CLI recording schema so these fields survive export.
- Added event model regression tests for action merging, removed-state sentinel, final-response semantics, and metadata preservation.

## Verification

- Ran `./gradlew test`.
