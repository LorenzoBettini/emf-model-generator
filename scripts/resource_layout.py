"""Forked fixed-EXTLibrary-model resource partition experiment and report."""

import csv
import html
import json
import os
import re
import subprocess
from pathlib import Path

import performance_evaluation as base
import publication_evaluation as pub

RAW = "phase index root_ns setup_ns population_ns total_ns roots eobjects resources fingerprint status error".split()
PREFLIGHT = "roots resources eobjects fingerprint roundtrip_fingerprint roundtrip_equal status".split()
DIAGNOSTIC = "class candidates order_sha256 elapsed_ns".split()
STAGES = ("root_ns", "setup_ns", "population_ns", "total_ns")
SUMMARY = "resources roots eobjects stage samples median_ns p25_ns p75_ns status reason".split()
FIGURE = "resources roots eobjects median_ms p25_ms p75_ms stage status".split()
SOURCES = (
    "scripts/performance_evaluation.py", "scripts/resource_layout.py",
    "src/test/java/io/github/lorenzobettini/emfmodelgenerator/performance/"
    "ResourceLayoutBenchmark.java",
    "src/test/java/io/github/lorenzobettini/emfmodelgenerator/performance/"
    "GraphFingerprint.java", "pom.xml",
)


def parse_cases(args):
    if args.scenario != "extlibrary" or args.selection or args.selection_output:
        raise ValueError("resource-layout requires --scenario extlibrary and no selection file")
    if not args.roots or not re.fullmatch(r"[1-9][0-9]*", args.roots):
        raise ValueError("--roots must be one positive integer")
    if not args.resources or not re.fullmatch(r"[1-9][0-9]*(,[1-9][0-9]*)*", args.resources):
        raise ValueError("--resources requires comma-separated positive integers")
    roots = int(args.roots)
    resources = [int(value) for value in args.resources.split(",")]
    if resources != sorted(set(resources)) or resources[-1] > roots:
        raise ValueError("resources must be unique, ascending, and no greater than roots")
    if (not re.fullmatch(r"[1-9][0-9]*[mgMG]", args.heap)
            or args.max_eobjects < 1 or args.max_resources < 1
            or args.warmups < 0 or args.repetitions < 1 or args.timeout < 1):
        raise ValueError("heap, caps, timeout and repetitions must be positive; warmups nonnegative")
    if roots > args.max_eobjects:
        raise ValueError("root count alone exceeds --max-eobjects")
    return roots, resources


def command(roots, resources, output, args):
    wrapper = str(base.ROOT / "mvnw.cmd") if os.name == "nt" else "./mvnw"
    stem = f"extlibrary-roots{roots}-resources{resources}"
    return [wrapper, "-q", "exec:exec@run-resource-layout", "-Pperformance-tests",
            f"-Dbenchmark.heap={args.heap}", f"-Dbenchmark.roots={roots}",
            f"-Dbenchmark.resources={resources}", f"-Dbenchmark.warmups={args.warmups}",
            f"-Dbenchmark.repetitions={args.repetitions}",
            f"-Dbenchmark.max-eobjects={args.max_eobjects}",
            f"-Dbenchmark.raw={output / (stem + '-raw.csv')}",
            f"-Dbenchmark.preflight={output / (stem + '-preflight.csv')}",
            f"-Dbenchmark.diagnostic={output / (stem + '-diagnostic.csv')}"], stem


