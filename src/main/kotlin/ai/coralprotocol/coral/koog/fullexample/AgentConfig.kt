package ai.coralprotocol.coral.koog.fullexample

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Minimal config loader that reads values from environment variables or a dev env file.
 */
object AgentSettingsLoader {
    fun load(useDevEnv: Boolean = true): ResolvedAgentSettings {
        return ResolvedAgentSettings(CoralOptionProvider(useDevEnv))
    }
}


/**
 * Settings that Coral Server provides to every orchestrated agent.
 * These are not encouraged to be modified but are still loaded from the environment/dev env.
 *
 * See https://docs.coralos.ai/guides/writing-agents#orchestration-environment
 */
data class CoralSettings(private val env: EnvironmentOptionProvider) {
    val agentId = env["CORAL_AGENT_ID"]
    val agentSecret = env["CORAL_AGENT_SECRET"]
    val apiUrl = env["CORAL_API_URL"]
    val connectionUrl = env["CORAL_CONNECTION_URL"]
    val runtimeId = env["CORAL_RUNTIME_ID"]
    val sessionId = env["CORAL_SESSION_ID"]

    /** Extra system instructions the application supplied when creating the session, if any. */
    val sessionSystemPrompt = env.getOptional("CORAL_PROMPT_SYSTEM")

    /**
     * One of the `[[llm.proxies]]` declared in coral-agent.toml, chosen by the MODEL_CHOICE option.
     * Coral Server provides a URL, model and wire format for each declared proxy.
     */
    val modelChoice = env["MODEL_CHOICE"]
    val modelProxyUrl = env["CORAL_PROXY_URL_$modelChoice"]
    val modelProxyModel = env["CORAL_PROXY_MODEL_$modelChoice"]
    val modelProxyFormat = env["CORAL_PROXY_FORMAT_$modelChoice"]
}


data class ResolvedAgentSettings(val env: EnvironmentOptionProvider) {
    val systemPrompt = env["SYSTEM_PROMPT"]
    val extraSystemPrompt = env["EXTRA_SYSTEM_PROMPT"]
    val extraInitialUserPrompt = env["EXTRA_INITIAL_USER_PROMPT"]
    val followUpUserPrompt = env["FOLLOWUP_USER_PROMPT"]
    val maxIterations = env["MAX_ITERATIONS"].toInt()
    val maxTokens = env["MAX_TOKENS"].toLong()
    val iterationDelayMs = env["ITERATION_DELAY_MS"].toLong().milliseconds
    val contextCompressionTokens = env["CONTEXT_COMPRESSION_TOKENS"].toLong()
    val reasoningEffort = env["REASONING_EFFORT"]
    val devCaptureEnv = env.getOptional("DEV_CAPTURE_ENV") == "1"

    val coral = CoralSettings(env)
}

/** coral-agent.dev.env lines are `KEY=<JSON string>`, so multiline values (e.g. SYSTEM_PROMPT) fit on one line. */
fun devEnvLine(key: String, value: String): String = "$key=${Json.encodeToString(value)}"

fun parseDevEnv(lines: List<String>): Map<String, String> = lines
    .filter { it.contains("=") && !it.startsWith("#") }
    .associate { line ->
        val (rawKey, value) = line.split("=", limit = 2)
        val key = rawKey.trim()
        val decoded = try {
            Json.decodeFromString<String>(value.trim())
        } catch (e: SerializationException) {
            // The value isn't echoed: it may be the agent secret
            throw IllegalArgumentException(
                "coral-agent.dev.env: the value of $key must be a JSON string, e.g. $key=\"value\". " +
                        "Recreate the file with the DEV_CAPTURE_ENV option rather than editing it by hand.", e
            )
        }
        key to decoded
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
                parseDevEnv(file.readLines())
            } else {
                emptyMap()
            }

        } else {
            emptyMap()
        }
    }

    override fun get(name: String): String {
        return getOptional(name)
            ?: throw IllegalArgumentException(
                "Environment variable $name is required but not set (and not found in $devEnvFile).\n" +
                        "This agent is meant to be started by Coral Server, which provides its options and CORAL_* " +
                        "variables. Create a session with it (see scripts/quick-session.sh), or see DevMain.kt to run it from an IDE."
            )
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
