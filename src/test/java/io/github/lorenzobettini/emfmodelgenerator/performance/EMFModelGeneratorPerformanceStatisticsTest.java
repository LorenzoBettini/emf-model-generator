package io.github.lorenzobettini.emfmodelgenerator.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.lorenzobettini.emfmodelgenerator.EMFModelGenerator;
import io.github.lorenzobettini.emfmodelgenerator.performance.EMFModelGeneratorPerformanceStatistics.Counts;
import io.github.lorenzobettini.emfmodelgenerator.performance.EMFModelGeneratorPerformanceStatistics.Sample;

class EMFModelGeneratorPerformanceStatisticsTest {

	@TempDir
	Path temporary;

	@Test
	void parsesConfigurationAndRejectsBadArguments() {
		final var config = EMFModelGeneratorPerformanceStatistics.parse("--roots", "3",
				"--depth", "0", "--preflight", "validate");
		assertThat(config.roots()).isEqualTo(3);
		assertThat(config.depth()).isZero();
		assertThat(config.preflight()).isEqualTo(
				EMFModelGeneratorPerformanceStatistics.Preflight.VALIDATE);
		assertThatThrownBy(() -> EMFModelGeneratorPerformanceStatistics.parse("--bad", "1"))
				.hasMessageContaining("Unknown option");
		assertThatThrownBy(() -> EMFModelGeneratorPerformanceStatistics.parse("--roots"))
				.hasMessageContaining("Missing value");
		assertThatThrownBy(() -> EMFModelGeneratorPerformanceStatistics.parse(
				"--roots", "1", "--roots", "2")).hasMessageContaining("Duplicate");
		assertThatThrownBy(() -> EMFModelGeneratorPerformanceStatistics.parse("--roots", "a"))
				.hasMessageContaining("integer");
		assertThatThrownBy(() -> EMFModelGeneratorPerformanceStatistics.parse("--roots", "0"))
				.hasMessageContaining("positive");
		assertThatThrownBy(() -> EMFModelGeneratorPerformanceStatistics.parse(
				"--preflight", "bad")).hasMessageContaining("preflight");
		assertThat(EMFModelGeneratorPerformanceStatistics.help()).contains("--help");
		assertThatThrownBy(() -> EMFModelGeneratorPerformanceStatistics.parse(
				"--diagnostics", "target/benchmark/raw.csv"))
				.hasMessageContaining("diagnostics output path must differ");
	}

	@Test
	void selectsTheNamedPackageAndCountsActualModelResources() throws Exception {
		final var loader = new EMFModelGenerator();
		try {
			final var packages = loader.loadEcoreModelPackages(
					"src/test/resources/inputs/multiple-packages.ecore");
			assertThat(packages).hasSizeGreaterThan(1);
			assertThatThrownBy(() -> EMFModelGeneratorPerformanceStatistics.selectRoot(
					packages, "missing", "Root")).hasMessageContaining("found 0");
			final var packageName = packages.get(1).getName();
			final var rootName = packages.get(1).getEClassifiers().stream()
					.filter(c -> c instanceof org.eclipse.emf.ecore.EClass e
							&& !e.isAbstract() && !e.isInterface())
					.findFirst().orElseThrow().getName();
			final var root = EMFModelGeneratorPerformanceStatistics.selectRoot(
					packages, packageName, rootName);
			assertThat(root.getName()).isEqualTo(rootName);
			assertThatThrownBy(() -> EMFModelGeneratorPerformanceStatistics.selectRoot(
					packages, packageName, "missing")).hasMessageContaining("concrete EClass");
		} finally {
			loader.unloadEcoreModels();
		}

		final var generator = new EMFModelGenerator();
		try {
			final var packages = generator.loadEcoreModelPackages(
					"src/test/resources/inputs/simplelibrary.ecore");
			final var root = EMFModelGeneratorPerformanceStatistics.selectRoot(packages,
					packages.getFirst().getName(), "Library");
			generator.getInstancePopulator().setMaxDepth(1);
			generator.getInstancePopulator().setContainmentReferenceDefaultMaxCount(1);
			generator.setNumberOfInstances(2);
			final var generated = generator.generateFromSeveral(root);
			final var counts = EMFModelGeneratorPerformanceStatistics.count(
					generator, generated.size());
			assertThat(counts.roots()).isEqualTo(2);
			assertThat(counts.objects()).isEqualTo(6);
			assertThat(counts.resources()).isEqualTo(2);
		} finally {
			generator.unloadEcoreModels();
		}
	}

