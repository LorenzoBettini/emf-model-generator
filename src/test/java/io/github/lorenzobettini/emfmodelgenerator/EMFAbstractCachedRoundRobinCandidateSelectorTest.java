package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResource;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResourceSet;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for EMFAbstractCachedRoundRobinCandidateSelector.
 * This class achieves 100% coverage of the abstract cached round-robin candidate selector functionality.
 */
class EMFAbstractCachedRoundRobinCandidateSelectorTest {

	private static final EcoreFactory ECORE_FACTORY = EcoreFactory.eINSTANCE;

	private TestableCachedRoundRobinSelector selector;
	private ResourceSet resourceSet;
	private EPackage testPackage;
	private EClass class1;
	private EClass class2;
	private Resource contextResource;
	private EObject context;

	/**
	 * Concrete implementation of EMFAbstractCachedRoundRobinCandidateSelector for testing purposes.
	 */
	private static class TestableCachedRoundRobinSelector
			extends EMFAbstractCachedRoundRobinCandidateSelector<EClass, EObject> {
		private final List<EObject> candidatesList = new ArrayList<>();
		private int computeCallCount = 0;

		@Override
		protected List<EObject> compute(final EObject context, final EClass key) {
			computeCallCount++;
			return new ArrayList<>(candidatesList);
		}

		public void setCandidates(final List<EObject> candidates) {
			candidatesList.clear();
			candidatesList.addAll(candidates);
		}

		public int getComputeCallCount() {
			return computeCallCount;
		}
	}

	@BeforeEach
	void setUp() {
		resourceSet = createResourceSet();

		testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");

		class1 = ECORE_FACTORY.createEClass();
		class1.setName("Class1");
		testPackage.getEClassifiers().add(class1);

		class2 = ECORE_FACTORY.createEClass();
		class2.setName("Class2");
		testPackage.getEClassifiers().add(class2);

		contextResource = createResource(resourceSet, "context.xmi");
		context = EcoreUtil.create(class1);
		contextResource.getContents().add(context);

		selector = new TestableCachedRoundRobinSelector();
	}

	@Test
	void shouldCacheCandidatesAndNotRecomputeOnSubsequentCalls() {
		final var candidate1 = EcoreUtil.create(class1);
		final var candidate2 = EcoreUtil.create(class1);
		selector.setCandidates(List.of(candidate1, candidate2));

		selector.getNextCandidate(context, class1);
		selector.getNextCandidate(context, class1);
		selector.getNextCandidate(context, class1);

		assertThat(selector.getComputeCallCount()).isEqualTo(1);
	}

	@Test
	void shouldCacheIndependentlyForDifferentKeys() {
		final var candidate1ForClass1 = EcoreUtil.create(class1);
		final var candidate1ForClass2 = EcoreUtil.create(class2);

		selector.setCandidates(List.of(candidate1ForClass1));
		selector.getNextCandidate(context, class1);

		selector.setCandidates(List.of(candidate1ForClass2));
		selector.getNextCandidate(context, class2);

		assertThat(selector.getComputeCallCount()).isEqualTo(2);

		selector.getNextCandidate(context, class1);
		selector.getNextCandidate(context, class2);

		assertThat(selector.getComputeCallCount()).isEqualTo(2);
	}

	@Test
	void shouldResetCacheAndRecompute() {
		final var candidate = EcoreUtil.create(class1);
		selector.setCandidates(List.of(candidate));

		selector.getNextCandidate(context, class1);
		selector.reset();
		selector.getNextCandidate(context, class1);

		assertThat(selector.getComputeCallCount()).isEqualTo(2);
	}

	@Test
	void shouldResetRoundRobinStateOnReset() {
		final var candidate1 = EcoreUtil.create(class1);
		final var candidate2 = EcoreUtil.create(class1);
		selector.setCandidates(List.of(candidate1, candidate2));

		selector.getNextCandidate(context, class1);
		selector.getNextCandidate(context, class1);

		selector.reset();

		final var resultAfterReset = selector.getNextCandidate(context, class1);

		assertThat(resultAfterReset).isSameAs(candidate1);
	}

	@Test
	void shouldHandleEmptyCandidatesList() {
		selector.setCandidates(List.of());

		final var result = selector.getNextCandidate(context, class1);

		assertThat(result).isNull();
		assertThat(selector.getComputeCallCount()).isEqualTo(1);
	}
}
