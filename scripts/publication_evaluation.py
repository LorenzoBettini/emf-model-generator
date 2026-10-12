"""Staged, host-specific publication workload selection and combined reporting."""

import csv
import datetime as dt
import hashlib
import html
import json
import math
import os
import re
import statistics
import subprocess
from pathlib import Path

import performance_evaluation as base


SCHEMA = 1
PROFILES = {
    "extlibrary": ("src/test/resources/inputs/extlibrary.ecore", "extlibrary", "Library", "roundtrip"),
    "bibtex": ("src/test/resources/external-metamodels/BibTeX.ecore", "BIBTEX", "Bibtex", "roundtrip"),
    "ant": ("src/test/resources/external-metamodels/Ant.ecore", "Ant", "Project", "roundtrip"),
    "plugin-eclipse": ("src/test/resources/external-metamodels/PluginEclipse.ecore", "PluginEclipse", "Eclipse", "roundtrip"),
    "rss": ("src/test/resources/external-metamodels/RSS-2.0.ecore", "RSS", "RSS", "roundtrip"),
    "bpmn-bounded": ("src/test/resources/external-metamodels/BPMN.ecore", "bpmn", "BpmnDiagram", "validate"),
}
ROOTS = (1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000)
BPMN_ROOTS = (1, 2, 3, 5, 10, 20, 50, 100, 200)
SERIES = (
    ("bibtex-breadth", "bibtex", "contained", 2, "breadth", (1, 4, 10, 32, 100, 316, 512, 707, 1024)),
    ("rss-breadth", "rss", "contained", 3, "breadth", (1, 2, 4, 8, 16, 32, 64, 128, 256, 512)),
    ("ant-breadth", "ant", "contained", 2, "breadth", (1, 2, 4, 8, 16, 32, 64, 128, 256)),
    ("extlibrary-roots", "extlibrary", "many-roots", 1, "roots", ROOTS),
    ("ant-roots", "ant", "many-roots", 2, "roots", ROOTS),
    ("plugin-eclipse-roots", "plugin-eclipse", "many-roots", 4, "roots", ROOTS),
    ("rss-roots", "rss", "many-roots", 3, "roots", ROOTS),
    ("bpmn-depth1-roots", "bpmn-bounded", "bpmn", 1, "roots", BPMN_ROOTS),
    ("bpmn-depth2-roots", "bpmn-bounded", "bpmn", 2, "roots", BPMN_ROOTS),
)
MAX_OBJECTS = 1_200_000
MAX_RESOURCES = 20_000
MAX_BREADTH = 1024
MIN_FREE_RAM_FACTOR = 1.25
FIGURES = {"contained": "figure-contained", "many-roots": "figure-many-roots",
           "diagnostic": "figure-diagnostic", "bpmn": "figure-bpmn"}
SOURCES = ("scripts/performance_evaluation.py", "scripts/publication_evaluation.py",
           "src/test/java/io/github/lorenzobettini/emfmodelgenerator/performance/"
           "EMFModelGeneratorPerformanceStatistics.java",
           "src/test/java/io/github/lorenzobettini/emfmodelgenerator/performance/ScenarioProfiles.java",
           "pom.xml")
FIGURE_COLUMNS = ["series", "scenario", "family", "depth", "changing_variable",
                  "requested_roots", "containment_max", "eobjects_min", "eobjects_median",
                  "eobjects_max", "resources_min", "resources_max", "samples", "median_ns",
                  "p25_ns", "p75_ns", "preflight", "heap", "status", "raw_file"]
DIAG_COLUMNS = FIGURE_COLUMNS + ["compute_calls_median", "candidate_entries_median",
                                 "compute_ns_median", "baseline_median_ns",
                                 "compute_fraction_of_baseline"]
FAIL_COLUMNS = ["stage", "series", "scenario", "depth", "roots", "containment_max",
                "mode", "status", "reason", "stem", "log_file", "raw_file"]


def utc():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")


