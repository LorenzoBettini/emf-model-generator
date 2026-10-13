"""Run and report current-default versus opt-in reference-aware comparisons."""

import json
import os
import re
import subprocess
from pathlib import Path

import performance_evaluation as base
import publication_evaluation as pub

MODES = ("baseline", "reference-aware")
FACTORS = {"plugin-eclipse": 15, "ant": 10, "extlibrary": 5}
SERIES = {"plugin-eclipse": "plugin-eclipse-roots", "ant": "ant-roots",
          "extlibrary": "extlibrary-roots"}
FINGERPRINT_COLUMNS = ["phase", "run_index", "sha256"]
TABLE_COLUMNS = ["scenario", "roots", "status", "reason", "eobjects", "resources",
                 "baseline_samples", "variant_samples", "baseline_median_ns",
                 "baseline_p25_ns", "baseline_p75_ns", "variant_median_ns",
                 "variant_p25_ns", "variant_p75_ns", "speedup_baseline_over_variant"]


def parse_cases(args):
    if args.selection or args.selection_output:
        raise ValueError("crossref-comparison uses explicit --roots, not a selection")
    if not args.roots or not re.fullmatch(r"[1-9][0-9]*(,[1-9][0-9]*)*", args.roots):
        raise ValueError("--roots requires ascending, comma-separated positive integers")
    roots = [int(value) for value in args.roots.split(",")]
    if roots != sorted(set(roots)):
        raise ValueError("--roots must be unique and ascending")
    scenarios = args.scenarios.split(",")
    if len(scenarios) != len(set(scenarios)) or any(s not in SERIES for s in scenarios):
        raise ValueError("--scenarios supports unique plugin-eclipse,ant,extlibrary names")
    if (not re.fullmatch(r"[1-9][0-9]*[mgMG]", args.heap) or
            args.max_eobjects < 1 or args.max_resources < 1 or
            args.warmups < 0 or args.repetitions < 1):
        raise ValueError("positive heap/caps, nonnegative warmups and positive repetitions required")
    cases = []
    for scenario in scenarios:
        spec = next(spec for spec in pub.SERIES if spec[0] == SERIES[scenario])
        cases.extend(pub.case_for(spec, roots_count) for roots_count in roots)
    return cases


def comparison_command(case, output, mode, heap, warmups, repetitions):
    command = pub.command(case, output, "baseline", heap, warmups, repetitions)
    old = pub.stem(case, "baseline")
    new = pub.stem(case, mode)
    return [argument.replace(old, new) for argument in command]


def invoke(case, output, mode, args):
    name = pub.stem(case, mode)
    item = {**case, "profile": case["scenario"], "mode": mode, "stem": name, "status": "running",
            "started_utc": pub.utc(), "command": comparison_command(
                case, output, mode, args.heap, args.warmups, args.repetitions)}
    env = os.environ.copy()
    env.pop("BENCHMARK_SELECTION_VARIANT", None)
    if mode == "reference-aware":
        env["BENCHMARK_SELECTION_VARIANT"] = "reference-aware"
    env["BENCHMARK_FINGERPRINTS"] = str(output / f"{name}-fingerprints.csv")
    item["selector_environment"] = {"BENCHMARK_SELECTION_VARIANT":
                                    env.get("BENCHMARK_SELECTION_VARIANT", ""),
                                    "BENCHMARK_FINGERPRINTS": env["BENCHMARK_FINGERPRINTS"]}
    print(f"{name}: starting forked JVM", flush=True)
    try:
        proc = subprocess.run(item["command"], cwd=base.ROOT, env=env, text=True,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                              timeout=args.timeout)
        log = proc.stdout or ""
        item["exit_code"] = proc.returncode
        item["status"] = "completed" if proc.returncode == 0 else "failed"
    except subprocess.TimeoutExpired as exc:
        log = exc.stdout or b""
        if isinstance(log, bytes):
            log = log.decode(errors="replace")
        item["status"] = "timeout"
        item["reason"] = f"wall time exceeded {args.timeout} seconds"
    item["finished_utc"] = pub.utc()
    (output / f"{name}.log").write_text(log)
    if item["status"] == "completed":
        try:
            rows = base.read_csv(output / f"{name}-raw.csv", base.RAW)
            if len(rows) != args.repetitions or any(row["status"] != "ok" for row in rows):
                raise ValueError("incomplete or unsuccessful raw observations")
            item["observed_eobjects"] = max(int(row["eobjects"]) for row in rows)
            item["observed_resources"] = max(int(row["model_resources"]) for row in rows)
            if (item["observed_eobjects"] > args.max_eobjects or
                    item["observed_resources"] > args.max_resources):
                item["status"] = "cap-exceeded"
                item["reason"] = "observed EObject/resource cap exceeded"
        except (OSError, ValueError, KeyError) as exc:
            item["status"] = "failed"
            item["reason"] = f"output validation: {exc}"
    elif "reason" not in item:
        item["reason"] = "fork failed; inspect log and partial raw CSV"
        if "OutOfMemoryError" in log or "Java heap space" in log:
            item["status"] = "oom"
    for path in (output / f"{name}-{suffix}" for suffix in
                 ("raw.csv", "summary.csv", "fingerprints.csv")):
        if path.exists():
            item.setdefault("sha256", {})[path.name] = pub.file_hash(path)
    item.setdefault("sha256", {})[f"{name}.log"] = pub.file_hash(output / f"{name}.log")
    print(f"{name}: {item['status']}; log={output / (name + '.log')}", flush=True)
    return item


