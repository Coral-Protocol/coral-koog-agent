package org.coralprotocol.coralserver

import ai.koog.agents.core.agent.*
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.PatchedSseClientTransport
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import kotlinx.coroutines.runBlocking
import kotlin.uuid.ExperimentalUuidApi

const val agentName = "exampleAgent"
const val defaultDevmodeUrl =
    "http://localhost:5555/devmode/exampleApplicationId/examplePrivacyKey/exampleSessionId/sse?agentId=$agentName"
const val stepMessage = "[automated] continue collaborating with other agents"
val maxAgentIterations = 20

@OptIn(ExperimentalUuidApi::class)
fun main(): Unit = runBlocking {
    val executor: PromptExecutor = simpleOpenAIExecutor(
        System.getenv("OPENAI_API_KEY") ?: throw IllegalArgumentException("OPENAI_API_KEY is not set.")
    )
    val serverUrl = System.getenv("CORAL_SERVER_URL") ?: defaultDevmodeUrl
    val toolRegistry = McpToolRegistryProvider.fromTransport(
        transport = PatchedSseClientTransport(
            client = HttpClient {
                install(SSE)
            },
            urlString = serverUrl,
        ),
    )

    val loopAgent = actAIAgent<Nothing?, Nothing?>(
        prompt = "You're $agentName",
        promptExecutor = executor,
        model = OpenAIModels.Chat.GPT4o,
        toolRegistry = toolRegistry) {
        repeat(maxAgentIterations) {
            println("User message: ")
            val userQuery = readln()
            var responses = requestLLMMultiple(userQuery)

            while (responses.containsToolCalls()) {
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
    }

    runBlocking {
        loopAgent.run(null)
    }
}
