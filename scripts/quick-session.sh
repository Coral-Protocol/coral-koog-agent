#!/usr/bin/env bash
# Creates a session on a local Coral Server that runs this agent, using its executable runtime.
#
# The agent must be linked into the server's registry first (npx @coral-protocol/coralizer@latest link .), and the
# server needs an LLM provider for the agent's [[llm.proxies]] (a Coral Cloud API key, or [[llm-proxy.providers]]).
#
# Environment overrides: CORAL_BASE_URL, CORAL_BEARER_TOKEN, EXTRA_INITIAL_USER_PROMPT, DEV_CAPTURE_ENV=true
set -euo pipefail
cd "$(dirname "$0")/.."

CORAL_BASE_URL=${CORAL_BASE_URL:-localhost:5555}
CORAL_BEARER_TOKEN=${CORAL_BEARER_TOKEN:-dev}
DEV_CAPTURE_ENV=${DEV_CAPTURE_ENV:-false}
DEFAULT_EXTRA_INITIAL_USER_PROMPT="create a new thread and say hello in it, then share your most unique ideas every 60s"

# The registry identifies agents by the exact name and version in coral-agent.toml
AGENT_NAME=$(sed -n 's/^name = "\(.*\)"/\1/p' coral-agent.toml | head -n 1)
AGENT_VERSION=$(sed -n 's/^version = "\(.*\)"/\1/p' coral-agent.toml | head -n 1)

# To create a session without asking anything, set EXTRA_INITIAL_USER_PROMPT (it may be empty).
if [ -z "${EXTRA_INITIAL_USER_PROMPT+x}" ]; then
  printf "What should the agent's main goal be?\n"
  printf "(This sets EXTRA_INITIAL_USER_PROMPT. Press Enter to use default: %s)\n" "${DEFAULT_EXTRA_INITIAL_USER_PROMPT}"
  read -r USER_MAIN_GOAL
  EXTRA_INITIAL_USER_PROMPT=${USER_MAIN_GOAL:-${DEFAULT_EXTRA_INITIAL_USER_PROMPT}}
fi

# Escape backslashes and quotes so the prompt is a valid JSON string
PROMPT_JSON="\"$(printf '%s' "${EXTRA_INITIAL_USER_PROMPT}" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')\""

curl -sS --fail-with-body -X POST "http://${CORAL_BASE_URL}/api/v1/local/session" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${CORAL_BEARER_TOKEN}" \
  -d "{
    \"agentGraphRequest\": {
      \"agents\": [
        {
          \"id\": {
            \"name\": \"${AGENT_NAME}\",
            \"version\": \"${AGENT_VERSION}\",
            \"registrySourceId\": { \"type\": \"local\" }
          },
          \"name\": \"${AGENT_NAME}\",
          \"provider\": { \"type\": \"local\", \"runtime\": \"executable\" },
          \"options\": {
            \"EXTRA_INITIAL_USER_PROMPT\": { \"type\": \"string\", \"value\": ${PROMPT_JSON} },
            \"DEV_CAPTURE_ENV\": { \"type\": \"bool\", \"value\": ${DEV_CAPTURE_ENV} }
          }
        }
      ],
      \"groups\": [],
      \"customTools\": {}
    },
    \"namespaceProvider\": {
      \"type\": \"create_if_not_exists\",
      \"namespaceRequest\": { \"name\": \"default\", \"annotations\": {}, \"deleteOnLastSessionExit\": false }
    },
    \"execution\": {
      \"mode\": \"immediate\",
      \"runtimeSettings\": {
        \"extendedEndReport\": true,
        \"persistenceMode\": { \"mode\": \"hold_after_exit\", \"duration\": 1800000 },
        \"ttl\": 900000
      }
    }
  }"
echo
echo "Watch the session in the Coral Console: http://${CORAL_BASE_URL}/ui/console"
