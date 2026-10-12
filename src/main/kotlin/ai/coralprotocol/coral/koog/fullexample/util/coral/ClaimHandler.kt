package ai.coralprotocol.coral.koog.fullexample.util.coral

import ai.coralprotocol.coral.koog.fullexample.CoralSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ClaimError(message: String, val statusCode: Int, val body: String?) : RuntimeException(message)

/** Defaults must be encoded: the server's own default for [ClaimRequest.autoKill] is true. */
internal val claimJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

@Serializable
data class ClaimRequest(
    val claimTypeName: String,
    val quantity: UInt = 1u,
    val additionalDescription: String? = null,
    /** False lets this agent wind down itself when the budget runs out; the session may still kill it. */
    val autoKill: Boolean = false,
)

/** Amounts are in micro cents: $1.00 is 100_000_000. */
@Serializable
data class ClaimResult(
    val claimId: Int,
    val requestedAmount: ULong,
    val fulfilledAmount: ULong,
    val remainingAgentBudget: ULong,
    val remainingSessionBudget: ULong,
    val shouldExit: Boolean,
)

/**
 * Reports work to Coral Server so it can be charged against the session's budget.
 *
 * Claim types (and optionally a default cost) are declared in the `[[claims]]` section of coral-agent.toml. The
 * application creating the session sets the budget and the cost of each claim type; unpriced claims are free but
 * still recorded. When a claim exhausts the budget, [ClaimResult.shouldExit] tells the agent to stop.
 *
 * See https://docs.coralos.ai/concepts/payments
 */
class ClaimHandler(private val coral: CoralSettings) : AutoCloseable {
    private val http = HttpClient(CIO)

    suspend fun claim(claimTypeName: String, quantity: UInt, description: String? = null): ClaimResult {
        val response = http.post("${coral.apiUrl.trimEnd('/')}/api/v1/agent-rpc/claim") {
            bearerAuth(coral.agentSecret)
            contentType(ContentType.Application.Json)
            setBody(claimJson.encodeToString(ClaimRequest(claimTypeName, quantity, description)))
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess())
            throw ClaimError("Claim of $quantity $claimTypeName failed with ${response.status}", response.status.value, body)

        return claimJson.decodeFromString<ClaimResult>(body)
    }

    override fun close() {
        http.close()
    }
}
