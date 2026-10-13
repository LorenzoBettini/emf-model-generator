package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createInstance;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResource;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResourceSet;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.loadEPackage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceCandidateSelectorStrategy.CandidateDecision;

/**
 *
 * @author Lorenzo Bettini
 */
class EMFRoundRobinReferenceAwareEObjectCandidateSelectorTest {
	private EMFCrossReferenceCandidateSelectorStrategy recordingSelector(
			final EObject candidate, final AtomicReference<CandidateDecision> observed) {
		return new EMFCrossReferenceCandidateSelectorStrategy() {
			@Override
			public EObject selectCandidate(final EObject owner, final EReference reference,
					final java.util.function.Function<EObject, CandidateDecision> evaluator) {
				observed.set(evaluator.apply(candidate));
				return null;
			}

			@Override
			public void reset() {
				// No state to reset in this recording selector
			}
		};
	}

	@Test
	void evaluatorReportsExactOwnerSpecificDecisionsToNominalStrategy() {
		final var graph = loadEPackage("inputs/graph.ecore");
		final var node = createInstance(graph, "Node");
		final var outgoing = (EReference) node.eClass().getEStructuralFeature("outgoing");
		final var observed = new AtomicReference<CandidateDecision>();
		final var setter = new EMFCrossReferenceSetter();
		setter.setCandidateSelectorStrategy(recordingSelector(node, observed));
		assertThat(setter.selectValue(node, outgoing)).isNull();
		assertThat(observed.get()).isEqualTo(CandidateDecision.SKIP);

		final var standalones = loadEPackage("inputs/standalone-references.ecore");
		final var owner = createInstance(standalones, "Document");
		final var related = createInstance(standalones, "RelatedDocument");
		final var relatedDocs = (EReference) owner.eClass()
				.getEStructuralFeature("relatedDocs");
		EMFUtils.getAsEObjectsList(owner, relatedDocs).add(related);
		setter.setCandidateSelectorStrategy(recordingSelector(related, observed));
		assertThat(setter.selectValue(owner, relatedDocs)).isNull();
		assertThat(observed.get()).isEqualTo(CandidateDecision.SKIP);

		final var opposites = loadEPackage("inputs/references-opposite.ecore");
		final var occupied = createInstance(opposites, "Library");
		final var another = createInstance(opposites, "Library");
		final var book = createInstance(opposites, "Book");
		final var books = (EReference) occupied.eClass().getEStructuralFeature("books");
		EMFUtils.getAsEObjectsList(occupied, books).add(book);
		setter.setCandidateSelectorStrategy(recordingSelector(book, observed));
		assertThat(setter.selectValue(another, books)).isNull();
		assertThat(observed.get())
				.isEqualTo(CandidateDecision.OPPOSITE_CAPACITY_EXHAUSTED);
	}
	@Test
	void adversarialCustomFunctionIsCalledOnceAndFallsBackWithinOneBoundedTraversal() {
		final var model = loadEPackage("inputs/standalone-references.ecore");
		for (final java.util.function.Supplier<EMFCrossReferenceCandidateSelectorStrategy> factory :
				List.<java.util.function.Supplier<EMFCrossReferenceCandidateSelectorStrategy>>of(
						EMFRoundRobinEObjectCandidateSelector::new,
						EMFRoundRobinReferenceAwareEObjectCandidateSelector::new)) {
			final var selector = factory.get();
			final var set = createResourceSet();
			final var resource = createResource(set, "custom-unique.xmi");
			final var owner = createInstance(model, "Document");
			final var first = createInstance(model, "RelatedDocument");
			final var second = createInstance(model, "RelatedDocument");
			final var fallback = createInstance(model, "RelatedDocument");
			resource.getContents().addAll(List.of(owner, first, second, fallback));
			final var reference = (EReference) owner.eClass()
					.getEStructuralFeature("relatedDocs");
			final var list = EMFUtils.getAsEObjectsList(owner, reference);
			list.addAll(List.of(first, second));
			final var setter = new EMFCrossReferenceSetter();
			setter.setCandidateSelectorStrategy(selector);
			setter.setDefaultMaxCount(3);
			final var calls = new AtomicInteger();
			setter.setFunctionFor(reference, ignored ->
					calls.getAndIncrement() == 0 ? first : second);
			assertTimeoutPreemptively(Duration.ofSeconds(2),
					() -> setter.setCrossReference(owner, reference));
			assertThat(calls).hasValue(1);
			assertThat(list).containsExactly(first, second, fallback);

			final var exhaustedResource = createResource(createResourceSet(), "exhausted.xmi");
			final var exhaustedOwner = createInstance(model, "Document");
			final var exhaustedFirst = createInstance(model, "RelatedDocument");
			final var exhaustedSecond = createInstance(model, "RelatedDocument");
			exhaustedResource.getContents().addAll(
					List.of(exhaustedOwner, exhaustedFirst, exhaustedSecond));
			final var exhaustedList = EMFUtils.getAsEObjectsList(exhaustedOwner, reference);
			exhaustedList.addAll(List.of(exhaustedFirst, exhaustedSecond));
			final var exhaustedSetter = new EMFCrossReferenceSetter();
			exhaustedSetter.setCandidateSelectorStrategy(factory.get());
			exhaustedSetter.setDefaultMaxCount(3);
			final var exhaustedCalls = new AtomicInteger();
			exhaustedSetter.setFunctionFor(reference, ignored ->
					exhaustedCalls.getAndIncrement() == 0 ? exhaustedFirst : exhaustedSecond);
			assertTimeoutPreemptively(Duration.ofSeconds(2),
					() -> exhaustedSetter.setCrossReference(exhaustedOwner, reference));
			assertThat(exhaustedCalls).hasValue(1);
			assertThat(exhaustedList).containsExactly(exhaustedFirst, exhaustedSecond);
		}
	}

