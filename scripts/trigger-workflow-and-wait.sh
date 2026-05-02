#!/usr/bin/env bash
set -euo pipefail

# This script triggers the repository's workflow_dispatch-enabled workflow
# and polls until the latest workflow_run finishes. It then reports the
# artifact download URLs for APKs if available.

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT_DIR"

REPO_URL=$(git config --get remote.origin.url || true)
if [[ -z "$REPO_URL" ]]; then
  echo "ERROR: Unable to determine remote.origin.url" >&2
  exit 1
fi

# Extract owner/repo from URL (supports SSH and HTTPS forms)
URL_PART="$REPO_URL"
URL_PART="${URL_PART#git@github.com:}"
URL_PART="${URL_PART#https://github.com/}"
URL_PART="${URL_PART#http://github.com/}"
URL_PART="${URL_PART%.git}"
OWNER="${URL_PART%%/*}"
REPO="${URL_PART##*/}"
if [[ -z "$OWNER" || -z "$REPO" ]]; then
  echo "ERROR: Could not parse owner/repo from $REPO_URL" >&2
  exit 1
fi

## Load GitHub token from environment or token file
GITHUB_TOKEN="${GITHUB_TOKEN:-}"
TOKEN_FILES=( ".github_token" ".github/token.txt" "secrets/github_token.txt" ".token" )
for f in "${TOKEN_FILES[@]}"; do
  if [[ -f "$f" ]]; then
    TOKEN_CONTENT=$(<"$f")
    TOKEN_CONTENT=$(echo "$TOKEN_CONTENT" | tr -d ' \n\t\r')
    if [[ -n "$TOKEN_CONTENT" ]]; then
      GITHUB_TOKEN="$TOKEN_CONTENT"
      break
    fi
  fi
done
if [[ -z "$GITHUB_TOKEN" ]]; then
  echo "ERROR: GITHUB_TOKEN not set and no token file found" >&2
  exit 1
fi
export GITHUB_TOKEN

echo "Triggering workflow on ${OWNER}/${REPO} (default branch will be used)"

# Get default branch and repo info
REPO_API="https://api.github.com/repos/${OWNER}/${REPO}"
DEFAULT_BRANCH=$(curl -sS -H "Authorization: token ${GITHUB_TOKEN}" -H "Accept: application/vnd.github+json" "$REPO_API" | python3 -c 'import sys, json; d=json.load(sys.stdin); print(d.get("default_branch","main"))')
if [[ -z "$DEFAULT_BRANCH" || "$DEFAULT_BRANCH" == "null" ]]; then
  echo "ERROR: Could not determine default branch" >&2
  exit 1
fi
echo "Default branch: ${DEFAULT_BRANCH}"
LOCAL_DOWNLOAD_PATH="${LOCAL_DOWNLOAD_PATH:-downloads}"

# Trigger the workflow dispatch for the build-apk.yml workflow
WORKFLOW_API="${REPO_API}/actions/workflows/build-apk.yml/dispatches"
curl -sS -X POST \
  -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Accept: application/vnd.github+json" \
  -d "{\"ref\":\"${DEFAULT_BRANCH}\"}" \
  "$WORKFLOW_API" >/dev/null
echo "Workflow dispatch triggered. Verifying run..."

# Poll for the latest workflow_run
RUN_ID=0
ATTEMPTS=0
MAX_ATTEMPTS=60
SLEEP_SEC=10
while [[ $RUN_ID -eq 0 && $ATTEMPTS -lt $MAX_ATTEMPTS ]]; do
  sleep $SLEEP_SEC
  RUNS_JSON=$(curl -sS -H "Authorization: token ${GITHUB_TOKEN}" -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/${OWNER}/${REPO}/actions/runs?branch=${DEFAULT_BRANCH}&event=workflow_dispatch&per_page=1")
  RUNS=$(echo "$RUNS_JSON" | python3 -c 'import sys, json; d=json.load(sys.stdin); print(d.get("workflow_runs", []))')
  if [[ "$RUNS" == "[]" ]]; then
    RUN_ID=""
    STATUS=""
  else
    RUN_ID=$(echo "$RUNS_JSON" | python3 -c 'import sys, json; d=json.load(sys.stdin); r=(d.get("workflow_runs") or [])[0]; print(r.get("id"))')
    STATUS=$(echo "$RUNS_JSON" | python3 -c 'import sys, json; d=json.load(sys.stdin); r=(d.get("workflow_runs") or [])[0]; print(r.get("status"))')
  fi
  if [[ -n "$RUN_ID" ]]; then
    echo "Found run ${RUN_ID} with status ${STATUS}"
    break
  fi
  ((ATTEMPTS++))
done

if [[ -z "$RUN_ID" || "$RUN_ID" == "null" ]]; then
  echo "ERROR: Could not determine workflow run" >&2
  exit 1
fi

echo "Waiting for run ${RUN_ID} to complete..."
while true; do
  RUN_STATUS_JSON=$(curl -sS -H "Authorization: token ${GITHUB_TOKEN}" -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/${OWNER}/${REPO}/actions/runs/${RUN_ID}")
  
  EXEC_STATUS=$(echo "$RUN_STATUS_JSON" | python3 -c 'import sys, json; d=json.load(sys.stdin); print(d.get("status"))')
  CONCLUSION=$(echo "$RUN_STATUS_JSON" | python3 -c 'import sys, json; d=json.load(sys.stdin); print(d.get("conclusion"))')
  if [[ "$EXEC_STATUS" == "completed" ]]; then
    echo "Run completed with conclusion: ${CONCLUSION}"
    break
  else
    echo "Current status: ${EXEC_STATUS} (conclusion: ${CONCLUSION}) - waiting..."
  fi
  sleep 15
done

if [[ "$CONCLUSION" != "success" ]]; then
  echo "APK build failed."
  exit 1
fi

echo "Fetching artifacts..."
ARTIFACTS_JSON=$(curl -sS -H "Authorization: token ${GITHUB_TOKEN}" -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/${OWNER}/${REPO}/actions/runs/${RUN_ID}/artifacts")

APKS=$(echo "$ARTIFACTS_JSON" | python3 -c 'import sys, json; d=json.load(sys.stdin); arts=d.get("artifacts", []); out=[]; import itertools;\
  for a in arts:\n    if a.get("name") in ("app-debug-apk", "app-release-apk"): out.append(f"{a["name"]}: {a.get(\"archive_download_url\")}")\n  print("\n".join(out))')
if [[ -z "$APKS" ]]; then
  echo "No APK artifacts found."
  exit 0
fi

echo "APK Artifacts:"
echo "$APKS" | sed 's/\t/  /g'

# Auto-download released APK to local path after successful run
if [[ -n "$RUN_ID" && "$CONCLUSION" == "success" ]]; then
  echo "Auto-downloading release APK to local path: ${LOCAL_DOWNLOAD_PATH}"
  bash scripts/download-release-apk.sh "$RUN_ID" "$LOCAL_DOWNLOAD_PATH"
fi

exit 0
