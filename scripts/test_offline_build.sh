#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
    echo "usage: scripts/test_offline_build.sh <instagram.apk|.apkm|.xapk|.apks>" >&2
    exit 1
fi
if ! command -v unshare >/dev/null 2>&1; then
    echo "Error: unshare is required to run the offline integration build." >&2
    exit 1
fi
if ! unshare --net true >/dev/null 2>&1; then
    echo "Error: this system does not allow an isolated network namespace." >&2
    exit 1
fi

# The debug flavor keeps this integration check from replacing the release APK.
exec unshare --net -- "$DIR/build.sh" "$1" --debug
