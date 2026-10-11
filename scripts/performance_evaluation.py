#!/usr/bin/env python3
"""Run bounded benchmark series and validate/report their unmodified CSV observations."""

import argparse
import ctypes
import csv
import datetime as dt
import hashlib
import json
import os
import platform
import re
import statistics
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PROFILES = {
    "extlibrary": (1, (1, 2, 5, 10, 20)),
    "bibtex": (2, (1, 2, 5, 10, 20)),
    "ant": (2, (1, 2, 5, 10, 20)),
    "plugin-eclipse": (4, (1, 2, 5, 10, 20)),
    "rss": (3, (1, 2, 5, 10, 20)),
}
BPMN_DEPTHS = (1, 2)
BPMN_ROOTS = (1, 2, 3, 5)
RAW = "run_id started_utc measurement_mode scenario metamodel metamodel_sha256 package root_class requested_roots depth attribute_max containment_max cross_reference_max feature_map_max warmups repetitions preflight run_index elapsed_ns actual_roots eobjects model_resources status error java_version java_vm jvm_args os_name os_arch cpu_count max_heap_bytes".split()
SUMMARY = "run_id measurement_mode scenario successful_samples median_ns p25_ns p75_ns status".split()
DIAGNOSTIC = "run_id scenario run_index requested_roots depth actual_roots eobjects model_resources generation_elapsed_ns compute_calls candidate_entries compute_elapsed_ns status error".split()
FIXED_FLAGS = ["-XX:+UseG1GC", "-Dfile.encoding=UTF-8"]


def command_output(command):
    try:
        return subprocess.check_output(command, cwd=ROOT, text=True,
                                       stderr=subprocess.STDOUT).strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def host_metadata():
    cpu = platform.processor() or None
    if sys.platform.startswith("linux"):
        info = Path("/proc/cpuinfo")
        if info.exists():
            match = re.search(r"^model name\s*:\s*(.+)$", info.read_text(), re.M)
            cpu = match.group(1) if match else cpu
    elif sys.platform == "darwin":
        cpu = command_output(["sysctl", "-n", "machdep.cpu.brand_string"]) or cpu
    ram = None
    if hasattr(os, "sysconf"):
        try:
            ram = os.sysconf("SC_PHYS_PAGES") * os.sysconf("SC_PAGE_SIZE")
        except (OSError, ValueError):
            pass
    if ram is None and sys.platform == "darwin":
        value = command_output(["sysctl", "-n", "hw.memsize"])
        ram = int(value) if value and value.isdigit() else None
    if ram is None and os.name == "nt":
        class MemoryStatus(ctypes.Structure):
            _fields_ = [("length", ctypes.c_ulong), ("load", ctypes.c_ulong),
                        ("total_physical", ctypes.c_ulonglong),
                        ("available_physical", ctypes.c_ulonglong),
                        ("total_page", ctypes.c_ulonglong),
                        ("available_page", ctypes.c_ulonglong),
                        ("total_virtual", ctypes.c_ulonglong),
                        ("available_virtual", ctypes.c_ulonglong),
                        ("available_extended", ctypes.c_ulonglong)]
        status = MemoryStatus()
        status.length = ctypes.sizeof(status)
        if ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
            ram = status.total_physical
    java_settings = command_output(["java", "-XshowSettings:properties", "-version"]) or ""
    vendor = re.search(r"^\s*java.vendor\s*=\s*(.+)$", java_settings, re.M)
    return {
        "platform": platform.platform(), "cpu_model": cpu,
        "logical_cores": os.cpu_count(), "ram_bytes": ram,
        "java_vendor": vendor.group(1) if vendor else None,
        "java_version_output": java_settings.splitlines()[-3:],
        "git_revision": command_output(["git", "rev-parse", "HEAD"]),
        "git_dirty": bool(command_output(["git", "status", "--porcelain"])),
    }


def invocation_list(preset):
    if preset == "smoke":
        return [("extlibrary", 1, 1, mode) for mode in ("baseline", "instrumented")]
    if preset in ("bpmn-pilot", "bpmn-paper"):
        return [("bpmn-bounded", depth, roots, "baseline")
                for depth in BPMN_DEPTHS for roots in BPMN_ROOTS]
    profiles = list(PROFILES.items())
    result = [(name, depth, roots, "baseline")
              for name, (depth, sizes) in profiles for roots in sizes]
    if preset == "paper":
        result += [(name, PROFILES[name][0], roots, "instrumented")
                   for name in ("extlibrary", "ant") for roots in PROFILES[name][1]]
    return result


