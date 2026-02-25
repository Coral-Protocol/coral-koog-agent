package ai.coralprotocol.coral.koog.fullexample.util

enum class ExceptionFallback {
    EXCEPTION_CLASS,
    EXCEPTION_CLASS_AND_MESSAGE,
    TRUNCATED_NO_HIDDEN
}

fun Throwable.toAgentFriendlyResult(fallback: ExceptionFallback): String {
    return when (fallback) {
        ExceptionFallback.EXCEPTION_CLASS -> this::class.simpleName ?: "UnknownException"
        ExceptionFallback.EXCEPTION_CLASS_AND_MESSAGE -> {
            val className = this::class.simpleName ?: "UnknownException"
            val message = this.message ?: "No message"
            "$className: $message"
        }
        ExceptionFallback.TRUNCATED_NO_HIDDEN -> {
            val sb = StringBuilder()
            val className = this::class.qualifiedName ?: this::class.simpleName ?: "UnknownException"
            sb.append("$className: ${this.message ?: "No message"}\n")
            this.stackTrace.take(5).forEach {
                sb.append("  at $it\n")
            }
            if (this.cause != null) {
                val causeClassName = this.cause!!::class.qualifiedName ?: this.cause!!::class.simpleName ?: "UnknownException"
                sb.append("Caused by: $causeClassName: ${this.cause!!.message ?: "No message"}\n")
            }
            sb.toString().trim()
        }
    }
}
