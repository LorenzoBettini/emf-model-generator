package io.github.lorenzobettini.emfmodelgenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

/**
 * @author Lorenzo Bettini
 */
class LegacyRoundRobinEquivalenceTest {
	/**
	 * @author Lorenzo Bettini
	 */
	private static final class Lists extends EMFRoundRobinCandidateSelector<EClass, EClass, EObject> {
		private final Map<EClass, List<EObject>> values = new HashMap<>();

		@Override
		protected EClass keyFor(final EClass request) {
			return request;
		}

		@Override
		protected List<EObject> getOrCompute(final EObject context, final EClass key) {
			return values.getOrDefault(key, List.of());
		}
	}

	/**
	 * @author Lorenzo Bettini
	 */
	private static final class OldLists extends EMFRoundRobinCandidateSelector<EClass, EClass, EObject> {
		private final Map<EClass, List<EObject>> values;
		private final Map<EClass, Integer> indices = new HashMap<>();

		private OldLists(final Map<EClass, List<EObject>> values) {
			this.values = values;
		}

		@Override
		protected EClass keyFor(final EClass request) {
			return request;
		}

		@Override
		protected List<EObject> getOrCompute(final EObject context, final EClass key) {
			return values.getOrDefault(key, List.of());
		}

		@Override
		public EObject getNextCandidate(final EObject context, final EClass key) {
			final var candidates = getOrCompute(context, key);
			if (candidates.isEmpty()) {
				return null;
			}
			final int current = indices.getOrDefault(key, 0);
			final var result = candidates.get(current);
			indices.put(key, (current + 1) % candidates.size());
			return result;
		}

		@Override
		public void reset() {
			super.reset();
			indices.clear();
		}
	}

	private static <K extends ENamedElement, V extends EObject> void compare(
			final EMFCandidateSelectorStrategy<K, V> adopted,
			final EMFCandidateSelectorStrategy<K, V> legacy,
			final EObject context, final K key, final int count) {
		assertThat(adopted.hasCandidates(context, key))
				.isEqualTo(legacy.hasCandidates(context, key));
		for (int i = 0; i < count; i++) {
			assertThat(adopted.getNextCandidate(context, key))
					.as("retrieval %s", i).isSameAs(legacy.getNextCandidate(context, key));
		}
	}

	@Test
	void genericCursorMatchesBoxedIndexAcrossKeysSizesAndResets() throws IOException {
		final var generator = new EMFModelGenerator();
		try {
			final var ecore = generator.loadEcoreModel("src/test/resources/inputs/extlibrary.ecore");
			final var book = (EClass) ecore.getEClassifier("Book");
			final var person = (EClass) ecore.getEClassifier("Person");
			final var adopted = new Lists();
			final var legacy = new OldLists(adopted.values);
			final var context = EcoreUtil.create(book);
			compare(adopted, legacy, context, book, 2); // empty does not create a cursor
			adopted.values.put(book, List.of(context));
			compare(adopted, legacy, context, book, 3); // singleton and wrap
			final var second = EcoreUtil.create(book);
			final var third = EcoreUtil.create(book);
			adopted.values.put(book, List.of(context, second, third));
			adopted.values.put(person, List.of(EcoreUtil.create(person), EcoreUtil.create(person)));
			compare(adopted, legacy, context, book, 8);
			compare(adopted, legacy, context, person, 1);
			compare(adopted, legacy, context, book, 2);
			compare(adopted, legacy, context, person, 5);
			adopted.reset();
			legacy.reset();
			adopted.values.put(book, List.of(third, second));
			compare(adopted, legacy, context, book, 7);
			adopted.reset();
			legacy.reset();
			compare(adopted, legacy, context, book, 3);
		} finally {
			generator.unloadEcoreModels();
		}
	}

