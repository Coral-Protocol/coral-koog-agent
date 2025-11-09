plugins {
    kotlin("jvm") version "2.2.0"
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
    implementation("ai.koog:koog-agents:$koogVersion")
    implementation("ai.koog:agents-mcp:$koogVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}