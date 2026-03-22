# ADK Kotlin

`adk-kotlin` is being built as a Kotlin-first SDK, not as a 1:1 port of the
official Java ADK.

The reference implementations in `../ref` are useful for capability mapping,
but the API shape here follows Kotlin conventions first:

- DSL-based agent definition instead of builder-heavy configuration.
- `suspend`-driven runtime boundaries instead of Rx-centric APIs.
- Immutable domain models for app, session, and run results.
- Lambda-friendly tools and small composable interfaces.

## Current Scope

The repository currently contains the first runnable foundation:

- A standalone Gradle/Kotlin library skeleton.
- `adkApp {}` and `rootAgent {}` DSL entry points.
- A minimal `Runner` that supports a model/tool loop.
- An in-memory `SessionStore`.
- Tests that validate the DSL and the initial runtime flow.

This is intentionally narrower than the official ADK libraries. The first goal
is to lock in an idiomatic Kotlin surface before expanding into provider
integrations and broader ADK feature parity.

## Example

```kotlin
val app = adkApp("travel-assistant") {
    rootAgent("planner") {
        model = "gemini-2.5-pro"
        instruction("Use tools before answering.")
        tool(
            tool(
                name = "lookup_weather",
                description = "Resolve current weather for a city.",
            ) { call ->
                val city = call.requireArgument("city")
                remember("last_city", city)
                ToolOutput("$city is sunny")
            }
        )
    }
}
```

## Near-Term Roadmap

- Expand from the single-agent runtime loop to multi-agent orchestration.
- Introduce provider modules instead of baking model transports into core.
- Add richer tool schemas, streaming, and persistent session backends.
