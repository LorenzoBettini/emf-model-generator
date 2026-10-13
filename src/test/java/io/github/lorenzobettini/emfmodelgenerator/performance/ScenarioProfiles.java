package io.github.lorenzobettini.emfmodelgenerator.performance;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Bounded, explicit-root configurations for short calibration pilots. */
/**
 * @author Lorenzo Bettini
 */
final class ScenarioProfiles {

	/**
	 * @author Lorenzo Bettini
	 */
	record Profile(String id, Path metamodel, String packageName, String rootClass,
			List<Integer> roots, List<Integer> depths, int defaultDepth, int attributeMax,
			int containmentMax, int crossReferenceMax, int featureMapMax,
			EMFModelGeneratorPerformanceStatistics.Preflight preflight) {
		Profile {
			roots = List.copyOf(roots);
			depths = List.copyOf(depths);
			if (roots.isEmpty() || depths.isEmpty() || !depths.contains(defaultDepth)) {
				throw new IllegalArgumentException("Profile needs roots and a default allowed depth");
			}
		}
	}

	private static final String EXTERNAL = "src/test/resources/external-metamodels/";
	private static final Map<String, Profile> PROFILES = List.of(
			new Profile("extlibrary", Path.of("src/test/resources/inputs/extlibrary.ecore"),
					"extlibrary", "Library", List.of(1, 2, 5, 10, 20), List.of(1, 2),
					1, 1, 1, 1, 1, EMFModelGeneratorPerformanceStatistics.Preflight.ROUNDTRIP),
			new Profile("bibtex", Path.of(EXTERNAL + "BibTeX.ecore"), "BIBTEX", "Bibtex",
					List.of(1, 2, 5, 10, 20), List.of(1, 2), 2, 1, 1, 1, 1,
					EMFModelGeneratorPerformanceStatistics.Preflight.ROUNDTRIP),
			new Profile("ant", Path.of(EXTERNAL + "Ant.ecore"), "Ant", "Project",
					List.of(1, 2, 5, 10, 20), List.of(2), 2, 1, 1, 1, 1,
					EMFModelGeneratorPerformanceStatistics.Preflight.ROUNDTRIP),
			new Profile("plugin-eclipse", Path.of(EXTERNAL + "PluginEclipse.ecore"),
					"PluginEclipse", "Eclipse", List.of(1, 2, 5, 10, 20), List.of(4),
					4, 1, 1, 1, 1,
					EMFModelGeneratorPerformanceStatistics.Preflight.ROUNDTRIP),
			new Profile("rss", Path.of(EXTERNAL + "RSS-2.0.ecore"), "RSS", "RSS",
					List.of(1, 2, 5, 10, 20), List.of(2, 3), 3, 1, 1, 1, 1,
					EMFModelGeneratorPerformanceStatistics.Preflight.ROUNDTRIP),
			new Profile("bpmn-bounded", Path.of(EXTERNAL + "BPMN.ecore"), "bpmn",
					"BpmnDiagram", List.of(1, 2, 3, 5), List.of(1, 2), 1, 1, 1, 1, 1,
					EMFModelGeneratorPerformanceStatistics.Preflight.VALIDATE))
			.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Profile::id, p -> p));

	private ScenarioProfiles() {
	}

	static Profile get(final String id) {
		final var profile = PROFILES.get(id);
		if (profile == null) {
			throw new IllegalArgumentException("Unknown profile: " + id + "; choose from "
					+ PROFILES.keySet());
		}
		return profile;
	}

	static List<Profile> all() {
		return PROFILES.values().stream().sorted((a, b) -> a.id().compareTo(b.id())).toList();
	}
}
