# Pure Kotlin Port Review

## Verdict

`adk-kotlin` is **not** wrapping `adk-java`.

The current codebase is a native Kotlin implementation:

- no `adk-java` dependency in [build.gradle.kts](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/build.gradle.kts)
- no `com.google.adk.*` imports in the Kotlin sources
- runtime, DSL, runner, events, plugins, sessions, tools, and web server all live under
  `dev.adk.kotlin`

So on the narrow question of "are we just wrapping the Java SDK?", the answer is **no**.

The more important review result is this:

- the project is still a pure Kotlin port in structure
- but parts of the newer provider/model surface are beginning to drift toward
  "Java API copied into Kotlin" instead of "Kotlin-first API that happens to stay source-aligned"

## Evidence That This Is Not a Wrapper

### 1. Build-level dependency evidence

[build.gradle.kts](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/build.gradle.kts) only pulls in:

- Kotlin stdlib
- coroutines
- Gson
- test dependencies

There is no `adk-java` artifact, no Spring, no RxJava, no GenAI Java SDK, and no Anthropic Java SDK.

### 2. Package-level implementation evidence

Core runtime code is implemented locally in Kotlin:

- DSL: [Dsl.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/Dsl.kt)
- agents/app model: [Agent.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/Agent.kt)
- runner: [Runner.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/Runner.kt)
- events: [Event.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/Event.kt)
- tools: [Tool.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/Tool.kt)
- plugins: [Plugin.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/Plugin.kt)
- sessions/artifacts/memory: [SessionStore.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/SessionStore.kt), [ArtifactService.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/ArtifactService.kt), [MemoryService.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/MemoryService.kt)

This is not the shape of a thin adapter layer.

## Findings

### 1. Provider/model objects are not integrated into the actual runtime path

The newly added [Model.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/Model.kt#L3), `Gemini`, `Claude`, and `ApigeeLlm` types look like first-class public API, but the actual runtime still routes model selection through string names:

- [Agent.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/Agent.kt#L127) keeps `LlmAgent.model` as `String`
- [Dsl.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/Dsl.kt#L66) keeps `LlmAgentDsl.model` as `String?`
- [Dsl.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/Dsl.kt#L153) builds agents with only the string model name
- [LlmRegistry.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/LlmRegistry.kt#L104) still resolves execution by `request.model`

That means the new `Model` value object is currently disconnected from the DSL and runner path. This is not wrapping Java, but it **is** a sign that the port is starting to add compatibility-shaped API before the Kotlin runtime actually consumes it.

### 2. Default provider registrations currently over-promise runtime support

[LlmRegistry.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/LlmRegistry.kt#L16) now registers default factories for `gemini-.*`, `claude-.*`, and `apigee/.*`, but the default provider instances still have no transport implementation.

That is visible in [BaseLlm.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/BaseLlm.kt#L33), where `TransportBackedLlm.generateContent(...)` fails unless a transport was injected.

So right now:

- the API surface says "official provider classes exist"
- the registry says "these model families are supported by default"
- but actual execution still throws unless the caller manually injects a transport

This is the biggest place where the code risks becoming a façade instead of a real Kotlin port.

### 3. The new provider surface is becoming Java-shaped faster than it is becoming Kotlin-first

The recent provider additions lean heavily on Java-style surface replication:

- `builder()` factories and nested `Builder` types in [Gemini.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/Gemini.kt#L23), [Claude.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/Claude.kt#L22), [ApigeeLlm.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/ApigeeLlm.kt#L36), [VertexCredentials.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/VertexCredentials.kt#L9), [Model.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/Model.kt#L17)
- `@JvmStatic`
- deprecated JavaBean-style setters like `setProject`, `setLocation`, `setCredentials` in [VertexCredentials.kt](/Users/jaichang/Documents/GitHub/adk-labs/adk-kotlin/src/main/kotlin/dev/adk/kotlin/VertexCredentials.kt#L18)

None of that proves wrapping. But it does show the implementation is drifting toward "Java compatibility surface first, Kotlin ergonomics second".

Given your stated goal, that is the main direction to correct.

## Practical Assessment

The current state is:

- `adk-kotlin` is a real Kotlin implementation, not a Java wrapper
- the core runtime direction is still correct
- the model/provider layer is the first place where design drift is visible

So the answer is:

- **Yes**, you are still doing a pure Kotlin port
- **No**, you should not be fully satisfied with the current direction yet

The thing to protect now is this rule:

- official naming can be mirrored
- official semantics can be mirrored
- but the public Kotlin usage path should stay Kotlin-native and actually wired into the runtime

## Recommended Next Corrections

1. Thread `Model` or `BaseLlm` into the actual agent DSL/runtime instead of leaving provider objects as disconnected compatibility surface.
2. Do not advertise default provider support through `LlmRegistry` unless a working Kotlin transport exists.
3. Prefer Kotlin construction paths first, and keep Java-style builders as compatibility shims rather than the primary public story.

If you keep that boundary, this stays a Kotlin port. If not, it will gradually become a Java API transcription.
