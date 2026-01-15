package org.coralprotocol.coral.koog.fullexample.coral

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.mcp.McpToolRegistryProvider.DEFAULT_MCP_CLIENT_NAME
import ai.koog.agents.mcp.McpToolRegistryProvider.DEFAULT_MCP_CLIENT_VERSION
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import kotlinx.datetime.Clock
import org.coralprotocol.coral.koog.fullexample.ResolvedAgentSettings
import org.coralprotocol.coral.koog.fullexample.util.buildIndentedString
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


suspend fun getMcpClient(serverUrl: String): Client {
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
    val client = Client(clientInfo = Implementation(name = name, version = version))
    client.connect(transport)
    return client
}

suspend fun AIAgentFunctionalContext.updateSystemResources(client: Client, settings: ResolvedAgentSettings) {
    val newSystemMessage = Message.System(
        injectedWithMcpResources(client, buildSystemPrompt(settings)),
        RequestMetaInfo(Clock.System.now())
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
