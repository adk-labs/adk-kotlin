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
    private val defaultRegistrations =
        listOf(
            DefaultRegistration("gemini-.*") { modelName ->
                Gemini.builder().modelName(modelName).build()
            },
            DefaultRegistration("claude-.*") { modelName ->
                Claude.builder().modelName(modelName).build()
            },
            DefaultRegistration("apigee/.*") { modelName ->
                ApigeeLlm.builder().modelName(modelName).build()
            },
        )

    init {
        resetToDefaults()
    }

    @Synchronized
    fun registerLlm(
        modelNamePattern: String,
        factory: LlmFactory,
    ) {
        registerInternal(
            modelNamePattern = modelNamePattern,
            factory = factory,
            clearMatchingInstances = true,
        )
    }

    @Synchronized
    fun resetToDefaults() {
        registrations.clear()
        instances.clear()
        defaultRegistrations.forEach { registration ->
            registerInternal(
                modelNamePattern = registration.pattern,
                factory = registration.factory,
                clearMatchingInstances = false,
            )
        }
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

    private fun registerInternal(
        modelNamePattern: String,
        factory: LlmFactory,
        clearMatchingInstances: Boolean,
    ) {
        val regex = Regex(modelNamePattern)
        registrations.removeAll { registration -> registration.pattern == modelNamePattern }
        registrations += RegisteredLlm(pattern = modelNamePattern, regex = regex, factory = factory)
        if (clearMatchingInstances) {
            instances.keys.removeIf { modelName -> regex.matches(modelName) }
        }
    }

    private data class RegisteredLlm(
        val pattern: String,
        val regex: Regex,
        val factory: LlmFactory,
    )

    private data class DefaultRegistration(
        val pattern: String,
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
