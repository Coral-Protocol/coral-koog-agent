plugins {
    kotlin("jvm") version "2.2.0"
}

group = "org.coralprotocol"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://github.com/CaelumF/koog/raw/master/maven-repo")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("ai.koog:koog-agents:0.3.0.2")
    implementation("ai.koog:agents-mcp:0.3.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}