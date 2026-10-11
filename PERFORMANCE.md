# Reproducing the performance evaluation

The benchmark is a test-source CLI that generates from **one selected concrete EClass**.
One observation creates the requested number of roots with a fresh generator. Its timer
covers only `generateFromSeveral`, including model resource creation and population.
Ecore loading, preflight validation, EObject/resource counting, XMI round trip, console
output, CSV writing, and cleanup are outside that interval. Start lines are flushed
before each execution; completion lines show measured values and status afterwards.
Normal `./mvnw verify` does not run benchmark series.

## Commands

Run from the repository root with JDK 21 and Python 3.12 or later:

```sh
python3 scripts/performance_evaluation.py run --preset smoke
python3 scripts/performance_evaluation.py run --preset pilot
python3 scripts/performance_evaluation.py run --preset paper --heap 512m --timeout 120
python3 scripts/performance_evaluation.py run --preset bpmn-pilot
python3 scripts/performance_evaluation.py run --preset bpmn-paper --heap 512m --timeout 120
```

Use `python` instead of `python3` on Windows. `smoke` launches two forked JVMs for
EXTLibrary baseline and instrumented observations (one root, depth 1, one matched
warmup and two measurements each). `pilot` launches 25 baseline JVMs: five root
counts each for EXTLibrary, BibTeX, Ant, PluginEclipse, and RSS, with one warmup and
two measurements. `paper` is explicitly opt-in: the same 25 baseline points plus
10 separate instrumented EXTLibrary/Ant points, each with three matched warmups and
seven measurements. These are initial bounded configurations; inspect the pilot
and recalibrate before any larger physical-host experiment. `bpmn-pilot` and
`bpmn-paper` are separate stress supplements: each forks eight baseline JVMs
for `bpmn-bounded`, sweeping roots 1, 2, 3, 5 at depths 1 and 2. The pilot
uses one warmup and two measurements; the controlled-host command uses three
warmups and seven measurements. This keeps BPMN out of the primary five-profile
comparison and makes its depth sensitivity visible without treating its small,
recursive outputs as equivalent to the other profiles. No preset runs through
`verify`.

Each profile/size/mode runs **serially in a separate forked Java process** through
`exec:exec@run-profile-pilot`. Maven first compiles test sources without running
tests. The child JVM uses equal initial and maximum heap (`512m` by default),
`-XX:+UseG1GC`, and `-Dfile.encoding=UTF-8`. The same profile, size, depth,
multiplicities, preflight, heap and JVM flags apply to all repetitions in that
process. The profile sets all four multiplicity maxima to one. Preflight uses an
XMI round trip except the optional BPMN profile, which uses in-memory validation.
The `--timeout` is a wall-clock cap for each Maven invocation, including the fork.
The driver does not request GC between observations. Avoid coverage or mutation
instrumentation during timed experiments.

Results go to a new directory under `target/benchmark/`, named with the preset,
UTC timestamp and short git revision. Use `--output /path/to/new-directory` to
choose a new empty location. Existing paths are rejected, preventing accidental
overwrite. Each configuration has a descriptive basename containing profile,
depth, roots and baseline/instrumented mode. It receives `-raw.csv`,
`-summary.csv`, an optional `-diagnostics.csv`, and a `.log`. `manifest.json`
records the exact commands, exits/timeouts, platform, CPU model, logical cores,
physical RAM when available, JDK vendor/version, git revision and dirty state,
heap, fixed JVM flags, run counts, and one-at-a-time process policy. Raw CSV also
records the selected Ecore SHA-256, Java/OS/JVM details, actual EObject and model
resource counts, configuration, run index, status and unrounded elapsed ns.
Do not edit or discard failed rows or logs. Failed/incomplete series retain their
files and cause reporting to stop until inspected.

Validate and regenerate a report from existing results:

```sh
python3 scripts/performance_evaluation.py report target/benchmark/<result-directory>
```

The report command checks the exact raw, summary and diagnostic CSV schemas,
row counts, run identity, status, preflight/warmup progress, per-measurement
console counts and nanoseconds, diagnostic counters, and median/p25/p75 values.
It then writes `report.md`, `figure-scaling.csv`, `figure-diagnostic.csv` when
instrumented data exist, and a compact scaling table in the report. Summary
quartiles use the same linear interpolation and nanosecond rounding as the Java
harness. The raw CSVs remain the authoritative per-run observations.

## Controlled measurement protocol

Run the `paper` command on one documented physical host with a fixed JDK 21
installation, fixed heap, stable power/performance policy and minimal competing
load. Record those policies, CPU throttling/thermal conditions if known, and any
container or VM limits alongside `manifest.json`. Use one process at a time and
keep its console behavior identical across compared cases. Repeat the whole
series on another day/session to assess session variation before making paper
claims. A GitHub-hosted runner or an interactive developer workstation can run
smoke/pilot checks, but its absolute times are not controlled-host evidence.
The GitHub Actions `performance.yml` workflow defaults to `smoke` on Linux,
macOS and Windows. Its manually selected `pilot` runs on the same hosted OS
matrix. Neither
CI option is a paper measurement. The Eclipse launch file is a small validated
EXTLibrary smoke invocation and writes fixed `eclipse-smoke-*` paths; the script
is the reproducible, collision-resistant series launcher.

## Paper output and limits

`report.md` gives at most two figure specifications and a compact table:

1. Plot baseline median generation milliseconds (p25–p75 intervals) against
   **actual median EObject count** from `figure-scaling.csv`, one series per
   metamodel and depth, with root count labels. Check the EObjects min/max at
   each point. BPMN depth curves belong in its separate stress panel/report.
2. Plot instrumented candidate `super.compute` milliseconds against actual
   EObject count from `figure-diagnostic.csv`. Annotate median cache-miss calls
   and returned candidate entries. Do not mix instrumented generation timings
   with baseline timings.

These figures address Reviewer 2's scaling question with observed output size
and a measured candidate-discovery diagnostic; they do not prove an asymptotic
complexity or identify a single cause. Candidate lists are cached per target
EClass, so whole-ResourceSet scans occur on cache misses, not on every root.
Resource and EObject counts co-vary in the root-count series, and allocation,
reference population, JIT, GC and platform effects remain alternatives.
The BibTeX, Ant, PluginEclipse and RSS profiles address the metamodel-diversity
request with distinct containment/reference topologies; EXTLibrary preserves
continuity with the earlier case. AADL/BPEL/WSDL are not claimed as performance
profiles. PluginEclipse depth 3 failed round-trip validity in calibration and
is excluded; depth 4 is used. Generated models pass the stated EMF preflight,
which does not establish full domain-level semantic correctness. A short pilot
cannot justify a universal growth exponent, cross-host ranking, or manuscript
claim. See the [benchmark harness notes](src/test/java/io/github/lorenzobettini/emfmodelgenerator/performance/README.md)
for profile bounds, schema details and earlier calibration counts.
