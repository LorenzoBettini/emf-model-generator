"""Fast contract tests for the opt-in selection investigation."""

import json
import hashlib
import subprocess
import sys
import unittest
from pathlib import Path
from unittest import mock

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

    def test_source_policy_accepts_diagnostic_edits_and_rejects_production(self):
        selection, current = self.selection()
        selection = json.loads(json.dumps(selection))
        selection["source_sha256"]["scripts/performance_evaluation.py"] = \
            hashlib.sha256(b"baseline").hexdigest()
        with mock.patch.object(investigation.subprocess, "check_output",
                               return_value=b"baseline"):
            self.assertEqual(investigation.selection_compatible(selection, current),
                             ["scripts/performance_evaluation.py"])
        current_changed = json.loads(json.dumps(current))
        current_changed["source_sha256"]["scripts/performance_evaluation.py"] = "f" * 64
        with self.assertRaisesRegex(ValueError, "audited version"):
            investigation.selection_compatible(selection, current_changed)
        selection["source_sha256"]["pom.xml"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "Production/workload"):
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
        result = subprocess.run([sys.executable, script, "run", "--preset",
                                 "crossref-comparison", "--heap", "2g", "--timeout", "900",
                                 "--selection", "target/benchmark/publication-selection.json"],
                                text=True, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("not available: no supported optimization", result.stderr)

    def test_vetted_publication_revision_is_compatible_for_diagnostics(self):
        selection, current = self.selection()
        selection = json.loads(json.dumps(selection))
        selection["host"]["git_revision"] = "ac673ba"
        for path in investigation.ALLOWED_DIAGNOSTIC_CHANGES:
            previous = subprocess.check_output(["git", "show", f"ac673ba:{path}"],
                                               cwd=publication.base.ROOT)
            selection["source_sha256"][path] = hashlib.sha256(previous).hexdigest()
        self.assertEqual(set(investigation.selection_compatible(selection, current)),
                         investigation.ALLOWED_DIAGNOSTIC_CHANGES)

    def test_prior_audited_diagnostic_requires_test_only_selection(self):
        selection, current = self.selection()
        selection = json.loads(json.dumps(selection))
        selection["test_only"] = True
        for path in investigation.ALLOWED_DIAGNOSTIC_CHANGES:
            selection["source_sha256"][path] = \
                investigation.PREVIOUS_DIAGNOSTIC_HASHES[path]
        self.assertEqual(set(investigation.selection_compatible(selection, current)),
                         {path for path in investigation.ALLOWED_DIAGNOSTIC_CHANGES
                          if investigation.PREVIOUS_DIAGNOSTIC_HASHES[path] !=
                          current["source_sha256"][path]})
        selection["test_only"] = False
        with self.assertRaises(ValueError):
            investigation.selection_compatible(selection, current)

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
