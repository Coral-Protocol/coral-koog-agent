package ai.coralprotocol.coral.koog.fullexample.util

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.JSONObject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Executes every tool call, turning failures into error results the LLM can see and react to
 * instead of ending the agent.
 */
suspend fun AIAgentFunctionalContext.executeMultipleToolsCatching(
    toolCalls: List<MessagePart.Tool.Call>,
): List<ReceivedToolResult> {
    return toolCalls.map {
        try {
            environment.executeTool(it)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val result = e.javaClass.name + ": ${e.message}"
            println("Got exception while executing tool ${it.tool}: Result is being set as: $result")
            ReceivedToolResult(
                it.id,
                it.tool,
                toolArgs = JSONObject(emptyMap()),
                null,
                result,
                ToolResultKind.Failure(e),
                null
            )
        }
    }
}