	@Test
	void computesInterpolatedQuartilesFromSuccessfulSamples() {
		final var counts = new Counts(1, 1, 1);
		final var samples = List.of(new Sample(1, 10, counts, "ok", ""),
				new Sample(2, 20, counts, "ok", ""),
				new Sample(3, 30, counts, "ok", ""),
				new Sample(4, 40, counts, "ok", ""),
				new Sample(5, 0, counts, "error", "failed"));
		final var summary = EMFModelGeneratorPerformanceStatistics.summarize(samples);
		assertThat(summary.median()).isEqualTo(25);
		assertThat(summary.p25()).isEqualTo(18);
		assertThat(summary.p75()).isEqualTo(33);
		assertThatThrownBy(() -> EMFModelGeneratorPerformanceStatistics.summarize(
				List.of(samples.getLast()))).hasMessageContaining("No successful");
	}

	@Test
	void smokeRunWritesOneRawRowPerMeasurementAndNoWarmupRow() throws Exception {
		final var raw = temporary.resolve("raw.csv");
		final var summary = temporary.resolve("summary.csv");
		final var config = EMFModelGeneratorPerformanceStatistics.parse(
				"--scenario", "small",
				"--metamodel", "src/test/resources/inputs/simplelibrary.ecore",
				"--package", "simplelibrary", "--root", "Library",
				"--roots", "2", "--depth", "0", "--warmups", "1",
				"--repetitions", "2", "--raw", raw.toString(),
				"--summary", summary.toString());
		final var samples = EMFModelGeneratorPerformanceStatistics.run(config);
		assertThat(samples).hasSize(2);
		assertThat(samples).allSatisfy(sample -> {
			assertThat(sample.status()).isEqualTo("ok");
			assertThat(sample.counts()).isEqualTo(new Counts(2, 2, 2));
			assertThat(sample.elapsedNanos()).isPositive();
		});
		final var rawLines = Files.readAllLines(raw);
		assertThat(rawLines).hasSize(3);
		assertThat(rawLines.getFirst()).contains("metamodel_sha256", "eobjects",
				"model_resources", "elapsed_ns", "measurement_mode", "status");
		assertThat(rawLines.get(1)).contains("\"small\"", "\"1\"", "\"ok\"");
		assertThat(rawLines.get(1)).contains("\"baseline\"");
		assertThat(rawLines.get(2)).contains("\"small\"", "\"2\"", "\"ok\"");
		final var summaryLines = Files.readAllLines(summary);
		assertThat(summaryLines).hasSize(2);
		assertThat(summaryLines.get(1)).contains("\"small\"", "\"2\"", "\"ok\"");
	}

	@Test
	void diagnosticsWriteSeparatePerMeasurementRows() throws Exception {
		final var raw = temporary.resolve("instrumented-raw.csv");
		final var summary = temporary.resolve("instrumented-summary.csv");
		final var diagnostics = temporary.resolve("diagnostics.csv");
		final var config = EMFModelGeneratorPerformanceStatistics.parse(
				"--profile", "extlibrary", "--roots", "2", "--depth", "1",
				"--warmups", "1", "--repetitions", "2", "--raw", raw.toString(),
				"--summary", summary.toString(), "--diagnostics", diagnostics.toString());
		final var samples = EMFModelGeneratorPerformanceStatistics.run(config);
		assertThat(samples).hasSize(2).allSatisfy(sample -> {
			assertThat(sample.status()).isEqualTo("ok");
			assertThat(sample.counts()).isEqualTo(new Counts(2, 8, 2));
			assertThat(sample.diagnostic().computeCalls()).isPositive();
			assertThat(sample.diagnostic().candidateEntries()).isPositive();
			assertThat(sample.diagnostic().computeNanos()).isPositive();
		});
		assertThat(Files.readAllLines(raw).get(1)).contains("\"instrumented\"");
		assertThat(Files.readAllLines(diagnostics)).hasSize(3);
		assertThat(Files.readAllLines(diagnostics).getFirst())
				.contains("compute_calls", "candidate_entries", "compute_elapsed_ns");
	}
}
