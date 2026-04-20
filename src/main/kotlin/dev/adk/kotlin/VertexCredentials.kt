package dev.adk.kotlin

data class VertexCredentials(
    val project: String? = null,
    val location: String? = null,
    val credentials: Any? = null,
) {
    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var project: String? = null
        private var location: String? = null
        private var credentials: Any? = null

        @Deprecated("Use project(value) instead.")
        fun setProject(value: String?): Builder = project(value)

        fun project(value: String?): Builder =
            apply {
                project = value?.trim()?.takeIf { it.isNotEmpty() }
            }

        @Deprecated("Use location(value) instead.")
        fun setLocation(value: String?): Builder = location(value)

        fun location(value: String?): Builder =
            apply {
                location = value?.trim()?.takeIf { it.isNotEmpty() }
            }

        @Deprecated("Use credentials(value) instead.")
        fun setCredentials(value: Any?): Builder = credentials(value)

        fun credentials(value: Any?): Builder =
            apply {
                credentials = value
            }

        fun build(): VertexCredentials =
            VertexCredentials(
                project = project,
                location = location,
                credentials = credentials,
            )
    }
}
