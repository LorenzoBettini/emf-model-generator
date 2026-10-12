# Benchmark harness

## Opt-in reference-aware monotonic selector

Install `new EMFReferenceAwareEObjectCandidateSelector()` through
`generator.getInstancePopulator().setCrossReferenceCandidateSelectorStrategy(...)`
only when a saturated opposite cannot become available again before the next
selector reset. `eUnset`, reassignment, inverse updates, FeatureMap changes,
callbacks, custom functions and external edits can restore capacity. Reset
after changing resources or metamodels; the selector rejects a different
resource scope without reset. Self-reference policies should be pure. The
ordinary round-robin selector remains the default. The opt-in selector uses
one `EClass` candidate list and cursor plus a `TreeSet` of stable active indices
per bounded `EReference` (O(N log N) construction, O(log N) navigation and
retirement, O(N) extra entries per bounded reference). Large numbers of
bounded references to the same type may justify using the default instead.

The new `reference-aware-comparison` preset runs the current default and the
opt-in selector in separate serial uninstrumented JVM forks. It preserves raw
nanoseconds, actual EObjects/resources, round-trip preflight, graph fingerprints,
source and host hashes, failures, medians, quartiles, CSV/SVG figures, and a
report-only regeneration path. The small development smoke is:

```sh
python3 scripts/performance_evaluation.py run --preset reference-aware-comparison --roots 1,2,5 --scenarios plugin-eclipse,ant,extlibrary --heap 512m --timeout 120 --max-eobjects 1000 --max-resources 10 --warmups 1 --repetitions 2
python3 scripts/performance_evaluation.py report --latest reference-aware-comparison
```

Candidate-work diagnostics use a separate instrumented runner. It records
attempts, opposite rejections, retirements, pool build nanoseconds, initial and
remaining index entries, and a rough 64-byte-per-entry footprint estimate.
Diagnostic times must not be compared with the uninstrumented A/B medians.

```sh
python3 scripts/reference_aware_diagnostics.py --roots 1,2,5 --scenarios plugin-eclipse,ant,extlibrary --heap 512m --timeout 120 --max-eobjects 1000 --max-resources 10
```

The bounded 1/2/5-root PluginEclipse diagnostic found opposite rejections
of 2/8/50 with the default and 2/4/10 with the opt-in selector; Ant and
EXTLibrary had none at these sizes. Every diagnostic pair matched its graph
fingerprint, actual counts and round-trip preflight. These are mechanism and
interface checks, not Dell-scale speedup evidence.
An optional 100-root PluginEclipse check also matched all fingerprints and
validation (1,466 EObjects / 100 resources). Separate diagnostics found
20,000 versus 200 opposite rejections and 20,400 versus 600 candidate attempts;
the opt-in selector built 200 pool entries and retired all 200. The rough
pool-index footprint was 12,800 bytes. This shows reduced rejection work at
this size, without establishing Dell-scale time or memory behavior.

## Archived cross-reference cursor comparison

Production now uses mutable cursor bookkeeping. New `crossref-comparison` runs
are disabled because both old CLI alternatives would use the adopted algorithm.
`python3 scripts/performance_evaluation.py report --latest crossref-comparison`
still regenerates reports from archived Task 06 raw results. Their `baseline`
and `experimental-cursor` labels describe the original, pre-adoption comparison.
Use a fresh `large-calibration` selection for publication measurements after
the production change; an older selection fails source compatibility checks.

The archived comparison was implemented with a test-source
`ExperimentalCursorSelector` installed through the existing selector strategy
hook. Historical baseline forks used the then-unchanged production selector.
The variant kept
the same cached candidate list, `getOrCompute` call sequence, live validity
checks, wrap behavior and reset, but stores a mutable cursor instead of replacing
a boxed map value on every candidate retrieval. It does **not** skip saturated
candidates or cache exhaustion: EMF inverse removal, reassignment, proxy
resolution and custom code can restore eligibility. Custom selector strategies
installed by a caller replace the experimental strategy; custom reference
functions and self policies still execute in the ordinary setter.

The historical Dell comparison commands were:

```sh
./mvnw -q -DskipTests test-compile
python3 scripts/performance_evaluation.py run --preset crossref-comparison --roots 100,1000,5000,10000,20000 --scenarios plugin-eclipse,ant,extlibrary --heap 2g --timeout 1800 --max-eobjects 400000 --max-resources 25000 --warmups 3 --repetitions 7
python3 scripts/performance_evaluation.py report --latest crossref-comparison
```

The last command only regenerated the report; the historical `run` reported automatically.
No calibration selection is needed. Each alternative uses its own serial,
uninstrumented fork, with pair order alternating by case. Every successful
repetition records elapsed nanoseconds, actual EObjects/resources and a graph
fingerprint that includes ordered feature values and reference target indices.
The fingerprint and XMI round-trip validation run outside the generation timer.
Only equivalent matched cases receive a speedup ratio. The unique output folder
under `target/benchmark/crossref-comparison-*/` contains raw/summary/fingerprint
CSVs, fork logs, a source/host/command manifest, `comparison-cases.csv`,
`failure-capped-cases.csv`, `figure-comparison.csv` and SVG, and `report.md`.
`latest-crossref-comparison.json` points to the last result. A failure or cap
stops larger sizes for that scenario and remains visible in the report.

