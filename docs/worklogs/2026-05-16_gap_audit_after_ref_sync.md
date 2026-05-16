# 2026-05-16 Gap Audit After Ref Sync

## Context

The official references were updated again on 2026-05-16. `adk-kotlin` has
already absorbed the immediately actionable changes for Chat Completions HTTP,
app-level event compaction, and skill runtime tools. This work unit verifies
whether the gap matrix still misses any newly introduced or previously
untracked official gaps.

## Work

- Recount and compare current `adk-python`, `adk-java`, and `adk-kotlin`
  source surfaces.
- Re-scan latest reference deltas for new modules or behavior that are not
  represented in `docs/adk_official_gap_matrix.md`.
- Update the matrix if any gap is missing or stale.
- Run the Kotlin test suite after documentation or code changes.

## Reason

The gap matrix is the implementation backlog contract for `adk-kotlin`.
If newly synced official features are not captured there, future work can miss
functional parity items even when the current implementation compiles.
