FROM ghcr.io/graalvm/native-image-community:24 AS builder
WORKDIR /workspace

COPY gradlew ./gradlew
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x ./gradlew
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

#
COPY --from=builder /workspace/build/native/nativeCompile/agent /app/agent
COPY --from=builder /workspace/coral-agent.toml /app/coral-agent.toml

RUN useradd -r -u 1000 -g root appuser && \
    chown -R appuser:root /app && \
    chmod +x /app/agent

USER appuser

ENTRYPOINT ["/app/agent"]