	@Test
	void undefinedListChangesAndNullRemainLegacyCompatible() throws IOException {
		final var generator = new EMFModelGenerator();
		try {
			final var ecore = generator.loadEcoreModel("src/test/resources/inputs/extlibrary.ecore");
			final var book = (EClass) ecore.getEClassifier("Book");
			final var context = EcoreUtil.create(book);
			final var adopted = new Lists();
			final var legacy = new OldLists(adopted.values);
			adopted.values.put(book, List.of(context, EcoreUtil.create(book),
					EcoreUtil.create(book)));
			compare(adopted, legacy, context, book, 2);
			adopted.values.put(book, List.of(context));
			assertThatThrownBy(() -> adopted.getNextCandidate(context, book))
					.isInstanceOf(IndexOutOfBoundsException.class);
			assertThatThrownBy(() -> legacy.getNextCandidate(context, book))
					.isInstanceOf(IndexOutOfBoundsException.class);
			adopted.reset();
			legacy.reset();
			compare(adopted, legacy, context, book, 3);
			adopted.values.put(book, null);
			assertThatThrownBy(() -> adopted.getNextCandidate(context, book))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> legacy.getNextCandidate(context, book))
					.isInstanceOf(NullPointerException.class);
		} finally {
			generator.unloadEcoreModels();
		}
	}

	@Test
	void everyDerivedSelectorUsesTheSameSequence() throws IOException {
		final var generator = new EMFModelGenerator();
		try {
			final var ecore = generator.loadEcoreModel("src/test/resources/inputs/extlibrary.ecore");
			final var book = (EClass) ecore.getEClassifier("Book");
			final var person = (EClass) ecore.getEClassifier("Person");
			final var context = EcoreUtil.create(book);
			final var resource = generator.getResourceSet()
					.createResource(URI.createURI("memory:/legacy-equivalence.xmi"));
			resource.getContents().add(context);
			resource.getContents().add(EcoreUtil.create(book));
			final var objects = new EMFRoundRobinEObjectCandidateSelector();
			final var oldObjects = new LegacyRoundRobinSelectors.EObjects();
			compare(objects, oldObjects, context, book, 7);
			compare(objects, oldObjects, context, person, 2); // empty key
			resource.getContents().add(EcoreUtil.create(book));
			objects.reset();
			oldObjects.reset();
			compare(objects, oldObjects, context, book, 9); // recomputed cache

			final var classes = new EMFRoundRobinEClassCandidateSelector();
			final var oldClasses = new LegacyRoundRobinSelectors.EClasses();
			compare(classes, oldClasses, context, person, 12);
			compare(classes, oldClasses, context, book, 3);
			classes.reset();
			oldClasses.reset();
			compare(classes, oldClasses, context, person, 5);

			final var literals = new EMFRoundRobinEEnumLiteralCandidateSelector();
			final var oldLiterals = new LegacyRoundRobinSelectors.EEnumLiterals();
			final var category = (EEnum) ecore.getEClassifier("BookCategory");
			compare(literals, oldLiterals, context, category, 10);
			literals.reset();
			oldLiterals.reset();
			compare(literals, oldLiterals, context, category, 4);
		} finally {
			generator.unloadEcoreModels();
		}

		final var featureGenerator = new EMFModelGenerator();
		try {
			final var ecore = featureGenerator.loadEcoreModel(
					"src/test/resources/inputs/featuremap-example.ecore");
			final var document = (EClass) ecore.getEClassifier("Document");
			final var group = (EAttribute) document.getEStructuralFeature("contentGroup");
			final var context = EcoreUtil.create(document);
			final var members = new EMFRoundRobinFeatureMapGroupMemberSelector();
			final var oldMembers = new LegacyRoundRobinSelectors.FeatureMapMembers();
			compare(members, oldMembers, context, group, 9);
			members.reset();
			oldMembers.reset();
			compare(members, oldMembers, context, group, 3);
		} finally {
			featureGenerator.unloadEcoreModels();
		}
	}
}
