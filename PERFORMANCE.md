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
seven measurements. These are bounded configurations; inspect the pilot
and calibrate before larger measurements. `bpmn-pilot` and
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

These figures show observed output size and candidate-discovery cost. They do
not prove an asymptotic complexity or identify a single cause. Candidate lists
are cached per target EClass, so whole-ResourceSet scans occur on cache misses, not on every root.
Resource and EObject counts co-vary in the root-count series, and allocation,
reference population, JIT, GC and platform effects remain alternatives.
The profiles cover distinct containment and reference topologies. Generated models
pass the stated EMF preflight, which does not establish full domain-level
semantic correctness. A short pilot cannot justify a universal growth exponent
or cross-host ranking.

## Calibrated pilot profiles

`ScenarioProfiles` defines these locked configurations. `--profile ID` selects the
exact Ecore file, package, concrete root EClass, and multiplicities. The loader
registers the packages and uses their dynamic EMF factories; no custom setter,
factory, auxiliary root, or source-Ecore edit is used. All profiles set attribute,
containment, cross-reference, and FeatureMap maxima to **1**. The normal generator
selection behavior is retained. Root counts and depths outside the listed values
are rejected by the profile CLI. Named profiles require preflight before timing.

All paths below are relative to the core repository root. `external/` abbreviates
`src/test/resources/external-metamodels/`; EXTLibrary uses
`src/test/resources/inputs/extlibrary.ecore`.

| Profile | Ecore, package, root EClass | Topology | Depths (scaling depth bold) | Pilot roots | Preflight |
|---|---|---|---|---|---|
| `extlibrary` | `inputs/extlibrary.ecore`, `extlibrary::Library` | Library containment baseline | **1**, 2 | 1, 2, 5, 10, 20 | XMI round trip |
| `bibtex` | `external/BibTeX.ecore`, `BIBTEX::Bibtex` | Shallow polymorphic entries and fields | 1, **2** | 1, 2, 5, 10, 20 | XMI round trip |
| `ant` | `external/Ant.ecore`, `Ant::Project` | Targets and tasks; required default reference and containment opposites | **2** | 1, 2, 5, 10, 20 | XMI round trip |
| `plugin-eclipse` | `external/PluginEclipse.ecore`, `PluginEclipse::Eclipse` | Bundles, versions, dependencies, and cross references | **4** | 1, 2, 5, 10, 20 | XMI round trip |
| `rss` | `external/RSS-2.0.ecore`, `RSS::RSS` | Channel, items, optional structures, and opposites | 2, **3** | 1, 2, 5, 10, 20 | XMI round trip |
| `bpmn-bounded` | `external/BPMN.ecore`, `bpmn::BpmnDiagram` | Recursive graph and FeatureMap structures; stress supplement | **1**, 2 | 1, 2, 3, 5 | In-memory validation |

The selected package namespace URIs, respectively, are
`http:///org/eclipse/emf/examples/library/extlibrary.ecore/1.0.0`,
`http://example.org/atlantic-zoo/bibtex`,
`http://example.org/atlantic-zoo/ant`,
`http://example.org/atlantic-zoo/plugineclipse`,
`http://example.org/atlantic-zoo/rss`, and
`http://stp.eclipse.org/bpmn`. Multi-package Ecores are loaded in full;
the selected root package is named explicitly. In particular,
PluginEclipse also registers `PluginsEclipse`, which contributes polymorphic
types. No profile overrides a package factory.

## Calibrated publication-scale workflow

Run the first three commands from the core repository root on a quiet physical host
with JDK 21+, Python 3, sufficient disk space, and a stable power/CPU policy.
Calibration and publication measurements are lengthy and explicitly opt-in.
The commands use a fixed 2 GiB heap, G1GC and UTF-8 in serial forked JVMs.

```sh
./mvnw -q -DskipTests test-compile
python3 scripts/performance_evaluation.py run --preset large-calibration --heap 2g --timeout 900 --selection-output target/benchmark/publication-selection.json
python3 scripts/performance_evaluation.py run --preset large-paper --heap 2g --timeout 900 --selection target/benchmark/publication-selection.json
python3 scripts/performance_evaluation.py report --latest large-paper
```

The fourth command is optional and only regenerates the report. Recalibrate
after source or environment changes. Publication forks clear any inherited
`BENCHMARK_SELECTION_VARIANT` and use the ordinary production selector.

Calibration probes progressively larger one-root containment breadth and fixed-shape
many-root workloads. It records every success, failure, timeout and capped case in
`target/benchmark/publication-selection.json` and in a unique
`target/benchmark/large-calibration-*/` directory. BibTeX breadth maxima
32, 100, 512, 707 and 1024 are candidates subject to observed object, time,
resource and heap limits, not guaranteed successes. The selection records the
host, JDK, git revision/dirty state, source and Ecore hashes, heap, actual EObject/resource
counts, every probe and the selected cases. `large-paper` rejects incompatible
selections and uses three matched warmups plus seven measured repetitions per
configuration. Its unique `target/benchmark/large-paper-*/` directory is printed
and saved in `target/benchmark/latest-large-paper.json`; the report command
uses that pointer without rerunning generation. A copied `selection.json` makes
the publication directory self-contained for report regeneration.

