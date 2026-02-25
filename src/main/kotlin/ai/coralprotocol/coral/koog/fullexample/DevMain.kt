package ai.coralprotocol.coral.koog.fullexample

import java.io.File

/**
 * This main method is for running the agent with the local dev env file.
 *
 * This is only meant to be ran via IDEs.
 */
fun main() {
    if(!File("coral-agent.dev.env").exists()) throw IllegalStateException("coral-agent.dev.env not found in the root of the project." +
            "\n This main method is meant to supply environment variables through this file. See Main.kt to create this file or run the agent normally through coral.")
    val settings: ResolvedAgentSettings = AgentSettingsLoader.load(useDevEnv = true)
    runAgent(settings)
}
