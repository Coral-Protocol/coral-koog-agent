FROM ghcr.io/graalvm/native-image-community:24 AS builder
WORKDIR /workspace

COPY gradlew ./gradlew
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x ./gradlew
COPY buildSrc ./buildSrc
COPY src ./src
COPY coral-agent.toml ./coral-agent.toml

RUN ./gradlew --no-daemon --stacktrace clean nativeCompile -x test

FROM debian:12-slim AS runtime
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    libc6 \
    libgcc-s1 \
    libstdc++6 \
    zlib1g \
    libssl3 \
    ca-certificates \
    libz-dev \
    libcurl4 \
    procps \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /workspace/build/native/nativeCompile/ /app/
COPY --from=builder /workspace/coral-agent.toml /app/coral-agent.toml

# Since we can't know the name of the generated executable ahead of time, we find it and rename it to a stable name "agent"
RUN rm -rf /app/reports && \
    file_count=$(find /app -maxdepth 1 -type f ! -name 'coral-agent.toml' | wc -l) && \
    if [ "$file_count" -ne 1 ]; then \
        echo "Expected exactly 1 executable file, found $file_count" && exit 1; \
    fi && \
    mv /app/$(find /app -maxdepth 1 -type f ! -name 'coral-agent.toml' -printf '%f') /app/agent

RUN useradd -r -u 1000 -g root appuser && \
    chown -R appuser:root /app && \
    chmod +x /app/agent

USER appuser

ENTRYPOINT ["/app/agent"]