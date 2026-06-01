#!/bin/bash
# Start RuleForge Console UI (Vite dev server)

set -e
cd "$(dirname "$0")/.."

# Load environment variables
if [ -f .env ]; then
    set -a && source .env && set +a
    echo "Loaded .env"
fi

echo "Starting Console UI dev server..."
cd console-ui

if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    npm install
fi
npm run dev
