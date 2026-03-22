package dev.adk.kotlin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

data class CodeBlockDelimiter(
    val start: String,
    val end: String,
)

data class CodeExecutionFile(
    val name: String,
    val content: String,
    val mimeType: String = "text/plain",
)

data class CodeExecutionInput(
    val code: String,
    val inputFiles: List<CodeExecutionFile> = emptyList(),
    val executionId: String? = null,
)

data class CodeExecutionResult(
    val stdout: String = "",
    val stderr: String = "",
    val outputFiles: List<CodeExecutionFile> = emptyList(),
    val stateDelta: Map<String, String?> = emptyMap(),
) {
    val hasError: Boolean
        get() = stderr.isNotBlank()

    fun renderOutput(): String {
        if (hasError) {
            return stderr.trimEnd()
        }

        val sections = mutableListOf<String>()
        if (stdout.isNotBlank() || outputFiles.isEmpty()) {
            sections += "Code execution result:\n${stdout.trimEnd()}"
        }

        if (outputFiles.isNotEmpty()) {
            sections += "Saved artifacts:\n" + outputFiles.joinToString(",") { file -> "`${file.name}`" }
        }

        return sections.joinToString("\n\n").trimEnd()
    }
}

data class ExtractedCodeBlock(
    val prefix: String,
    val code: String,
)

typealias CodeExecutor = BaseCodeExecutor

abstract class BaseCodeExecutor(
    open val optimizeDataFile: Boolean = false,
    open val stateful: Boolean = false,
    open val errorRetryAttempts: Int = 2,
    open val codeBlockDelimiters: List<CodeBlockDelimiter> = DEFAULT_CODE_BLOCK_DELIMITERS,
    open val executionResultDelimiters: CodeBlockDelimiter = DEFAULT_EXECUTION_RESULT_DELIMITERS,
    open val timeoutSeconds: Long? = null,
) {
    init {
        require(errorRetryAttempts >= 0) { "errorRetryAttempts must be zero or positive." }
        require(this.timeoutSeconds == null || this.timeoutSeconds!! > 0) {
            "timeoutSeconds must be positive when provided."
        }
    }

    open fun processLlmRequest(llmRequest: LlmRequest): LlmRequest = llmRequest

    fun extractCodeAndTruncateContent(text: String): ExtractedCodeBlock? {
        val match =
            codeBlockDelimiters
                .mapNotNull { delimiter ->
                    val startIndex = text.indexOf(delimiter.start)
                    if (startIndex < 0) {
                        return@mapNotNull null
                    }

                    val codeStartIndex = startIndex + delimiter.start.length
                    val endIndex = text.indexOf(delimiter.end, codeStartIndex)
                    if (endIndex < 0) {
                        return@mapNotNull null
                    }

                    Triple(startIndex, endIndex, delimiter)
                }.minByOrNull { (startIndex, _, _) -> startIndex }
                ?: return null

        val (startIndex, endIndex, delimiter) = match
        val code = text.substring(startIndex + delimiter.start.length, endIndex)
        if (code.isBlank()) {
            return null
        }

        return ExtractedCodeBlock(
            prefix = text.substring(0, startIndex).trimEnd(),
            code = code,
        )
    }

    fun formatExecutionResult(result: CodeExecutionResult): String =
        buildString {
            append(executionResultDelimiters.start)
            append(result.renderOutput())
            append(executionResultDelimiters.end)
        }

    abstract suspend fun executeCode(
        invocationContext: InvocationContext,
        codeExecutionInput: CodeExecutionInput,
    ): CodeExecutionResult

    companion object {
        const val BUILT_IN_TOOL_NAME = "code_execution"

        val DEFAULT_CODE_BLOCK_DELIMITERS =
            listOf(
                CodeBlockDelimiter(start = "```tool_code\n", end = "\n```"),
                CodeBlockDelimiter(start = "```python\n", end = "\n```"),
            )

        val DEFAULT_EXECUTION_RESULT_DELIMITERS =
            CodeBlockDelimiter(
                start = "```tool_output\n",
                end = "\n```",
            )
    }
}

open class BuiltInCodeExecutor : BaseCodeExecutor() {
    override fun processLlmRequest(llmRequest: LlmRequest): LlmRequest {
        require(isGeminiCompatibleModel(llmRequest.model)) {
            "Gemini code execution tool is not supported for model ${llmRequest.model}."
        }

        if (llmRequest.availableTools.any { tool -> tool.name == BUILT_IN_TOOL_NAME }) {
            return llmRequest
        }

        return llmRequest.copy(
            availableTools =
                llmRequest.availableTools +
                    ToolDefinition(
                        name = BUILT_IN_TOOL_NAME,
                        description = "Model built-in code execution tool.",
                        customMetadata = mapOf("builtin" to true),
                    ),
        )
    }

