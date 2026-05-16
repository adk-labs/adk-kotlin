# ADK Official Gap Matrix

## Reference Basis

- Kotlin source reviewed: `src/main/kotlin/dev/adk/kotlin` (`50` files)
- Python reference reviewed: `../ref/adk-python/src/google/adk` (`536` Python files)
- Java reference reviewed: `../ref/adk-java/core/src/main/java/com/google/adk` (`195` Java files)

This matrix uses `adk-python` as the broadest feature baseline and
`adk-java/core` as the closest official JVM baseline.

## Status Legend

- `Aligned`: present in Kotlin with roughly the intended official role
- `Partial`: present, but materially narrower than the official SDKs
- `Missing`: no meaningful Kotlin implementation yet

## Capability Matrix

| Capability Area | adk-python | adk-java | adk-kotlin | Status | Remaining Kotlin Work | Priority |
| --- | --- | --- | --- | --- | --- | --- |
| Public API naming and core entry points | `Agent`, `Context`, `Runner`, app-level modules | `App`, `LlmAgent`, `Runner`, builders | `App`/`Agent`/`Context` aliases, `app(...)`, `agent(...)`, `Runner`, Kotlin-first factories | Partial | Keep aligning deeper config names, package/module layout, and builder/config surfaces beyond current aliases | P0 |
| App container and root agent model | `apps/app.py`, compaction, summarizer hooks | `apps/App.java`, cache/compaction config | `AdkApp` with root agent, global instruction, transfer tree helpers, events compaction config, resumability config | Partial | Add app-level context cache, service composition, richer app metadata, and first-party summarizer implementations | P0 |
| LLM agent core | Rich `LlmAgent` fields, callbacks, schemas, planners, code executors | Rich `LlmAgent` builder/config/flow selection | `LlmAgent` with model, instructions, tools/toolsets, schemas, output key, planner, code executor, sub-agents, transfer flags | Partial | Add official agent callbacks, config schemas/loading parity, active streaming tools, context cache config, and fuller flow selection | P0 |
| Prompt assembly semantics | Identity, instructions, output schema, transfer, artifacts, cache processors | Identity, instructions, output schema, transfer, artifacts | Official identity/transfer/output-schema wording, global/static/dynamic instruction placement, artifact interpolation, include contents | Partial | Add context cache processor, request confirmation prompt processing, richer content/function-response formatting, and memory/artifact prompt processors | P0 |
| Runner/runtime loop | Full runner with services, plugins, memory, tracing | Full runner with services, plugins, memory, tracing | Runner with tool loop, transfer, structured output, plugins, memory, artifacts, code execution, composite agent execution, event callbacks, post-run compaction hook | Partial | Add full event stream abstraction, run config parity, tracing/telemetry, live runtime, and official flow processor decomposition | P0 |
| Session services | In-memory, sqlite, database, Vertex, migration support | Session services, Vertex, JSON conversion | `SessionStore`, in-memory and file-backed stores | Partial | Add sqlite/database/Vertex services, schema migration/versioning, event paging, and official session JSON conversion | P1 |
| Artifact services | Base, in-memory, file, GCS, version metadata, delete/list | Base, in-memory, GCS | Base interface, in-memory/file services, versioned save/load/list/delete | Partial | Add GCS/cloud-backed service, official response types, artifact namespace parity, and binary/mime-rich artifact content | P1 |
| Model/provider layer | Multiple provider implementations, registry, request/response config | Model abstraction, registry, Chat Completions HTTP client | `Model`, `BaseLlm`, Gemini/Claude/Apigee models, explicit transport registry, OpenAI-compatible chat-completions HTTP transport, capabilities, generate config | Partial | Add streaming Chat Completions, first-party Gemini/Claude transports/connections, request/response metadata propagation, and provider-specific utilities | P0 |
| Tool base runtime | Base tool classes, toolsets, tool config, confirmations, agent tools | Base tools, transfer/set-model-response, MCP/computer-use/retrieval | Tool schema, function tools, toolsets, confirmations, auth hooks, agent tool, load/preload memory, load artifacts | Partial | Add official function declaration conversion depth, long-running function tools, MCP/OpenAPI/retrieval/computer-use toolsets, and richer tool config | P0 |
| Built-in tool ecosystems | Search, MCP, OpenAPI, Google APIs, BigQuery, Spanner, computer use, retrieval | Application integration, MCP, retrieval, computer use | Internal ADK tools plus load memory/artifacts and agent tool; no external ecosystem toolsets yet | Partial | Add packaged MCP, OpenAPI, Google Search, Vertex AI Search, computer use, retrieval, and cloud data toolsets | P2 |
| Memory services | Multiple memory services and memory tools | Memory package | Base memory service, in-memory memory service, load/preload memory tools | Partial | Add Vertex/RAG memory services, richer ranking/search metadata, memory entry schema parity, and persistence backends | P1 |
| Plugins | Logging, global instruction, retry, multimodal, save-files-as-artifacts | Plugin manager and plugins | Plugin interface/manager plus logging, global instruction, context filter, debug logging, multimodal tool results, save-files, recordings | Partial | Add reflect/retry plugin, BigQuery analytics, official agent callback integration, telemetry context propagation, and plugin package layout | P1 |
| Events and event actions | Full event model, event actions, UI widgets | Full event model | Event model with action deltas, compaction metadata, UI widgets, long-running IDs, streaming/error/model metadata, `EventStream` | Partial | Add genai-style content parts/function calls, event compactor/summarizer integration, and official JSON conversion | P0 |
| Streaming and multimodal flows | Audio/transcription/cache managers, live request queue | Audio/streaming support in flows | SSE web endpoint, streaming event flags, multimodal tool-result plugin metadata | Partial | Add live request queue, bidi/audio/video runtime, transcription managers, cache managers, and model connection streaming parity | P1 |
| Planner support | Built-in planner, ReAct planner, planning flow processor | Planning in `LlmAgent` / flows | `BasePlanner`, `BuiltInPlanner`, `PlanReActPlanner` | Partial | Align official planner prompts/config, integrate with flow processors, expose planner package APIs, and add planning tests against reference semantics | P2 |
| Non-LLM agent types | Sequential, parallel, loop, langgraph, remote A2A | Sequential, parallel, loop | Sequential/parallel/loop DSL and runner execution semantics | Partial | Add dedicated class hierarchy/configs, branch/history isolation parity, langgraph/remote A2A agent types, and richer composite result semantics | P1 |
| A2A support | Full `a2a` package, converters, executor, utils | Separate A2A module in official repo | None | Missing | Add only after event model and non-LLM orchestration are in place | P3 |
| Auth and credential services | Auth providers, credential services, OAuth flow | Auth-adjacent tool support | Auth config, credential model/service, in-memory credential service, auth handler | Partial | Add OAuth discovery/exchange/refresh, provider registry, session-state credential service, and official auth tool parity | P2 |
| Code executors | Built-in, local, container, Vertex/GKE executors | Code executor package | Base executor, local execution support, code result artifacts/state deltas | Partial | Add container/Vertex/GKE executors, sandbox policy depth, code execution utils parity, and generated file handling | P2 |
| CLI, dev server, browser UI, conformance | Large CLI/dev/browser/conformance surface | Separate dev/web modules outside core | CLI runner, web server, SSE, recordings schema/plugin | Partial | Add official CLI command parity, browser UI assets, eval/conformance commands, replay/test tooling, and service registry | P3 |
| Evaluation and optimization | Large evaluation/optimizer/simulator stack | Samples and evaluation-adjacent utilities | None | Missing | Defer until event model, sessions, and provider integrations exist | P3 |
| Integrations | Slack, LangChain, CrewAI, registry modules | Some integration-oriented tooling | None | Missing | Defer until stable tool/provider/plugin APIs exist | P3 |
| Telemetry and tracing | Dedicated telemetry package | Dedicated telemetry package | None | Missing | Add tracing abstraction, span export, request/response metadata capture, and plugin/runner integration | P2 |
| Skills, platform, features, misc utils | Separate skills/platform/features/errors/utils packages | `skills` package, utility packages | `SkillSource`, `Frontmatter`, in-memory/local skill sources; minimal inline platform/utils and typed errors | Partial | Wire skills into prompt/tool flows where official SDKs consume them, then add platform/time/uuid shims, feature registry, utility packages, and official error hierarchy when concrete modules need them | P2 |

