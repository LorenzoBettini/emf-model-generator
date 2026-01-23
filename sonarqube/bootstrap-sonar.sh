#!/bin/sh
set -eu

echo "Installing curl and jq..."
apk add --no-cache curl jq >/dev/null 2>&1

SONAR_HOST_URL="${SONAR_HOST_URL:-http://sonarqube:9000}"
ADMIN_USER="${SONARQUBE_ADMIN_USER:-admin}"
ADMIN_PASS="${SONARQUBE_ADMIN_PASSWORD:-admin}"
USER_LOGIN="${BOOTSTRAP_USER_LOGIN:-user}"
USER_NAME="${BOOTSTRAP_USER_NAME:-Predefined User}"
USER_PASS="${BOOTSTRAP_USER_PASSWORD:-userpass}"
TOKEN_NAME="${BOOTSTRAP_TOKEN_NAME:-predefined-ci-token}"

echo "Waiting for SonarQube at $SONAR_HOST_URL ..."

# Wait until SonarQube reports status=UP
while : ; do
  status_json=$(curl -sf "$SONAR_HOST_URL/api/system/status" 2>/dev/null || true)
  if echo "$status_json" | jq -e '.status == "UP"' >/dev/null 2>&1; then
    echo "SonarQube is UP."
    break
  fi
  echo "SonarQube not ready yet, sleeping 5s..."
  sleep 5
done

echo "Validating admin credentials..."
if ! curl -sf -u "$ADMIN_USER:$ADMIN_PASS" \
  "$SONAR_HOST_URL/api/authentication/validate" \
  | jq -e '.valid == true' >/dev/null 2>&1; then
  echo "ERROR: Admin credentials are invalid."
  echo "Check SONARQUBE_ADMIN_USER / SONARQUBE_ADMIN_PASSWORD in docker-compose.yml."
  exit 1
fi

echo "Ensuring user '$USER_LOGIN' exists..."

# Search users with q=<login> and explicitly match .login == <login>
user_json=$(curl -sf -u "$ADMIN_USER:$ADMIN_PASS" \
  "$SONAR_HOST_URL/api/users/search?q=$USER_LOGIN&ps=500")

if echo "$user_json" | jq -e --arg login "$USER_LOGIN" \
  '.users[]? | select(.login == $login)' >/dev/null 2>&1; then
  echo "User '$USER_LOGIN' already exists."
else
  echo "Creating user '$USER_LOGIN'..."
  curl -sf -u "$ADMIN_USER:$ADMIN_PASS" -X POST \
    "$SONAR_HOST_URL/api/users/create" \
    -d "login=$USER_LOGIN" \
    -d "name=$USER_NAME" \
    -d "password=$USER_PASS" \
    -d "local=true" \
  >/dev/null 2>&1 || {
    echo "ERROR: Failed to create user '$USER_LOGIN'."
    exit 1
  }
fi

echo "Checking existing tokens for '$USER_LOGIN'..."

tokens_json=$(curl -sf -u "$USER_LOGIN:$USER_PASS" \
  "$SONAR_HOST_URL/api/user_tokens/search")

token_count=$(echo "$tokens_json" | jq '.userTokens | length // 0')

if [ "$token_count" -eq 0 ]; then
  echo "No existing tokens for '$USER_LOGIN'; generating one named '$TOKEN_NAME'..."
  token_json=$(curl -sf -u "$USER_LOGIN:$USER_PASS" -X POST \
    "$SONAR_HOST_URL/api/user_tokens/generate" \
    -d "name=$TOKEN_NAME")

  token=$(echo "$token_json" | jq -r '.token')

  if [ -z "$token" ] || [ "$token" = "null" ]; then
    echo "ERROR: Failed to get token value from API response:"
    echo "$token_json"
    exit 1
  fi

  echo "$token" | tee /bootstrap/sonar-token.txt
  echo "Token created and stored at /bootstrap/sonar-token.txt"
else
  echo "User '$USER_LOGIN' already has $token_count token(s); not creating a new one."
  echo "Existing /bootstrap/sonar-token.txt (if any) is left untouched."
fi

echo "Bootstrap complete."
