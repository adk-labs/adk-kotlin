package dev.adk.kotlin

import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

data class Artifact(
    val content: String,
    val version: Int = 0,
) : Serializable {
    override fun toString(): String = content
}

interface ArtifactService {
    suspend fun saveArtifact(
        appName: String,
        userId: String,
        sessionId: String? = null,
        filename: String,
        artifact: Artifact,
    ): Int

    suspend fun loadArtifact(
        appName: String,
        userId: String,
        sessionId: String? = null,
        filename: String,
        version: Int? = null,
    ): Artifact?

    suspend fun listArtifactKeys(
        appName: String,
        userId: String,
        sessionId: String? = null,
    ): List<String>

    suspend fun listVersions(
        appName: String,
        userId: String,
        sessionId: String? = null,
        filename: String,
    ): List<Int>

    suspend fun deleteArtifact(
        appName: String,
        userId: String,
        sessionId: String? = null,
        filename: String,
    )
}

class InMemoryArtifactService : ArtifactService {
    private val storage = ConcurrentHashMap<ArtifactScope, ConcurrentHashMap<String, MutableList<Artifact>>>()

    override suspend fun saveArtifact(
        appName: String,
        userId: String,
        sessionId: String?,
        filename: String,
        artifact: Artifact,
    ): Int {
        val scope = ArtifactScope(appName = appName, userId = userId, sessionId = sessionId)
        val versions =
            storage
                .computeIfAbsent(scope) { ConcurrentHashMap() }
                .computeIfAbsent(filename) { mutableListOf() }

        val version = versions.size
        versions += artifact.copy(version = version)
        return version
    }

    override suspend fun loadArtifact(
        appName: String,
        userId: String,
        sessionId: String?,
        filename: String,
        version: Int?,
    ): Artifact? {
        val scope = ArtifactScope(appName = appName, userId = userId, sessionId = sessionId)
        val versions = storage[scope]?.get(filename) ?: return null

        return if (version == null) {
            versions.lastOrNull()
        } else {
            versions.firstOrNull { artifact -> artifact.version == version }
        }
    }

    override suspend fun listArtifactKeys(
        appName: String,
        userId: String,
        sessionId: String?,
    ): List<String> {
        val userScoped =
            storage[ArtifactScope(appName = appName, userId = userId, sessionId = null)]
                ?.keys
                .orEmpty()
        val sessionScoped =
            sessionId
                ?.let { storage[ArtifactScope(appName = appName, userId = userId, sessionId = it)]?.keys.orEmpty() }
                .orEmpty()
        return (userScoped + sessionScoped).toSortedSet().toList()
    }

    override suspend fun listVersions(
        appName: String,
        userId: String,
        sessionId: String?,
        filename: String,
    ): List<Int> {
        val scope = ArtifactScope(appName = appName, userId = userId, sessionId = sessionId)
        return storage[scope]
            ?.get(filename)
            ?.map { it.version }
            .orEmpty()
            .sorted()
    }

    override suspend fun deleteArtifact(
        appName: String,
        userId: String,
        sessionId: String?,
        filename: String,
    ) {
        val scope = ArtifactScope(appName = appName, userId = userId, sessionId = sessionId)
        storage[scope]?.remove(filename)
    }

    private data class ArtifactScope(
        val appName: String,
        val userId: String,
        val sessionId: String?,
    )
}

class FileArtifactService(
    private val rootDir: Path,
) : ArtifactService {
    override suspend fun saveArtifact(
        appName: String,
        userId: String,
        sessionId: String?,
        filename: String,
        artifact: Artifact,
    ): Int {
        val versionsDir = versionsDir(appName, userId, sessionId, filename)
        Files.createDirectories(versionsDir)
        val version = listVersions(appName, userId, sessionId, filename).size
        ObjectOutputStream(Files.newOutputStream(versionsDir.resolve("$version.bin"))).use { output ->
            output.writeObject(artifact.copy(version = version))
        }
        return version
    }

    override suspend fun loadArtifact(
        appName: String,
        userId: String,
        sessionId: String?,
        filename: String,
        version: Int?,
    ): Artifact? {
        val versions = listVersions(appName, userId, sessionId, filename)
        if (versions.isEmpty()) {
            return null
        }

        val resolvedVersion = version ?: versions.max()
        val artifactFile = versionsDir(appName, userId, sessionId, filename).resolve("$resolvedVersion.bin")
        if (!Files.exists(artifactFile)) {
            return null
        }

        return ObjectInputStream(Files.newInputStream(artifactFile)).use { input ->
            input.readObject() as Artifact
        }
    }

    override suspend fun listArtifactKeys(
        appName: String,
        userId: String,
        sessionId: String?,
    ): List<String> {
        val keys = linkedSetOf<String>()
        keys += listArtifactKeysInScope(appName, userId, sessionId = null)
        if (sessionId != null) {
            keys += listArtifactKeysInScope(appName, userId, sessionId)
        }
        return keys.toList().sorted()
    }

    override suspend fun listVersions(
        appName: String,
        userId: String,
        sessionId: String?,
        filename: String,
    ): List<Int> {
        val versionsDir = versionsDir(appName, userId, sessionId, filename)
        if (!Files.isDirectory(versionsDir)) {
            return emptyList()
        }

        return Files.list(versionsDir).use { stream ->
            stream
                .map { path -> path.fileName.toString().removeSuffix(".bin").toIntOrNull() }
                .filter { it != null }
                .map { it!! }
                .toList()
                .sorted()
        }
    }

    override suspend fun deleteArtifact(
        appName: String,
        userId: String,
        sessionId: String?,
        filename: String,
    ) {
        val versionsDir = versionsDir(appName, userId, sessionId, filename)
        if (!Files.exists(versionsDir)) {
            return
        }

        Files.walk(versionsDir)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::deleteIfExists)
    }

    private fun listArtifactKeysInScope(
        appName: String,
        userId: String,
        sessionId: String?,
    ): List<String> {
        val scopeDir = scopeDir(appName, userId, sessionId)
        if (!Files.isDirectory(scopeDir)) {
            return emptyList()
        }

        return Files.list(scopeDir).use { stream ->
            stream
                .filter(Files::isDirectory)
                .map { decodeSegment(it.fileName.toString()) }
                .toList()
                .sorted()
        }
    }

    private fun versionsDir(
        appName: String,
        userId: String,
        sessionId: String?,
        filename: String,
    ): Path = scopeDir(appName, userId, sessionId).resolve(encodeSegment(filename))

    private fun scopeDir(
        appName: String,
        userId: String,
        sessionId: String?,
    ): Path {
        val base = rootDir.resolve(encodeSegment(appName)).resolve(encodeSegment(userId))
        return sessionId?.let { base.resolve("sessions").resolve(encodeSegment(it)) } ?: base.resolve("user")
    }

    private fun encodeSegment(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun decodeSegment(value: String): String = java.net.URLDecoder.decode(value, StandardCharsets.UTF_8)
}
