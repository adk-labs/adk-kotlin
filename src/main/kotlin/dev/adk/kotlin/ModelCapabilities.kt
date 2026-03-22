package dev.adk.kotlin

data class ModelCapabilities(
    val supportsOutputSchemaWithTools: Boolean = false,
)

interface SupportsModelCapabilities {
    val modelCapabilities: ModelCapabilities
}

interface SupportsPerModelCapabilities {
    fun modelCapabilities(modelName: String): ModelCapabilities
}
