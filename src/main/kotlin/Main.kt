package org.coralprotocol.coralserver


import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.simpleStrategy
import ai.koog.agents.core.dsl.extension.compressHistory
import ai.koog.agents.core.dsl.extension.executeMultipleTools
import ai.koog.agents.core.dsl.extension.extractToolCalls
import ai.koog.agents.core.dsl.extension.iterations
import ai.koog.agents.core.dsl.extension.latestTokenUsage
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.requestLLMMultiple
import ai.koog.agents.core.dsl.extension.sendMultipleToolResults
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val executor: PromptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY") ?: throw IllegalArgumentException("OPENAI_API_KEY is not set."))

    val toolRegistry = ToolRegistry {
        tool(AskUser)
        tool(SayToUser)
    }

    // Create the agent
    val agent = AIAgent(
        executor = executor,
        llmModel = OpenAIModels.Chat.GPT4o,
        strategy = simpleStrategy("calculator") { input ->
            while (iterations() < config.maxAgentIterations) {
                val response: List<Message.Response> = requestLLMMultiple(input)
                onAssistantMessage(response.first()) { return@simpleStrategy it.content }
                val tools = extractToolCalls(response)

                if (latestTokenUsage(tools) > 100500) {
                    compressHistory()
                }

                val results = executeMultipleTools(tools)
                sendMultipleToolResults(results)

            }
            "Failed to finish the agent in the given number of iterations."
        },
        systemPrompt = "You are a calculator.",
        toolRegistry = toolRegistry
    ) {
        handleEvents {
            onToolCall { eventContext ->
                println("Tool called: tool ${eventContext.tool.name}, args ${eventContext.toolArgs}")
            }

            onAgentRunError { eventContext ->
                println("An error occurred: ${eventContext.throwable.message}\n${eventContext.throwable.stackTraceToString()}")
            }

            onAgentFinished { eventContext ->
                println("Result: ${eventContext.result}")
            }
        }
    }

    runBlocking {
        agent.run("(10 + 20) * (5 + 5) / (2 - 11)")
    }
}