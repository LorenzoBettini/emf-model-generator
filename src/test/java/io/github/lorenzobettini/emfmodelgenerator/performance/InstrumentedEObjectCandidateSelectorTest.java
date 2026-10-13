package io.github.lorenzobettini.emfmodelgenerator.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.junit.jupiter.api.Test;

import io.github.lorenzobettini.emfmodelgenerator.EMFModelGenerator;
import io.github.lorenzobettini.emfmodelgenerator.EMFRoundRobinEObjectCandidateSelector;

/**
 * @author Lorenzo Bettini
 */
class InstrumentedEObjectCandidateSelectorTest {

	@Test
	void cacheMissesAndResetPreserveRoundRobinOrder() throws Exception {
		final var loader = new EMFModelGenerator();
		try {
			final var root = root(loader, "src/test/resources/inputs/simplelibrary.ecore",
					"simplelibrary", "Library");
			final var generator = new EMFModelGenerator();
			generator.setNumberOfInstances(3);
			final var models = generator.generateFromSeveral(root);
			final var baseline = new EMFRoundRobinEObjectCandidateSelector();
			final var instrumented = new InstrumentedEObjectCandidateSelector();
			final var expected = List.of(baseline.getNextCandidate(models.getFirst(), root),
					baseline.getNextCandidate(models.getFirst(), root),
					baseline.getNextCandidate(models.getFirst(), root));
			final var actual = List.of(instrumented.getNextCandidate(models.getFirst(), root),
					instrumented.getNextCandidate(models.getFirst(), root),
					instrumented.getNextCandidate(models.getFirst(), root));
			assertThat(actual).containsExactlyElementsOf(expected);
			assertThat(actual.get(1)).isNotSameAs(actual.getFirst());
			assertThat(instrumented.snapshot().computeCalls()).isEqualTo(1);
			assertThat(instrumented.snapshot().candidateEntries()).isEqualTo(3);
			instrumented.reset();
			baseline.reset();
			assertThat(instrumented.getNextCandidate(models.getFirst(), root))
					.isSameAs(baseline.getNextCandidate(models.getFirst(), root));
			assertThat(instrumented.snapshot().computeCalls()).isEqualTo(2);
		} finally {
			loader.unloadEcoreModels();
		}
	}

	@Test
	void generatedStructureAndCrossReferencesMatchBaseline() throws Exception {
		for (final var profileId : List.of("extlibrary", "ant")) {
			final var profile = ScenarioProfiles.get(profileId);
			final var loader = new EMFModelGenerator();
			try {
				final var root = root(loader, profile.metamodel().toString(),
						profile.packageName(), profile.rootClass());
				assertThat(signature(generate(profile, root, null))).as(profileId)
						.isEqualTo(signature(generate(profile, root,
								new InstrumentedEObjectCandidateSelector())));
			} finally {
				loader.unloadEcoreModels();
			}
		}
	}

	private static EClass root(final EMFModelGenerator loader, final String path,
			final String packageName, final String className) throws Exception {
		return EMFModelGeneratorPerformanceStatistics.selectRoot(
				loader.loadEcoreModelPackages(path), packageName, className);
	}

	private static List<EObject> generate(final ScenarioProfiles.Profile profile,
			final EClass root, final InstrumentedEObjectCandidateSelector selector) {
		final var generator = new EMFModelGenerator();
		generator.setNumberOfInstances(2);
		final var populator = generator.getInstancePopulator();
		populator.setMaxDepth(profile.defaultDepth());
		populator.setAttributeDefaultMaxCount(profile.attributeMax());
		populator.setContainmentReferenceDefaultMaxCount(profile.containmentMax());
		populator.setCrossReferenceDefaultMaxCount(profile.crossReferenceMax());
		populator.setFeatureMapDefaultMaxCount(profile.featureMapMax());
		if (selector != null) {
			populator.setCrossReferenceCandidateSelectorStrategy(selector);
		}
		return generator.generateFromSeveral(root);
	}

	private static List<String> signature(final List<EObject> roots) {
		final var objects = new ArrayList<EObject>();
		for (final var root : roots) {
			objects.add(root);
			final var contents = root.eAllContents();
			while (contents.hasNext()) {
				objects.add(contents.next());
			}
		}
		final var ids = new IdentityHashMap<EObject, Integer>();
		for (int i = 0; i < objects.size(); i++) {
			ids.put(objects.get(i), i);
		}
		final var lines = new ArrayList<String>();
		for (final var object : objects) {
			final var line = new StringBuilder(object.eClass().getName());
			for (final var feature : object.eClass().getEAllStructuralFeatures()) {
				line.append('|').append(feature.getName()).append('=');
				if (!object.eIsSet(feature)) {
					line.append("<unset>");
					continue;
				}
				final var value = object.eGet(feature);
				if (feature.isMany()) {
					line.append(((List<?>) value).stream()
							.map(item -> value(item, feature instanceof EAttribute, ids)).toList());
				} else {
					line.append(value(value, feature instanceof EAttribute, ids));
				}
			}
			lines.add(line.toString());
		}
		return lines;
	}

	private static String value(final Object item, final boolean attribute,
			final IdentityHashMap<EObject, Integer> ids) {
		if (item instanceof FeatureMap.Entry entry) {
			return entry.getEStructuralFeature().getName() + '='
					+ value(entry.getValue(), false, ids);
		}
		return attribute ? String.valueOf(item) : String.valueOf(ids.get(item));
	}
}
