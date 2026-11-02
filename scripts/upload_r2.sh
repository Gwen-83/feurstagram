#!/usr/bin/env bash
set -euo pipefail

APK_PATH="${1:-./public/feurstagram-release.apk}"
R2_REMOTE="${R2_REMOTE:-r2}"
R2_BUCKET="${R2_BUCKET:-feurstagram-apk}"
R2_ENDPOINT="${R2_ENDPOINT:-https://5924d3e3a1c8d798a247ddcd798bdd0f.r2.cloudflarestorage.com}"

if ! command -v rclone >/dev/null 2>&1; then
  echo "rclone not installed. Install it first: https://rclone.org/install/"
  exit 1
fi

if ! rclone config show "$R2_REMOTE" >/dev/null 2>&1; then
  echo "Remote '$R2_REMOTE' is not configured. Run:"
  echo "  rclone config"
  echo "Then use:"
  echo "  provider = Cloudflare"
  echo "  access_key_id = <your R2 access key>"
  echo "  secret_access_key = <your R2 secret key>"
  echo "  endpoint = $R2_ENDPOINT"
  echo "  region = auto"
  exit 1
fi

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK not found: $APK_PATH"
  exit 1
fi

rclone copy "$APK_PATH" "$R2_REMOTE:$R2_BUCKET"

echo "Upload complete."
