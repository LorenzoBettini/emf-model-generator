"""Bounded, selection-compatible cross-reference attempt diagnostics."""

import hashlib
import json
import statistics
import subprocess
from pathlib import Path

import performance_evaluation as base
import publication_evaluation as pub


COLUMNS = "run_id scenario run_index reference calls selected custom_selected null_selections no_candidates exhausted_cycles attempts rejected_unknown max_attempts median_attempts p95_attempts attempt_histogram selection_elapsed_ns retrieval_elapsed_ns compute_calls candidate_entries compute_elapsed_ns status".split()
ALLOWED_DIAGNOSTIC_CHANGES = {
    "scripts/performance_evaluation.py", "scripts/publication_evaluation.py",
    "src/test/java/io/github/lorenzobettini/emfmodelgenerator/performance/"
    "EMFModelGeneratorPerformanceStatistics.java",
}
# Exact audited transition from the publication runner to this diagnostic runner.
# A later edit must either match calibration anew or explicitly review this policy.
VETTED_DIAGNOSTIC_HASHES = {
    "scripts/performance_evaluation.py":
        "96bb0e81501b4ec115fcd94ffb9a9523b62455d7e7e743447c318363ce9ffd49",
    "scripts/publication_evaluation.py":
        "a3d30c7530c7ef45499448437ef7d4e4d3219b778d5371e2ea2b3e11abbdb1ee",
    "src/test/java/io/github/lorenzobettini/emfmodelgenerator/performance/"
    "EMFModelGeneratorPerformanceStatistics.java":
        "55e065f3cdfa9b3735ab45eacccb081836f76aba3c56a686b8ae97f6adb34c17",
}
MAX_DIAGNOSTIC_OBJECTS = 200_000
MAX_DIAGNOSTIC_RESOURCES = 5_000


def selection_compatible(selection, current):
    """Compare immutable workload/production inputs, retaining both revision identities."""
    if selection.get("schema_version") != pub.SCHEMA or selection.get("kind") != \
            "large-calibration-selection" or not selection.get("selected"):
        raise ValueError("A nonempty large-calibration selection is required")
    for key in ("heap", "fixed_jvm_flags", "metamodel_sha256"):
        if selection.get(key) != current[key]:
            raise ValueError(f"Selection {key} changed; recalibrate on this host")
    for key in ("platform", "cpu_model", "logical_cores", "ram_bytes", "java_vendor",
                "java_version_output"):
        if selection["host"].get(key) != current["host"].get(key):
            raise ValueError(f"Selection host.{key} changed; use this host's calibration")
    old = selection.get("source_sha256", {})
    new = current["source_sha256"]
    if old.keys() != new.keys():
        raise ValueError("Source inventory changed; cannot prove selection compatibility")
    changes = {key for key in old if old[key] != new[key]}
    if changes - ALLOWED_DIAGNOSTIC_CHANGES:
        raise ValueError("Production/workload source changed: " +
                         ", ".join(sorted(changes - ALLOWED_DIAGNOSTIC_CHANGES)))
    # A differing benchmark file must be the exact historical input recorded by
    # calibration, rather than an arbitrary edit to scenario setup or timing.
    for path in changes:
        if new[path] != VETTED_DIAGNOSTIC_HASHES[path]:
            raise ValueError(f"Diagnostic source {path} differs from audited version; "
                             "recalibrate or review the compatibility policy")
        revision = selection["host"].get("git_revision")
        try:
            previous = subprocess.check_output(["git", "show", f"{revision}:{path}"],
                                               cwd=base.ROOT, stderr=subprocess.DEVNULL)
        except (OSError, subprocess.CalledProcessError) as exc:
            raise ValueError(f"Cannot audit calibration source {path}; recalibrate") from exc
        if hashlib.sha256(previous).hexdigest() != old[path]:
            raise ValueError(f"Calibration source {path} had uncommitted changes; "
                             "cannot audit compatibility")
        vetted = subprocess.check_output(["git", "show", f"ac673ba:{path}"],
                                         cwd=base.ROOT, stderr=subprocess.DEVNULL)
        if hashlib.sha256(vetted).hexdigest() != old[path]:
            raise ValueError(f"Calibration benchmark source {path} is not the vetted "
                             "publication runner; recalibrate")
    for case in selection["selected"]:
        pub.validate_case(case)
        if not any(probe.get("status") == "completed" and
                   all(probe.get(key) == case[key] for key in
                       ("series", "scenario", "depth", "roots", "containment_max",
                        "observed_eobjects", "observed_resources"))
                   for probe in selection.get("probes", [])):
            raise ValueError("Selection has an uncalibrated workload")
    return sorted(changes)