The bounded 1–2-root three-scenario smoke and a separate 100-root PluginEclipse
check matched all graph signatures. These small development-host times are
interface/correctness checks, not Dell performance evidence. An independent
2-root counter sample found identical 16 attempts and 8 occupied-single-opposite
rejections per measured repetition for both alternatives. This is a
constant-factor experiment; production adoption requires a separate decision.

## Cross-reference selection investigation

Run from the core repository root using a calibrated selection from this physical
host. The first command compiles test sources; `run` writes its report automatically.
The final command regenerates it from retained raw files without generation.

```sh
./mvnw -q -DskipTests test-compile
python3 scripts/performance_evaluation.py run --preset crossref-investigation --heap 2g --timeout 900 --selection target/benchmark/publication-selection.json
python3 scripts/performance_evaluation.py report --latest crossref-investigation
```

This opt-in diagnostic chooses up to three calibrated many-root sizes each for
PluginEclipse, EXTLibrary and Ant, with a 200,000 EObject and 5,000 resource cap.
It records skipped sizes and failed forks. Each selected size runs in its own fork
with one warmup and two measured repetitions. These short instrumented runs are
diagnostic samples, not new publication timings. The directory is unique under
`target/benchmark/crossref-investigation-*/`, and
`target/benchmark/latest-crossref-investigation.json` points to the last complete
report. It contains an archived selection, manifest, raw CSVs, fork logs,
`selection-features.csv`, `selection-cases.csv`, `selection-hotspots.csv`,
`failure-capped-cases.csv`, and
`report.md`.

Compatibility checks the calibrated Ecore and production Java hashes, POM,
scenario profile, host, JDK, JVM flags, and heap. Benchmark driver/runner source
may differ only when its calibrated version matches the vetted publication
implementation at `ac673ba`, or the preserved test-only selection matches the
exact previous diagnostic hashes, and its current version matches the new
audited hashes. Other changed sources are rejected. The
calibration and diagnostic revision/dirty state are both retained. Use a new
calibration from the same host if this policy rejects the old one; never edit the
selection to force a match.

The test-only probe overrides `selectValue` and observes the existing round-robin
selector. It invokes the original private validity predicate once per candidate.
A full-cycle closing lookup is excluded from attempts. A null first lookup means
zero attempts. A successful custom-function short circuit has zero selector
attempts. Rejected selector candidates are classified *after* rejection: a
non-self candidate with an applicable opposite is classified as occupied single
or full many opposite; a self candidate without an opposite is self rejection.
Self candidates with opposites and other ambiguous cases remain unknown. No
policy or validity predicate is reevaluated. These are diagnostic inferences,
not a replacement validity rule.

The test-only setter mirrors the current production single/many assignment
loops to isolate `multi_selection_calls`, `unique_retries`, `unique_exhaustions`,
`contains_checks`, `contains_list_elements` (sum of list lengths checked),
`contains_elapsed_ns`, and single/many assignment count and elapsed nanoseconds.
Assignment time includes EMF inverse/opposite maintenance, notification and
list operations; it cannot isolate those internal costs. `selectValue` elapsed
contains selector retrieval and candidate compute; these nested times must not
be added. All timings include instrumentation overhead. The test suite compares
selected identity/order, XMI, validation and opposite behavior with the normal
setter. The later cursor comparison above remains a separate uninstrumented
experiment; diagnostic elapsed times must never be mixed into its A/B table.

### Explicit root sweep for the follow-up

The calibrated selection currently reaches only tiny models. `--roots` runs
independent, opt-in, ascending configurations without that selection. It records
source/host metadata, actual counts, raw rows, failures and caps in a new result
directory. A projected EObject bound (14 per PluginEclipse root, 4 for
EXTLibrary, 9 for Ant) checks each request before launch; the observed count is
checked after each fork. It stops the sweep after a failure, timeout or cap.
Keep the Mac smoke bounded to 5 roots:

```sh
./mvnw -q -DskipTests test-compile
python3 scripts/performance_evaluation.py run --preset crossref-investigation --roots 1,2,5 --scenarios plugin-eclipse --heap 512m --timeout 120 --max-eobjects 1000 --max-resources 10 --warmups 1 --repetitions 2
python3 scripts/performance_evaluation.py report --latest crossref-investigation
```

On the Dell workstation, stage the same fixed PluginEclipse profile toward the
previous ~293,000 EObject region. Raise caps only with an explicit new command:

```sh
./mvnw -q -DskipTests test-compile
python3 scripts/performance_evaluation.py run --preset crossref-investigation --roots 100,1000,5000,10000,20000 --scenarios plugin-eclipse --heap 2g --timeout 1800 --max-eobjects 400000 --max-resources 25000 --warmups 1 --repetitions 2
python3 scripts/performance_evaluation.py report --latest crossref-investigation
```

