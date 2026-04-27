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
    }

    fun registerGemini(
        modelNamePattern: String = "gemini-.*",
        modelCapabilities: ModelCapabilities = Gemini.DEFAULT_MODEL_CAPABILITIES,
        transportFactory: LlmTransportFactory,
    ) {
        registerLlm(modelNamePattern) { modelName ->
            Gemini.builder()
                .modelName(modelName)
                .modelCapabilities(modelCapabilities)
                .transport(transportFactory.create(modelName))
                .build()
        }
    }

    fun registerGemini(
        modelNamePattern: String = "gemini-.*",
        modelCapabilities: ModelCapabilities = Gemini.DEFAULT_MODEL_CAPABILITIES,
        transport: LlmTransport,
    ) {
        registerGemini(
            modelNamePattern = modelNamePattern,
            modelCapabilities = modelCapabilities,
            transportFactory = LlmTransportFactory { transport },
        )
    }

    fun registerClaude(
        modelNamePattern: String = "claude-.*",
        modelCapabilities: ModelCapabilities = Claude.DEFAULT_MODEL_CAPABILITIES,
        transportFactory: LlmTransportFactory,
    ) {
        registerLlm(modelNamePattern) { modelName ->
            Claude.builder()
                .modelName(modelName)
                .modelCapabilities(modelCapabilities)
                .transport(transportFactory.create(modelName))
                .build()
        }
    }

    fun registerClaude(
        modelNamePattern: String = "claude-.*",
        modelCapabilities: ModelCapabilities = Claude.DEFAULT_MODEL_CAPABILITIES,
        transport: LlmTransport,
    ) {
        registerClaude(
            modelNamePattern = modelNamePattern,
            modelCapabilities = modelCapabilities,
            transportFactory = LlmTransportFactory { transport },
        )
    }

    fun registerApigee(
        modelNamePattern: String = "apigee/.*",
        modelCapabilities: ModelCapabilities = Gemini.DEFAULT_MODEL_CAPABILITIES,
        transportFactory: LlmTransportFactory,
    ) {
        registerLlm(modelNamePattern) { modelName ->
            ApigeeLlm.builder()
                .modelName(modelName)
                .modelCapabilities(modelCapabilities)
                .transport(transportFactory.create(modelName))
                .build()
        }
    }

    fun registerApigee(
        modelNamePattern: String = "apigee/.*",
        modelCapabilities: ModelCapabilities = Gemini.DEFAULT_MODEL_CAPABILITIES,
        transport: LlmTransport,
    ) {
        registerApigee(
            modelNamePattern = modelNamePattern,
            modelCapabilities = modelCapabilities,
            transportFactory = LlmTransportFactory { transport },
        )
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
