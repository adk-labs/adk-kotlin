package dev.adk.kotlin

class PluginManager(
    plugins: List<Plugin> = emptyList(),
) {
    private val plugins = mutableListOf<Plugin>()

    init {
        plugins.forEach(::registerPlugin)
    }

    fun registerPlugin(plugin: Plugin) {
        require(this.plugins.none { it.name == plugin.name }) {
            "Plugin with name '${plugin.name}' already registered."
        }
        this.plugins += plugin
    }

    fun getPlugin(pluginName: String): Plugin? = plugins.firstOrNull { it.name == pluginName }

    fun snapshotPlugins(): List<Plugin> = plugins.toList()

    suspend fun runOnUserMessageCallback(
        invocationContext: InvocationContext,
        userMessage: UserMessage,
    ): UserMessage? =
        runCallbacks { plugin ->
            plugin.onUserMessageCallback(invocationContext, userMessage)
        }

    suspend fun runBeforeRunCallback(invocationContext: InvocationContext): Event? =
        runCallbacks { plugin ->
            plugin.beforeRunCallback(invocationContext)
        }

    suspend fun runOnEventCallback(
        invocationContext: InvocationContext,
        event: Event,
    ): Event? =
        runCallbacks { plugin ->
            plugin.onEventCallback(invocationContext, event)
        }

    suspend fun runAfterRunCallback(
        invocationContext: InvocationContext,
        runResult: RunResult,
    ) {
        plugins.forEach { plugin ->
            plugin.afterRunCallback(invocationContext, runResult)
        }
    }

    suspend fun runBeforeModelCallback(
        callbackContext: CallbackContext,
        llmRequest: LlmRequest,
    ): LlmResponse? =
        runCallbacks { plugin ->
            plugin.beforeModelCallback(callbackContext, llmRequest)
        }

    suspend fun runAfterModelCallback(
        callbackContext: CallbackContext,
        llmResponse: LlmResponse,
    ): LlmResponse? =
        runCallbacks { plugin ->
            plugin.afterModelCallback(callbackContext, llmResponse)
        }

    suspend fun runOnModelErrorCallback(
        callbackContext: CallbackContext,
        llmRequest: LlmRequest,
        error: Throwable,
    ): LlmResponse? =
        runCallbacks { plugin ->
            plugin.onModelErrorCallback(callbackContext, llmRequest, error)
        }

    suspend fun runBeforeToolCallback(
        tool: Tool,
        toolCall: ToolCall,
        toolContext: ToolContext,
    ): ToolOutput? =
        runCallbacks { plugin ->
            plugin.beforeToolCallback(tool, toolCall, toolContext)
        }

    suspend fun runAfterToolCallback(
        tool: Tool,
        toolCall: ToolCall,
        toolContext: ToolContext,
        result: ToolOutput,
    ): ToolOutput? =
        runCallbacks { plugin ->
            plugin.afterToolCallback(tool, toolCall, toolContext, result)
        }

    suspend fun runOnToolErrorCallback(
        tool: Tool,
        toolCall: ToolCall,
        toolContext: ToolContext,
        error: Throwable,
    ): ToolOutput? =
        runCallbacks { plugin ->
            plugin.onToolErrorCallback(tool, toolCall, toolContext, error)
        }

    suspend fun close() {
        plugins.forEach { plugin ->
            plugin.close()
        }
    }

    private suspend fun <T> runCallbacks(block: suspend (Plugin) -> T?): T? {
        plugins.forEach { plugin ->
            val result = block(plugin)
            if (result != null) {
                return result
            }
        }
        return null
    }
}
