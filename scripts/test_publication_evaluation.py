"""Fast publication-driver contract tests; no large models are generated here."""

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import publication_evaluation as publication


class PublicationEvaluationTest(unittest.TestCase):
    def test_runbook_cli_syntax_and_old_presets(self):
        script = Path(__file__).with_name("performance_evaluation.py")
        for command, expected in (([], "{run,report}"), (["run"], "large-calibration"),
                                  (["report"], "--latest")):
            process = subprocess.run([sys.executable, script, *command, "--help"],
                                     text=True, capture_output=True)
            self.assertEqual(process.returncode, 0, process.stderr)
            self.assertIn(expected, process.stdout)
        for preset in ("smoke", "pilot", "paper", "bpmn-pilot", "bpmn-paper"):
            self.assertTrue(__import__("performance_evaluation").invocation_list(preset))
        # The same arguments used in the runbook reach the intended parser branches.
        for command in (["run", "--preset", "large-calibration", "--heap", "2g",
                         "--timeout", "900", "--selection-output",
                         "target/benchmark/publication-selection.json"],
                        ["run", "--preset", "large-paper", "--heap", "2g",
                         "--timeout", "900", "--selection",
                         "target/benchmark/publication-selection.json"]):
            self.assertEqual(subprocess.run([sys.executable, script, *command, "--help"],
                                            capture_output=True).returncode, 0)

    def test_staged_matrix_and_bpmn_preflight(self):
        self.assertEqual({spec[1] for spec in publication.SERIES}, set(publication.PROFILES))
        self.assertEqual({spec[3] for spec in publication.SERIES if spec[2] == "bpmn"},
                         {1, 2})
        bibtex = next(spec for spec in publication.SERIES if spec[0] == "bibtex-breadth")
        self.assertTrue({32, 100, 512, 707, 1024}.issubset(bibtex[5]))
        case = publication.case_for(next(spec for spec in publication.SERIES
                                         if spec[0] == "bpmn-depth2-roots"), 5)
        self.assertEqual(case["preflight"], "validate")
        self.assertEqual(case["depth"], 2)
        self.assertEqual(case["roots"], 5)

    def test_staging_stops_on_projected_object_or_time_cap(self):
        spec = publication.SERIES[0]
        case = publication.case_for(spec, 1024)
        previous = [{**publication.case_for(spec, 707),
                     "observed_eobjects": 1_000_000, "wall_seconds": 3}]
        self.assertIn("EObject", publication.projected_over_cap(case, previous, 900))
        previous[0]["observed_eobjects"] = 100
        previous[0]["wall_seconds"] = 800
        self.assertIn("wall", publication.projected_over_cap(case, previous, 900))
        successes = [{"observed_eobjects": n} for n in (1, 2, 5, 10, 20, 50, 100, 200, 500)]
        chosen = publication.choose(successes)
        self.assertEqual(chosen[0]["observed_eobjects"], 1)
        self.assertEqual(chosen[-1]["observed_eobjects"], 500)
        self.assertEqual(len(chosen), 7)

    def test_selection_compatibility_and_safety_caps(self):
        current = publication.machine("2g")
        case = publication.case_for(publication.SERIES[0], 32)
        case.update(observed_eobjects=1057, observed_resources=1)
        selection = {"schema_version": publication.SCHEMA,
                     "kind": "large-calibration-selection", **current,
                     "selected": [case], "probes": [{**case, "status": "completed"}],
                     "test_only": False, "series_summary": {}}
        publication.compatible(selection, current)
        changed = json.loads(json.dumps(current))
        changed["heap"] = "4g"
        with self.assertRaisesRegex(ValueError, "heap"):
            publication.compatible(selection, changed)
        changed = json.loads(json.dumps(current))
        changed["metamodel_sha256"]["bibtex"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "metamodel_sha256"):
            publication.compatible(selection, changed)
        changed = json.loads(json.dumps(current))
        selector_path = next(path for path in changed["source_sha256"]
                             if path.endswith("EMFRoundRobinCandidateSelector.java"))
        changed["source_sha256"][selector_path] = "0" * 64
        with self.assertRaisesRegex(ValueError, "source_sha256"):
            publication.compatible(selection, changed)
        selection["selected"][0]["preflight"] = "none"
        with self.assertRaisesRegex(ValueError, "settings"):
            publication.compatible(selection, current)

    def test_checked_in_selection_schema_covers_profiles(self):
        schema = json.loads(Path(__file__).with_name("publication-selection.schema.json").read_text())
        self.assertEqual(schema["properties"]["schema_version"]["const"],
                         publication.SCHEMA)
        self.assertEqual(schema["properties"]["kind"]["const"],
                         "large-calibration-selection")
        self.assertEqual(set(schema["$defs"]["case"]["properties"]["scenario"]["enum"]),
                         set(publication.PROFILES))

    def test_command_uses_forked_flexible_execution_and_separate_modes(self):
        case = publication.case_for(publication.SERIES[0], 32)
        with tempfile.TemporaryDirectory() as temp:
            baseline = publication.command(case, Path(temp), "baseline", "2g", 3, 7)
            diagnostic = publication.command(case, Path(temp), "instrumented", "2g", 3, 7)
        self.assertIn("exec:exec@run-performance-tests", baseline)
        self.assertIn("-Dbenchmark.containment.max=32", baseline)
        self.assertIn("-Dbenchmark.warmups=3", baseline)
        self.assertIn("-Dbenchmark.repetitions=7", baseline)
        self.assertIn("-Dbenchmark.preflight=roundtrip", baseline)
        self.assertIn("-Dbenchmark.diagnostics=off", baseline)
        self.assertTrue(any(x.endswith("-diagnostics.csv") for x in diagnostic))

    def test_timeout_retains_log_and_status(self):
        case = publication.case_for(publication.SERIES[0], 1)
        with tempfile.TemporaryDirectory() as temp, mock.patch.object(
                publication.subprocess, "run", side_effect=subprocess.TimeoutExpired(
                    "mvnw", 1, output=b"started\n")):
            item = publication.invoke(case, Path(temp), "baseline", "2g", 1, 2, 1)
            self.assertEqual(item["status"], "timeout")
            self.assertIn("started", (Path(temp) / f"{item['stem']}.log").read_text())

    def test_publication_fork_clears_inherited_experimental_selector(self):
        case = publication.case_for(publication.SERIES[0], 1)
        with tempfile.TemporaryDirectory() as temp, mock.patch.dict(
                os.environ, {"BENCHMARK_SELECTION_VARIANT": "reference-aware"}), mock.patch.object(
                publication.subprocess, "run", side_effect=subprocess.TimeoutExpired(
                    "mvnw", 1, output=b"started\n")) as run:
            publication.invoke(case, Path(temp), "baseline", "512m", 1, 2, 1)
            self.assertNotIn("BENCHMARK_SELECTION_VARIANT", run.call_args.kwargs["env"])

    def test_oom_and_invalid_preflight_are_preserved(self):
        case = publication.case_for(publication.SERIES[0], 1)
        with tempfile.TemporaryDirectory() as temp:
            with mock.patch.object(publication.subprocess, "run", return_value=
                                   subprocess.CompletedProcess([], 1, "OutOfMemoryError")):
                item = publication.invoke(case, Path(temp), "baseline", "2g", 1, 2, 1)
            self.assertEqual(item["status"], "oom")
            self.assertTrue((Path(temp) / f"{item['stem']}.log").exists())
        with tempfile.TemporaryDirectory() as temp:
            def invalid(command, **_):
                raw = Path(next(x.split("=", 1)[1] for x in command
                                if x.startswith("-Dbenchmark.raw=")))
                row = {key: "" for key in __import__("performance_evaluation").RAW}
                row.update(status="invalid", error="required containment missing")
                __import__("performance_evaluation").write_csv(
                    raw, __import__("performance_evaluation").RAW, [row])
                return subprocess.CompletedProcess(command, 1, "preflight done status=invalid")
            with mock.patch.object(publication.subprocess, "run", side_effect=invalid):
                item = publication.invoke(case, Path(temp), "baseline", "2g", 1, 2, 1)
            self.assertEqual(item["status"], "invalid")
            self.assertIn("required containment", item["reason"])

    def test_partial_combined_report_includes_bpmn_and_failure(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)
            case = publication.case_for(publication.SERIES[-2], 1)
            case.update(observed_eobjects=5, observed_resources=1)
            selected = [case, publication.case_for(publication.SERIES[-1], 2)]
            selection = {"selected": selected, "probes": []}
            publication.write_json(output / "selection.json", selection)
            good = {**case, "profile": case["scenario"], "mode": "baseline",
                    "stem": "good", "status": "completed"}
            failed = {**selected[1], "profile": case["scenario"], "mode": "baseline",
                      "stem": "failed", "status": "timeout", "reason": "wall cap"}
            manifest = {"preset": "large-paper", "selection_file": "unused",
                        "selection_sha256": publication.file_hash(output / "selection.json"),
                        "invocations": [good, failed], "heap": "2g", "warmups": 3,
                        "repetitions": 7, "fixed_jvm_flags": publication.base.FIXED_FLAGS,
                        "host": {"platform": "test", "cpu_model": "test",
                                 "java_vendor": "test", "java_version_output": ["test"],
                                 "git_revision": "test", "git_dirty": False}}
            publication.write_json(output / "manifest.json", manifest)
            row = {key: "" for key in publication.FIGURE_COLUMNS}
            row.update(series=case["series"], scenario="bpmn-bounded", family="bpmn",
                       depth=1, changing_variable="roots", requested_roots=1,
                       containment_max=1, eobjects_min=5, eobjects_median=5,
                       eobjects_max=5, resources_min=1, resources_max=1, samples=7,
                       median_ns=1_000_000, p25_ns=900_000, p75_ns=1_100_000,
                       preflight="validate", heap="2g", status="completed", raw_file="good-raw.csv")
            with mock.patch.object(publication, "verified_case", return_value=row), \
                 mock.patch.object(publication, "invoke", side_effect=AssertionError("reran generation")):
                publication.report_publication(output)
            report = (output / "report.md").read_text()
            self.assertIn("INCOMPLETE", report)
            self.assertIn("bpmn-bounded", report)
            self.assertIn("validate", report)
            self.assertIn("timeout", (output / "failure-capped-cases.csv").read_text())
            for name in publication.FIGURES.values():
                self.assertTrue((output / (name + ".svg")).exists())
                self.assertTrue((output / (name + ".csv")).exists())


if __name__ == "__main__":
    unittest.main()
