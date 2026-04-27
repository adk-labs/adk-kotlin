# 2026-04-27 gap matrix refresh

## What I worked on

- Reviewed the current Kotlin source tree after recent provider, prompt, plugin, memory, artifact, code executor, and event work.
- Compared the current module surface against `adk-python` and `adk-java`.
- Planned a refresh of `docs/adk_official_gap_matrix.md` because it still marked several now-present Kotlin modules as missing.

## Why this work was needed

- The gap matrix is used to choose implementation order.
- If it says implemented modules are still missing, follow-up work will target the wrong gaps.
- Keeping the matrix current makes the next implementation units concrete: real provider transports, event streaming/live runtime, persistent service depth, and ecosystem tools.

## Planned result for this work unit

- Update source counts and current Kotlin status.
- Reclassify implemented-but-incomplete areas from `Missing` to `Partial`.
- Keep remaining missing modules explicit so the next implementation work is not ambiguous.

## Result

- Updated source counts to the current Kotlin, Python, and Java reference trees.
- Reclassified current Kotlin implementations for models, plugins, events, memory, auth, code executors, CLI/web, planners, and composite agents.
- Updated the strongest-current-capabilities section to reflect recent prompt, provider, plugin, memory/artifact, and event work.
- Reordered the next implementation list around the real remaining gaps: provider transports, event stream/compaction, tool ecosystems, persistent services, live multimodal runtime, and ecosystem surfaces.

## Verification

- Documentation-only change; implementation tests were already run in the preceding event model work unit.
