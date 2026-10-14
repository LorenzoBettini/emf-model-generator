package io.github.lorenzobettini.emfmodelgenerator.performance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

import io.github.lorenzobettini.emfmodelgenerator.EMFInstancePopulator;
import io.github.lorenzobettini.emfmodelgenerator.EMFModelGenerator;
import io.github.lorenzobettini.emfmodelgenerator.EMFResourceSetHelper;
import io.github.lorenzobettini.emfmodelgenerator.EMFUtils;

/**
 * Fork-only fixed logical model benchmark; clocks exclude checks and serialization.
 *
 * @author Lorenzo Bettini
 */
public final class ResourceLayoutBenchmark {
	private ResourceLayoutBenchmark() {
	}

	/**
	 * @author Lorenzo Bettini
	 */
	record Observation(ResourceSet set, EObject[] roots, long rootNanos, long setupNanos,
			long populationNanos) {
		long totalNanos() {
			return rootNanos + setupNanos + populationNanos;
		}
	}

	static int[] blockSizes(final int roots, final int resources) {
		if (roots < 1 || resources < 1 || resources > roots) {
			throw new IllegalArgumentException("Require 1 <= resources <= roots");
		}
		final var sizes = new int[resources];
		for (int i = 0; i < resources; i++) {
			sizes[i] = roots / resources + (i < roots % resources ? 1 : 0);
		}
		return sizes;
	}

	static Observation observe(final EClass rootClass, final int rootCount,
			final int resourceCount) {
		final var set = EMFResourceSetHelper.createResourceSet();
		set.getPackageRegistry().put(rootClass.getEPackage().getNsURI(), rootClass.getEPackage());
		final var populator = new EMFInstancePopulator();
		populator.setMaxDepth(1);
		populator.setAttributeDefaultMaxCount(1);
		populator.setContainmentReferenceDefaultMaxCount(1);
		populator.setCrossReferenceDefaultMaxCount(1);
		populator.setFeatureMapDefaultMaxCount(1);
		final var roots = new EObject[rootCount];
		long start = System.nanoTime();
		for (int i = 0; i < roots.length; i++) {
			roots[i] = EcoreUtil.create(rootClass);
		}
		final long rootNanos = System.nanoTime() - start;
		start = System.nanoTime();
		int next = 0;
		final var blocks = blockSizes(rootCount, resourceCount);
		for (int i = 0; i < resourceCount; i++) {
			final var resource = set.createResource(URI.createURI(
					"layout:/model-" + String.format("%05d", i) + ".xmi"));
			for (int j = 0; j < blocks[i]; j++) {
				resource.getContents().add(roots[next++]);
			}
		}
		final long setupNanos = System.nanoTime() - start;
		start = System.nanoTime();
		populator.populateEObjects(roots);
		final long populationNanos = System.nanoTime() - start;
		return new Observation(set, roots, rootNanos, setupNanos, populationNanos);
	}

	static long count(final ResourceSet set) {
		long count = 0;
		for (final var resource : set.getResources()) {
			final var contents = resource.getAllContents();
			while (contents.hasNext()) {
				contents.next();
				count++;
			}
		}
		return count;
	}

	static String candidateOrder(final Observation observation, final EClass candidateClass) {
		return candidateOrder(observation, EMFUtils.findAllAssignableInstances(
				observation.roots()[0], candidateClass));
	}

	private static String candidateOrder(final Observation observation,
			final List<EObject> candidates) {
		final var indices = new IdentityHashMap<EObject, Integer>();
		for (final var resource : observation.set().getResources()) {
			final var all = resource.getAllContents();
			while (all.hasNext()) {
				indices.put(all.next(), indices.size());
			}
		}
		final var builder = new StringBuilder();
		for (final var candidate : candidates) {
			final var index = indices.get(candidate);
			if (index == null) {
				throw new IllegalStateException("Candidate outside model");
			}
			builder.append(index).append(';');
		}
		return builder.toString();
	}

