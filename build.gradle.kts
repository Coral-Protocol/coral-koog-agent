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
    val string = "0.3.0.4"
    implementation("ai.koog:koog-agents:$string")
    implementation("ai.koog:agents-mcp:$string")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}