# 2026-04-20 pure kotlin port review

## What I worked on

- Reviewed whether `adk-kotlin` is wrapping the official `adk-java` library or actually porting the concepts into native Kotlin code.
- Audited the current codebase for Java ADK runtime dependencies, imports, and interoperability usage.
- Wrote an architectural assessment focused on the difference between:
  - a real Kotlin port
  - a Java-shaped API surface copied into Kotlin syntax

## Why this work was needed

- The implementation goal is not a 1:1 wrapper over `adk-java`.
- A Kotlin SDK can still drift in the wrong direction even without depending on the Java library, if it mostly recreates Java builder/config surfaces without integrating them into the Kotlin runtime and DSL.
- That distinction matters now because the provider/model layer was recently expanded with official-style names.

## Result

- There is no code-level evidence that `adk-kotlin` wraps `adk-java`.
- The current implementation is a native Kotlin runtime with its own DSL, runner, events, plugins, and services.
- However, some of the newest provider/model surfaces are starting to become Java-shaped compatibility layers rather than fully integrated Kotlin-native APIs.
- The main risks are:
  - provider/model objects are not wired into the agent/runtime path yet
  - default provider registrations look real but still require injected transport implementations
  - builder-heavy Java compatibility APIs are growing faster than Kotlin-native integration