The combined `report.md` indexes EXTLibrary, BibTeX, Ant, PluginEclipse, RSS,
and BPMN. SVG and matching CSV pairs are `figure-contained`,
`figure-many-roots`, `figure-diagnostic`, and `figure-bpmn`. Their horizontal
axis is actual generated EObject count; CSVs also contain observed resources,
depth, roots, breadth, median/p25/p75 and sample count. The compact six-scenario
table and `failure-capped-cases.csv` make incomplete or unsupported cases visible.
Per-configuration raw/summary/diagnostic CSVs and logs remain in the result
directory. Share the result-directory archive and the calibration selection for
paper analysis. Calibration data are not publication timings.

One-root breadth curves and many-root/resource curves answer different scaling
questions. BPMN depths 1 and 2 are separate series, with in-memory `validate`
preflight; the other profiles use XMI `roundtrip`. BPMN is FeatureMap-capable,
but these measurements do not establish that generated FeatureMap entries were
stressed. Candidate diagnostics are separate instrumented runs and are compared
descriptively with matching baseline medians. Failed cases are never aggregated.
The generated graph shape, host, heap, JIT/GC and co-varying EObject/resource
counts limit interpretation; the report makes no universal complexity claim.

## Small smoke run

From the core repository root:

```sh
./mvnw test-compile exec:exec@run-performance-tests -Pperformance-tests
```

This explicitly opted-in profile starts a **forked `java` process** with
`-Xms512m -Xmx512m`, one EXTLibrary `Library` root at depth 1, one warmup,
and two measured repetitions. The output is
`target/benchmark/raw.csv` and `target/benchmark/summary.csv`.
The matching Eclipse launch file uses the same goal. Normal `verify`,
JaCoCo and PIT runs only execute fast JUnit tests.

Override Maven properties for a bounded pilot, for example:

```sh
./mvnw test-compile exec:exec@run-performance-tests -Pperformance-tests \
  -Dbenchmark.scenario=extlibrary-pilot -Dbenchmark.roots=2 \
  -Dbenchmark.depth=1 -Dbenchmark.warmups=3 -Dbenchmark.repetitions=7 \
  -Dbenchmark.preflight=validate -Dbenchmark.heap=1g \
  -Dbenchmark.raw=target/benchmark/pilot-raw.csv \
  -Dbenchmark.summary=target/benchmark/pilot-summary.csv
```

The profile exposes `benchmark.metamodel`, `benchmark.package`,
`benchmark.root`, `benchmark.roots`, `benchmark.depth`,
`benchmark.attribute.max`, `benchmark.containment.max`,
`benchmark.cross.reference.max`, `benchmark.feature.map.max`,
`benchmark.warmups`, `benchmark.repetitions`, `benchmark.preflight`,
`benchmark.raw`, `benchmark.summary`, and `benchmark.heap`.
All defaults are deliberately small. For publication runs, use at least seven
measured samples after a bounded pilot and keep the heap and other JVM options
fixed across comparable runs. Record the host CPU model separately when reporting
results; the CSV records architecture, available processor count, OS, heap,
Java VM, and actual JVM arguments.

The CLI entry point is
`io.github.lorenzobettini.emfmodelgenerator.performance.EMFModelGeneratorPerformanceStatistics`.
`--help` lists its options. A direct Java launch must include test classes and
test dependencies on its classpath. The Maven profile supplies this classpath
and forks Java; `exec:java` runs in Maven's process and is unsuitable for
comparable measurements.

## Observation lifecycle

1. In the forked JVM, the harness reads and fingerprints the Ecore file, loads
   all its packages once, and selects the requested concrete root class.
   Loading is outside every timed interval.
2. Optional `validate` or `roundtrip` preflight generates the **same**
   configuration with a fresh generator and checks validity before warmup.
   Round-trip preflight may write XMI under `target/test-output`. Invalid
   preflight produces a status row and aborts measurements.
3. Each warmup uses a fresh generator and the same root count, depth and
   multiplicities as measurement. Warmup generation is discarded and has no
   raw measurement row. No explicit GC is requested between observations.
4. Each measured repetition constructs and configures a fresh generator
   **before** calling `System.nanoTime()`. The timer surrounds only
   `generateFromSeveral(rootEClass)`, including its internal model resource
   creation, package registration, root construction and population. The timer
   stops before counting, validation, serialization, logging or CSV writing.
   The candidate selector/cache is fresh with each generator; the library also
   resets its candidate cache before reference population.
