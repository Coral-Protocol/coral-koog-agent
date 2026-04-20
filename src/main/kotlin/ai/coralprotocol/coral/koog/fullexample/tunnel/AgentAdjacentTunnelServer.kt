package ai.coralprotocol.coral.koog.fullexample.tunnel

import ai.coralprotocol.coral.koog.fullexample.TunnelSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** Fixed placeholder that replaces the real agent secret in tunneled requests. */
const val TUNNEL_SECRET_PLACEHOLDER = "TUNNEL_AGENT_SECRET"

/**
 * Result of starting the agent-adjacent tunnel proxy server.
 * The agent should rewrite its CORAL_CONNECTION_URL and LLM proxy URLs to point
 * to `http://localhost:[localPort]/...` with the agent secret replaced by the placeholder.
 */
data class AgentTunnelProxyInfo(
    val localPort: Int,
    val localBaseUrl: String
)

/**
 * Starts a local ktor server that acts as a transparent proxy for the target mimic agent.
 *
 * All requests to this server are forwarded through the tunnel server at
 * `tunnelSettings.serverUrl / tunnelSettings.uuid / <original-path>`.
 *
 * The [agentSecret] is stripped from paths and headers before sending to the tunnel,
 * replaced with [TUNNEL_SECRET_PLACEHOLDER]. The wrapper agent on the other side
 * will replace the placeholder with its own secret before forwarding to the real Coral server.
 *
 * Stdout and stderr are also forwarded as GET endpoints.
 */
fun startAgentAdjacentTunnelProxy(
    tunnelSettings: TunnelSettings,
    agentSecret: String,
    port: Int = 19280
): AgentTunnelProxyInfo {
    val tunnelBaseUrl = "${tunnelSettings.serverUrl.trimEnd('/')}/${tunnelSettings.uuid}"

    val httpClient = HttpClient(CIO)

    val server = embeddedServer(io.ktor.server.cio.CIO, port = port) {
        routing {
            route("{path...}") {
                handle {
                    val originalPath = "/" + (call.parameters.getAll("path")?.joinToString("/") ?: "")
                    val queryString = call.request.queryString()
                    val fullPath = if (queryString.isNotEmpty()) "$originalPath?$queryString" else originalPath

                    // Replace agent secret with placeholder in path
                    val sanitizedPath = fullPath.replace(agentSecret, TUNNEL_SECRET_PLACEHOLDER)

                    val tunnelUrl = "$tunnelBaseUrl$sanitizedPath"

                    val bodyBytes = call.receive<ByteArray>()

                    try {
                        val response: HttpResponse = httpClient.request(tunnelUrl) {
                            method = HttpMethod.parse(call.request.httpMethod.value)

                            // Forward headers, replacing agent secret in values
                            call.request.headers.forEach { name, values ->
                                if (!name.equals("Host", ignoreCase = true) &&
                                    !name.equals("Content-Length", ignoreCase = true)) {
                                    values.forEach { value ->
                                        header(name, value.replace(agentSecret, TUNNEL_SECRET_PLACEHOLDER))
                                    }
                                }
                            }

                            if (bodyBytes.isNotEmpty()) {
                                setBody(bodyBytes)
                            }
                        }

                        // Forward response headers back
                        response.headers.forEach { name, values ->
                            if (!name.equals("Content-Length", ignoreCase = true) &&
                                !name.equals("Transfer-Encoding", ignoreCase = true)) {
                                values.forEach { value -> call.response.header(name, value) }
                            }
                        }

                        val responseBody = response.readRawBytes()
                        call.respondBytes(responseBody, status = response.status)
                    } catch (e: Exception) {
                        println("[AgentTunnelProxy] Error proxying request to $tunnelUrl: ${e.message}")
                        call.respondText(
                            "Tunnel proxy error: ${e.message}",
                            status = HttpStatusCode.BadGateway
                        )
                    }
                }
            }
        }
    }

    server.start(wait = false)
    val localBaseUrl = "http://localhost:$port"

    println("[AgentTunnelProxy] Started on $localBaseUrl, forwarding to $tunnelBaseUrl")

    Runtime.getRuntime().addShutdownHook(Thread {
        httpClient.close()
        server.stop(1000, 2000)
    })

    return AgentTunnelProxyInfo(
        localPort = port,
        localBaseUrl = localBaseUrl
    )
}

/**
 * Rewrites a Coral URL to go through the local tunnel proxy.
 * Replaces the original base URL with the proxy base URL and the agent secret with the placeholder.
 */
fun rewriteUrlForTunnel(originalUrl: String, originalApiUrl: String, proxyBaseUrl: String, agentSecret: String): String {
    // The original URL starts with the original CORAL_API_URL base
    // Replace it with the local proxy base, and replace the agent secret with placeholder
    return originalUrl
        .replace(originalApiUrl.trimEnd('/'), proxyBaseUrl.trimEnd('/'))
        .replace(agentSecret, TUNNEL_SECRET_PLACEHOLDER)
}
