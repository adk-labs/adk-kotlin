# ADK Official Gap Matrix

## Reference Basis

- Kotlin source reviewed: `src/main/kotlin/dev/adk/kotlin` (`14` files)
- Python reference reviewed: `../ref/adk-python/src/google/adk` (`544` files)
- Java reference reviewed: `../ref/adk-java/core/src/main/java/com/google/adk` (`181` files)

This matrix uses `adk-python` as the broadest feature baseline and
`adk-java/core` as the closest official JVM baseline.

## Status Legend

- `Aligned`: present in Kotlin with roughly the intended official role
- `Partial`: present, but materially narrower than the official SDKs
- `Missing`: no meaningful Kotlin implementation yet

## Capability Matrix

| Capability Area | adk-python | adk-java | adk-kotlin | Status | Remaining Kotlin Work | Priority |
| --- | --- | --- | --- | --- | --- | --- |
| Public API naming and core entry points | `Agent`, `Context`, `Runner`, app-level modules | `App`, `LlmAgent`, `Runner`, builders | `App`/`Agent`/`Context` aliases, `app(...)`, `agent(...)`, `Runner` | Partial | Keep aligning deeper config names and builder/config surfaces beyond the current aliases | P0 |
| App container and root agent model | `apps/app.py`, compaction, summarizer hooks | `apps/App.java`, cache/compaction config | Minimal `App` container with `rootAgent` and global instruction | Partial | Add plugins, compaction config, context cache config, app-level services | P0 |
| LLM agent core | Rich `LlmAgent` fields, callbacks, schemas, planners, code executors | Rich `LlmAgent` builder/config/flow selection | Core `LlmAgent` with instruction, tools, sub-agents, transfer flags, output schema | Partial | Add callbacks, input schema, output key, planner/executor hooks, config parity | P0 |
| Prompt assembly semantics | Identity, instructions, output schema, transfer, artifacts, cache processors | Identity, instructions, output schema, transfer, artifacts | Global/static/dynamic instruction, identity, transfer, output schema workaround, artifacts | Partial | Add broader flow processors like context cache, content inclusion, request confirmation, load artifacts/memory prompts | P0 |
| Runner/runtime loop | Full runner with services, plugins, memory, tracing | Full runner with services, plugins, memory, tracing | Minimal runner with tool loop, transfer, structured output, artifacts | Partial | Add plugin pipeline, memory service, tracing hooks, richer run config, event stream support | P0 |
| Session services | In-memory, sqlite, database, Vertex, migration support | Session services and in-memory runner support | `SessionStore` + `InMemorySessionStore` only | Partial | Add persistent session backends, session schema evolution, richer state/session utilities | P1 |
| Artifact services | Base, in-memory, file, GCS, version metadata, delete/list | Base, in-memory, GCS | In-memory artifact service with save/load/list | Partial | Add base contract depth, delete/version metadata, file-backed and cloud-backed services | P1 |
| Model/provider layer | Multiple provider implementations, registry, request/response config | Model abstraction and registry | `LanguageModel` abstraction only | Missing | Build provider modules, model registry, request config surface, capability utilities | P0 |
| Tool base runtime | Base tool classes, toolsets, tool config, confirmations, agent tools | Base tools, transfer/set-model-response, MCP/computer-use/retrieval | Simple tool definition/context/lambda tools | Partial | Add richer parameter schema, toolsets, confirmation/auth hooks, agent-as-tool, memory/artifact loaders | P0 |
| Built-in tool ecosystems | Search, MCP, OpenAPI, Google APIs, BigQuery, Spanner, computer use, retrieval | Application integration, MCP, retrieval, computer use | None beyond internal transfer/structured-output helpers | Missing | Add packaged tool modules after core provider/tool abstractions stabilize | P2 |
| Memory services | Multiple memory services and memory tools | Memory package | None | Missing | Add memory service abstraction, in-memory backend, load/preload memory tools | P1 |
| Plugins | Logging, global instruction, retry, multimodal, save-files-as-artifacts | Plugin manager and plugins | None | Missing | Add plugin interfaces and runner/plugin pipeline before implementing individual plugins | P1 |
| Events and event actions | Full event model, event actions, UI widgets | Full event model | Transcript messages only | Missing | Add first-class event model, state/artifact deltas, tool/model event stream | P0 |
| Streaming and multimodal flows | Audio/transcription/cache managers, live request queue | Audio/streaming support in flows | None | Missing | Add streaming response model, live request queue, multimodal request/result handling | P1 |
| Planner support | Built-in planner, ReAct planner, planning flow processor | Planning in `LlmAgent` / flows | None | Missing | Add planner abstraction and planning-aware runner/flow integration | P2 |
| Non-LLM agent types | Sequential, parallel, loop, langgraph, remote A2A | Sequential, parallel, loop | Only hierarchical `LlmAgent` tree + transfer | Missing | Add explicit composite agent types and execution semantics | P1 |
| A2A support | Full `a2a` package, converters, executor, utils | Separate A2A module in official repo | None | Missing | Add only after event model and non-LLM orchestration are in place | P3 |
| Auth and credential services | Auth providers, credential services, OAuth flow | Not a strong core Java focus, but present across official surface | None | Missing | Add auth tool/config/service abstractions before tool ecosystem expansion | P2 |
| Code executors | Built-in, local, container, Vertex/GKE executors | Code executor package | None | Missing | Add base executor abstraction and one local reference implementation | P2 |
| CLI, dev server, browser UI, conformance | Large CLI/dev/browser/conformance surface | Separate dev/web modules outside core | None | Missing | Defer until runtime/model/events stabilize | P3 |
| Evaluation and optimization | Large evaluation/optimizer/simulator stack | Samples and evaluation-adjacent utilities | None | Missing | Defer until event model, sessions, and provider integrations exist | P3 |
| Integrations | Slack, LangChain, CrewAI, registry modules | Some integration-oriented tooling | None | Missing | Defer until stable tool/provider/plugin APIs exist | P3 |
| Telemetry and tracing | Dedicated telemetry package | Dedicated telemetry package | None | Missing | Add tracing abstraction once runner/plugins/events are more complete | P2 |
| Skills, platform, features, misc utils | Separate skills/platform/features/errors/utils packages | Utility packages | Minimal inline utilities only | Missing | Add only when a concrete feature requires them; do not port wholesale | P3 |

