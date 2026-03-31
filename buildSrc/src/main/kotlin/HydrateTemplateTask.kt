import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File

/**
 * Gradle task that "hydrates" this Coral Koog agent template into a concrete project.
 *
 * Interactive usage:
 *   ./gradlew hydrate
 *
 * Non-interactive usage (all parameters via CLI options):
 *   ./gradlew hydrate --agentName=my-cool-agent --packageName=com.example.myagent
 *
 * Or via Gradle properties:
 *   ./gradlew hydrate -PagentName=my-cool-agent -PpackageName=com.example.myagent
 */
abstract class HydrateTemplateTask : DefaultTask() {

    companion object {
        /** The package that currently exists in the template source tree. */
        const val TEMPLATE_PACKAGE = "ai.coralprotocol.coral.koog.fullexample"

        /** The default group used in the template build.gradle.kts. */
        const val TEMPLATE_GROUP = "ai.coralprotocol"

        /** The agent name used in the template coral-agent.toml [agent] section. */
        const val TEMPLATE_AGENT_NAME = "koog-template-agents"

        /** The project / binary name used in settings.gradle.kts and graalvmNative. */
        const val TEMPLATE_PROJECT_NAME = "koog-coral-agent"
    }

    private var agentNameValue: String = ""
    private var packageNameValue: String = ""

    @Option(option = "agentName", description = "Name for the agent (kebab-case, e.g. my-cool-agent)")
    fun setAgentName(value: String) { agentNameValue = value }

    @Option(option = "packageName", description = "Java/Kotlin package name (e.g. com.example.myagent)")
    fun setPackageName(value: String) { packageNameValue = value }

    @TaskAction
    fun hydrate() {
        val rootDir = project.rootDir

        // Resolve parameters: CLI option > Gradle property > interactive prompt
        val agentName = resolveParam("agentName", agentNameValue, "Agent name (kebab-case, e.g. my-cool-agent)")
        val packageName = resolveParam("packageName", packageNameValue, "Package name (e.g. com.example.myagent)")

        validate(agentName, packageName)

        logger.quiet("")
        logger.quiet("╔══════════════════════════════════════════════════╗")
        logger.quiet("║          Hydrating Coral Koog Agent Template     ║")
        logger.quiet("╠══════════════════════════════════════════════════╣")
        logger.quiet("║  Agent name:   ${agentName.padEnd(33)}║")
        logger.quiet("║  Package name: ${packageName.padEnd(33)}║")
        logger.quiet("╚══════════════════════════════════════════════════╝")
        logger.quiet("")

        // Derive values from inputs
        val group = packageName.split(".").take(
            // Use first 2-3 segments as group, or the full package if <= 3 segments
            minOf(packageName.split(".").size, 3)
        ).joinToString(".")
        val mainClassFqn = "$packageName.MainKt"
        val templatePackagePath = TEMPLATE_PACKAGE.replace('.', '/')
        val newPackagePath = packageName.replace('.', '/')

        // 1. Update build.gradle.kts
        logger.quiet("→ Updating build.gradle.kts")
        updateFile(rootDir.resolve("build.gradle.kts")) { content ->
            content
                .replaceLineContaining("{CORALIZER:BUILD_GROUP}") {
                    "group = \"$group\" //{CORALIZER:BUILD_GROUP}"
                }
                .replaceLineContaining("{CORALIZER:APPLICATION_MAIN_CLASS}") {
                    "    mainClass.set(\"$mainClassFqn\") //{CORALIZER:APPLICATION_MAIN_CLASS}"
                }
                .replaceLineContaining("{CORALIZER:NATIVE_BINARY_IMAGE_NAME}") {
                    "            imageName.set(\"$agentName\") //{CORALIZER:NATIVE_BINARY_IMAGE_NAME}"
                }
                .replaceLineContaining("{CORALIZER:NATIVE_BINARY_MAIN_CLASS}") {
                    "            mainClass.set(\"$mainClassFqn\") //{CORALIZER:NATIVE_BINARY_MAIN_CLASS}"
                }
        }

        // 2. Update settings.gradle.kts
        logger.quiet("→ Updating settings.gradle.kts")
        updateFile(rootDir.resolve("settings.gradle.kts")) { content ->
            content.replaceLineContaining("{CORALIZER:ROOT_PROJECT_NAME}") {
                "rootProject.name = \"$agentName\" //{CORALIZER:ROOT_PROJECT_NAME}"
            }
        }

        // 3. Update coral-agent.toml
        logger.quiet("→ Updating coral-agent.toml")
        updateFile(rootDir.resolve("coral-agent.toml")) { content ->
            content
                .replace("name = \"$TEMPLATE_AGENT_NAME\"", "name = \"$agentName\"")
                .replace(
                    "summary = \"A template agent built with Koog (Kotlin).\"",
                    "summary = \"A Coral agent built with Koog (Kotlin).\""
                )
                .replace(
                    "description = \"A base template for creating Coral agents using the Koog framework in Kotlin.\"",
                    "description = \"A Coral agent ($agentName) built with the Koog framework in Kotlin.\""
                )
                .replace("# Koog Template Agent", "# $agentName")
                .replace(
                    "This is a template for building autonomous agents in Kotlin using the Koog framework.",
                    "A Coral agent built with the Koog framework in Kotlin."
                )
                .replace(
                    "keywords = [\"template\", \"koog\", \"kotlin\", \"example\"]",
                    "keywords = [\"koog\", \"kotlin\", \"$agentName\"]"
                )
        }

        // 4. Update README.md
        logger.quiet("→ Updating README.md")
        updateFile(rootDir.resolve("README.md")) { content ->
            content.replace("# Koog Coral Agent (Kotlin)", "# $agentName")
        }

        // 5. Rename package in all Kotlin source files
        logger.quiet("→ Renaming package in source files: $TEMPLATE_PACKAGE → $packageName")
        val srcRoot = rootDir.resolve("src/main/kotlin")
        val oldPackageDir = srcRoot.resolve(templatePackagePath)

        if (!oldPackageDir.exists()) {
            throw GradleException("Template source directory not found: $oldPackageDir")
        }

        // Update package declarations and imports in all .kt files
        oldPackageDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            updateFile(file) { content ->
                content.replace(TEMPLATE_PACKAGE, packageName)
            }
        }