def run(args):
    warmups, repetitions = {"smoke": (1, 2), "pilot": (1, 2),
                            "paper": (3, 7), "bpmn-pilot": (1, 2),
                            "bpmn-paper": (3, 7)}[args.preset]
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    revision = (command_output(["git", "rev-parse", "--short", "HEAD"]) or "unknown")
    output = Path(args.output or ROOT / "target" / "benchmark" / f"{args.preset}-{stamp}-{revision}")
    output = output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    cases = invocation_list(args.preset)
    manifest = {
        "schema_version": 1, "preset": args.preset,
        "created_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "host": host_metadata(), "heap": args.heap, "fixed_jvm_flags": FIXED_FLAGS,
        "warmups": warmups, "repetitions": repetitions,
        "max_parallel_benchmark_processes": 1, "planned_forked_jvms": len(cases),
        "timeout_seconds_per_invocation": args.timeout, "invocations": [],
    }
    manifest_path = output / "manifest.json"

    def save_manifest():
        manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")

    save_manifest()
    wrapper = str(ROOT / "mvnw.cmd") if os.name == "nt" else "./mvnw"
    subprocess.run([wrapper, "-q", "-DskipTests", "test-compile"], cwd=ROOT,
                   check=True, timeout=args.timeout)
    for profile, depth, roots, mode in cases:
        stem = f"{profile}-depth{depth}-roots{roots}-{mode}"
        item = {"profile": profile, "depth": depth, "roots": roots,
                "mode": mode, "stem": stem, "status": "running"}
        manifest["invocations"].append(item)
        save_manifest()
        raw = output / f"{stem}-raw.csv"
        summary = output / f"{stem}-summary.csv"
        diagnostic = output / f"{stem}-diagnostics.csv"
        cmd = [wrapper, "-q", "exec:exec@run-profile-pilot", "-Pperformance-tests",
               f"-Dbenchmark.profile={profile}", f"-Dbenchmark.depth={depth}",
               f"-Dbenchmark.roots={roots}", f"-Dbenchmark.heap={args.heap}",
               f"-Dbenchmark.warmups={warmups}",
               f"-Dbenchmark.repetitions={repetitions}",
               f"-Dbenchmark.raw={raw}", f"-Dbenchmark.summary={summary}",
               f"-Dbenchmark.diagnostics={diagnostic if mode == 'instrumented' else 'off'}"]
        item["command"] = cmd
        item["started_utc"] = dt.datetime.now(dt.timezone.utc).isoformat()
        print(f"{stem}: starting forked JVM", flush=True)
        try:
            completed = subprocess.run(cmd, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, timeout=args.timeout)
            log = completed.stdout
            item["exit_code"] = completed.returncode
            item["status"] = "completed" if completed.returncode == 0 else "failed"
        except subprocess.TimeoutExpired as exc:
            log = (exc.stdout or b"").decode(errors="replace") if isinstance(exc.stdout, bytes) else (exc.stdout or "")
            item["status"] = "timeout"
        (output / f"{stem}.log").write_text(log)
        item["finished_utc"] = dt.datetime.now(dt.timezone.utc).isoformat()
        save_manifest()
        print(log, end="" if log.endswith("\n") else "\n", flush=True)
        if item["status"] != "completed":
            raise RuntimeError(f"{stem}: {item['status']}; raw data and log retained in {output}")
    report(output)
    print(f"Validated results: {output}")


