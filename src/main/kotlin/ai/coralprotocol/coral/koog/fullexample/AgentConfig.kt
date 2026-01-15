package ai.coralprotocol.coral.koog.fullexample

/**
 * Minimal config loader that reads values from environment variables.
 */
object AgentSettingsLoader {
    fun load(env: EnvironmentProvider = SystemEnvironmentProvider): ResolvedAgentSettings {
        return ResolvedAgentSettings(
            modelApiKey = env.getNeeded("MODEL_API_KEY"),
            modelProviderUrl = env.getNeeded("MODEL_PROVIDER_URL"),
            modelId = env.getNeeded("MODEL_ID"),
            systemPrompt = env.getNeeded("SYSTEM_PROMPT"),
            extraInitialUserPrompt = env.getNeeded("EXTRA_INITIAL_USER_PROMPT"),
            maxIterations = env.getNeeded("MAX_ITERATIONS").toInt(),
            followUpUserPrompt = env.getNeeded("FOLLOWUP_USER_PROMPT"),
            extraSystemPrompt = env.getNeeded("EXTRA_SYSTEM_PROMPT"),
            serverUrl = env.getNeeded("CORAL_CONNECTION_URL"),
        )
    }
}

data class ResolvedAgentSettings(
    val modelApiKey: String, // option commented out in coral-agent.toml for convenience
    val modelProviderUrl: String, // option commented out in coral-agent.toml for convenience
    val modelId: String, // option commented out in coral-agent.toml for convenience
    val systemPrompt: String,
    val extraSystemPrompt: String,
    val extraInitialUserPrompt: String,
    val followUpUserPrompt: String,
    val maxIterations: Int,
    val serverUrl: String, // not an option, but comes in through env var anyway
)

interface EnvironmentProvider {
    operator fun get(name: String): String?

    /**
     * Returns the value of the environment variable [name], or throws an exception if not set.
     * If a default is specified in coral-agent.toml (even if it defaults to a blank string),
     * it's appropriate to use this to indicate to the type system that the value is always present.
     */
    fun getNeeded(name: String): String
}

object SystemEnvironmentProvider : EnvironmentProvider {
    override fun get(name: String): String? = System.getenv(name)
    override fun getNeeded(name: String): String = System.getenv(name)
        ?: throw IllegalArgumentException("Environment variable $name is required but not set")
}
