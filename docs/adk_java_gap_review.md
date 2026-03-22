# ADK Java Gap Review

## Verdict

`adk-kotlin` is no longer a toy subset, but it is still not at feature parity with the official
`adk-java` libraries.

The short version:

- Core prompt/runtime semantics are now credible.
- Several official agent/runtime concepts already exist in Kotlin.
- The gap is still material in provider implementations, event compaction/summarization, telemetry,
  packaged tool ecosystems, replay/conformance, and the richer dev web stack.

## Reference Basis

- Kotlin reviewed: `src/main/kotlin/dev/adk/kotlin` (`41` Kotlin files)
- Java core reviewed: `../ref/adk-java/core/src/main/java/com/google/adk` (`181` Java files)
- Java dev reviewed: `../ref/adk-java/dev/src/main/java/com/google/adk` (`36` Java files)

Top-level source distribution:

| Surface | File count |
| --- | ---: |
| `adk-kotlin` root package | 36 |
| `adk-kotlin/cli` | 4 |
| `adk-kotlin/web` | 1 |
| `adk-java/core/agents` | 24 |
| `adk-java/core/tools` | 52 |
| `adk-java/core/sessions` | 18 |
| `adk-java/core/flows` | 18 |
| `adk-java/core/models` | 13 |
| `adk-java/core/plugins` | 11 |
| `adk-java/dev/web` | 26 |
| `adk-java/dev/plugins` | 10 |

This review compares Kotlin against both official JVM surfaces:

- `core`: runtime, agents, tools, sessions, events, models
- `dev`: web server, replay/recordings, dev-only plugins

## Status Legend

- `Aligned`: the Kotlin capability exists with roughly the same role
- `Partial`: the capability exists, but is materially narrower than Java
- `Missing`: there is no meaningful Kotlin equivalent yet

## Capability Matrix

| Capability Area | Official Java Reference | Kotlin Status | Notes |
| --- | --- | --- | --- |
| Public API naming | `apps/App.java`, `agents/LlmAgent.java`, `runner/Runner.java` | Partial | Kotlin follows official names for `App`, `Agent`, `Context`, `Runner`, but builder/config surfaces are still much thinner |
| Agent types | `LlmAgent`, `SequentialAgent`, `LoopAgent`, `ParallelAgent` | Partial | Kotlin has these execution kinds, but Java still has richer config classes, callbacks, run config, and live request support |
| Prompt assembly semantics | `flows/llmflows/*`, `agents/Instruction.java` | Partial | Kotlin already aligns well on instruction, identity, transfer, output schema, and artifact interpolation, but does not expose Java's flow package structure |
| Runner lifecycle | `runner/Runner.java`, `runner/InMemoryRunner.java` | Partial | Kotlin runner now supports tools, transfer, structured output, plugins, memory, confirmations, and streaming, but Java still has richer builder/config integration |
| Event model | `events/Event.java`, `EventActions.java`, `EventStream.java`, `EventCompaction.java` | Partial | Kotlin has first-class events and streaming callbacks, but no dedicated event compaction or iterable event stream type |
| Plugins | `plugins/Plugin.java`, `PluginManager.java`, bundled plugins | Partial | Kotlin has plugin hooks plus logging/context/global/multimodal/save-files plugins, but lacks analytics and replay parity |
| Sessions | `sessions/BaseSessionService.java`, `InMemorySessionService.java`, `VertexAiSessionService.java` | Partial | Kotlin has in-memory and file-backed stores, but not the broader service/API-oriented session layer |
| Artifacts | `artifacts/BaseArtifactService.java`, `InMemoryArtifactService.java`, `GcsArtifactService.java` | Partial | Kotlin has in-memory and file-backed artifacts with versioning, but no cloud/backend integrations or Java `Part`-centric contract |
| Memory | `memory/BaseMemoryService.java`, `InMemoryMemoryService.java` | Partial | Kotlin has in-memory memory and load/preload tools, but the surface is smaller and less integrated with the rest of the runtime |
| Models/providers | `models/Gemini.java`, `Claude.java`, `ApigeeLlm.java`, `LlmRegistry.java` | Partial | Kotlin has `BaseLlm`, registry, and capability hooks, but no official provider implementations yet |
| Code executors | `codeexecutors/*` | Partial | Kotlin has executor foundations, but Java still has broader executor variants and tighter flow integration |
| Tool base runtime | `tools/BaseTool.java`, `FunctionTool.java`, `AgentTool.java`, `ToolContext.java` | Partial | Kotlin now has tool schemas, auth, confirmation, toolsets, agent-as-tool, and memory/artifact built-ins |
| Packaged tool ecosystems | `tools/mcp`, `tools/retrieval`, `tools/computeruse`, `tools/applicationintegrationtoolset` | Missing | Kotlin has no comparable packaged tool modules yet |
| Summarization / compaction | `summarizer/*`, `events/EventCompaction.java` | Missing | No Kotlin summarizer/compactor package exists |
| Telemetry | `telemetry/Tracing.java` | Missing | Kotlin has logging plugins, but no telemetry/tracing abstraction or OpenTelemetry bridge |
| Web/dev server | `dev/web/*` | Partial | Kotlin has a minimal HTTP server with session/run/SSE endpoints, but not the Spring-based controller/service/websocket stack |
| Recordings | `dev/plugins/recordings/*` | Partial | Kotlin has recordings generation, but not the same loader/model split as Java |
| Replay | `dev/plugins/ReplayPlugin.java` | Missing | Kotlin does not yet have replay verification/runtime support |
| Examples/utilities | `examples/*`, `utils/*` | Missing | Kotlin does not yet expose comparable example/provider utility packages |

