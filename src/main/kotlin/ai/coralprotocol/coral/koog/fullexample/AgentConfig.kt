package ai.coralprotocol.coral.koog.fullexample

import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import ai.coralprotocol.coral.koog.fullexample.util.coral.tunnel.TunnelSettings //{CORALIZER:TUNNEL_IMPORT}

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
    val sendClaims = env.getOptional("CORAL_SEND_CLAIMS")?.toInt() ?: 0

    // note: we're masking some unused coral envs here. Feel free to change if multiple models are used.
    val modelChoice = env.get("MODEL_CHOICE")
    val modelProxyUrl = env.get("CORAL_PROXY_URL_$modelChoice")
    val modelProxyModel = env.get("CORAL_PROXY_MODEL_$modelChoice")
    val modelProxyFormat = env.get("CORAL_PROXY_FORMAT_$modelChoice")
    val modelProxyProvider = env.get("CORAL_PROXY_PROVIDER_$modelChoice")
}


data class ResolvedAgentSettings(val env: EnvironmentOptionProvider) {
    val systemPrompt = env["SYSTEM_PROMPT"]
    val extraSystemPrompt = env["EXTRA_SYSTEM_PROMPT"]
    val extraInitialUserPrompt = env["EXTRA_INITIAL_USER_PROMPT"]
    val followUpUserPrompt = env["FOLLOWUP_USER_PROMPT"]
    val maxIterations = env.get("MAX_ITERATIONS").toInt()
    val maxTokens = env.get("MAX_TOKENS").toInt()
    val iterationDelayMs = env.get("ITERATION_DELAY_MS").toInt().milliseconds

    val coral = CoralSettings(env)

    // {CORALIZER:TUNNEL_PROPERTY_START}
    /** Non-null when this agent is being tunneled through a cloud wrapper. */
    val tunnel: TunnelSettings? by lazy {
        val url = env.getOptional("TUNNEL_SERVER_URL")?.takeIf { it.isNotBlank() }
        val uuid = env.getOptional("TUNNEL_UUID")?.takeIf { it.isNotBlank() }
        val key = env.getOptional("TUNNEL_PUBLIC_KEY")?.takeIf { it.isNotBlank() }
        if (url != null && uuid != null && key != null) {
            TunnelSettings(serverUrl = url, uuid = uuid, publicKey = key)
        } else null
    }
    // {CORALIZER:TUNNEL_PROPERTY_END}
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
