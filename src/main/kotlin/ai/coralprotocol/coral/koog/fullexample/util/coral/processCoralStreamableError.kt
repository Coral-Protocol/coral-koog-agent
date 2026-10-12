package ai.coralprotocol.coral.koog.fullexample.util.coral

import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import java.net.ConnectException

/**
 * Adds context to the common ways connecting to Coral fails.
 */
fun processCoralThrowable(e: Throwable): Throwable {
    val causes = generateSequence(e) { it.cause }
    val httpError = causes.filterIsInstance<StreamableHttpError>().firstOrNull()
    return when {
        httpError?.code == 401 -> IllegalArgumentException(
            "Can't connect to coral (401): the session likely no longer exists, or the agent secret is invalid",
            e
        )

        causes.any { it is ConnectException } -> IllegalStateException(
            "Coral server doesn't seem to be running, or is running on a different address than CORAL_CONNECTION_URL",
            e
        )

        else -> e
    }
}
