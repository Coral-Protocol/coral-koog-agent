package ai.coralprotocol.coral.koog.fullexample

import ai.coralprotocol.coral.koog.fullexample.util.ModelProvider
import java.io.File

/**
 * Minimal config loader that reads values from environment variables or a dev env file.
 */
object AgentSettingsLoader {
    fun load(useDevEnv: Boolean = true): ResolvedAgentSettings {
        return ResolvedAgentSettings(CoralOptionProvider(useDevEnv))
    }
}


/**
 * Common Coral settings that are typically provided by the environment.
 * These are not encouraged to be modified but are still loaded from the environment/dev env.
 */
data class CoralSettings(private val env: EnvironmentOptionProvider) {
    val agentId = env["CORAL_AGENT_ID"]
    val agentSecret = env["CORAL_AGENT_SECRET"]
    val apiUrl = env["CORAL_API_URL"]
    val connectionUrl = env["CORAL_CONNECTION_URL"]
    val runtimeId = env["CORAL_RUNTIME_ID"]
    val sessionId = env["CORAL_SESSION_ID"]
    val sendClaims = env["CORAL_SEND_CLAIMS"].toDouble().toInt()
}

data class ResolvedAgentSettings(private val env: EnvironmentOptionProvider) {
    val modelApiKey = env["MODEL_API_KEY"]
    val modelProvider = ModelProvider.entries.find {
        it.name.equals(env["MODEL_PROVIDER"], ignoreCase = true)
    }
        ?: throw IllegalArgumentException("Invalid MODEL_PROVIDER, must be one of ${ModelProvider.entries.joinToString { it.name }}")
    val modelId = env["MODEL_ID"]

    val modelProviderUrlOverride = env["MODEL_PROVIDER_URL_OVERRIDE"].ifEmpty { null }
    val systemPrompt = env["SYSTEM_PROMPT"]
    val extraSystemPrompt = env["EXTRA_SYSTEM_PROMPT"]
    val extraInitialUserPrompt = env["EXTRA_INITIAL_USER_PROMPT"]
    val followUpUserPrompt = env["FOLLOWUP_USER_PROMPT"]
    val maxIterations = env.getOptional("MAX_ITERATIONS")?.toInt() ?: 20
    val maxTokens = env.getOptional("MAX_TOKENS")?.toLong() ?: 20000L
    val iterationDelayMs = env.getOptional("ITERATION_DELAY_MS")?.toLong() ?: 0L

    val coral = CoralSettings(env)
}

interface EnvironmentOptionProvider {
    /**
     *  Gets an option via environment variable or fallback.
     *  @return non-nullable string for the option.
     *  @throws IllegalArgumentException if the option is not found.
     */
    operator fun get(name: String): String

    fun getOptional(name: String): String?
}

class CoralOptionProvider(useDevEnv: Boolean = true) :
    EnvironmentOptionProvider {
    private val devEnvFile = "coral-agent.dev.env"
    private val devEnv: Map<String, String> by lazy {
        if (useDevEnv) {
            val file = File(devEnvFile)
            if (file.exists()) {
                println("Informing: Reading from $devEnvFile")
                file.readLines()
                    .filter { it.contains("=") && !it.startsWith("#") }
                    .associate { line ->
                        val (key, value) = line.split("=", limit = 2)
                        key.trim() to value.trim().removeSurrounding("\"")
                    }
            } else {
                emptyMap()
            }

        } else {
            emptyMap()
        }
    }

    override fun get(name: String): String {
        return getOptional(name)
            ?: throw IllegalArgumentException("Environment variable $name is required but not set (and not found in $devEnvFile)")
    }

    override fun getOptional(name: String): String? {
        val systemValue = System.getenv(name)
        val devValue = devEnv[name]
        return if (systemValue != null) {
            if (devValue != null && systemValue != devValue) {
                println("Warning: Environment variable $name is overriding the value in $devEnvFile")
            }
            systemValue
        } else {
            devValue
        }
    }
}
