package ai.coralprotocol.coral.koog.fullexample.util

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

fun findKoogModelByName(
    id: String, modelObjects: List<Any> = listOf(
        OpenRouterModels, OpenAIModels.Chat, AnthropicModels
    )
): LLModel {
    val allLmModels: List<LLModel> = modelObjects.flatMap {
        it::class.members
            .filter { member -> member.returnType.classifier == LLModel::class }
            .mapNotNull { member -> member.call() as? LLModel }
    }
    return allLmModels.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Model with id $id not found in known model definitions. Available models: ${allLmModels.joinToString { it.id }}")
}

fun findKoogModelByInfo(
    id: String,
    provider: String? = null,
    format: String? = null
): LLModel {
    val modelObjects = when { // TODO: Separate matters of provider and format
        provider?.lowercase() == "openai" || format?.lowercase() == "openai" -> listOf(OpenAIModels.Chat)
        provider?.lowercase() == "anthropic" || format?.lowercase() == "anthropic" -> listOf(AnthropicModels)
        provider?.lowercase() == "openrouter" || format?.lowercase() == "openrouter" -> listOf(OpenRouterModels)
        else -> listOf(OpenRouterModels, OpenAIModels.Chat, AnthropicModels)
    }
    return findKoogModelByName(id, modelObjects)
}

fun getPromptExecutor(format: String, url: String): PromptExecutor {
    return when (format.lowercase()) {
        "openai" -> MultiLLMPromptExecutor(
            OpenAILLMClient(
                apiKey = "", // api key not relevant, agent secret encoded in base url
                settings = OpenAIClientSettings(baseUrl = url)
            )
        )
        "anthropic" -> MultiLLMPromptExecutor(
            AnthropicLLMClient(
                apiKey = "",
                settings = AnthropicClientSettings(baseUrl = url)
            )
        )
        "openrouter" -> MultiLLMPromptExecutor(
            OpenRouterLLMClient(
                apiKey = "",
                settings = OpenRouterClientSettings(baseUrl = url)
            )
        )
        else -> throw IllegalArgumentException("Unsupported model format: $format")
    }
}