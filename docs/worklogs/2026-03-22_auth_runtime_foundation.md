# 2026-03-22 Auth Runtime Foundation

## Planned Work

- Add official-style auth models so tool/runtime code can exchange auth request
  metadata using stable Kotlin types instead of ad-hoc maps.
- Extend `ToolContext` with `requestCredential(...)`, `getAuthResponse(...)`,
  and credential load/save helpers.
- Add a minimal credential service seam with an in-memory implementation and
  propagate requested auth configs through runtime events.
- Add tests covering auth request emission and credential retrieval paths.

## Why This Work

- Official ADK tool ecosystems depend on auth-aware runtime seams before
  authenticated toolsets such as MCP, OpenAPI, or Google APIs become practical.
- Kotlin already has the right execution point for this inside `ToolContext`
  and `Runner`; the main missing piece is the auth model/service surface.
- This closes a concrete gap without forcing a full OAuth or browser flow in
  the first pass.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Event.kt`
- `src/main/kotlin/dev/adk/kotlin/Tool.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/main/kotlin/dev/adk/kotlin/Auth.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added `AuthCredential`, `AuthConfig`, `AuthHandler`, `CredentialService`,
  and `InMemoryCredentialService` as the first auth runtime surface.
- Extended `ToolContext` with `requestCredential(...)`, `getAuthResponse(...)`,
  `saveCredential(...)`, and `loadCredential(...)`.
- Extended `EventActions` so tool events can carry `requestedAuthConfigs` in
  the same style as the official SDK event model.
- Wired `Runner` to pass credential services and per-call ids into tool
  execution so auth requests can be emitted deterministically.
- Added tests for auth request emission and stored credential/auth-response
  retrieval.
- Updated `README.md` to document the new auth-aware tool surface.

## Why The Final Shape

- Auth becomes useful only when it is attached to the actual tool execution
  path, because that is where the SDK knows the current function call id and
  can emit request metadata on events.
- A small credential service seam is enough to unlock authenticated tool
  behavior now without prematurely committing to a full OAuth/browser stack.
- Reusing session state for auth responses matches the official temp-state
  pattern and keeps Kotlin interoperable with existing state interpolation.

## Verification Results

- `./gradlew test` passed successfully.
- Work unit is ready for commit and push.
