# 2026-03-22 Storage And Memory Foundation

## Planned Work

- Add persistent file-backed implementations for sessions and artifacts.
- Extend storage interfaces toward a more official ADK-style capability set
  with list/delete/version operations.
- Add a memory service foundation with in-memory ingestion and search.
- Wire memory lookup into `ToolContext` so tools can query user memory.
- Add tests for persistent storage behavior and memory search behavior.

## Why This Work

- `adk-kotlin` currently stops at in-memory session/artifact state, which is
  too narrow for the official ADK runtime shape.
- The official SDKs treat session management, artifact persistence, and memory
  retrieval as first-class pluggable services.
- Later work on CLI/dev server/integrations needs persistent backends and a
  memory abstraction instead of runner-local maps.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/SessionStore.kt`
- `src/main/kotlin/dev/adk/kotlin/InMemorySessionStore.kt`
- `src/main/kotlin/dev/adk/kotlin/ArtifactService.kt`
- `src/main/kotlin/dev/adk/kotlin/Tool.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- New file-backed storage and memory model files
- New tests
- `README.md`

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added `FileSessionStore` with persistent session save/load/list/delete
  behavior so Kotlin now has a file-backed session backend in addition to the
  existing in-memory store.
- Extended `SessionStore` with `list(...)` and `delete(...)`, and implemented
  both operations for the in-memory and file-backed stores.
- Added `FileArtifactService` with artifact version persistence, listing, and
  deletion, plus widened `ArtifactService` with `listVersions(...)` and
  `deleteArtifact(...)`.
- Updated in-memory artifact behavior so session-scoped listing includes both
  user-scoped and session-scoped artifact keys, matching official ADK semantics
  more closely.
- Added `MemoryService`, `MemoryEntry`, `SearchMemoryResponse`, and
  `InMemoryMemoryService` so Kotlin now has a first-class memory abstraction
  instead of only session state.
- Wired `ToolContext.searchMemory(query)` and passed `MemoryService` through the
  runner so tools can retrieve user memory during execution.
- Marked session/event/message/artifact models as serializable so file-backed
  persistence can store the same runtime objects the runner already uses.
- Added tests covering:
  file session round trips,
  artifact version persistence and deletion,
  and tool-level memory search through the runner.

## Why These Changes

- This moves `adk-kotlin` from purely process-local runtime state toward the
  official ADK service model where sessions, artifacts, and memory are distinct
  pluggable backends.
- File-backed implementations give Kotlin a practical persistent baseline
  without waiting for database or cloud-specific modules.
- Memory search exposed through `ToolContext` is the minimal bridge needed for
  later built-in memory tools and retrieval-oriented workflows.

## Verification

- `./gradlew test`
