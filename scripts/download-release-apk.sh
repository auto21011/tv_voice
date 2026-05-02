#!/usr/bin/env bash
set -euo pipefail

# Usage: download-release-apk.sh <RUN_ID> [LOCAL_DIR]
RUN_ID="${1:-}"
LOCAL_DIR="${2:-downloads}"
if [[ -z "$RUN_ID" ]]; then
  echo "Usage: $0 <RUN_ID> [LOCAL_DIR]" >&2
  exit 1
fi
REPO_URL=$(git config --get remote.origin.url || true)
if [[ -z "$REPO_URL" ]]; then
  echo "ERROR: cannot determine repo URL" >&2
  exit 1
fi
URL_PART="$REPO_URL"
URL_PART="${URL_PART#git@github.com:}"
URL_PART="${URL_PART#https://github.com/}"
URL_PART="${URL_PART#http://github.com/}"
URL_PART="${URL_PART%.git}"
OWNER="${URL_PART%%/*}"
REPO="${URL_PART##*/}"
TOKEN="${GITHUB_TOKEN:-}"
if [[ -z "$TOKEN" ]]; then
  echo "ERROR: GITHUB_TOKEN not set" >&2
  exit 1
fi

ARTIFACTS_JSON=$(curl -sS -H "Authorization: token ${TOKEN}" -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/${OWNER}/${REPO}/actions/runs/${RUN_ID}/artifacts")

ARCHIVE_URL=$(echo "$ARTIFACTS_JSON" | python3 - << 'PY'
import sys, json
d=json.load(sys.stdin)
arts=d.get('artifacts', [])
for a in arts:
  if a.get('name') == 'app-release-apk':
    print(a.get('archive_download_url'))
    break
PY
)

if [[ -z "$ARCHIVE_URL" ]]; then
  echo "No app-release-apk artifact found for RUN_ID=$RUN_ID" >&2
  exit 0
fi

mkdir -p "$LOCAL_DIR"
ZIP_PATH="$LOCAL_DIR/app-release-apk.zip"
curl -L -sS -H "Authorization: token ${TOKEN}" -H "Accept: application/vnd.github+json" -o "$ZIP_PATH" "$ARCHIVE_URL"
if [[ ! -f "$ZIP_PATH" ]]; then
  echo "Failed to download APK artifact zip" >&2
  exit 1
fi
unzip -o "$ZIP_PATH" -d "$LOCAL_DIR" >/dev/null 2>&1 || true
APK_FOUND=$(find "$LOCAL_DIR" -type f -iname "*.apk" -print -quit 2>/dev/null)
if [[ -n "$APK_FOUND" ]]; then
  echo "APK located at: $APK_FOUND"
  # Optionally, normalize name
  mv "$APK_FOUND" "$LOCAL_DIR/app-release.apk" 2>/dev/null || true
  echo "Final path: $LOCAL_DIR/app-release.apk"
else
  echo "APK not found after extraction" >&2
fi
