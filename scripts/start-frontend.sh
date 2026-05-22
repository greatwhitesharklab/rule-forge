#!/bin/bash
# Start RuleForge Frontend (React dev server)

set -e
cd "$(dirname "$0")/.."

# Load environment variables
if [ -f .env ]; then
    set -a && source .env && set +a
    echo "Loaded .env"
fi

echo "Starting Frontend dev server..."
cd frontend

if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    npm install
fi
npm start