## What Is Already Strong In Kotlin

- Official prompt semantics are no longer the main gap.
  Current Kotlin covers global instruction, static/dynamic instruction behavior,
  identity prompt, transfer prompt, output schema workaround, native output
  schema request/result path, artifact-aware interpolation, and prompt-facing
  whitespace preservation.
- Core runtime loop is now credible beyond a narrow MVP.
  `Runner`, `ToolContext`, `SessionStore`, `ArtifactService`, `MemoryService`,
  `PluginManager`, planners, code executors, and event callbacks are integrated
  enough to support tool execution, transfer, composite agents, structured
  output, plugins, memory/artifacts, and web/SSE flows.
- Public naming drift has been reduced.
  Kotlin can now expose official-style `App`, `Agent`, `Context`, `app(...)`,
  `agent(...)`, `tools(...)`, `subAgents(...)`, `Model`, `BaseLlm`, `Gemini`,
  `Claude`, `ApigeeLlm`, and Kotlin-first provider factories.
- The event substrate is now present.
  Kotlin now has event action deltas, compaction metadata, UI widget metadata,
  long-running tool IDs, streaming/error/model metadata, and export paths through
  web payloads and recordings.
- The official skills substrate is now present.
  Kotlin now has frontmatter parsing/validation, in-memory skill loading, local
  `SKILL.md` discovery, instruction extraction, and resource loading with
  official API names.