def file_hash(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def fingerprints():
    return {name: file_hash(base.ROOT / details[0]) for name, details in PROFILES.items()}


def source_fingerprints():
    paths = list(SOURCES) + [str(path.relative_to(base.ROOT)) for path in sorted(
        (base.ROOT / "src" / "main" / "java").rglob("*.java"))]
    return {path: file_hash(base.ROOT / path) for path in paths}


def machine(heap):
    host = base.host_metadata()
    heap_bytes = int(heap[:-1]) * (1024 ** (3 if heap[-1].lower() == "g" else 2))
    if host["ram_bytes"] is not None and heap_bytes * MIN_FREE_RAM_FACTOR > host["ram_bytes"]:
        raise ValueError("Selected heap is too large for physical RAM; choose a smaller "
                         "matched calibration/publication heap")
    return {"host": host, "heap": heap, "fixed_jvm_flags": base.FIXED_FLAGS,
            "metamodel_sha256": fingerprints(),
            "source_sha256": source_fingerprints()}


def compatible(selection, current):
    if (selection.get("schema_version") != SCHEMA or
            selection.get("kind") != "large-calibration-selection"):
        raise ValueError("Unsupported selection schema; rerun large-calibration")
    if not isinstance(selection.get("test_only"), bool) or not isinstance(
            selection.get("series_summary"), dict):
        raise ValueError("Selection is missing calibration coverage metadata")
    for key in ("heap", "fixed_jvm_flags", "metamodel_sha256", "source_sha256"):
        if selection.get(key) != current[key]:
            raise ValueError(f"Selection {key} changed; rerun large-calibration on this setup")
    for key in ("platform", "cpu_model", "logical_cores", "ram_bytes", "java_vendor",
                "java_version_output", "git_revision", "git_dirty"):
        if selection["host"].get(key) != current["host"].get(key):
            raise ValueError(f"Selection host.{key} changed; rerun large-calibration")
    if not selection.get("selected"):
        raise ValueError("Selection has no feasible workloads; inspect calibration probes")
    for case in selection["selected"]:
        validate_case(case)
        if not any(probe.get("status") == "completed" and
                   all(probe.get(key) == case[key] for key in
                       ("series", "scenario", "depth", "roots", "containment_max",
                        "observed_eobjects", "observed_resources"))
                   for probe in selection.get("probes", [])):
            raise ValueError("Selection contains an uncalibrated workload")


def validate_case(case):
    spec = next((s for s in SERIES if s[0] == case["series"]), None)
    if spec is None:
        raise ValueError("Selection contains an unknown series")
    value = case["roots"] if spec[4] == "roots" else case["containment_max"]
    expected = case_for(spec, value)
    if value not in spec[5] or any(case.get(key) != value for key, value in expected.items()):
        raise ValueError("Selection differs from calibrated series settings")
    if case["scenario"] not in PROFILES or case["preflight"] != PROFILES[case["scenario"]][3]:
        raise ValueError("Selection has unsupported scenario or preflight")
    if case["roots"] < 1 or case["roots"] > MAX_RESOURCES or case["depth"] not in (1, 2, 3, 4):
        raise ValueError("Selection exceeds root/depth safety bounds")
    if case["containment_max"] < 1 or case["containment_max"] > MAX_BREADTH:
        raise ValueError("Selection exceeds breadth safety bound")
    if case["observed_eobjects"] < 1 or case["observed_eobjects"] > MAX_OBJECTS:
        raise ValueError("Selection exceeds EObject safety bound")
    if case["observed_resources"] != case["roots"]:
        raise ValueError("Selection resource count disagrees with roots")
    if any(case[key] != 1 for key in ("attribute_max", "cross_reference_max", "feature_map_max")):
        raise ValueError("Selection changes fixed feature maxima")
    if case["family"] == "bpmn" and case["depth"] not in (1, 2):
        raise ValueError("BPMN depth must be 1 or 2")


def case_for(spec, value):
    series, scenario, family, depth, variable, _ = spec
    return {"series": series, "scenario": scenario, "family": family, "depth": depth,
            "changing_variable": variable, "roots": value if variable == "roots" else 1,
            "attribute_max": 1, "containment_max": value if variable == "breadth" else 1,
            "cross_reference_max": 1, "feature_map_max": 1,
            "preflight": PROFILES[scenario][3]}


def stem(case, mode):
    return (f"{case['series']}-depth{case['depth']}-roots{case['roots']}"
            f"-breadth{case['containment_max']}-{mode}")


def command(case, output, mode, heap, warmups, repetitions):
    profile = PROFILES[case["scenario"]]
    name = stem(case, mode)
    wrapper = str(base.ROOT / "mvnw.cmd") if os.name == "nt" else "./mvnw"
    paths = {"raw": output / f"{name}-raw.csv", "summary": output / f"{name}-summary.csv",
             "diagnostics": output / f"{name}-diagnostics.csv"}
    cmd = [wrapper, "-q", "exec:exec@run-performance-tests", "-Pperformance-tests",
           f"-Dbenchmark.scenario={case['scenario']}", f"-Dbenchmark.metamodel={profile[0]}",
           f"-Dbenchmark.package={profile[1]}", f"-Dbenchmark.root={profile[2]}",
           f"-Dbenchmark.roots={case['roots']}", f"-Dbenchmark.depth={case['depth']}",
           f"-Dbenchmark.attribute.max={case['attribute_max']}",
           f"-Dbenchmark.containment.max={case['containment_max']}",
           f"-Dbenchmark.cross.reference.max={case['cross_reference_max']}",
           f"-Dbenchmark.feature.map.max={case['feature_map_max']}",
           f"-Dbenchmark.preflight={case['preflight']}", f"-Dbenchmark.heap={heap}",
           f"-Dbenchmark.warmups={warmups}", f"-Dbenchmark.repetitions={repetitions}",
           f"-Dbenchmark.raw={paths['raw']}", f"-Dbenchmark.summary={paths['summary']}",
           f"-Dbenchmark.diagnostics={('selection:' + str(paths['diagnostics'])) if mode == 'selection' else paths['diagnostics'] if mode == 'instrumented' else 'off'}"]
    return cmd


def invoke(case, output, mode, heap, warmups, repetitions, timeout):
    name = stem(case, mode)
    item = {**case, "profile": case["scenario"], "mode": mode, "stem": name,
            "status": "running", "started_utc": utc()}
    item["command"] = command(case, output, mode, heap, warmups, repetitions)
    print(f"{name}: starting forked JVM", flush=True)
    start = dt.datetime.now(dt.timezone.utc)
    env = os.environ.copy()
    env.pop("BENCHMARK_SELECTION_VARIANT", None)
    try:
        proc = subprocess.run(item["command"], cwd=base.ROOT, env=env, text=True,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                              timeout=timeout)
        log = proc.stdout or ""
        item["exit_code"] = proc.returncode
        item["status"] = "completed" if proc.returncode == 0 else "failed"
    except subprocess.TimeoutExpired as exc:
        log = exc.stdout or b""
        if isinstance(log, bytes):
            log = log.decode(errors="replace")
        item["status"] = "timeout"
        item["reason"] = f"wall time exceeded {timeout} seconds"
    item["wall_seconds"] = (dt.datetime.now(dt.timezone.utc) - start).total_seconds()
    item["finished_utc"] = utc()
    (output / f"{name}.log").write_text(log)
    if item["status"] == "completed":
        try:
            raw = base.read_csv(output / f"{name}-raw.csv", base.RAW)
            if len(raw) != repetitions or any(row["status"] != "ok" for row in raw):
                raise ValueError("incomplete or unsuccessful raw observations")
            item["observed_eobjects"] = max(int(row["eobjects"]) for row in raw)
            item["observed_resources"] = max(int(row["model_resources"]) for row in raw)
            item["metamodel_sha256"] = raw[0]["metamodel_sha256"]
        except (OSError, ValueError, KeyError) as exc:
            item["status"] = "failed"
            item["reason"] = f"output validation: {exc}"
    elif "reason" not in item:
        raw_path = output / f"{name}-raw.csv"
        reason = "fork exited unsuccessfully; inspect log and raw status"
        if raw_path.exists():
            try:
                rows = base.read_csv(raw_path, base.RAW)
                reason = "; ".join(f"{row['status']}: {row['error']}" for row in rows
                                   if row["status"] != "ok") or reason
                if any(row["status"] == "invalid" for row in rows):
                    item["status"] = "invalid"
            except (OSError, ValueError):
                pass
        if "OutOfMemoryError" in log or "Java heap space" in log:
            item["status"] = "oom"
        item["reason"] = reason
    for suffix in ("raw.csv", "summary.csv", "diagnostics.csv", "log"):
        path = output / f"{name}-{suffix}" if suffix != "log" else output / f"{name}.log"
        if path.exists():
            item.setdefault("sha256", {})[path.name] = file_hash(path)
    print(f"{name}: {item['status']} ({item.get('observed_eobjects', '?')} EObjects); "
          f"log={output / (name + '.log')}", flush=True)
    return item


def projected_over_cap(case, previous, timeout):
    if case["roots"] > MAX_RESOURCES or case["containment_max"] > MAX_BREADTH:
        return "configured root/breadth ceiling"
    if previous:
        last = previous[-1]
        old_size = last["roots"] if case["changing_variable"] == "roots" else last["containment_max"]
        new_size = case["roots"] if case["changing_variable"] == "roots" else case["containment_max"]
        ratio = new_size / old_size
        # Breadth can multiply at two containment levels; favor a conservative projection.
        growth = ratio if case["changing_variable"] == "roots" else ratio * ratio
        if last["observed_eobjects"] * growth > MAX_OBJECTS:
            return f"projected EObject count exceeds {MAX_OBJECTS}"
        if last["wall_seconds"] * growth > timeout:
            return f"projected wall time exceeds {timeout} seconds"
    return None


def choose(successes, limit=7):
    if len(successes) <= limit:
        return successes
    # Use observed output size to spread points and preserve the largest feasible probe.
    ordered = sorted(successes, key=lambda x: x["observed_eobjects"])
    logs = [math.log(max(1, x["observed_eobjects"])) for x in ordered]
    targets = [logs[0] + i * (logs[-1] - logs[0]) / (limit - 1) for i in range(limit)]
    chosen = {0, len(ordered) - 1}
    for target in targets[1:-1]:
        chosen.add(min((i for i in range(len(ordered)) if i not in chosen),
                       key=lambda i: abs(logs[i] - target)))
    return [ordered[i] for i in sorted(chosen)]


def new_output(preset, explicit=None):
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    revision = base.command_output(["git", "rev-parse", "--short", "HEAD"]) or "unknown"
    output = Path(explicit or base.ROOT / "target" / "benchmark" /
                  f"{preset}-{stamp}-{revision}").resolve()
    output.mkdir(parents=True, exist_ok=False)
    return output


def save_latest(preset, output):
    path = base.ROOT / "target" / "benchmark" / f"latest-{preset}.json"
    write_json(path, {"preset": preset, "output": str(output), "updated_utc": utc()})


def latest_output(preset):
    path = base.ROOT / "target" / "benchmark" / f"latest-{preset}.json"
    pointer = json.loads(path.read_text())
    if pointer["preset"] != preset:
        raise ValueError("Latest-result pointer has wrong preset")
    output = Path(pointer["output"]).resolve()
    if not (output / "manifest.json").exists():
        raise ValueError(f"Latest {preset} result is missing: {output}")
    return output


def run_publication(args):
    calibration = args.preset == "large-calibration"
    if calibration and args.selection:
        raise ValueError("--selection is only for large-paper")
    if not calibration and (not args.selection or args.selection_output):
        raise ValueError("large-paper requires --selection and no --selection-output")
    if calibration and not args.selection_output:
        raise ValueError("large-calibration requires --selection-output")
    if not args.heap or not re.fullmatch(r"[1-9][0-9]*[mgMG]", args.heap):
        raise ValueError("--heap must be a positive JVM size such as 2g")
    current = machine(args.heap)
    selection = None
    if not calibration:
        selection = json.loads(Path(args.selection).read_text())
        compatible(selection, current)
    output = new_output(args.preset, args.output)
    manifest = {"schema_version": SCHEMA, "preset": args.preset, "created_utc": utc(),
                **current, "warmups": 1 if calibration else 3,
                "repetitions": 2 if calibration else 7,
                "timeout_seconds_per_invocation": args.timeout,
                "max_parallel_benchmark_processes": 1, "invocations": [],
                "selection_file": str(Path(args.selection or args.selection_output).resolve()),
                "selection_sha256": file_hash(args.selection) if selection else None,
                "safety_caps": {"eobjects": MAX_OBJECTS, "resources": MAX_RESOURCES,
                                "breadth": MAX_BREADTH, "per_jvm_seconds": args.timeout}}
    write_json(output / "manifest.json", manifest)
    save_latest(args.preset, output)
    if selection:
        write_json(output / "selection.json", selection)
    wrapper = str(base.ROOT / "mvnw.cmd") if os.name == "nt" else "./mvnw"
    prep = [wrapper, "-q", "-DskipTests", "test-compile"]
    manifest["preparation_command"] = prep
    write_json(output / "manifest.json", manifest)
    subprocess.run(prep, cwd=base.ROOT, check=True, timeout=args.timeout)
    if calibration:
        run_calibration(args, output, manifest)
    else:
        run_large_paper(args, output, manifest, selection)
    print(f"Result directory: {output}", flush=True)


def run_calibration(args, output, manifest):
    selection = {"schema_version": SCHEMA, "kind": "large-calibration-selection",
                 "created_utc": utc(), **{key: manifest[key] for key in
                                            ("host", "heap", "fixed_jvm_flags",
                                             "metamodel_sha256", "source_sha256")},
                 "calibration_result": str(output), "safety_caps": manifest["safety_caps"],
                 "probes": [], "selected": [], "series_summary": {}, "rationale":
                 "Observed valid probes only; at most seven log-spaced sizes per series."}
    # A test-only cap keeps exact runbook commands executable in an end-to-end bounded check.
    test_limit = int(os.environ.get("EMF_BENCHMARK_TEST_PROBES_PER_SERIES", "0"))
    test_series = os.environ.get("EMF_BENCHMARK_TEST_SERIES")
    if test_limit < 0:
        raise ValueError("test probe limit must be nonnegative")
    selection["test_only"] = bool(test_limit or test_series)
    for spec in SERIES:
        if test_series and spec[0] not in test_series.split(","):
            continue
        successes = []
        for position, value in enumerate(spec[5]):
            case = case_for(spec, value)
            if test_limit and position >= test_limit:
                break
            reason = projected_over_cap(case, successes, args.timeout)
            if reason:
                probe = {**case, "status": "capped", "reason": reason}
                selection["probes"].append(probe)
                break
            item = invoke(case, output, "baseline", args.heap, 1, 2, args.timeout)
            manifest["invocations"].append(item)
            write_json(output / "manifest.json", manifest)
            probe = {**case, "status": item["status"], "reason": item.get("reason", ""),
                     "stem": item["stem"], "wall_seconds": item["wall_seconds"],
                     "observed_eobjects": item.get("observed_eobjects"),
                     "observed_resources": item.get("observed_resources")}
            selection["probes"].append(probe)
            if item["status"] != "completed":
                break
            if item["observed_eobjects"] > MAX_OBJECTS or item["observed_resources"] > MAX_RESOURCES:
                probe["status"] = "over-cap"
                probe["reason"] = "observed output exceeded safety cap"
                break
            successes.append({**case, "observed_eobjects": item["observed_eobjects"],
                              "observed_resources": item["observed_resources"],
                              "wall_seconds": item["wall_seconds"],
                              "calibration_stem": item["stem"]})
        selected = choose(successes)
        selection["selected"].extend(selected)
        selection["series_summary"][spec[0]] = {
            "probes": sum(p["series"] == spec[0] for p in selection["probes"]),
            "successful_probes": len(successes), "selected": len(selected),
            "minimum_eobjects": min((p["observed_eobjects"] for p in selected), default=None),
            "maximum_eobjects": max((p["observed_eobjects"] for p in selected), default=None),
            "limited_by": next((p["reason"] for p in reversed(selection["probes"])
                                if p["series"] == spec[0] and p["status"] != "completed"),
                               "test-only probe cap" if test_limit else "candidate schedule")}
        write_json(args.selection_output, selection)
    write_json(args.selection_output, selection)
    manifest["selection_sha256"] = file_hash(args.selection_output)
    write_json(output / "manifest.json", manifest)
    write_failures(output, manifest, selection["probes"])
    (output / "calibration-report.md").write_text(
        "# Large calibration (not publication measurements)\n\n"
        f"Selected {len(selection['selected'])} feasible configurations from "
        f"{len(selection['probes'])} recorded probes.\n\n"
        "See the selection JSON, per-probe raw CSV/logs, and failure-capped-cases.csv. "
        "The test-only probe cap, if set, limits this to a command-interface check.\n")


def run_large_paper(args, output, manifest, selection):
    manifest["test_only_selection"] = selection.get("test_only", False)
    manifest["planned_forked_jvms"] = len(selection["selected"]) + sum(
        1 for c in selection["selected"] if c["family"] == "many-roots"
        and c["scenario"] in ("extlibrary", "ant", "plugin-eclipse"))
    write_json(output / "manifest.json", manifest)
    for case in selection["selected"]:
        item = invoke(case, output, "baseline", args.heap, 3, 7, args.timeout)
        manifest["invocations"].append(item)
        write_json(output / "manifest.json", manifest)
        if item["status"] == "completed" and case["family"] == "many-roots" and case[
                "scenario"] in ("extlibrary", "ant", "plugin-eclipse"):
            diagnostic = invoke(case, output, "instrumented", args.heap, 3, 7, args.timeout)
            manifest["invocations"].append(diagnostic)
            write_json(output / "manifest.json", manifest)
    report_publication(output)


def write_failures(output, manifest, probes=(), selected=()):
    rows = []
    for case in probes:
        if case["status"] not in ("completed",):
            rows.append({"stage": "calibration", "series": case["series"],
                         "scenario": case["scenario"], "depth": case["depth"],
                         "roots": case["roots"], "containment_max": case["containment_max"],
                         "mode": "baseline", "status": case["status"],
                         "reason": case.get("reason", ""), "stem": case.get("stem", ""),
                         "log_file": case.get("stem", "") + ".log" if case.get("stem") else "",
                         "raw_file": case.get("stem", "") + "-raw.csv" if case.get("stem") else ""})
    for item in manifest["invocations"] if manifest["preset"] == "large-paper" else ():
        if item["status"] != "completed":
            rows.append({"stage": manifest["preset"], "series": item["series"],
                         "scenario": item["scenario"], "depth": item["depth"],
                         "roots": item["roots"], "containment_max": item["containment_max"],
                         "mode": item["mode"], "status": item["status"],
                         "reason": item.get("reason", ""), "stem": item["stem"],
                         "log_file": item["stem"] + ".log",
                         "raw_file": item["stem"] + "-raw.csv" if (output / (item["stem"] + "-raw.csv")).exists() else ""})
    existing = {(item["stem"], item["mode"]) for item in manifest["invocations"]}
    for case in selected:
        for mode in ("baseline", "instrumented") if (case["family"] == "many-roots" and
                      case["scenario"] in ("extlibrary", "ant", "plugin-eclipse")) else ("baseline",):
            name = stem(case, mode)
            if (name, mode) not in existing:
                rows.append({"stage": "large-paper", "series": case["series"],
                             "scenario": case["scenario"], "depth": case["depth"],
                             "roots": case["roots"], "containment_max": case["containment_max"],
                             "mode": mode, "status": "skipped", "reason": "run ended before this fork",
                             "stem": name, "log_file": "", "raw_file": ""})
    base.write_csv(output / "failure-capped-cases.csv", FAIL_COLUMNS, rows)
    return rows


def verified_case(output, item, manifest):
    for filename, expected in item.get("sha256", {}).items():
        if file_hash(output / filename) != expected:
            raise ValueError(f"{filename}: checksum changed")
    raw, diagnostic, stats = base.check_case(output, item, manifest)
    for row in raw:
        for key, expected in (("attribute_max", item["attribute_max"]),
                              ("containment_max", item["containment_max"]),
                              ("cross_reference_max", item["cross_reference_max"]),
                              ("feature_map_max", item["feature_map_max"])):
            if int(row[key]) != expected:
                raise ValueError(f"{item['stem']}: {key} changed")
        if row["preflight"] != item["preflight"]:
            raise ValueError(f"{item['stem']}: preflight changed")
    counts = [int(row["eobjects"]) for row in raw]
    resources = [int(row["model_resources"]) for row in raw]
    result = {"series": item["series"], "scenario": item["scenario"],
              "family": item["family"], "depth": item["depth"],
              "changing_variable": item["changing_variable"],
              "requested_roots": item["roots"], "containment_max": item["containment_max"],
              "eobjects_min": min(counts), "eobjects_median": statistics.median(counts),
              "eobjects_max": max(counts), "resources_min": min(resources),
              "resources_max": max(resources), "samples": len(raw),
              "median_ns": stats["median_ns"], "p25_ns": stats["p25_ns"],
              "p75_ns": stats["p75_ns"], "preflight": item["preflight"],
              "heap": manifest["heap"], "status": "completed",
              "raw_file": item["stem"] + "-raw.csv"}
    if diagnostic is not None:
        result.update({"compute_calls_median": statistics.median(int(x["compute_calls"]) for x in diagnostic),
                       "candidate_entries_median": statistics.median(int(x["candidate_entries"]) for x in diagnostic),
                       "compute_ns_median": statistics.median(int(x["compute_elapsed_ns"]) for x in diagnostic)})
    return result


def svg(output, name, rows, y_key, title):
    width, height = 1200, 540
    left, top, right, bottom = 90, 55, 850, 450
    colors = ["#005f9e", "#a24100", "#367339", "#803a8c", "#bd2639", "#555555",
              "#008c95", "#a57900", "#743f29"]
    groups = sorted({row["series"] for row in rows})
    points = [(float(row["eobjects_median"]), float(row[y_key])) for row in rows]
    xmin = min((x for x, _ in points), default=0.0)
    xmax = max((x for x, _ in points), default=1.0)
    ymax = max((y for _, y in points), default=1.0) * 1.12 or 1.0
    if xmax == xmin:
        xmax = xmin + 1
    xcoord = lambda x: left + (x - xmin) / (xmax - xmin) * (right - left)
    ycoord = lambda y: bottom - y / ymax * (bottom - top)
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
             '<rect width="100%" height="100%" fill="white"/>',
             f'<text x="{left}" y="28" font-size="19" font-family="sans-serif">{html.escape(title)}</text>',
             f'<path d="M{left},{top} V{bottom} H{right}" fill="none" stroke="black"/>',
             f'<text x="{(left+right)//2}" y="510" text-anchor="middle" font-family="sans-serif">Actual generated EObjects</text>',
             '<text transform="translate(20 255) rotate(-90)" text-anchor="middle" font-family="sans-serif">Elapsed time (ms)</text>']
    if not rows:
        parts.append('<text x="470" y="250" text-anchor="middle" font-size="18" '
                     'font-family="sans-serif">No completed observations</text>')
    for tick in range(5):
        x = xmin + (xmax - xmin) * tick / 4
        y = ymax * tick / 4
        parts += [f'<text x="{xcoord(x):.1f}" y="470" text-anchor="middle" font-size="12">{x:,.0f}</text>',
                  f'<text x="78" y="{ycoord(y)+4:.1f}" text-anchor="end" font-size="12">{y/1e6:.1f}</text>']
    for index, group in enumerate(groups):
        color = colors[index % len(colors)]
        subset = sorted((row for row in rows if row["series"] == group),
                        key=lambda row: float(row["eobjects_median"]))
        coords = " ".join(f"{xcoord(float(row['eobjects_median'])):.1f},{ycoord(float(row[y_key])):.1f}" for row in subset)
        if len(subset) > 1:
            parts.append(f'<polyline points="{coords}" fill="none" stroke="{color}" stroke-width="2"/>')
        for row in subset:
            x, y = xcoord(float(row["eobjects_median"])), ycoord(float(row[y_key]))
            if y_key == "median_ns":
                upper = ycoord(float(row["p75_ns"]))
                lower = ycoord(float(row["p25_ns"]))
                parts.append(f'<path d="M{x:.1f},{upper:.1f} V{lower:.1f} M{x-4:.1f},{upper:.1f} H{x+4:.1f} M{x-4:.1f},{lower:.1f} H{x+4:.1f}" fill="none" stroke="{color}"/>')
            tooltip = (f"{group}; roots={row['requested_roots']}; breadth={row['containment_max']}; "
                       f"depth={row['depth']}; resources={row['resources_min']}–{row['resources_max']}; "
                       f"preflight={row['preflight']}; samples={row['samples']}")
            parts.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="4" fill="{color}"><title>{html.escape(tooltip)}</title></circle>')
        parts.append(f'<text x="880" y="{90 + index*28}" font-size="13" fill="{color}" font-family="sans-serif">{html.escape(group)}</text>')
    parts.append("</svg>")
    (output / f"{name}.svg").write_text("\n".join(parts) + "\n")


