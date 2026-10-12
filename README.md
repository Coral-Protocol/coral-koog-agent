# Koog Coral Agent (Kotlin)

The starting point for writing a [Coral](https://docs.coralos.ai) agent in code, using JetBrains' [Koog](https://docs.koog.ai) framework.

Writing the agent yourself gives you control over its loop, its tools, and above all its context: what it remembers, what it summarises and what it re-reads each turn. The template shows each of these working against Coral Server, and is also a reference for porting the same patterns to another agent framework.

If your agent only needs a prompt and Coral's own tools, Coral Server's built-in [prototype runtime](https://docs.coralos.ai/reference/agent/tables/runtimes/prototype) runs one without any code.

## What's included

- **Agent loop** (`Main.kt`): each iteration refreshes the system prompt, asks the LLM for tool calls, runs them and records the results.
- **Coral resources**: `coral://instruction` and `coral://state` are re-read into the system prompt every iteration, so the agent always sees the current threads and messages.
- **Coral and application tools**: everything the session's MCP server offers, including [custom tools](https://docs.coralos.ai/guides/integrating-with-your-app) granted by the application. Add local Koog tools in `getToolRegistry`.
- **LLM proxy**: models are reached through Coral Server's LLM proxy, in whichever format the session's provider speaks (OpenAI, Anthropic or DeepSeek). No API keys live in the agent.
- **Context management**: once a request reaches `CONTEXT_COMPRESSION_TOKENS`, the history is replaced with a summary (`compressContext`). Swap in another Koog `HistoryCompressionStrategy` to change how the agent remembers.
- **Budgets**: token usage is reported as `LLM_TOKENS` claims. The agent stops when the session's budget runs out.
- **Packaging**: JVM and GraalVM native Docker images, with GitHub workflows that publish them and a matching `coral-agent.toml`.

## Create your agent

You can turn this template into your own project using either NPM (recommended) or Gradle.

### Option 1: Using NPM (recommended)
```bash
npm create koog <my-cool-agent>
```
You will be prompted for anything not provided. You can also pass hydration options (see below):
```bash
npm create koog <my-cool-agent> --packageName=com.example.myagent
```

### Option 2: Using Gradle
```bash
git clone https://github.com/Coral-Protocol/coral-koog-agent my-cool-agent
cd my-cool-agent
./gradlew -q hydrate --agentName=my-cool-agent --packageName=com.example.myagent
```

### Hydration options
- `--agentName`: Name for the agent (kebab-case, e.g. `my-cool-agent`).
- `--packageName`: Java/Kotlin package name (e.g. `com.example.myagent`).
- `--keepWorkflows`, `--keepPublishJvm`, `--keepPublishNative`, `--keepReleasePlease`: `true` or `false` to keep or remove the GitHub workflows without being asked.

### What hydration does
- Updates `build.gradle.kts`, `settings.gradle.kts`, `coral-agent.toml` and this README with your project info.
- Moves the sources to your package.
- Removes the hydrator itself (`buildSrc`, `package.json`, `create-koog.js` and the `hydrate` task).
- Starts a fresh git repository on `main` with one commit, replacing the template's history and its remote.
  - A template clone with commits of your own keeps them. The template remote is still removed, and work continues on a `main` branch.
  - A repository created from the template on GitHub, or with any other origin, is left as it is apart from one new commit.

## Requirements
- JDK 25. If it isn't installed, Gradle downloads it on first build.
- Git
- Coral Server 1.5.0 or newer to run the agent in (see the [quickstart](https://docs.coralos.ai/guides/quickstart)). 1.4.0 also works, but only with OpenAI-format LLM proxies. Building the server from source needs its own JDK (24).
- (Optional) Node.js/NPM, for `npm create koog` and `coralizer`
- (Optional) Docker, for the docker runtime and image builds
