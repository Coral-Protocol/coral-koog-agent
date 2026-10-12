package ai.coralprotocol.coral.koog.fullexample.util.coral

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.DefaultMcpToolDescriptorParser
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.toKoogJSONElement
import ai.koog.serialization.kotlinx.toKotlinxJsonElement
import ai.koog.serialization.kotlinx.toKotlinxJsonObject
import ai.koog.serialization.typeToken
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Coral's wait tools (e.g. coral_wait_for_mention) block for up to 60 seconds, which is also the MCP SDK's default
 * request timeout, so they must be called with a longer one or they fail just as they would have returned.
 */
val CORAL_TOOL_CALL_TIMEOUT = 2.minutes

/**
 * Builds a tool registry from every tool the Coral MCP server offers this agent: Coral's own thread/message tools and
 * any custom tools the application granted it.
 */
suspend fun coralToolRegistry(client: Client, timeout: Duration = CORAL_TOOL_CALL_TIMEOUT): ToolRegistry {
    val tools = client.listTools().tools.map { CoralMcpTool(client, DefaultMcpToolDescriptorParser.parse(it), timeout) }
    return ToolRegistry { tools(tools) }
}

/**
 * Calls one MCP tool, like Koog's own MCP tools, but with a configurable timeout and with only the text content
 * of the result given to the LLM, rather than the full protocol envelope.
 */
class CoralMcpTool(
    private val client: Client,
    descriptor: ToolDescriptor,
    private val timeout: Duration,
) : Tool<JSONObject, CallToolResult?>(
    argsType = typeToken<JSONObject>(),
    resultType = typeToken<CallToolResult?>(),
    descriptor = descriptor,
) {
    private val json = Json.Default
    private val resultSerializer = CallToolResult.serializer().nullable

    override suspend fun execute(args: JSONObject): CallToolResult = client.callTool(
        CallToolRequest(CallToolRequestParams(name = descriptor.name, arguments = args.toKotlinxJsonObject())),
        RequestOptions(timeout = timeout)
    )

    override fun decodeResult(rawResult: JSONElement, serializer: JSONSerializer): CallToolResult? =
        json.decodeFromJsonElement(resultSerializer, rawResult.toKotlinxJsonElement())

    override fun encodeResult(result: CallToolResult?, serializer: JSONSerializer): JSONElement =
        json.encodeToJsonElement(resultSerializer, result).toKoogJSONElement()

    override fun encodeResultToString(result: CallToolResult?, serializer: JSONSerializer): String {
        if (result == null) return "null"

        val text = result.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
        val body = text.ifBlank { serializer.encodeJSONElementToString(encodeResult(result, serializer)) }
        return if (result.isError == true) "Error: $body" else body
    }
}