def run_comparison(args):
    cases = parse_cases(args)
    output = pub.new_output(args.preset, args.output)
    current = pub.machine(args.heap)
    manifest = {"schema_version": 1, "preset": args.preset,
                "created_utc": pub.utc(), **current,
                "comparison_source_sha256": pub.file_hash(__file__),
                "selector_source_sha256": pub.file_hash(base.ROOT / "src/main/java/io/github/lorenzobettini/emfmodelgenerator/EMFRoundRobinReferenceAwareEObjectCandidateSelector.java"),
                "selected_cases": cases, "skipped_cases": [], "invocations": [],
                "warmups": args.warmups, "repetitions": args.repetitions,
                "timeout_seconds_per_invocation": args.timeout,
                "caps": {"eobjects": args.max_eobjects,
                         "resources": args.max_resources},
                "max_parallel_benchmark_processes": 1}
    pub.write_json(output / "manifest.json", manifest)
    subprocess.run(["./mvnw", "-q", "-DskipTests", "test-compile"], cwd=base.ROOT,
                   check=True, timeout=args.timeout)
    blocked = set()
    for index, case in enumerate(cases):
        scenario = case["scenario"]
        reason = None
        if scenario in blocked:
            reason = "larger root count skipped after failed or capped pair"
        elif case["roots"] > args.max_resources or case["roots"] * FACTORS[scenario] > args.max_eobjects:
            reason = "projected EObject/resource cap"
        if reason:
            manifest["skipped_cases"].append({"scenario": scenario,
                                              "roots": case["roots"],
                                              "status": "skipped", "reason": reason})
            blocked.add(scenario)
            pub.write_json(output / "manifest.json", manifest)
            continue
        modes = MODES if index % 2 == 0 else tuple(reversed(MODES))
        for mode in modes:
            item = invoke(case, output, mode, args)
            manifest["invocations"].append(item)
            pub.write_json(output / "manifest.json", manifest)
        if any(item["status"] != "completed" for item in manifest["invocations"][-2:]):
            blocked.add(scenario)
    report_comparison(output)
    pub.save_latest(args.preset, output)
    print(f"Result directory: {output}", flush=True)