        // 6. Move source files to new package directory using git mv
        val newPackageDir = srcRoot.resolve(newPackagePath)
        if (oldPackageDir.absolutePath != newPackageDir.absolutePath) {
            logger.quiet("→ Moving sources via git: $templatePackagePath → $newPackagePath")
            newPackageDir.parentFile.mkdirs()
            gitMove(rootDir, oldPackageDir, newPackageDir)
            // Clean up empty parent directories left behind by git mv
            gitCleanEmptyDirs(rootDir, srcRoot, oldPackageDir)
        }

        // 7. Clean up compiled output from old package
        val outDir = rootDir.resolve("out")
        if (outDir.exists()) {
            logger.quiet("→ Cleaning old compiled output in out/")
            gitCleanDirectory(rootDir, "out")
        }

        // 8. Remove git remote origin (template was likely cloned)
        removeGitRemoteOrigin(rootDir)

        // 9. Self-destruct: remove hydrator code
        logger.quiet("→ Removing hydrator code")
        gitRemove(rootDir, "buildSrc")
        gitRemove(rootDir, "package.json")
        gitRemove(rootDir, "create-koog.js")
        updateFile(rootDir.resolve("Dockerfile")) { content ->
            content.lines()
                .filter { !it.contains("COPY buildSrc ./buildSrc") }
                .joinToString("\n")
                .trimEnd() + "\n"
        }
        updateFile(rootDir.resolve("build.gradle.kts")) { content ->
            val lines = content.lines()
            val newLines = mutableListOf<String>()
            var inHydrateBlock = false
            for (line in lines) {
                // Remove console fix block
                if (line.contains("Ensure ./gradlew hydrate is not interrupted by gradle execution updates")) {
                    inHydrateBlock = true
                    continue
                }
                if (line.contains("tasks.register<HydrateTemplateTask>(\"hydrate\")")) {
                    inHydrateBlock = true
                    continue
                }
                if (inHydrateBlock) {
                    if (line.trim() == "}") {
                        inHydrateBlock = false
                    }
                    continue
                }
                newLines.add(line)
            }
            newLines.joinToString("\n").trimEnd() + "\n"
        }

