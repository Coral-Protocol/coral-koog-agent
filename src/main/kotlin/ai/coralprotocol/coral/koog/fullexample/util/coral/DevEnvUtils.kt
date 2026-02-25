package ai.coralprotocol.coral.koog.fullexample.util.coral

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

fun mainPrintDevEnv() {
    // This should be ran via Coral's orchestrator only.
    // Continuing otherwise risks accidental env var leakage.
    if (System.getenv()["CORAL_RUNTIME_ID"] == null) {
        throw IllegalStateException("This script should be ran via Coral's orchestrator only." +
                "\nYou probably need to switch the main method back.")
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

    val devEnvFile = File("coral-agent.dev.env")
    
    val devEnvLines = System.getenv().map { (key, value) ->
        "$key=\"${value.replace("\"", "\\\"")}\""
    }

    devEnvFile.writeText(devEnvLines.joinToString("\n"))
    println("Successfully created coral-agent.dev.env with ${devEnvLines.size} environment variables.")
    println("You may now switch main method back and run the application through your preferred IDE to connect to the session.")
    println("This process will keep running to keep the session alive.")
    println("If you see this message in an IDE, you have it backwards (and your IDE is loading the .env), create a session with this agent and then switch main methods.")

    runBlocking {
        delay(Long.MAX_VALUE)
    }
}
