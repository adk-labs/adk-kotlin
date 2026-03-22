# 2026-03-22 ADK Gap Matrix Assessment

## Planned Work

- Audit the current `adk-kotlin` module surface against `adk-python` and
  `adk-java`.
- Produce a matrix document that groups the official ADK surface by capability
  area and marks Kotlin status as implemented, partial, or missing.
- Add a practical priority order so the matrix is actionable for continued
  implementation work rather than just descriptive.

## Why This Work

- The current repository has gained a useful core, but the remaining work is
  broad and now needs a shared map against the official SDKs.
- `adk-python` especially exposes a much wider surface area than the current
  Kotlin implementation, spanning tools, events, evaluation, CLI, auth,
  integrations, and A2A.
- Without a capability matrix, it is easy to over-focus on local refactors
  while missing higher-value gaps relative to the official SDK scope.

## Files Expected To Change

- `docs/adk_official_gap_matrix.md`

## Verification Plan

- Review the matrix against the current Kotlin source tree and reference trees
- Commit and push this documentation unit

## Completed Work

- Audited the current Kotlin implementation footprint against the official
  reference trees:
  `adk-kotlin` core source,
  `adk-python/src/google/adk`,
  and `adk-java/core/src/main/java/com/google/adk`.
- Added [docs/adk_official_gap_matrix.md](../adk_official_gap_matrix.md), which
  organizes the official ADK surface by capability area and marks Kotlin status
  as `Aligned`, `Partial`, or `Missing`.
- Included a priority column and a recommended implementation order so the
  matrix is directly usable for planning the next work units.
- Captured the main conclusion explicitly:
  Kotlin is no longer mainly blocked on prompt wording, but on the absence of
  the broader official runtime substrate around models, events, plugins,
  persistent services, and ecosystem modules.

## Why These Changes

- The repository had already accumulated enough core runtime work that the next
  bottleneck was no longer implementation detail inside a single file; it was
  lack of a shared map of the remaining official surface.
- A matrix anchored to the official Python and Java trees makes it easier to
  choose the next work by leverage rather than by whatever file was most
  recently edited.
- The matrix also makes it clear where Kotlin already has credible parity
  signals and where almost the entire official feature family is still absent.

## Verification

- Reviewed the matrix against:
  `src/main/kotlin/dev/adk/kotlin`
  `../ref/adk-python/src/google/adk`
  `../ref/adk-java/core/src/main/java/com/google/adk`
