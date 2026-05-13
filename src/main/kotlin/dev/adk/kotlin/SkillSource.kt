package dev.adk.kotlin

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exception used by [SkillSource] implementations for recoverable loading
 * errors whose message can be surfaced back to the model.
 */
class SkillSourceException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * YAML frontmatter metadata from a `SKILL.md` file.
 *
 * The public field names mirror the official ADK Java `Frontmatter` model while
 * keeping Kotlin construction concise.
 */
data class Frontmatter(
    val name: String,
    val description: String,
    val license: String? = null,
    val compatibility: String? = null,
    val allowedTools: String? = null,
    val metadata: Map<String, Any?> = emptyMap(),
) {
    init {
        require(name.length <= 64) { "name must be at most 64 characters" }
        require(NAME_PATTERN.matches(name)) {
            "name must be lowercase kebab-case (a-z, 0-9, hyphens), with no leading, trailing, or consecutive hyphens"
        }
        require(description.isNotEmpty()) { "description must not be empty" }
        require(description.length <= 1024) { "description must be at most 1024 characters" }
        require(compatibility == null || compatibility.length <= 500) {
            "compatibility must be at most 500 characters"
        }
    }

    fun toXml(): String =
        """
        <skill>
        <name>
        ${name.escapeXml()}
        </name>
        <description>
        ${description.escapeXml()}
        </description>
        </skill>
        """.trimIndent()

    companion object {
        private val NAME_PATTERN = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

        @JvmStatic
        fun builder(): Builder = Builder()

        fun fromYaml(yaml: String): Frontmatter = FrontmatterYamlParser.parse(yaml)
    }

    class Builder {
        private var name: String? = null
        private var description: String? = null
        private var license: String? = null
        private var compatibility: String? = null
        private var allowedTools: String? = null
        private var metadata: Map<String, Any?> = emptyMap()

        fun name(value: String): Builder = apply { name = value }

        fun description(value: String): Builder = apply { description = value }

        fun license(value: String?): Builder = apply { license = value }

        fun compatibility(value: String?): Builder = apply { compatibility = value }

        fun allowedTools(value: String?): Builder = apply { allowedTools = value }

        fun metadata(value: Map<String, Any?>): Builder = apply { metadata = value.toMap() }

        fun build(): Frontmatter =
            Frontmatter(
                name = requireNotNull(name) { "name is required" },
                description = requireNotNull(description) { "description is required" },
                license = license,
                compatibility = compatibility,
                allowedTools = allowedTools,
                metadata = metadata.toMap(),
            )
    }
}

/**
 * Source for discovering skill metadata, instructions, and resource files.
 */
interface SkillSource {
    suspend fun listFrontmatters(): Map<String, Frontmatter>

    suspend fun listResources(
        skillName: String,
        resourceDirectory: String,
    ): List<String>

    suspend fun loadFrontmatter(skillName: String): Frontmatter

    suspend fun loadInstructions(skillName: String): String

    suspend fun loadResource(
        skillName: String,
        resourcePath: String,
    ): ByteArray
}

abstract class AbstractSkillSource<PathT> : SkillSource {
    protected data class SkillMdPath<PathT>(
        val name: String,
        val mdPath: PathT,
    )

    override suspend fun listFrontmatters(): Map<String, Frontmatter> =
        listSkills().associate { skillMdPath ->
            val frontmatter = loadFrontmatter(skillMdPath.name, skillMdPath.mdPath)
            frontmatter.name to frontmatter
        }

    override suspend fun loadFrontmatter(skillName: String): Frontmatter =
        loadFrontmatter(skillName, findSkillMdPath(skillName))

    override suspend fun loadInstructions(skillName: String): String {
        val skillMdPath = findSkillMdPath(skillName)
        return readInstructions(readText(skillMdPath))
    }

    override suspend fun loadResource(
        skillName: String,
        resourcePath: String,
    ): ByteArray = readBytes(findResourcePath(skillName, resourcePath))

    protected abstract suspend fun listSkills(): List<SkillMdPath<PathT>>

    protected abstract suspend fun findSkillMdPath(skillName: String): PathT

    protected abstract suspend fun findResourcePath(
        skillName: String,
        resourcePath: String,
    ): PathT

    protected abstract suspend fun readBytes(path: PathT): ByteArray

    private suspend fun readText(path: PathT): String = String(readBytes(path), StandardCharsets.UTF_8)

    private suspend fun loadFrontmatter(
        skillName: String,
        skillMdPath: PathT,
    ): Frontmatter {
        val frontmatter =
            try {
                Frontmatter.fromYaml(readFrontmatterYaml(readText(skillMdPath)))
            } catch (e: SkillSourceException) {
                throw e
            } catch (e: Exception) {
                throw SkillSourceException("Cannot load frontmatter for skill '$skillName'", e)
            }

        if (frontmatter.name != skillName) {
            throw SkillSourceException(
                "Skill name '${frontmatter.name}' does not match directory name '$skillName'.",
            )
        }
        return frontmatter
    }

