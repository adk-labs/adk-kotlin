# 2026-03-22 Prompt And Transfer Alignment

## Why This Unit Exists

The first foundation unit established a Kotlin-first API surface, but the
prompt assembly semantics still diverge from the official ADK implementations.
That gap is especially visible in system instruction handling and agent
transfer. The official libraries do not rely on a single ad-hoc prompt string;
they build model requests by layering global instructions, agent instructions,
state injection, and transfer instructions in a specific order.

This unit reduces that gap by aligning the Kotlin SDK with the official ADK
behavior where it matters most for model behavior.

## Scope

- Replace the custom system prompt renderer with official-style instruction
  assembly semantics.
- Support application-level global instructions and per-agent instructions.
- Inject session state placeholders into instruction templates using the same
  placeholder rules as the official libraries.
- Add official transfer instructions text and built-in `transfer_to_agent`
  tool exposure.
- Add agent tree semantics needed for parent, peer, and child transfers.
- Extend the runner so an active agent can hand off control to another agent.
- Add tests that lock the prompt text and transfer behavior to the aligned
  semantics.

## Why This Shape

- Prompt assembly is behavior-critical. If the Kotlin SDK improvises here, it
  will diverge from the official libraries even when the agent definition looks
  similar.
- The transfer instruction string is effectively part of the runtime contract,
  so it should be preserved verbatim instead of paraphrased.
- Agent tree traversal and transfer rules are foundational enough that later
  provider integrations should build on them rather than retrofit them.

## Completed Work

- Replaced the custom prompt renderer with official-style instruction assembly.
- Added application-level global instructions and per-agent instruction
  templates.
- Added session-state placeholder injection for instruction templates using the
  official placeholder rules for state variables.
- Added framework identity instructions to every request.
- Added official transfer instructions text and built-in
  `transfer_to_agent` tool exposure with constrained agent names.
- Extended the runner so model control can transfer from one agent to another
  within the app tree.
- Added tests for instruction order, dynamic instruction placement, and agent
  handoff.
- Updated the README to explain the aligned runtime semantics.

## Non-Goals

This unit still does not attempt full ADK parity. It does not cover:

- Artifact-backed instruction injection.
- Dev UI integrations.
- Persistent sessions or memory services.
- Full event/action graphs from the official runtime.

## Exit Criteria

This unit is complete when the Kotlin SDK assembles instructions using the same
ordering rules as the official libraries for the currently supported features,
the transfer instructions match the official text, the runner can transfer
between agents in the tree, and the new behavior is covered by tests.

## Verification

- `./gradlew test`
