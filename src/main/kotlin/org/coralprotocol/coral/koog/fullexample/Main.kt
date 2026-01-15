package org.coralprotocol.coral.koog.fullexample

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.dsl.extension.executeMultipleTools
import ai.koog.agents.core.dsl.extension.extractToolCalls
import ai.koog.agents.core.dsl.extension.latestTokenUsage
import ai.koog.agents.core.dsl.extension.requestLLMOnlyCallingTools
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.coralprotocol.coral.koog.fullexample.coral.*
import org.coralprotocol.coral.koog.fullexample.util.findKoogModelByName
import java.io.File
import kotlin.uuid.ExperimentalUuidApi


@OptIn(ExperimentalUuidApi::class)
fun main() {

    runBlocking {
        val settings = AgentSettingsLoader.load()
        val executor: PromptExecutor = SingleLLMPromptExecutor(
            OpenAILLMClient(
                apiKey = settings.modelApiKey,
                settings = OpenAIClientSettings(baseUrl = settings.modelProviderUrl)
            )
        )
        val llmModel = findKoogModelByName(settings.modelId)

        println("Connecting to MCP server at ${settings.serverUrl}")
        val coralMcpClient = getMcpClient(settings.serverUrl)
        val coralToolRegistry = McpToolRegistryProvider.fromClient(coralMcpClient)
//        val exampleMcpToolRegistry = McpToolRegistryProvider.fromTransport()
        val toolRegistry = ToolRegistry {
            tools(coralToolRegistry.tools)

            // Add more local tools here as desired
        }

        println("Available tools: ${toolRegistry.tools.joinToString { it.name }}")


        val loopAgent = AIAgent.Companion(
            systemPrompt = "", // This gets replaced later
            promptExecutor = executor,
            llmModel = llmModel,
            toolRegistry = toolRegistry,
            strategy = functionalStrategy { _: Nothing? ->
                val maxIterations = settings.maxIterations
                val claimHandler = ClaimHandler(currency = "usd")

                repeat(maxIterations) { i ->
                    try {
                        if (claimHandler.noBudget()) return@functionalStrategy

                        updateSystemResources(coralMcpClient, settings)
                        val response =
                            if (i == 0) {
                                requestLLMOnlyCallingTools(buildInitialUserMessage(settings))
                            } else requestLLMOnlyCallingTools(
                                settings.followUpUserPrompt
                            )
                        println("Iteration $i LLM response: ${response.content}")
                        val toolsToCall = extractToolCalls(listOf(response))
                        println("Extracted tool calls: ${toolsToCall.joinToString { it.tool }}")
                        val toolResult = executeMultipleTools(toolsToCall)
                        println("Executed tools, got ${toolResult.size} results: ${Json.encodeToString(toolResult.map { it.toMessage() })}")
                        llm.writeSession {
                            appendPrompt {
                                tool {
                                    toolResult.forEach { toolResult -> this@tool.result(toolResult) }
                                }
                            }
                        }

                        // For debugging: save the full prompt messages to a file
                        llm.readSession {
                            val file = File("agent_log.json")
                            file.writeText(Json.encodeToString(prompt.messages))
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
                    } catch (e: Exception) {
                        println("Error during agent iteration: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        )

        loopAgent.run(null)
    }
}