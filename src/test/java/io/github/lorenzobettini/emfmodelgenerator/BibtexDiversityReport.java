package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFModelGeneratorDiversityTest.distinct;
import static io.github.lorenzobettini.emfmodelgenerator.EMFModelGeneratorDiversityTest.measurements;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.emf.ecore.EPackage;

import io.github.lorenzobettini.emfmodelgenerator.EMFModelGeneratorDiversityTest.Measurement;
import io.github.lorenzobettini.emfmodelgenerator.EMFModelGeneratorDiversityTest.Run;
import io.github.lorenzobettini.emfmodelgenerator.EMFModelGeneratorDiversityTest.Strategy;

/**
 * Regenerates CSV measurements and a transfer-ready summary outside test-output cleanup.
 *
 * @author Lorenzo Bettini
 */
final class BibtexDiversityReport {

	private BibtexDiversityReport() {
	}

	static void write(final List<Run> runs) throws IOException {
		var directory = Path.of("target/diversity-experiment");
		Files.createDirectories(directory);
		var csv = new StringBuilder("strategy,run,seed,entries,fields_per_entry,eobjects,"
				+ "entry_types_count,field_types_count,entry_types,field_types,type_counts,"
				+ "first_entry_type,first_field_type,"
				+ "valid,diagnostics,reproduced,replay_diagnostics,"
				+ "value_signature,type_signature,topology_signature\n");
		for (var run : runs) {
			var m = run.measurement();
			var s = m.snapshot();
			csv.append(Stream.of(m.strategy(), m.run(), m.seed(), m.entries(), m.fieldsPerEntry(),
					s.objectCount(), m.entryTypes().size(), m.fieldTypes().size(),
					joined(m.entryTypes()), joined(m.fieldTypes()), new TreeMap<>(s.typeCounts()),
					m.firstEntryType(), m.firstFieldType(),
					m.valid(), m.diagnostics(), run.reproduced(), run.replayDiagnostics(),
					s.values(), s.types(), s.topology())
					.map(BibtexDiversityReport::csvCell).collect(Collectors.joining(",")))
					.append('\n');
		}
		Files.writeString(directory.resolve("runs.csv"), csv);
		var summary = new StringBuilder("""
				# BibTeX reproducibility and diversity

				Regenerate: `./mvnw -Dtest=EMFModelGeneratorDiversityTest test`.
				CSV contains 60 primary runs, full signatures, concrete type sets/frequencies,
				multiplicities, validation diagnostics, and replay outcomes. No failed model is dropped.

				| Strategy | Runs | Value signatures | Type signatures | Topology signatures | Entry coverage (sample) | Field coverage (sample) | EObjects | Valid | Failed | Reproduced |
				|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
				""");
		var details = new StringBuilder();
		for (var strategy : Strategy.values()) {
			var sample = measurements(runs, strategy);
			var counts = sample.stream().mapToInt(m -> m.snapshot().objectCount())
					.summaryStatistics();
			var valid = sample.stream().filter(Measurement::valid).count();
			var reproduced = runs.stream().filter(r -> r.measurement().strategy() == strategy)
					.filter(Run::reproduced).count();
			summary.append("| %s | %d | %d | %d | %d | %d/12 | %d/29 | %d–%d | %d | %d | %d/%d |%n"
					.formatted(strategy, sample.size(),
							distinct(sample, ModelDiversityMetrics.Snapshot::values),
							distinct(sample, ModelDiversityMetrics.Snapshot::types),
							distinct(sample, ModelDiversityMetrics.Snapshot::topology),
							coverage(sample, Measurement::entryTypes),
							coverage(sample, Measurement::fieldTypes),
							counts.getMin(), counts.getMax(), valid, sample.size() - valid,
							reproduced, sample.size()));
			details.append("%s per-model coverage: Entry %s/12; Field %s/29.\n\n"
					.formatted(strategy, range(sample, m -> m.entryTypes().size()),
							range(sample, m -> m.fieldTypes().size())));
		}
		var c = measurements(runs, Strategy.C);
		details.append("C at fixed structural positions: %d distinct first-Entry types and "
				.formatted(c.stream().map(Measurement::firstEntryType).distinct().count()))
				.append("%d distinct first-Field types across the sample.\n"
						.formatted(c.stream().map(Measurement::firstFieldType).distinct().count()));
		summary.append('\n').append(details).append("""

				## Fixed protocol

				A: fresh default attribute generation and round-robin subtype selection,
				12 entries × 12 fields each (157 EObjects including one Bibtex root).
				B: same structure, with Datafaker through functionForAttribute for Entry.key
				(sanitized surname plus a fresh increasing suffix) and Field.value (four-word
				Lorem sentence). Other attributes retain their defaults.
				C: same value functions, uniform subtype draws with replacement via
				functionForContainmentReference and EcoreUtil.create. Candidate lists are sorted
				by namespace URI/class name. Random(seed XOR 0x5EED) chooses Entry/Field types.
				A separate Random(seed) draws entries with nextInt(6,19), then fields with
				nextInt(4,17). The field count applies to every entry in that run. Existing
				per-reference multiplicity setters apply both counts; maximum depth is 2 for all strategies.
				The maximum permitted size is 307 EObjects. Seeds are 1–20 for B/C; A has no seed.
				Datafaker 2.7.0 (test scope), Locale.ENGLISH, and its own new Random(seed) are
				used per run. Datafaker's declared Java baseline is 17, compatible with Java 21.
				No date/time providers are used. All generators, resource sets, metamodels,
				selectors, counters and random sources are fresh. Owned package registrations
				are removed and resources unloaded after each measurement.

				A is compared across 20 fresh runs. Each B/C seed is independently generated
				twice (40 additional replay models, excluded from the 60-model sample). All
				three full signatures and type frequencies must match, and both models must
				validate. Another test changes Datafaker consumption and verifies that C's
				structure remains identical.

				## Measurements and interpretation

				Traversal orders roots by list position, containment features by name, and
				children by list position (including features declared unordered in Ecore).
				Values: structural position, declaring attribute's namespace/class/name,
				set/unset state, value count and EFactory lexical values, including defaults.
				Types: structural position and namespace-qualified concrete EClass.
				Topology: root count, object positions, containment feature names and child
				counts (including zero), hierarchical arrangement and total object count.
				Length-prefixed tokens distinguish embedded delimiters and null/empty values.
				Equality compares full signatures, without hashes or object identities.
				Entry/Field sample coverage is the union across the 20 primary models;
				per-model ranges above distinguish it from coverage within one model.
				Validity uses EMFModelGenerator.validate(); diagnostics remain in CSV.

				B isolates value changes with identical structure to A. In C, concrete subtype
				choices change the type signature and observed subtype frequencies; count
				changes also affect its length. Only seed-controlled multiplicities change
				containment topology here: all Entry subclasses share fields, and Field
				subclasses have no children. Metric sensitivity tests independently change
				values, concrete types, root counts and child placement to check this separation.
				The fixed-position observations above isolate actual subtype diversity from size changes.
				The value signature in C can also change because of size or default attributes
				that incorporate class names; it is not an isolated measure of Datafaker's effect.

				BibTeX has no non-containment references: no arbitrary graph/connectivity
				diversity is demonstrated. EMF validity does not establish domain-specific
				BibTeX validity (e.g. a Year value can be generic text). These finite fixed-seed
				observations are neither statistical guarantees, systematic model coverage,
				nor proof of adequacy for testing every MDE tool. Reproducibility is conditional
				on the same procedure, runtime, metamodel and dependency configuration;
				in-memory signature equality does not require byte-identical XMI.

				Datafaker release: https://www.datafaker.net/releases/2.7.0/
				Seeded API: https://www.datafaker.net/documentation/usage/#repeatable-random-results
				""");
		summary.append("\nRuntime: Java %s (%s); EMF artifact %s; model generator 2.0.0-SNAPSHOT.\n"
				.formatted(System.getProperty("java.version"), System.getProperty("java.vendor"),
						Path.of(URI.create(EPackage.class.getProtectionDomain().getCodeSource()
								.getLocation().toExternalForm())).getFileName()));
		Files.writeString(directory.resolve("summary.md"), summary);
	}

	private static String csvCell(final Object value) {
		return "\"" + value.toString().replace("\"", "\"\"") + "\"";
	}

	private static String joined(final Collection<String> values) {
		return values.stream().sorted().collect(Collectors.joining(";"));
	}

	private static long coverage(final List<Measurement> sample,
			final Function<Measurement, Set<String>> types) {
		return sample.stream().map(types).flatMap(Collection::stream).distinct().count();
	}

	private static String range(final List<Measurement> sample,
			final java.util.function.ToIntFunction<Measurement> metric) {
		var stats = sample.stream().mapToInt(metric).summaryStatistics();
		return stats.getMin() + "–" + stats.getMax();
	}
}
