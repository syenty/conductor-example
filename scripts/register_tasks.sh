#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"
TASK_DIR="$(cd "$(dirname "$0")/.." && pwd)/tasks"

echo "Registering Conductor tasks to ${BASE_URL}..."

for task in "${TASK_DIR}"/*.json; do
  echo "-> ${task}"
  # Wrap single task definition in array and POST to /metadata/taskdefs
  curl -sS -X POST "${BASE_URL}/metadata/taskdefs" \
    -H "Content-Type: application/json" \
    -d "[$(cat "${task}")]"
  echo
done
