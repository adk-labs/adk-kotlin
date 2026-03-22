package dev.adk.kotlin

data class ThinkingConfig(
    val includeThoughts: Boolean? = null,
    val thinkingBudget: Int? = null,
)

data class GenerateContentConfig(
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val candidateCount: Int? = null,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String> = emptyList(),
    val responseMimeType: String? = null,
    val thinkingConfig: ThinkingConfig? = null,
) {
    init {
        require(topK == null || topK > 0) { "topK must be positive when provided." }
        require(candidateCount == null || candidateCount > 0) { "candidateCount must be positive when provided." }
        require(maxOutputTokens == null || maxOutputTokens > 0) {
            "maxOutputTokens must be positive when provided."
        }
    }
}

@AdkDsl
class ThinkingConfigDsl {
    var includeThoughts: Boolean? = null
    var thinkingBudget: Int? = null

    internal fun build(): ThinkingConfig =
        ThinkingConfig(
            includeThoughts = includeThoughts,
            thinkingBudget = thinkingBudget,
        )
}

fun thinkingConfig(block: ThinkingConfigDsl.() -> Unit): ThinkingConfig =
    ThinkingConfigDsl().apply(block).build()

@AdkDsl
class GenerateContentConfigDsl {
    var temperature: Double? = null
    var topP: Double? = null
    var topK: Int? = null
    var candidateCount: Int? = null
    var maxOutputTokens: Int? = null
    var responseMimeType: String? = null

    private val stopSequences = mutableListOf<String>()
    private var thinkingConfig: ThinkingConfig? = null

    fun stopSequence(value: String) {
        value
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let(stopSequences::add)
    }

    fun stopSequences(vararg values: String) {
        values.forEach(::stopSequence)
    }

    fun thinkingConfig(block: ThinkingConfigDsl.() -> Unit) {
        thinkingConfig = dev.adk.kotlin.thinkingConfig(block)
    }

    internal fun build(): GenerateContentConfig =
        GenerateContentConfig(
            temperature = temperature,
            topP = topP,
            topK = topK,
            candidateCount = candidateCount,
            maxOutputTokens = maxOutputTokens,
            stopSequences = stopSequences.toList(),
            responseMimeType = responseMimeType,
            thinkingConfig = thinkingConfig,
        )
}

fun generateContentConfig(block: GenerateContentConfigDsl.() -> Unit): GenerateContentConfig =
    GenerateContentConfigDsl().apply(block).build()
