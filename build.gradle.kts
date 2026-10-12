plugins {
    kotlin("jvm") version "2.4.21"
    application
    kotlin("plugin.serialization") version "2.4.21"
    id("org.graalvm.buildtools.native") version "1.1.14"
}

// Ensure ./gradlew -q hydrate is not interrupted by gradle execution updates (progress bars)
if (gradle.startParameter.taskNames.any { it.contains("hydrate") }) {
    gradle.startParameter.consoleOutput = org.gradle.api.logging.configuration.ConsoleOutput.Plain
}

group = "ai.coralprotocol" //{CORALIZER:BUILD_GROUP}
version = "1.0-SNAPSHOT" //{CORALIZER:BUILD_VERSION}

repositories {
    mavenCentral()
    mavenLocal()
}

// Directory where GraalVM native-image agent will dump collected configuration
val nativeImageConfigDir = layout.projectDirectory.dir("src/native-image").asFile

dependencies {
    val koogVersion = "1.3.0"
    api("ai.koog:koog-agents:$koogVersion")
    // Koog's MCP integration is published on the beta stream
    api("ai.koog:agents-mcp:$koogVersion-beta")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Ktor client transport for Coral streamable HTTP/SSE. The BOM keeps the Ktor modules Koog brings in on the same version.
    implementation(platform("io.ktor:ktor-bom:3.6.0"))
    implementation("io.ktor:ktor-client-cio")
    constraints {
        implementation("org.apache.httpcomponents.client5:httpclient5:5.6.4") {
            because("CVE-2026-71290: async TLS hostname verification bypass before 5.6.4 (via Koog's Ktor Apache5 engine)")
        }
    }

    // Koog depends on 8.0.01, whose GraalVM substitution targets a class that doesn't exist and breaks nativeCompile
    implementation("io.github.oshai:kotlin-logging-jvm:8.0.4")

    // Koog and the MCP SDK log through SLF4J; without a provider their logs are silently dropped
    runtimeOnly("org.slf4j:slf4j-simple:2.0.20")

    testImplementation(kotlin("test"))
}

application {
    // Kotlin top-level main in Main.kt compiles to *Kt suffix
    mainClass.set("ai.coralprotocol.coral.koog.fullexample.MainKt") //{CORALIZER:APPLICATION_MAIN_CLASS}
}

graalvmNative {
    toolchainDetection.set(false)
    binaries {
        named("main") {
            imageName.set("coral-koog-agent") //{CORALIZER:NATIVE_BINARY_IMAGE_NAME}
            mainClass.set("ai.coralprotocol.coral.koog.fullexample.MainKt") //{CORALIZER:NATIVE_BINARY_MAIN_CLASS}
            buildArgs.addAll(
                listOf(
                    "--no-fallback",
                    "-H:+ReportExceptionStackTraces",
                    "--initialize-at-build-time=kotlin.DeprecationLevel",
                    "--initialize-at-run-time=java.security.SecureRandom,com.sun.jndi.dns.DnsClient",
                    "--enable-http",
                    "--add-modules=java.net.http",
                    "--enable-url-protocols=https",
                    "-H:+UnlockExperimentalVMOptions"
                )
            )

            // Reflection and resource metadata recorded by running the agent in sessions with the DEV_NATIVE_METADATA
            // option. Re-record it after adding libraries or code paths that use reflection, or the native image may
            // fail at runtime where the JVM build works.
            buildArgs.add("-H:ConfigurationFileDirectories=${nativeImageConfigDir.absolutePath}")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
}

// Prevent issue where sometimes ./gradlew -q run would be considered UP-TO-DATE and not actually run
tasks.named<JavaExec>("run") {
    outputs.upToDateWhen { false }

    // The DEV_NATIVE_METADATA option (coral-agent.toml) reaches this build as an environment variable: run on GraalVM
    // with its tracing agent, merging the reflection and resource metadata this run needed into src/native-image.
    if (System.getenv("DEV_NATIVE_METADATA") == "1") {
        javaLauncher.set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
            nativeImageCapable.set(true)
        })
        jvmArgs("-agentlib:native-image-agent=config-merge-dir=${nativeImageConfigDir.absolutePath}")
    }
}

tasks.register<HydrateTemplateTask>("hydrate") {
    group = "setup"
    description = "Hydrate this template into a concrete Coral Koog agent project."
}
