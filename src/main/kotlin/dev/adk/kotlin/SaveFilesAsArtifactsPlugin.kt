package dev.adk.kotlin

class SaveFilesAsArtifactsPlugin(
    name: String = "save_files_as_artifacts_plugin",
) : BasePlugin(name) {
    override suspend fun onUserMessageCallback(
        invocationContext: InvocationContext,
        userMessage: UserMessage,
    ): UserMessage? {
        if (userMessage.attachments.isEmpty()) {
            return null
        }

        val artifactDelta = linkedMapOf<String, Int>()
        val placeholders = mutableListOf<String>()

        userMessage.attachments.forEachIndexed { index, attachment ->
            val displayName = attachment.filename.ifBlank { "artifact_${invocationContext.invocationId}_$index" }
            val artifactName =
                when {
                    attachment.scope == AttachmentScope.USER && displayName.startsWith("user:") -> displayName
                    attachment.scope == AttachmentScope.USER -> "user:$displayName"
                    else -> displayName
                }
            val sessionId =
                when (attachment.scope) {
                    AttachmentScope.SESSION -> invocationContext.session.id
                    AttachmentScope.USER -> null
                }

            val version =
                invocationContext.artifactService.saveArtifact(
                    appName = invocationContext.app.name,
                    userId = invocationContext.userId,
                    sessionId = sessionId,
                    filename = artifactName,
                    artifact =
                        Artifact(
                            content = attachment.content,
                            mimeType = attachment.mimeType,
                        ),
                )
            artifactDelta[artifactName] = version
            placeholders += "[Uploaded Artifact: \"$displayName\"]"
        }

        val rewrittenText =
            listOf(
                userMessage.text.trim(),
                placeholders.joinToString("\n"),
            ).filter(String::isNotBlank)
                .joinToString("\n")

        return userMessage.copy(
            text = rewrittenText,
            attachments = emptyList(),
            artifactDelta = artifactDelta.toMap(),
        )
    }
}

fun saveFilesAsArtifactsPlugin(
    name: String = "save_files_as_artifacts_plugin",
): SaveFilesAsArtifactsPlugin = SaveFilesAsArtifactsPlugin(name = name)
