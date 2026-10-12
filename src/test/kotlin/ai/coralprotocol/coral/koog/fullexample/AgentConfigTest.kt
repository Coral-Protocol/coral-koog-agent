package ai.coralprotocol.coral.koog.fullexample

import ai.coralprotocol.coral.koog.fullexample.util.coral.buildSystemPrompt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentConfigTest {
    private class MapOptions(private val values: Map<String, String>) : EnvironmentOptionProvider {
        override fun get(name: String) = values[name] ?: throw IllegalArgumentException("$name not set")
        override fun getOptional(name: String) = values[name]
    }

    private val options = mapOf(
        "CORAL_AGENT_ID" to "agent", "CORAL_AGENT_SECRET" to "secret", "CORAL_API_URL" to "http://localhost:5555",
        "CORAL_CONNECTION_URL" to "http://localhost:5555/mcp/v1/secret/mcp", "CORAL_RUNTIME_ID" to "executable",
        "CORAL_SESSION_ID" to "session", "MODEL_CHOICE" to "LLM_FAST",
        "CORAL_PROXY_URL_LLM_FAST" to "http://localhost:5555/llm-proxy/secret/LLM_FAST",
        "CORAL_PROXY_MODEL_LLM_FAST" to "gpt-5.4-mini", "CORAL_PROXY_FORMAT_LLM_FAST" to "OpenAI",
        "SYSTEM_PROMPT" to "You are an agent.\n<resource>coral://state</resource>", "EXTRA_SYSTEM_PROMPT" to "",
        "EXTRA_INITIAL_USER_PROMPT" to "", "FOLLOWUP_USER_PROMPT" to "continue", "MAX_ITERATIONS" to "4",
        "MAX_TOKENS" to "1000", "ITERATION_DELAY_MS" to "0", "CONTEXT_COMPRESSION_TOKENS" to "0", "REASONING_EFFORT" to "none",
    )

    @Test
    fun `the session's system prompt is included when provided`() {
        val withSessionPrompt = ResolvedAgentSettings(MapOptions(options + ("CORAL_PROMPT_SYSTEM" to "Answer in French.")))
        assertTrue("Answer in French." in buildSystemPrompt(withSessionPrompt))

        val without = ResolvedAgentSettings(MapOptions(options))
        assertEquals("You are an agent.\n<resource>coral://state</resource>\n", buildSystemPrompt(without))
    }

    @Test
    fun `bool options use Coral's 1 and 0 encoding`() {
        assertTrue(ResolvedAgentSettings(MapOptions(options + ("DEV_CAPTURE_ENV" to "1"))).devCaptureEnv)
        assertFalse(ResolvedAgentSettings(MapOptions(options + ("DEV_CAPTURE_ENV" to "0"))).devCaptureEnv)
    }

    @Test
    fun `dev env values survive the round trip, including multiline prompts`() {
        val values = mapOf("SYSTEM_PROMPT" to "line one\nline \"two\" = 2", "CORAL_SESSION_ID" to "abc")
        val lines = values.map { (key, value) -> devEnvLine(key, value) }
        assertEquals(2, lines.size)
        assertEquals(values, parseDevEnv(lines))
    }
}
