# 2026-05-16 Events Compaction Config

## Context

The latest `adk-python` split app configuration into `_configs.py` and added
application-level `EventsCompactionConfig` plus compaction helpers. The Kotlin
event model already had `EventCompaction`, but there was no app-level config,
summarizer contract, or post-run compaction hook.

## Work

- Add `BaseEventsSummarizer`, `EventsCompactionConfig`, and
  `ResumabilityConfig`.
- Add sliding-window and token-threshold event compaction helpers.
- Add `eventsCompactionConfig` and `resumabilityConfig` to `AdkApp`.
- Add Kotlin DSL support for `eventsCompactionConfig(...)` and
  `resumabilityConfig(...)`.
- Hook post-invocation compaction into `Runner` after the normal run events are
  saved, matching the official behavior that compaction happens after agent
  events are yielded.
- Add tests for config validation, sliding-window selection, overlap behavior,
  token-threshold retention, and Runner persistence.
- Refresh the official gap matrix for app/runtime compaction status.

## Reason

Official ADK now treats event compaction as an app-level runtime capability.
Adding the config and post-run hook closes the most visible new `adk-python`
app/runtime gap while keeping summarization provider-agnostic and Kotlin-first.
