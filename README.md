# ADK Kotlin

`adk-kotlin` is being built as a Kotlin-first SDK, not as a 1:1 port of the
official Java ADK.

Kotlin-first does not mean naming drift. Public API names should stay aligned
with the official ADK surface where possible, so this library now supports both
the original Kotlin-first helpers and official-style names such as `App`,
`Agent`, `Context`, `app(...)`, `agent(...)`, `tools(...)`, and
`subAgents(...)`.

The reference implementations in `../ref` are useful for capability mapping,
but the API shape here follows Kotlin conventions first:

- DSL-based agent definition instead of builder-heavy configuration.
- `suspend`-driven runtime boundaries instead of Rx-centric APIs.
- Immutable domain models for app, session, and run results.
- Lambda-friendly tools and small composable interfaces.
- Official ADK-aligned instruction assembly for identity, global instruction,
  session-state injection, and transfer instructions.
- Native output schema request/result alignment, with the internal
  `set_model_response` workaround only when tools and structured output cannot
  coexist for the current model.
- Artifact-aware instruction interpolation via `{artifact.filename}` with an
  in-memory artifact service wired by default in `Runner`.
- Model-layer foundation with `BaseLlm`, `LlmRegistry`, and explicit
  `generateContentConfig` propagation on requests.
- First-class `Event` and `EventActions` emission with state, artifact,
  transfer, and completion deltas.
- Official-style plugin lifecycle hooks around user input, model calls, tool
  execution, event emission, and run completion.
- Structured tool schemas and richer declaration metadata for internal and
  user-defined tools.
- File-backed session/artifact backends and a first-class memory service
  surface.
- Sequential, loop, and parallel shell agents for official-style multi-agent
  orchestration.
- Planner foundations with `BuiltInPlanner` thinking-config override and
  `PlanReActPlanner` instruction injection.
- Controlled I/O semantics with `includeContents` and `outputKey`.
- Agent-as-tool support with `inputSchema` and `AgentTool`.
- Code executor foundations with official-style `codeExecutor`, built-in model
  markers, and an `UnsafeLocalCodeExecutor` retry loop.
- Tool confirmation foundation so `requiresConfirmation` can drive real runtime
  approval events instead of remaining a passive declaration flag.
- Auth runtime foundation with `AuthConfig`, `AuthCredential`, and
  credential-aware `ToolContext` helpers.
- Official-style `ToolContext.requestConfirmation(...)` so tools can ask for
  approval directly during execution.
- `BaseAuthenticatedTool` and `BaseToolset` foundations for auth-aware tools
  and grouped tool modules.
- Official-style built-in utility tools for `loadMemory`, `preloadMemory`, and
  `loadArtifacts`, including request preprocessing hooks.
- Plugin request mutation plus `GlobalInstructionPlugin` for official-style
  before-model instruction prepending on immutable requests.
- Packaged `ContextFilterPlugin` and `DebugLoggingPlugin` for official-style
  prompt trimming and local debug trace capture.
- Packaged `LoggingPlugin` for console-oriented lifecycle tracing.
- Attachment-aware `UserMessage` input plus `SaveFilesAsArtifactsPlugin` for
  official-style upload-to-artifact flows.

## Current Scope

The repository currently contains the first runnable foundation:

- A standalone Gradle/Kotlin library skeleton.
- `adkApp {}` and `rootAgent {}` DSL entry points.
- A `Runner` that supports a model/tool loop and agent handoff.
- An in-memory `SessionStore`.
- A default in-memory `ArtifactService` for official-style instruction
  interpolation.
- A provider-ready model foundation instead of a single raw callback surface.
- First-class runtime events persisted on sessions and returned from runs.
- A plugin manager and global plugin callbacks integrated into `Runner`.
- Structured tool schema support for built-in and user-defined tools.
- Persistent file-backed session/artifact services and in-memory memory search.
- Sequential, loop, and parallel shell-agent orchestration on top of the same
  runner/event model.
- Planner support for model-native thinking and NL plan/react prompting.
- Controlled transcript inclusion and session-state output persistence.
- Wrapped-agent tool execution with schema-derived tool declarations.
- Code-executor request preprocessing and local fenced-code execution retries.
- Runner-level tool confirmation handling with approval metadata on events.
- Runtime auth request emission plus in-memory credential loading hooks.
- Tool-driven confirmation requests propagated on emitted tool events.
- Toolset expansion and authenticated-tool execution on top of the auth
  runtime.
- Built-in memory/artifact utility tools wired into the same request-building
  pipeline as official ADK preprocessors.
- Plugin-level request rewriting before model execution, including a packaged
  `GlobalInstructionPlugin`.