    private fun readFrontmatterYaml(text: String): String {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != THREE_DASHES) {
            throw SkillSourceException("Skill file must start with $THREE_DASHES")
        }

        val yaml = StringBuilder()
        for (line in lines.drop(1)) {
            if (line.trim() == THREE_DASHES) {
                return yaml.toString()
            }
            yaml.append(line).append('\n')
        }
        throw SkillSourceException("Skill file frontmatter not properly closed with $THREE_DASHES")
    }

    private fun readInstructions(text: String): String {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != THREE_DASHES) {
            throw SkillSourceException("Skill file must start with $THREE_DASHES")
        }

        var bodyStartIndex = -1
        for (index in 1 until lines.size) {
            if (lines[index].trim() == THREE_DASHES) {
                bodyStartIndex = index + 1
                break
            }
        }
        if (bodyStartIndex == -1) {
            throw SkillSourceException("Skill file frontmatter not properly closed with $THREE_DASHES")
        }

        return lines.drop(bodyStartIndex).joinToString("\n").trim()
    }

    private companion object {
        const val THREE_DASHES = "---"
    }
}

class InMemorySkillSource private constructor(
    private val skills: Map<String, SkillData>,
) : SkillSource {
    override suspend fun listFrontmatters(): Map<String, Frontmatter> =
        skills.mapValues { (_, data) -> data.frontmatter }

    override suspend fun listResources(
        skillName: String,
        resourceDirectory: String,
    ): List<String> {
        val data = skillData(skillName)
        val prefix =
            if (resourceDirectory.isEmpty()) {
                ""
            } else if (resourceDirectory.endsWith("/")) {
                resourceDirectory
            } else {
                "$resourceDirectory/"
            }

        if (resourceDirectory.isNotEmpty() && data.resources.keys.none { path -> path.startsWith(prefix) }) {
            throw SkillSourceException("Resource directory not found: $resourceDirectory for skill: $skillName")
        }

        return data.resources.keys.filter { path -> path.startsWith(prefix) }
    }

    override suspend fun loadFrontmatter(skillName: String): Frontmatter = skillData(skillName).frontmatter

    override suspend fun loadInstructions(skillName: String): String = skillData(skillName).instructions

    override suspend fun loadResource(
        skillName: String,
        resourcePath: String,
    ): ByteArray =
        skillData(skillName).resources[resourcePath]?.copyOf()
            ?: throw SkillSourceException("Resource not found: $resourcePath")

    private fun skillData(skillName: String): SkillData =
        skills[skillName] ?: throw SkillSourceException("Skill not found: $skillName")

    class Builder {
        private val skillBuilders = linkedMapOf<String, SkillBuilder>()

        fun skill(name: String): SkillBuilder =
            skillBuilders.getOrPut(name) {
                SkillBuilder(owner = this)
            }

        fun build(): InMemorySkillSource =
            InMemorySkillSource(
                skillBuilders.mapValuesTo(linkedMapOf()) { (_, builder) -> builder.buildSkillData() },
            )

        class SkillBuilder internal constructor(
            private val owner: Builder,
        ) {
            private var frontmatter: Frontmatter? = null
            private var instructions: String? = null
            private val resources = linkedMapOf<String, ByteArray>()

            fun frontmatter(value: Frontmatter): SkillBuilder =
                apply {
                    frontmatter = value
                }

            fun instructions(value: String): SkillBuilder =
                apply {
                    instructions = value
                }

            fun addResource(
                path: String,
                content: ByteArray,
            ): SkillBuilder =
                apply {
                    resources[path] = content.copyOf()
                }

            fun addResource(
                path: String,
                content: String,
            ): SkillBuilder = addResource(path, content.toByteArray(StandardCharsets.UTF_8))

            fun skill(name: String): SkillBuilder = owner.skill(name)

            fun build(): InMemorySkillSource = owner.build()

            internal fun buildSkillData(): SkillData =
                SkillData(
                    frontmatter = checkNotNull(frontmatter) { "Frontmatter is required" },
                    instructions = checkNotNull(instructions) { "Instructions are required" },
                    resources = resources.mapValuesTo(linkedMapOf()) { (_, value) -> value.copyOf() },
                )
        }
    }

    internal data class SkillData(
        val frontmatter: Frontmatter,
        val instructions: String,
        val resources: Map<String, ByteArray>,
    )

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}

