package dev.adk.kotlin

import java.util.concurrent.ConcurrentHashMap

data class Artifact(
    val content: String,
    val version: Int = 0,
) {
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

    private data class ArtifactScope(
        val appName: String,
        val userId: String,
        val sessionId: String?,
    )
}
