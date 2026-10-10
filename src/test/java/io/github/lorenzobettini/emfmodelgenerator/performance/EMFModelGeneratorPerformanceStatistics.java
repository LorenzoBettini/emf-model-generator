package io.github.lorenzobettini.emfmodelgenerator.performance;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;

import io.github.lorenzobettini.emfmodelgenerator.EMFModelGenerator;

/**
 * Explicit-root benchmark harness. Launch in a separate JVM; see
 * {@code src/test/java/io/github/lorenzobettini/emfmodelgenerator/performance/README.md}.
 */
public final class EMFModelGeneratorPerformanceStatistics {

	private static final String RAW_HEADER = String.join(",",
			"run_id", "started_utc", "scenario", "metamodel", "metamodel_sha256",
			"package", "root_class", "requested_roots", "depth", "attribute_max",
			"containment_max", "cross_reference_max", "feature_map_max", "warmups",
			"repetitions", "preflight", "run_index", "elapsed_ns", "actual_roots",
			"eobjects", "model_resources", "status", "error", "java_version",
			"java_vm", "jvm_args", "os_name", "os_arch", "cpu_count", "max_heap_bytes");
	private static final String SUMMARY_HEADER = String.join(",",
			"run_id", "scenario", "successful_samples", "median_ns", "p25_ns", "p75_ns",
			"status");

	private EMFModelGeneratorPerformanceStatistics() {
	}

	record Configuration(String scenario, Path metamodel, String packageName, String rootClass,
			int roots, int depth, int attributeMax, int containmentMax, int crossReferenceMax,
			int featureMapMax, int warmups, int repetitions, Preflight preflight,
			Path rawPath, Path summaryPath) {
		Configuration {
			if (scenario.isBlank() || packageName.isBlank() || rootClass.isBlank()) {
				throw new IllegalArgumentException("scenario, package and root must be nonblank");
			}
			if (roots < 1 || depth < 0 || attributeMax < 0 || containmentMax < 0
					|| crossReferenceMax < 0 || featureMapMax < 0
					|| warmups < 0 || repetitions < 1) {
				throw new IllegalArgumentException(
						"roots and repetitions must be positive; depth, maxima and warmups nonnegative");
			}
			if (rawPath.equals(summaryPath)) {
				throw new IllegalArgumentException("raw and summary output paths must differ");
			}
		}
	}

	enum Preflight {
		NONE, VALIDATE, ROUNDTRIP
	}

	record Counts(int roots, long objects, int resources) {
	}

	record Sample(int index, long elapsedNanos, Counts counts, String status, String error) {
	}

	record Summary(long median, long p25, long p75) {
	}

