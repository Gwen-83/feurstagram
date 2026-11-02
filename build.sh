#!/usr/bin/env bash
#
# Feurstagram build pipeline.
#
#   ./build.sh <instagram.apk|.apkm|.xapk|.apks> [--clone] [--install] [--debug]
#
# Builds the patch bundle (.mpp) from the Gradle project, then applies it to the
# given Instagram APK with the local Morphe CLI. Release and debug APKs have
# separate outputs; --debug never overwrites the last release APK.
#   --clone     install side-by-side as a separate package (com.instagram.android.feurstagram)
#   --install   install the result on the connected ADB device
#   --debug     enable the Debug bridge patch: the settings become drivable over
#               ADB broadcasts (see extensions/.../DebugBridge.java). Never ship it.
#
# Split bundles: an APKMirror .apkm (or .xapk/.apks) is a zip of base.apk plus
# split APKs. The Morphe CLI patches one APK, so those are merged into a single
# universal APK with APKEditor (tools/APKEditor-*.jar) before patching. The merge
# is cached under build/intermediates/merged/ and reused while it is newer than
# the bundle.
#
# Signing: set FEURSTAGRAM_KEYSTORE_PASS (and optionally FEURSTAGRAM_KEY_PASS)
# to sign with feurstagram.keystore. That keystore is PKCS12, which the Morphe
# CLI cannot read (it expects BKS), so the APK is built unsigned and signed with
# the Android SDK's apksigner — this reproduces the existing release signature,
# so users update in place without uninstalling. Override the keystore/alias
# with FEURSTAGRAM_KEYSTORE / FEURSTAGRAM_KEY_ALIAS. Without a keystore password
# the CLI signs with a throwaway key (fine for testing, not for release).
set -euo pipefail

# Note the "|| true" on the ls|head lookups below: under `pipefail` a glob that
# matches nothing makes the whole substitution fail, and `set -e` would abort the
# script before the "not found" message it is meant to feed could ever print.
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# The CLI jar was renamed morphe-cli-* -> morphe-desktop-* upstream at 1.11; accept both.
CLI="$(ls -t "$DIR"/tools/morphe-cli-*.jar "$DIR"/tools/morphe-desktop-*.jar 2>/dev/null | head -1 || true)"

# Morphe's Android Gradle plugin targets JDK 17-21; prefer JDK 21 so the build
# does not pick up a newer system JDK when JAVA_HOME is not already configured.
if [ -z "${JAVA_HOME:-}" ]; then
    for jdk in \
        "$HOME/.local/opt/temurin-21" \
        "/usr/lib/jvm/java-21-openjdk" \
        "/usr/lib/jvm/temurin-21-jdk" \
        "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"; do
        if [ -x "$jdk/bin/java" ] && [ -x "$jdk/bin/javac" ]; then
            export JAVA_HOME="$jdk"
            break
        fi
    done
fi

# The extension is an Android library, so the Android SDK must be locatable.
if [ -z "${ANDROID_HOME:-}" ]; then
    for sdk in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" \
        "/opt/homebrew/share/android-commandlinetools" "/usr/local/share/android-commandlinetools" \
        "${ANDROID_SDK_ROOT:-}"; do
        if [ -n "$sdk" ] && { [ -d "$sdk/platform-tools" ] || [ -d "$sdk/build-tools" ]; }; then
            export ANDROID_HOME="$sdk"
            break
        fi
    done
fi

# Run every JVM tool with the pinned JDK rather than whatever `java` is on PATH
# (Homebrew often puts a JDK 17 there, which the patcher rejects).
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
JAVA_BIN="${JAVA_BIN:-java}"

# Signing material. apksigner ships with the Android SDK build-tools.
KEYSTORE="${FEURSTAGRAM_KEYSTORE:-$DIR/feurstagram.keystore}"
KEY_ALIAS="${FEURSTAGRAM_KEY_ALIAS:-feurstagram}"
APKSIGNER=""
AAPT=""
if [ -n "${ANDROID_HOME:-}" ]; then
    APKSIGNER="$(ls -t "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | head -1 || true)"
    AAPT="$(ls -t "$ANDROID_HOME"/build-tools/*/aapt 2>/dev/null | head -1 || true)"
fi

APK=""
CLONE=0
INSTALL=0
DEBUG=0
for arg in "$@"; do
    case "$arg" in
        --clone) CLONE=1 ;;
        --install) INSTALL=1 ;;
        --debug) DEBUG=1 ;;
        *) APK="$arg" ;;
    esac
