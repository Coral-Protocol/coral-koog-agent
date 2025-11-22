# Simple Dockerfile for the Koog Coral agent (Kotlin)
# This image builds and runs the agent with Gradle Wrapper.

FROM eclipse-temurin:17-jdk as runtime

WORKDIR /app

# Copy Gradle wrapper and sources
COPY gradlew /app/gradlew
COPY gradle /app/gradle
COPY build.gradle.kts settings.gradle.kts /app/
COPY src /app/src

# Make wrapper executable
RUN chmod +x /app/gradlew

# Pre-download dependencies and build (skip tests for speed)
RUN ./gradlew --no-daemon --stacktrace build -x test || true

# Default command expected by coral-agent.toml's executable runtime is `./gradlew run`
CMD ["./gradlew", "--no-daemon", "run"]
