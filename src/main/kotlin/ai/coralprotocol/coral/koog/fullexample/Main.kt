package ai.coralprotocol.coral.koog.fullexample

import ai.coralprotocol.coral.koog.fullexample.util.ProxyFormat
import ai.coralprotocol.coral.koog.fullexample.util.coral.*
import ai.coralprotocol.coral.koog.fullexample.util.executeMultipleToolsCatching
import ai.coralprotocol.coral.koog.fullexample.util.getPromptExecutor
import ai.coralprotocol.coral.koog.fullexample.util.llmParams
import ai.coralprotocol.coral.koog.fullexample.util.resolveModel
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

private const val logLatestLlmRequestToLogFile = true
private val llmLogJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

/** Declared in the `[[claims]]` section of coral-agent.toml. */
private const val TOKEN_CLAIM_TYPE = "LLM_TOKENS"

private fun getToolRegistry(coralToolRegistry: ToolRegistry): ToolRegistry {
    return ToolRegistry {
        tools(coralToolRegistry.tools)
        //{CORALIZER:INSIDE_TOOL_REGISTRY_BLOCK}
        // Add more local tools here as desired

    }
}

/**
 * Real main method. This method is meant to be run via orchestration (by creating a session).
 * It ignores any local coral-agent.dev.env file.
 *
 * To run with the local dev env file, run the main method in DevMain.kt
 */
fun main() {
    val settings: ResolvedAgentSettings = AgentSettingsLoader.load(useDevEnv = false)
    if (settings.devCaptureEnv) {
        captureDevEnvAndWait()
        return
    }
    runAgent(settings)
}

fun runAgent(settings: ResolvedAgentSettings) {
    val format = ProxyFormat.parse(settings.coral.modelProxyFormat)
    val executor = getPromptExecutor(format, settings.coral.modelProxyUrl)
    val llmModel = resolveModel(format, settings.coral.modelProxyModel)

    runBlocking {
        println("Connecting to Coral at ${settings.coral.connectionUrl}")
        println("Using model ${llmModel.id} (${format.name}) through LLM proxy ${settings.coral.modelChoice}")

        val coralMcpClient = try {
            connectToCoral(settings.coral.connectionUrl, settings.coral.agentId)
        } catch (e: Throwable) {
            throw processCoralThrowable(e)
        }

        try {
            val combinedTools = getToolRegistry(coralToolRegistry(coralMcpClient))
            println("Available tools: ${combinedTools.tools.joinToString { it.name }}")

            ClaimHandler(settings.coral).use { claims ->
                val loopAgent = AIAgent(
                    promptExecutor = executor,
                    agentConfig = AIAgentConfig(
                        // The system prompt is set each iteration by updateSystemResources
                        prompt = prompt("coral-agent", params = llmParams(format, llmModel, settings.reasoningEffort)) {},
                        model = llmModel,
                        maxAgentIterations = settings.maxIterations,
                    ),
                    toolRegistry = combinedTools,
                    strategy = functionalStrategy<Unit, Unit> {
                        var totalTokens = 0L

                        repeat(settings.maxIterations) { i ->
                            try {
                                if (totalTokens >= settings.maxTokens) {
                                    println("Max tokens reached: $totalTokens >= ${settings.maxTokens}")
                                    return@functionalStrategy
                                }

                                if (i > 0 && settings.iterationDelayMs > 0.milliseconds) {
                                    println("Waiting ${settings.iterationDelayMs} before next iteration...")
                                    delay(settings.iterationDelayMs)
                                }

                                // Refresh coral://instruction and coral://state so the system prompt reflects the session as it is now
                                updateSystemResources(coralMcpClient, settings)
                                val response = requestLLMOnlyCallingTools(
                                    if (i == 0) buildInitialUserMessage(settings) else settings.followUpUserPrompt
                                )

                                val toolCalls = getToolCalls(response)
                                println("Iteration $i: ${response.textContent().ifBlank { "(no text)" }} | tool calls: ${toolCalls.joinToString { it.tool }}")

                                val toolResults = executeMultipleToolsCatching(toolCalls)
                                llm.writeSession {
                                    appendPrompt {
                                        user {
                                            toolResults.forEach { toolResult(it.toMessagePart()) }
                                        }
                                    }
                                }

                                // For debugging: save the full prompt messages to a file
                                if (logLatestLlmRequestToLogFile) {
                                    llm.readSession {
                                        File("agent_log.json").writeText(llmLogJson.encodeToString(prompt.messages))
                                    }
                                }

                                // The latest request's usage is roughly the size of the conversation so far
                                val contextTokens = latestTokenUsage().toLong()
                                totalTokens += contextTokens
                                if (claimTokens(claims, contextTokens)) {
                                    println("Budget exhausted, stopping")
                                    return@functionalStrategy
                                }

                                if (settings.contextCompressionTokens in 1..contextTokens) {
                                    compressContext(contextTokens)
                                    val compressionTokens = latestTokenUsage().toLong()
                                    totalTokens += compressionTokens
                                    if (claimTokens(claims, compressionTokens)) return@functionalStrategy
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: ClaimError) {
                                // Stop rather than keep doing work that can't be paid for
                                println("Claim failed (${e.statusCode}): ${e.body}")
                                return@functionalStrategy
                            } catch (e: Exception) {
                                println("Error during agent iteration: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    }
                )

                loopAgent.run(Unit)
            }
        } finally {
            coralMcpClient.close()
        }
    }
}

/**
 * Context management: once the conversation grows past CONTEXT_COMPRESSION_TOKENS, replace it with an LLM-written
 * summary. The system prompt is kept, and since it embeds coral://state, the current threads and messages are
 * re-read every iteration rather than lost.
 *
 * This is the place to customise how this agent remembers; see Koog's HistoryCompressionStrategy implementations.
 */
private suspend fun AIAgentFunctionalContext.compressContext(contextTokens: Long) {
    println("Context is $contextTokens tokens, compressing history")
    compressHistory(HistoryCompressionStrategy.WholeHistory)
}

/**
 * Claims [tokens] as LLM_TOKENS and returns true if the budget is exhausted and the agent should stop.
 */
private suspend fun claimTokens(claims: ClaimHandler, tokens: Long): Boolean {
    // Nothing to charge; Coral Server would only log a warning for a zero-cost claim
    if (tokens <= 0) return false

    val result = claims.claim(TOKEN_CLAIM_TYPE, tokens.coerceAtMost(UInt.MAX_VALUE.toLong()).toUInt())
    if (result.fulfilledAmount < result.requestedAmount)
        println("Claimed ${result.fulfilledAmount} of ${result.requestedAmount} micro cents; remaining agent budget ${result.remainingAgentBudget}")
    return result.shouldExit
}
