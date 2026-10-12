# <agent name>
## Congratulations! You have a Coral agent!

It is built with [Koog](https://docs.koog.ai) from the Coral Koog agent template. To learn more about CoralOS, check out [docs.coralos.ai](https://docs.coralos.ai).

## Running the agent
(If you made this agent following a guide, feel free to skip this and return to the guide)

Coral agents are started by Coral Server as part of a session; the server provides their options, LLM access and connection details as environment variables. Running the agent directly (`./gradlew run`) fails for that reason.

### 1. Make the agent available to your Coral Server
Link it into your "agent home" directory, which makes it available to any Coral Server running on your machine:
```bash
npx @coral-protocol/coralizer@latest link .
```

### 2. Start Coral Server
Follow the [quickstart](https://docs.coralos.ai/guides/quickstart). The server needs an LLM provider for this agent's `[[llm.proxies]]`, for example a Coral Cloud API key.

When configuring your own provider, give the provider's root URL as its `base_url` (e.g. `https://api.openai.com`, not `https://api.openai.com/v1`); Koog adds the version to its request paths.

### 3. Create a session that runs the agent
```bash
./scripts/quick-session.sh
```
Then watch it in the Coral Console at http://localhost:5555/ui/console. For repeated runs, a session template in the Console is the easiest way to manage options.

## Developing

### Running from your IDE
To debug the agent from your IDE while it takes part in a real session:
1. Create a session with the `DEV_CAPTURE_ENV` option set to `true` (`DEV_CAPTURE_ENV=true ./scripts/quick-session.sh`). That agent writes its environment to `coral-agent.dev.env` and idles to keep the session alive.
2. Run the `main` method in `DevMain.kt`. It loads `coral-agent.dev.env` and joins the session as the agent.

`coral-agent.dev.env` contains the session's agent secret. It is gitignored and excluded from Docker builds; don't share it.

### Where to make changes
- **Tools**: add Koog tools in `getToolRegistry` (`Main.kt`). Tools from other MCP servers can be added with `toolRegistryFromProcess`.
- **Context management**: `compressContext` (`Main.kt`) decides what happens when the conversation gets long.
- **Prompts and limits**: the options in `coral-agent.toml`, which applications can set per session.
- **Budgets**: token usage is reported as `LLM_TOKENS` claims (`[[claims]]` in `coral-agent.toml`). Applications set the price and the budget per session; the agent stops when the budget runs out.

### LLM formats
Each `[[llm.proxies]]` entry in `coral-agent.toml` declares a `format.type`: `OpenAI`, `Anthropic` or `DeepSeek`. Coral Server only routes a proxy to providers of that format, and the agent uses the matching Koog client. Coral Cloud serves OpenAI and DeepSeek models.

Anthropic and DeepSeek proxies need Coral Server 1.5.0 or newer.

OpenAI-format requests use the Chat Completions API, where GPT-5.1 and newer only accept tools with reasoning turned off. That is the `REASONING_EFFORT` option's default (`none`); set it to `minimal` or higher for gpt-5 and o-series models, or `default` to leave it to the provider.

---

## Production usage

<details>
<summary>Production usage</summary>

Production servers should run agents with the docker runtime. The workflows in `.github/workflows` build JVM and native images, push them to GitHub's container registry and publish a release `coral-agent.toml` that references them. Distribute that file to the Coral Server instances that should run this agent.

The native image starts much faster, but only includes the reflection and resource metadata recorded in `src/native-image`. After adding libraries or code paths, record it again: run sessions that exercise the agent with the `DEV_NATIVE_METADATA` option set to `true`. This needs a GraalVM 25 JDK, which Gradle finds or downloads.

For more information about running Coral in production, see [here](https://docs.coralos.ai/guides/production/running-in-production).
</details>

---