- The model layer now has a concrete HTTP transport path.
  Kotlin now includes an OpenAI-compatible Chat Completions HTTP client and
  `LlmTransport` adapter for non-streaming provider calls.
- App-level event compaction is now wired into runtime persistence.
  Kotlin now has `EventsCompactionConfig`, `BaseEventsSummarizer`, sliding-window
  and token-threshold compaction helpers, plus a post-run Runner hook.

## Highest-Value Next Implementation Order

1. Real provider transports and streaming connections
   - The model/provider API and non-streaming Chat Completions HTTP transport
     are present, but Gemini/Claude first-party transports, streaming, and live
     connections remain the next utility gap.
2. Event compaction and summarizer integration
   - App-level compaction config and the post-run hook are present, but
     first-party LLM summarizer implementations and deeper content filtering
     parity still need work.
3. Tool ecosystem modules
   - MCP, OpenAPI, Google Search, Vertex AI Search, retrieval, computer use,
     and long-running function tools are still large user-visible gaps.
4. Persistent production services
   - SQLite/database/Vertex sessions, GCS artifacts, Vertex/RAG memory, and
     schema migration are needed before production-like usage.
5. Live streaming and multimodal runtime
   - SSE exists, but official live request queue, audio/video, transcription,
     and connection-level streaming are still missing.
6. Ecosystem surfaces
   - CLI/browser/dev server parity, conformance, evaluation, A2A, integrations,
     telemetry, feature registry, and utility packages should follow after the
     runtime substrate is stable.

## Practical Conclusion

`adk-kotlin` is no longer missing the prompt/runtime basics and is no longer a
simple transcript-only prototype. The remaining gap is now concentrated in
provider transports, official event-stream/compaction behavior, external tool
ecosystems, production persistence services, live multimodal runtime, and the
larger CLI/evaluation/A2A/telemetry surfaces.