def choose_cases(selection):
    chosen, skipped = [], []
    for scenario in ("plugin-eclipse", "extlibrary", "ant"):
        cases = sorted((case for case in selection["selected"]
                        if case["family"] == "many-roots" and case["scenario"] == scenario),
                       key=lambda case: case["observed_eobjects"])
        eligible = []
        for case in cases:
            if (case["observed_eobjects"] > MAX_DIAGNOSTIC_OBJECTS or
                    case["observed_resources"] > MAX_DIAGNOSTIC_RESOURCES):
                skipped.append({"series": case["series"], "roots": case["roots"],
                                "reason": "diagnostic EObject/resource cap"})
            else:
                eligible.append(case)
        if not eligible:
            continue
        positions = sorted({0, len(eligible) // 2, len(eligible) - 1})
        chosen.extend(eligible[index] for index in positions)
        skipped.extend({"series": case["series"], "roots": case["roots"],
                        "reason": "bounded representative subset"}
                       for index, case in enumerate(eligible) if index not in positions)
    if not chosen:
        raise ValueError("No calibrated Ant, EXTLibrary or PluginEclipse many-root case "
                         "fits the diagnostic caps")
    return chosen, skipped


def run_investigation(args):
    if args.selection_output or not args.selection:
        raise ValueError("crossref-investigation requires --selection and no --selection-output")
    if not args.heap or not __import__("re").fullmatch(r"[1-9][0-9]*[mgMG]", args.heap):
        raise ValueError("--heap must be a positive JVM size such as 2g")
    selection_path = Path(args.selection)
    selection = json.loads(selection_path.read_text())
    current = pub.machine(args.heap)
    allowed_changes = selection_compatible(selection, current)
    cases, skipped = choose_cases(selection)
    output = pub.new_output(args.preset, args.output)
    pub.write_json(output / "selection.json", selection)
    manifest = {"schema_version": 1, "preset": args.preset, "created_utc": pub.utc(),
                **current, "selection_sha256": pub.file_hash(output / "selection.json"),
                "original_selection_sha256": pub.file_hash(selection_path),
                "calibration_host": selection["host"], "allowed_diagnostic_changes": allowed_changes,
                "warmups": 1, "repetitions": 2,
                "timeout_seconds_per_invocation": args.timeout,
                "caps": {"eobjects": MAX_DIAGNOSTIC_OBJECTS,
                         "resources": MAX_DIAGNOSTIC_RESOURCES},
                "selected_cases": cases, "skipped_cases": skipped, "invocations": []}
    pub.write_json(output / "manifest.json", manifest)
    wrapper = "./mvnw"
    subprocess.run([wrapper, "-q", "-DskipTests", "test-compile"], cwd=base.ROOT,
                   check=True, timeout=args.timeout)
    for case in cases:
        item = pub.invoke(case, output, "selection", args.heap, 1, 2, args.timeout)
        manifest["invocations"].append(item)
        pub.write_json(output / "manifest.json", manifest)
    report_investigation(output)
    pub.save_latest(args.preset, output)
    print(f"Result directory: {output}", flush=True)


def validated_rows(output, item, manifest):
    for filename, expected in item.get("sha256", {}).items():
        if pub.file_hash(output / filename) != expected:
            raise ValueError(f"{filename}: checksum changed")
    raw = base.read_csv(output / (item["stem"] + "-raw.csv"), base.RAW)
    rows = base.read_csv(output / (item["stem"] + "-diagnostics.csv"), COLUMNS)
    if len(raw) != manifest["repetitions"] or any(row["status"] != "ok" or
            row["measurement_mode"] != "instrumented" for row in raw):
        raise ValueError(f"{item['stem']}: incomplete instrumented raw observations")
    ids = {row["run_id"] for row in raw}
    if len(ids) != 1 or any(row["run_id"] not in ids for row in rows):
        raise ValueError(f"{item['stem']}: run IDs disagree")
    summary = base.read_csv(output / (item["stem"] + "-summary.csv"), base.SUMMARY)
    times = [int(row["elapsed_ns"]) for row in raw]
    if (len(summary) != 1 or summary[0]["run_id"] not in ids or
            summary[0]["status"] != "ok" or
            int(summary[0]["successful_samples"]) != len(raw) or
            int(summary[0]["median_ns"]) != base.quantile(times, 0.5) or
            int(summary[0]["p25_ns"]) != base.quantile(times, 0.25) or
            int(summary[0]["p75_ns"]) != base.quantile(times, 0.75)):
        raise ValueError(f"{item['stem']}: summary disagrees with raw samples")
    log = (output / (item["stem"] + ".log")).read_text()
    for index, raw_row in enumerate(raw, 1):
        subset = [row for row in rows if int(row["run_index"]) == index]
        if not subset:
            raise ValueError(f"{item['stem']}: missing per-reference rows for sample {index}")
        if (int(raw_row["eobjects"]) != item["observed_eobjects"] or
                int(raw_row["model_resources"]) != item["observed_resources"]):
            raise ValueError(f"{item['stem']}: output shape differs from calibration")
        if (raw_row["scenario"] != item["scenario"] or
                int(raw_row["run_index"]) != index or
                int(raw_row["requested_roots"]) != item["roots"] or
                int(raw_row["depth"]) != item["depth"] or
                raw_row["preflight"] != item["preflight"] or
                raw_row["metamodel_sha256"] != manifest["metamodel_sha256"][item["scenario"]] or
                any(int(raw_row[key]) != item[key] for key in
                    ("attribute_max", "containment_max", "cross_reference_max",
                     "feature_map_max"))):
            raise ValueError(f"{item['stem']}: workload provenance mismatch")
        if (f"measurement {index}/{len(raw)} done status=ok "
                f"actual_roots={raw_row['actual_roots']} "
                f"eobjects={raw_row['eobjects']} "
                f"model_resources={raw_row['model_resources']} "
                f"elapsed_ns={raw_row['elapsed_ns']}") not in log:
            raise ValueError(f"{item['stem']}: console disagrees with raw sample")
        for flag in (f"-Xms{manifest['heap']}", f"-Xmx{manifest['heap']}",
                     *manifest["fixed_jvm_flags"]):
            if flag not in raw_row["jvm_args"].split():
                raise ValueError(f"{item['stem']}: JVM flags changed")
        for row in subset:
            hist = dict(part.split(":") for part in row["attempt_histogram"].split(";"))
            calls = int(row["calls"])
            if (sum(int(value) for value in hist.values()) != calls or
                    sum(int(key) * int(value) for key, value in hist.items()) !=
                    int(row["attempts"]) or
                    int(row["selected"]) + int(row["null_selections"]) != calls or
                    int(row["rejected_unknown"]) > int(row["attempts"])):
                raise ValueError(f"{item['stem']}: inconsistent attempt counters")
    return raw, rows


def report_investigation(output):
    output = Path(output).resolve()
    manifest = json.loads((output / "manifest.json").read_text())
    if manifest["preset"] != "crossref-investigation":
        raise ValueError("Wrong report preset")
    if pub.file_hash(output / "selection.json") != manifest["selection_sha256"]:
        raise ValueError("Selection checksum changed")
    cases = []
    features = []
    failures = []
    for item in manifest["invocations"]:
        if item["status"] != "completed":
            failures.append({"series": item["series"], "roots": item["roots"],
                             "status": item["status"], "reason": item.get("reason", "")})
            continue
        raw, rows = validated_rows(output, item, manifest)
        cases.append({"series": item["series"], "scenario": item["scenario"],
                      "roots": item["roots"], "eobjects": item["observed_eobjects"],
                      "resources": item["observed_resources"],
                      "samples": len(raw),
                      "generation_median_ns_instrumented": statistics.median(
                          int(row["elapsed_ns"]) for row in raw),
                      **{key: statistics.median(sum(int(row[key]) for row in rows
                                                     if int(row["run_index"]) == index)
                                                 for index in range(1, len(raw) + 1))
                         for key in ("calls", "attempts", "rejected_unknown",
                                     "null_selections", "exhausted_cycles",
                                     "selection_elapsed_ns", "retrieval_elapsed_ns")},
                      "compute_elapsed_ns": statistics.median(
                          max(int(row["compute_elapsed_ns"]) for row in rows
                              if int(row["run_index"]) == index)
                          for index in range(1, len(raw) + 1))})
        for row in rows:
            features.append({"series": item["series"], "roots": item["roots"], **row})
    base.write_csv(output / "selection-cases.csv", list(cases[0]) if cases else
                   ["series", "scenario", "roots", "eobjects", "resources", "samples",
                    "generation_median_ns_instrumented", "calls", "attempts",
                    "rejected_unknown", "null_selections", "exhausted_cycles",
                    "selection_elapsed_ns", "retrieval_elapsed_ns", "compute_elapsed_ns"], cases)
    base.write_csv(output / "selection-features.csv", ["series", "roots"] + COLUMNS, features)
    hotspot_rows = []
    groups = sorted({(row["series"], row["roots"], row["reference"])
                     for row in features})
    for series, roots, reference in groups:
        subset = [row for row in features if (row["series"], row["roots"],
                                               row["reference"]) == (series, roots, reference)]
        hotspot_rows.append({"series": series, "roots": roots, "reference": reference,
                             "samples": len(subset),
                             **{key + "_median": statistics.median(int(row[key]) for row in subset)
                                for key in ("calls", "attempts", "rejected_unknown",
                                            "null_selections", "exhausted_cycles",
                                            "selection_elapsed_ns")}})
    hotspot_columns = ["series", "roots", "reference", "samples"] + [
        key + "_median" for key in ("calls", "attempts", "rejected_unknown",
                                    "null_selections", "exhausted_cycles",
                                    "selection_elapsed_ns")]
    base.write_csv(output / "selection-hotspots.csv", hotspot_columns, hotspot_rows)
    base.write_csv(output / "failure-capped-cases.csv", ["series", "roots", "status", "reason"],
                   failures + [{**case, "status": "skipped"} for case in manifest["skipped_cases"]])
    lines = ["# Cross-reference selection investigation", "",
             "**Diagnostic measurements only; no uninstrumented speed comparison.**", "",
             f"Calibration revision: `{manifest['calibration_host']['git_revision']}`; "
             f"diagnostic revision: `{manifest['host']['git_revision']}`; "
             f"allowed changed benchmark files: {manifest['allowed_diagnostic_changes']}.",
             f"Host: {manifest['host']['platform']}; heap: {manifest['heap']}; "
             f"JDK: {manifest['host']['java_version_output']}; "
             f"JVM flags: {manifest['fixed_jvm_flags']}.",
             "Production Java, scenario configuration, Maven POM, metamodel hashes, "
             "JVM and physical host match the calibrated selection. Only the listed "
             "benchmark driver/runner files may differ. Git revision alone is not a "
             "compatibility test; the original selection is archived intact.", "",
             "| Series | Roots | EObjects | Samples | Calls | Attempts | Rejected "
             "(cause unknown) | Exhausted | Selection ms | Compute ms |",
             "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for case in cases:
        lines.append(f"| {case['series']} | {case['roots']} | {case['eobjects']} | "
                     f"{case['samples']} | {case['calls']} | {case['attempts']} | "
                     f"{case['rejected_unknown']} | {case['exhausted_cycles']} | "
                     f"{case['selection_elapsed_ns']/1e6:.3f} | "
                     f"{case['compute_elapsed_ns']/1e6:.3f} |")
    lines += ["", "`selection-features.csv` contains each measured repetition and reference, "
              "including an exact attempts-per-call histogram, median, p95 and maximum. "
              "`selection-cases.csv` gives per-case medians; `selection-hotspots.csv` "
              "gives per-reference medians. Failed/capped cases are in "
              "`failure-capped-cases.csv`; raw CSVs and fork logs remain present.", "",
              "## Per-reference hot spots (instrumented selection elapsed)", "",
              "| Series | Roots | Reference | Attempts median | Rejected median | Selection ms median |",
              "|---|---:|---|---:|---:|---:|"]
    for row in sorted(hotspot_rows, key=lambda row: row["selection_elapsed_ns_median"],
                      reverse=True)[:12]:
        lines.append(f"| {row['series']} | {row['roots']} | {row['reference']} | "
                     f"{row['attempts_median']} | {row['rejected_unknown_median']} | "
                     f"{row['selection_elapsed_ns_median']/1e6:.3f} |")
    lines += ["",
              "A selector call returning the first candidate again closes an exhausted cycle "
              "and is not a second attempted candidate. A null first retrieval means zero "
              "attempts. A non-null custom-function result with no selector call is counted "
              "separately. Each `selectValue` invocation is counted once; unique-list retries "
              "are separate invocations, and list membership cost is not isolated here. "
              "Rejected causes remain unknown because the production validity predicate is "
              "private; no extra policy/predicate invocation was made.", "",
              "Selection elapsed includes selector retrieval and candidate compute. Retrieval "
              "includes compute; these are nested timings and must not be added. All elapsed "
              "values are instrumented, include probe overhead, and are not comparable to "
              "the old uninstrumented publication wall times. EMF eSet/opposite maintenance, "
              "list.contains, setup and GC can also contribute to generation time.", "",
              "## Decision gate", "",
              "This diagnostic report alone cannot establish that rejection loops caused "
              "PluginEclipse slowdown. Phase B is skipped; no safe experimental fast path "
              "or uninstrumented A/B comparison is offered.", ""]
    (output / "report.md").write_text("\n".join(lines))
    print(f"Regenerated cross-reference report: {output}", flush=True)
