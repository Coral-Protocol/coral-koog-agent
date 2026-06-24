import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File

abstract class HydrateTemplateTask : DefaultTask() {

    companion object {
        const val TEMPLATE_PACKAGE = "ai.coralprotocol.coral.koog.fullexample"
        const val TEMPLATE_GROUP = "ai.coralprotocol"
        const val TEMPLATE_AGENT_NAME = "coral-koog-agent"
        const val TEMPLATE_PROJECT_NAME = "coral-koog-agent"
    }

    private var agentNameValue: String = ""
    private var packageNameValue: String = ""
    private var keepWorkflowsValue: String = ""
    private var keepPublishJvmValue: String = ""
    private var keepPublishNativeValue: String = ""
    private var keepReleasePleaseValue: String = ""

    @Option(option = "agentName", description = "Name for the agent (kebab-case, e.g. my-cool-agent)")
    fun setAgentName(value: String) {
        agentNameValue = value
    }

    @Option(option = "packageName", description = "Java/Kotlin package name (e.g. com.example.myagent)")
    fun setPackageName(value: String) {
        packageNameValue = value
    }

    @Option(option = "keepWorkflows", description = "Whether to keep each GitHub workflow (true/false/prompt)")
    fun setKeepWorkflows(value: String) {
        keepWorkflowsValue = value
    }

    @Option(option = "keepPublishJvm", description = "Whether to keep the publish-jvm workflow (true/false)")
    fun setKeepPublishJvm(value: String) {
        keepPublishJvmValue = value
    }

    @Option(option = "keepPublishNative", description = "Whether to keep the publish-native workflow (true/false)")
    fun setKeepPublishNative(value: String) {
        keepPublishNativeValue = value
    }

    @Option(option = "keepReleasePlease", description = "Whether to keep the release-please workflow (true/false)")
    fun setKeepReleasePlease(value: String) {
        keepReleasePleaseValue = value
    }

    @TaskAction
    fun hydrate() {
        val rootDir = project.rootDir
        val agentName = resolveParam("agentName", agentNameValue, "Agent name (kebab-case, e.g. my-cool-agent)")
        val packageName = resolveParam("packageName", packageNameValue, "Package name (e.g. com.example.myagent)")

        validate(agentName, packageName)

        val group = deriveGroup(packageName)
        val mainClassFqn = "$packageName.MainKt"

        logStart(agentName, packageName)
        updateBuildFile(rootDir, group, agentName, mainClassFqn)
        updateSettingsFile(rootDir, agentName)
        updateAgentManifest(rootDir, agentName)
        updateReadme(rootDir, agentName)
        updateQuickSessionScript(rootDir, agentName)
        renameSourcePackage(rootDir, packageName)
        manageWorkflows(rootDir)
        cleanCompiledOutput(rootDir)
        removeGitRemoteOrigin(rootDir)
        gitCreateBranch(rootDir, "main")
        removeHydratorArtifacts(rootDir)

        logStep("Committing hydrated state")
        gitCommit(rootDir, agentName)
        logCompletion(agentName, packageName, mainClassFqn)
    }

    private fun deriveGroup(packageName: String): String {
        val segments = packageName.split(".")
        return segments.take(minOf(segments.size, 3)).joinToString(".")
    }

    private fun logStart(agentName: String, packageName: String) {
        logger.quiet("")
        logger.quiet("Hydrating Coral Koog Agent Template")
        logger.quiet("Agent name:    $agentName")
        logger.quiet("Package name:  $packageName")
        if (keepWorkflowsValue.isNotBlank()) logger.quiet("Keep workflows: $keepWorkflowsValue")
        if (keepPublishJvmValue.isNotBlank()) logger.quiet("Keep publish-jvm: $keepPublishJvmValue")
        if (keepPublishNativeValue.isNotBlank()) logger.quiet("Keep publish-native: $keepPublishNativeValue")
        if (keepReleasePleaseValue.isNotBlank()) logger.quiet("Keep release-please: $keepReleasePleaseValue")
        logger.quiet("")
    }

    private fun updateBuildFile(rootDir: File, group: String, agentName: String, mainClassFqn: String) {
        logStep("Updating build.gradle.kts")
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
    }