## What Is Effectively Aligned

These are the areas where Kotlin already captures the official Java intent well enough that they
are no longer the primary parity blocker:

- Official naming direction for the main entry points
- Prompt assembly semantics around instruction composition
- Transfer semantics and `set_model_response` fallback flow
- Explicit non-LLM agent execution kinds: sequential, loop, parallel
- First-class plugin lifecycle hooks
- Streaming event delivery from the runner
- Basic session/artifact/memory services usable in local development
- Minimal CLI and dev server foundations

## Highest-Impact Remaining Gaps

### 1. Provider implementations are still absent

Java ships real model classes and registry defaults in `models/*`.
Kotlin currently has only the abstraction and registry scaffolding, which means JVM parity is still
blocked on actual provider modules.

### 2. The Java event pipeline is richer than Kotlin's current event layer

Java has `EventStream`, `EventCompaction`, and the summarizer package. Kotlin can emit and stream
events, but it still lacks the compaction and summarization layer that the official runner expects.

### 3. Kotlin does not yet cover the official packaged tool ecosystems

Java already exposes dedicated modules for MCP, retrieval, computer use, and application
integration. Kotlin only has the shared runtime substrate; the packaged tool families themselves
are still missing.

### 4. Dev parity is still incomplete

Kotlin now has a working HTTP server and recordings plugin, but Java dev goes further with replay,
graph/debug/evaluation endpoints, service/controller separation, and live websocket support.

### 5. Telemetry is still missing as a first-class subsystem

Java's tracing layer is not just logging. It captures invocation, tool, and model execution
metadata into OTEL-compatible spans. Kotlin does not yet have an equivalent subsystem.

## Practical Conclusion

If the bar is "does `adk-kotlin` already mirror the official JVM SDK closely enough that there is
no material Java gap left", the answer is **no**.

If the bar is "has `adk-kotlin` already crossed from prompt-only prototype into a real ADK-shaped
runtime", the answer is **yes**.

The remaining Java parity work should be prioritized in this order:

1. Real model/provider modules
2. Event compaction and summarizer package
3. Telemetry/tracing package
4. Packaged tool ecosystems: MCP, retrieval, computer use, application integration
5. Replay plugin and broader dev web stack
6. Utility/examples/conformance-style support layers
