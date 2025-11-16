package org.coralprotocol.coral.koog.fullexample

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.McpToolRegistryProvider.DEFAULT_MCP_CLIENT_NAME
import ai.koog.agents.mcp.McpToolRegistryProvider.DEFAULT_MCP_CLIENT_VERSION
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi

private const val agentName = "koog-agent"
private const val defaultDevmodeUrl =
    "http://localhost:5555/sse/v1/devmode/exampleApplicationId/examplePrivacyKey/exampleSessionId/sse?agentId=$agentName"
private const val USD_PER_TOKEN = 0.000001
private const val DEFAULT_MAX_ITERATIONS = 10

private fun buildSystemPrompt(): String {
    val systemPrompt = System.getenv("SYSTEM_PROMPT")
        ?: error("SYSTEM_PROMPT is required (provided by Coral via coral-agent.toml)")
    val extra = System.getenv("CORAL_PROMPT_SYSTEM") ?: ""
    return """
$systemPrompt $extra

-- Start of messages and status --
<resource>coral://agent/instruction</resource>
<resource>coral://messages</resource>
-- End of messages and status --
""".trimIndent()
}

@OptIn(ExperimentalUuidApi::class)
fun main(): Unit = runBlocking {
    val modelApiKey = System.getenv("MODEL_API_KEY") ?: System.getenv("OPENAI_API_KEY")
    ?: error("MODEL_API_KEY (or OPENAI_API_KEY) is required")
    val executor: PromptExecutor = simpleOpenAIExecutor(modelApiKey)

    val serverUrl = System.getenv("CORAL_CONNECTION_URL")
        ?: System.getenv("CORAL_SERVER_URL")
        ?: defaultDevmodeUrl

    println("Connecting to MCP server at $serverUrl")
    val mcpClient = getMcpClient(serverUrl)
    val toolRegistry = McpToolRegistryProvider.fromClient(mcpClient)

    val loopAgent = AIAgent(
        systemPrompt = "(replaced later)",
        promptExecutor = executor,
        llmModel = OpenAIModels.Chat.GPT4o,
        toolRegistry = toolRegistry,
        strategy = functionalStrategy { _: Nothing? ->
            val maxIterations =
                (System.getenv("MAX_ITERATIONS")?.toDoubleOrNull() ?: DEFAULT_MAX_ITERATIONS.toDouble()).toInt()
            val claimHandler = ClaimHandler(currency = "usd")

            repeat(maxIterations) { _ ->
                if (claimHandler.noBudget()) return@functionalStrategy

                updateSystemResources(mcpClient)
                var responses = requestLLMMultiple("[automated] continue collaborating with other agents")

                while (responses.containsToolCalls()) {
                    updateSystemResources(mcpClient)

                    if (latestTokenUsage() > 100_000) {
                        compressHistory()
                    }

                    val tools = extractToolCalls(responses)
                    val results = executeMultipleTools(tools)
                    responses = sendMultipleToolResults(results)
                }

                val tokens = latestTokenUsage()
                if (tokens > 0) {
                    val toClaim = tokens.toDouble() * USD_PER_TOKEN
                    try {
                        claimHandler.claim(toClaim)
                    } catch (e: Exception) {
                        // If a claim fails, stop to avoid unpaid work when orchestrated
                        return@functionalStrategy
                    }
                }
            }
        }
    )

    runBlocking {
        loopAgent.run(null)
    }
}

private suspend fun getMcpClient(serverUrl: String): Client {
    val name: String = DEFAULT_MCP_CLIENT_NAME
    val version: String = DEFAULT_MCP_CLIENT_VERSION
    val transport = PatchedSseClientTransport(
        client = HttpClient {
            install(SSE)
        },
        urlString = serverUrl,
    )
    val client = Client(clientInfo = Implementation(name = name, version = version))
    client.connect(transport)
    return client
}

suspend fun AIAgentFunctionalContext.updateSystemResources(client: Client) {
    val newSystemMessage = Message.System(
        injectedWithMcpResources(client, buildSystemPrompt()),
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

private suspend fun injectedWithMcpResources(client: Client, original: String): String {
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