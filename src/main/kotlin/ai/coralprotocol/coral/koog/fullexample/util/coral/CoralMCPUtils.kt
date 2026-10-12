package ai.coralprotocol.coral.koog.fullexample.util.coral

import ai.coralprotocol.coral.koog.fullexample.ResolvedAgentSettings
import ai.coralprotocol.coral.koog.fullexample.util.buildIndentedString
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.McpToolRegistryProvider.DEFAULT_MCP_CLIENT_NAME
import ai.koog.agents.mcp.McpToolRegistryProvider.DEFAULT_MCP_CLIENT_VERSION
import ai.koog.agents.mcp.defaultStdioTransport
import ai.koog.agents.mcp.fromProcess
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import io.ktor.client.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlin.time.Clock.System
import kotlin.time.Duration.Companion.seconds

fun buildSystemPrompt(settings: ResolvedAgentSettings): String {
    return buildString {
        appendLine(settings.systemPrompt)
        settings.coral.sessionSystemPrompt?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine(it)
        }
        if (settings.extraSystemPrompt.isNotBlank()) {
            appendLine()
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

/**
 * Connects to this agent's Coral MCP server.
 *
 * [connectionUrl] must be the exact `CORAL_CONNECTION_URL` provided by Coral Server. It is a Streamable HTTP endpoint
 * unless the runtime in coral-agent.toml sets `transport = "sse"`, in which case it ends with `/sse/`.
 */
suspend fun connectToCoral(connectionUrl: String, agentName: String): Client {
    val httpClient = HttpClient(CIO) {
        install(SSE)
        install(HttpTimeout) {
            // Must outlive Coral's longest blocking tool call (see CORAL_TOOL_CALL_TIMEOUT)
            requestTimeoutMillis = CORAL_TOOL_CALL_TIMEOUT.inWholeMilliseconds
            connectTimeoutMillis = 30.seconds.inWholeMilliseconds
            socketTimeoutMillis = CORAL_TOOL_CALL_TIMEOUT.inWholeMilliseconds
        }
    }
    val transport = if (connectionUrl.trimEnd('/').endsWith("/sse")) {
        SseClientTransport(client = httpClient, urlString = connectionUrl, reconnectionTime = 10.seconds)
    } else {
        StreamableHttpClientTransport(client = httpClient, url = connectionUrl)
    }
    transport.onError { e -> e.printStackTrace() }

    val client = Client(clientInfo = Implementation(name = agentName, version = DEFAULT_MCP_CLIENT_VERSION))
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
    val client = Client(clientInfo = Implementation(name = DEFAULT_MCP_CLIENT_NAME, version = DEFAULT_MCP_CLIENT_VERSION))
    client.connect(McpToolRegistryProvider.defaultStdioTransport(process))
    return client
}

/**
 * Creates a tool registry from an MCP server launched as a local process, e.g.
 * `toolRegistryFromProcess(McpProcessUtils.npx("@modelcontextprotocol/server-everything"))`.
 */
suspend fun toolRegistryFromProcess(process: Process): ToolRegistry = McpToolRegistryProvider.fromProcess(process)

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

/**
 * Replaces the system message with [buildSystemPrompt] with its `<resource>` tags filled in from the Coral MCP server,
 * so that coral://instruction and coral://state reflect the session as it is now.
 */
suspend fun AIAgentFunctionalContext.updateSystemResources(client: Client, settings: ResolvedAgentSettings) {
    val newSystemMessage = Message.System(
        injectedWithMcpResources(client, buildSystemPrompt(settings)),
        RequestMetaInfo(System.now())
    )
    llm.writeSession {
        rewritePrompt { prompt ->
            // History compression can reorder messages, so drop the old system message wherever it is
            prompt.copy(messages = listOf(newSystemMessage) + prompt.messages.filterNot { it is Message.System })
        }
    }
}

suspend fun injectedWithMcpResources(client: Client, original: String): String {
    val resourceRegex = "<resource>(.*?)</resource>".toRegex()
    val matches = resourceRegex.findAll(original)
    val uris = matches.map { it.groupValues[1] }.toList()
    if (uris.isEmpty()) return original

    val resolvedResources = uris.map { uri ->
        val resource = client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri = uri)))
        val contents = resource.contents.joinToString("\n") { (it as TextResourceContents).text }
        "<resource uri=\"$uri\">\n$contents\n</resource>"
    }
    var result = original
    matches.forEachIndexed { index, matchResult ->
        result = result.replace(matchResult.value, resolvedResources[index])
    }
    return result
}
