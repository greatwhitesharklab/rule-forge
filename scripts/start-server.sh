#!/bin/bash
# Start RuleForge Server (Console + Executor)

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

# Install dependencies to local repo
echo "Installing dependencies to local Maven repo..."
mvn install -DskipTests -q

echo "Starting Console (${CONSOLE_PORT:-8081}) and Executor (${EXECUTOR_PORT:-8082})..."
mvn spring-boot:run -pl ruleforge-console-app &
mvn spring-boot:run -pl ruleforge-executor-app &

wait