    private fun updateSettingsFile(rootDir: File, agentName: String) {
        logStep("Updating settings.gradle.kts")
        updateFile(rootDir.resolve("settings.gradle.kts")) { content ->
            content.replaceLineContaining("{CORALIZER:ROOT_PROJECT_NAME}") {
                "rootProject.name = \"$agentName\" //{CORALIZER:ROOT_PROJECT_NAME}"
            }
        }
    }

    private fun updateAgentManifest(rootDir: File, agentName: String) {
        logStep("Updating coral-agent.toml")
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
    }

    private fun manageWorkflows(rootDir: File) {
        val workflowsDir = rootDir.resolve(".github/workflows")
        if (!workflowsDir.exists() || !workflowsDir.isDirectory) return

        val workflows = workflowsDir.listFiles { f -> f.isFile && (f.extension == "yml" || f.extension == "yaml") } ?: return
        if (workflows.isEmpty()) return

        val globalKeep = parseKeepPolicy(keepWorkflowsValue.ifBlank {
            project.findProperty("keepWorkflows")?.toString() ?: ""
        })

        if (globalKeep == null) {
            logStep("GitHub workflows found. Specific options or prompting will be used.")
        } else {
            logStep("Managing GitHub workflows (global policy: ${if (globalKeep) "keep" else "remove"})")
        }

        workflows.sortedBy { it.name }.forEach { workflowFile ->
            val specificKeep = when (workflowFile.name) {
                "publish-jvm.yml" -> parseKeepPolicy(
                    keepPublishJvmValue.ifBlank { project.findProperty("keepPublishJvm")?.toString() ?: "" }
                )
                "publish-native.yml" -> parseKeepPolicy(
                    keepPublishNativeValue.ifBlank { project.findProperty("keepPublishNative")?.toString() ?: "" }
                )
                "release-please.yml" -> parseKeepPolicy(
                    keepReleasePleaseValue.ifBlank { project.findProperty("keepReleasePlease")?.toString() ?: "" }
                )
                else -> null
            }

            val keep = specificKeep ?: globalKeep ?: promptKeepWorkflow(workflowFile.name)

            if (!keep) {
                logStep("Removing workflow: ${workflowFile.name}")
                gitRemove(rootDir, ".github/workflows/${workflowFile.name}")
                // In case it's not in git, or git rm failed to remove from disk
                if (workflowFile.exists()) {
                    workflowFile.delete()
                }

                if (workflowFile.name == "release-please.yml") {
                    removeReleasePleaseConfig(rootDir)
                }
            }
        }

        // If .github/workflows is empty, remove it and .github if empty
        val remainingWorkflows = workflowsDir.listFiles()
        if (remainingWorkflows != null && remainingWorkflows.isEmpty()) {
            logStep("Removing empty workflows directory")
            workflowsDir.delete()
            val githubDir = workflowsDir.parentFile
            if (githubDir != null && githubDir.name == ".github" && githubDir.listFiles()?.isEmpty() == true) {
                githubDir.delete()
            }
        }
    }

    private fun parseKeepPolicy(value: String): Boolean? {
        return when (value.lowercase()) {
            "true", "yes", "all", "keep" -> true
            "false", "no", "none", "remove" -> false
            else -> null
        }
    }

