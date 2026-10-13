package io.github.lorenzobettini.emfmodelgenerator.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceSetter;
import io.github.lorenzobettini.emfmodelgenerator.EMFCandidateSelectorStrategy;
import io.github.lorenzobettini.emfmodelgenerator.EMFModelGenerator;
import io.github.lorenzobettini.emfmodelgenerator.EMFRoundRobinEObjectCandidateSelector;
import io.github.lorenzobettini.emfmodelgenerator.LegacyRoundRobinSelectors;

/**
 * @author Lorenzo Bettini
 */
class ExperimentalCursorSelectorTest {
	private EMFModelGenerator generator;

	/**
	 * @author Lorenzo Bettini
	 */
	private static final class CountingSelector
			implements io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceCandidateSelectorStrategy {
		private final io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceCandidateSelectorStrategy delegate;
		private int calls;

		private CountingSelector(final io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceCandidateSelectorStrategy delegate) {
			this.delegate = delegate;
		}

		@Override
		public EObject selectCandidate(final EObject owner, final EReference reference,
				final java.util.function.Function<EObject, CandidateDecision> evaluator) {
			return delegate.selectCandidate(owner, reference, candidate -> {
				calls++;
				return evaluator.apply(candidate);
			});
		}

		@Override
		public void reset() {
			delegate.reset();
		}
	}

	@AfterEach
	void cleanup() {
		if (generator != null) {
			generator.unloadEcoreModels();
		}
	}

	@Test
	void preservesGeneratedGraphsIncludingUniqueListsAndFeatureMaps() throws IOException {
		for (final var id : new String[] {"plugin-eclipse", "ant", "extlibrary",
				"bpmn-bounded"}) {
			final var profile = ScenarioProfiles.get(id);
			final var baseline = generateFingerprint(profile, false);
			final var variant = generateFingerprint(profile, true);
			assertThat(variant).as(id).isEqualTo(baseline);
		}
	}

	private String generateFingerprint(final ScenarioProfiles.Profile profile,
			final boolean experimental) throws IOException {
		final var modelGenerator = new EMFModelGenerator();
		try {
			final var packages = modelGenerator.loadEcoreModelPackages(profile.metamodel().toString());
			final var root = EMFModelGeneratorPerformanceStatistics.selectRoot(packages,
					profile.packageName(), profile.rootClass());
			modelGenerator.setNumberOfInstances(2);
			final var populator = modelGenerator.getInstancePopulator();
			populator.setMaxDepth(profile.defaultDepth());
			populator.setAttributeDefaultMaxCount(1);
			populator.setContainmentReferenceDefaultMaxCount(1);
			populator.setCrossReferenceDefaultMaxCount(1);
			populator.setFeatureMapDefaultMaxCount(1);
			if (experimental) {
				populator.setCrossReferenceCandidateSelectorStrategy(
						new LegacyRoundRobinSelectors.EObjects());
			}
			modelGenerator.generateFromSeveral(root);
			final var fingerprint = GraphFingerprint.compute(modelGenerator.getResourceSet());
			final var validation = profile.preflight()
					== EMFModelGeneratorPerformanceStatistics.Preflight.ROUNDTRIP
						? modelGenerator.saveAndValidateRoundTrip() : modelGenerator.validate();
			assertThat(validation.isValid()).as(profile.id()).isTrue();
			return fingerprint;
		} finally {
			modelGenerator.unloadEcoreModels();
		}
	}

	@Test
	void preservesSelfPolicyCallsAndExplicitCustomSelector() throws IOException {
		generator = new EMFModelGenerator();
		final var ecore = generator.loadEcoreModel("src/test/resources/inputs/extlibrary.ecore");
		final var employee = (EClass) ecore.getEClassifier("Employee");
		final var manager = (EReference) employee.getEStructuralFeature("manager");
		final var resource = generator.getResourceSet().createResource(URI.createURI("memory:/self.xmi"));
		final var owner = EcoreUtil.create(employee);
		final var other = EcoreUtil.create(employee);
		resource.getContents().add(owner);
		resource.getContents().add(other);
		final var baseline = new EMFCrossReferenceSetter();
		final var variant = new EMFCrossReferenceSetter();
		baseline.setCandidateSelectorStrategy(new LegacyRoundRobinSelectors.EObjects());
		final int[] baselineCalls = {0};
		final int[] variantCalls = {0};
		baseline.setSelfReferencePolicy((ignored, reference) -> ++baselineCalls[0] > 1);
		variant.setSelfReferencePolicy((ignored, reference) -> ++variantCalls[0] > 1);
		for (int i = 0; i < 4; i++) {
			assertThat(variant.selectValue(owner, manager))
					.isSameAs(baseline.selectValue(owner, manager));
		}
		assertThat(variantCalls[0]).isEqualTo(baselineCalls[0]);
		var custom = new io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceCandidateSelectorStrategy() {
			@Override
			public EObject selectCandidate(final EObject owner, final EReference reference,
					final java.util.function.Function<EObject, CandidateDecision> evaluator) {
				return evaluator.apply(other) == CandidateDecision.ACCEPT ? other : null;
			}
			@Override
			public void reset() { }
		};
		baseline.setCandidateSelectorStrategy(custom);
		variant.setCandidateSelectorStrategy(custom);
		assertThat(variant.selectValue(owner, manager))
				.isSameAs(baseline.selectValue(owner, manager)).isSameAs(other);
	}