	@Test
	void nonuniqueManyReferenceAllowsRepeatedSelection() {
		final var model = loadEPackage("inputs/nonunique-references.ecore");
		final var resource = createResource(createResourceSet(), "nonunique.xmi");
		final var playlist = createInstance(model, "Playlist");
		final var song = createInstance(model, "Song");
		resource.getContents().addAll(List.of(playlist, song));
		final var queue = (EReference) playlist.eClass().getEStructuralFeature("queue");
		final var setter = new EMFCrossReferenceSetter();
		setter.setDefaultMaxCount(3);
		setter.setCrossReference(playlist, queue);
		assertThat(EMFUtils.getAsEObjectsList(playlist, queue))
				.containsExactly(song, song, song);
	}

	@Test
	void uniqueFeatureMapProjectionRejectsExistingCustomAndSelectorTargets() {
		final var model = loadEPackage("inputs/featuremap_mixed.ecore");
		final var resource = createResource(createResourceSet(), "projected.xmi");
		final var owner = createInstance(model, "Document");
		final var first = createInstance(model, "Target");
		final var second = createInstance(model, "Target");
		resource.getContents().addAll(List.of(owner, first, second));
		final var related = (EReference) owner.eClass().getEStructuralFeature("related");
		final var group = (FeatureMap) owner.eGet(owner.eClass().getEStructuralFeature("group"));
		group.add(related, first);
		assertThat(EMFUtils.getAsEObjectsList(owner, related)).containsExactly(first);
		final var setter = new EMFCrossReferenceSetter();
		setter.setCandidateSelectorStrategy(
				new EMFRoundRobinReferenceAwareEObjectCandidateSelector());
		final var calls = new AtomicInteger();
		setter.setFunctionFor(related, ignored -> {
			calls.incrementAndGet();
			return first;
		});
		assertThat(setter.selectValue(owner, related)).isSameAs(second);
		assertThat(calls).hasValue(1);
	}
	@Test
	void selfPolicyIsEvaluatedOnceForEachExaminedSelfCandidate() {
		final var ePackage = loadEPackage("inputs/graph.ecore");
		final var node = createInstance(ePackage, "Node");
		final var reference = (EReference) node.eClass().getEStructuralFeature("outgoing");
		for (final EMFCrossReferenceCandidateSelectorStrategy selector :
				List.of(new EMFRoundRobinEObjectCandidateSelector(),
						new EMFRoundRobinReferenceAwareEObjectCandidateSelector())) {
			final var setter = new EMFCrossReferenceSetter();
			setter.setCandidateSelectorStrategy(selector);
			final var calls = new AtomicInteger();
			setter.setSelfReferencePolicy((owner, actual) -> calls.incrementAndGet() == 1);
			assertThat(setter.selectValue(node, reference)).isSameAs(node);
			assertThat(calls).hasValue(1);
		}
	}

