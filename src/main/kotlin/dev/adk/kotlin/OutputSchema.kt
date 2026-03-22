package dev.adk.kotlin

data class OutputSchema(
    val fields: List<ToolParameter>,
) {
    init {
        require(fields.isNotEmpty()) { "Output schema must declare at least one field." }
    }

    fun validate(arguments: Map<String, Any?>): Map<String, Any?> {
        fields
            .filter { it.required }
            .forEach { field ->
                require(arguments.containsKey(field.name)) {
                    "Missing required output field: ${field.name}"
                }
            }

        val declaredFieldNames = fields.map { it.name }.toSet()
        return arguments.filterKeys { key -> key in declaredFieldNames }
    }
}

@AdkDsl
class OutputSchemaDsl internal constructor() {
    private val fields = mutableListOf<ToolParameter>()

    fun field(
        name: String,
        description: String = "",
        required: Boolean = true,
    ) {
        fields +=
            ToolParameter(
                name = name,
                description = description,
                required = required,
            )
    }

    internal fun build(): OutputSchema = OutputSchema(fields.toList())
}
