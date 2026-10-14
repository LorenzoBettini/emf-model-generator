package io.github.lorenzobettini.emfmodelgenerator.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.junit.jupiter.api.Test;

import io.github.lorenzobettini.emfmodelgenerator.EMFModelGenerator;
import io.github.lorenzobettini.emfmodelgenerator.EMFUtils;

/**
 * @author Lorenzo Bettini
 */
class ResourceLayoutBenchmarkTest {
	@Test
	void partitionsUnevenRootCountsIntoOrderedNonemptyBlocks() {
		assertThat(ResourceLayoutBenchmark.blockSizes(7, 3)).containsExactly(3, 2, 2);
		assertThat(ResourceLayoutBenchmark.blockSizes(5, 5)).containsExactly(1, 1, 1, 1, 1);
		assertThatThrownBy(() -> ResourceLayoutBenchmark.blockSizes(3, 4))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void preservesLogicalGraphAndCandidateOrderAcrossLayouts() throws IOException {
		final var loader = new EMFModelGenerator();
		try {
			final var profile = ScenarioProfiles.get("extlibrary");
			final var packages = loader.loadEcoreModelPackages(profile.metamodel().toString());
			final var root = EMFModelGeneratorPerformanceStatistics.selectRoot(packages,
					profile.packageName(), profile.rootClass());
			final var book = (EClass) root.getEPackage().getEClassifier("Book");
			final var one = ResourceLayoutBenchmark.observe(root, 5, 1);
			final var uneven = ResourceLayoutBenchmark.observe(root, 5, 3);
			final var separate = ResourceLayoutBenchmark.observe(root, 5, 5);
			final var expected = GraphFingerprint.computeLogical(one.set());
			final var candidates = ResourceLayoutBenchmark.candidateOrder(one, book);
			assertThat(EMFUtils.findAllAssignableInstances(EcoreUtil.create(root), book))
					.isEmpty();
			final var reloaded = ResourceLayoutBenchmark.roundTrip(one, root.getEPackage());
			assertThat(candidates.split(";")).hasSizeGreaterThan(1);
			assertThat(reloaded).isNotEqualTo(expected);
			for (final var observation : new ResourceLayoutBenchmark.Observation[] {
					one, uneven, separate }) {
				ResourceLayoutBenchmark.validate(observation);
				assertThat(ResourceLayoutBenchmark.count(observation.set())).isEqualTo(20);
				assertThat(GraphFingerprint.computeLogical(observation.set())).isEqualTo(expected);
				assertThat(ResourceLayoutBenchmark.candidateOrder(observation, book))
						.isEqualTo(candidates);
				assertThat(ResourceLayoutBenchmark.roundTrip(observation, root.getEPackage()))
						.isEqualTo(reloaded);
			}
			assertThat(uneven.set().getResources().stream()
					.map(resource -> resource.getContents().size())).containsExactly(2, 2, 1);
			uneven.set().getResourceFactoryRegistry().getExtensionToFactoryMap()
					.put("ecore", new EcoreResourceFactoryImpl());
			uneven.set().getResource(URI.createFileURI(profile.metamodel()
					.toAbsolutePath().toString()), true);
			assertThat(ResourceLayoutBenchmark.candidateOrder(uneven, book))
					.isEqualTo(candidates);
		} finally {
			loader.unloadEcoreModels();
		}
	}
}