	@Test
	void rejectedSelfIsEvaluatedOncePerBoundedSelection() {
		final var ePackage = loadEPackage("inputs/graph.ecore");
		final var node = createInstance(ePackage, "Node");
		final var reference = (EReference) node.eClass().getEStructuralFeature("outgoing");
		for (final EMFCrossReferenceCandidateSelectorStrategy selector :
				List.of(new EMFRoundRobinEObjectCandidateSelector(),
						new EMFRoundRobinReferenceAwareEObjectCandidateSelector())) {
			final var setter = new EMFCrossReferenceSetter();
			setter.setCandidateSelectorStrategy(selector);
			final var calls = new AtomicInteger();
			setter.setSelfReferencePolicy((owner, actual) -> {
				calls.incrementAndGet();
				return false;
			});
			assertThat(setter.selectValue(node, reference)).isNull();
			assertThat(calls).hasValue(1);
		}
	}

	@Test
	void nominalUserSelectorScansPastAnOccupiedFirstCandidateAndResets() {
		final var ePackage = loadEPackage("inputs/references-opposite.ecore");
		final var f = libraryFixture(ePackage, "legacy-user.xmi");
		EMFUtils.getAsEObjectsList(f.libraries().get(0), f.libraryBooks())
				.add(f.books().get(0));
		final var next = new AtomicInteger();
		final var resets = new AtomicInteger();
		final EMFCrossReferenceCandidateSelectorStrategy custom =
				new EMFCrossReferenceCandidateSelectorStrategy() {
					private final EMFRoundRobinEObjectCandidateSelector delegate =
							new EMFRoundRobinEObjectCandidateSelector();
					@Override
					public EObject selectCandidate(final EObject owner, final EReference reference,
							final java.util.function.Function<EObject, CandidateDecision> evaluator) {
						next.incrementAndGet();
						return delegate.selectCandidate(owner, reference, evaluator);
					}

					@Override
					public void reset() {
						delegate.reset();
						next.set(0);
						resets.incrementAndGet();
					}
				};
		final var setter = new EMFCrossReferenceSetter();
		setter.setCandidateSelectorStrategy(custom);
		assertThat(setter.selectValue(f.libraries().get(1), f.libraryBooks()))
				.isSameAs(f.books().get(1));
		assertThat(next).hasValue(1);
		setter.reset();
		assertThat(resets).hasValue(1);
		assertThat(setter.selectValue(f.libraries().get(2), f.libraryBooks()))
				.isSameAs(f.books().get(1));
	}

	@Test
	void overriddenSelfPolicyAndUniqueManyValuesWorkWithOptInSelector() {
		final var ePackage = loadEPackage("inputs/graph.ecore");
		final var resources = createResourceSet();
		final var resource = createResource(resources, "unique-graph.xmi");
		final var first = createInstance(ePackage, "Node");
		final var second = createInstance(ePackage, "Node");
		resource.getContents().addAll(List.of(first, second));
		final var outgoing = (EReference) first.eClass().getEStructuralFeature("outgoing");
		final var setter = new EMFCrossReferenceSetter() {
			@Override
			protected boolean allowSelfReferenceFor(final EObject owner,
					final EReference reference) {
				return owner == second;
			}
		};
		final var selector = new EMFRoundRobinReferenceAwareEObjectCandidateSelector();
		setter.setCandidateSelectorStrategy(selector);
		setter.setDefaultMaxCount(3);
		setter.setCrossReference(first, outgoing);
		assertThat(EMFUtils.getAsEObjectsList(first, outgoing)).containsExactly(second);
		assertThat(setter.selectValue(second, outgoing)).isSameAs(second);
	}

	@AfterEach
	void cleanup() {
		EMFTestUtils.cleanupRegisteredPackages();
	}

	/**
	 * @author Lorenzo Bettini
	 */
	private record LibraryFixture(ResourceSet resources, List<EObject> libraries,
			List<EObject> books, EObject author, EReference libraryBooks,
			EReference authorBooks, EClass bookClass) {
	}

