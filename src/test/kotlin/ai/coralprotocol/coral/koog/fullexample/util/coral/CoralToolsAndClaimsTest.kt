package ai.coralprotocol.coral.koog.fullexample.util.coral

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.serialization.kotlinx.KotlinxSerializer
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CoralToolsAndClaimsTest {
    private val tool = CoralMcpTool(
        Client(Implementation("test", "1.0.0")),
        ToolDescriptor("coral_send_message", "Posts a message"),
        CORAL_TOOL_CALL_TIMEOUT,
    )

    @Test
    fun `the LLM sees only the text of tool results`() {
        val result = CallToolResult(content = listOf(TextContent("""{"status":"Message sent"}""")))
        assertEquals("""{"status":"Message sent"}""", tool.encodeResultToString(result, KotlinxSerializer()))
    }

    @Test
    fun `tool errors are marked as errors`() {
        val result = CallToolResult(content = listOf(TextContent("Thread not found")), isError = true)
        assertEquals("Error: Thread not found", tool.encodeResultToString(result, KotlinxSerializer()))
    }

    @Test
    fun `claims opt out of automatic killing explicitly`() {
        // The server defaults autoKill to true, so the field must be sent even though it is our default
        val body = claimJson.parseToJsonElement(claimJson.encodeToString(ClaimRequest("LLM_TOKENS", 110u))).jsonObject
        assertFalse(body.getValue("autoKill").jsonPrimitive.boolean)
        assertEquals("110", body.getValue("quantity").jsonPrimitive.content)
    }

    @Test
    fun `claim results decode from the server's response`() {
        val result = claimJson.decodeFromString<ClaimResult>(
            """{"claimId":3,"requestedAmount":11000,"fulfilledAmount":3000,"remainingAgentBudget":0,"remainingSessionBudget":0,"shouldExit":true}"""
        )
        assertEquals(3000UL, result.fulfilledAmount)
        assertEquals(true, result.shouldExit)
    }
}
