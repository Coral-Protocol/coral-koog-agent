# Koog Coral Agent (Kotlin)

This is a template for building autonomous agents in Kotlin using the Koog framework and CoralOS.

## Hydration

You can transform this template into your own project using either NPM (recommended) or Gradle:

### Option 1: Using NPM (Recommended)
Run the following to create a new project. You will be prompted for a name if you don't provide one:
```bash
npm create koog <my-cool-agent>
```
You can also pass hydration options (see below):
```bash
npm create koog <my-cool-agent> --enableTunnel=true
```
or just:
```bash
npm create koog
```

### Option 2: Using Gradle
If you have already cloned the repository, run the hydration task directly:
```bash
./gradlew -q hydrate
```

The tool will prompt you for:
1. **Agent name**: kebab-case name (e.g., `my-cool-agent`).
2. **Package name**: Java/Kotlin package (e.g., `com.example.myagent`).

Alternatively, you can provide these (and other options) as arguments:
```bash
./gradlew -q hydrate --agentName=my-cool-agent --packageName=com.example.myagent
```

### Hydration Options
- `--agentName`: Name for the agent (kebab-case, e.g., `my-cool-agent`).
- `--packageName`: Java/Kotlin package name (e.g., `com.example.myagent`).
- `--enableTunnel`: Whether to enable tunnel functionality (default: `false`).

### What hydration does:
- Updates `build.gradle.kts`, `settings.gradle.kts`, and `coral-agent.toml` with your project info.
- Renames packages in all Kotlin source files.
- Moves source files to the correct directory structure.
- Removes the template's git remote.
- Commits the changes using the **CoralOS** identity.
- **Removes the hydrator code** (`buildSrc`, `package.json`, `create-koog.js`, and the `hydrate` task) so the project is clean.

## Requirements
- JDK 24
- Git
- (Optional) Node.js/NPM (for `npm create koog`)

## Running the Agent
Once hydrated, you can run the agent with:
```bash
./gradlew -q run
```