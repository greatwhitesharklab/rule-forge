#!/bin/bash
# Start all RuleForge services (Console + Executor + Console UI)

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Load environment variables
if [ -f "$PROJECT_DIR/.env" ]; then
    set -a && source "$PROJECT_DIR/.env" && set +a
    echo "Loaded .env"
else
    echo "Warning: .env not found, using defaults from .env.example"
fi

echo "Starting all RuleForge services..."
echo "  Console:    http://localhost:${CONSOLE_PORT:-8081}"
echo "  Executor:   http://localhost:${EXECUTOR_PORT:-8082}"
echo "  Console UI: http://localhost:3000"
echo ""

# Start console-ui in background
"$SCRIPT_DIR/start-console-ui.sh" &
UI_PID=$!

# Start console + executor in background
cd "$PROJECT_DIR/server"
mvn spring-boot:run -pl ruleforge-console-app &
CONSOLE_PID=$!
mvn spring-boot:run -pl ruleforge-executor-app &
EXECUTOR_PID=$!

# Handle shutdown
trap "kill $CONSOLE_PID $EXECUTOR_PID $UI_PID 2>/dev/null; exit" INT TERM

wait
