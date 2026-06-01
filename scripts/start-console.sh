#!/bin/bash
# Start RuleForge Console (editor) on port 8081

set -e
cd "$(dirname "$0")/.."

# Load environment variables
if [ -f .env ]; then
    set -a && source .env && set +a
    echo "Loaded .env"
else
    echo "Warning: .env not found, using defaults from .env.example"
fi

cd server

echo "Starting RuleForge Console on port ${CONSOLE_PORT:-8081}..."
mvn spring-boot:run -pl ruleforge-console-app