done

if [ -z "$APK" ] || [ ! -f "$APK" ]; then
    echo "usage: ./build.sh <instagram.apk|.apkm|.xapk|.apks> [--clone] [--install] [--debug]" >&2
    exit 1
fi
if [ -z "$CLI" ]; then
    echo "Error: Morphe CLI not found under tools/ (morphe-cli-*.jar / morphe-desktop-*.jar)." >&2
    exit 1
fi
if [ -z "$APKSIGNER" ] || [ -z "$AAPT" ]; then
    echo "Error: Android SDK build-tools with both apksigner and aapt are required." >&2
    exit 1
fi

# Fail before invoking the wrapper: Gradle --offline prevents dependency
# downloads, but the wrapper itself would still download its distribution.
WRAPPER_PROPERTIES="$DIR/gradle/wrapper/gradle-wrapper.properties"
WRAPPER_URL="$(sed -n 's/^distributionUrl=//p' "$WRAPPER_PROPERTIES" | sed 's/\\:/:/g')"
if [ -z "$WRAPPER_URL" ]; then
    echo "Error: could not read distributionUrl from $WRAPPER_PROPERTIES." >&2
    exit 1
fi
WRAPPER_ARCHIVE="${WRAPPER_URL##*/}"
WRAPPER_CACHE_NAME="${WRAPPER_ARCHIVE%.zip}"
WRAPPER_VERSION_DIR="${WRAPPER_CACHE_NAME%-bin}"
WRAPPER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
WRAPPER_DIST_ROOT="$WRAPPER_HOME/wrapper/dists/$WRAPPER_CACHE_NAME"
WRAPPER_GRADLE=""
for candidate in "$WRAPPER_DIST_ROOT"/*/"$WRAPPER_VERSION_DIR/bin/gradle"; do
    if [ -x "$candidate" ]; then
        WRAPPER_GRADLE="$candidate"
        break
    fi
done
if [ -z "$WRAPPER_GRADLE" ]; then
    echo "Error: Gradle distribution $WRAPPER_ARCHIVE is not cached under $WRAPPER_DIST_ROOT." >&2
    echo "       Install/cache it while online before running this offline build." >&2
    exit 1
fi

SUFFIX=""
if [ "$DEBUG" -eq 1 ]; then
    SUFFIX="-debug"
fi
OUT="$DIR/build/outputs/feurstagram${SUFFIX}.apk"
CANDIDATE="$DIR/build/outputs/feurstagram${SUFFIX}-candidate.apk"
REPORT="$DIR/build/reports/patch-report${SUFFIX}.json"

# A split bundle has to become one universal APK before the patcher can touch it.
case "$APK" in
    *.apkm | *.xapk | *.apks)
        EDITOR="$(ls -t "$DIR"/tools/APKEditor-*.jar 2>/dev/null | head -1 || true)"
        if [ -z "$EDITOR" ]; then
            echo "Error: $(basename "$APK") is a split bundle and needs APKEditor to merge." >&2
            echo "       Place a locally available APKEditor-*.jar in tools/, then re-run." >&2
            exit 1
        fi
        MERGED="$DIR/build/intermediates/merged/$(basename "${APK%.*}").apk"
        if [ -f "$MERGED" ] && [ "$MERGED" -nt "$APK" ]; then
            echo "==> [0/3] Reusing merged bundle: $(basename "$MERGED")"
        else
            echo "==> [0/3] Merging splits from $(basename "$APK")"
            mkdir -p "$(dirname "$MERGED")"
            # The bundle holds base.apk plus feature/density splits; -f overwrites
            # a stale merge. Instagram's is ~400 MB, so give the JVM room. The
            # per-file merge log is noisy, so keep it on disk rather than inline.
            if ! "$JAVA_BIN" -Xmx4g -jar "$EDITOR" m -f -i "$APK" -o "$MERGED" > "$MERGED.log" 2>&1; then
                echo "Error: APKEditor failed to merge $(basename "$APK")." >&2
                echo "       Log: $MERGED.log" >&2
                exit 1
            fi
            echo "    merged: $MERGED"
        fi
        APK="$MERGED"
        ;;
esac

echo "==> [1/3] Building patch bundle (.mpp)"
"$DIR/gradlew" --offline -p "$DIR" :patches:build
MPP="$(ls -t "$DIR"/patches/build/libs/patches-*[0-9].mpp 2>/dev/null | grep -v -- '-sources\|-javadoc' | head -1 || true)"
if [ -z "$MPP" ]; then
    echo "Error: no .mpp produced under patches/build/libs/" >&2
    exit 1
fi
echo "    bundle: $MPP"

echo "==> [2/3] Applying to $(basename "$APK")"
# Scratch files are purged by default in the CLI; -r writes a JSON report of each
# patch step so a fingerprint that stopped matching is visible instead of silent.
mkdir -p "$DIR/build/reports" "$DIR/build/outputs"
rm -f "$REPORT" "$CANDIDATE" "$CANDIDATE.idsig"
ARGS=(-jar "$CLI" patch -p "$MPP" -f -r "$REPORT" -o "$CANDIDATE")
[ "$CLONE" -eq 1 ] && ARGS+=(-e "Clone")
[ "$DEBUG" -eq 1 ] && ARGS+=(-e "Debug bridge")

# With a keystore password, defer signing to apksigner (the CLI can't read the
# PKCS12 keystore); otherwise let the CLI sign with a throwaway key for testing.
SIGN_WITH_APKSIGNER=0
if [ -n "${FEURSTAGRAM_KEYSTORE_PASS:-}" ]; then
    if [ ! -f "$KEYSTORE" ]; then
        echo "Error: keystore not found: $KEYSTORE" >&2
        exit 1
    fi
    if [ -z "$APKSIGNER" ]; then
        echo "Error: apksigner not found under \$ANDROID_HOME/build-tools." >&2
        echo "       Install the Android SDK build-tools, or unset FEURSTAGRAM_KEYSTORE_PASS" >&2
        echo "       to sign with a throwaway key (testing only)." >&2
        exit 1
    fi
    SIGN_WITH_APKSIGNER=1
    ARGS+=(--unsigned)
fi
ARGS+=("$APK")
if "$JAVA_BIN" "${ARGS[@]}"; then
    :
else
    echo "Error: patcher failed; no new APK was published." >&2
    if [ -f "$REPORT" ]; then
        python3 - "$REPORT" <<'EOF' || echo "Error: could not read patch report." >&2
import json, sys

report = json.load(open(sys.argv[1], encoding="utf-8"))
for patch in report.get("failedPatches", []):
    if isinstance(patch, dict):
        print(f"    FAIL {patch.get('name', 'unknown')}: {patch.get('error') or patch.get('exception') or ''}")
    else:
        print(f"    FAIL {patch}")
EOF
    fi
    exit 1
fi

if [ ! -s "$REPORT" ]; then
    echo "Error: patcher did not produce a report; no new APK was published." >&2
    exit 1
fi

python3 "$DIR/scripts/verify_build.py" report "$REPORT" "$CANDIDATE" "$DEBUG"
python3 "$DIR/scripts/verify_build.py" apk "$AAPT" "$CANDIDATE" "$DEBUG"

if [ "$SIGN_WITH_APKSIGNER" -eq 1 ]; then
    echo "    signing with apksigner ($(basename "$KEYSTORE"), alias $KEY_ALIAS)"
    # Force v1+v2+v3 so the signature is accepted across the whole user base's
    # devices and matches the schemes prior releases shipped; v4 (the .idsig
    # sidecar) is only useful for adb incremental install, so leave it off.
    printf '%s\n%s\n' \
        "$FEURSTAGRAM_KEYSTORE_PASS" \
        "${FEURSTAGRAM_KEY_PASS:-$FEURSTAGRAM_KEYSTORE_PASS}" \
        | "$APKSIGNER" sign \
            --ks "$KEYSTORE" \
            --ks-key-alias "$KEY_ALIAS" \
            --ks-pass stdin \
            --key-pass stdin \
            --v1-signing-enabled true \
            --v2-signing-enabled true \
            --v3-signing-enabled true \
            --v4-signing-enabled false \
            "$CANDIDATE"
    # apksigner only writes a v4 .idsig when v4 is enabled; clean up just in case.
    rm -f "$CANDIDATE.idsig"
    "$APKSIGNER" verify --print-certs "$CANDIDATE" 2>/dev/null \
        | grep -i "SHA-256" | head -1 | sed 's/^/    cert /' || true
fi

"$APKSIGNER" verify "$CANDIDATE"
mv -f "$CANDIDATE" "$OUT"

echo "==> [3/3] Output: $OUT"
if [ "$DEBUG" -eq 1 ]; then
    echo "    !! DEBUG — do not distribute; ADB debug bridge enabled"
fi
if [ "$INSTALL" -eq 1 ]; then
    echo "    installing on device..."
    adb install -r "$OUT"
fi