def run_layout(args):
    roots, resources = parse_cases(args)
    output = pub.new_output("resource-layout", args.output)
    manifest = {"schema_version": 1, "preset": "resource-layout", "created_utc": pub.utc(),
                **pub.machine(args.heap), "scenario": "extlibrary", "roots": roots,
                "requested_resources": resources, "warmups": args.warmups,
                "repetitions": args.repetitions, "timeout_seconds_per_invocation": args.timeout,
                "caps": {"eobjects": args.max_eobjects, "resources": args.max_resources},
                "max_parallel_benchmark_processes": 1,
                "measurement": "root creation; resource creation and consecutive attachment; "
                               "one public populateEObjects call; sum of stages",
                "preflight": "full graph in-memory validation and XMI byte-stream round trip "
                             "for every layout, before warmups; outside clocks",
                "source_sha256": {path: pub.file_hash(base.ROOT / path) for path in SOURCES},
                "invocations": []}
    pub.write_json(output / "manifest.json", manifest)
    subprocess.run(["./mvnw", "-q", "-DskipTests", "test-compile"], cwd=base.ROOT,
                   check=True, timeout=args.timeout)
    blocked = False
    for count in resources:
        cmd, stem = command(roots, count, output, args)
        item = {"resources": count, "stem": stem, "command": cmd,
                "started_utc": pub.utc(), "status": "running"}
        manifest["invocations"].append(item)
        pub.write_json(output / "manifest.json", manifest)
        if blocked:
            item.update(status="skipped", reason="earlier layout failed or reached a cap")
        elif count > args.max_resources:
            item.update(status="capped", reason="requested resources exceed cap")
        else:
            print(f"{stem}: starting forked JVM", flush=True)
            try:
                result = subprocess.run(cmd, cwd=base.ROOT, stdout=subprocess.PIPE,
                                        stderr=subprocess.STDOUT, text=True,
                                        timeout=args.timeout)
                log = result.stdout or ""
                item["exit_code"] = result.returncode
                item["status"] = "completed" if result.returncode == 0 else "failed"
                if result.returncode:
                    item["reason"] = "fork failed; inspect log and partial CSV"
            except subprocess.TimeoutExpired as exc:
                log = exc.stdout or b""
                if isinstance(log, bytes):
                    log = log.decode(errors="replace")
                item.update(status="timeout", reason=f"wall time exceeded {args.timeout} seconds")
            (output / f"{stem}.log").write_text(log)
            print(f"{stem}: {item['status']}; log={output / (stem + '.log')}", flush=True)
        item["finished_utc"] = pub.utc()
        item["sha256"] = {path.name: pub.file_hash(path) for path in
                          (output.glob(f"{stem}*")) if path.is_file()}
        pub.write_json(output / "manifest.json", manifest)
        if item["status"] != "completed":
            blocked = True
    valid = report_layout(output)
    pub.save_latest("resource-layout", output)
    print(f"Result directory: {output}", flush=True)
    if not valid:
        raise RuntimeError(f"Resource-layout series is NO-GO; inspect {output / 'report.md'}")


def _checked(output, item, manifest):
    stem = item["stem"]
    for name, digest in item.get("sha256", {}).items():
        if pub.file_hash(output / name) != digest:
            raise ValueError(f"{name}: checksum changed")
    raw = base.read_csv(output / f"{stem}-raw.csv", RAW)
    preflight = base.read_csv(output / f"{stem}-preflight.csv", PREFLIGHT)
    diagnostic = base.read_csv(output / f"{stem}-diagnostic.csv", DIAGNOSTIC)
    if len(raw) != manifest["repetitions"] or len(preflight) != 1 or len(diagnostic) != 2:
        raise ValueError(f"{stem}: incomplete rows")
    prior = preflight[0]
    if (prior["status"] != "ok" or prior["roundtrip_equal"] not in ("true", "false")
            or int(prior["roots"]) != manifest["roots"]
            or int(prior["resources"]) != item["resources"]
            or int(prior["eobjects"]) > manifest["caps"]["eobjects"]):
        raise ValueError(f"{stem}: invalid preflight")
    for index, row in enumerate(raw, 1):
        if (row["phase"] != "measurement" or int(row["index"]) != index
                or row["status"] != "ok" or row["fingerprint"] != prior["fingerprint"]
                or any(row[key] != prior[key] for key in ("roots", "resources", "eobjects"))
                or any(int(row[stage]) <= 0 for stage in STAGES)
                or int(row["total_ns"]) != sum(int(row[stage]) for stage in STAGES[:3])):
            raise ValueError(f"{stem}: invalid raw row {index}")
    if [row["class"] for row in diagnostic] != ["Book", "Writer"]:
        raise ValueError(f"{stem}: diagnostic classes changed")
    for row in diagnostic:
        if (int(row["candidates"]) < 0 or int(row["elapsed_ns"]) <= 0
                or not re.fullmatch(r"[0-9a-f]{64}", row["order_sha256"])):
            raise ValueError(f"{stem}: invalid candidate diagnostic")
    return raw, prior, diagnostic


