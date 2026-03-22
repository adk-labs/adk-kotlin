# 2026-03-22 Output Schema Alignment

## Why This Unit Exists

The Kotlin SDK can already align instructions and transfer semantics with the
official ADK runtime, but it still lacks the internal `set_model_response`
workaround used when an agent combines output schema requirements with other
tools. Without that behavior, structured outputs in Kotlin would diverge from
the official libraries at exactly the point where final answer shape matters.

## Scope

- Add output schema support to Kotlin agents.
- Inject the internal `set_model_response` tool when an agent uses both output
  schema and tools.
- Add the official output-schema instruction text for the workaround path.
- Let the runner recognize `set_model_response` as the terminal structured
  response signal.
- Expose the structured response on the Kotlin run result.
- Add tests for prompt assembly and final structured response handling.

## Why This Shape

- The workaround is runtime semantics, not convenience sugar, so it belongs in
  core instead of individual model adapters.
- Structured output without the official internal tool would push provider
  quirks into application code and break cross-language behavior parity.
- Returning the structured response explicitly from the Kotlin runner keeps the
  SDK idiomatic while still matching the official flow.

## Completed Work

- Added output schema support to Kotlin agents.
- Added the internal `set_model_response` tool when an agent combines tools and
  output schema.
- Added the official workaround instruction text for the structured-output path.
- Extended the runner to treat `set_model_response` as the terminal structured
  result.
- Exposed structured responses on the Kotlin `RunResult`.
- Added tests for request assembly and final structured response handling.
- Updated README to mention the new structured-output semantics.

## Non-Goals

This unit does not yet cover:

- Provider-specific checks for models that natively support output schema with
  tools.
- Full JSON Schema expressiveness.
- Artifact-aware instruction interpolation.

## Exit Criteria

This unit is complete when Kotlin agents can declare an output schema, the
model request exposes `set_model_response` and the official instruction text
when needed, the runner treats the tool call as the final structured result,
and tests cover the workaround path.

## Verification

- `./gradlew test`
