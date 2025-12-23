#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"
WORKFLOW_DIR="$(cd "$(dirname "$0")/.." && pwd)/workflows"

echo "Registering Conductor workflows to ${BASE_URL}..."

for workflow in "${WORKFLOW_DIR}"/*.json; do
  echo "-> ${workflow}"
  curl -sS -X POST "${BASE_URL}/metadata/workflow" \
    -H "Content-Type: application/json" \
    --data-binary @"${workflow}"
  echo
done
