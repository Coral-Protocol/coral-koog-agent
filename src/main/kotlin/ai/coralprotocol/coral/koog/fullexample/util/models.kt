package ai.coralprotocol.coral.koog.fullexample.util

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams

/**
 * The wire formats Coral Server's LLM proxy can speak, as provided in `CORAL_PROXY_FORMAT_<NAME>`.
 * Each maps to the Koog client that speaks that format, the Koog model catalogue for it, and a baseline model whose
 * capabilities are assumed for models Koog doesn't know about yet.
 */
enum class ProxyFormat(val definitions: LLModelDefinitions, val baseline: LLModel) {
    OPENAI(OpenAIModels, OpenAIModels.Chat.GPT5_6Luna),
    ANTHROPIC(AnthropicModels, AnthropicModels.Sonnet_4_6),
    DEEPSEEK(DeepSeekModels, DeepSeekModels.DeepSeekV4Flash);

    companion object {
        fun parse(format: String): ProxyFormat = entries.firstOrNull { it.name.equals(format, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unsupported LLM proxy format: $format. Supported: ${entries.joinToString()}")
    }
}

/**
 * Finds the Koog model definition for [id], or builds one with the [ProxyFormat.baseline]'s capabilities.
 *
 * The model is chosen by whoever runs the session (see `[[llm.proxies]]` in coral-agent.toml), so it may be newer
 * than this Koog version's catalogue. Failing would make the agent unusable with it.
 */
fun resolveModel(format: ProxyFormat, id: String): LLModel {
    format.definitions.models.firstOrNull { it.id == id }?.let { return it }

    println("Model $id is not in Koog's ${format.name} catalogue; assuming the capabilities of ${format.baseline.id}")
    return format.baseline.copy(id = id)
}

/**
 * Creates a prompt executor that talks to Coral Server's LLM proxy.
 *
 * The proxy URL stands in for the provider's base URL: Koog appends its usual request paths (e.g. `v1/chat/completions`)
 * and the server forwards them to the configured provider. No API key is needed; the agent secret in the URL
 * authenticates the agent, and the server attaches the real provider key.
 */
fun getPromptExecutor(format: ProxyFormat, proxyUrl: String): PromptExecutor {
    // Koog's HTTP client ends the base URL with a slash, so its request paths are appended to the proxy URL
    val client = when (format) {
        ProxyFormat.OPENAI -> OpenAILLMClient(apiKey = "", settings = OpenAIClientSettings(baseUrl = proxyUrl))
        ProxyFormat.ANTHROPIC -> AnthropicLLMClient(apiKey = "", settings = AnthropicClientSettings(baseUrl = proxyUrl))
        ProxyFormat.DEEPSEEK -> DeepSeekLLMClient(apiKey = "", settings = DeepSeekClientSettings(baseUrl = proxyUrl))
    }
    return MultiLLMPromptExecutor(client)
}

/**
 * Request parameters for [model], applying the REASONING_EFFORT option to OpenAI-format reasoning models.
 *
 * Koog talks to OpenAI-format proxies through the Chat Completions API (Coral Cloud doesn't serve the Responses API),
 * where GPT-5.1 and newer only accept function tools with reasoning effort "none", while gpt-5 and o-series models
 * reject "none". "default" leaves the effort to the provider.
 */
fun llmParams(format: ProxyFormat, model: LLModel, reasoningEffort: String): LLMParams {
    if (format != ProxyFormat.OPENAI || !model.supports(LLMCapability.Thinking) || reasoningEffort == "default")
        return LLMParams()

    val effort = ReasoningEffort.entries.firstOrNull { it.name.equals(reasoningEffort, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unsupported REASONING_EFFORT: $reasoningEffort")
    return OpenAIChatParams(reasoningEffort = effort)
}
