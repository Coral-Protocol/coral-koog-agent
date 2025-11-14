# Koog Coral Agent (Kotlin)

This is a minimal Koog-based agent wired for Coral orchestration. It:

- Uses MCP to pull `coral://agent/instruction` and `coral://messages` into the system prompt on every step
- Runs in an iterative loop and executes MCP tools exposed by Coral and any attached servers
- Sends claim requests to Coral’s payment API per-step based on token usage (USD estimate)
- Can run locally in devmode or be orchestrated by Coral Server

The repository also includes a Python `langchain-agent` reference, which this Kotlin agent mirrors in behavior and configuration where reasonable.

## Quick start

### Requirements
- JDK 17+
- Internet access for dependencies
- An OpenAI-compatible API key (set as `MODEL_API_KEY` or `OPENAI_API_KEY`)
- Coral Server (for orchestration and MCP resources)

### Build and run (local)

```
./gradlew run
```

When not orchestrated (devmode), claims are skipped (`CORAL_SEND_CLAIMS=0`).

### Docker

Build the image:

```
docker build -t koog-coral-agent .
```

Run it (example):

```
docker run --rm \
  -e MODEL_API_KEY=sk-your-key \
  -e SYSTEM_PROMPT="You are a helpful autonomous agent." \
  -e CORAL_SERVER_URL="http://localhost:5555/sse/v1/devmode/exampleApplicationId/examplePrivacyKey/exampleSessionId/sse?agentId=koog-agent" \
  koog-coral-agent
```

When orchestrated by Coral Server, the agent should be run via Coral with the provided `coral-agent.toml` in this repository’s root.

## Coral configuration (coral-agent.toml)

This agent supports Coral’s `coral-agent.toml` for orchestration-time configuration:

```
[agent]
name = "koog-agent"
version = "0.1.0"
description = "A Koog (Kotlin) example agent for Coral that supports MCP resources and Coral claims."

[options]
SYSTEM_PROMPT = { type = "string", required = true, description = "The system prompt to use" }
MODEL_API_KEY = { type = "string", required = true, description = "The API key for the LLM provider (OpenAI compatible)" }
MAX_ITERATIONS = { type = "number", default = 10, description = "The maximum number of LLM iterations (ignoring tool calls) to run." }

[runtimes.executable]
command = ["./gradlew", "run"]
```

Coral will map these options to environment variables for the agent at runtime:
- `SYSTEM_PROMPT` (required)
- `MODEL_API_KEY` (required)
- `MAX_ITERATIONS` (optional; default 10)
- `CORAL_PROMPT_SYSTEM` (injected by Coral; appended to system prompt)
- `CORAL_CONNECTION_URL` (injected by Coral; SSE URL for the MCP client)

## Claims

This agent claims per step based on token usage, converting tokens to USD with a simple constant rate (`USD_PER_TOKEN = 0.000001`).

- In devmode (local), `CORAL_SEND_CLAIMS=0` (default) and claim requests are skipped
- When orchestrated by Coral Server, environment variables are injected:
  - `CORAL_SEND_CLAIMS=1`
  - `CORAL_API_URL` (e.g. `http://coral-server:port`)
  - `CORAL_SESSION_ID` (the running session id)

The implementation is in `src/main/kotlin/org/coralprotocol/coral/koog/fullexample/ClaimHandler.kt` and mirrors the Python reference logic.

## How it works

- The main loop is implemented in `FullKoogExample.kt` using Koog’s `AIAgent` and MCP tool registry
- On each iteration:
  1. The system prompt is built from `SYSTEM_PROMPT` and `CORAL_PROMPT_SYSTEM`, then enriched with Coral MCP resources `coral://agent/instruction` and `coral://messages`
  2. The LLM is called. If tool calls are present, tools are executed and results are sent back until completion
  3. Token usage is read and a cost claim is sent to Coral (only when orchestrated)
- The loop stops when `MAX_ITERATIONS` is reached or when the known remaining budget is 0

## Devmode notes

You can run locally using a devmode URL for the MCP SSE connection via `CORAL_SERVER_URL` (see example in Docker command above). In devmode, the agent will still read from MCP resources as exposed by Coral.