5. After timing, the harness counts the actual returned roots and every EObject
   in generated model resources, including contained objects. It counts the
   non-Ecore resources in that generator's ResourceSet. It unloads and removes
   those resources after the observation. The loader owns Ecore registrations
   and removes only its own registrations at the end of the run. The separate
   JVM prevents unrelated earlier scenario state from sharing the registry.

The raw CSV has one row per completed measured repetition with exact elapsed
nanoseconds and counts. It includes run ID, UTC start, configuration, SHA-256
metamodel fingerprint, Java/OS/JVM metadata and status/error. Failed warmup or
preflight has a status row with index 0; a failed measured repetition retains
its own status row. The summary CSV reports successful sample count, median,
p25 and p75 nanoseconds (linear interpolation, rounded to a nanosecond).
Neither file is append-only: give every run a distinct pair of output paths.
Do not combine invalid or instrumented runs with baseline timings.

The forked CLI also prints a start and completion line for each preflight,
warmup, and measured generation. Completion lines show status and actual
root, EObject, and model-resource counts; measured lines include the exact
`elapsed_ns` written to raw CSV. After successful measurements, a final line
shows sample count, median, p25, p75, and output paths. Failures show their
status and error before the CLI aborts. Instrumented measured lines also show
`compute_calls`, `candidate_entries`, and `compute_elapsed_ns`. For example:

```text
[benchmark] scenario=extlibrary mode=baseline measurement 1/2 start requested_roots=1 depth=1
[benchmark] scenario=extlibrary mode=baseline measurement 1/2 done status=ok actual_roots=1 eobjects=4 model_resources=1 elapsed_ns=1234567
[benchmark] scenario=extlibrary mode=baseline summary samples=2 median_ns=1234567 p25_ns=1200000 p75_ns=1300000 raw=target/benchmark/raw.csv summary=target/benchmark/summary.csv
```

These numbers illustrate the format; actual results come from each run.
Console output is on by default and is flushed before timing begins. The
timer still surrounds only `generateFromSeveral`; output and CSV writing are
outside the timed interval. Console activity may still indirectly influence
JIT, GC, or scheduling, so use the same output behavior for compared runs.

## Candidate discovery diagnostic

The default `benchmark.diagnostics=off` uses the ordinary selector. Set the
property to a **third, distinct CSV path** to install a test-source subclass of
`EMFRoundRobinEObjectCandidateSelector` and record actual calls to its
`compute` method. The subclass delegates to `super.compute` and changes no
candidate order or cache behavior. Each observation gets a fresh selector;
preflight and warmup use the same mode but have no measured row. The raw and
summary CSVs label rows `baseline` or `instrumented` in `measurement_mode`.
Keep separate raw/summary paths for each mode as well as the diagnostics path.

For example, after `./mvnw -DskipTests test-compile`, run these bounded,
forked commands separately:

```sh
./mvnw exec:exec@run-profile-pilot -Pperformance-tests \
  -Dbenchmark.profile=extlibrary -Dbenchmark.roots=5 -Dbenchmark.depth=1 \
  -Dbenchmark.heap=512m -Dbenchmark.warmups=1 -Dbenchmark.repetitions=3 \
  -Dbenchmark.raw=target/benchmark/extlibrary-5-baseline-raw.csv \
  -Dbenchmark.summary=target/benchmark/extlibrary-5-baseline-summary.csv

./mvnw exec:exec@run-profile-pilot -Pperformance-tests \
  -Dbenchmark.profile=extlibrary -Dbenchmark.roots=5 -Dbenchmark.depth=1 \
  -Dbenchmark.heap=512m -Dbenchmark.warmups=1 -Dbenchmark.repetitions=3 \
  -Dbenchmark.raw=target/benchmark/extlibrary-5-instrumented-raw.csv \
  -Dbenchmark.summary=target/benchmark/extlibrary-5-instrumented-summary.csv \
  -Dbenchmark.diagnostics=target/benchmark/extlibrary-5-diagnostics.csv
```

The diagnostic CSV has one row per measured repetition with `run_id`,
`scenario`, `run_index`, `requested_roots`, `depth`, `actual_roots`, `eobjects`,
`model_resources`, `generation_elapsed_ns`, `compute_calls`,
`candidate_entries`, `compute_elapsed_ns`, `status`, and `error`. A failed
preflight or warmup has a status row at index zero. `compute_calls` counts
candidate-list recomputations on cache misses; `candidate_entries` sums list
sizes, including repeated entries across target classes. `compute_elapsed_ns`
times only calls to the superclass computation, including its ResourceSet
traversal and list construction. The instrumented generation time includes
timer and counter overhead, so it is not a substitute for baseline timing.
The selectors cache by target EClass and reset once before cross-reference
population. Counts of model resources and EObjects describe the generated
model, but root scaling varies both together; it does not isolate their costs.

