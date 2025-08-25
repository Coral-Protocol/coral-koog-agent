package org.coralprotocol.coralserver.org.coralprotocol.coral.koog.fullexample

import ai.koog.agents.core.agent.*
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.McpToolRegistryProvider.DEFAULT_MCP_CLIENT_NAME
import ai.koog.agents.mcp.McpToolRegistryProvider.DEFAULT_MCP_CLIENT_VERSION
import ai.koog.agents.mcp.PatchedSseClientTransport
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
import java.lang.IllegalStateException
import kotlin.uuid.ExperimentalUuidApi

const val agentName = "exampleAgent"
const val defaultDevmodeUrl =
    "http://localhost:5555/sse/v1/devmode/exampleApplicationId/examplePrivacyKey/exampleSessionId/sse?agentId=$agentName"
//const val stepMessage = "[automated] continue collaborating with other agents"
val maxAgentIterations = 20

fun getOriginalSystemPrompt(coralConnectionUrl: String): String = """
Ur an agent arry

-- Start of messages and status --
<resource>coral://${(coralConnectionUrl).substringAfter("http://")}</resource>
-- End of messages and status --
""".trimIndent()

@OptIn(ExperimentalUuidApi::class)
fun main(): Unit = runBlocking {
    val executor: PromptExecutor = simpleOpenAIExecutor(
        System.getenv("OPENAI_API_KEY") ?: throw IllegalArgumentException("OPENAI_API_KEY is not set.")
    )
    val serverUrl = System.getenv("CORAL_SERVER_URL") ?: defaultDevmodeUrl
    val mcpClient = getMcpClient(serverUrl)
    val toolRegistry = McpToolRegistryProvider.fromClient(mcpClient)

    val loopAgent: ActAIAgent<Nothing?, Nothing?> = actAIAgent<Nothing?, Nothing?>(
        prompt = "(replaced later)",
        promptExecutor = executor,
        model = OpenAIModels.Chat.GPT4o,
        featureContext = {},
        toolRegistry = toolRegistry,
    ) {
        repeat(maxAgentIterations) {
            println("User message: ")
            val userQuery = readln()
            updateSystemResources(mcpClient, serverUrl)
            var responses = requestLLMMultiple(userQuery)

            while (responses.containsToolCalls()) {
                updateSystemResources(mcpClient, serverUrl)
                val tools = extractToolCalls(responses)

                if (latestTokenUsage() > 100500) {
                    compressHistory()
                }

                val results = executeMultipleTools(tools)
                responses = sendMultipleToolResults(results)
            }
            println("Response: $responses")
        }
        return@actAIAgent null
    } as ActAIAgent<Nothing?, Nothing?>


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

suspend fun AIAgentLoopContext.updateSystemResources(client: Client, coralConnectionUrl: String) {
    val newSystemMessage = Message.System(
        injectedWithMcpResources(client, getOriginalSystemPrompt(coralConnectionUrl)),
        RequestMetaInfo(kotlinx.datetime.Clock.System.now())
    )
    return llm.writeSession {
        rewritePrompt { prompt ->
            if (prompt.messages.firstOrNull() !is Message.System) {
                throw IllegalStateException("First message isn't a system message")
            }
            if (prompt.messages.count { it is Message.System } != 1) {
                throw IllegalStateException("Not exactly 1 system message")
            }

            val messagesWithoutSystemMessage = prompt.messages.drop(1)
            val messagesWithNewSystemMessage =
                listOf(
                    newSystemMessage
                ) + messagesWithoutSystemMessage
            return@rewritePrompt prompt.copy(messages = messagesWithoutSystemMessage)
        }
    }
}

private suspend fun injectedWithMcpResources(client: Client, original: String): String {
    // Find all occurrences of <resource>...</resource> in the original string and their URIs
    val resourceRegex = "<resource>(.*?)</resource>".toRegex()
    val matches = resourceRegex.findAll(original)
    val uris = matches.map { it.groupValues[1] }.toList()
    if (uris.isEmpty()) {
        return original
    }

    val resolvedResources = uris.map { uri ->
        val resource = client.readResource(ReadResourceRequest(uri = uri))
        val contents =
            resource?.contents?.joinToString("\n") { (it as TextResourceContents).text }
                ?: throw IllegalStateException("No contents for resource $uri")
        "<resource uri=\"$uri\">\n$contents\n</resource>"
    }
    // reduce original by replacing each <resource>...</resource> with the corresponding resolved resource
    var result = original
    matches.forEachIndexed { index, matchResult ->
        result = result.replace(matchResult.value, resolvedResources[index])
    }
    return result
}