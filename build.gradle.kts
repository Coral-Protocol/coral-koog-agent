plugins {
    kotlin("jvm") version "2.2.0"
}

group = "org.coralprotocol"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/caelumf/koog")
    }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.github.caelumf.koog:koog-agents:0.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}