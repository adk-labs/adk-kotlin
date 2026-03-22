package dev.adk.kotlin

import java.util.concurrent.ConcurrentHashMap

fun interface LlmFactory {
    fun create(modelName: String): BaseLlm
}

interface ModelResolver {
    fun getLlm(modelName: String): BaseLlm
}

object LlmRegistry : ModelResolver {
    private val instances = ConcurrentHashMap<String, BaseLlm>()
    private val registrations = mutableListOf<RegisteredLlm>()

    @Synchronized
    fun registerLlm(
        modelNamePattern: String,
        factory: LlmFactory,
    ) {
        registrations.removeAll { registration -> registration.pattern == modelNamePattern }
        registrations += RegisteredLlm(pattern = modelNamePattern, regex = Regex(modelNamePattern), factory = factory)
        instances.keys.removeIf { modelName -> Regex(modelNamePattern).matches(modelName) }
    }

    @Synchronized
    fun resolve(modelName: String): LlmFactory =
        registrations
            .firstOrNull { registration -> registration.regex.matches(modelName) }
            ?.factory
            ?: error("Unsupported model: $modelName")

    fun newLlm(modelName: String): BaseLlm = resolve(modelName).create(modelName)

    override fun getLlm(modelName: String): BaseLlm =
        instances.computeIfAbsent(modelName) { resolvedModelName -> newLlm(resolvedModelName) }

    internal fun clearForTests() {
        registrations.clear()
        instances.clear()
    }

    private data class RegisteredLlm(
        val pattern: String,
        val regex: Regex,
        val factory: LlmFactory,
    )
}

@Suppress("PropertyName")
val LLMRegistry: LlmRegistry
    get() = LlmRegistry

class RegistryBackedLanguageModel(
    private val modelResolver: ModelResolver = LlmRegistry,
) : LanguageModel, SupportsPerModelCapabilities {
    override suspend fun generate(request: ModelRequest): ModelResponse =
        modelResolver.getLlm(request.model).generate(request)

    override fun modelCapabilities(modelName: String): ModelCapabilities =
        modelResolver.getLlm(modelName).modelCapabilities
}
