# Koog Coral Agent (Kotlin)

This is a template for building autonomous agents in Kotlin using the Koog framework and CoralOS.

## Hydration

You can transform this template into your own project using either NPM (recommended) or Gradle:

### Option 1: Using NPM (Recommended)
Run the following to create a new project in a directory of your choice:
```bash
npm create koog <my-cool-agent>
```

### Option 2: Using Gradle
If you have already cloned the repository, run the hydration task directly:
```bash
./gradlew hydrate
```

The tool will prompt you for:
1. **Agent name**: kebab-case name (e.g., `my-cool-agent`).
2. **Package name**: Java/Kotlin package (e.g., `com.example.myagent`).

Alternatively, you can provide these as arguments:
```bash
./gradlew hydrate --agentName=my-cool-agent --packageName=com.example.myagent
```

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
./gradlew run
```