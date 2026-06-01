#!/bin/bash
# Start RuleForge Executor on port 8082

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

echo "Starting RuleForge Executor on port ${EXECUTOR_PORT:-8082}..."
mvn spring-boot:run -pl ruleforge-executor-app
