package dev.adk.kotlin.cli

import kotlinx.coroutines.runBlocking

private data class ParsedCliArgs(
    val loaderClass: String,
    val appName: String,
    val message: String? = null,
    val userId: String = "cli-user",
    val sessionId: String = "cli-session",
    val streaming: Boolean = true,
    val interactive: Boolean = false,
)

fun main(args: Array<String>) {
    val parsedArgs = parseArgs(args)
    val cli =
        AdkCli(
            agentLoader = instantiateAgentLoader(parsedArgs.loaderClass),
        )

    runBlocking {
        if (parsedArgs.interactive || parsedArgs.message == null) {
            cli.runInteractive(
                CliInteractiveRequest(
                    appName = parsedArgs.appName,
                    userId = parsedArgs.userId,
                    sessionId = parsedArgs.sessionId,
                    streaming = parsedArgs.streaming,
                ),
            )
        } else {
            cli.run(
                CliRunRequest(
                    appName = parsedArgs.appName,
                    userId = parsedArgs.userId,
                    sessionId = parsedArgs.sessionId,
                    userMessage = dev.adk.kotlin.UserMessage(parsedArgs.message),
                    streaming = parsedArgs.streaming,
                ),
            )
        }
    }
}

private fun parseArgs(args: Array<String>): ParsedCliArgs {
    if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
        printUsage()
        kotlin.system.exitProcess(0)
    }

    var loaderClass: String? = null
    var appName: String? = null
    var message: String? = null
    var userId = "cli-user"
    var sessionId = "cli-session"
    var streaming = true
    var interactive = false

    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--loader" -> {
                loaderClass = args.getOrNull(++index) ?: error("--loader requires a class name.")
            }

            "--app" -> {
                appName = args.getOrNull(++index) ?: error("--app requires an app name.")
            }

            "--message" -> {
                message = args.getOrNull(++index) ?: error("--message requires text.")
            }

            "--user" -> {
                userId = args.getOrNull(++index) ?: error("--user requires a user id.")
            }

            "--session" -> {
                sessionId = args.getOrNull(++index) ?: error("--session requires a session id.")
            }

            "--non-streaming" -> {
                streaming = false
            }

            "--interactive" -> {
                interactive = true
            }

            else -> error("Unknown argument: $arg")
        }
        index += 1
    }

    return ParsedCliArgs(
        loaderClass = requireNotNull(loaderClass) { "--loader is required." },
        appName = requireNotNull(appName) { "--app is required." },
        message = message,
        userId = userId,
        sessionId = sessionId,
        streaming = streaming,
        interactive = interactive,
    )
}

private fun printUsage() {
    println(
        """
        Usage: adk-kotlin-cli --loader <fqcn> --app <app-name> [options]

        Options:
          --message <text>       Run a single turn.
          --interactive          Start an interactive loop.
          --user <user-id>       Override the user id. Default: cli-user
          --session <session-id> Override the session id. Default: cli-session
          --non-streaming        Print events after the run completes.
          --help, -h             Show this message.
        """.trimIndent(),
    )
}

private fun instantiateAgentLoader(className: String): AgentLoader {
    val clazz = Class.forName(className)

    val singletonInstance =
        runCatching { clazz.getField("INSTANCE").get(null) }
            .getOrNull()
            ?.takeIf { it is AgentLoader }
    if (singletonInstance != null) {
        return singletonInstance as AgentLoader
    }

    val instance = clazz.getDeclaredConstructor().newInstance()
    return instance as? AgentLoader ?: error("$className must implement AgentLoader.")
}
