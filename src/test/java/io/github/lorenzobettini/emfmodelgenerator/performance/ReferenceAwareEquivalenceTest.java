package io.github.lorenzobettini.emfmodelgenerator.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import io.github.lorenzobettini.emfmodelgenerator.EMFModelGenerator;
import io.github.lorenzobettini.emfmodelgenerator.EMFRoundRobinReferenceAwareEObjectCandidateSelector;

/**
 * @author Lorenzo Bettini
 */
class ReferenceAwareEquivalenceTest {
	@Test
	void preservesBoundedExternalGraphFingerprints() throws IOException {
		for (final var id : new String[] {"plugin-eclipse", "ant", "extlibrary",
				"bpmn-bounded"}) {
			final var profile = ScenarioProfiles.get(id);
			final var baseline = fingerprint(profile, false);
			assertThat(fingerprint(profile, true)).as(id).isEqualTo(baseline);
		}
	}

	private String fingerprint(final ScenarioProfiles.Profile profile, final boolean aware)
			throws IOException {
		final var generator = new EMFModelGenerator();
		try {
			final var packages = generator.loadEcoreModelPackages(profile.metamodel().toString());
			final var root = EMFModelGeneratorPerformanceStatistics.selectRoot(packages,
					profile.packageName(), profile.rootClass());
			generator.setNumberOfInstances(2);
			final var populator = generator.getInstancePopulator();
			populator.setMaxDepth(profile.defaultDepth());
			populator.setAttributeDefaultMaxCount(1);
			populator.setContainmentReferenceDefaultMaxCount(1);
			populator.setCrossReferenceDefaultMaxCount(1);
			populator.setFeatureMapDefaultMaxCount(1);
			if (aware) {
				populator.setCrossReferenceCandidateSelectorStrategy(
						new EMFRoundRobinReferenceAwareEObjectCandidateSelector());
			}
			generator.generateFromSeveral(root);
			final var fingerprint = GraphFingerprint.compute(generator.getResourceSet());
			final var validation = profile.preflight()
					== EMFModelGeneratorPerformanceStatistics.Preflight.ROUNDTRIP
						? generator.saveAndValidateRoundTrip() : generator.validate();
			assertThat(validation.isValid())
					.as("%s aware=%s: %s", profile.id(), aware,
							validation.rejectedDiagnostics()).isTrue();
			return fingerprint;
		} finally {
			generator.unloadEcoreModels();
		}
	}
}