- Packaged context filtering and debug-log file emission plugins.
- Packaged console logging for runner, model, tool, and event callbacks.
- Attachment-aware user input and packaged save-files-to-artifacts upload
  handling.
- Tests that validate the DSL, prompt assembly, and transfer flow.

This is intentionally narrower than the official ADK libraries. The first goal
is to lock in an idiomatic Kotlin surface before expanding into provider
integrations and broader ADK feature parity.

## Example

```kotlin
val greeter: Agent = agent("greeter") {
    model = "gemini-2.5-flash"
    instruction("Handle greeting-heavy requests.")
}

val app: App = app("travel_assistant") {
    globalInstruction("Prefer factual answers for {user:name}.")

    rootAgent(
        agent("planner") {
            model = "gemini-2.5-pro"
            generateContentConfig =
                generateContentConfig {
                    temperature = 0.2
                    maxOutputTokens = 512
                }
            description = "Coordinates trip planning."
            instruction("Use tools before answering.")
            tools(
                tool(
                    name = "lookup_weather",
                    description = "Resolve current weather for a city.",
                ) { call ->
                    val city = call.requireArgument("city")
                    state["last_city"] = city
                    ToolOutput("$city is sunny")
                }
            )
            subAgents(greeter)
        }
    )
}

val pipeline: SequentialAgent =
    sequentialAgent("pipeline") {
        description = "Runs specialists in order."
        subAgents(greeter, app.rootAgent)
    }

val orchestrator: LoopAgent =
    loopAgent("orchestrator") {
        description = "Repeats work until a worker exits the loop."
        maxIterations = 3
        subAgents(
            agent("worker") {
                model = "gemini-2.5-flash"
                instruction("Work until complete, then call exit_loop.")
            }
        )
    }

val fanOut: ParallelAgent =
    parallelAgent("fan_out") {
        description = "Runs multiple specialists in isolated branches."
        subAgents(greeter, app.rootAgent)
    }

val plannerDriven: Agent =
    agent("planner_driven") {
        model = "gemini-2.5-pro"
        planner =
            builtInPlanner {
                includeThoughts = true
                thinkingBudget = 128
            }
    }

val planReact: Agent =
    agent("plan_react") {
        model = "gemini-2.5-pro"
        planner = planReActPlanner()
    }

val focusedExtractor: Agent =
    agent("focused_extractor") {
        model = "gemini-2.5-pro"
        includeContents = IncludeContents.NONE
        outputKey = "extracted_summary"
    }

val researcherTool: Tool =
    agentTool(
        agent("researcher") {
            model = "gemini-2.5-flash"
            description = "Research specialist."
            inputSchema {
                string("topic", description = "Topic to research")
            }
            outputKey = "last_topic_summary"
        },
    )

val analyst: Agent =
    agent("analyst") {
        model = "gemini-2.5-pro"
        codeExecutor =
            unsafeLocalCodeExecutor(
                timeoutSeconds = 10,
                errorRetryAttempts = 2,
            )
    }

val destructiveTool: Tool =
    tool(
        name = "delete_files",
        description = "Deletes files from disk.",
        requiresConfirmation = true,
        confirmationHint = "Confirm file deletion before continuing.",
    ) {
        ToolOutput("deleted")
    }

val authAwareTool: Tool =
    tool(
        name = "call_maps_api",
        description = "Calls an authenticated maps API.",
    ) {
        requestCredential(
            AuthConfig(
                authScheme = "api_key",
                rawAuthCredential = AuthCredential(apiKey = ""),
                credentialKey = "maps_api",
            ),
        )
        ToolOutput("Authentication required.")
    }

val memoryAwareAgent: Agent =
    agent("memory_aware") {
        model = "gemini-2.5-pro"
        tools(loadMemory, preloadMemory, loadArtifacts)
    }

val runtimePlugins =
    listOf(
        globalInstructionPlugin("Speak to {user:name} politely."),
        contextFilterPlugin(numInvocationsToKeep = 2),
        DebugLoggingPlugin(),
        LoggingPlugin(),
        saveFilesAsArtifactsPlugin(),
    )

val publishTool: Tool =
    tool(
        name = "publish_report",
        description = "Publishes a report to users.",
    ) {
        requestConfirmation(
            hint = "Confirm publishing the report to all users.",
            payload = mapOf("audience" to "all"),
        )
        ToolOutput("Publish confirmation requested.")
    }

val mapsTool: Tool =
    authenticatedTool(
        name = "call_maps_api",
        description = "Calls an authenticated maps API.",
        authConfig =
            AuthConfig(
                authScheme = "api_key",
                credentialKey = "maps_api",
            ),
    ) { _, credential ->
        ToolOutput("authorized:${credential?.apiKey}")
    }

val prefixedToolset =
    object : BaseToolset(toolNamePrefix = "maps") {
        override suspend fun getTools(readonlyContext: ReadonlyContext?): List<Tool> = listOf(mapsTool)
    }
```

