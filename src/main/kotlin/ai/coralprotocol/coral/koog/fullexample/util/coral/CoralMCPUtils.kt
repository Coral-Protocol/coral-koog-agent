package ai.coralprotocol.coral.koog.fullexample.util.coral

import ai.coralprotocol.coral.koog.fullexample.ResolvedAgentSettings
import ai.coralprotocol.coral.koog.fullexample.util.buildIndentedString
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.McpToolRegistryProvider.DEFAULT_MCP_CLIENT_NAME
import ai.koog.agents.mcp.McpToolRegistryProvider.DEFAULT_MCP_CLIENT_VERSION
import ai.koog.agents.mcp.defaultStdioTransport
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import kotlin.time.Clock.System
import kotlin.time.Duration.Companion.seconds

const val USD_PER_TOKEN = 0.000001

fun buildSystemPrompt(settings: ResolvedAgentSettings): String {
    return buildString {
        appendLine(settings.systemPrompt)
        appendLine()
        if (settings.extraSystemPrompt.isNotBlank()) {
            appendLine(settings.extraSystemPrompt)
        }
    }
}

fun buildInitialUserMessage(settings: ResolvedAgentSettings): String = buildIndentedString {
    appendLine(
        "[automated message] You are an autonomous agent designed to assist users by collaborating with other agents. Your goal is to fulfill user requests to the best of your ability using the tools and resources available to you." +
                "If no instructions are provided, consider waiting for mentions until another agent provides further direction."
    )
    if (settings.extraInitialUserPrompt.isNotBlank()) {
        appendLine("Here are some additional instructions to guide your behavior:")
        withIndentedXml("specific instructions") {
            appendLine(settings.extraInitialUserPrompt)
        }
    }
    appendLine("Remember that 'I' am not the user, who is not directly reachable. Use tools to interact with other agents as necessary to fulfil the users needs. You will receive further automated messages this way.")
}


suspend fun getMcpClientStreamableHttp(serverUrl: String): Client {
    val transport = StreamableHttpClientTransport(
        client = HttpClient {
            install(SSE)
            install(HttpTimeout) {
                requestTimeoutMillis = 60000
                connectTimeoutMillis = 60000
                socketTimeoutMillis = 60000
            }
        },
        url = serverUrl,
        reconnectionTime = 10.seconds
    )
    val client = Client(
        clientInfo = Implementation(name = DEFAULT_MCP_CLIENT_NAME, version = DEFAULT_MCP_CLIENT_VERSION),
        options = ClientOptions(
            ClientCapabilities(), enforceStrictCapabilities = false
        )
    )
    client.connect(transport)
    transport.onError { e ->
        e.printStackTrace()
    }
    return client
}

suspend fun getMcpClientSse(serverUrl: String): Client {
    val name: String = DEFAULT_MCP_CLIENT_NAME
    val version: String = DEFAULT_MCP_CLIENT_VERSION
    val transport = SseClientTransport(
        client = HttpClient {
            install(SSE)
            // Supports wait for mentions up to the default max 60 seconds + 1 second buffer
            install(HttpTimeout) {
                requestTimeoutMillis = 61 * 1000
                connectTimeoutMillis = 61 * 1000
                socketTimeoutMillis = 61 * 1000
            }
        },
        urlString = serverUrl,
        reconnectionTime = 61.seconds
    )

    val client = Client(
        clientInfo = Implementation(name = name, version = version), options = ClientOptions(
            ClientCapabilities(), enforceStrictCapabilities = false
        )
    )
    transport.onError { e ->
        e.printStackTrace()
    }
    client.connect(transport)

    return client
}

/**
 * Utilities for launching MCP servers via Stdio.
 */
object McpProcessUtils {
    /**
     * Launch a process with npx.
     * Note: We don't use -y by default to avoid surprise installations on developer machines.
     */
    fun npx(pkg: String, vararg args: String, env: Map<String, String> = emptyMap(), autoInstall: Boolean = false): Process {
        val command = mutableListOf("npx")
        if (autoInstall) {
            command.add("-y")
        }
        command.add(pkg)
        command.addAll(args)
        return ProcessBuilder(command).apply {
            environment().putAll(env)
        }.start()
    }

    /**
     * Launch a process with uvx.
     */
    fun uvx(pkg: String, vararg args: String, env: Map<String, String> = emptyMap()): Process {
        return ProcessBuilder("uvx", pkg, *args).apply {
            environment().putAll(env)
        }.start()
    }

