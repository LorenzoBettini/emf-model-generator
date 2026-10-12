"""Fast contract tests for the optional experimental selector comparison."""

import argparse
import json
import tempfile
import unittest
from pathlib import Path

import crossref_comparison as comparison
import publication_evaluation as publication


class CrossReferenceComparisonTest(unittest.TestCase):
    def args(self, **overrides):
        values = dict(roots="100,1000,5000,10000,20000",
                      scenarios="plugin-eclipse,ant,extlibrary", heap="2g",
                      timeout=1800, max_eobjects=400000, max_resources=25000,
                      warmups=3, repetitions=7, selection=None, selection_output=None)
        values.update(overrides)
        return argparse.Namespace(**values)

    def test_exact_dell_case_matrix_and_bounds(self):
        cases = comparison.parse_cases(self.args())
        self.assertEqual(len(cases), 15)
        self.assertEqual([case["scenario"] for case in cases[::5]],
                         ["plugin-eclipse", "ant", "extlibrary"])
        self.assertTrue(all(case["preflight"] == "roundtrip" for case in cases))
        for arguments in (dict(warmups=2), dict(repetitions=6),
                          dict(selection="old.json"), dict(roots="2,1")):
            with self.assertRaises(ValueError):
                comparison.parse_cases(self.args(**arguments))

    def test_command_diff_is_only_output_identity(self):
        case = comparison.parse_cases(self.args(roots="1", scenarios="ant"))[0]
        with tempfile.TemporaryDirectory() as directory:
            baseline = comparison.comparison_command(case, Path(directory), "baseline",
                                                     "512m", 3, 7)
            variant = comparison.comparison_command(case, Path(directory),
                                                    "experimental-cursor", "512m", 3, 7)
        self.assertEqual(len(baseline), len(variant))
        old, new = (publication.stem(case, mode) for mode in comparison.MODES)
        self.assertEqual([part.replace(new, old) for part in variant], baseline)
        self.assertIn("-Dbenchmark.diagnostics=off", variant)

    def test_capped_case_report_preserves_failure(self):
        case = comparison.parse_cases(self.args(roots="1", scenarios="ant"))[0]
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            manifest = {"preset": "crossref-comparison", "selected_cases": [case],
                        "invocations": [], "skipped_cases": [
                            {"scenario": "ant", "roots": 1, "status": "capped",
                             "reason": "projected EObject/resource cap"}],
                        "host": {"git_revision": "abc", "git_dirty": False,
                                 "platform": "test", "cpu_model": "test",
                                 "java_version_output": ["test"]},
                        "selector_source_sha256": "0" * 64, "heap": "512m",
                        "fixed_jvm_flags": [], "warmups": 3, "repetitions": 7}
            (output / "manifest.json").write_text(json.dumps(manifest))
            comparison.report_comparison(output)
            self.assertIn("capped", (output / "report.md").read_text())
            self.assertEqual(len((output / "failure-capped-cases.csv").read_text().splitlines()), 2)
            self.assertTrue((output / "figure-comparison.svg").exists())


if __name__ == "__main__":
    unittest.main()
