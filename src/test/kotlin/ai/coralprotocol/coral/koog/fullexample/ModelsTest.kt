package ai.coralprotocol.coral.koog.fullexample

import ai.coralprotocol.coral.koog.fullexample.util.ProxyFormat
import ai.coralprotocol.coral.koog.fullexample.util.llmParams
import ai.coralprotocol.coral.koog.fullexample.util.resolveModel
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ModelsTest {
    @Test
    fun `proxy formats are parsed as Coral Server reports them`() {
        assertEquals(ProxyFormat.OPENAI, ProxyFormat.parse("OpenAI"))
        assertEquals(ProxyFormat.ANTHROPIC, ProxyFormat.parse("Anthropic"))
        assertEquals(ProxyFormat.DEEPSEEK, ProxyFormat.parse("DeepSeek"))
        assertFailsWith<IllegalArgumentException> { ProxyFormat.parse("Gemini") }
    }

    @Test
    fun `known models come from Koog's catalogue`() {
        assertEquals(OpenAIModels.Chat.GPT5_6Luna, resolveModel(ProxyFormat.OPENAI, "gpt-5.6-luna"))
    }

    @Test
    fun `unknown models get the baseline model's capabilities`() {
        val model = resolveModel(ProxyFormat.OPENAI, "gpt-9-imaginary")
        assertEquals("gpt-9-imaginary", model.id)
        assertEquals(ProxyFormat.OPENAI.baseline.capabilities, model.capabilities)
        assertEquals(ProxyFormat.OPENAI.baseline.provider, model.provider)
    }

    @Test
    fun `reasoning effort is sent to OpenAI reasoning models unless left to the provider`() {
        val params = llmParams(ProxyFormat.OPENAI, OpenAIModels.Chat.GPT5_6Luna, "none")
        assertEquals(ReasoningEffort.NONE, assertIs<OpenAIChatParams>(params).reasoningEffort)

        assertFalse(llmParams(ProxyFormat.OPENAI, OpenAIModels.Chat.GPT5_6Luna, "default") is OpenAIChatParams)
        assertFalse(llmParams(ProxyFormat.OPENAI, OpenAIModels.Chat.GPT4o, "none") is OpenAIChatParams)
        assertFalse(llmParams(ProxyFormat.ANTHROPIC, AnthropicModels.Sonnet_4_6, "none") is OpenAIChatParams)
        assertFailsWith<IllegalArgumentException> { llmParams(ProxyFormat.OPENAI, OpenAIModels.Chat.GPT5_6Luna, "extreme") }
    }
}
