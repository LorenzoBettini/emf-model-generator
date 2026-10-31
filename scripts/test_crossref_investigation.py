"""Fast contract tests for the opt-in selection investigation."""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import crossref_investigation as investigation
import profile_crossref_jfr as jfr
import publication_evaluation as publication


class CrossReferenceInvestigationTest(unittest.TestCase):
    def selection(self):
        current = publication.machine("2g")
        spec = next(spec for spec in publication.SERIES if spec[0] == "plugin-eclipse-roots")
        cases = []
        for roots in (1, 2, 5, 10, 20):
            case = publication.case_for(spec, roots)
            case.update(observed_eobjects=roots * 14, observed_resources=roots)
            cases.append(case)
        selection = {"schema_version": publication.SCHEMA,
                     "kind": "large-calibration-selection", **current,
                     "selected": cases,
                     "probes": [{**case, "status": "completed"} for case in cases]}
        return selection, current

    def test_fresh_calibration_with_identical_source_is_compatible(self):
        selection, current = self.selection()
        self.assertEqual(investigation.selection_compatible(selection, current), [])

    def test_source_and_host_changes_require_recalibration(self):
        for path in ("scripts/performance_evaluation.py", "pom.xml"):
            selection, current = self.selection()
            selection = json.loads(json.dumps(selection))
            selection["source_sha256"][path] = "0" * 64
            with self.subTest(path=path):
                with self.assertRaisesRegex(ValueError, "source_sha256 changed"):
                    investigation.selection_compatible(selection, current)
        selection, current = self.selection()
        selection = json.loads(json.dumps(selection))
        selection["host"]["cpu_model"] = "different"
        with self.assertRaisesRegex(ValueError, "host.cpu_model"):
            investigation.selection_compatible(selection, current)

    def test_bounded_choice_and_exact_cli_syntax(self):
        selection, _ = self.selection()
        cases, skipped = investigation.choose_cases(selection)
        self.assertEqual([case["roots"] for case in cases], [1, 5, 20])
        self.assertEqual(len(skipped), 2)
        script = Path(__file__).with_name("performance_evaluation.py")
        for command in (("run", "--preset", "crossref-investigation", "--heap", "2g",
                         "--timeout", "900", "--selection",
                         "target/benchmark/publication-selection.json"),
                        ("report", "--latest", "crossref-investigation")):
            result = subprocess.run([sys.executable, script, *command, "--help"],
                                    text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_report_preserves_failures_for_calibrated_and_explicit_runs(self):
        selection, current = self.selection()
        for mode in ("calibrated", "explicit-roots"):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as directory:
                output = Path(directory)
                selection_path = output / "selection.json"
                selection_path.write_text(json.dumps(selection))
                manifest = {"preset": "crossref-investigation", "mode": mode, **current,
                            "selection_sha256": publication.file_hash(selection_path),
                            "calibration_host": selection["host"],
                            "caps": {"eobjects": 1000, "resources": 10},
                            "timeout_seconds_per_invocation": 60,
                            "invocations": [{"series": "plugin-eclipse-roots", "roots": 1,
                                             "status": "timeout", "reason": "fork timed out"}],
                            "skipped_cases": []}
                (output / "manifest.json").write_text(json.dumps(manifest))
                investigation.report_investigation(output)
                self.assertIn("failure-capped-cases.csv", (output / "report.md").read_text())
                failures = (output / "failure-capped-cases.csv").read_text()
                self.assertIn("timeout", failures)
                self.assertIn("fork timed out", failures)

    def test_explicit_root_caps_reject_large_work_before_maven(self):
        script = Path(__file__).with_name("performance_evaluation.py")
        base = [sys.executable, script, "run", "--preset", "crossref-investigation",
                "--roots", "1,100", "--heap", "512m", "--timeout", "60",
                "--max-eobjects", "100", "--max-resources", "10"]
        result = subprocess.run(base, text=True, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("projected EObject/resource caps", result.stderr)
        self.assertNotIn("mvnw", result.stderr)
        result = subprocess.run(base + ["--selection", "target/benchmark/x.json"],
                                text=True, capture_output=True)
        self.assertIn("incompatible with selection", result.stderr)

    def test_jfr_profile_requires_explicit_safe_bounds(self):
        jfr.validate(5, 1000, 10, 60)
        with self.assertRaisesRegex(ValueError, "projected"):
            jfr.validate(20000, 1000, 10, 60)
        with self.assertRaisesRegex(ValueError, "positive"):
            jfr.validate(0, 1000, 10, 60)
