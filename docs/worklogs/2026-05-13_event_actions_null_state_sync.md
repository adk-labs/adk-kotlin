# 2026-05-13 Event Actions Null State Sync

## Context

The latest `adk-java` reference changed `EventActions.stateDelta` and
`sessions.State` handling so null values are converted to the official removed
state sentinel instead of being stored as null. `adk-kotlin` already had
`STATE_REMOVED`, but direct `EventActions(stateDelta = mapOf(key to null))`
still preserved null in event metadata.

## Work

- Normalize `EventActions.stateDelta` values so null means
  `STATE_REMOVED`.
- Preserve the existing `EventActions` constructor and `copy(...)` call surface
  while moving to an explicit class implementation.
- Keep merge behavior aligned with existing deep merge semantics while applying
  the official null-to-removed conversion.
- Add event model tests for direct construction and merged null state deltas.

## Reason

Official ADK treats null state delta values as deletions. Matching that behavior
prevents Kotlin event streams, recordings, and external callers from seeing a
different deletion representation than `adk-java`.