def checked(output, item, manifest):
    for filename, expected in item.get("sha256", {}).items():
        if pub.file_hash(output / filename) != expected:
            raise ValueError(f"{filename}: checksum changed")
    raw = base.read_csv(output / f"{item['stem']}-raw.csv", base.RAW)
    summary = base.read_csv(output / f"{item['stem']}-summary.csv", base.SUMMARY)
    log = (output / f"{item['stem']}.log").read_text()
    if len(raw) != manifest["repetitions"] or len(summary) != 1:
        raise ValueError(f"{item['stem']}: incomplete measured observations")
    times = []
    ids = {row["run_id"] for row in raw}
    if len(ids) != 1 or summary[0]["run_id"] not in ids:
        raise ValueError(f"{item['stem']}: inconsistent run ID")
    for index, row in enumerate(raw, 1):
        if (row["status"] != "ok" or row["preflight"] != "roundtrip" or
                row["measurement_mode"] != item["mode"] or
                row["scenario"] != item["scenario"] or
                int(row["run_index"]) != index or
                int(row["requested_roots"]) != item["roots"] or
                int(row["actual_roots"]) != item["roots"] or
                int(row["depth"]) != item["depth"] or
                int(row["warmups"]) != manifest["warmups"] or
                int(row["repetitions"]) != manifest["repetitions"] or
                int(row["eobjects"]) < 1 or
                int(row["model_resources"]) != item["roots"] or
                row["metamodel_sha256"] != manifest["metamodel_sha256"][item["scenario"]] or
                any(int(row[key]) != item[key] for key in
                    ("attribute_max", "containment_max", "cross_reference_max",
                     "feature_map_max"))):
            raise ValueError(f"{item['stem']}: invalid raw row {index}")
        for flag in (f"-Xms{manifest['heap']}", f"-Xmx{manifest['heap']}",
                     *manifest["fixed_jvm_flags"]):
            if flag not in row["jvm_args"].split():
                raise ValueError(f"{item['stem']}: child JVM missing {flag}")
        nanos = int(row["elapsed_ns"])
        if nanos <= 0:
            raise ValueError(f"{item['stem']}: nonpositive elapsed time")
        times.append(nanos)
        if (f"measurement {index}/{len(raw)} start requested_roots={item['roots']}" not in log or
                f"measurement {index}/{len(raw)} done status=ok "
                f"actual_roots={row['actual_roots']} eobjects={row['eobjects']} "
                f"model_resources={row['model_resources']} elapsed_ns={nanos}" not in log):
            raise ValueError(f"{item['stem']}: console/raw disagreement")
    for phase in ("preflight", *(f"warmup {index}/{manifest['warmups']}"
                                  for index in range(1, manifest["warmups"] + 1))):
        if f"{phase} start" not in log or f"{phase} done status=ok" not in log:
            raise ValueError(f"{item['stem']}: missing successful {phase}")
    stats = {"median_ns": base.quantile(times, 0.5),
             "p25_ns": base.quantile(times, 0.25),
             "p75_ns": base.quantile(times, 0.75)}
    if (summary[0]["status"] != "ok" or summary[0]["measurement_mode"] != item["mode"] or
            summary[0]["scenario"] != item["scenario"] or
            int(summary[0]["successful_samples"]) != len(raw) or
            any(int(summary[0][key]) != value for key, value in stats.items())):
        raise ValueError(f"{item['stem']}: raw/summary disagreement")
    fingerprints = base.read_csv(output / f"{item['stem']}-fingerprints.csv",
                                 FINGERPRINT_COLUMNS)
    if (len(fingerprints) != len(raw) + 1 or fingerprints[0]["phase"] != "preflight" or
            fingerprints[0]["run_index"] != "0" or
            not re.fullmatch(r"[0-9a-f]{64}", fingerprints[0]["sha256"]) or
            any(row["phase"] != "measurement" or int(row["run_index"]) != index or
                not re.fullmatch(r"[0-9a-f]{64}", row["sha256"])
                for index, row in enumerate(fingerprints[1:], 1))):
        raise ValueError(f"{item['stem']}: missing or malformed graph fingerprints")
    if len({row["sha256"] for row in fingerprints}) != 1:
        raise ValueError(f"{item['stem']}: output changed between preflight and measurements")
    counts = {(int(row["eobjects"]), int(row["model_resources"])) for row in raw}
    if len(counts) != 1:
        raise ValueError(f"{item['stem']}: output counts vary across samples")
    return raw, stats, fingerprints, next(iter(counts))


