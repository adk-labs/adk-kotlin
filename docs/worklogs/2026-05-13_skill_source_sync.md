# 2026-05-13 Skill Source Sync

## Context

The latest `adk-java` reference added the `com.google.adk.skills` package with
`SkillSource`, `Frontmatter`, `InMemorySkillSource`, `LocalSkillSource`, and
`SkillSourceException`. `adk-kotlin` did not have this module, so official skill
loading APIs were still missing.

## Work

- Add a Kotlin-first `SkillSource` API with method names aligned to the official
  Java surface: `listFrontmatters`, `listResources`, `loadFrontmatter`,
  `loadInstructions`, and `loadResource`.
- Add `Frontmatter` validation and XML rendering compatible with the official
  Agents Skills frontmatter fields.
- Add `InMemorySkillSource` for test and embedded skill loading.
- Add `LocalSkillSource` for filesystem-backed `SKILL.md`/`skill.md` loading,
  instruction extraction, and resource access.
- Add tests that mirror the official Java behavior for frontmatter parsing,
  missing skills/resources, unclosed frontmatter, and builder validation.
- Refresh the official gap matrix so the skills row no longer reports the whole
  area as missing.

## Reason

Skills are now part of the official JVM baseline and are used as a first-class
resource/instruction loading mechanism. Porting this as pure Kotlin keeps
`adk-kotlin` aligned with `adk-java` without wrapping Java classes, while still
using idiomatic suspending functions and standard Kotlin/JDK types.
