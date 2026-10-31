#!/usr/bin/env python3
"""Record one uninstrumented PluginEclipse generation call with JFR."""

import argparse
import os
import re
import subprocess

import performance_evaluation as base
import publication_evaluation as pub


def validate(roots, max_eobjects, max_resources, timeout):
    if roots < 1 or max_eobjects < 1 or max_resources < 1 or timeout < 1:
        raise ValueError("roots, caps and timeout must be positive")
    if roots > max_resources or roots * 14 > max_eobjects:
        raise ValueError("projected PluginEclipse output exceeds explicit caps")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--roots", type=int, required=True)
    parser.add_argument("--heap", default="2g")
    parser.add_argument("--timeout", type=int, default=1800)
    parser.add_argument("--max-eobjects", type=int, default=400000)
    parser.add_argument("--max-resources", type=int, default=25000)
    args = parser.parse_args()
    validate(args.roots, args.max_eobjects, args.max_resources, args.timeout)
    if not re.fullmatch(r"[1-9][0-9]*[mgMG]", args.heap):
        parser.error("--heap must be a positive JVM size such as 2g")
    spec = next(spec for spec in pub.SERIES if spec[0] == "plugin-eclipse-roots")
    case = pub.case_for(spec, args.roots)
    output = pub.new_output("crossref-jfr")
    name = pub.stem(case, "baseline")
    recording = output / "plugin-eclipse.jfr"
    command = pub.command(case, output, "baseline", args.heap, 1, 1)
    manifest = {"schema_version": 1, "kind": "crossref-jfr", "created_utc": pub.utc(),
                **pub.machine(args.heap), "case": case, "command": command,
                "caps": {"eobjects": args.max_eobjects,
                         "resources": args.max_resources},
                "timeout_seconds": args.timeout, "recording": recording.name,
                "profile_boundary": "first measured generateFromSeveral call only"}
    pub.write_json(output / "manifest.json", manifest)
    subprocess.run(["./mvnw", "-q", "-DskipTests", "test-compile"], cwd=base.ROOT,
                   check=True, timeout=args.timeout)
    environment = os.environ.copy()
    existing = environment.get("JAVA_TOOL_OPTIONS", "")
    environment["JAVA_TOOL_OPTIONS"] = (existing + " " +
                                        f"-Dbenchmark.jfr.path={recording}").strip()
    try:
        result = subprocess.run(command, cwd=base.ROOT, env=environment,
                                text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, timeout=args.timeout)
        (output / "benchmark.log").write_text(result.stdout)
        manifest["exit_code"] = result.returncode
    except subprocess.TimeoutExpired as exc:
        log = exc.stdout or b""
        (output / "benchmark.log").write_bytes(
            log if isinstance(log, bytes) else log.encode())
        manifest["status"] = "timeout"
        pub.write_json(output / "manifest.json", manifest)
        raise RuntimeError(f"JFR fork exceeded timeout; inspect {output}") from exc
    if result.returncode == 0:
        rows = base.read_csv(output / f"{name}-raw.csv", base.RAW)
        if len(rows) != 1 or rows[0]["status"] != "ok":
            raise RuntimeError(f"JFR benchmark produced no valid measurement; inspect {output}")
        manifest["observed_eobjects"] = int(rows[0]["eobjects"])
        manifest["observed_resources"] = int(rows[0]["model_resources"])
        manifest["cap_exceeded"] = (manifest["observed_eobjects"] > args.max_eobjects or
                                    manifest["observed_resources"] > args.max_resources)
    manifest["recording_exists"] = recording.exists()
    manifest["status"] = ("completed" if result.returncode == 0 and recording.exists()
                          and not manifest.get("cap_exceeded") else "failed")
    pub.write_json(output / "manifest.json", manifest)
    if manifest["status"] != "completed":
        raise RuntimeError(f"JFR run incomplete; inspect {output}")
    print(f"JFR recording: {recording}")
    print(f"Inspect with: jfr view hot-methods {recording}")
    print(f"Result directory: {output}")


if __name__ == "__main__":
    main()
