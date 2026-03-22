# ADK Kotlin

`adk-kotlin` is being built as a Kotlin-first SDK, not as a 1:1 port of the
official Java ADK.

The reference implementations in `../ref` are useful for capability mapping,
but the API shape here follows Kotlin conventions first:

- DSL-based agent definition instead of builder-heavy configuration.
- `suspend`-driven runtime boundaries instead of Rx-centric APIs.
- Immutable domain models for app, session, and run results.
- Lambda-friendly tools and small composable interfaces.
- Official ADK-aligned instruction assembly for identity, global instruction,
  session-state injection, and transfer instructions.

## Current Scope

The repository currently contains the first runnable foundation:

- A standalone Gradle/Kotlin library skeleton.
- `adkApp {}` and `rootAgent {}` DSL entry points.
- A `Runner` that supports a model/tool loop and agent handoff.
- An in-memory `SessionStore`.
- Tests that validate the DSL, prompt assembly, and transfer flow.

This is intentionally narrower than the official ADK libraries. The first goal
is to lock in an idiomatic Kotlin surface before expanding into provider
integrations and broader ADK feature parity.

## Example

```kotlin
val app = adkApp("travel-assistant") {
    globalInstruction("Prefer factual answers for {user:name}.")

    rootAgent("planner") {
        model = "gemini-2.5-pro"
        description = "Coordinates trip planning."
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
        subAgent("researcher") {
            model = "gemini-2.5-flash"
            description = "Researches destinations and local details."
            instruction("Handle research-heavy questions.")
        }
    }
}
```

The system instruction passed to the model is not improvised from that DSL.
The Kotlin runtime now follows the official ADK layering:

1. Global instruction.
2. Agent instruction or static instruction semantics.
3. Framework identity instruction.
4. Transfer instructions when agent handoff is available.

## Near-Term Roadmap

- Introduce provider modules instead of baking model transports into core.
- Add richer tool schemas, streaming, and persistent session backends.
- Align more of the official runtime surface, including output schema and
  artifact-aware instructions.
