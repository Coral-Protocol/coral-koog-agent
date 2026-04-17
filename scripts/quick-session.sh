CORAL_BASE_URL=${CORAL_BASE_URL:-localhost:5555}
CORAL_BEARER_TOKEN=${CORAL_BEARER_TOKEN:-dev}
AGENT_NAME=${AGENT_NAME:-coral-koog-agent}
# EXTRA_INITIAL_USER_PROMPT=${EXTRA_INITIAL_USER_PROMPT:-"create a new thread and say hello in it, then share your most unique ideas every 60s"}
EXTRA_INITIAL_USER_PROMPT=${EXTRA_INITIAL_USER_PROMPT:-$(cat)}

curl -X POST "http://${CORAL_BASE_URL}/api/v1/local/session" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${CORAL_BEARER_TOKEN}" \
  -d "{
    \"agentGraphRequest\": {
      \"agents\": [
        {
          \"id\": {
            \"name\": \"${AGENT_NAME}\",
            \"version\": \"0.1.0\",
            \"registrySourceId\": {
              \"type\": \"local\"
            }
          },
          \"name\": \"${AGENT_NAME}\",
          \"description\": \"\",
          \"provider\": {
            \"type\": \"local\",
            \"runtime\": \"executable\"
          },
          \"blocking\": false,
          \"customToolAccess\": [],
          \"plugins\": [],
          \"x402Budgets\": [],
          \"options\": {
            \"EXTRA_INITIAL_USER_PROMPT\": {
              \"type\": \"string\",
              \"value\": \"${EXTRA_INITIAL_USER_PROMPT}\"
            }
          }
        }
      ],
      \"groups\": [],
      \"customTools\": {}
    },
    \"namespaceProvider\": {
      \"type\": \"create_if_not_exists\",
      \"namespaceRequest\": {
        \"name\": \"default\",
        \"annotations\": {},
        \"deleteOnLastSessionExit\": false
      }
    },
    \"execution\": {
      \"mode\": \"immediate\",
      \"runtimeSettings\": {
        \"extendedEndReport\": true,
        \"persistenceMode\": {
          \"mode\": \"hold_after_exit\",
          \"duration\": 1800000
        },
        \"ttl\": 900000
      }
    }
  }"