def report_comparison(output):
    output = Path(output).resolve()
    manifest = json.loads((output / "manifest.json").read_text())
    if manifest["preset"] != "reference-aware-comparison":
        raise ValueError("Wrong report preset")
    records = []
    for case in manifest["selected_cases"]:
        matches = {item["mode"]: item for item in manifest["invocations"]
                   if item["scenario"] == case["scenario"] and item["roots"] == case["roots"]}
        skipped = next((item for item in manifest["skipped_cases"]
                        if item["scenario"] == case["scenario"] and
                        item["roots"] == case["roots"]), None)
        row = dict.fromkeys(TABLE_COLUMNS, "")
        row.update(scenario=case["scenario"], roots=case["roots"])
        if skipped:
            row.update(status=skipped["status"], reason=skipped["reason"])
        elif set(matches) != set(MODES) or any(item["status"] != "completed"
                                             for item in matches.values()):
            row.update(status="unmatched", reason="; ".join(
                f"{mode}: {matches[mode]['status']} {matches[mode].get('reason', '')}"
                if mode in matches else f"{mode}: not run" for mode in MODES))
        else:
            baseline = checked(output, matches["baseline"], manifest)
            variant = checked(output, matches["reference-aware"], manifest)
            equivalent = (baseline[3] == variant[3] and
                          [r["sha256"] for r in baseline[2]] ==
                          [r["sha256"] for r in variant[2]])
            row.update(status="equivalent" if equivalent else "not semantically equivalent",
                       reason="" if equivalent else "graph signature or counts differ",
                       eobjects=baseline[3][0], resources=baseline[3][1],
                       baseline_samples=len(baseline[0]), variant_samples=len(variant[0]))
            for prefix, result in (("baseline", baseline), ("variant", variant)):
                row.update({f"{prefix}_{key}": value for key, value in result[1].items()})
            if equivalent:
                row["speedup_baseline_over_variant"] = (
                    baseline[1]["median_ns"] / variant[1]["median_ns"])
        records.append(row)
    base.write_csv(output / "comparison-cases.csv", TABLE_COLUMNS, records)
    base.write_csv(output / "figure-comparison.csv", TABLE_COLUMNS, records)
    failures = [row for row in records if row["status"] != "equivalent"]
    base.write_csv(output / "failure-capped-cases.csv", TABLE_COLUMNS, failures)
    lines = ["# Reference-aware monotonic selector comparison", "",
             "Baseline uses the current production default. Variant uses the opt-in reference-aware "
             "monotonic selector. Both serial JVM forks use the same compiled classes, Ecores, "
             "settings, JDK, heap and host. Timed calls contain only generation. Structural "
             "fingerprints and XMI round-trip validation run outside the timer.", "",
             f"Revision: `{manifest['host']['git_revision']}`; dirty: "
             f"`{manifest['host']['git_dirty']}`; selector SHA-256: "
             f"`{manifest['selector_source_sha256']}`.",
             f"Host: {manifest['host']['platform']}; CPU: {manifest['host']['cpu_model']}; "
             f"JDK: {manifest['host']['java_version_output']}; heap: {manifest['heap']}; "
             f"flags: {manifest['fixed_jvm_flags']}.",
             f"Serial forks; {manifest['warmups']} warmups and {manifest['repetitions']} "
             "measured repetitions per completed alternative. Pair order alternates by case.",
             "", "| Scenario | Roots | Status | EObjects | Resources | Samples B/V | "
             "Median B/V ms | p25–p75 B/V ms | Speedup B/V |",
             "|---|---:|---|---:|---:|---:|---:|---|---:|"]
    for row in records:
        if row["baseline_median_ns"]:
            ms = lambda key: f"{int(row[key])/1e6:.3f}"
            median = f"{ms('baseline_median_ns')} / {ms('variant_median_ns')}"
            spread = (f"{ms('baseline_p25_ns')}–{ms('baseline_p75_ns')} / "
                      f"{ms('variant_p25_ns')}–{ms('variant_p75_ns')}")
        else:
            median = spread = "—"
        speedup = (f"{row['speedup_baseline_over_variant']:.3f}×"
                   if row["speedup_baseline_over_variant"] else "—")
        lines.append(f"| {row['scenario']} | {row['roots']} | {row['status']} | "
                     f"{row['eobjects'] or '—'} | {row['resources'] or '—'} | "
                     f"{row['baseline_samples'] or '—'}/{row['variant_samples'] or '—'} | "
                     f"{median} | {spread} | {speedup} |")
    lines += ["", "`comparison-cases.csv` and `figure-comparison.csv` retain the exact "
              "summary numbers; per-fork raw CSVs retain every elapsed nanosecond, count, "
              "and failure. `failure-capped-cases.csv` lists all unmatched and capped cases. "
              "The manifest stores commands, source/Ecore hashes, statuses and checksums. "
              "Speedup is shown only when every measured graph signature matches.", "",
              "## Interpretation", "", "This is an opt-in monotonic-capacity experiment. Timing alone cannot establish "
              "reduced candidate work; separate diagnostics are required. Results are workload "
              "and host dependent and do not authorize default adoption.", ""]
    (output / "report.md").write_text("\n".join(lines))
    # SVG is a directly shareable vector overview; the full values are in the CSV.
    width, height = 900, 80 + 30 * len(records)
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}">',
           '<style>text{font:13px sans-serif} .bar{fill:#3973b7}</style>',
           '<text x="12" y="25">Median baseline / variant (equivalent cases only)</text>']
    for index, row in enumerate(records):
        y = 55 + 30 * index
        label = f"{row['scenario']} {row['roots']}"
        svg.append(f'<text x="12" y="{y}">{label}</text>')
        if row["speedup_baseline_over_variant"]:
            ratio = min(float(row["speedup_baseline_over_variant"]), 4)
            svg.append(f'<rect class="bar" x="250" y="{y-12}" width="{ratio*100:.1f}" height="16"/>')
            svg.append(f'<text x="{260+ratio*100:.1f}" y="{y}">'
                       f'{row["speedup_baseline_over_variant"]:.3f}×</text>')
        else:
            svg.append(f'<text x="250" y="{y}">{row["status"]}</text>')
    svg.append('</svg>')
    (output / "figure-comparison.svg").write_text("\n".join(svg) + "\n")
    print(f"Regenerated comparison report: {output}", flush=True)