def report_publication(output):
    output = Path(output).resolve()
    manifest = json.loads((output / "manifest.json").read_text())
    if manifest["preset"] != "large-paper":
        raise ValueError("Combined report requires a large-paper result")
    selection_path = output / "selection.json"
    if file_hash(selection_path) != manifest["selection_sha256"]:
        raise ValueError("Selection checksum changed since measurement")
    selection = json.loads(selection_path.read_text())
    baseline, diagnostics = [], []
    failures = write_failures(output, manifest, selection["probes"], selection["selected"])
    for item in manifest["invocations"]:
        for filename, expected_hash in item.get("sha256", {}).items():
            if file_hash(output / filename) != expected_hash:
                raise ValueError(f"{filename}: checksum changed")
        if item["status"] != "completed":
            continue
        row = verified_case(output, item, manifest)
        (baseline if item["mode"] == "baseline" else diagnostics).append(row)
    baseline_map = {(r["series"], r["requested_roots"], r["containment_max"]): r
                    for r in baseline}
    for row in diagnostics:
        matching = baseline_map.get((row["series"], row["requested_roots"],
                                     row["containment_max"]))
        row["baseline_median_ns"] = matching["median_ns"] if matching else ""
        row["compute_fraction_of_baseline"] = (
            row["compute_ns_median"] / matching["median_ns"] if matching else "")
    families = {"contained": [r for r in baseline if r["family"] == "contained"],
                "many-roots": [r for r in baseline if r["family"] == "many-roots"],
                "bpmn": [r for r in baseline if r["family"] == "bpmn"],
                "diagnostic": diagnostics}
    for family, name in FIGURES.items():
        rows = families[family]
        base.write_csv(output / f"{name}.csv", DIAG_COLUMNS if family == "diagnostic"
                       else FIGURE_COLUMNS, rows)
        svg(output, name, rows, "compute_ns_median" if family == "diagnostic"
            else "median_ns", {"contained": "One-root containment breadth",
                               "many-roots": "Many roots and model resources",
                               "diagnostic": "Instrumented candidate computation",
                               "bpmn": "BPMN by depth (in-memory validation)"}[family])
    expected = {"extlibrary", "bibtex", "ant", "plugin-eclipse", "rss", "bpmn-bounded"}
    publication_failures = [f for f in failures if f["stage"] == "large-paper"]
    lines = ["# Combined publication-scale performance report", "",
             ("**SMOKE/TEST SELECTION — these tiny measurements are not publication evidence.**"
              if selection.get("test_only") else
              "Controlled-host publication selection; interpret completion and limits below."), "",
             f"Status: {'INCOMPLETE' if publication_failures or len(baseline) < len(selection['selected']) else 'complete selected matrix'}; "
             f"{len(baseline)}/{len(selection['selected'])} baseline configurations complete; "
             f"{len(publication_failures)} publication failures; {len(failures) - len(publication_failures)} "
             "failed, timed-out or capped calibration probes.", "",
             "Calibration and publication measurements are distinct. All times below come from "
             "successful baseline measurements only; instrumented diagnostic times are separate.", "",
             f"Host: {manifest['host']['platform']}; CPU: {manifest['host']['cpu_model']}; "
             f"JDK: {manifest['host']['java_vendor']} {manifest['host']['java_version_output']}; "
             f"git: {manifest['host']['git_revision']} (dirty={manifest['host']['git_dirty']}); "
             f"heap: {manifest['heap']}; JVM flags: {manifest['fixed_jvm_flags']}; "
             f"{manifest['warmups']} matched warmups, {manifest['repetitions']} measured repetitions "
             "per completed configuration; serial forked JVMs.", "",
             "## All six metamodels", "",
             "| Scenario | Topology / variable | Preflight | Completed / selected | "
             "Largest measured EObjects | Resources | Median ms (p25–p75) |", "|---|---|---|---:|---:|---:|---:|"]
    for scenario in sorted(expected):
        rows = [r for r in baseline if r["scenario"] == scenario]
        selected = [r for r in selection["selected"] if r["scenario"] == scenario]
        largest = max(rows, key=lambda r: r["eobjects_median"]) if rows else None
        topology = "BPMN depth 1 and 2; roots" if scenario == "bpmn-bounded" else \
                   "contained breadth and/or many roots"
        detail = (f"{largest['eobjects_median']} | {largest['resources_min']}–{largest['resources_max']} | "
                  f"{largest['median_ns']/1e6:.3f} ({largest['p25_ns']/1e6:.3f}–{largest['p75_ns']/1e6:.3f})"
                  if largest else "— | — | —")
        lines.append(f"| {scenario} | {topology} | {PROFILES[scenario][3]} | "
                     f"{len(rows)} / {len(selected)} | {detail} |")
    lines += ["", "## Calibration coverage", "",
              "| Series | Successful probes | Selected points | Observed EObject span | Limit |",
              "|---|---:|---:|---:|---|"]
    for series, summary in selection.get("series_summary", {}).items():
        lines.append(f"| {series} | {summary['successful_probes']} | {summary['selected']} | "
                     f"{summary['minimum_eobjects']}–{summary['maximum_eobjects']} | "
                     f"{summary['limited_by']} |")
    lines += ["", "Fewer than five selected points or a narrow observed EObject span "
              "limits interpretation of that series; no missing sizes are inferred.", "",
              "## Completed configurations (baseline only)", "",
              "| Series | Depth | Roots | Containment max | Actual EObjects min–max | "
              "Resources min–max | Samples | Median ms (p25–p75) | Preflight |",
              "|---|---:|---:|---:|---:|---:|---:|---:|---|"]
    for row in baseline:
        lines.append(f"| {row['series']} | {row['depth']} | {row['requested_roots']} | "
                     f"{row['containment_max']} | {row['eobjects_min']}–{row['eobjects_max']} | "
                     f"{row['resources_min']}–{row['resources_max']} | {row['samples']} | "
                     f"{row['median_ns']/1e6:.3f} "
                     f"({row['p25_ns']/1e6:.3f}–{row['p75_ns']/1e6:.3f}) | "
                     f"{row['preflight']} |")
    lines += ["", "## Figure files", "",
              "Each SVG uses actual generated EObject count on the horizontal axis and elapsed "
              "time on the vertical axis. Point details in the paired CSV include roots, "
              "resources, depth, breadth, preflight, samples and p25/p75. Figure series never "
              "join different depths or containment breadth with many-root scaling.", "",
              "- `figure-contained.svg`: one root, changing containment maximum; BibTeX, RSS, Ant.",
              "- `figure-many-roots.svg`: fixed depth and maxima, changing roots/resources.",
              "- `figure-diagnostic.svg`: candidate `super.compute` time from instrumented runs; "
              "its CSV gives calls, entries and compute time divided by matching *baseline* "
              "median as a descriptive fraction, not an overhead estimate.",
              "- `figure-bpmn.svg`: separate depth-1 and depth-2 BPMN root series, "
              "with in-memory `validate` preflight.", "",
              "## Unsuccessful and capped cases", "",
              f"See `failure-capped-cases.csv` for all {len(failures)} recorded cases. "
              "Raw CSVs and logs remain available. An incomplete series is not treated as "
              "a successful measurement.", ""]
    if failures:
        lines += ["| Stage | Series | Mode | Status | Reason |", "|---|---|---|---|---|"]
        for failure in failures:
            reason = str(failure["reason"]).replace("|", "/").replace("\n", " ")
            lines.append(f"| {failure['stage']} | {failure['series']} | "
                         f"{failure['mode']} | {failure['status']} | {reason} |")
    lines += ["",
              "## Interpretation limits", "",
              "The timer surrounds only one `generateFromSeveral` call. Preflight, loading, "
              "counting, GC metrics collection, serialization, CSV, and SVG writing are outside "
              "it. BibTeX and other one-root breadth sweeps change containment shape; many-root "
              "sweeps increase resources and EObjects together. BPMN is a FeatureMap-capable "
              "recursive metamodel; FeatureMap entry stress is not established by these runs. "
              "Round-trip preflight for five scenarios and in-memory validation for BPMN provide "
              "different guarantees. Generated graph shapes, JIT/GC, heap, host state and "
              "timing noise limit interpretation. No universal complexity exponent or cross-paper "
              "speed comparison follows from these observations.", ""]
    (output / "report.md").write_text("\n".join(lines))
    print(f"Regenerated combined report: {output}", flush=True)
