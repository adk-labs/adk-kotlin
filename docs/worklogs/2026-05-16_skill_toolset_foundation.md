# 2026-05-16 Skill Toolset Foundation

## Context

The latest `adk-python` refresh keeps `SkillToolset` as the primary runtime
surface for discovering and loading skills. `adk-kotlin` already had
`SkillSource`, but agents still had no official-style tools for listing skills,
loading instructions, or reading skill resources.

## Work

- Add `SkillToolset` built on the existing pure Kotlin `SkillSource`.
- Add official-name tools: `list_skills`, `load_skill`, and
  `load_skill_resource`.
- Inject the official skill system instruction wording into LLM requests.
- Format available skill frontmatter as XML using the existing `Frontmatter`
  renderer.
- Record loaded skills in agent state using the official
  `_adk_activated_skill_<agent>` key shape.
- Add tests for tool discovery, request preprocessing, skill loading, resource
  loading, and missing-skill errors.
- Refresh the gap matrix so skills are represented as a runtime toolset, not
  only a source/loading substrate.

## Reason

`SkillSource` alone only closes the data-loading gap. The official SDK exposes
skills to agents through tools and prompt instructions, so Kotlin needs this
toolset layer for behavior parity without wrapping Java or Python code.
