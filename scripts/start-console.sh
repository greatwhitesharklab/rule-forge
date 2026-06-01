#!/bin/bash
# Start RuleForge Console (editor) on port 8081

set -e
cd "$(dirname "$0")/.."

# Load environment variables
if [ -f .env ]; then
    export $(grep -v '^#' .env | grep -v '^$' | xargs)
    echo "Loaded .env"
else
    echo "Warning: .env not found, using defaults from .env.example"
fi

cd server

# Install dependencies to local repo (needed for spring-boot:run)
if [ ! -f "$HOME/.m2/repository/com/ruleforge/ruleforge-console/3.5.3-SNAPSHOT/ruleforge-console-3.5.3-SNAPSHOT.jar" ]; then
    echo "Installing dependencies to local Maven repo..."
    mvn install -DskipTests -q
fi

echo "Starting RuleForge Console on port ${CONSOLE_PORT:-8081}..."
mvn spring-boot:run -pl ruleforge-console-app