## What Is Already Strong In Kotlin

- Official prompt semantics are no longer the main gap.
  Current Kotlin covers global instruction, static/dynamic instruction behavior,
  identity prompt, transfer prompt, output schema workaround, native output
  schema request/result path, and artifact-aware interpolation.
- Core runtime loop is now credible for a narrow MVP.
  `Runner`, `ToolContext`, `SessionStore`, and `ArtifactService` are integrated
  enough to support single-agent and transfer-based flows with tool execution.
- Public naming drift has been reduced.
  Kotlin can now expose official-style `App`, `Agent`, `Context`, `app(...)`,
  `agent(...)`, `tools(...)`, and `subAgents(...)`.

## Highest-Value Next Implementation Order

1. Provider/model modules
   - Without real providers, Kotlin cannot approach official utility even if the
     core runtime shape is improving.
2. Event model and plugin pipeline
   - Many official capabilities depend on event actions, streaming events,
     plugin interception, and richer run lifecycle hooks.
3. Richer tool/runtime schema layer
   - Toolsets, confirmations, auth hooks, agent-as-tool, memory/artifact
     loaders, and provider-aware tool declarations should sit on a stronger core
     tool abstraction.
4. Persistent sessions, artifacts, and memory
   - Needed before evaluation, dev server, and production usage can make sense.
5. Composite agents and planners
   - Sequential/parallel/loop/planner support expands the agent runtime beyond
     the current transfer-oriented `LlmAgent` tree.
6. Ecosystem surfaces
   - CLI/browser/dev server, evaluation, A2A, integrations, telemetry, and
     specialized tool packages should follow after the runtime substrate exists.

## Practical Conclusion

`adk-kotlin` is no longer missing the prompt/runtime basics, but it is still a
very small subset of the official SDK surface. The main gap is no longer
"instruction wording"; it is now the absence of the official runtime
infrastructure around models, events, plugins, persistent services, tools, and
ecosystem modules.
