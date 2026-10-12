"""Fast contracts for the opt-in reference-aware comparison."""

import argparse
import json
import tempfile
import unittest
from pathlib import Path

import publication_evaluation as publication
import reference_aware_comparison as comparison


class ReferenceAwareComparisonTest(unittest.TestCase):
    def args(self, **overrides):
        values = dict(preset="reference-aware-comparison", roots="100,1000,5000,10000,20000",
                      scenarios="plugin-eclipse,ant,extlibrary", heap="2g", timeout=1800,
                      max_eobjects=400000, max_resources=25000, warmups=3, repetitions=7,
                      selection=None, selection_output=None, output=None)
        values.update(overrides)
        return argparse.Namespace(**values)

    def test_dell_matrix_and_invalid_settings(self):
        cases = comparison.parse_cases(self.args())
        self.assertEqual(len(cases), 15)
        self.assertEqual([case["scenario"] for case in cases[::5]],
                         ["plugin-eclipse", "ant", "extlibrary"])
        self.assertTrue(all(case["preflight"] == "roundtrip" for case in cases))
        for change in (dict(selection="old.json"), dict(roots="2,1"),
                       dict(heap="0g"), dict(warmups=-1), dict(repetitions=0)):
            with self.assertRaises(ValueError):
                comparison.parse_cases(self.args(**change))
        self.assertEqual(len(comparison.parse_cases(self.args(
            roots="1,2,5", warmups=1, repetitions=2))), 9)

    def test_commands_differ_only_by_fork_identity(self):
        case = comparison.parse_cases(self.args(roots="1", scenarios="ant"))[0]
        with tempfile.TemporaryDirectory() as directory:
            baseline = comparison.comparison_command(case, Path(directory), "baseline",
                                                     "512m", 1, 2)
            variant = comparison.comparison_command(case, Path(directory), "reference-aware",
                                                    "512m", 1, 2)
        old, new = (publication.stem(case, mode) for mode in comparison.MODES)
        self.assertEqual([part.replace(new, old) for part in variant], baseline)
        self.assertIn("-Dbenchmark.diagnostics=off", variant)

    def test_skipped_case_remains_in_report(self):
        case = comparison.parse_cases(self.args(roots="1", scenarios="ant"))[0]
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            manifest = {"preset": "reference-aware-comparison", "selected_cases": [case],
                        "invocations": [], "skipped_cases": [
                            {"scenario": "ant", "roots": 1, "status": "skipped",
                             "reason": "projected EObject/resource cap"}],
                        "host": {"git_revision": "abc", "git_dirty": False,
                                 "platform": "test", "cpu_model": "test",
                                 "java_version_output": ["test"]},
                        "selector_source_sha256": "0" * 64, "heap": "512m",
                        "fixed_jvm_flags": [], "warmups": 1, "repetitions": 2}
            (output / "manifest.json").write_text(json.dumps(manifest))
            comparison.report_comparison(output)
            self.assertIn("skipped", (output / "report.md").read_text())
            self.assertTrue((output / "figure-comparison.svg").exists())


if __name__ == "__main__":
    unittest.main()