	private LibraryFixture libraryFixture(final EPackage ePackage, final String uri) {
		final var resources = createResourceSet();
		final var resource = createResource(resources, uri);
		final var libraries = new ArrayList<EObject>();
		final var books = new ArrayList<EObject>();
		for (int i = 0; i < 4; i++) {
			final var library = createInstance(ePackage, "Library");
			libraries.add(library);
			resource.getContents().add(library);
		}
		for (int i = 0; i < 3; i++) {
			final var book = createInstance(ePackage, "Book");
			books.add(book);
			resource.getContents().add(book);
		}
		final var author = createInstance(ePackage, "Author");
		resource.getContents().add(author);
		final var libraryBooks = (EReference) libraries.getFirst().eClass()
				.getEStructuralFeature("books");
		final var authorBooks = (EReference) author.eClass().getEStructuralFeature("books");
		return new LibraryFixture(resources, libraries, books, author,
				libraryBooks, authorBooks, (EClass) ePackage.getEClassifier("Book"));
	}

	private List<Integer> trace(final EMFCrossReferenceSetter setter, final LibraryFixture f) {
		final var result = new ArrayList<Integer>();
		for (int i = 0; i < 3; i++) {
			final EObject book = setter.selectValue(f.libraries().get(i), f.libraryBooks());
			result.add(f.books().indexOf(book));
			EMFUtils.getAsEObjectsList(f.libraries().get(i), f.libraryBooks()).add(book);
			result.add(f.books().indexOf(setter.selectValue(f.author(), f.authorBooks())));
		}
		result.add(f.books().indexOf(setter.selectValue(f.libraries().get(3), f.libraryBooks())));
		result.add(f.books().indexOf(setter.selectValue(f.author(), f.authorBooks())));
		return result;
	}

	@Test
	void preservesSharedClassCursorAndReferenceSpecificRetirement() {
		final var ePackage = loadEPackage("inputs/references-opposite.ecore");
		final var baselineFixture = libraryFixture(ePackage, "baseline.xmi");
		final var awareFixture = libraryFixture(ePackage, "aware.xmi");
		final var baseline = new EMFCrossReferenceSetter();
		final var aware = new EMFCrossReferenceSetter();
		final var selector = new EMFRoundRobinReferenceAwareEObjectCandidateSelector();
		aware.setCandidateSelectorStrategy(selector);
		assertThat(trace(aware, awareFixture)).containsExactly(0, 1, 2, 0, 1, 2, -1, 1)
				.containsExactlyElementsOf(trace(baseline, baselineFixture));
		assertThat(selector.hasCandidates(awareFixture.author(), awareFixture.authorBooks())).isTrue();
		assertThat(selector.getNextCandidate(awareFixture.author(), awareFixture.authorBooks()))
				.isSameAs(awareFixture.books().get(2));
		assertThat(selector.getNextCandidate(awareFixture.author(), awareFixture.authorBooks()))
				.isSameAs(awareFixture.books().get(0));
	}

	@Test
	void resetRestoresCapacityAndDiscoversNewResources() {
		final var ePackage = loadEPackage("inputs/references-opposite.ecore");
		final var f = libraryFixture(ePackage, "reset.xmi");
		final var selector = new EMFRoundRobinReferenceAwareEObjectCandidateSelector();
		final var setter = new EMFCrossReferenceSetter();
		setter.setCandidateSelectorStrategy(selector);
		for (int i = 0; i < 3; i++) {
			final var book = setter.selectValue(f.libraries().get(i), f.libraryBooks());
			EMFUtils.getAsEObjectsList(f.libraries().get(i), f.libraryBooks()).add(book);
		}
		assertThat(setter.selectValue(f.libraries().get(3), f.libraryBooks())).isNull();
		f.books().get(0).eUnset(f.libraryBooks().getEOpposite());
		assertThat(setter.selectValue(f.libraries().get(3), f.libraryBooks())).isNull();
		assertThat(selector.getNextCandidate(f.author(), f.authorBooks()))
				.isSameAs(f.books().get(2));
		setter.setCandidateSelectorStrategy(new EMFRoundRobinEObjectCandidateSelector());
		assertThat(setter.selectValue(f.libraries().get(3), f.libraryBooks()))
				.isSameAs(f.books().get(0));
		setter.setCandidateSelectorStrategy(selector);
		selector.reset();
		assertThat(setter.selectValue(f.libraries().get(3), f.libraryBooks()))
				.isSameAs(f.books().get(0));
		final var added = createInstance(ePackage, "Book");
		createResource(f.resources(), "added.xmi").getContents().add(added);
		assertThat(selector.getNextCandidate(f.author(), f.authorBooks())).isSameAs(f.books().get(1));
		selector.reset();
		assertThat(selector.getNextCandidate(f.author(), f.authorBooks())).isSameAs(f.books().get(0));
		assertThat(selector.getNextCandidate(f.author(), f.authorBooks())).isSameAs(f.books().get(1));
		assertThat(selector.getNextCandidate(f.author(), f.authorBooks())).isSameAs(f.books().get(2));
		assertThat(selector.getNextCandidate(f.author(), f.authorBooks())).isSameAs(added);
	}