    private fun removeReleasePleaseConfig(rootDir: File) {
        logStep("Removing release-please configuration files")
        val githubDir = rootDir.resolve(".github")
        val configFiles = listOf("release-please-config.json", "release-please-manifest.json")
        configFiles.forEach { fileName ->
            val file = githubDir.resolve(fileName)
            if (file.exists()) {
                gitRemove(rootDir, ".github/$fileName")
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    private fun promptKeepWorkflow(fileName: String): Boolean {
        print("Keep workflow '$fileName'? [Y/n]: ")
        System.out.flush()
        val input = try {
            System.console()?.readLine() ?: readlnOrNull()
        } catch (_: Exception) {
            null
        }
        return input.isNullOrBlank() || input.lowercase().startsWith("y")
    }

    private fun updateReadme(rootDir: File, agentName: String) {
        logStep("Updating README.md")
        val prependixFile = rootDir.resolve("post-hydrate-readme-prependix.md")
        val prependixContent = if (prependixFile.exists()) {
            prependixFile.readText().replace("# <agent name>", "# $agentName")
        } else {
            null
        }

        updateFile(rootDir.resolve("README.md")) { content ->
            var newContent = content.replace("# Koog Coral Agent (Kotlin)", "# $agentName")
            if (prependixContent != null) {
                val requirementsIdx = newContent.indexOf("## Requirements")
                val nextSectionIdx = newContent.indexOf("## ", requirementsIdx + 3)
                val requirementsSection = if (requirementsIdx != -1) {
                    if (nextSectionIdx != -1) {
                        newContent.substring(requirementsIdx, nextSectionIdx).trim()
                    } else {
                        newContent.substring(requirementsIdx).trim()
                    }
                } else {
                    ""
                }

                newContent = prependixContent + "\n" + requirementsSection
            }
            newContent.trimEnd() + "\n"
        }

        if (prependixFile.exists()) {
            logStep("Removing ${prependixFile.name}")
            gitRemove(rootDir, "post-hydrate-readme-prependix.md")
            if (prependixFile.exists()) {
                prependixFile.delete()
            }
        }
    }

    private fun updateQuickSessionScript(rootDir: File, agentName: String) {
        logStep("Updating scripts/quick-session.sh")
        updateFile(rootDir.resolve("scripts/quick-session.sh")) { content ->
            content.replace("AGENT_NAME=\${AGENT_NAME:-$TEMPLATE_AGENT_NAME}", "AGENT_NAME=\${AGENT_NAME:-$agentName}")
        }
    }

    private fun renameSourcePackage(rootDir: File, packageName: String) {
        val srcRoot = rootDir.resolve("src/main/kotlin")
        val templatePackagePath = TEMPLATE_PACKAGE.replace('.', '/')
        val newPackagePath = packageName.replace('.', '/')
        val oldPackageDir = srcRoot.resolve(templatePackagePath)

        logStep("Renaming package in source files: $TEMPLATE_PACKAGE -> $packageName")
        if (!oldPackageDir.exists()) {
            throw GradleException("Template source directory not found: $oldPackageDir")
        }

        oldPackageDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                updateFile(file) { content -> content.replace(TEMPLATE_PACKAGE, packageName) }
            }

        val newPackageDir = srcRoot.resolve(newPackagePath)
        if (oldPackageDir.absolutePath != newPackageDir.absolutePath) {
            logStep("Moving sources via git: $templatePackagePath -> $newPackagePath")
            newPackageDir.parentFile.mkdirs()
            gitMove(rootDir, oldPackageDir, newPackageDir)
            gitCleanEmptyDirs(rootDir, srcRoot, oldPackageDir)
        }
    }

    private fun cleanCompiledOutput(rootDir: File) {
        if (rootDir.resolve("out").exists()) {
            logStep("Cleaning old compiled output in out/")
            gitCleanDirectory(rootDir, "out")
        }
    }

    private fun removeHydratorArtifacts(rootDir: File) {
        logStep("Removing hydrator code")
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
            val newLines = mutableListOf<String>()
            var inHydrateBlock = false

            for (line in content.lines()) {
                if (line.contains("Ensure ./gradlew -q hydrate is not interrupted by gradle execution updates")) {
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
    }

    private fun logCompletion(agentName: String, packageName: String, mainClassFqn: String) {
        logger.quiet("")
        logger.quiet("Template hydrated successfully.")
        logger.quiet("Agent name: $agentName")
        logger.quiet("Package:    $packageName")
        logger.quiet("Main class: $mainClassFqn")
        logger.quiet("")
        logger.quiet("Next steps:")
        logger.quiet("1. Review the changes")
        logger.quiet("2. Set a new git remote: git remote add origin <your-repo-url>")
        logger.quiet("3. Run: ./gradlew -q build")
        logger.quiet("4. Run: ./gradlew -q run")
        logger.quiet("")
    }

    private fun logStep(message: String) {
        logger.quiet(message)
    }

    private fun resolveParam(name: String, cliValue: String, prompt: String): String {
        if (cliValue.isNotBlank()) return cliValue.trim()

        val prop = project.findProperty(name)?.toString()
        if (!prop.isNullOrBlank()) return prop.trim()

        print("$prompt: ")
        System.out.flush()
        val input = try {
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
        if (!agentName.matches(Regex("^[a-z][a-z0-9]*(-[a-z0-9]+)*$"))) {
            throw GradleException(
                "Invalid agent name: '$agentName'. Use kebab-case (e.g. 'my-cool-agent'). " +
                    "Must start with a lowercase letter, contain only lowercase letters, digits, and hyphens."
            )
        }

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

    private fun updateFile(file: File, transform: (String) -> String) {
        if (!file.exists()) {
            logger.warn("File not found, skipping: ${file.relativeTo(project.rootDir)}")
            return
        }

        val original = file.readText()
        val updated = transform(original)
        if (original != updated) {
            file.writeText(updated)
            logger.quiet("Updated: ${file.relativeTo(project.rootDir)}")
        }
    }

    private fun String.replaceLineContaining(marker: String, replacement: () -> String): String {
        return lines().joinToString("\n") { line ->
            if (line.contains(marker)) replacement() else line
        }
    }

    private fun removeGitRemoteOrigin(rootDir: File) {
        val gitDir = rootDir.resolve(".git")
        if (!gitDir.exists()) {
            logStep("No .git directory found, skipping remote removal")
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
                logStep("Removed git remote 'origin'")
            } else if (output.contains("No such remote") || output.contains("could not remove")) {
                logStep("No git remote 'origin' found")
            } else {
                logger.warn("Failed to remove git remote 'origin': $output")
            }
        } catch (e: Exception) {
            logger.warn("Could not run 'git remote remove origin': ${e.message}")
        }
    }

    private fun gitCreateBranch(rootDir: File, branchName: String) {
        if (!rootDir.resolve(".git").exists()) return

        try {
            val process = ProcessBuilder("git", "checkout", "-B", branchName)
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                logStep("Switched to branch '$branchName'")
            } else {
                val output = process.inputStream.bufferedReader().readText().trim()
                logger.warn("Failed to checkout branch '$branchName': $output")
            }
        } catch (e: Exception) {
            logger.warn("Could not run 'git checkout -B $branchName': ${e.message}")
        }
    }

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
                logger.quiet("Moved with git: $relSource -> $relTarget")
            } else {
                throw RuntimeException("git mv failed (exit $exitCode): $output")
            }
        } catch (e: Exception) {
            logger.warn("git mv failed, falling back to file move: ${e.message}")
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

    private fun gitCleanEmptyDirs(rootDir: File, stopAt: File, start: File) {
        var dir = if (start.exists()) start else start.parentFile
        while (dir != null && dir != stopAt && dir.startsWith(stopAt)) {
            val contents = dir.listFiles()
            if (dir.exists() && (contents == null || contents.isEmpty())) {
                val relDir = dir.relativeTo(rootDir).path
                dir.delete()
                logger.quiet("Removed empty directory: $relDir")
                dir = dir.parentFile
            } else {
                break
            }
        }
    }

    private fun gitCleanDirectory(rootDir: File, relPath: String) {
        try {
            gitRemove(rootDir, relPath)

            val cleanProcess = ProcessBuilder("git", "clean", "-fdx", "--", relPath)
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            cleanProcess.inputStream.bufferedReader().readText()
            cleanProcess.waitFor()

            val dir = rootDir.resolve(relPath)
            if (dir.exists() && dir.isDirectory) {
                dir.delete()
            }

            logger.quiet("Cleaned $relPath via git")
        } catch (e: Exception) {
            logger.warn("git clean failed, falling back to deleteRecursively: ${e.message}")
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
            logger.warn("git rm failed: ${e.message}")
        }
    }

    private fun gitCommit(rootDir: File, agentName: String) {
        try {
            ProcessBuilder("git", "add", ".").directory(rootDir).start().waitFor()

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
                logger.quiet("Created hydration commit as CoralOS")
            } else {
                logger.warn("Git commit failed: $output")
            }
        } catch (e: Exception) {
            logger.warn("Could not perform git commit: ${e.message}")
        }
    }
}
