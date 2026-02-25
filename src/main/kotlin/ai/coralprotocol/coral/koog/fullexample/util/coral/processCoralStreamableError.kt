package ai.coralprotocol.coral.koog.fullexample.util.coral

import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import org.apache.hc.client5.http.HttpHostConnectException

private fun processCoralStreamableError(e: StreamableHttpError): Throwable {
    return if (e.code == 401) {
        // Agent secret musn't be valid, wrap exception with more information
        IllegalArgumentException(
            "Can't connect to coral (401), likely session no longer exists or agent secret invalid",
            e
        )
    } else {
        e
    }
}

fun processCoralThrowable(e: Throwable): Throwable = when (e) {
    is StreamableHttpError -> return processCoralStreamableError(e)
    is IllegalStateException -> {
        return when (e.cause is HttpHostConnectException) {
            true -> IllegalStateException(
                "Coral server doesn't seem to be running or is running on a different port/address",
                e
            )

            else -> e
        }
    }
    else -> e
}