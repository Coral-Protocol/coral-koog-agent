package ai.coralprotocol.coral.koog.fullexample.util.coral

import ai.coralprotocol.coral.koog.fullexample.devEnvLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Writes the environment this agent was started with in a session (its options and CORAL_* variables, nothing else)
 * to `coral-agent.dev.env`, then idles so the session stays alive. DevMain.kt can then run the agent from an IDE,
 * connected to that session.
 *
 * Enabled by the DEV_CAPTURE_ENV option, which only makes sense with the executable runtime.
 * The captured agent secret is only valid for that session, so give the session a long TTL.
 */
fun captureDevEnvAndWait() {
    // This should be run via Coral's orchestrator only.
    // Continuing otherwise risks accidental env var leakage.
    if (System.getenv()["CORAL_RUNTIME_ID"] == null) {
        throw IllegalStateException("DEV_CAPTURE_ENV should only be set on an agent started by Coral Server.")
    }
    val tomlFile = File("coral-agent.toml")
    if (!tomlFile.exists()) {
        println("Error: coral-agent.toml not found in the root of the project.")
        return
    }

    val gitignoreFile = File(".gitignore")
    if (!gitignoreFile.exists()) {
        println("Error: .gitignore not found in the root of the project.")
        return
    }

    val gitignoreContent = gitignoreFile.readText()
    if (!gitignoreContent.contains("coral-agent.dev.env")) {
        println("Error: .gitignore does not contain 'coral-agent.dev.env'. Please add it to avoid committing secrets.")
        return
    }

    val tomlLines = tomlFile.readLines()
    val optionsKeys = mutableSetOf<String>()
    var inOptions = false
    for (line in tomlLines) {
        val trimmed = line.trim()
        val lineWithoutComment = trimmed.substringBefore("#").trim()
        if (lineWithoutComment.startsWith("[") && lineWithoutComment.endsWith("]")) {
            val section = lineWithoutComment.removeSurrounding("[", "]").trim()
            if (section == "options") {
                inOptions = true
            } else if (section.startsWith("options.")) {
                val key = section.substringAfter("options.").trim()
                if (key.isNotEmpty()) optionsKeys.add(key)
                inOptions = false
            } else {
                inOptions = false
            }
            continue
        }

        if (inOptions && lineWithoutComment.contains("=")) {
            val key = lineWithoutComment.substringBefore("=").trim()
            if (key.isNotEmpty()) {
                optionsKeys.add(key)
            }
        }
    }

    val devEnvFile = File("coral-agent.dev.env")
    
    val devEnvLines = System.getenv()
        .filter { (key, _) ->
            (key in optionsKeys || key.startsWith("CORAL_")) && key != "DEV_CAPTURE_ENV"
        }
        .map { (key, value) -> devEnvLine(key, value) }

    devEnvFile.writeText(devEnvLines.joinToString("\n"))
    println("Successfully created coral-agent.dev.env with ${devEnvLines.size} environment variables.")
    println("You may now run DevMain.kt from your IDE to connect to the session as this agent.")
    println("This process will keep running to keep the session alive.")

    runBlocking {
        delay(Long.MAX_VALUE)
    }
}