def _svg(rows, stage):
    values = [row for row in rows if row["stage"] == stage and row["status"] == "ok"]
    if not values:
        return ""
    high = max(float(row["p75_ms"]) for row in values) or 1
    points = []
    for i, row in enumerate(values):
        x = 90 + i * (650 / max(1, len(values) - 1))
        y = 320 - 260 * float(row["median_ms"]) / high
        points.append((x, y, row))
    path = " ".join(f"{x:.1f},{y:.1f}" for x, y, _ in points)
    labels = "".join(f'<text x="{x:.1f}" y="350" text-anchor="middle">'
                     f'{html.escape(str(row["resources"]))}</text>' for x, _, row in points)
    circles = "".join(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="5" fill="#1769aa"/>'
                      for x, y, _ in points)
    intervals = "".join(
        f'<path d="M{x:.1f} {320 - 260 * float(row["p25_ms"]) / high:.1f} '
        f'V{320 - 260 * float(row["p75_ms"]) / high:.1f}" stroke="#1769aa"/>'
        for x, _, row in points)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="820" height="390" '
            f'viewBox="0 0 820 390"><title>{stage} vs resources</title>'
            '<rect width="820" height="390" fill="white"/>'
            '<path d="M90 45 V320 H740" fill="none" stroke="black"/>'
            f'<polyline points="{path}" fill="none" stroke="#1769aa" stroke-width="2"/>'
            f'{intervals}{circles}{labels}<text x="410" y="380" text-anchor="middle">'
            'Model resources</text><text x="20" y="30">Milliseconds</text></svg>')