        // 10. Commit changes
        logger.quiet("→ Committing hydrated state")
        gitCommit(rootDir, agentName)

        logger.quiet("")
        logger.quiet("✅ Template hydrated successfully!")
        logger.quiet("   Agent name:   $agentName")
        logger.quiet("   Package:      $packageName")
        logger.quiet("   Main class:   $mainClassFqn")
        logger.quiet("")
        logger.quiet("Next steps:")
        logger.quiet("  1. Review the changes")
        logger.quiet("  2. Set a new git remote:  git remote add origin <your-repo-url>")
        logger.quiet("  3. Run: ./gradlew build")
        logger.quiet("  4. Run: ./gradlew run")
        logger.quiet("")
    }

    private fun resolveParam(name: String, cliValue: String, prompt: String): String {
        // 1. CLI option (--agentName=...)
        if (cliValue.isNotBlank()) return cliValue.trim()

        // 2. Gradle property (-PagentName=...)
        val prop = project.findProperty(name)?.toString()
        if (!prop.isNullOrBlank()) return prop.trim()

        // 3. Interactive prompt
        print("$prompt: ")
        System.out.flush()
        val input = try {
            // System.console() is null when stdin is not a terminal (e.g. IDE run)
            System.console()?.readLine() ?: readlnOrNull()
        } catch (_: Exception) {
            null
        }

        if (input.isNullOrBlank()) {
            throw GradleException("Parameter '$name' is required. Provide it via --$name=<value> or -P$name=<value>")
        }
        return input.trim()
    }

    private fun validate(agentName: String, packageName: String) {
        // Validate agent name: kebab-case with optional dots
        if (!agentName.matches(Regex("^[a-z][a-z0-9]*(-[a-z0-9]+)*$"))) {
            throw GradleException(
                "Invalid agent name: '$agentName'. Use kebab-case (e.g. 'my-cool-agent'). " +
                "Must start with a lowercase letter, contain only lowercase letters, digits, and hyphens."
            )
        }

        // Validate package name
        if (!packageName.matches(Regex("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$"))) {
            throw GradleException(
                "Invalid package name: '$packageName'. Must be a valid Java package name " +
                "(e.g. 'com.example.myagent'). Only lowercase letters, digits, and dots."
            )
        }

        if (packageName == TEMPLATE_PACKAGE) {
            throw GradleException("Package name is the same as the template default. Please choose a different package name.")
        }
    }

    /**
     * Read a file, apply a transformation, and write it back.
     */
    private fun updateFile(file: File, transform: (String) -> String) {
        if (!file.exists()) {
            logger.warn("  ⚠ File not found, skipping: ${file.relativeTo(project.rootDir)}")
            return
        }
        val original = file.readText()
        val updated = transform(original)
        if (original != updated) {
            file.writeText(updated)
            logger.quiet("  ✓ Updated: ${file.relativeTo(project.rootDir)}")
        }
    }

    /**
     * Replace a line containing a specific marker with a new line.
     */
    private fun String.replaceLineContaining(marker: String, replacement: () -> String): String {
        return lines().joinToString("\n") { line ->
            if (line.contains(marker)) replacement() else line
        }
    }

    /**
     * Remove the git remote "origin" so the hydrated project is no longer linked to the template repo.
     */
    private fun removeGitRemoteOrigin(rootDir: File) {
        val gitDir = rootDir.resolve(".git")
        if (!gitDir.exists()) {
            logger.quiet("→ No .git directory found, skipping remote removal")
            return
        }

        try {
            val process = ProcessBuilder("git", "remote", "remove", "origin")
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                logger.quiet("→ Removed git remote 'origin' (template repository link)")
            } else if (output.contains("No such remote") || output.contains("could not remove")) {
                logger.quiet("→ No git remote 'origin' found, nothing to remove")
            } else {
                logger.warn("  ⚠ Failed to remove git remote 'origin': $output")
            }
        } catch (e: Exception) {
            logger.warn("  ⚠ Could not run 'git remote remove origin': ${e.message}")
        }
    }

    /**
     * Move a directory using `git mv`. Falls back to regular move if git is unavailable.
     */
    private fun gitMove(rootDir: File, source: File, target: File) {
        val relSource = source.relativeTo(rootDir).path
        val relTarget = target.relativeTo(rootDir).path
        try {
            val process = ProcessBuilder("git", "mv", relSource, relTarget)
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                logger.quiet("  ✓ git mv $relSource → $relTarget")
            } else {
                throw RuntimeException("git mv failed (exit $exitCode): $output")
            }
        } catch (e: Exception) {
            logger.warn("  ⚠ git mv failed, falling back to file move: ${e.message}")
            target.mkdirs()
            source.walkTopDown().forEach { file ->
                val relativePath = file.relativeTo(source)
                val dest = target.resolve(relativePath)
                if (file.isDirectory) {
                    dest.mkdirs()
                } else {
                    dest.parentFile.mkdirs()
                    file.copyTo(dest, overwrite = true)
                }
            }
            source.deleteRecursively()
        }
    }

    /**
     * Clean up empty parent directories left behind after git mv, walking up to (but not including) stopAt.
     */
    private fun gitCleanEmptyDirs(rootDir: File, stopAt: File, start: File) {
        var dir = if (start.exists()) start else start.parentFile
        while (dir != null && dir != stopAt && dir.startsWith(stopAt)) {
            val contents = dir.listFiles()
            if (dir.exists() && (contents == null || contents.isEmpty())) {
                val relDir = dir.relativeTo(rootDir).path
                dir.delete()
                logger.quiet("  ✓ Removed empty directory: $relDir")
                dir = dir.parentFile
            } else {
                break
            }
        }
    }

    /**
     * Clean a directory using `git clean -fdx` (removes untracked and ignored files)
     * and `git rm -rf` (removes tracked files). Falls back to deleteRecursively.
     */
    private fun gitCleanDirectory(rootDir: File, relPath: String) {
        try {
            gitRemove(rootDir, relPath)

            // Remove any untracked/ignored files in the directory
            val cleanProcess = ProcessBuilder("git", "clean", "-fdx", "--", relPath)
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            cleanProcess.inputStream.bufferedReader().readText()
            cleanProcess.waitFor()

            // Remove the directory itself if still present
            val dir = rootDir.resolve(relPath)
            if (dir.exists() && dir.isDirectory) {
                dir.delete()
            }

            logger.quiet("  ✓ Cleaned $relPath via git")
        } catch (e: Exception) {
            logger.warn("  ⚠ git clean failed, falling back to deleteRecursively: ${e.message}")
            rootDir.resolve(relPath).deleteRecursively()
        }
    }

    private fun gitRemove(rootDir: File, relPath: String) {
        try {
            val process = ProcessBuilder("git", "rm", "-rf", "--ignore-unmatch", relPath)
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText()
            process.waitFor()
        } catch (e: Exception) {
            logger.warn("  ⚠ git rm failed: ${e.message}")
        }
    }

    private fun gitCommit(rootDir: File, agentName: String) {
        try {
            // Stage everything
            ProcessBuilder("git", "add", ".").directory(rootDir).start().waitFor()

            // Commit with CoralOS identity
            val commitProcess = ProcessBuilder(
                "git",
                "-c", "user.name=CoralOS",
                "-c", "user.email=kooghydrator@coralos.ai",
                "commit",
                "-m", "Hydrate template: $agentName"
            )
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            
            val output = commitProcess.inputStream.bufferedReader().readText().trim()
            val exitCode = commitProcess.waitFor()
            
            if (exitCode == 0) {
                logger.quiet("  ✓ Created hydration commit as CoralOS")
            } else {
                logger.warn("  ⚠ Git commit failed: $output")
            }
        } catch (e: Exception) {
            logger.warn("  ⚠ Could not perform git commit: ${e.message}")
        }
    }
}
