#!/usr/bin/env python3
"""Regenerate archived reference-aware mechanism reports without running a probe.

The live diagnostic command was retired when production selection counters were
removed. Archived observations remain available at their recorded revisions.
"""

import argparse
import csv
import json
from pathlib import Path

import publication_evaluation as pub


def report(output):
    output = Path(output).resolve()
    manifest = json.loads((output / "manifest.json").read_text())
    if manifest["preset"] != "reference-aware-diagnostics":
        raise ValueError("Not a reference-aware diagnostics archive")
    with (output / "diagnostic-comparison.csv").open(newline="") as stream:
        rows = list(csv.DictReader(stream))
    lines = ["# Separate reference-aware mechanism diagnostics", "",
             "These archived forked runs were instrumented. Their times are not comparable "
             "to uninstrumented A/B medians. Estimated pool bytes use a rough 64 bytes per "
             "TreeSet index entry; this is not a heap measurement.", "",
             "| Scenario | Roots | Status | Attempts B/V | Opposite rejects B/V | "
             "Retired | Built/active indices |",
             "|---|---:|---|---:|---:|---:|---:|"]
    for row in rows:
        lines.append(f"| {row['scenario']} | {row['roots']} | {row['status']} | "
                     f"{row.get('baseline_attempts') or '—'}/"
                     f"{row.get('variant_attempts') or '—'} | "
                     f"{row.get('baseline_opposite_rejections') or '—'}/"
                     f"{row.get('variant_opposite_rejections') or '—'} | "
                     f"{row.get('retirements') or '—'} | "
                     f"{row.get('pool_entries_built') or '—'}/"
                     f"{row.get('active_indices') or '—'} |")
    (output / "report.md").write_text("\n".join(lines) + "\n")
    return output / "report.md"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("report",))
    location = parser.add_mutually_exclusive_group(required=True)
    location.add_argument("--latest", action="store_true")
    location.add_argument("--output", type=Path)
    args = parser.parse_args()
    output = pub.latest_output("reference-aware-diagnostics") if args.latest else args.output
    print(f"Archived diagnostic report: {report(output)}")


if __name__ == "__main__":
    main()