def read_csv(path, columns):
    with path.open(newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != columns:
            raise ValueError(f"{path}: unexpected columns {reader.fieldnames}")
        rows = list(reader)
    if not rows:
        raise ValueError(f"{path}: no rows")
    return rows


def quantile(values, fraction):
    values = sorted(values)
    position = fraction * (len(values) - 1)
    lower = int(position)
    upper = min(lower + 1, len(values) - 1)
    # Java Math.round for nonnegative elapsed nanoseconds.
    return int(values[lower] + (values[upper] - values[lower]) *
               (position - lower) + 0.5)


def check_case(output, item, manifest):
    stem = item["stem"]
    raw = read_csv(output / f"{stem}-raw.csv", RAW)
    summary = read_csv(output / f"{stem}-summary.csv", SUMMARY)
    if len(summary) != 1:
        raise ValueError(f"{stem}: expected one summary row")
    diagnostic = (read_csv(output / f"{stem}-diagnostics.csv", DIAGNOSTIC)
                  if item["mode"] == "instrumented" else None)
    log = (output / f"{stem}.log").read_text()
    expected = manifest["repetitions"]
    if len(raw) != expected or (diagnostic is not None and len(diagnostic) != expected):
        raise ValueError(f"{stem}: incomplete measured rows")
    ids = {row["run_id"] for row in raw}
    if len(ids) != 1 or summary[0]["run_id"] not in ids:
        raise ValueError(f"{stem}: inconsistent run ID")
    times = []
    fingerprints = set()
    for index, row in enumerate(raw, 1):
        if (row["status"] != "ok" or row["measurement_mode"] != item["mode"]
                or row["scenario"] != item["profile"]
                or int(row["run_index"]) != index
                or int(row["requested_roots"]) != item["roots"]
                or int(row["depth"]) != item["depth"]
                or int(row["warmups"]) != manifest["warmups"]
                or int(row["repetitions"]) != expected
                or int(row["actual_roots"]) != item["roots"]
                or int(row["model_resources"]) != item["roots"]
                or int(row["eobjects"]) <= 0
                or not re.fullmatch(r"[0-9a-f]{64}", row["metamodel_sha256"])):
            raise ValueError(f"{stem}: invalid raw row {index}")
        nanos = int(row["elapsed_ns"])
        fingerprints.add(row["metamodel_sha256"])
        for flag in (f"-Xms{manifest['heap']}", f"-Xmx{manifest['heap']}",
                     *manifest["fixed_jvm_flags"]):
            if flag not in row["jvm_args"].split():
                raise ValueError(f"{stem}: child JVM did not use {flag}")
        if nanos <= 0:
            raise ValueError(f"{stem}: invalid elapsed time at row {index}")
        times.append(nanos)
        phase = f"measurement {index}/{expected}"
        start = f"{phase} start requested_roots={item['roots']} depth={item['depth']}"
        done = (f"{phase} done status=ok actual_roots={row['actual_roots']} "
                f"eobjects={row['eobjects']} model_resources={row['model_resources']} "
                f"elapsed_ns={nanos}")
        if start not in log or done not in log:
            raise ValueError(f"{stem}: console/CSV disagreement at row {index}")
        if diagnostic is not None:
            diag = diagnostic[index - 1]
            for key in ("actual_roots", "eobjects", "model_resources", "status"):
                if diag[key] != row[key]:
                    raise ValueError(f"{stem}: diagnostic {key} mismatch")
            if (diag["run_id"] != row["run_id"] or diag["run_index"] != row["run_index"]
                    or diag["generation_elapsed_ns"] != row["elapsed_ns"]):
                raise ValueError(f"{stem}: diagnostic identity/time mismatch")
            done += (f" compute_calls={diag['compute_calls']} "
                     f"candidate_entries={diag['candidate_entries']} "
                     f"compute_elapsed_ns={diag['compute_elapsed_ns']}")
            if done not in log:
                raise ValueError(f"{stem}: console diagnostic mismatch at row {index}")
    for phase in ("preflight", *(f"warmup {i}/{manifest['warmups']}"
                                  for i in range(1, manifest["warmups"] + 1))):
        if f"{phase} start" not in log or f"{phase} done status=ok" not in log:
            raise ValueError(f"{stem}: missing successful {phase} progress")
    if len(fingerprints) != 1 or next(iter(fingerprints)) != hashlib.sha256(
            (ROOT / raw[0]["metamodel"]).read_bytes()).hexdigest():
        raise ValueError(f"{stem}: Ecore fingerprint disagreement")
    stats = {"median_ns": quantile(times, 0.5), "p25_ns": quantile(times, 0.25),
             "p75_ns": quantile(times, 0.75)}
    summary_line = (f"summary samples={expected} median_ns={stats['median_ns']} "
                    f"p25_ns={stats['p25_ns']} p75_ns={stats['p75_ns']}")
    if (summary[0]["status"] != "ok" or summary[0]["measurement_mode"] != item["mode"]
            or int(summary[0]["successful_samples"]) != expected
            or any(int(summary[0][key]) != value for key, value in stats.items())
            or summary_line not in log):
        raise ValueError(f"{stem}: summary/console disagreement")
    return raw, diagnostic, stats


def write_csv(path, columns, rows):
    with path.open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=columns)
        writer.writeheader()
        writer.writerows(rows)


