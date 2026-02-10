package ai.coralprotocol.coral.koog.fullexample.util

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
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
            .mapNotNull { member -> member.call(it) as? LLModel }
    }
    return allLmModels.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Model with id $id not found in known model definitions. Available models: ${allLmModels.joinToString { it.id }}")
}

enum class ModelProvider(val getExecutor: (urlOverride: String?, modelApiKey: String) -> PromptExecutor) {
    OPENAI({ urlOverride, modelApiKey ->
        SingleLLMPromptExecutor(
            OpenAILLMClient(
                apiKey = modelApiKey,
                settings = if (urlOverride == null) OpenAIClientSettings() else OpenAIClientSettings(baseUrl = urlOverride)
            )
        )
    }),
    OPENROUTER({ urlOverride, modelApiKey ->
        SingleLLMPromptExecutor(
            OpenRouterLLMClient(
                apiKey = modelApiKey,
                settings = if (urlOverride == null) OpenRouterClientSettings() else OpenRouterClientSettings(baseUrl = urlOverride)
            )
        )
    }),
    ANTHROPIC({ urlOverride, modelApiKey ->
        SingleLLMPromptExecutor(
            AnthropicLLMClient(
                apiKey = modelApiKey,
                settings = if (urlOverride == null) AnthropicClientSettings() else AnthropicClientSettings(baseUrl = urlOverride)
            )
        )
    }),
    CORAL_LLM_PROXY({ urlOverride, modelApiKey ->
        SingleLLMPromptExecutor(
            OpenRouterLLMClient(
                apiKey = modelApiKey,
                settings = if (urlOverride == null) OpenRouterClientSettings(baseUrl = System.getenv("CORAL_LLM_PROXY_BASE_URL")) else OpenRouterClientSettings(
                    baseUrl = urlOverride
                )
            )
        )
    })
}