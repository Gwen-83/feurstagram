import json
import pathlib
import tempfile
import unittest
from unittest.mock import patch

from verify_build import verify_apk_manifest, verify_report


class VerifyBuildTest(unittest.TestCase):
    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temporary_directory.name)
        self.report_path = self.root / "report.json"
        self.apk_path = self.root / "candidate.apk"
        self.apk_path.write_bytes(b"apk")

    def tearDown(self):
        self.temporary_directory.cleanup()

    def write_report(self, report):
        self.report_path.write_text(json.dumps(report), encoding="utf-8")

    def test_accepts_release_report_without_debug_patch(self):
        self.write_report({
            "success": True,
            "packageVersion": "454.0.0.13.77",
            "appliedPatches": ["Feed item filtering"],
            "failedPatches": [],
        })
        verify_report(self.report_path, self.apk_path, False)

    def test_rejects_failed_or_empty_patch_reports(self):
        for report in (
            {"success": False, "appliedPatches": [], "failedPatches": ["one"]},
            {"success": True, "appliedPatches": [], "failedPatches": []},
            {"success": True, "appliedPatches": None, "failedPatches": []},
            {"success": True, "appliedPatches": [], "failedPatches": None},
        ):
            with self.subTest(report=report):
                self.write_report(report)
                with self.assertRaises(ValueError):
                    verify_report(self.report_path, self.apk_path, False)

    def test_debug_and_release_reports_must_match_requested_flavor(self):
        self.write_report({
            "success": True,
            "appliedPatches": ["Debug bridge"],
            "failedPatches": [],
        })
        with self.assertRaisesRegex(ValueError, "release report"):
            verify_report(self.report_path, self.apk_path, False)

        self.write_report({
            "success": True,
            "appliedPatches": ["Feed item filtering"],
            "failedPatches": [],
        })
        with self.assertRaisesRegex(ValueError, "debug build report"):
            verify_report(self.report_path, self.apk_path, True)

    @patch("verify_build.subprocess.run")
    def test_manifest_permissions_and_debuggable_flag_match_build_flavor(self, run):
        result = lambda output: type("Result", (), {"stdout": output})()
        run.side_effect = [
            result("package: com.instagram.android\n"),
            result("package: com.instagram.android\n"),
        ]
        verify_apk_manifest("aapt", self.apk_path, False)

        run.side_effect = [
            result("package: com.instagram.android\nuses-permission: name='android.permission.DUMP'\n"),
            result("package: com.instagram.android\napplication-debuggable\n"),
        ]
        with self.assertRaisesRegex(ValueError, "android.permission.DUMP"):
            verify_apk_manifest("aapt", self.apk_path, True)

        run.side_effect = [
            result("package: com.instagram.android\n"),
            result("package: com.instagram.android\napplication-debuggable\n"),
        ]
        with self.assertRaisesRegex(ValueError, "debuggable"):
            verify_apk_manifest("aapt", self.apk_path, False)


if __name__ == "__main__":
    unittest.main()
