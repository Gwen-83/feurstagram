# Roadmap

This roadmap is intentionally simple and may evolve over time.

## Reliability

- Improve compatibility with new Instagram versions.
- Document known issues per release.
- Improve clone APK behavior where possible.
- Add basic smoke tests where possible.

## Privacy and trust

- Publish SHA256 hashes for every release artifact.
- Document what each patch changes.
- Improve build-from-source instructions.
- Document blocked endpoints or telemetry-related changes when relevant.

## Installation

- Make the difference between classic APK and clone APK clearer.
- Improve troubleshooting for Android installation warnings.
- Add a dedicated installation guide.

## Community

- Label beginner-friendly issues.
- Add templates for bug reports and feature requests.
- Document contribution workflow.

## Features

Recently delivered:

- Granular controls for Reels tab content, recommended Reels in the Home feed,
  the Stories tray, and opening Stories. Navigation icons are independent from
  content blocking.
- Messages only, Following only, and Strict mode settings profiles, with a
  preview and confirmation before preferences are replaced.
- A second settings shortcut by long-pressing Profile when that tab is shown.

Potential future improvements:

- measure and validate ABI-specific APK outputs when supported multi-ABI
  Instagram bundles and target devices are available; current tested output is
  already `arm64-v8a` only;
- a safe way to distinguish Reels opened from messages from other shared
  videos, without blocking ordinary message media;
- device verification of Story viewer endpoint coverage across supported
  Instagram versions;
- optional settings import/export;
- clearer update flow when Instagram releases new versions.