class LocalSkillSource(
    private val skillsBasePath: Path,
) : AbstractSkillSource<Path>() {
    override suspend fun listResources(
        skillName: String,
        resourceDirectory: String,
    ): List<String> {
        val skillDir = skillsBasePath.resolve(skillName)
        if (!Files.isDirectory(skillDir)) {
            throw SkillSourceException("Skill not found: $skillName")
        }
        val resourceDir = skillDir.resolve(resourceDirectory)
        if (!Files.isDirectory(resourceDir)) {
            throw SkillSourceException("Resource directory '$resourceDirectory' not found for skill '$skillName'")
        }

        return try {
            val paths = Files.walk(resourceDir)
            try {
                paths
                    .filter(Files::isRegularFile)
                    .map { path -> skillDir.relativize(path).toString().replace('\\', '/') }
                    .sorted()
                    .toList()
            } finally {
                paths.close()
            }
        } catch (e: Exception) {
            throw SkillSourceException("Failed to traverse resource directory: $resourceDirectory", e)
        }
    }

    override suspend fun listSkills(): List<SkillMdPath<Path>> =
        try {
            val paths = Files.list(skillsBasePath)
            try {
                paths
                    .filter(Files::isDirectory)
                    .map { dir -> findSkillMd(dir)?.let { SkillMdPath(dir.fileName.toString(), it) } }
                    .filter { it != null }
                    .map { requireNotNull(it) }
                    .sorted { left, right -> left.name.compareTo(right.name) }
                    .toList()
            } finally {
                paths.close()
            }
        } catch (e: Exception) {
            throw SkillSourceException("Failed to list skills in directory: $skillsBasePath", e)
        }

    override suspend fun findSkillMdPath(skillName: String): Path {
        val skillDir = skillsBasePath.resolve(skillName)
        if (!Files.isDirectory(skillDir)) {
            throw SkillSourceException("Skill directory not found: $skillName")
        }
        return findSkillMd(skillDir) ?: throw SkillSourceException("SKILL.md not found in $skillName")
    }

    override suspend fun findResourcePath(
        skillName: String,
        resourcePath: String,
    ): Path {
        val file = skillsBasePath.resolve(skillName).resolve(resourcePath)
        if (!Files.exists(file)) {
            throw SkillSourceException("Resource not found: $file")
        }
        return file
    }

    override suspend fun readBytes(path: Path): ByteArray = Files.readAllBytes(path)

    private fun findSkillMd(dir: Path): Path? =
        dir.resolve("SKILL.md").takeIf(Files::exists)
            ?: dir.resolve("skill.md").takeIf(Files::exists)
}

private object FrontmatterYamlParser {
    fun parse(yaml: String): Frontmatter {
        val values = linkedMapOf<String, Any?>()
        val metadata = linkedMapOf<String, Any?>()
        var section: String? = null

        yaml.lineSequence().forEach { rawLine ->
            if (rawLine.isBlank() || rawLine.trimStart().startsWith("#")) {
                return@forEach
            }

            if (rawLine.startsWith(" ") || rawLine.startsWith("\t")) {
                if (section == "metadata") {
                    val (key, value) = parseKeyValue(rawLine.trim())
                    metadata[key] = parseScalar(value)
                }
                return@forEach
            }

            val (key, value) = parseKeyValue(rawLine.trim())
            section = null
            if (key == "metadata" && value.isBlank()) {
                section = "metadata"
            } else {
                values[key] = parseScalar(value)
            }
        }

        return Frontmatter(
            name = values["name"] as? String ?: throw IllegalArgumentException("name is required"),
            description = values["description"] as? String ?: throw IllegalArgumentException("description is required"),
            license = values["license"] as? String,
            compatibility = values["compatibility"] as? String,
            allowedTools = (values["allowed-tools"] ?: values["allowed_tools"]) as? String,
            metadata = metadata.toMap(),
        )
    }

    private fun parseKeyValue(line: String): Pair<String, String> {
        val separator = line.indexOf(':')
        require(separator >= 0) { "Invalid YAML frontmatter line: $line" }
        return line.substring(0, separator).trim() to line.substring(separator + 1).trim()
    }

    private fun parseScalar(rawValue: String): Any? {
        if (rawValue.isEmpty() || rawValue == "null" || rawValue == "~") {
            return null
        }
        if (
            (rawValue.startsWith('"') && rawValue.endsWith('"')) ||
            (rawValue.startsWith('\'') && rawValue.endsWith('\''))
        ) {
            return rawValue.substring(1, rawValue.length - 1)
        }
        return when {
            rawValue == "true" -> true
            rawValue == "false" -> false
            rawValue.matches(Regex("-?\\d+")) -> rawValue.toIntOrNull() ?: rawValue.toLongOrNull() ?: rawValue
            rawValue.matches(Regex("-?\\d+\\.\\d+")) -> rawValue.toDoubleOrNull() ?: rawValue
            else -> rawValue
        }
    }
}

private fun String.escapeXml(): String =
    buildString(length) {
        this@escapeXml.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }
