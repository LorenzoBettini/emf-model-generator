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
 * Test class for EMFRoundRobinCandidateSelector.
 * This class achieves 100% coverage of the abstract round-robin candidate selector functionality.
 */
class EMFRoundRobinCandidateSelectorTest {

	private static final EcoreFactory ECORE_FACTORY = EcoreFactory.eINSTANCE;

	private TestableRoundRobinSelector selector;
	private ResourceSet resourceSet;
	private EPackage testPackage;
	private EClass class1;
	private EClass class2;
	private EClass class3;
	private Resource contextResource;
	private EObject context;

	/**
	 * Concrete implementation of EMFRoundRobinCandidateSelector for testing purposes.
	 */
	private static class TestableRoundRobinSelector extends EMFRoundRobinCandidateSelector<EClass, EObject> {
		private final List<EObject> candidatesList = new ArrayList<>();

		@Override
		protected List<EObject> getOrCompute(EObject context, EClass type) {
			return candidatesList;
		}

		public void setCandidates(List<EObject> candidates) {
			candidatesList.clear();
			candidatesList.addAll(candidates);
		}
	}

	@BeforeEach
	void setUp() {
		resourceSet = createResourceSet();

		// Create test package
		testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");

		// Create three different classes for testing
		class1 = ECORE_FACTORY.createEClass();
		class1.setName("Class1");
		testPackage.getEClassifiers().add(class1);

		class2 = ECORE_FACTORY.createEClass();
		class2.setName("Class2");
		testPackage.getEClassifiers().add(class2);

		class3 = ECORE_FACTORY.createEClass();
		class3.setName("Class3");
		testPackage.getEClassifiers().add(class3);

		// Create context resource and context object
		contextResource = createResource(resourceSet, "context.xmi");
		context = EcoreUtil.create(class1);
		contextResource.getContents().add(context);

		// Create selector
		selector = new TestableRoundRobinSelector();
	}

	@Test
	void shouldReturnNullWhenNoCandidatesAvailable() {
		// Set up empty candidates list
		selector.setCandidates(List.of());

		// Act
		EObject result = selector.getNextCandidate(context, class1);

		// Assert
		assertThat(result).isNull();
	}

