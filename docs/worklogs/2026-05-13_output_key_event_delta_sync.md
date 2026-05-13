# 2026-05-13 Output Key Event Delta Sync

## Context

The latest `adk-python` saves an agent `output_key` result into
`event.actions.state_delta` when the final response belongs to the same agent.
`adk-kotlin` already persisted `outputKey` into the working session state, but
the emitted final event only exposed the updated `agentState`.

## Work

- Return an output-key state delta when final LLM output is persisted.
- Attach that delta to final model response events and `set_model_response`
  final events.
- Preserve existing session-state behavior so Kotlin callers continue reading
  `result.session.state[outputKey]`.
- Add runner tests to ensure final events carry the output-key state delta.

## Reason

Official ADK consumers can replay or inspect event deltas to understand state
changes. Emitting the output-key save only in `agentState` made Kotlin event
streams less faithful than `adk-python`.
