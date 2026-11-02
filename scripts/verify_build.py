#!/usr/bin/env python3
"""Reproducible checks for patch reports and generated APK manifest permissions."""

import json
import pathlib
import subprocess
import sys


DEBUG_PATCH = "Debug bridge"


def _patch_name(patch):
    return patch.get("name", "unknown") if isinstance(patch, dict) else str(patch)


def verify_report(report_path, apk_path, debug):
    try:
        report = json.loads(pathlib.Path(report_path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"could not read patch report: {error}") from error
    if not isinstance(report, dict):
        raise ValueError("patch report has an unexpected format")

    failed = report.get("failedPatches", [])
    applied = report.get("appliedPatches", [])
    if not isinstance(failed, list) or not isinstance(applied, list):
        raise ValueError("patch report lists have an unexpected format")
    if report.get("success") is not True or failed:
        failures = ", ".join(_patch_name(patch) for patch in failed) or "unsuccessful build"
        raise ValueError(f"patch report does not describe a fully successful build: {failures}")
    if not applied:
        raise ValueError("patch report lists no applied patches")
    if not pathlib.Path(apk_path).is_file() or pathlib.Path(apk_path).stat().st_size == 0:
        raise ValueError("patcher produced no candidate APK")

    applied_names = {_patch_name(patch) for patch in applied}
    if debug and DEBUG_PATCH not in applied_names:
        raise ValueError("debug build report does not list the Debug bridge patch")
    if not debug and DEBUG_PATCH in applied_names:
        raise ValueError("release report unexpectedly lists the Debug bridge patch")

    print(f"    verified report: {report.get('packageVersion', 'unknown Instagram version')}")
    for patch in applied:
        print(f"    ok   {_patch_name(patch)}")


def verify_apk_manifest(aapt, apk_path, debug):
    try:
        permissions = subprocess.run(
            [aapt, "dump", "permissions", str(apk_path)],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
        badging = subprocess.run(
            [aapt, "dump", "badging", str(apk_path)],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError) as error:
        raise ValueError(f"could not inspect candidate APK manifest: {error}") from error

    if "package:" not in permissions or "package:" not in badging:
        raise ValueError("aapt returned an unexpected manifest format")
    if "android.permission.DUMP" in permissions:
        raise ValueError("candidate APK declares android.permission.DUMP")
    is_debuggable = any(line.strip() == "application-debuggable" for line in badging.splitlines())
    if is_debuggable != debug:
        flavor = "debug" if debug else "release"
        raise ValueError(f"{flavor} APK has an unexpected android:debuggable manifest flag")

    print("    verified manifest permissions and debuggable flag")


def main(argv):
    if len(argv) == 5 and argv[1] == "report":
        if argv[4] not in ("0", "1"):
            raise ValueError("build flavor must be 0 (release) or 1 (debug)")
        verify_report(argv[2], argv[3], argv[4] == "1")
    elif len(argv) == 5 and argv[1] == "apk":
        if argv[4] not in ("0", "1"):
            raise ValueError("build flavor must be 0 (release) or 1 (debug)")
        verify_apk_manifest(argv[2], argv[3], argv[4] == "1")
    else:
        raise ValueError(
            "usage: verify_build.py report <report.json> <candidate.apk> <0|1>"
            " | apk <aapt> <candidate.apk> <0|1>"
        )


if __name__ == "__main__":
    try:
        main(sys.argv)
    except ValueError as error:
        print(f"Error: {error}", file=sys.stderr)
        raise SystemExit(1)
