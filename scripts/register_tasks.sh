#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"
TASK_DIR="$(cd "$(dirname "$0")/.." && pwd)/tasks"

echo "Registering Conductor tasks to ${BASE_URL}..."

for task in "${TASK_DIR}"/*.json; do
  echo "-> ${task}"
  curl -sS -X POST "${BASE_URL}/metadata/task" \
    -H "Content-Type: application/json" \
    --data-binary @"${task}"
  echo
done
