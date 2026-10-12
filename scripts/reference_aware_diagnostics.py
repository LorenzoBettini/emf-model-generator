#!/usr/bin/env python3
"""Separate instrumented candidate-work probe; its times are not A/B measurements."""

import argparse
import csv
import json
import os
import subprocess

import performance_evaluation as base
import publication_evaluation as pub
import reference_aware_comparison as comparison


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--roots", required=True)
    parser.add_argument("--scenarios", default="plugin-eclipse,ant,extlibrary")
    parser.add_argument("--heap", default="512m")
    parser.add_argument("--timeout", type=int, default=120)
    parser.add_argument("--max-eobjects", type=int, default=1000)
    parser.add_argument("--max-resources", type=int, default=10)
    parser.add_argument("--output")
    args = parser.parse_args()
    args.selection = args.selection_output = None
    args.warmups, args.repetitions = 0, 1
    cases = comparison.parse_cases(args)
    if args.timeout < 1:
        parser.error("--timeout must be positive")
    output = pub.new_output("reference-aware-diagnostics", args.output)
    manifest = {"preset": "reference-aware-diagnostics", "created_utc": pub.utc(),
                **pub.machine(args.heap), "selected_cases": cases, "invocations": [],
                "caps": {"eobjects": args.max_eobjects, "resources": args.max_resources},
                "timeout_seconds_per_invocation": args.timeout,
                "warning": "Instrumented mechanism probe; never use its times as A/B timings"}
    pub.write_json(output / "manifest.json", manifest)
    subprocess.run(["./mvnw", "-q", "-DskipTests", "test-compile"], cwd=base.ROOT,
                   check=True, timeout=args.timeout)
    rows = []
    for case in cases:
        if (case["roots"] > args.max_resources or
                case["roots"] * comparison.FACTORS[case["scenario"]] > args.max_eobjects):
            rows.append({"scenario": case["scenario"], "roots": case["roots"],
                         "status": "projected cap"})
            continue
        pair = []
        for mode in comparison.MODES:
            name = f"{case['scenario']}-roots{case['roots']}-{mode}"
            csv_path = output / f"{name}.csv"
            command = ["./mvnw", "-q", "exec:java",
                       "-Dexec.mainClass=io.github.lorenzobettini.emfmodelgenerator.performance.ReferenceAwareDiagnosticMain",
                       "-Dexec.classpathScope=test",
                       f"-Dexec.args={case['scenario']} {case['roots']} {mode} {csv_path}"]
            env = os.environ.copy()
            env["MAVEN_OPTS"] = f"-Xms{args.heap} -Xmx{args.heap}"
            item = {"scenario": case["scenario"], "roots": case["roots"],
                    "mode": mode, "command": command, "started_utc": pub.utc()}
            try:
                completed = subprocess.run(command, cwd=base.ROOT, env=env, text=True,
                                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                           timeout=args.timeout)
                log = completed.stdout
                item["exit_code"] = completed.returncode
                item["status"] = "completed" if completed.returncode == 0 else "failed"
            except subprocess.TimeoutExpired as exc:
                log = exc.stdout or b""
                if isinstance(log, bytes):
                    log = log.decode(errors="replace")
                item["status"] = "timeout"
            (output / f"{name}.log").write_text(log)
            item["finished_utc"] = pub.utc()
            if item["status"] == "completed":
                with csv_path.open(newline="") as stream:
                    data = list(csv.DictReader(stream))
                if len(data) != 1:
                    item["status"] = "failed"
                    item["reason"] = "missing diagnostic row"
                else:
                    pair.append(data[0])
                    item["sha256"] = pub.file_hash(csv_path)
            manifest["invocations"].append(item)
            pub.write_json(output / "manifest.json", manifest)
        if len(pair) == 2 and all(pair[0][key] == pair[1][key] for key in
                                  ("eobjects", "resources", "fingerprint", "preflight")):
            rows.append({"scenario": case["scenario"], "roots": case["roots"],
                         "status": "equivalent", "baseline_attempts": pair[0]["attempts"],
                         "variant_attempts": pair[1]["attempts"],
                         "baseline_opposite_rejections": pair[0]["opposite_rejections"],
                         "variant_opposite_rejections": pair[1]["opposite_rejections"],
                         "retirements": pair[1]["retirements"],
                         "pool_entries_built": pair[1]["pool_entries_built"],
                         "active_indices": pair[1]["active_indices"],
                         "estimated_pool_bytes": pair[1]["estimated_pool_bytes"],
                         "pool_build_ns": pair[1]["pool_build_ns"],
                         "selection_ns_instrumented": pair[1]["selection_ns"]})
        else:
            rows.append({"scenario": case["scenario"], "roots": case["roots"],
                         "status": "failed or output mismatch"})
    columns = ["scenario", "roots", "status", "baseline_attempts", "variant_attempts",
               "baseline_opposite_rejections", "variant_opposite_rejections",
               "retirements", "pool_entries_built", "active_indices",
               "estimated_pool_bytes", "pool_build_ns", "selection_ns_instrumented"]
    base.write_csv(output / "diagnostic-comparison.csv", columns, rows)
    lines = ["# Separate reference-aware mechanism diagnostics", "",
             "These forked runs are instrumented. Their times are not comparable to the "
             "uninstrumented A/B medians. Estimated pool bytes use a rough 64 bytes per "
             "TreeSet index entry; this is not a heap measurement.", "",
             "| Scenario | Roots | Status | Attempts B/V | Opposite rejects B/V | Retired | Built/active indices |",
             "|---|---:|---|---:|---:|---:|---:|"]
    for row in rows:
        lines.append(f"| {row['scenario']} | {row['roots']} | {row['status']} | "
                     f"{row.get('baseline_attempts', '—')}/{row.get('variant_attempts', '—')} | "
                     f"{row.get('baseline_opposite_rejections', '—')}/"
                     f"{row.get('variant_opposite_rejections', '—')} | "
                     f"{row.get('retirements', '—')} | "
                     f"{row.get('pool_entries_built', '—')}/"
                     f"{row.get('active_indices', '—')} |")
    (output / "report.md").write_text("\n".join(lines) + "\n")
    pub.save_latest("reference-aware-diagnostics", output)
    print(f"Diagnostic result directory: {output}")


if __name__ == "__main__":
    main()