	record Metadata(String runId, String started, String fingerprint, String javaVersion,
			String javaVm, String jvmArgs, String osName, String osArch, int cpuCount,
			long maxHeapBytes) {
		static Metadata capture(final Path metamodel) throws IOException {
			final byte[] bytes = Files.readAllBytes(metamodel);
			final byte[] hash;
			try {
				hash = MessageDigest.getInstance("SHA-256").digest(bytes);
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException("SHA-256 unavailable", e);
			}
			final var fingerprint = new StringBuilder();
			for (final byte value : hash) {
				fingerprint.append(String.format(Locale.ROOT, "%02x", value & 0xff));
			}
			return new Metadata(UUID.randomUUID().toString(), Instant.now().toString(),
					fingerprint.toString(), System.getProperty("java.version"),
					System.getProperty("java.vm.name"),
					String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()),
					System.getProperty("os.name"), System.getProperty("os.arch"),
					Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory());
		}
	}

	static Configuration parse(final String... args) {
		final Map<String, String> values = new TreeMap<>();
		final var allowed = List.of("scenario", "metamodel", "package", "root", "roots",
				"depth", "attribute-max", "containment-max", "cross-reference-max",
				"feature-map-max", "warmups", "repetitions", "preflight", "raw", "summary");
		for (int i = 0; i < args.length; i += 2) {
			final String key = args[i];
			if (!key.startsWith("--") || !allowed.contains(key.substring(2))) {
				throw new IllegalArgumentException("Unknown option: " + key);
			}
			if (i + 1 == args.length || args[i + 1].startsWith("--")) {
				throw new IllegalArgumentException("Missing value for " + key);
			}
			if (values.putIfAbsent(key.substring(2), args[i + 1]) != null) {
				throw new IllegalArgumentException("Duplicate option: " + key);
			}
		}
		final var metamodel = Path.of(values.getOrDefault("metamodel",
				"src/test/resources/inputs/extlibrary.ecore"));
		final var preflight = values.getOrDefault("preflight", "none").toUpperCase(Locale.ROOT);
		final Preflight mode;
		try {
			mode = Preflight.valueOf(preflight);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(
					"preflight must be none, validate or roundtrip", e);
		}
		return new Configuration(values.getOrDefault("scenario", "extlibrary-smoke"), metamodel,
				values.getOrDefault("package", "extlibrary"),
				values.getOrDefault("root", "Library"),
				number(values, "roots", 1), number(values, "depth", 1),
				number(values, "attribute-max", 1),
				number(values, "containment-max", 1),
				number(values, "cross-reference-max", 1),
				number(values, "feature-map-max", 1),
				number(values, "warmups", 1), number(values, "repetitions", 2), mode,
				Path.of(values.getOrDefault("raw", "target/benchmark/raw.csv")),
				Path.of(values.getOrDefault("summary", "target/benchmark/summary.csv")));
	}

	private static int number(final Map<String, String> values, final String key,
			final int defaultValue) {
		try {
			return Integer.parseInt(values.getOrDefault(key, Integer.toString(defaultValue)));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("--" + key + " requires an integer", e);
		}
	}

	static String help() {
		return """
				Usage: EMFModelGeneratorPerformanceStatistics [--option value ...]
				--scenario ID --metamodel FILE --package NAME --root ECLASS
				--roots N --depth N --attribute-max N --containment-max N
				--cross-reference-max N --feature-map-max N
				--warmups N --repetitions N --preflight none|validate|roundtrip
				--raw FILE --summary FILE
				Defaults: EXTLibrary Library, 1 root, depth 1, maxima 1,
				1 warmup, 2 repetitions, no preflight, output under target/benchmark.
				Use --help alone for this text.
				""";
	}

	public static void main(final String[] args) throws IOException {
		if (Arrays.equals(args, new String[] {"--help"})) {
			System.out.print(help());
			return;
		}
		final var config = parse(args);
		run(config);
	}

	static List<Sample> run(final Configuration config) throws IOException {
		final var metadata = Metadata.capture(config.metamodel());
		final var loader = new EMFModelGenerator();
		final var samples = new ArrayList<Sample>();
		try {
			final var packages = loader.loadEcoreModelPackages(config.metamodel().toString());
			final var root = selectRoot(packages, config.packageName(), config.rootClass());
			if (config.preflight() != Preflight.NONE) {
				final var checked = observe(config, root, 0, false, true);
				if (!checked.status().equals("ok")) {
					samples.add(checked);
					write(config, metadata, samples);
					throw new IllegalStateException("Preflight failed: " + checked.error());
				}
			}
			for (int i = 0; i < config.warmups(); i++) {
				final var warmup = observe(config, root, 0, false, false);
				if (!warmup.status().equals("ok")) {
					samples.add(warmup);
					write(config, metadata, samples);
					throw new IllegalStateException("Warmup failed: " + warmup.error());
				}
			}
			for (int i = 1; i <= config.repetitions(); i++) {
				final var sample = observe(config, root, i, true, false);
				samples.add(sample);
				write(config, metadata, samples);
				if (!sample.status().equals("ok")) {
					throw new IllegalStateException("Measurement failed: " + sample.error());
				}
			}
			return List.copyOf(samples);
		} finally {
			loader.unloadEcoreModels();
		}
	}

	static EClass selectRoot(final List<EPackage> packages, final String packageName,
			final String rootName) {
		final var matches = packages.stream().filter(p -> p.getName().equals(packageName)).toList();
		if (matches.size() != 1) {
			throw new IllegalArgumentException("Expected exactly one package named "
					+ packageName + "; found " + matches.size());
		}
		final var classifier = matches.getFirst().getEClassifier(rootName);
		if (!(classifier instanceof EClass root) || root.isAbstract() || root.isInterface()) {
			throw new IllegalArgumentException("Root must be a concrete EClass in "
					+ packageName + ": " + rootName);
		}
		return root;
	}

	private static EMFModelGenerator configured(final Configuration config) {
		final var generator = new EMFModelGenerator();
		generator.setNumberOfInstances(config.roots());
		final var populator = generator.getInstancePopulator();
		populator.setMaxDepth(config.depth());
		populator.setAttributeDefaultMaxCount(config.attributeMax());
		populator.setContainmentReferenceDefaultMaxCount(config.containmentMax());
		populator.setCrossReferenceDefaultMaxCount(config.crossReferenceMax());
		populator.setFeatureMapDefaultMaxCount(config.featureMapMax());
		return generator;
	}

	private static Sample observe(final Configuration config, final EClass root,
			final int index, final boolean measured, final boolean preflight) {
		final var generator = configured(config);
		try {
			final long start = measured ? System.nanoTime() : 0;
			final var models = generator.generateFromSeveral(root);
			final long elapsed = measured ? System.nanoTime() - start : 0;
			final var counts = count(generator, models.size());
			if (preflight) {
				final var result = config.preflight() == Preflight.ROUNDTRIP
						? generator.saveAndValidateRoundTrip() : generator.validate();
				if (!result.isValid()) {
					return new Sample(index, elapsed, counts, "invalid",
							result.diagnostic().toString());
				}
			}
			return new Sample(index, elapsed, counts, "ok", "");
		} catch (Exception e) {
			return new Sample(index, 0, new Counts(0, 0, 0), "error", e.toString());
		} finally {
			for (final Resource resource : List.copyOf(generator.getResourceSet().getResources())) {
				resource.unload();
				generator.getResourceSet().getResources().remove(resource);
			}
		}
	}

	static Counts count(final EMFModelGenerator generator, final int roots) {
		long objects = 0;
		int resources = 0;
		for (final var resource : generator.getResourceSet().getResources()) {
			if (!"ecore".equals(resource.getURI().fileExtension())) {
				resources++;
				final var contents = resource.getAllContents();
				while (contents.hasNext()) {
					contents.next();
					objects++;
				}
			}
		}
		return new Counts(roots, objects, resources);
	}

	static Summary summarize(final List<Sample> samples) {
		final var sorted = samples.stream().filter(s -> s.status().equals("ok"))
				.map(Sample::elapsedNanos).sorted(Comparator.naturalOrder()).toList();
		if (sorted.isEmpty()) {
			throw new IllegalArgumentException("No successful samples");
		}
		return new Summary(percentile(sorted, 0.5), percentile(sorted, 0.25),
				percentile(sorted, 0.75));
	}

	private static long percentile(final List<Long> sorted, final double fraction) {
		final double position = fraction * (sorted.size() - 1);
		final int lower = (int) position;
		final int upper = (int) Math.ceil(position);
		return Math.round(sorted.get(lower)
				+ (sorted.get(upper) - sorted.get(lower)) * (position - lower));
	}

	static void write(final Configuration config, final Metadata metadata,
			final List<Sample> samples) throws IOException {
		final var raw = new StringBuilder(RAW_HEADER).append('\n');
		for (final var sample : samples) {
			raw.append(csv(metadata.runId(), metadata.started(), config.scenario(),
					config.metamodel().toString(), metadata.fingerprint(), config.packageName(),
					config.rootClass(), config.roots(), config.depth(), config.attributeMax(),
					config.containmentMax(), config.crossReferenceMax(), config.featureMapMax(),
					config.warmups(), config.repetitions(), config.preflight().name().toLowerCase(
							Locale.ROOT), sample.index(),
					sample.index() == 0 || !sample.status().equals("ok")
							? "" : sample.elapsedNanos(),
					sample.counts().roots(), sample.counts().objects(), sample.counts().resources(),
					sample.status(), sample.error(), metadata.javaVersion(), metadata.javaVm(),
					metadata.jvmArgs(), metadata.osName(), metadata.osArch(), metadata.cpuCount(),
					metadata.maxHeapBytes())).append('\n');
		}
		final var successful = samples.stream().filter(s -> s.index() > 0
				&& s.status().equals("ok")).toList();
		final String summary;
		if (successful.isEmpty()) {
			summary = csv(metadata.runId(), config.scenario(), 0, "", "", "",
					samples.isEmpty() ? "empty" : samples.getLast().status());
		} else {
			final var stats = summarize(successful);
			summary = csv(metadata.runId(), config.scenario(), successful.size(),
					stats.median(), stats.p25(), stats.p75(), "ok");
		}
		writeFile(config.rawPath(), raw.toString());
		writeFile(config.summaryPath(), SUMMARY_HEADER + "\n" + summary + "\n");
	}

	private static void writeFile(final Path path, final String value) throws IOException {
		final var parent = path.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		Files.writeString(path, value, StandardCharsets.UTF_8);
	}

	private static String csv(final Object... values) {
		final var result = new ArrayList<String>();
		for (final var value : values) {
			final String text = String.valueOf(value);
			final String quote = Character.toString(34);
			result.add(quote + text.replace(quote, quote + quote) + quote);
		}
		return String.join(",", result);
	}
}
