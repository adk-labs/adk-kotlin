# 2026-03-22 adk-java gap review

## What I worked on

- Re-reviewed `adk-kotlin` against the official `ref/adk-java` source tree.
- Split the comparison across both `ref/adk-java/core` and `ref/adk-java/dev`.
- Wrote a Java-specific gap assessment document at `docs/adk_java_gap_review.md`.

## Why this work was needed

- The previous broader official gap matrix was built when `adk-kotlin` had a much smaller surface.
- Since then, Kotlin gained runner, events, plugins, toolsets, CLI, dev server, recordings, and storage foundations.
- The question now is no longer "does Kotlin have any ADK shape at all" but "is the gap to the official JVM SDK actually closed".
- That requires a Java-specific review, because `adk-python` breadth and `adk-java` JVM parity are different baselines.

## Result

- The gap with `adk-java` is not closed.
- `adk-kotlin` now covers a meaningful subset of the official runtime shape, but it is still materially narrower than `adk-java/core` plus `adk-java/dev`.
- The biggest remaining gaps are concrete provider implementations, event compaction/summarization, telemetry, richer packaged tool modules, replay/conformance, and the full dev web stack.
