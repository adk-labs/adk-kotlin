# 2026-04-27 official prompt alignment

## What I worked on

- Reviewed Kotlin prompt assembly against `adk-python` and `adk-java`.
- Selected `adk-python` as the wording source of truth where the official SDKs differ.
- Planned fixes for transfer instructions and global instruction whitespace handling.

## Why this work was needed

- Agent system prompts must behave like the official ADK runtime, not just approximate the intent.
- The identity and output schema workaround prompts already matched official wording, but transfer wording and global instruction handling still had exact-behavior gaps.
- Preserving official prompt text and whitespace is important because small prompt differences can affect model behavior.

## Planned result for this work unit

- Make transfer system instructions match the official `adk-python` text shape.
- Preserve global instruction whitespace instead of trimming user-provided or provider-provided instructions.
- Add regression tests that assert the official prompt wording and whitespace behavior.

## Result

- Updated transfer prompt wording and line breaks to match `adk-python`.
- Preserved prompt-facing DSL text for global instruction, dynamic instruction, static instruction, and description.
- Preserved `GlobalInstructionPlugin` input/provider whitespace instead of trimming it.
- Kept identity and output-schema workaround wording unchanged because they already matched official ADK.

## Verification

- Ran `./gradlew test`.
