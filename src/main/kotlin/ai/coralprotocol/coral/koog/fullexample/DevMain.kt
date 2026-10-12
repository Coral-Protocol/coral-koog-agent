package ai.coralprotocol.coral.koog.fullexample

import java.io.File

/**
 * This main method is for running the agent with the local dev env file, so you can debug it from an IDE while it
 * takes part in a real session.
 *
 * To create coral-agent.dev.env, create a session with this agent and its DEV_CAPTURE_ENV option set to true
 * (see scripts/quick-session.sh). That agent instance writes the file and then idles to keep the session alive.
 *
 * This is only meant to be run via IDEs.
 */
fun main() {
    if (!File("coral-agent.dev.env").exists()) throw IllegalStateException(
        "coral-agent.dev.env not found in the root of the project.\n" +
                "Create a session with this agent and its DEV_CAPTURE_ENV option set to true to create it (see DevMain.kt)."
    )
    val settings: ResolvedAgentSettings = AgentSettingsLoader.load(useDevEnv = true)
    runAgent(settings)
}
