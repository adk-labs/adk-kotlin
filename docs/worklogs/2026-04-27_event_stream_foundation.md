# 2026-04-27 event stream foundation

## What I worked on

- Reviewed official `adk-java` `EventStream`.
- Selected a small Kotlin-first `EventStream` foundation as the next event-runtime gap after expanding the event model.

## Why this work was needed

- Official ADK exposes an event stream abstraction in addition to stored session events.
- Kotlin already has coroutine `Flow<Event>` from `Runner.stream(...)`, but no official-named event stream type for synchronous/lazy event consumers or compatibility surfaces.
- Adding the type now gives sessions, conformance, CLI, and future streaming adapters a stable event-stream API.

## Planned result for this work unit

- Add an `EventStream` class with official lazy iteration semantics.
- Provide a convenience constructor for existing event collections.
- Add tests for lazy supplier behavior and end-of-stream semantics.

## Result

- Added `EventStream` as an official-named lazy `Iterable<Event>` abstraction.
- Added `EventStream.from(...)` for adapting existing event collections.
- Updated the gap matrix to mark event stream foundation as present and move the next event-runtime gap to compaction/summarizer integration.
- Added tests for lazy supplier calls, cached `hasNext()` behavior, end-of-stream exceptions, and collection adaptation.

## Verification

- Ran `./gradlew test`.
