package io.github.lorenzobettini.emfmodelgenerator.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.TreeMap;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.Test;

import io.github.lorenzobettini.emfmodelgenerator.EMFModelGenerator;

/**
 * @author Lorenzo Bettini
 */
class ScenarioProfilesTest {

	@Test
	void everyProfileSelectsItsDeclaredConcreteRoot() throws Exception {
		for (final var profile : ScenarioProfiles.all()) {
			final var loader = new EMFModelGenerator();
			try {
				final var packages = loader.loadEcoreModelPackages(
						profile.metamodel().toString());
				final var root = EMFModelGeneratorPerformanceStatistics.selectRoot(packages,
						profile.packageName(), profile.rootClass());
				assertThat(root.getName()).isEqualTo(profile.rootClass());
				assertThat(profile.roots()).isSorted().doesNotHaveDuplicates();
				assertThat(profile.depths()).isSorted().doesNotHaveDuplicates();
			} finally {
				loader.unloadEcoreModels();
			}
		}
	}

	@Test
	void namedProfileLocksMetamodelAndPilotBounds() {
		final var config = EMFModelGeneratorPerformanceStatistics.parse(
				"--profile", "ant", "--roots", "5", "--depth", "2");
		assertThat(config.scenario()).isEqualTo("ant");
		assertThat(config.metamodel().toString()).endsWith("Ant.ecore");
		assertThat(config.packageName()).isEqualTo("Ant");
		assertThat(config.rootClass()).isEqualTo("Project");
		assertThat(config.containmentMax()).isEqualTo(1);
		assertThat(config.preflight()).isEqualTo(
				EMFModelGeneratorPerformanceStatistics.Preflight.ROUNDTRIP);
		assertThatThrownBy(() -> EMFModelGeneratorPerformanceStatistics.parse(
				"--profile", "ant", "--roots", "100"))
				.hasMessageContaining("Outside pilot bounds");
		assertThatThrownBy(() -> EMFModelGeneratorPerformanceStatistics.parse(
				"--profile", "bpmn-bounded", "--depth", "5"))
				.hasMessageContaining("Outside pilot bounds");
		assertThatThrownBy(() -> EMFModelGeneratorPerformanceStatistics.parse(
				"--profile", "ant", "--metamodel", "other.ecore"))
				.hasMessageContaining("cannot override");
		assertThatThrownBy(() -> EMFModelGeneratorPerformanceStatistics.parse(
				"--profile", "ant", "--preflight", "none"))
				.hasMessageContaining("require validation preflight");
	}

	@Test
	void independentRunsKeepTheSameContainedEClassMix() throws Exception {
		for (final var profile : ScenarioProfiles.all()) {
			final var loader = new EMFModelGenerator();
			try {
				final var packages = loader.loadEcoreModelPackages(profile.metamodel().toString());
				final var root = EMFModelGeneratorPerformanceStatistics.selectRoot(packages,
						profile.packageName(), profile.rootClass());
				assertThat(classMix(profile, root)).as(profile.id())
						.isEqualTo(classMix(profile, root));
			} finally {
				loader.unloadEcoreModels();
			}
		}
	}

	private static Map<String, Integer> classMix(final ScenarioProfiles.Profile profile,
			final EClass root) {
		final var generator = new EMFModelGenerator();
		generator.setNumberOfInstances(5);
		final var populator = generator.getInstancePopulator();
		populator.setMaxDepth(profile.defaultDepth());
		populator.setAttributeDefaultMaxCount(profile.attributeMax());
		populator.setContainmentReferenceDefaultMaxCount(profile.containmentMax());
		populator.setCrossReferenceDefaultMaxCount(profile.crossReferenceMax());
		populator.setFeatureMapDefaultMaxCount(profile.featureMapMax());
		final var counts = new TreeMap<String, Integer>();
		for (final var model : generator.generateFromSeveral(root)) {
			addClass(counts, model);
			final var contents = model.eAllContents();
			while (contents.hasNext()) {
				addClass(counts, contents.next());
			}
		}
		return counts;
	}

	private static void addClass(final Map<String, Integer> counts, final EObject object) {
		final var type = object.eClass();
		counts.merge(type.getEPackage().getName() + "::" + type.getName(), 1, Integer::sum);
	}
}
