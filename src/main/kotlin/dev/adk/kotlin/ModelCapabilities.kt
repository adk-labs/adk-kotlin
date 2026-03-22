package dev.adk.kotlin

data class ModelCapabilities(
    val supportsOutputSchemaWithTools: Boolean = false,
)

interface SupportsModelCapabilities {
    val modelCapabilities: ModelCapabilities
}