    /**
     * Launch an arbitrary command after validating it's not a common installation command.
     */
    fun safeCommand(vararg args: String, env: Map<String, String> = emptyMap()): Process {
        val commandList = args.toList()
        validateSafeCommand(commandList)
        return ProcessBuilder(commandList).apply {
            environment().putAll(env)
        }.start()
    }

    private fun validateSafeCommand(command: List<String>) {
        val fullCommand = command.joinToString(" ").lowercase()
        val installationPatterns = listOf(
            "npm install", "npm i ", "npm add", "yarn add", "pnpm add",
            "pip install", "pip3 install", "python -m pip install", "uv pip install", "poetry add",
            "apt install", "apt-get install", "brew install", "yum install", "dnf install", "apk add", "pkg install"
        )
        if (installationPatterns.any { fullCommand.contains(it) }) {
            throw IllegalArgumentException("Forbidden installation command detected and blocked for safety: $fullCommand")
        }
    }
}

/**
 * Creates an MCP client that uses Stdio to communicate with the given [process].
 */
suspend fun getMcpClientStdio(process: Process): Client {
    val transport = McpToolRegistryProvider.defaultStdioTransport(process)
    val client = Client(
        clientInfo = Implementation(name = DEFAULT_MCP_CLIENT_NAME, version = DEFAULT_MCP_CLIENT_VERSION),
        options = ClientOptions(
            ClientCapabilities(), enforceStrictCapabilities = false
        )
    )
    client.connect(transport)
    return client
}

/**
 * Helper to construct a Stdio transport from an already-started process.
 */
fun stdioTransportFromProcess(process: Process): Transport = McpToolRegistryProvider.defaultStdioTransport(process)

/**
 * Create an MCP client by launching an MCP server via npx and wiring stdio.
 * autoInstall=false by default to avoid surprise installations; pass true only in controlled environments (e.g., Docker).
 */
suspend fun getMcpClientFromNpx(
    pkg: String,
    vararg args: String,
    env: Map<String, String> = emptyMap(),
    autoInstall: Boolean = false
): Client = getMcpClientStdio(McpProcessUtils.npx(pkg, *args, env = env, autoInstall = autoInstall))

/**
 * Create an MCP client by launching an MCP server via uvx and wiring stdio.
 */
suspend fun getMcpClientFromUvx(
    pkg: String,
    vararg args: String,
    env: Map<String, String> = emptyMap()
): Client = getMcpClientStdio(McpProcessUtils.uvx(pkg, *args, env = env))

/**
 * Construct a Stdio transport from a safe arbitrary command (blocked if it looks like an installer).
 */
fun stdioTransportFromSafeCommand(
    vararg args: String,
    env: Map<String, String> = emptyMap()
): Transport = McpToolRegistryProvider.defaultStdioTransport(McpProcessUtils.safeCommand(*args, env = env))

suspend fun AIAgentFunctionalContext.updateSystemResources(client: Client, settings: ResolvedAgentSettings) {
    val newSystemMessage = Message.System(
        injectedWithMcpResources(client, buildSystemPrompt(settings)),
        RequestMetaInfo(System.now())
    )
    return llm.writeSession {
        rewritePrompt { prompt ->
            require(prompt.messages.firstOrNull() is Message.System) { "First message isn't a system message" }
            require(prompt.messages.count { it is Message.System } == 1) { "Not exactly 1 system message" }
            val messagesWithoutSystemMessage = prompt.messages.drop(1)
            val messagesWithNewSystemMessage = listOf(newSystemMessage) + messagesWithoutSystemMessage
            prompt.copy(messages = messagesWithNewSystemMessage)
        }
    }
}

suspend fun injectedWithMcpResources(client: Client, original: String): String {
    val resourceRegex = "<resource>(.*?)</resource>".toRegex()
    val matches = resourceRegex.findAll(original)
    val uris = matches.map { it.groupValues[1] }.toList()
    if (uris.isEmpty()) return original

    val resolvedResources = uris.map { uri ->
        val resource = client.readResource(ReadResourceRequest(uri = uri))
        val contents = resource.contents.joinToString("\n") { (it as TextResourceContents).text }
        "<resource uri=\"$uri\">\n$contents\n</resource>"
    }
    var result = original
    matches.forEachIndexed { index, matchResult ->
        result = result.replace(matchResult.value, resolvedResources[index])
    }
    return result
}
