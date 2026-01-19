package ai.coralprotocol.coral.koog.fullexample.util

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.llm.LLModel


fun findKoogModelByName(
    id: String, modelObjects: List<Any> = listOf(
        OpenRouterModels, OpenAIModels.Chat, AnthropicModels
    )
): LLModel {
    val allLmModels: List<LLModel> = modelObjects.flatMap {
        it::class.members
            .filter { member -> member.returnType.classifier == LLModel::class }
            .mapNotNull { member -> member.call(it) as? LLModel }
    }
    return allLmModels.first { it.id == id }
}