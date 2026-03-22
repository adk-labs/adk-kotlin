# 2026-03-22 Native Output Schema Alignment

## Planned Work

- Add native output schema metadata to `ModelRequest` so Kotlin model adapters
  can receive structured-output instructions directly, not only through the
  internal workaround tool.
- Extend `ModelResponse.Final` so model adapters can return validated
  structured results when the provider supports native structured output.
- Keep the existing `set_model_response` workaround only for the official ADK
  case where tools and output schema cannot be used together.
- Add tests that distinguish native structured output from workaround-based
  structured output.

## Why This Work

- The previous unit aligned prompt/runtime behavior around artifacts, but
  native structured output was still incomplete because `ModelRequest` could not
  carry an output schema at all.
- Official ADK only falls back to `set_model_response` when tools and output
  schema are incompatible for a given model. Otherwise the response schema is
  passed directly to the model request.
- Without a structured result path in `ModelResponse.Final`, Kotlin provider
  adapters would have no clean way to return native structured output back into
  `RunResult`.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Model.kt`
- `src/main/kotlin/dev/adk/kotlin/PromptAssembler.kt`
- `src/main/kotlin/dev/adk/kotlin/Runner.kt`
- `src/test/kotlin/dev/adk/kotlin/PromptAssemblerTest.kt`
- `src/test/kotlin/dev/adk/kotlin/RunnerTest.kt`
- `README.md`

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Extended `ModelRequest` with `outputSchema` and `responseMimeType` so Kotlin
  model adapters can receive native structured-output settings directly.
- Extended `ModelResponse.Final` with `structuredResponse` so model adapters can
  return native structured data back to the runner without routing through the
  internal workaround tool.
- Updated `PromptAssembler` to attach the native output schema and
  `application/json` MIME type whenever the agent should use native structured
  output, while still suppressing both when the workaround path is active.
- Updated `Runner` to validate native structured responses against the agent
  output schema and expose them through `RunResult.structuredResponse`.
- Added tests that differentiate:
  native output schema metadata on `ModelRequest`,
  workaround mode with `set_model_response`,
  and native structured result propagation when model capabilities allow schema
  and tools together.
- Updated `README.md` so the Kotlin-first surface documents the official ADK
  split between native structured output and workaround mode.

## Why These Changes

- Official ADK does not treat `set_model_response` as the universal solution.
  It is a fallback path only when tools and output schema cannot coexist for the
  current model path.
- Putting native schema metadata on `ModelRequest` lets future provider modules
  implement official behavior without inventing Kotlin-specific side channels.
- Returning validated structured data from `ModelResponse.Final` closes the loop
  so native structured output is useful end-to-end, not just at request build
  time.

## Verification

- `./gradlew test`