Profile one separate **uninstrumented** PluginEclipse fork with JFR at a size
the staged sweep has shown feasible. The benchmark driver starts JFR around
the first measured `generateFromSeveral` call only, excluding metamodel loading
and post-generation validation. This preserves a separate raw baseline and a
`.jfr` file; it is diagnostic, not a publication measurement:

```sh
python3 scripts/profile_crossref_jfr.py --roots 20000 --heap 2g --timeout 1800 --max-eobjects 400000 --max-resources 25000
jfr view --width 160 hot-methods target/benchmark/crossref-jfr-*/plugin-eclipse.jfr
```

Inspect `gc`, `allocation-by-site` and `hot-methods` views, and compare the
dominant stacks with per-reference counters. Keep JFR timings separate from
uninstrumented baseline and instrumented counter timings. The Mac smoke does
not establish the cause of the previously observed large-model slowdown.

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

The fourth command is optional and only regenerates the report. The calibration
must be fresh after cursor adoption. Publication forks clear any inherited
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

This test-source harness measures generation from one **named EClass** in one named
EPackage. It replaces the old all-class sweep: `numberOfInstances` is the number
of roots of the selected class, rather than the number for every concrete class.
The old `--models-min/max/step` and `--depth-min/max/step` options are retired.
Invoke one configuration per JVM. Size and depth series are separate invocations.

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

The following are **observed pilot counts**, not publication measurements. Each
entry is the total EObject count for the root counts in the same table row, in
order. All measured rows reported the requested number of roots and one model
resource per root. Preflight passed at every listed scaling size. One warmup and
two measured observations at five roots had identical EObject/resource counts
within each profile. This checks size stability; it does not prove identical
internal graphs across repetitions. A fast independent-generation test also
compares the contained EClass frequency maps at five roots for every profile.

| Profile at scaling depth | Roots | Actual EObjects | Model resources |
|---|---|---|---|
| `extlibrary` depth 1 | 1, 2, 5, 10, 20 | 4, 8, 20, 40, 80 | 1, 2, 5, 10, 20 |
| `bibtex` depth 2 | 1, 2, 5, 10, 20 | 3, 6, 15, 30, 60 | 1, 2, 5, 10, 20 |
| `ant` depth 2 | 1, 2, 5, 10, 20 | 9, 18, 45, 90, 180 | 1, 2, 5, 10, 20 |
| `plugin-eclipse` depth 4 | 1, 2, 5, 10, 20 | 14, 28, 72, 146, 292 | 1, 2, 5, 10, 20 |
| `rss` depth 3 | 1, 2, 5, 10, 20 | 13, 26, 65, 130, 260 | 1, 2, 5, 10, 20 |
| `bpmn-bounded` depth 1 | 1, 2, 3, 5 | 5, 10, 15, 25 | 1, 2, 3, 5 |

At alternate depths, one root and the largest allowed root count also passed
preflight: EXTLibrary depth 2 produced 7 and 140 EObjects; BibTeX depth 1
produced 2 and 40; RSS depth 2 produced 9 and 180; BPMN depth 2 produced 16
and 80. Intermediate root counts at those alternate depths remain exploratory.
PluginEclipse depth 3 with these multiplicities failed XMI round-trip preflight:
an imported `Range` lacked required `lowerBound` and `upperBound` children.
Depths 4 and 5 passed at one root; only depth 4 was profiled across sizes.

These bounds are for **short pilots** with a 512 MiB heap and a 30 second wall
limit per forked Maven invocation. Calibration used zero warmups and one measured
sample at each size, then one warmup and two measured samples at five roots to
check counts. The measured call took roughly 1–24 ms across these runs, but
single samples after little or no warmup do not establish scaling or comparative
performance. PluginEclipse EObject totals are not exactly proportional to roots,
which is another reason to use actual counts. The graph and cross-reference
costs still need larger, repeated measurements. BPMN is a bounded stress case,
not part of a claim that all eight external metamodels are benchmarked. AADL
`ComponentImplementation` and BPEL/WSDL were deliberately excluded because
their functional examples need specialized setup or have problematic validity.

Reproduce one size with a separate forked JVM and unique CSV paths:

```sh
./mvnw -DskipTests test-compile
timeout 30s ./mvnw exec:exec@run-profile-pilot -Pperformance-tests \
  -Dbenchmark.profile=ant -Dbenchmark.roots=5 -Dbenchmark.depth=2 \
  -Dbenchmark.heap=512m -Dbenchmark.warmups=0 -Dbenchmark.repetitions=1 \
  -Dbenchmark.raw=target/benchmark/calibration-ant-5-raw.csv \
  -Dbenchmark.summary=target/benchmark/calibration-ant-5-summary.csv
```

Repeat with each profile and listed root count; set `benchmark.depth` to the
bold depth in the first table. Use distinct output paths for every invocation.
The profile execution supplies its validity preflight automatically, outside
the timed call. On Linux, `timeout` caps Maven plus the forked Java process;
the heap cap applies to the forked benchmark JVM. For any publication run,
recalibrate larger sizes and collect at least seven measured repetitions after
scenario-matched warmups.

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
