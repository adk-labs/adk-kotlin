package dev.adk.kotlin

import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class FileSessionStore(
    private val rootDir: Path,
) : SessionStore {
    override suspend fun getOrCreate(
        appName: String,
        userId: String,
        sessionId: String?,
    ): AgentSession {
        val resolvedSessionId = sessionId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        return get(appName, userId, resolvedSessionId)
            ?: AgentSession(
                id = resolvedSessionId,
                userId = userId,
            ).also { save(appName, it) }
    }

    override suspend fun get(
        appName: String,
        userId: String,
        sessionId: String,
    ): AgentSession? {
        val file = sessionFile(appName, userId, sessionId)
        if (!Files.exists(file)) {
            return null
        }

        return ObjectInputStream(Files.newInputStream(file)).use { input ->
            input.readObject() as AgentSession
        }
    }

    override suspend fun save(
        appName: String,
        session: AgentSession,
    ) {
        val file = sessionFile(appName, session.userId, session.id)
        Files.createDirectories(file.parent)
        ObjectOutputStream(Files.newOutputStream(file)).use { output ->
            output.writeObject(session)
        }
    }

    override suspend fun list(
        appName: String,
        userId: String?,
    ): List<AgentSession> {
        val appDir = rootDir.resolve(encodeSegment(appName))
        if (!Files.isDirectory(appDir)) {
            return emptyList()
        }

        val userDirs =
            if (userId != null) {
                listOf(appDir.resolve(encodeSegment(userId)))
            } else {
                Files.list(appDir).use { stream -> stream.filter(Files::isDirectory).toList() }
            }

        return userDirs
            .filter { Files.isDirectory(it) }
            .flatMap { dir ->
                Files.list(dir).use { stream ->
                    stream
                        .filter { path -> path.fileName.toString().endsWith(".bin") }
                        .map { path ->
                            ObjectInputStream(Files.newInputStream(path)).use { input ->
                                input.readObject() as AgentSession
                            }
                        }.toList()
                }
            }.sortedBy { it.updatedAt }
    }

    override suspend fun delete(
        appName: String,
        userId: String,
        sessionId: String,
    ) {
        Files.deleteIfExists(sessionFile(appName, userId, sessionId))
    }

    private fun sessionFile(
        appName: String,
        userId: String,
        sessionId: String,
    ): Path =
        rootDir
            .resolve(encodeSegment(appName))
            .resolve(encodeSegment(userId))
            .resolve("${encodeSegment(sessionId)}.bin")

    private fun encodeSegment(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    @Suppress("unused")
    private fun decodeSegment(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)
}
