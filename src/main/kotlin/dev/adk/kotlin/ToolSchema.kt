package dev.adk.kotlin

enum class ToolSchemaType {
    OBJECT,
    STRING,
    NUMBER,
    INTEGER,
    BOOLEAN,
    ARRAY,
}

data class ToolSchema(
    val type: ToolSchemaType,
    val description: String = "",
    val properties: Map<String, ToolSchema> = emptyMap(),
    val required: List<String> = emptyList(),
    val enumValues: List<String> = emptyList(),
    val items: ToolSchema? = null,
    val nullable: Boolean = false,
) {
    init {
        require(type == ToolSchemaType.OBJECT || properties.isEmpty()) {
            "Only object schemas can declare properties."
        }
        require(type == ToolSchemaType.ARRAY || items == null) {
            "Only array schemas can declare items."
        }
    }

    companion object {
        fun string(
            description: String = "",
            enumValues: List<String> = emptyList(),
            nullable: Boolean = false,
        ): ToolSchema =
            ToolSchema(
                type = ToolSchemaType.STRING,
                description = description,
                enumValues = enumValues,
                nullable = nullable,
            )

        fun number(
            description: String = "",
            nullable: Boolean = false,
        ): ToolSchema =
            ToolSchema(
                type = ToolSchemaType.NUMBER,
                description = description,
                nullable = nullable,
            )

        fun integer(
            description: String = "",
            nullable: Boolean = false,
        ): ToolSchema =
            ToolSchema(
                type = ToolSchemaType.INTEGER,
                description = description,
                nullable = nullable,
            )

        fun boolean(
            description: String = "",
            nullable: Boolean = false,
        ): ToolSchema =
            ToolSchema(
                type = ToolSchemaType.BOOLEAN,
                description = description,
                nullable = nullable,
            )

        fun array(
            items: ToolSchema,
            description: String = "",
            nullable: Boolean = false,
        ): ToolSchema =
            ToolSchema(
                type = ToolSchemaType.ARRAY,
                description = description,
                items = items,
                nullable = nullable,
            )
    }
}

class ToolSchemaBuilder {
    private val properties = linkedMapOf<String, ToolSchema>()
    private val required = linkedSetOf<String>()

    fun string(
        name: String,
        description: String = "",
        required: Boolean = true,
        enumValues: List<String> = emptyList(),
        nullable: Boolean = false,
    ) {
        properties[name] = ToolSchema.string(description = description, enumValues = enumValues, nullable = nullable)
        if (required) {
            this.required += name
        }
    }

    fun number(
        name: String,
        description: String = "",
        required: Boolean = true,
        nullable: Boolean = false,
    ) {
        properties[name] = ToolSchema.number(description = description, nullable = nullable)
        if (required) {
            this.required += name
        }
    }

    fun integer(
        name: String,
        description: String = "",
        required: Boolean = true,
        nullable: Boolean = false,
    ) {
        properties[name] = ToolSchema.integer(description = description, nullable = nullable)
        if (required) {
            this.required += name
        }
    }

    fun boolean(
        name: String,
        description: String = "",
        required: Boolean = true,
        nullable: Boolean = false,
    ) {
        properties[name] = ToolSchema.boolean(description = description, nullable = nullable)
        if (required) {
            this.required += name
        }
    }

    fun array(
        name: String,
        items: ToolSchema,
        description: String = "",
        required: Boolean = true,
        nullable: Boolean = false,
    ) {
        properties[name] = ToolSchema.array(items = items, description = description, nullable = nullable)
        if (required) {
            this.required += name
        }
    }

    fun obj(
        name: String,
        description: String = "",
        required: Boolean = true,
        block: ToolSchemaBuilder.() -> Unit,
    ) {
        val nested = toolSchema(block)
        properties[name] = nested.copy(description = description.ifBlank { nested.description })
        if (required) {
            this.required += name
        }
    }

    internal fun build(): ToolSchema =
        ToolSchema(
            type = ToolSchemaType.OBJECT,
            properties = properties.toMap(),
            required = required.toList(),
        )
}

fun toolSchema(block: ToolSchemaBuilder.() -> Unit): ToolSchema = ToolSchemaBuilder().apply(block).build()

fun ToolSchema.validateArguments(arguments: Map<String, Any?>): Map<String, Any?> {
    require(type == ToolSchemaType.OBJECT) { "Only object input schemas can validate arguments." }

    required.forEach { fieldName ->
        require(arguments.containsKey(fieldName)) { "Missing required input field: $fieldName" }
    }

    val declaredProperties = properties.keys
    return arguments
        .filterKeys { key -> key in declaredProperties }
        .onEach { (key, value) ->
            properties[key]?.validateValue(key, value)
        }
}

private fun ToolSchema.validateValue(
    fieldName: String,
    value: Any?,
) {
    if (value == null) {
        require(nullable) { "Field '$fieldName' cannot be null." }
        return
    }

    when (type) {
        ToolSchemaType.OBJECT -> {
            val nested = value as? Map<*, *> ?: error("Field '$fieldName' must be an object.")
            @Suppress("UNCHECKED_CAST")
            validateArguments(nested.entries.associate { it.key.toString() to it.value } as Map<String, Any?>)
        }

        ToolSchemaType.STRING -> require(value is String) { "Field '$fieldName' must be a string." }
        ToolSchemaType.NUMBER -> require(value is Number) { "Field '$fieldName' must be a number." }
        ToolSchemaType.INTEGER ->
            require(
                value is Byte || value is Short || value is Int || value is Long,
            ) { "Field '$fieldName' must be an integer." }
        ToolSchemaType.BOOLEAN -> require(value is Boolean) { "Field '$fieldName' must be a boolean." }
        ToolSchemaType.ARRAY -> {
            val listValue = value as? List<*> ?: error("Field '$fieldName' must be an array.")
            val itemSchema = requireNotNull(items) { "Array schema for '$fieldName' must declare items." }
            listValue.forEachIndexed { index, item ->
                itemSchema.validateValue("$fieldName[$index]", item)
            }
        }
    }
}
