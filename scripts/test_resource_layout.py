"""Fast parser and report tests for fixed-model layout experiments."""

import argparse
import json
import tempfile
import unittest
from pathlib import Path

import performance_evaluation as base
import resource_layout as layout
import publication_evaluation as pub


class ResourceLayoutTest(unittest.TestCase):
    def args(self, **updates):
        values = dict(scenario="extlibrary", roots="7", resources="1,3,7",
                      selection=None, selection_output=None, heap="512m",
                      max_eobjects=100, max_resources=10, warmups=1,
                      repetitions=2, timeout=120)
        values.update(updates)
        return argparse.Namespace(**values)

    def test_accepts_uneven_partition(self):
        self.assertEqual(layout.parse_cases(self.args()), (7, [1, 3, 7]))

    def test_rejects_invalid_bounds_and_lists(self):
        for changes in (dict(roots="0"), dict(resources="1,8"),
                        dict(resources="3,1"), dict(resources="1,1"),
                        dict(resources="0"), dict(max_eobjects=0),
                        dict(max_resources=0), dict(warmups=-1),
                        dict(repetitions=0), dict(timeout=0),
                        dict(scenario="ant")):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                layout.parse_cases(self.args(**changes))

    def test_provenance_sources_exist_and_are_hashed(self):
        for source in layout.SOURCES:
            with self.subTest(source=source):
                self.assertEqual(len(pub.file_hash(base.ROOT / source)), 64)

    def test_report_flags_missing_requested_layout(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)
            (output / "manifest.json").write_text(json.dumps({
                "preset": "resource-layout", "roots": 7,
                "requested_resources": [1], "heap": "512m", "warmups": 1,
                "repetitions": 2, "host": {"git_revision": "x", "git_dirty": True,
                "platform": "test", "cpu_model": "test", "java_vendor": "test",
                "java_version_output": ["test"]},
                "metamodel_sha256": {"extlibrary": "a" * 64},
                "invocations": []}))
            self.assertFalse(layout.report_layout(output))
            self.assertIn("requested layouts missing", (output / "report.md").read_text())

    def test_report_preserves_failed_case(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)
            (output / "manifest.json").write_text(json.dumps({
                "preset": "resource-layout", "roots": 7,
                "requested_resources": [1, 3, 7], "heap": "512m", "warmups": 1,
                "repetitions": 2, "host": {"git_revision": "x", "git_dirty": True,
                "platform": "test", "cpu_model": "test", "java_vendor": "test",
                "java_version_output": ["test"]},
                "metamodel_sha256": {"extlibrary": "a" * 64},
                "invocations": [{"resources": 1, "stem": "one", "status": "timeout",
                                 "reason": "limit"}]}))
            self.assertFalse(layout.report_layout(output))
            rows = base.read_csv(output / "summary.csv", layout.SUMMARY)
            self.assertEqual(len(rows), len(layout.STAGES))
            self.assertTrue(all(row["status"] == "timeout" for row in rows))
            self.assertIn("NO-GO", (output / "report.md").read_text())


if __name__ == "__main__":
    unittest.main()
