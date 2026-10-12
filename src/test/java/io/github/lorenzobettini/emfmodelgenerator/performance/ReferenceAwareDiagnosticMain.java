package io.github.lorenzobettini.emfmodelgenerator.performance;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.emf.ecore.EPackage;

import io.github.lorenzobettini.emfmodelgenerator.EMFModelGenerator;
import io.github.lorenzobettini.emfmodelgenerator.EMFReferenceAwareEObjectCandidateSelector;

/** Separate instrumented mechanism probe; never use its elapsed time as A/B timing. */
public final class ReferenceAwareDiagnosticMain {
	private ReferenceAwareDiagnosticMain() {
	}

	public static void main(final String[] args) throws Exception {
		if (args.length != 4 || (!args[2].equals("baseline") && !args[2].equals("reference-aware"))) {
			throw new IllegalArgumentException("scenario roots baseline|reference-aware output.csv");
		}
		final var profile = ScenarioProfiles.get(args[0]);
		final int roots = Integer.parseInt(args[1]);
		final var generator = new EMFModelGenerator();
		try {
			final var packages = generator.loadEcoreModelPackages(profile.metamodel().toString());
			final var root = EMFModelGeneratorPerformanceStatistics.selectRoot(packages,
					profile.packageName(), profile.rootClass());
			generator.setNumberOfInstances(roots);
			final var populator = generator.getInstancePopulator();
			final var diagnostics = new EMFReferenceAwareEObjectCandidateSelector.Diagnostics();
			final var selector = new EMFReferenceAwareEObjectCandidateSelector(diagnostics);
			final ReferenceAwareBaselineProbe baseline;
			if (args[2].equals("baseline")) {
				baseline = new ReferenceAwareBaselineProbe();
				populator.setCrossReferenceSetter(baseline);
			} else {
				baseline = null;
				populator.setCrossReferenceCandidateSelectorStrategy(selector);
			}
			populator.setMaxDepth(profile.defaultDepth());
			populator.setAttributeDefaultMaxCount(1);
			populator.setContainmentReferenceDefaultMaxCount(1);
			populator.setCrossReferenceDefaultMaxCount(1);
			populator.setFeatureMapDefaultMaxCount(1);
			final var generated = generator.generateFromSeveral(root);
			final var fingerprint = GraphFingerprint.compute(generator.getResourceSet());
			final var validation = profile.preflight()
					== EMFModelGeneratorPerformanceStatistics.Preflight.ROUNDTRIP
						? generator.saveAndValidateRoundTrip() : generator.validate();
			if (!validation.isValid()) {
				throw new IllegalStateException("Diagnostic model failed validation: "
						+ validation.rejectedDiagnostics());
			}
			long objects = 0;
			long resources = 0;
			for (final var resource : generator.getResourceSet().getResources()) {
				if (!resource.getContents().isEmpty()
						&& resource.getContents().getFirst() instanceof EPackage) {
					continue;
				}
				resources++;
				final var iterator = resource.getAllContents();
				while (iterator.hasNext()) {
					iterator.next();
					objects++;
				}
			}
			final long attempts = baseline == null ? diagnostics.attempts()
					: baseline.attempts();
			final long rejected = baseline == null ? diagnostics.oppositeRejections()
					: baseline.oppositeRejections();
			final var header = "scenario,roots,mode,eobjects,resources,fingerprint,preflight,attempts,"
					+ "opposite_rejections,retirements,pool_entries_built,active_indices,"
					+ "estimated_pool_bytes,pool_build_ns,selection_ns\n";
			final var row = String.join(",", args[0], Integer.toString(generated.size()), args[2],
					Long.toString(objects), Long.toString(resources), fingerprint,
					profile.preflight().name().toLowerCase(), Long.toString(attempts),
					Long.toString(rejected), Long.toString(diagnostics.retirements()),
					Long.toString(diagnostics.poolEntriesBuilt()),
					Long.toString(selector.activeIndexCount()),
					Long.toString(diagnostics.poolEntriesBuilt() * 64),
					Long.toString(diagnostics.poolBuildNanos()),
					Long.toString(diagnostics.selectionNanos())) + "\n";
			Files.writeString(Path.of(args[3]), header + row);
		} finally {
			generator.unloadEcoreModels();
		}
	}
}
