# <agent name>
## Congratulations! You have a Coral agent!

To learn more about CoralOS, check out [docs.coralos.ai](https://docs.coralos.ai).

## Running the agent
(If you made this agent following a guide, feel free to skip this and return to the guide)

### During development
To run this agent, you'll need to first run the coral server and ensure it's configured to have this agent:

<details>
<summary>Running server with just this agent</summary>

You can quickly run a server with just this agent by running:
```bash
npx coralos-dev@latest server start -- --auth.keys=dev --registry.local-agents="$(PWD)"
```
</details>

### Running via a local coral server installation
You can link this agent to your "agent home" directory, which will make it available to any coral server running on your machine:

```bash
npx @coral-protocol/coralizer@latest link .
```

Then run the coral server:
```bash
npx coralos-dev@latest server start -- --auth.keys=dev
```

### Running directly or via your IDE
You can run this agent directly by first orchestrating it to run through the Coral Server and recording the environment variables it runs with.

#### 1. Set the agent to record these values instead of running by 
Go to Main.kt and uncomment the main method at the bottom of the file.

#### 2. Create a session that runs this agent with a long timeout

#### 3. Wait for the agent to finish saving the environment variables
The agent will save the file once it starts. You can see it's done when the file is there, or by checking the agent's logs.
(this may take a minute if it's the first time the agent is compiling)

It will stay running to keep the session alive.

#### 4. Run the agent via DevMain.kt
Now you can run the main method in DevMain.kt, which will load the environment variables from the file.

If you're using IntelliJ (recommended), you can open this file in the IDE and run it directly by clicking the green play button next to the Main method.


#### 5. Set the agent back to run normally
Go to Main.kt and comment out the main method at the bottom of the file again. This lets the agent be orchestrated normally again.

---

## Production usage

<details>
<summary>Production usage</summary>
This agent template is production-ready out of the box. See the `.github/workflows` for building prod images, and `coral-agent.toml`'s runtime section.

Add the container tag to the `runtime` section of `coral-agent.toml`, and then distribute that `coral-agent.toml` file to whichever production coral server instances you want to run this agent on.

For more information about running coral in production, see [here](https://docs.coralos.ai/guides/production/running-in-production).
</details>

## devex notes

### fast iteration
We recommend creating a template in coral consle for easier development, espcially if you end up with many options.
For a cli alternative, there's a script in `scripts/quick-session.sh` that creates a session with this agent running.


---