def report_layout(output):
    output = Path(output).resolve()
    manifest = json.loads((output / "manifest.json").read_text())
    if manifest["preset"] != "resource-layout":
        raise ValueError("Wrong preset")
    summary, figures, candidate_rows, failures, fingerprints = [], [], [], [], set()
    counts = set()
    reloaded_fingerprints = set()
    roundtrip_mismatches = []
    if [item["resources"] for item in manifest["invocations"]] != manifest["requested_resources"]:
        failures.append(("all", "incomplete", "requested layouts missing from manifest"))
    for item in manifest["invocations"]:
        resources = item["resources"]
        if item["status"] != "completed":
            failures.append((resources, item["status"], item.get("reason", "")))
            for stage in STAGES:
                summary.append(dict(resources=resources, roots=manifest["roots"], eobjects="",
                                    stage=stage, samples=0, median_ns="", p25_ns="",
                                    p75_ns="", status=item["status"],
                                    reason=item.get("reason", "")))
            continue
        try:
            raw, prior, diagnostic = _checked(output, item, manifest)
            fingerprints.add(prior["fingerprint"])
            reloaded_fingerprints.add(prior["roundtrip_fingerprint"])
            if prior["roundtrip_equal"] != "true":
                roundtrip_mismatches.append(resources)
            counts.add(int(prior["eobjects"]))
            for candidate in diagnostic:
                candidate_rows.append({"resources": resources, **candidate})
            for stage in STAGES:
                values = [int(row[stage]) for row in raw]
                values_stats = [base.quantile(values, fraction) for fraction in (.5, .25, .75)]
                summary.append(dict(zip(SUMMARY,
                                        (resources, manifest["roots"], prior["eobjects"],
                                         stage, len(raw), *values_stats, "ok", ""))))
                figures.append(dict(resources=resources, roots=manifest["roots"],
                                    eobjects=prior["eobjects"],
                                    median_ms=values_stats[0] / 1e6,
                                    p25_ms=values_stats[1] / 1e6,
                                    p75_ms=values_stats[2] / 1e6, stage=stage, status="ok"))
        except (OSError, KeyError, ValueError) as exc:
            failures.append((resources, "invalid", str(exc)))
            for stage in STAGES:
                summary.append(dict(resources=resources, roots=manifest["roots"], eobjects="",
                                    stage=stage, samples=0, median_ns="", p25_ns="",
                                    p75_ns="", status="invalid", reason=str(exc)))
    if len(fingerprints) > 1 or len(counts) > 1 or len(reloaded_fingerprints) > 1:
        failures.append(("all", "invalid", "logical fingerprint or EObject count differs"))
    for name in ("Book", "Writer"):
        matches = {(row["candidates"], row["order_sha256"]) for row in candidate_rows
                   if row["class"] == name}
        if len(matches) > 1:
            failures.append(("all", "invalid", f"{name} candidate count/order differs"))
    base.write_csv(output / "summary.csv", SUMMARY, summary)
    base.write_csv(output / "figure-stages.csv", FIGURE, figures)
    base.write_csv(output / "candidate-discovery.csv",
                   ["resources", *DIAGNOSTIC], candidate_rows)
    base.write_csv(output / "failures.csv", ["resources", "status", "reason"],
                   [dict(zip(("resources", "status", "reason"), failure))
                    for failure in failures])
    for stage in ("population_ns", "setup_ns"):
        (output / f"figure-{stage}.svg").write_text(_svg(figures, stage))
    direct = [dict(resources=row["resources"], roots=manifest["roots"],
                   eobjects=next(iter(counts)) if len(counts) == 1 else "",
                   median_ms=int(row["elapsed_ns"]) / 1e6,
                   p25_ms=int(row["elapsed_ns"]) / 1e6,
                   p75_ms=int(row["elapsed_ns"]) / 1e6,
                   stage="direct_" + row["class"], status="ok")
              for row in candidate_rows]
    for name in ("Book", "Writer"):
        (output / f"figure-direct-{name}.svg").write_text(
            _svg(direct, "direct_" + name))
    lines = ["# Fixed logical EXTLibrary model / ResourceSet layout", "",
             f"Status: **{'NO-GO' if failures else 'GO'}**. Roots: {manifest['roots']}; "
             f"actual EObjects: {next(iter(counts)) if len(counts) == 1 else 'inconsistent'}.",
             f"Layouts requested: {manifest['requested_resources']}; "
             f"completed: {len(manifest['invocations'])}.",
             f"Heap: {manifest['heap']}; warmups: {manifest['warmups']}; "
             f"measured repetitions: {manifest['repetitions']}; one serial fork per layout.",
             f"JVM flags: {manifest.get('fixed_jvm_flags')}; the child log records actual arguments.",
             f"Git: {manifest['host']['git_revision']}; dirty: {manifest['host']['git_dirty']}; "
             f"host: {manifest['host']['platform']}; CPU: {manifest['host']['cpu_model']}.",
             f"JDK vendor: {manifest['host']['java_vendor']}; "
             f"version: {manifest['host']['java_version_output']}.",
             f"Fixture SHA-256: {manifest['metamodel_sha256']['extlibrary']}.", "",
             "The model is populated by one public populateEObjects call after attaching "
             "unpopulated roots in consecutive blocks. Total is the sum of root creation, "
             "resource creation/attachment and population; it is not facade generate() time. "
             "Each layout has a full-size validation and XMI byte-stream round trip outside "
             "timing. The canonical graph includes attributes, containment, FeatureMaps and "
             "exact reference target traversal indices. Only physical resource layout is omitted.",
             "Round-trip reloaded graphs also agree across layouts. "
             + ("The reloaded graph differs from the in-memory graph for layouts "
                f"{roundtrip_mismatches}: EXTLibrary Writer.name is transient/volatile and "
                "XMI omits its generated value. Both full hashes are retained in each "
                "preflight CSV; no semantic value was normalized away. "
                if roundtrip_mismatches else "Every round-trip hash equals its in-memory hash. "),
             "", "## Stage timings", "",
             "| Resources | Stage | Samples | Median ms | p25–p75 ms | Status |",
             "|---:|---|---:|---:|---:|---|"]
    for row in summary:
        if row["status"] == "ok":
            lines.append(f"| {row['resources']} | {row['stage']} | {row['samples']} | "
                         f"{int(row['median_ns']) / 1e6:.3f} | "
                         f"{int(row['p25_ns']) / 1e6:.3f}–{int(row['p75_ns']) / 1e6:.3f} | ok |")
        else:
            lines.append(f"| {row['resources']} | {row['stage']} | 0 | — | — | "
                         f"{row['status']} |")
    lines += ["", "See `figure-stages.csv`, `figure-population_ns.svg` and "
              "`figure-setup_ns.svg` for population and setup versus resource count. "
              "Candidate discovery is a separate direct EMFUtils scan diagnostic in "
              "`candidate-discovery.csv`, with one observation per class/layout and "
              "separate `figure-direct-Book.svg` and `figure-direct-Writer.svg` plots; "
              "its time is excluded from all stage clocks. "
              "Selector cache-miss counts are unavailable in this uninstrumented run. "
              "A cold scan is not repeated for every reference assignment because candidate "
              "lists are cached by EClass. Fixed-size points cannot establish linear or "
              "quadratic complexity. Short development smokes are not publication timings.",
              "", "## Failures and caps", ""]
    lines += [f"- Resources {resources}: {status}: {reason}" for resources, status, reason in failures]
    if not failures:
        lines.append("None. In-memory graphs, counts, candidate order, XMI validation and "
                     "cross-layout reloaded graphs match. The in-memory/XMI transient-attribute "
                     "difference is disclosed above.")
    lines.append("")
    (output / "report.md").write_text("\n".join(lines))
    return not failures