## Cross-reference selector comparison

The `reference-aware-comparison` preset compares the default selector with
`EMFRoundRobinReferenceAwareEObjectCandidateSelector` in separate serial,
uninstrumented JVM forks. The opt-in selector requires monotonic opposite
capacity between resets; see the [selector documentation](README.md#cross-reference-candidate-selection)
for configuration and constraints.

```sh
python3 scripts/performance_evaluation.py run --preset reference-aware-comparison --roots 1,2,5 --scenarios plugin-eclipse,ant,extlibrary --heap 512m --timeout 120 --max-eobjects 1000 --max-resources 10 --warmups 1 --repetitions 2
python3 scripts/performance_evaluation.py report --latest reference-aware-comparison
```

Results include raw nanoseconds, actual EObject/resource counts, round-trip
preflight, graph fingerprints, selector source hashes, host metadata, failures,
medians, quartiles, and CSV/SVG figures. Speedup ratios require matching graph
fingerprints and counts.

## Cross-reference selection diagnostics

Run a bounded instrumented root sweep:

```sh
python3 scripts/performance_evaluation.py run --preset crossref-investigation --roots 1,2,5 --scenarios plugin-eclipse --heap 512m --timeout 120 --max-eobjects 1000 --max-resources 10 --warmups 1 --repetitions 2
python3 scripts/performance_evaluation.py report --latest crossref-investigation
```

Alternatively, use `--selection target/benchmark/publication-selection.json`
in place of `--roots`, with the same heap used for calibration. The selection
must match the current sources, metamodels, host, JDK, and JVM flags. The
calibrated diagnostic chooses up to three many-root sizes per supported scenario,
with caps of 200,000 EObjects and 5,000 resources.

The test-only probe records candidate attempts, rejection classifications,
selection/retrieval time, candidate discovery, and assignment time per reference.
It invokes the ordinary candidate evaluator once per attempt. Rejection reasons
are inferred after rejection; ambiguous self/opposite cases remain unknown.
Nested timings must not be added. Instrumented timings include probe overhead
and must be kept separate from uninstrumented generation timings.

To capture JFR for a separate uninstrumented PluginEclipse fork:

```sh
python3 scripts/profile_crossref_jfr.py --roots 5 --heap 512m --timeout 120 --max-eobjects 1000 --max-resources 10
jfr view --width 160 hot-methods target/benchmark/crossref-jfr-*/plugin-eclipse.jfr
```

JFR surrounds only the first measured generation call. Keep its timings separate
from baseline and instrumented counter measurements.

## Fixed logical model / resource layout

The `resource-layout` preset creates ordered EXTLibrary `Library` roots with
depth 1 and all feature maxima 1, places consecutive root blocks into a chosen
number of model resources in one fresh ResourceSet, then calls the public
`EMFInstancePopulator.populateEObjects(allRoots)` once. The normal
`generateFromSeveral` facade still creates one resource per root; this preset
does not time that facade. Every layout runs in its own serial, uninstrumented
forked JVM, with a fresh graph for preflight, warmups and each measured sample.

Development smoke:

```sh
./mvnw -q -DskipTests test-compile
python3 scripts/performance_evaluation.py run --preset resource-layout --scenario extlibrary --roots 1000 --resources 1,10,100,1000 --heap 512m --timeout 180 --max-eobjects 5000 --max-resources 1200 --warmups 1 --repetitions 2
python3 scripts/performance_evaluation.py report --latest resource-layout
```

The manifest records source and fixture hashes, host/JDK,
JVM flags, caps, status, command and logs. The raw CSV records root creation,
resource creation plus attachment, population, and their sum in nanoseconds.
The report and figure CSV/SVG show median and p25/p75 for each stage; failures
and caps remain in the manifest, summary and `failures.csv`.

Each layout has a full-size in-memory validation and XMI byte-stream reload
validation outside timing. The canonical graph fingerprint includes EClasses,
all attributes and their set state, containment, FeatureMaps and exact target
indices for cross-references. It ignores only physical resource boundaries and
URIs. `candidate-discovery.csv` records separate direct
`EMFUtils.findAllAssignableInstances` scans for Book and Writer, their counts,
logical index order hashes and cold-scan time. These scans are excluded from
population clocks and do not count selector cache misses. The default selector
caches candidate lists by EClass, so cold scans are not necessarily repeated
for each reference assignment.

EXTLibrary declares `Writer.name` transient and volatile. The ordinary
populator sets it in memory, but XMI omits it. Thus reloads validate and match
each other across layouts, while their full fingerprints differ from the
in-memory graph; preflight CSVs retain both hashes and `roundtrip_equal=false`.
The report discloses this semantic difference rather than normalizing it.
Fixed-size layout sweeps do not establish a universal complexity exponent.
