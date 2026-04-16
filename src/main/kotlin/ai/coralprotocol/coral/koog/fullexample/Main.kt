package ai.coralprotocol.coral.koog.fullexample

import ai.coralprotocol.coral.koog.fullexample.util.getPromptExecutor
import ai.coralprotocol.coral.koog.fullexample.util.findKoogModelByInfo
import ai.coralprotocol.coral.koog.fullexample.util.coral.*
import ai.coralprotocol.coral.koog.fullexample.util.executeMultipleToolsCatching
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi

private suspend fun getToolRegistry(coralToolRegistry: ToolRegistry): ToolRegistry {
    return ToolRegistry {
        tools(coralToolRegistry.tools)
        //{CORALIZER:INSIDE_TOOL_REGISTRY_BLOCK}
        // Add more local tools here as desired

    }
}

/**
 * Real main method. This method is meant to be ran via orchestration (by creating a session).
 * It ignores any local coral-agent.dev.env file.
 *
 * To run with the local dev env file, run the main method in DevMain.kt
 */
fun main() {
    val settings: ResolvedAgentSettings = AgentSettingsLoader.load(useDevEnv = false)
    runAgent(settings)
}

@OptIn(ExperimentalUuidApi::class)
fun runAgent(settings: ResolvedAgentSettings) {
    runBlocking {
        val executor: PromptExecutor =
            getPromptExecutor(
                settings.coral.modelProxyFormat,
                settings.coral.modelProxyUrl)

        val llmModel =
            findKoogModelByInfo(settings.coral.modelProxyModel, settings.coral.modelProxyProvider, settings.coral.modelProxyFormat)

        println("Connecting to MCP server at ${settings.coral.connectionUrl}")

        val coralMcpClient = try {
            getMcpClientStreamableHttp(settings.coral.connectionUrl)
        } catch (e: Throwable) {
            throw processCoralThrowable(e)
        }

        val coralToolRegistry = McpToolRegistryProvider.fromClient(coralMcpClient)
        val combinedTools = getToolRegistry(coralToolRegistry)

        println("Available tools: ${combinedTools.tools.joinToString { it.name }}")


        val loopAgent = AIAgent.Companion(
            systemPrompt = "", // This gets replaced later by updateSystemResources
            promptExecutor = executor,
            llmModel = llmModel,
            toolRegistry = combinedTools,
            strategy = functionalStrategy { _: Nothing? ->
                val maxIterations = settings.maxIterations
                val claimHandler = ClaimHandler(coralSettings = settings.coral, currency = "usd")
                var totalTokens = 0L

                repeat(maxIterations) { i ->
                    try {
                        if (claimHandler.noBudget()) return@functionalStrategy
                        if (totalTokens >= settings.maxTokens) {
                            println("Max tokens reached: $totalTokens >= ${settings.maxTokens}")
                            return@functionalStrategy
                        }

                        if (i > 0 && settings.iterationDelayMs > 0.milliseconds) {
                            println("Waiting ${settings.iterationDelayMs}ms before next iteration...")
                            delay(settings.iterationDelayMs)
                        }

                        updateSystemResources(coralMcpClient, settings)
                        val response =
                            requestLLMOnlyCallingTools(if (i == 0) buildInitialUserMessage(settings) else settings.followUpUserPrompt)

                        println("Iteration $i LLM response: ${response.content}")
                        val toolsToCall = extractToolCalls(listOf(response))
                        println("Extracted tool calls: ${toolsToCall.joinToString { it.tool }}")

                        val toolResult = executeMultipleToolsCatching(toolsToCall)

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
                        totalTokens += tokens
                        if (tokens > 0) {
                            val toClaim = tokens.toDouble() * USD_PER_TOKEN
                            try {
                                claimHandler.claim(toClaim)
                            } catch (e: Exception) {
                                // If a claim fails, stop to avoid unpaid work when orchestrated
                                e.printStackTrace()
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

/**
 * Uncomment this main method to get a printed environment variable file with exactly the environment variables that are passed
 * to this agent in the session (and no others).
 *
 * The envs will be written to `coral-agent.dev.env`. Note the agent secret is good for only that session, make sure the TTL is set high.
 *
 * Then re-comment it for the agent to run normally through coral again.
 * This is useful for running the agent directly without needing to make a new session during development.
 * To run with the local dev env file, run the main method in DevMain.kt
 *
 * (Kotlin prioritizes the main method with an args parameter)
 */
//fun main(args: Array<String>) {
//    mainPrintDevEnv()
//}