package ai.coralprotocol.coral.koog.fullexample

import ai.coralprotocol.coral.koog.fullexample.util.ProxyFormat
import ai.coralprotocol.coral.koog.fullexample.util.getPromptExecutor
import ai.koog.prompt.dsl.prompt
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Coral Server's proxy URL ends in the proxy name with no trailing slash. Each Koog client's request path must be
 * appended to it, or requests would replace the proxy name and miss the proxy route.
 */
class ProxyUrlTest {
    private val requestPaths = CopyOnWriteArrayList<String>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            requestPaths += exchange.requestURI.path
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        start()
    }
    private val proxyUrl = "http://127.0.0.1:${server.address.port}/api/v1/llm-proxy/secret/LLM_FAST"

    @AfterTest
    fun stop() = server.stop(0)

    private fun requestPathFor(format: ProxyFormat): String {
        requestPaths.clear()
        runBlocking {
            runCatching {
                getPromptExecutor(format, proxyUrl).execute(prompt("test") { user("hi") }, format.baseline, emptyList())
            }
        }
        return requestPaths.first()
    }

    @Test
    fun `each format's request path is appended to the proxy URL`() {
        val proxyPath = "/api/v1/llm-proxy/secret/LLM_FAST"
        assertEquals("$proxyPath/v1/chat/completions", requestPathFor(ProxyFormat.OPENAI))
        assertEquals("$proxyPath/v1/messages", requestPathFor(ProxyFormat.ANTHROPIC))
        assertEquals("$proxyPath/chat/completions", requestPathFor(ProxyFormat.DEEPSEEK))
    }
}