	static String hash(final String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	static String roundTrip(final Observation original, final EPackage pkg) throws IOException {
		final var bytes = new ArrayList<byte[]>();
		for (final var resource : original.set().getResources()) {
			final var stream = new ByteArrayOutputStream();
			resource.save(stream, null);
			bytes.add(stream.toByteArray());
		}
		final var reloaded = EMFResourceSetHelper.createResourceSet();
		reloaded.getPackageRegistry().put(pkg.getNsURI(), pkg);
		for (final var resource : original.set().getResources()) {
			reloaded.createResource(resource.getURI());
		}
		for (int i = 0; i < bytes.size(); i++) {
			reloaded.getResources().get(i).load(new ByteArrayInputStream(bytes.get(i)), null);
		}
		EcoreUtil.resolveAll(reloaded);
		if (count(reloaded) != count(original.set())) {
			throw new IllegalStateException("Round-trip EObject count changed");
		}
		final var validation = new EMFModelGenerator(reloaded).validate();
		if (!validation.isValid()) {
			throw new IllegalStateException("Round-trip validation: "
					+ validation.rejectedDiagnostics());
		}
		return GraphFingerprint.computeLogical(reloaded);
	}

	static void validate(final Observation observation) {
		final var validation = new EMFModelGenerator(observation.set()).validate();
		if (!validation.isValid()) {
			throw new IllegalStateException("In-memory validation: "
					+ validation.rejectedDiagnostics());
		}
	}

	private static String row(final String phase, final int index, final Observation observation,
			final long objects, final String fingerprint) {
		return String.join(",", phase, Integer.toString(index),
				Long.toString(observation.rootNanos()), Long.toString(observation.setupNanos()),
				Long.toString(observation.populationNanos()), Long.toString(observation.totalNanos()),
				Integer.toString(observation.roots().length), Long.toString(objects),
				Integer.toString(observation.set().getResources().size()), fingerprint, "ok", "");
	}

	public static void main(final String[] args) throws Exception {
		if (args.length != 8) {
			throw new IllegalArgumentException(
					"roots resources warmups repetitions max-eobjects raw preflight diagnostic");
		}
		final int roots = Integer.parseInt(args[0]);
		final int resources = Integer.parseInt(args[1]);
		final int warmups = Integer.parseInt(args[2]);
		final int repetitions = Integer.parseInt(args[3]);
		final long maxObjects = Long.parseLong(args[4]);
		blockSizes(roots, resources);
		if (warmups < 0 || repetitions < 1 || maxObjects < 1) {
			throw new IllegalArgumentException("Invalid warmups, repetitions or cap");
		}
		final var profile = ScenarioProfiles.get("extlibrary");
		final var loader = new EMFModelGenerator();
		final var packages = loader.loadEcoreModelPackages(profile.metamodel().toString());
		final var rootClass = EMFModelGeneratorPerformanceStatistics.selectRoot(packages,
				profile.packageName(), profile.rootClass());
		final var raw = Path.of(args[5]);
		final var preflight = Path.of(args[6]);
		final var diagnostic = Path.of(args[7]);
		Files.createDirectories(raw.getParent());
		Files.writeString(raw, "phase,index,root_ns,setup_ns,population_ns,total_ns,roots,eobjects,resources,fingerprint,status,error\n");
		System.out.println("JVM args: " + ManagementFactory.getRuntimeMXBean().getInputArguments());
		try {
			System.out.println("preflight start roots=" + roots + " resources=" + resources);
			final var checked = observe(rootClass, roots, resources);
			final long objects = count(checked.set());
			if (objects > maxObjects) {
				throw new IllegalStateException("EObject cap exceeded: " + objects + " > " + maxObjects);
			}
			validate(checked);
			final var fingerprint = GraphFingerprint.computeLogical(checked.set());
			final var reloaded = roundTrip(checked, rootClass.getEPackage());
			Files.writeString(preflight, "roots,resources,eobjects,fingerprint,roundtrip_fingerprint,roundtrip_equal,status\n"
					+ roots + "," + resources + "," + objects + "," + fingerprint + ","
					+ reloaded + "," + fingerprint.equals(reloaded) + ",ok\n");
			final var diagnosticRows = new StringBuilder(
					"class,candidates,order_sha256,elapsed_ns\n");
			for (final var name : List.of("Book", "Writer")) {
				final var candidateClass = (EClass) rootClass.getEPackage().getEClassifier(name);
				if (candidateClass == null) {
					throw new IllegalStateException("Missing diagnostic EClass " + name);
				}
				final long start = System.nanoTime();
				final var candidates = EMFUtils.findAllAssignableInstances(
						checked.roots()[0], candidateClass);
				final long elapsed = System.nanoTime() - start;
				final var order = candidateOrder(checked, candidates);
				diagnosticRows.append(name).append(',')
						.append(candidates.size()).append(',')
						.append(hash(order)).append(',').append(elapsed).append('\n');
			}
			Files.writeString(diagnostic, diagnosticRows);
			System.out.println("preflight done status=ok eobjects=" + objects
					+ " fingerprint=" + fingerprint);
			for (int i = 0; i < warmups + repetitions; i++) {
				final boolean measured = i >= warmups;
				final int index = measured ? i - warmups + 1 : i + 1;
				System.out.println((measured ? "measurement" : "warmup") + " " + index
						+ " start");
				final var observation = observe(rootClass, roots, resources);
				final long actual = count(observation.set());
				final var graph = GraphFingerprint.computeLogical(observation.set());
				if (actual != objects || !graph.equals(fingerprint)) {
					throw new IllegalStateException("Logical graph/count changed on execution " + i);
				}
				if (measured) {
					Files.writeString(raw, row("measurement", index, observation, actual, graph)
							+ "\n", java.nio.file.StandardOpenOption.APPEND);
				}
				System.out.println((measured ? "measurement" : "warmup") + " " + index
						+ " done status=ok eobjects=" + actual);
			}
		} finally {
			loader.unloadEcoreModels();
		}
	}
}
