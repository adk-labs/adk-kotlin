# 2026-03-22 Tool Schema Foundation

## Planned Work

- Add a structured tool-schema model to `adk-kotlin` instead of relying only on
  free-form strings and flat parameter lists.
- Extend tool declarations with richer official-style metadata such as long
  running flags, confirmation flags, and custom metadata.
- Update internal runner/prompt tool declarations so transfer and structured
  output tools use the same schema surface.
- Add tests for schema DSL behavior and internal tool schema generation.

## Why This Work

- Official ADK tools are declared through structured function schemas, not only
  string descriptions.
- The current Kotlin tool surface is too thin for later work on toolsets,
  built-in tools, confirmation flows, and external tool adapters.
- This layer needs to exist before session/artifact/memory modules can expose
  richer tool-backed runtime features without inventing Kotlin-only metadata.

## Files Expected To Change

- `src/main/kotlin/dev/adk/kotlin/Tool.kt`
- `src/main/kotlin/dev/adk/kotlin/PromptAssembler.kt`
- `src/test/kotlin/dev/adk/kotlin/PromptAssemblerTest.kt`
- New tool schema model file(s)
- `README.md`

## Verification Plan

- Run `./gradlew test`
- Commit and push this work unit after verification

## Completed Work

- Added `ToolSchema`, `ToolSchemaType`, and `toolSchema {}` so Kotlin now has a
  structured tool declaration surface instead of only string schema text.
- Extended `ToolDefinition` with richer official-style metadata:
  `jsonSchema`,
  `isLongRunning`,
  `requiresConfirmation`,
  and `customMetadata`.
- Added `effectiveJsonSchema` so existing flat `parameters` declarations are
  automatically promoted into structured object schemas instead of being lost.
- Updated `PromptAssembler` so internal `transfer_to_agent` and
  `set_model_response` declarations now expose structured schemas through the
  same tool definition surface as user tools.
- Added tests covering:
  schema DSL object generation,
  legacy parameter promotion,
  transfer tool schema generation,
  and `set_model_response` schema generation.
- Updated `README.md` with the new tool schema DSL and richer tool declaration
  metadata.

## Why These Changes

- Official ADK tools are declared through structured schemas. Kotlin needed the
  same substrate before moving on to toolsets, confirmations, or external tool
  adapters.
- Promoting legacy `parameters` into structured schemas preserves current
  ergonomics while moving the core runtime toward the official declaration
  model.
- Internal tools using the same schema surface as user tools reduces future
  divergence in how tools are represented and inspected.

## Verification

- `./gradlew test`
