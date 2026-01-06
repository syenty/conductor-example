#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${CONDUCTOR_BASE_URL:-http://localhost:8080/api}"
WORKFLOW_DIR="$(cd "$(dirname "$0")/.." && pwd)/workflows"

echo "Registering Conductor workflows to ${BASE_URL}..."

# Register sub-workflows first (must be registered before main workflows that use them)
echo ""
echo "=== Registering Sub-Workflows ==="
SUB_WORKFLOW_COUNT=0
for workflow in "${WORKFLOW_DIR}"/sub_*.json; do
  if [ -f "$workflow" ]; then
    echo "-> ${workflow}"
    curl -sS -X POST "${BASE_URL}/metadata/workflow" \
      -H "Content-Type: application/json" \
      --data-binary @"${workflow}"
    echo
    SUB_WORKFLOW_COUNT=$((SUB_WORKFLOW_COUNT + 1))
  fi
done

if [ $SUB_WORKFLOW_COUNT -eq 0 ]; then
  echo "No sub-workflows found (sub_*.json)"
fi

# Register main workflows
echo ""
echo "=== Registering Main Workflows ==="
MAIN_WORKFLOW_COUNT=0
for workflow in "${WORKFLOW_DIR}"/W*.json; do
  if [ -f "$workflow" ]; then
    echo "-> ${workflow}"
    curl -sS -X POST "${BASE_URL}/metadata/workflow" \
      -H "Content-Type: application/json" \
      --data-binary @"${workflow}"
    echo
    MAIN_WORKFLOW_COUNT=$((MAIN_WORKFLOW_COUNT + 1))
  fi
done

echo ""
echo "=== Registration Summary ==="
echo "Sub-workflows: ${SUB_WORKFLOW_COUNT}"
echo "Main workflows: ${MAIN_WORKFLOW_COUNT}"
echo "Total: $((SUB_WORKFLOW_COUNT + MAIN_WORKFLOW_COUNT))"
