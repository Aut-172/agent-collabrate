#!/usr/bin/env bash
set -euo pipefail

: "${ACR_PUSH_ATTEMPTS:=3}"
: "${ACR_PUSH_TIMEOUT:=15m}"

if [[ ! "$ACR_PUSH_ATTEMPTS" =~ ^[1-9][0-9]*$ ]]; then
  echo "ACR_PUSH_ATTEMPTS must be a positive integer." >&2
  exit 2
fi
if [[ "$#" -eq 0 ]]; then
  echo "At least one image reference is required." >&2
  exit 2
fi

push_with_retry() {
  local reference="$1"
  local delay=15
  local attempt status
  for ((attempt = 1; attempt <= ACR_PUSH_ATTEMPTS; attempt++)); do
    echo "Pushing ${reference} (attempt ${attempt}/${ACR_PUSH_ATTEMPTS}, timeout ${ACR_PUSH_TIMEOUT})."
    set +e
    timeout --signal=TERM --kill-after=30s "$ACR_PUSH_TIMEOUT" docker push "$reference"
    status=$?
    set -e
    if ((status == 0)); then return 0; fi
    if ((attempt == ACR_PUSH_ATTEMPTS)); then
      echo "Push failed after ${ACR_PUSH_ATTEMPTS} attempts (exit ${status})." >&2
      return "$status"
    fi
    echo "Push attempt failed (exit ${status}); retrying in ${delay}s."
    sleep "$delay"
    delay=$((delay * 2))
  done
}

for reference in "$@"; do
  push_with_retry "$reference"
done
