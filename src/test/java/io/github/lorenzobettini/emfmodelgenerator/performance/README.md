# Benchmark harness

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