	@Test
	void shouldReturnSingleCandidateRepeatedly() {
		// Set up with single candidate
		EObject candidate = EcoreUtil.create(class1);
		selector.setCandidates(List.of(candidate));

		// Act & Assert - call multiple times, should always return the same candidate
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate);
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate);
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate);
	}

	@Test
	void shouldRotateThroughCandidatesInRoundRobinOrder() {
		// Set up with three candidates
		EObject candidate1 = EcoreUtil.create(class1);
		EObject candidate2 = EcoreUtil.create(class1);
		EObject candidate3 = EcoreUtil.create(class1);
		selector.setCandidates(List.of(candidate1, candidate2, candidate3));

		// Act & Assert - should cycle through all candidates
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate1);
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate2);
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate3);
		// Should wrap around
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate1);
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate2);
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate3);
	}

	@Test
	void shouldMaintainSeparateStateForDifferentTypes() {
		// Set up different candidates for different types
		EObject candidate1ForClass1 = EcoreUtil.create(class1);
		EObject candidate2ForClass1 = EcoreUtil.create(class1);
		
		EObject candidate1ForClass2 = EcoreUtil.create(class2);
		EObject candidate2ForClass2 = EcoreUtil.create(class2);

		// First, set candidates for class1 and get first one
		selector.setCandidates(List.of(candidate1ForClass1, candidate2ForClass1));
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate1ForClass1);

		// Now switch to class2 candidates
		selector.setCandidates(List.of(candidate1ForClass2, candidate2ForClass2));
		// Should start from beginning for class2
		assertThat(selector.getNextCandidate(context, class2)).isSameAs(candidate1ForClass2);

		// Go back to class1 - should continue from where we left off (candidate2)
		selector.setCandidates(List.of(candidate1ForClass1, candidate2ForClass1));
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate2ForClass1);

		// Continue with class2 - should continue from where we left off (candidate2)
		selector.setCandidates(List.of(candidate1ForClass2, candidate2ForClass2));
		assertThat(selector.getNextCandidate(context, class2)).isSameAs(candidate2ForClass2);
	}

	@Test
	void shouldHandleWrappingWhenIndexExceedsSize() {
		// Set up with two candidates
		EObject candidate1 = EcoreUtil.create(class1);
		EObject candidate2 = EcoreUtil.create(class1);
		selector.setCandidates(List.of(candidate1, candidate2));

		// Get both candidates
		selector.getNextCandidate(context, class1); // candidate1, index now at 1
		selector.getNextCandidate(context, class1); // candidate2, index now at 0 (wrapped)

		// Next should be candidate1 again
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate1);
	}

	@Test
	void shouldReturnFalseWhenNoCandidatesForHasCandidates() {
		// Set up empty candidates list
		selector.setCandidates(List.of());

		// Act & Assert
		assertThat(selector.hasCandidates(context, class1)).isFalse();
	}

	@Test
	void shouldReturnTrueWhenCandidatesExist() {
		// Set up with candidates
		EObject candidate = EcoreUtil.create(class1);
		selector.setCandidates(List.of(candidate));

		// Act & Assert
		assertThat(selector.hasCandidates(context, class1)).isTrue();
	}

	@Test
	void shouldReturnTrueWhenMultipleCandidatesExist() {
		// Set up with multiple candidates
		EObject candidate1 = EcoreUtil.create(class1);
		EObject candidate2 = EcoreUtil.create(class1);
		EObject candidate3 = EcoreUtil.create(class1);
		selector.setCandidates(List.of(candidate1, candidate2, candidate3));

		// Act & Assert
		assertThat(selector.hasCandidates(context, class1)).isTrue();
	}

	@Test
	void shouldResetStateAndStartFromBeginning() {
		// Set up with three candidates
		EObject candidate1 = EcoreUtil.create(class1);
		EObject candidate2 = EcoreUtil.create(class1);
		EObject candidate3 = EcoreUtil.create(class1);
		selector.setCandidates(List.of(candidate1, candidate2, candidate3));

		// Advance to the second candidate
		selector.getNextCandidate(context, class1); // candidate1
		selector.getNextCandidate(context, class1); // candidate2

		// Reset
		selector.reset();

		// Should start from the beginning again
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate1);
	}

	@Test
	void shouldResetStateForMultipleTypes() {
		// Set up different candidates for different types
		EObject candidate1ForClass1 = EcoreUtil.create(class1);
		EObject candidate2ForClass1 = EcoreUtil.create(class1);
		
		EObject candidate1ForClass2 = EcoreUtil.create(class2);
		EObject candidate2ForClass2 = EcoreUtil.create(class2);

		// Advance state for both types
		selector.setCandidates(List.of(candidate1ForClass1, candidate2ForClass1));
		selector.getNextCandidate(context, class1); // candidate1

		selector.setCandidates(List.of(candidate1ForClass2, candidate2ForClass2));
		selector.getNextCandidate(context, class2); // candidate1
		selector.getNextCandidate(context, class2); // candidate2

		// Reset
		selector.reset();

		// Both types should start from beginning
		selector.setCandidates(List.of(candidate1ForClass1, candidate2ForClass1));
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate1ForClass1);

		selector.setCandidates(List.of(candidate1ForClass2, candidate2ForClass2));
		assertThat(selector.getNextCandidate(context, class2)).isSameAs(candidate1ForClass2);
	}

	@Test
	void shouldHandleDefaultIndexOfZero() {
		// Set up with candidates
		EObject candidate1 = EcoreUtil.create(class1);
		EObject candidate2 = EcoreUtil.create(class1);
		selector.setCandidates(List.of(candidate1, candidate2));

		// First call for a type should use default index of 0
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate1);
	}

	@Test
	void shouldHandleLargeCandidateList() {
		// Set up with many candidates
		List<EObject> candidates = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			candidates.add(EcoreUtil.create(class1));
		}
		selector.setCandidates(candidates);

		// Cycle through all candidates and verify wrapping
		for (int cycle = 0; cycle < 3; cycle++) {
			for (int i = 0; i < 100; i++) {
				assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidates.get(i));
			}
		}
	}

	@Test
	void shouldHandleEmptyListForHasCandidatesAfterReset() {
		// Set up with candidates
		EObject candidate = EcoreUtil.create(class1);
		selector.setCandidates(List.of(candidate));

		// Verify has candidates
		assertThat(selector.hasCandidates(context, class1)).isTrue();

		// Reset and change to empty list
		selector.reset();
		selector.setCandidates(List.of());

		// Should return false now
		assertThat(selector.hasCandidates(context, class1)).isFalse();
	}

	@Test
	void shouldHandleMultipleResets() {
		// Set up with candidates
		EObject candidate1 = EcoreUtil.create(class1);
		EObject candidate2 = EcoreUtil.create(class1);
		selector.setCandidates(List.of(candidate1, candidate2));

		// Advance and reset multiple times
		selector.getNextCandidate(context, class1);
		selector.reset();
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate1);

		selector.getNextCandidate(context, class1);
		selector.reset();
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate1);

		selector.getNextCandidate(context, class1);
		selector.reset();
		assertThat(selector.getNextCandidate(context, class1)).isSameAs(candidate1);
	}

	@Test
	void shouldPreserveStateBetweenDifferentContextObjects() {
		// Set up with candidates
		EObject candidate1 = EcoreUtil.create(class1);
		EObject candidate2 = EcoreUtil.create(class1);
		selector.setCandidates(List.of(candidate1, candidate2));

		// Create different context objects
		EObject context1 = EcoreUtil.create(class1);
		EObject context2 = EcoreUtil.create(class2);
		contextResource.getContents().add(context1);
		contextResource.getContents().add(context2);

		// State is maintained per type, not per context
		assertThat(selector.getNextCandidate(context1, class1)).isSameAs(candidate1);
		assertThat(selector.getNextCandidate(context2, class1)).isSameAs(candidate2);
	}
}