    override suspend fun executeCode(
        invocationContext: InvocationContext,
        codeExecutionInput: CodeExecutionInput,
    ): CodeExecutionResult =
        throw UnsupportedOperationException(
            "Code execution is not supported for built-in code executor.",
        )
}

class UnsafeLocalCodeExecutor(
    private val pythonCommand: String = "python3",
    private val rootDirectory: Path? = null,
    override val optimizeDataFile: Boolean = false,
    override val stateful: Boolean = false,
    override val errorRetryAttempts: Int = 2,
    override val codeBlockDelimiters: List<CodeBlockDelimiter> = DEFAULT_CODE_BLOCK_DELIMITERS,
    override val executionResultDelimiters: CodeBlockDelimiter = DEFAULT_EXECUTION_RESULT_DELIMITERS,
    override val timeoutSeconds: Long? = 30,
) : BaseCodeExecutor(
        optimizeDataFile = optimizeDataFile,
        stateful = stateful,
        errorRetryAttempts = errorRetryAttempts,
        codeBlockDelimiters = codeBlockDelimiters,
        executionResultDelimiters = executionResultDelimiters,
        timeoutSeconds = timeoutSeconds,
    ) {
    override suspend fun executeCode(
        invocationContext: InvocationContext,
        codeExecutionInput: CodeExecutionInput,
    ): CodeExecutionResult =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val workingDirectory = resolveWorkingDirectory(invocationContext, codeExecutionInput)
                val process =
                    ProcessBuilder(pythonCommand, "-c", codeExecutionInput.code)
                        .directory(workingDirectory.toFile())
                        .start()

                val stdoutReader =
                    async {
                        process.inputStream.bufferedReader().use { reader -> reader.readText() }
                    }
                val stderrReader =
                    async {
                        process.errorStream.bufferedReader().use { reader -> reader.readText() }
                    }

                val finished =
                    timeoutSeconds?.let { timeout ->
                        process.waitFor(timeout, TimeUnit.SECONDS)
                    } ?: run {
                        process.waitFor()
                        true
                    }

                if (!finished) {
                    process.destroyForcibly()
                    process.waitFor(1, TimeUnit.SECONDS)
                    val stdout = stdoutReader.await().trimEnd()
                    stderrReader.await()
                    return@coroutineScope CodeExecutionResult(
                        stdout = stdout,
                        stderr = "Code execution timed out after $timeoutSeconds seconds.",
                    )
                }

                val exitCode = process.exitValue()
                val stdout = stdoutReader.await().trimEnd()
                val stderr = stderrReader.await().trimEnd()
                val normalizedStderr =
                    when {
                        stderr.isNotBlank() -> stderr
                        exitCode != 0 -> "Code execution exited with status $exitCode."
                        else -> ""
                    }

                CodeExecutionResult(
                    stdout = stdout,
                    stderr = normalizedStderr,
                )
            }
        }

    private fun resolveWorkingDirectory(
        invocationContext: InvocationContext,
        codeExecutionInput: CodeExecutionInput,
    ): Path {
        val baseDirectory =
            rootDirectory
                ?: Path.of(System.getProperty("java.io.tmpdir"), "adk-kotlin-code-executor")
        Files.createDirectories(baseDirectory)

        val scope =
            if (stateful) {
                codeExecutionInput.executionId ?: invocationContext.session.id
            } else {
                "${invocationContext.session.id}-${UUID.randomUUID()}"
            }

        val workingDirectory = baseDirectory.resolve(scope)
        Files.createDirectories(workingDirectory)
        return workingDirectory
    }
}

fun builtInCodeExecutor(): BuiltInCodeExecutor = BuiltInCodeExecutor()

fun unsafeLocalCodeExecutor(
    pythonCommand: String = "python3",
    rootDirectory: Path? = null,
    optimizeDataFile: Boolean = false,
    stateful: Boolean = false,
    errorRetryAttempts: Int = 2,
    codeBlockDelimiters: List<CodeBlockDelimiter> = BaseCodeExecutor.DEFAULT_CODE_BLOCK_DELIMITERS,
    executionResultDelimiters: CodeBlockDelimiter = BaseCodeExecutor.DEFAULT_EXECUTION_RESULT_DELIMITERS,
    timeoutSeconds: Long? = 30,
): UnsafeLocalCodeExecutor =
    UnsafeLocalCodeExecutor(
        pythonCommand = pythonCommand,
        rootDirectory = rootDirectory,
        optimizeDataFile = optimizeDataFile,
        stateful = stateful,
        errorRetryAttempts = errorRetryAttempts,
        codeBlockDelimiters = codeBlockDelimiters,
        executionResultDelimiters = executionResultDelimiters,
        timeoutSeconds = timeoutSeconds,
    )

private fun isGeminiCompatibleModel(modelName: String): Boolean = modelName.startsWith("gemini", ignoreCase = true)
