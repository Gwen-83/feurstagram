#!/usr/bin/env bash
set -euo pipefail

# Publish a Feurstagram update bundle to a static HTTPS host.
#
# Usage:
#   ./scripts/publish_update.sh <apk-path> <version> <manifest-url> [output-dir]
#
# Example:
#   ./scripts/publish_update.sh build/outputs/feurstagram.apk 1.0.2 https://updates.example.com ./dist
#
# Optional env vars for automatic upload:
#   export PUBLIC_UPDATE_BASE_URL='https://updates.example.com'
#   export UPLOAD_MODE='scp'       # scp | r2 | none
#   export UPLOAD_TARGET='user@host:/var/www/updates/'
#   export R2_ENDPOINT='https://<account>.r2.cloudflarestorage.com'
#   export R2_BUCKET='<bucket-name>'
#   export AWS_ACCESS_KEY_ID='...'
#   export AWS_SECRET_ACCESS_KEY='...'
#
# Required environment for signing if you want to rebuild a release first:
#   export FEURSTAGRAM_KEYSTORE=/home/gwenael/Documents/code/FeurStagram/feurstagram.keystore
#   export FEURSTAGRAM_KEYSTORE_PASS='...'
#   export FEURSTAGRAM_KEY_PASS='...'
#   export FEURSTAGRAM_KEY_ALIAS='feurstagram'

if [ "$#" -lt 3 ]; then
  echo "Usage: $0 <apk-path> <version> <manifest-url> [output-dir]" >&2
  echo "Example: $0 build/outputs/feurstagram.apk 1.0.2 https://updates.example.com ./dist" >&2
  exit 1
fi

APK_PATH="$1"
VERSION="$2"
MANIFEST_URL="$3"
OUTPUT_DIR="${4:-build/updates}"
UPLOAD_MODE="${UPLOAD_MODE:-none}"
UPLOAD_TARGET="${UPLOAD_TARGET:-}"
R2_ENDPOINT="${R2_ENDPOINT:-}"
R2_BUCKET="${R2_BUCKET:-}"
PUBLIC_UPDATE_BASE_URL="${PUBLIC_UPDATE_BASE_URL:-$MANIFEST_URL}"
PUBLIC_UPDATE_BASE_URL="${PUBLIC_UPDATE_BASE_URL%/}"

if [ ! -f "$APK_PATH" ]; then
  echo "Error: APK not found: $APK_PATH" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"
APK_DEST="$OUTPUT_DIR/feurstagram-release.apk"
MANIFEST_PATH="$OUTPUT_DIR/updates.json"
cp "$APK_PATH" "$APK_DEST"

cat > "$MANIFEST_PATH" <<EOF
{
  "version": "$VERSION",
  "apkUrl": "$PUBLIC_UPDATE_BASE_URL/feurstagram-release.apk",
  "notes": "Version $VERSION - mise à jour Feurstagram."
}
EOF

echo "Generated update package in: $OUTPUT_DIR"
echo "APK: $APK_DEST"
echo "Manifest: $MANIFEST_PATH"

auto_upload() {
  case "$UPLOAD_MODE" in
    scp)
      if [ -z "$UPLOAD_TARGET" ]; then
        echo "UPLOAD_MODE=scp requires UPLOAD_TARGET to be set, for example: user@server:/var/www/updates/" >&2
        return 1
      fi
      echo "Uploading with scp to $UPLOAD_TARGET"
      scp -r "$OUTPUT_DIR"/* "$UPLOAD_TARGET"
      ;;
    rclone|r2)
      if [ -z "$R2_BUCKET" ]; then
        echo "UPLOAD_MODE=$UPLOAD_MODE requires R2_BUCKET to be set." >&2
        return 1
      fi
      if ! command -v rclone >/dev/null 2>&1; then
        echo "rclone is required for UPLOAD_MODE=$UPLOAD_MODE. Install it first." >&2
        return 1
      fi
      R2_REMOTE="${R2_REMOTE:-r2}"
      echo "Uploading with rclone to Cloudflare R2 bucket $R2_BUCKET via remote $R2_REMOTE"
      rclone copy "$APK_DEST" "$R2_REMOTE:$R2_BUCKET/" --quiet
      rclone copy "$MANIFEST_PATH" "$R2_REMOTE:$R2_BUCKET/" --quiet
      ;;
    none|"")
      echo "Automatic upload disabled (UPLOAD_MODE=none)."
      ;;
    *)
      echo "Unsupported UPLOAD_MODE='$UPLOAD_MODE'. Supported: none, scp, rclone, r2" >&2
      return 1
      ;;
  esac
}

auto_upload || exit 1

echo ""
echo "Phones should read the update feed at:"
echo "  $PUBLIC_UPDATE_BASE_URL/updates.json"

echo ""
echo "If uploading manually, use:"
echo "  scp -r $OUTPUT_DIR/* user@server:/var/www/updates/"
echo ""
echo "Keep the same signing key between builds to allow Android to accept the update in place."
