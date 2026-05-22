#!/bin/bash
# Start all RuleForge services (Console + Executor + Frontend)

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
echo "  Console:  http://localhost:${CONSOLE_PORT:-8081}"
echo "  Executor: http://localhost:${EXECUTOR_PORT:-8082}"
echo "  Frontend: http://localhost:3000"
echo ""

# Start backend services in background
"$SCRIPT_DIR/start-backend.sh" &
BACKEND_PID=$!

# Start frontend
"$SCRIPT_DIR/start-frontend.sh" &
FRONTEND_PID=$!

# Handle shutdown
trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit" INT TERM

wait
