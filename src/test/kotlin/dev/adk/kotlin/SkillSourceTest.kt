package dev.adk.kotlin

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SkillSourceTest {
    @Test
    fun `frontmatter parses official fields and metadata`() {
        val frontmatter =
            Frontmatter.fromYaml(
                """
                name: test-skill-metadata
                description: Test with metadata
                allowed-tools: "tool1 tool2"
                compatibility: "1.0"
                metadata:
                  key1: value1
                  key2: 123
                """.trimIndent(),
            )

        assertEquals("test-skill-metadata", frontmatter.name)
        assertEquals("Test with metadata", frontmatter.description)
        assertEquals("tool1 tool2", frontmatter.allowedTools)
        assertEquals("1.0", frontmatter.compatibility)
        assertEquals("value1", frontmatter.metadata["key1"])
        assertEquals(123, frontmatter.metadata["key2"])
    }

    @Test
    fun `frontmatter validates name shape and length`() {
        val invalidName =
            assertFailsWith<IllegalArgumentException> {
                Frontmatter.builder().name("Invalid_Name").description("test").build()
            }
        assertTrue(invalidName.message.orEmpty().contains("lowercase kebab-case"))

        val longName =
            assertFailsWith<IllegalArgumentException> {
                Frontmatter.builder().name("a".repeat(65)).description("test").build()
            }
        assertTrue(longName.message.orEmpty().contains("must be at most 64 characters"))
    }

    @Test
    fun `in-memory source lists frontmatters resources and content`() =
        runTest {
            val source =
                InMemorySkillSource
                    .builder()
                    .skill("skill-1")
                    .frontmatter(Frontmatter.builder().name("skill-1").description("desc1").build())
                    .instructions("body1")
                    .skill("skill-2")
                    .frontmatter(Frontmatter.builder().name("skill-2").description("desc2").build())
                    .instructions("body2")
                    .addResource("assets/file1.txt", "content1")
                    .addResource("assets/subdir/file2.txt", "content2")
                    .addResource("other/file3.txt", "content3")
                    .build()

            assertEquals(setOf("skill-1", "skill-2"), source.listFrontmatters().keys)
            assertEquals("body1", source.loadInstructions("skill-1"))
            assertEquals(
                listOf("assets/file1.txt", "assets/subdir/file2.txt"),
                source.listResources("skill-2", "assets"),
            )
            assertEquals(
                "content1",
                String(source.loadResource("skill-2", "assets/file1.txt"), StandardCharsets.UTF_8),
            )
        }

    @Test
    fun `in-memory source reports missing skills resources and builder fields`() =
        runTest {
            val source = InMemorySkillSource.builder().build()
            assertFailsWith<SkillSourceException> { source.loadFrontmatter("non-existent") }

            val configured =
                InMemorySkillSource
                    .builder()
                    .skill("my-skill")
                    .frontmatter(Frontmatter.builder().name("my-skill").description("desc").build())
                    .instructions("body")
                    .addResource("assets/file1.txt", "content1")
                    .build()

            assertFailsWith<SkillSourceException> {
                configured.listResources("my-skill", "non-existent")
            }
            assertFailsWith<SkillSourceException> {
                configured.loadResource("my-skill", "missing.txt")
            }
            assertFailsWith<IllegalStateException> {
                InMemorySkillSource.builder().skill("missing-frontmatter").instructions("body").build()
            }
            assertFailsWith<IllegalStateException> {
                InMemorySkillSource
                    .builder()
                    .skill("missing-instructions")
                    .frontmatter(Frontmatter.builder().name("missing-instructions").description("desc").build())
                    .build()
            }
        }

    @Test
    fun `local source loads frontmatters instructions and resources`() =
        runTest {
            val skillsBase = Files.createTempDirectory("adk-kotlin-skills")
            val skillOne = Files.createDirectories(skillsBase.resolve("skill-1"))
            Files.writeString(
                skillOne.resolve("SKILL.md"),
                """
                ---
                name: skill-1
                description: test1
                ---
                Body one
                """.trimIndent(),
            )
            val skillTwo = Files.createDirectories(skillsBase.resolve("skill-2"))
            Files.writeString(
                skillTwo.resolve("skill.md"),
                """
                ---
                name: skill-2
                description: test2
                ---
                Body two
                """.trimIndent(),
            )
            val assetsDir = Files.createDirectories(skillTwo.resolve("assets").resolve("subdir"))
            Files.writeString(skillTwo.resolve("assets").resolve("file1.txt"), "hello content")
            Files.writeString(assetsDir.resolve("file2.txt"), "nested content")

            val source = LocalSkillSource(skillsBase)
            val frontmatters = source.listFrontmatters()

            assertEquals(setOf("skill-1", "skill-2"), frontmatters.keys)
            assertEquals("test1", frontmatters.getValue("skill-1").description)
            assertEquals("Body two", source.loadInstructions("skill-2"))
            assertEquals(
                listOf("assets/file1.txt", "assets/subdir/file2.txt"),
                source.listResources("skill-2", "assets"),
            )
            assertEquals(
                "hello content",
                String(source.loadResource("skill-2", "assets/file1.txt"), StandardCharsets.UTF_8),
            )
        }

    @Test
    fun `local source reports malformed and missing skill files`() =
        runTest {
            val skillsBase = Files.createTempDirectory("adk-kotlin-skills")
            val skillDir = Files.createDirectories(skillsBase.resolve("my-skill"))
            Files.writeString(
                skillDir.resolve("SKILL.md"),
                """
                ---
                name: my-skill
                description: Test
                Some Markdown Body without closing dashes
                """.trimIndent(),
            )

            val source = LocalSkillSource(skillsBase)
            val exception = assertFailsWith<SkillSourceException> { source.loadInstructions("my-skill") }
            assertTrue(exception.message.orEmpty().contains("frontmatter not properly closed"))

            assertFailsWith<SkillSourceException> { source.loadFrontmatter("non-existent") }
            assertFailsWith<SkillSourceException> { source.listResources("non-existent", "assets") }
            assertFailsWith<SkillSourceException> { source.loadResource("my-skill", "missing.txt") }
        }

    @Test
    fun `local source enforces frontmatter name matches directory`() =
        runTest {
            val skillsBase = Files.createTempDirectory("adk-kotlin-skills")
            val skillDir = Files.createDirectories(skillsBase.resolve("directory-name"))
            Files.writeString(
                skillDir.resolve("SKILL.md"),
                """
                ---
                name: other-name
                description: Test
                ---
                Body
                """.trimIndent(),
            )

            val source = LocalSkillSource(skillsBase)
            val exception = assertFailsWith<SkillSourceException> { source.loadFrontmatter("directory-name") }

            assertTrue(exception.message.orEmpty().contains("does not match directory name"))
        }
}