def report(output):
    output = Path(output).resolve()
    manifest = json.loads((output / "manifest.json").read_text())
    cases = manifest["invocations"]
    if not cases or any(item["status"] != "completed" for item in cases):
        raise ValueError("Incomplete series; retained raw rows and logs need inspection")
    scaling, diagnostics, table = [], [], []
    for item in cases:
        raw, diagnostic, stats = check_case(output, item, manifest)
        counts = [int(row["eobjects"]) for row in raw]
        resources = [int(row["model_resources"]) for row in raw]
        record = {
            "scenario": item["profile"], "depth": item["depth"],
            "requested_roots": item["roots"], "eobjects_min": min(counts),
            "eobjects_median": statistics.median(counts), "eobjects_max": max(counts),
            "model_resources_min": min(resources), "model_resources_max": max(resources),
            "samples": len(raw), "median_ms": stats["median_ns"] / 1e6,
            "p25_ms": stats["p25_ns"] / 1e6, "p75_ms": stats["p75_ns"] / 1e6,
            "raw_file": f"{item['stem']}-raw.csv",
        }
        if item["mode"] == "baseline":
            scaling.append(record)
            group = (item["profile"], item["depth"])
            if group not in {(entry["scenario"], entry["depth"]) for entry in table}:
                table.append(record)
            else:
                table[next(i for i, entry in enumerate(table)
                           if (entry["scenario"], entry["depth"]) == group)] = record
        else:
            diagnostics.append({
                "scenario": item["profile"], "depth": item["depth"],
                "requested_roots": item["roots"], "eobjects_median": statistics.median(counts),
                "compute_calls_median": statistics.median(int(d["compute_calls"]) for d in diagnostic),
                "candidate_entries_median": statistics.median(int(d["candidate_entries"]) for d in diagnostic),
                "compute_ms_median": statistics.median(int(d["compute_elapsed_ns"]) for d in diagnostic) / 1e6,
                "diagnostic_file": f"{item['stem']}-diagnostics.csv",
            })
    if scaling:
        write_csv(output / "figure-scaling.csv", list(scaling[0]), scaling)
    if diagnostics:
        write_csv(output / "figure-diagnostic.csv", list(diagnostics[0]), diagnostics)
    lines = ["# Performance evaluation", "", f"Preset: `{manifest['preset']}`.",
             f"Git revision: `{manifest['host']['git_revision']}`; dirty: `{manifest['host']['git_dirty']}`.",
             f"Host: {manifest['host']['platform']}; CPU: {manifest['host']['cpu_model']}; "
             f"logical cores: {manifest['host']['logical_cores']}; RAM bytes: {manifest['host']['ram_bytes']}.",
             f"JDK vendor: {manifest['host']['java_vendor']}; "
             f"version: {manifest['host']['java_version_output'][0] if manifest['host']['java_version_output'] else 'unknown'}; "
             f"heap: {manifest['heap']}; "
             f"JVM flags: `{', '.join(manifest['fixed_jvm_flags'])}`.",
             f"One forked JVM per configuration, serially ({len(cases)} total); "
             f"{manifest['warmups']} matched warmups and {manifest['repetitions']} measured repetitions.",
             "Each named profile uses its locked multiplicities of 1 and required preflight "
             "(XMI round trip except BPMN, which uses in-memory validation).", "",
             "## Scaling table (baseline only)", "",
             "| Scenario | Depth | Roots | Actual EObjects min–max | Model resources min–max | "
             "Median ms | p25–p75 ms |", "|---|---:|---:|---:|---:|---:|---:|"]
    for row in table:
        lines.append(f"| {row['scenario']} | {row['depth']} | {row['requested_roots']} | "
                     f"{row['eobjects_min']}–{row['eobjects_max']} | "
                     f"{row['model_resources_min']}–{row['model_resources_max']} | "
                     f"{row['median_ms']:.6f} | {row['p25_ms']:.6f}–{row['p75_ms']:.6f} |")
    lines += ["", "Figure 1 specification: plot `figure-scaling.csv` with "
              "`eobjects_median` on x and `median_ms` on y; one connected series per "
              "scenario and depth, with p25–p75 vertical intervals. Label each point with its "
              "requested root count. Use actual EObjects, not roots, as the x axis."]
    if diagnostics:
        lines += ["", "Figure 2 specification: plot `figure-diagnostic.csv` with "
                  "`eobjects_median` on x and `compute_ms_median` on y, one series per "
                  "scenario; annotate `compute_calls_median` and "
                  "`candidate_entries_median`. These instrumented runs are separate "
                  "from baseline generation timing."]
    scaling_mapping = (
        "(a) The two BPMN depth series show observed time against actual output size. "
        "Changing depth also changes the generated shape and EObject count, so the "
        "timing difference cannot be attributed to depth alone."
        if manifest["preset"].startswith("bpmn-") else
        "(a) The scaling table and Figure 1 show observed generation time against "
        "actual output size. " +
        ("Figure 2 shows measured cache-miss computation and its cost. "
         if diagnostics else "A separate instrumented series is needed for Figure 2. ") +
        "These observations cannot assign a single cause to a "
        "superlinear-looking trend because resources and EObjects co-vary."
    )
    lines += ["", "## Reviewer 2 mapping", "", scaling_mapping,
              ("(b) BPMN is a separate recursive/FeatureMap stress supplement at "
               "depths 1–2 with in-memory validation; it is not part of the "
               "round-trip-validated primary comparison."
               if manifest["preset"].startswith("bpmn-") else
               "(b) The independently sourced BibTeX, Ant, PluginEclipse and RSS profiles "
               "cover distinct containment and reference structures; EXTLibrary is a "
               "continuity case. Compare only matched configurations and preflight modes."),
              "", "## Limits", "",
              "These are implementation, platform, heap and scenario dependent observations. "
              "Preflight checks EMF validity, not full semantic correctness. Short smoke/pilot "
              "runs are not paper measurements. Console output is outside the timed call but "
              "may affect scheduling, JIT and GC. No universal growth exponent or cross-host "
              "absolute performance ranking follows. Failed/incomplete cases remain in the "
              "manifest and raw/log files and must be reported, not silently discarded.", ""]
    (output / "report.md").write_text("\n".join(lines))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    launch = sub.add_parser("run")
    launch.add_argument("--preset", choices=("smoke", "pilot", "paper",
                                             "bpmn-pilot", "bpmn-paper",
                                             "large-calibration", "large-paper"), required=True)
    launch.add_argument("--output", help="new, nonexisting result directory")
    launch.add_argument("--heap", default="512m")
    launch.add_argument("--timeout", type=int, default=120,
                        help="wall seconds per Maven invocation, including fork")
    launch.add_argument("--selection-output", help="calibration selection JSON path")
    launch.add_argument("--selection", help="compatible calibration selection JSON path")
    analyze = sub.add_parser("report")
    analyze.add_argument("output", nargs="?", help="existing result directory")
    analyze.add_argument("--latest", choices=("large-paper",),
                         help="regenerate the latest publication result")
    args = parser.parse_args()
    if args.action == "run" and args.timeout <= 0:
        parser.error("--timeout must be positive")
    if args.action == "run":
        if args.preset.startswith("large-"):
            from publication_evaluation import run_publication
            run_publication(args)
        else:
            if args.selection or args.selection_output:
                parser.error("selection options require a large preset")
            run(args)
    else:
        if args.latest:
            if args.output:
                parser.error("report accepts a path or --latest, not both")
            from publication_evaluation import latest_output, report_publication
            report_publication(latest_output(args.latest))
        elif args.output:
            manifest = json.loads((Path(args.output) / "manifest.json").read_text())
            if manifest["preset"] == "large-paper":
                from publication_evaluation import report_publication
                report_publication(args.output)
            else:
                report(args.output)
        else:
            parser.error("report requires a result path or --latest")


if __name__ == "__main__":
    main()
