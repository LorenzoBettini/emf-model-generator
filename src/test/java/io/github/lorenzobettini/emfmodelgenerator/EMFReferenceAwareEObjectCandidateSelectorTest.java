package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createInstance;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResource;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResourceSet;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.loadEPackage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.emf.common.util.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * @author Lorenzo Bettini
 */
class EMFReferenceAwareEObjectCandidateSelectorTest {

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
		final var selector = new EMFReferenceAwareEObjectCandidateSelector();
		aware.setCandidateSelectorStrategy(selector);
		assertThat(trace(aware, awareFixture)).containsExactlyElementsOf(trace(baseline,
				baselineFixture));
		assertThat(selector.hasCandidates(awareFixture.author(), awareFixture.bookClass())).isTrue();
		assertThat(selector.getNextCandidate(awareFixture.author(), awareFixture.bookClass()))
				.isSameAs(awareFixture.books().get(2));
		assertThat(selector.getNextCandidate(awareFixture.author(), awareFixture.bookClass()))
				.isSameAs(awareFixture.books().get(0));
	}

	@Test
	void resetRestoresCapacityAndDiscoversNewResources() {
		final var ePackage = loadEPackage("inputs/references-opposite.ecore");
		final var f = libraryFixture(ePackage, "reset.xmi");
		final var selector = new EMFReferenceAwareEObjectCandidateSelector();
		final var setter = new EMFCrossReferenceSetter();
		setter.setCandidateSelectorStrategy(selector);
		for (int i = 0; i < 3; i++) {
			final var book = setter.selectValue(f.libraries().get(i), f.libraryBooks());
			EMFUtils.getAsEObjectsList(f.libraries().get(i), f.libraryBooks()).add(book);
		}
		assertThat(setter.selectValue(f.libraries().get(3), f.libraryBooks())).isNull();
		f.books().get(0).eUnset(f.libraryBooks().getEOpposite());
		assertThat(setter.selectValue(f.libraries().get(3), f.libraryBooks())).isNull();
		assertThat(selector.getNextCandidate(f.author(), f.bookClass()))
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
		assertThat(selector.getNextCandidate(f.author(), f.bookClass())).isSameAs(f.books().get(1));
		selector.reset();
		assertThat(selector.getNextCandidate(f.author(), f.bookClass())).isSameAs(f.books().get(0));
		assertThat(selector.getNextCandidate(f.author(), f.bookClass())).isSameAs(f.books().get(1));
		assertThat(selector.getNextCandidate(f.author(), f.bookClass())).isSameAs(f.books().get(2));
		assertThat(selector.getNextCandidate(f.author(), f.bookClass())).isSameAs(added);
	}

	@Test
	void rejectsChangedScopeUntilReset() {
		final var ePackage = loadEPackage("inputs/references-opposite.ecore");
		final var first = libraryFixture(ePackage, "first.xmi");
		final var second = libraryFixture(ePackage, "second.xmi");
		final var selector = new EMFReferenceAwareEObjectCandidateSelector();
		assertThat(selector.getNextCandidate(first.author(), first.bookClass()))
				.isSameAs(first.books().get(0));
		final var sameSet = createInstance(ePackage, "Book");
		createResource(first.resources(), "same-set.xmi").getContents().add(sameSet);
		assertThat(selector.hasCandidates(sameSet, first.bookClass())).isTrue();
		assertThatThrownBy(() -> selector.hasCandidates(second.author(), second.bookClass()))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("reset");
		selector.reset();
		assertThat(selector.getNextCandidate(second.author(), second.bookClass()))
				.isSameAs(second.books().get(0));
		selector.reset();
		final var detachedFirst = createInstance(ePackage, "Book");
		final var detachedSecond = createInstance(ePackage, "Book");
		assertThat(selector.getNextCandidate(detachedFirst, first.bookClass()))
				.isSameAs(detachedFirst);
		assertThatThrownBy(() -> selector.hasCandidates(detachedSecond, first.bookClass()))
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
		final var selector = new EMFReferenceAwareEObjectCandidateSelector();
		final var setter = new EMFCrossReferenceSetter();
		setter.setCandidateSelectorStrategy(selector);
		assertThat(setter.selectValue(first, reference)).isSameAs(second);
		setter.setSelfReferencePolicy((owner, ignored) -> owner == first);
		assertThat(setter.selectValue(first, reference)).isSameAs(first);
		assertThat(selector.getNextCandidate(first, (EClass) ePackage.getEClassifier("Node")))
				.isSameAs(second);
	}

	@Test
	void handlesDetachedSingleResourceEmptyAndExhaustedContexts() {
		final var ePackage = loadEPackage("inputs/graph.ecore");
		final var node = createInstance(ePackage, "Node");
		final var edge = createInstance(ePackage, "Edge");
		final var nodeClass = (EClass) ePackage.getEClassifier("Node");
		final var edgeClass = (EClass) ePackage.getEClassifier("Edge");
		final var outgoing = (EReference) node.eClass().getEStructuralFeature("outgoing");
		final var target = (EReference) edge.eClass().getEStructuralFeature("target");
		final var selector = new EMFReferenceAwareEObjectCandidateSelector();
		final var setter = new EMFCrossReferenceSetter();
		setter.setCandidateSelectorStrategy(selector);
		assertThat(selector.hasCandidates(node, edgeClass)).isFalse();
		assertThat(selector.getNextCandidate(node, edgeClass)).isNull();
		assertThat(setter.selectValue(node, outgoing)).isNull();
		selector.reset();
		assertThat(setter.selectValue(edge, target)).isNull();
		selector.reset();
		final var standAlone = new XMIResourceImpl(URI.createURI("memory:/standalone.xmi"));
		standAlone.getContents().add(node);
		assertThat(selector.getNextCandidate(node, nodeClass)).isSameAs(node);
	}

	@Test
	void skipsRetiredIndicesAcrossWrapWithoutMovingOtherReferenceCursor() {
		final var ePackage = loadEPackage("inputs/references-opposite.ecore");
		final var f = libraryFixture(ePackage, "wrap.xmi");
		final var selector = new EMFReferenceAwareEObjectCandidateSelector();
		final var setter = new EMFCrossReferenceSetter();
		setter.setCandidateSelectorStrategy(selector);
		selector.getNextCandidate(f.author(), f.bookClass());
		selector.getNextCandidate(f.author(), f.bookClass());
		EMFUtils.getAsEObjectsList(f.libraries().get(0), f.libraryBooks())
				.add(f.books().get(2));
		assertThat(setter.selectValue(f.libraries().get(1), f.libraryBooks()))
				.isSameAs(f.books().get(0));
		selector.getNextCandidate(f.author(), f.bookClass());
		assertThat(setter.selectValue(f.libraries().get(1), f.libraryBooks()))
				.isSameAs(f.books().get(0));
	}

	@Test
	void scansEveryIndexWhenStartingAfterZero() {
		final var ePackage = loadEPackage("inputs/references-opposite.ecore");
		final var f = libraryFixture(ePackage, "offset.xmi");
		final var selector = new EMFReferenceAwareEObjectCandidateSelector();
		final var setter = new EMFCrossReferenceSetter();
		setter.setCandidateSelectorStrategy(selector);
		selector.getNextCandidate(f.author(), f.bookClass());
		EMFUtils.getAsEObjectsList(f.libraries().get(0), f.libraryBooks())
				.add(f.books().get(1));
		EMFUtils.getAsEObjectsList(f.libraries().get(0), f.libraryBooks())
				.add(f.books().get(2));
		assertThat(setter.selectValue(f.libraries().get(1), f.libraryBooks()))
				.isSameAs(f.books().get(0));
	}

	@Test
	void diagnosticCountersAreExplicitAndSeparateFromOrdinarySelection() {
		final var ePackage = loadEPackage("inputs/references-opposite.ecore");
		final var f = libraryFixture(ePackage, "diagnostics.xmi");
		final var diagnostics = new EMFReferenceAwareEObjectCandidateSelector.Diagnostics();
		final var selector = new EMFReferenceAwareEObjectCandidateSelector(diagnostics);
		final var setter = new EMFCrossReferenceSetter();
		setter.setCandidateSelectorStrategy(selector);
		EMFUtils.getAsEObjectsList(f.libraries().get(0), f.libraryBooks())
				.add(f.books().get(0));
		assertThat(setter.selectValue(f.libraries().get(1), f.libraryBooks()))
				.isSameAs(f.books().get(1));
		assertThat(diagnostics.attempts()).isEqualTo(2);
		assertThat(diagnostics.oppositeRejections()).isEqualTo(1);
		assertThat(diagnostics.retirements()).isEqualTo(1);
		assertThat(diagnostics.poolEntriesBuilt()).isEqualTo(3);
		assertThat(selector.activeIndexCount()).isEqualTo(2);
		assertThat(diagnostics.poolBuildNanos()).isBetween(1L, 999_999_999L);
		assertThat(diagnostics.selectionNanos()).isBetween(1L, 999_999_999L);
		selector.reset();
		assertThat(selector.activeIndexCount()).isZero();
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
				new EMFReferenceAwareEObjectCandidateSelector());
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
