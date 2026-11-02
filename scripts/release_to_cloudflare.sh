#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_APK="${INPUT_APK:-${1:-$ROOT_DIR/build/inputs/instagram-454.apkm}}"
VERSION="${VERSION:-${2:-$(date -u +%Y.%m.%d)}}"
PUBLIC_UPDATE_BASE_URL="${PUBLIC_UPDATE_BASE_URL:-${R2_PUBLIC_BASE_URL:-https://<your-account>.r2.dev}}"
R2_BUCKET="${R2_BUCKET:-feurstagram-apk}"
R2_REMOTE="${R2_REMOTE:-r2}"
UPLOAD_MODE="${UPLOAD_MODE:-rclone}"

if [ ! -f "$INPUT_APK" ]; then
  echo "Input APK not found: $INPUT_APK" >&2
  exit 1
fi

if ! command -v rclone >/dev/null 2>&1; then
  echo "rclone is required. Install it first: https://rclone.org/downloads/" >&2
  exit 1
fi

cd "$ROOT_DIR"
./build.sh "$INPUT_APK"
./scripts/publish_update.sh "$ROOT_DIR/build/outputs/feurstagram.apk" "$VERSION" "$PUBLIC_UPDATE_BASE_URL" "$ROOT_DIR/public"

if [ "$UPLOAD_MODE" = "rclone" ]; then
  rclone copy "$ROOT_DIR/public/feurstagram-release.apk" "$R2_REMOTE:$R2_BUCKET/" --quiet
  rclone copy "$ROOT_DIR/public/updates.json" "$R2_REMOTE:$R2_BUCKET/" --quiet
else
  echo "UPLOAD_MODE=$UPLOAD_MODE is not handled by this helper; use scripts/publish_update.sh directly." >&2
  exit 1
fi

echo "Published build $VERSION to $PUBLIC_UPDATE_BASE_URL"
echo "Manifest: $PUBLIC_UPDATE_BASE_URL/updates.json"
echo "APK: $PUBLIC_UPDATE_BASE_URL/feurstagram-release.apk"
