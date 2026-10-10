# Benchmark harness

This test-source harness measures generation from one **named EClass** in one named
EPackage. It replaces the old all-class sweep: `numberOfInstances` is the number
of roots of the selected class, rather than the number for every concrete class.
The old `--models-min/max/step` and `--depth-min/max/step` options are retired.
Invoke one configuration per JVM. Size and depth series are separate invocations;
Task 01 will calibrate safe ranges and scenario profiles.

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