	@Test
	void rejectsChangedScopeUntilReset() {
		final var ePackage = loadEPackage("inputs/references-opposite.ecore");
		final var first = libraryFixture(ePackage, "first.xmi");
		final var second = libraryFixture(ePackage, "second.xmi");
		final var selector = new EMFRoundRobinReferenceAwareEObjectCandidateSelector();
		assertThat(selector.getNextCandidate(first.author(), first.authorBooks()))
				.isSameAs(first.books().get(0));
		final var sameSet = createInstance(ePackage, "Book");
		createResource(first.resources(), "same-set.xmi").getContents().add(sameSet);
		assertThat(selector.hasCandidates(sameSet, first.authorBooks())).isTrue();
		var secondAuthorBooks = second.authorBooks();
		var secondAuthor = second.author();
		assertThatThrownBy(() -> selector.hasCandidates(secondAuthor, secondAuthorBooks))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("reset");
		selector.reset();
		assertThat(selector.getNextCandidate(second.author(), second.authorBooks()))
				.isSameAs(second.books().get(0));
		selector.reset();
		final var detachedFirst = createInstance(ePackage, "Book");
		final var detachedSecond = createInstance(ePackage, "Book");
		assertThat(selector.getNextCandidate(detachedFirst, first.authorBooks()))
				.isSameAs(detachedFirst);
		var firstAuthorBooks = first.authorBooks();
		assertThatThrownBy(() -> selector.hasCandidates(detachedSecond, firstAuthorBooks))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void selfPolicyIsOwnerDependentAndLegacyInterfaceRemainsAvailable() {
		final var ePackage = loadEPackage("inputs/graph.ecore");
		final var resources = createResourceSet();
		final var resource = createResource(resources, "graph.xmi");
		final var first = createInstance(ePackage, "Node");
		final var second = createInstance(ePackage, "Node");
		resource.getContents().addAll(List.of(first, second));
		final var reference = (EReference) first.eClass().getEStructuralFeature("outgoing");
		final var selector = new EMFRoundRobinReferenceAwareEObjectCandidateSelector();
		final var setter = new EMFCrossReferenceSetter();
		setter.setCandidateSelectorStrategy(selector);
		assertThat(setter.selectValue(first, reference)).isSameAs(second);
		setter.setSelfReferencePolicy((owner, ignored) -> owner == first);
		assertThat(setter.selectValue(first, reference)).isSameAs(first);
		assertThat(selector.getNextCandidate(first, reference))
				.isSameAs(second);
	}

	@Test
	void handlesDetachedSingleResourceEmptyAndExhaustedContexts() {
		final var ePackage = loadEPackage("inputs/graph.ecore");
		final var node = createInstance(ePackage, "Node");
		final var edge = createInstance(ePackage, "Edge");
		final var edgeClass = (EClass) ePackage.getEClassifier("Edge");
		final var outgoing = (EReference) node.eClass().getEStructuralFeature("outgoing");
		final var target = (EReference) edge.eClass().getEStructuralFeature("target");
		final var selector = new EMFRoundRobinReferenceAwareEObjectCandidateSelector();
		final var setter = new EMFCrossReferenceSetter();
		setter.setCandidateSelectorStrategy(selector);
		assertThat(selector.hasCandidates(node, EMFTestUtils.referenceFor(edgeClass))).isFalse();
		assertThat(selector.getNextCandidate(node, EMFTestUtils.referenceFor(edgeClass))).isNull();
		assertThat(setter.selectValue(node, outgoing)).isNull();
		selector.reset();
		assertThat(setter.selectValue(edge, target)).isNull();
		selector.reset();
		final var standAlone = new XMIResourceImpl(URI.createURI("memory:/standalone.xmi"));
		standAlone.getContents().add(node);
		assertThat(selector.getNextCandidate(node, outgoing)).isSameAs(node);
	}

	@Test
	void skipsRetiredIndicesAcrossWrapWithoutMovingOtherReferenceCursor() {
		final var ePackage = loadEPackage("inputs/references-opposite.ecore");
		final var f = libraryFixture(ePackage, "wrap.xmi");
		final var selector = new EMFRoundRobinReferenceAwareEObjectCandidateSelector();
		final var setter = new EMFCrossReferenceSetter();
		setter.setCandidateSelectorStrategy(selector);
		selector.getNextCandidate(f.author(), f.authorBooks());
		selector.getNextCandidate(f.author(), f.authorBooks());
		EMFUtils.getAsEObjectsList(f.libraries().get(0), f.libraryBooks())
				.add(f.books().get(2));
		assertThat(setter.selectValue(f.libraries().get(1), f.libraryBooks()))
				.isSameAs(f.books().get(0));
		selector.getNextCandidate(f.author(), f.authorBooks());
		assertThat(setter.selectValue(f.libraries().get(1), f.libraryBooks()))
				.isSameAs(f.books().get(0));
	}

	@Test
	void scansEveryIndexWhenStartingAfterZero() {
		final var ePackage = loadEPackage("inputs/references-opposite.ecore");
		final var f = libraryFixture(ePackage, "offset.xmi");
		final var selector = new EMFRoundRobinReferenceAwareEObjectCandidateSelector();
		final var setter = new EMFCrossReferenceSetter();
		setter.setCandidateSelectorStrategy(selector);
		selector.getNextCandidate(f.author(), f.authorBooks());
		EMFUtils.getAsEObjectsList(f.libraries().get(0), f.libraryBooks())
				.add(f.books().get(1));
		EMFUtils.getAsEObjectsList(f.libraries().get(0), f.libraryBooks())
				.add(f.books().get(2));
		assertThat(setter.selectValue(f.libraries().get(1), f.libraryBooks()))
				.isSameAs(f.books().get(0));
	}


	@Test
	void featureMapSelectionValidatesCustomFunctionAndSharesClassCursor() {
		final var ePackage = loadEPackage("inputs/featuremap_mixed.ecore");
		final var resources = createResourceSet();
		final var resource = createResource(resources, "featuremap-aware.xmi");
		final var owner = createInstance(ePackage, "Document");
		final var other = createInstance(ePackage, "Document");
		final var occupied = createInstance(ePackage, "Target");
		final var free = createInstance(ePackage, "Target");
		resource.getContents().addAll(List.of(owner, other, occupied, free));
		final var related = (EReference) owner.eClass().getEStructuralFeature("related");
		final var favorite = (EReference) owner.eClass().getEStructuralFeature("favorite");
		final var document = (EReference) occupied.eClass().getEStructuralFeature("document");
		occupied.eSet(document, other);
		final var populator = new EMFInstancePopulator();
		populator.setCrossReferenceCandidateSelectorStrategy(
				new EMFRoundRobinReferenceAwareEObjectCandidateSelector());
		populator.setMaxDepth(0);
		populator.setFeatureMapMaxCountFor((org.eclipse.emf.ecore.EAttribute)
				owner.eClass().getEStructuralFeature("group"), 3);
		populator.functionForCrossReference(related, ignored -> occupied);
		populator.populateEObjects(owner);
		assertThat(EMFUtils.getAsEObjectsList(owner, related)).containsExactly(free);
		assertThat(occupied.eGet(document)).isSameAs(other);
		assertThat(owner.eGet(favorite)).isSameAs(occupied);
	}
}