Provider modules can now plug in through `BaseLlm` and `LlmRegistry`:

```kotlin
LlmRegistry.registerLlm("gemini-.*") { modelName ->
    object : BaseLlm(modelName) {
        override suspend fun generateContent(
            request: ModelRequest,
            stream: Boolean,
        ): ModelResponse = ModelResponse.Final("Implement provider call here.")
    }
}

val runner = Runner(
    app = app,
    model = RegistryBackedLanguageModel(),
    plugins =
        listOf(
            object : BasePlugin("logger") {
                override suspend fun onEventCallback(
                    invocationContext: InvocationContext,
                    event: Event,
                ): Event? {
                    println("${event.author}: ${event.content?.text}")
                    return null
                }
            }
        ),
)

val result = runner.run(
    userId = "user-1",
    sessionId = "session-1",
    input = "Plan a trip to Seoul.",
)

result.events.forEach { event ->
    println("${event.author}: ${event.content?.text} -> ${event.actions}")
}
```

Artifact-backed instructions use the same interpolation path:

```kotlin
val artifactService = InMemoryArtifactService()
val memoryService = InMemoryMemoryService()
artifactService.saveArtifact(
    appName = "travel-assistant",
    userId = "user-1",
    sessionId = "session-1",
    filename = "knowledge.txt",
    artifact = Artifact("Seoul neighborhoods summary"),
)

val runner = Runner(
    app = app,
    model = model,
    artifactService = artifactService,
    memoryService = memoryService,
)
```

With that runner, instructions such as
`globalInstruction("Use this context: {artifact.knowledge.txt}")` are resolved
through the artifact service before the model call.

Tools can also participate in that loop directly:

```kotlin
val persistKnowledge =
    tool(
        name = "persist_knowledge",
        description = "Save reusable context as an artifact.",
        jsonSchema =
            toolSchema {
                string("content", description = "Knowledge content to persist")
            },
        requiresConfirmation = false,
    ) { call ->
        saveArtifact("knowledge.txt", Artifact(call.requireArgument("content")))
        ToolOutput("saved")
    }
```

Artifacts saved from a tool are scoped to the current app/user/session and are
available to later turns through `{artifact.filename}` interpolation.

The system instruction passed to the model is not improvised from that DSL.
The Kotlin runtime now follows the official ADK layering:

1. Global instruction.
2. Agent instruction or static instruction semantics.
3. Framework identity instruction.
4. Output schema workaround instruction when `outputSchema` and tools coexist.
5. Transfer instructions when agent handoff is available.
6. Loop-exit instruction when a `LoopAgent` exposes `exit_loop`.

`ParallelAgent` follows the official isolated-branch shape as well: sub-agents
run against the same starting session snapshot, and Kotlin merges their emitted
events by completion order instead of letting sibling state leak across the
fan-out.

Planner support now follows the same split as the official ADK:

- `BuiltInPlanner` overrides `thinkingConfig` on the outgoing model request.
- `PlanReActPlanner` appends planning instructions and extracts the
  `/*FINAL_ANSWER*/` section from final responses.

Controlled I/O semantics now align with the official agent surface as well:

- `includeContents = IncludeContents.NONE` sends only the current user turn to
  the model instead of the full transcript.
- `outputKey = "..."` writes the final agent output back into session state for
  downstream agents and tools.

`AgentTool` is now available as a first-class wrapper:

- Tool input schema comes from the wrapped agent's `inputSchema`, or falls back
  to a default `request: string` input.
- Wrapped agent runs use an isolated in-memory child runner and forward state
  deltas back into the parent tool context.

Structured output now follows the official split path as well:

- If an agent has `outputSchema` and the model path can use schema natively, the
  schema is attached to `ModelRequest` with `application/json`.
- If an agent combines tools with `outputSchema` on a model path that cannot
  support both together, Kotlin falls back to the internal
  `set_model_response` tool and matching instruction text.

## Near-Term Roadmap

- Introduce provider modules instead of baking model transports into core.
- Add richer plugin behaviors such as request mutation and built-in plugins.
- Add richer tool runtime semantics such as confirmations and toolsets.
- Add database/cloud session and artifact backends.
- Add `ParallelAgent` and planner layers for broader orchestration parity.
- Align more of the official runtime surface, including broader tool/runtime
  schemas and storage modules.
