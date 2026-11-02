# Build from source

This guide explains how to build Feurstagram yourself. Building from source lets
you inspect the patches and avoid trusting a prebuilt APK.

For the prepared Fedora/Linux workstation, see the
[French setup and installation guide](SETUP_LINUX.md).

## Requirements

- JDK 21 and the Android SDK (`ANDROID_HOME` set, build-tools installed)
- The Morphe CLI JAR in `tools/` (`morphe-cli-*.jar` or `morphe-desktop-*.jar`)
- APK/APKM inputs may be stored in `build/inputs/` (local files in `build/`
  are ignored by Git)
- The Gradle Wrapper distribution and all Gradle plugins/libraries already
  present locally. `build.sh` checks the Wrapper distribution before invoking
  Gradle, then resolves dependencies in offline mode.
- Morphe CLI, APKEditor for split bundles, and Android SDK build-tools
  (`aapt` and `apksigner`) installed locally. These tools are separate from
  Gradle's offline dependency cache.
- A supported Instagram APK (arm64-v8a from a trusted source such as APKMirror)

## Steps

1. Use this local source checkout. The build does not clone or download
   Feurstagram source from GitHub. Keep the required tools, Android SDK,
   Instagram APK, Gradle distribution, and dependencies available locally.

2. Run regression tests before building:

   ```sh
   ./gradlew --offline -p . :extensions:extension:testDebugUnitTest
   python3 -m unittest discover -s scripts -p 'test_*.py'
   ```

   The build also verifies the patch report, APK permission declarations, and
   the `android:debuggable` flag before publishing an APK.

3. Build and apply the patches to your Instagram APK. The wrapper distribution,
   Gradle dependencies, Morphe/APKEditor tools, SDK, and input APK must all be
   available locally; the script does not download them.

   ```sh
   ./build.sh build/inputs/instagram.apk
   ```

   Add `--clone` to install alongside a stock Instagram, and `--install` to push
   to a connected device:

   ```sh
   ./build.sh build/inputs/instagram.apk --clone --install
   ```

   A development build uses a separate output (`feurstagram-debug.apk`) and
   report (`patch-report-debug.json`); it is marked **DEBUG — do not distribute**
   and never replaces the release APK.
   The patched APK retains the ABI splits present in the input bundle; the
   current tested Instagram bundle contains only `arm64-v8a`. The result is
   therefore not a universal APK. There is no post-build ABI stripping or
   architecture-specific output mode: use an input bundle compatible with the
   target device.

4. On Linux systems that allow isolated network namespaces, verify the complete
   build with networking disabled:

   ```sh
   scripts/test_offline_build.sh build/inputs/instagram.apk
   ```

   This produces only the separate debug APK. The command fails explicitly if
   `unshare --net` is unavailable or not permitted.

5. Install the signed result (if you did not pass `--install`).
   ```sh
   adb install -r build/outputs/feurstagram.apk
   ```

## Signing

The bundle is signed during the build. Set `FEURSTAGRAM_KEYSTORE_PASS` (and
optionally `FEURSTAGRAM_KEY_PASS`) to sign with `feurstagram.keystore`;
otherwise a throwaway keystore is generated. Do not commit your keystore, and
keep the same one to reinstall without losing app data. The build passes these
passwords to `apksigner` through standard input rather than command-line
arguments.

## Release and update flow

For the full server-side publish process and the key-handling procedure for in-place updates, follow [docs/maintenance/DEPLOIEMENT_MISE_A_JOUR.md](maintenance/DEPLOIEMENT_MISE_A_JOUR.md). A helper can also be used to prepare a release bundle with a manifest and a signed APK: [scripts/publish_update.sh](../scripts/publish_update.sh).

## Notes

- Only use APKs from trusted sources.
- Patches are fingerprint-based, so a new Instagram version usually only needs a
  rebuild.
- Versions included in the tested build matrix and the scope of those checks
  are recorded in [the Instagram compatibility matrix](maintenance/COMPATIBILITE_INSTAGRAM.md).
- If the build fails, check open issues or create a bug report.
