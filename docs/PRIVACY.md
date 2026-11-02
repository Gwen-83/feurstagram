# Privacy

Feurstagram is designed to remove distracting and intrusive parts of Instagram.

## Data collection

Feurstagram itself does not collect analytics, credentials, personal data, or usage data.
The installed app does not check GitHub for releases, fetch release notes, or
download executable updates.

The GitHub Pages site does not load third-party analytics scripts.

## Credentials

Feurstagram does not collect, store, proxy, or transmit Instagram usernames, passwords, or session tokens.

Authentication is handled by Instagram inside the app.

## Network behavior

Feurstagram may block or modify some Instagram app behavior depending on the active patches.

Instagram continues to communicate with Meta. Feurstagram's patches block or
modify selected Instagram requests as documented in the README. Opening an
external support link is user-initiated.

The local build checks that the Gradle Wrapper distribution is already cached,
then invokes Gradle in offline mode using locally cached plugins and libraries.
The Morphe CLI, APKEditor (for split bundles), Android SDK build-tools, and the
Instagram APK are separate local inputs; Gradle's offline mode does not provide
or download them. For an end-to-end network-isolated build check on Linux, use
`scripts/test_offline_build.sh` on a system that permits `unshare --net`.

## Third parties

Feurstagram is not affiliated with Instagram, Meta, Facebook, or any third party.

## Recommendation

If you do not want to trust a prebuilt APK, build Feurstagram yourself from source.
