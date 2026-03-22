# 2026-03-22 Kotlin-First Foundation

## Why This Unit Exists

`adk-kotlin` starts from an almost empty repository, so the first priority is to
create a foundation that is idiomatic in Kotlin instead of mirroring the Java
API surface. The official ADK libraries in `ref/` are useful references for
capabilities, but they are heavily shaped by Java builders and RxJava. Copying
that shape directly would make the Kotlin SDK feel like a wrapper, not a native
SDK.

## Work Scope

This unit bootstraps a compileable JVM library with a Kotlin-first core:

- Gradle project skeleton for a standalone Kotlin library.
- Immutable app, agent, session, and run result models.
- Kotlin DSL for defining an app and root agent.
- `suspend`-based model abstraction instead of Rx-based APIs.
- Lambda-friendly tool registration and an in-memory session store.
- A minimal runner that can execute a model/tool loop.
- Tests that prove the DSL shape and the tool loop behavior.
- README refresh so the repository explains the current direction.

## Why This Shape

- Kotlin users expect DSLs, immutable data, and `suspend` functions before they
  expect builders and callback trees.
- A small runtime loop is enough to validate the API design early without
  prematurely binding the SDK to Google-specific transports.
- In-memory storage and fake-model tests keep the first unit focused on API
  ergonomics and runtime boundaries.

## Completed Work

- Added a standalone Gradle-based Kotlin/JVM project skeleton.
- Added a Kotlin DSL around `adkApp {}` and `rootAgent {}`.
- Added immutable core models for agents, sessions, messages, tools, and run
  results.
- Added a `suspend`-based `LanguageModel` boundary and a minimal `Runner`.
- Added an in-memory `SessionStore` for early runtime testing.
- Added tests for DSL construction and the model/tool execution loop.
- Rewrote the repository README so the Kotlin-first direction is explicit.

## Explicit Non-Goals

This unit does not try to ship feature parity with the official ADK libraries.
The following are intentionally left for later units:

- Remote provider integrations.
- Dev UI, config loaders, and deployment commands.
- Persistent session backends.
- Full multi-agent orchestration semantics.
- MCP, artifact, and memory service integrations.

## Exit Criteria

The unit is complete when `adk-kotlin` has a clear Kotlin-first API direction,
the code compiles, the tests cover the initial runtime loop, and the changes are
committed with the reasoning preserved in version control.

## Verification

- `./gradlew test`
