package org.coralprotocol.coral.koog.fullexample.util.coral

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

class ClaimError(message: String, val statusCode: Int, val body: String?) : RuntimeException(message)

//TODO: Move to dedicated library
/**
 * Minimal claims handler mirroring the Python version's behavior.
 * - Supported currencies: "coral", "micro_coral", "usd"
 * - Uses Coral Server env when orchestrated:
 *   - CORAL_SEND_CLAIMS == "1" to enable claim calls
 *   - CORAL_API_URL and CORAL_SESSION_ID must be provided by Coral when enabled
 */
class ClaimHandler(private val currency: String = "micro_coral") : AutoCloseable {
    private var remaining: Double? = null
    private val http = HttpClient() // engine is provided transitively by koog

    init {
        require(currency in setOf("coral", "micro_coral", "usd")) { "invalid currency $currency" }
    }

    fun noBudget(): Boolean = remaining?.let { it <= 0.0 } ?: false

    fun remaining(): Double? = remaining

    fun currency(): String = currency

    /**
     * Sends a claim to Coral Server for the given amount (in this handler's currency).
     * Returns the remaining budget in the same currency.
     */
    suspend fun claim(amount: Double): Double {
        val sendClaims = System.getenv("CORAL_SEND_CLAIMS") ?: "0"
        if (sendClaims == "0") {
            // Not orchestrated - skip claim
            println("[ClaimHandler] Not orchestrated (CORAL_SEND_CLAIMS=0) - skipping claim of $amount $currency")
            return remaining ?: Double.POSITIVE_INFINITY
        }

        val coralApiUrl = System.getenv("CORAL_API_URL")
            ?: throw IllegalArgumentException("CORAL_API_URL must be set by Coral when CORAL_SEND_CLAIMS=1")
        val sessionId = System.getenv("CORAL_SESSION_ID")
            ?: throw IllegalArgumentException("CORAL_SESSION_ID must be set by Coral when CORAL_SEND_CLAIMS=1")

        val json = """{"amount":{"type":"$currency","amount":$amount}}"""

       return try {
            val resp: HttpResponse = http.post("$coralApiUrl/api/v1/internal/claim/$sessionId") {
                contentType(ContentType.Application.Json)
                setBody(json)
            }

            val body = resp.bodyAsText()
            if (resp.status.value == 200) {
                // Very small parsing without external JSON libs
                val remainingBudgetMicroCoral = extractNumber(body, "remainingBudget")
                    ?: throw ClaimError("No remainingBudget in response", resp.status.value, body)
                val coralUsdPrice = extractNumber(body, "coralUsdPrice") ?: 0.0

                val rem = when (currency) {
                    "coral" -> remainingBudgetMicroCoral / 1_000_000.0
                    "micro_coral" -> remainingBudgetMicroCoral
                    "usd" -> (remainingBudgetMicroCoral / 1_000_000.0) * coralUsdPrice
                    else -> remainingBudgetMicroCoral // guarded by require, fallback for safety
                }

                println("[ClaimHandler] Claimed $amount $currency - remaining: $rem $currency")
                this@ClaimHandler.remaining = rem
                rem
            } else {
                this@ClaimHandler.remaining = 0.0
                throw ClaimError(
                    "Failed to claim $amount $currency - status ${resp.status.value}",
                    resp.status.value,
                    body
                )
            }
        } catch (e: ResponseException) {
            val status = e.response.status.value
            val body = e.response.bodyAsText()
            this@ClaimHandler.remaining = 0.0
            throw ClaimError("Failed to claim $amount $currency - status $status", status, body)
        }
    }

    private fun extractNumber(json: String, key: String): Double? {
        // naive: find "key":<number>
        val regex = Regex("\"$key\"\\s*:\\s*([0-9]+(\\.[0-9]+)?)")
        val match = regex.find(json) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    override fun close() {
        http.close()
    }
}
