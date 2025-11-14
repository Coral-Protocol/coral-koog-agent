plugins {
    kotlin("jvm") version "2.2.0"
    application
}

group = "org.coralprotocol"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
//    maven("https://github.com/CaelumF/koog/raw/master/maven-repo")
}

dependencies {
    testImplementation(kotlin("test"))
    val koogVersion = "0.5.2"
    api("ai.koog:koog-agents:$koogVersion")
    api("ai.koog:agents-mcp:$koogVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

application {
    // Kotlin top-level main in FullKoogExample.kt compiles to *Kt suffix
    mainClass.set("org.coralprotocol.coral.koog.fullexample.FullKoogExampleKt")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}