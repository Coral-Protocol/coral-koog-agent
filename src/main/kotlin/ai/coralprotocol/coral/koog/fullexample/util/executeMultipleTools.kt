package ai.coralprotocol.coral.koog.fullexample.util

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.JsonObject

suspend fun AIAgentFunctionalContext.executeMultipleToolsCatching(
    toolCalls: List<Message.Tool.Call>,
): List<ReceivedToolResult> {
    return toolCalls.map {
        try {
            environment.executeTool(it)
        } catch (e: Exception) {
            val result = e.javaClass.name + ": ${e.message}"
            println("Got exception while executing tool ${it.tool}: Result is being set as: $result")
            ReceivedToolResult(
                it.id,
                it.tool,
                toolArgs = JsonObject(emptyMap()),
                null,
                result,
                ToolResultKind.Failure(AIAgentError(e)),
                null
            )
        }
    }
}