	@Test
	void preservesCursorAndLiveOppositeEligibility() throws IOException {
		generator = new EMFModelGenerator();
		final var ecore = generator.loadEcoreModel(
				"src/test/resources/external-metamodels/PluginEclipse.ecore");
		final var packageClass = (EClass) ecore.getEClassifier("Package");
		final var exportClass = (EClass) ecore.getEClassifier("ExportedPackage");
		final var reference = (EReference) exportClass.getEStructuralFeature("package");
		final var opposite = reference.getEOpposite();
		final var resource = generator.getResourceSet()
				.createResource(URI.createURI("memory:/cursor.xmi"));
		final var candidates = new ArrayList<EObject>();
		for (int i = 0; i < 3; i++) {
			final var candidate = EcoreUtil.create(packageClass);
			resource.getContents().add(candidate);
			candidates.add(candidate);
		}
		final var owner = EcoreUtil.create(exportClass);
		resource.getContents().add(owner);
		final var baselineSelector = new LegacyRoundRobinSelectors.EObjects();
		final var variantSelector = new EMFRoundRobinEObjectCandidateSelector();
		final var baselineCounting = new CountingSelector(baselineSelector);
		final var variantCounting = new CountingSelector(variantSelector);
		final var baseline = new EMFCrossReferenceSetter();
		final var variant = new EMFCrossReferenceSetter();
		baseline.setCandidateSelectorStrategy(baselineCounting);
		variant.setCandidateSelectorStrategy(variantCounting);

		for (int i = 0; i < 7; i++) {
			assertThat(variant.selectValue(owner, reference))
					.isSameAs(baseline.selectValue(owner, reference));
			assertThat(variantCounting.calls).isEqualTo(baselineCounting.calls);
		}
		final var exports = new ArrayList<EObject>();
		for (final var candidate : candidates) {
			final var exported = EcoreUtil.create(exportClass);
			candidate.eSet(opposite, exported);
			exports.add(exported);
		}
		for (int i = 0; i < 3; i++) {
			assertThat(variant.selectValue(owner, reference)).isNull();
			assertThat(baseline.selectValue(owner, reference)).isNull();
			assertThat(variantCounting.calls).isEqualTo(baselineCounting.calls);
		}
		candidates.get(1).eUnset(opposite);
		assertThat(variant.selectValue(owner, reference))
				.isSameAs(baseline.selectValue(owner, reference)).isSameAs(candidates.get(1));
		assertThat(variantCounting.calls).isEqualTo(baselineCounting.calls);
		exports.get(0).eSet(reference, candidates.get(1));
		assertThat(variant.selectValue(owner, reference))
				.isSameAs(baseline.selectValue(owner, reference)).isSameAs(candidates.get(0));
		assertThat(variantCounting.calls).isEqualTo(baselineCounting.calls);
		final int[] baselineFunctions = {0};
		final int[] variantFunctions = {0};
		baseline.setFunctionFor(reference, ignored -> {
			baselineFunctions[0]++;
			return candidates.get(2);
		});
		variant.setFunctionFor(reference, ignored -> {
			variantFunctions[0]++;
			return candidates.get(2);
		});
		assertThat(variant.selectValue(owner, reference))
				.isSameAs(baseline.selectValue(owner, reference));
		assertThat(variantFunctions[0]).isEqualTo(baselineFunctions[0]);
		assertThat(variantCounting.calls).isEqualTo(baselineCounting.calls);
		baseline.reset();
		variant.reset();
		assertThat(variantSelector.getNextCandidate(owner, reference))
				.isSameAs(baselineSelector.getNextCandidate(owner, reference));
		resource.getContents().add(EcoreUtil.create(packageClass));
		baseline.reset();
		variant.reset();
		for (int i = 0; i < 8; i++) {
			assertThat(variantSelector.getNextCandidate(owner, reference))
					.isSameAs(baselineSelector.getNextCandidate(owner, reference));
		}
	}
}
