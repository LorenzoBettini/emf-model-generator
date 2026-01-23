package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEClass;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEEnum;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEEnumLiteral;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEPackage;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createInstance;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResource;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResourceSet;
import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for EMFRoundRobinEEnumLiteralCandidateSelector.
 * This class achieves 100% coverage of the round-robin enum literal selection functionality.
 */
class EMFRoundRobinEEnumLiteralCandidateSelectorTest {

	private EMFRoundRobinEEnumLiteralCandidateSelector selector;
	private ResourceSet resourceSet;
	private EPackage testPackage;
	private EClass personClass;
	private Resource contextResource;
	private EObject context;
	private EEnum statusEnum;
	private EEnum priorityEnum;

	@BeforeEach
	void setUp() {
		resourceSet = createResourceSet();

		testPackage = createEPackage("test", "http://test", "test");
		personClass = createEClass(testPackage, "Person");
		statusEnum = createEEnum(testPackage, "Status");
		priorityEnum = createEEnum(testPackage, "Priority");

		contextResource = createResource(resourceSet, "context.xmi");
		contextResource.getContents().add(testPackage);

		context = createInstance(personClass);
		contextResource.getContents().add(context);

		selector = new EMFRoundRobinEEnumLiteralCandidateSelector();
	}

	// ========== Tests with no literals ==========

	@Test
	void shouldReturnNullWhenEnumHasNoLiterals() {
		var result = selector.getNextCandidate(context, statusEnum);

		assertThat(result).isNull();
	}

	// ========== Tests with single literal ==========

	@Test
	void shouldReturnSingleLiteralWhenOnlyOneExists() {
		var active = createEEnumLiteral(statusEnum, "ACTIVE", 0);

		var result = selector.getNextCandidate(context, statusEnum);

		assertThat(result).isEqualTo(active);
	}

	@Test
	void shouldReturnSameSingleLiteralOnMultipleCalls() {
		var active = createEEnumLiteral(statusEnum, "ACTIVE", 0);

		var result1 = selector.getNextCandidate(context, statusEnum);
		var result2 = selector.getNextCandidate(context, statusEnum);
		var result3 = selector.getNextCandidate(context, statusEnum);

		assertThat(result1).isEqualTo(active);
		assertThat(result2).isEqualTo(active);
		assertThat(result3).isEqualTo(active);
	}

	// ========== Tests with multiple literals - basic round-robin ==========

	@Test
	void shouldReturnLiteralsInRoundRobinOrder() {
		var active = createEEnumLiteral(statusEnum, "ACTIVE", 0);
		var inactive = createEEnumLiteral(statusEnum, "INACTIVE", 1);
		var pending = createEEnumLiteral(statusEnum, "PENDING", 2);

		var result1 = selector.getNextCandidate(context, statusEnum);
		assertThat(result1).isEqualTo(active);

		var result2 = selector.getNextCandidate(context, statusEnum);
		assertThat(result2).isEqualTo(inactive);

		var result3 = selector.getNextCandidate(context, statusEnum);
		assertThat(result3).isEqualTo(pending);
	}

	@Test
	void shouldWrapAroundWhenReachingEndOfLiterals() {
		var active = createEEnumLiteral(statusEnum, "ACTIVE", 0);
		var inactive = createEEnumLiteral(statusEnum, "INACTIVE", 1);

		var r1 = selector.getNextCandidate(context, statusEnum);
		var r2 = selector.getNextCandidate(context, statusEnum);
		var r3 = selector.getNextCandidate(context, statusEnum);

		assertThat(r1).isEqualTo(active);
		assertThat(r2).isEqualTo(inactive);
		assertThat(r3).isEqualTo(active); // Wrapped around
	}

	@Test
	void shouldContinueRoundRobinAcrossMultipleCycles() {
		var active = createEEnumLiteral(statusEnum, "ACTIVE", 0);
		var inactive = createEEnumLiteral(statusEnum, "INACTIVE", 1);
		var pending = createEEnumLiteral(statusEnum, "PENDING", 2);

		// First cycle
		assertThat(selector.getNextCandidate(context, statusEnum)).isEqualTo(active);
		assertThat(selector.getNextCandidate(context, statusEnum)).isEqualTo(inactive);
		assertThat(selector.getNextCandidate(context, statusEnum)).isEqualTo(pending);

		// Second cycle
		assertThat(selector.getNextCandidate(context, statusEnum)).isEqualTo(active);
		assertThat(selector.getNextCandidate(context, statusEnum)).isEqualTo(inactive);
		assertThat(selector.getNextCandidate(context, statusEnum)).isEqualTo(pending);

		// Third cycle
		assertThat(selector.getNextCandidate(context, statusEnum)).isEqualTo(active);
	}

	// ========== Tests with multiple enums (state isolation) ==========

	@Test
	void shouldMaintainSeparateStateForDifferentEnums() {
		var active = createEEnumLiteral(statusEnum, "ACTIVE", 0);
		var inactive = createEEnumLiteral(statusEnum, "INACTIVE", 1);
		createEEnumLiteral(statusEnum, "PENDING", 2);

		var low = createEEnumLiteral(priorityEnum, "LOW", 0);
		var high = createEEnumLiteral(priorityEnum, "HIGH", 1);

		// Request for Status
		var resultStatus1 = selector.getNextCandidate(context, statusEnum);
		assertThat(resultStatus1).isEqualTo(active);

		var resultStatus2 = selector.getNextCandidate(context, statusEnum);
		assertThat(resultStatus2).isEqualTo(inactive);

		// Request for Priority (should start from index 0 for Priority)
		var resultPriority1 = selector.getNextCandidate(context, priorityEnum);
		assertThat(resultPriority1).isEqualTo(low);

		// Request for Status again (should continue from PENDING)
		var resultStatus3 = selector.getNextCandidate(context, statusEnum);
		assertThat(resultStatus3).isNotEqualTo(active).isNotEqualTo(inactive);

		// Request for Priority again (should continue from HIGH)
		var resultPriority2 = selector.getNextCandidate(context, priorityEnum);
		assertThat(resultPriority2).isEqualTo(high);
	}

	@Test
	void shouldHandleInterleavedRequestsForDifferentEnums() {
		var active = createEEnumLiteral(statusEnum, "ACTIVE", 0);
		createEEnumLiteral(statusEnum, "INACTIVE", 1);

		var low = createEEnumLiteral(priorityEnum, "LOW", 0);
		var high = createEEnumLiteral(priorityEnum, "HIGH", 1);

		var r1 = selector.getNextCandidate(context, statusEnum);
		var r2 = selector.getNextCandidate(context, priorityEnum);
		var r3 = selector.getNextCandidate(context, statusEnum);
		var r4 = selector.getNextCandidate(context, priorityEnum);

		assertThat(r1).isEqualTo(active);
		assertThat(r2).isEqualTo(low);
		assertThat(r3).isNotEqualTo(active);
		assertThat(r4).isEqualTo(high);
	}

	// ========== Tests with literals having non-sequential values ==========

	@Test
	void shouldHandleLiteralsWithNonSequentialValues() {
		var low = createEEnumLiteral(priorityEnum, "LOW", 0);
		var medium = createEEnumLiteral(priorityEnum, "MEDIUM", 5);
		var high = createEEnumLiteral(priorityEnum, "HIGH", 10);

		var r1 = selector.getNextCandidate(context, priorityEnum);
		var r2 = selector.getNextCandidate(context, priorityEnum);
		var r3 = selector.getNextCandidate(context, priorityEnum);
		var r4 = selector.getNextCandidate(context, priorityEnum);

		assertThat(r1).isEqualTo(low);
		assertThat(r2).isEqualTo(medium);
		assertThat(r3).isEqualTo(high);
		assertThat(r4).isEqualTo(low); // Wrapped around
	}

	// ========== Test ordering based on enum definition ==========

	@Test
	void shouldReturnLiteralsInOrderTheyAreDefinedInEnum() {
		var literal1 = createEEnumLiteral(statusEnum, "FIRST", 10);
		var literal2 = createEEnumLiteral(statusEnum, "SECOND", 5);
		var literal3 = createEEnumLiteral(statusEnum, "THIRD", 0);

		var r1 = selector.getNextCandidate(context, statusEnum);
		var r2 = selector.getNextCandidate(context, statusEnum);
		var r3 = selector.getNextCandidate(context, statusEnum);

		// The order should match the order they were added to the enum, not their values
		assertThat(r1).isEqualTo(literal1);
		assertThat(r2).isEqualTo(literal2);
		assertThat(r3).isEqualTo(literal3);
	}

	// ========== Reset method tests ==========

	@Test
	void shouldResetStateAndStartFromBeginningAfterReset() {
		var active = createEEnumLiteral(statusEnum, "ACTIVE", 0);
		createEEnumLiteral(statusEnum, "INACTIVE", 1);
		createEEnumLiteral(statusEnum, "PENDING", 2);

		// Get first two literals
		var result1 = selector.getNextCandidate(context, statusEnum);
		selector.getNextCandidate(context, statusEnum);
		assertThat(result1).isEqualTo(active);

		// Reset
		selector.reset();

		// After reset, should start from beginning again
		var result3 = selector.getNextCandidate(context, statusEnum);
		assertThat(result3).isEqualTo(active);
	}

	@Test
	void shouldResetStateForAllEnums() {
		var active = createEEnumLiteral(statusEnum, "ACTIVE", 0);
		createEEnumLiteral(statusEnum, "INACTIVE", 1);
		var low = createEEnumLiteral(priorityEnum, "LOW", 0);
		createEEnumLiteral(priorityEnum, "HIGH", 1);

		// Get some literals for both enums
		var resultStatus1 = selector.getNextCandidate(context, statusEnum);
		var resultPriority1 = selector.getNextCandidate(context, priorityEnum);

		assertThat(resultStatus1).isEqualTo(active);
		assertThat(resultPriority1).isEqualTo(low);

		// Reset
		selector.reset();

		// After reset, both enums should start from beginning
		var resultStatus2 = selector.getNextCandidate(context, statusEnum);
		var resultPriority2 = selector.getNextCandidate(context, priorityEnum);

		assertThat(resultStatus2).isEqualTo(active);
		assertThat(resultPriority2).isEqualTo(low);
	}

	@Test
	void shouldHandleResetWithEmptyState() {
		// Reset on empty selector should not cause any issues
		selector.reset();

		var active = createEEnumLiteral(statusEnum, "ACTIVE", 0);
		var result = selector.getNextCandidate(context, statusEnum);

		assertThat(result).isEqualTo(active);
	}

	@Test
	void shouldHandleMultipleResetCalls() {
		var active = createEEnumLiteral(statusEnum, "ACTIVE", 0);
		createEEnumLiteral(statusEnum, "INACTIVE", 1);

		// Get some literals
		var result1 = selector.getNextCandidate(context, statusEnum);
		assertThat(result1).isEqualTo(active);

		// Multiple resets
		selector.reset();
		selector.reset();
		selector.reset();

		// Should still work correctly
		var result2 = selector.getNextCandidate(context, statusEnum);
		var result3 = selector.getNextCandidate(context, statusEnum);
		assertThat(result2).isEqualTo(active);
		assertThat(result3).isNotEqualTo(active);
	}

	// ========== Test with many literals ==========

	@Test
	void shouldHandleEnumWithManyLiterals() {
		var literals = new EEnumLiteral[10];
		for (int i = 0; i < 10; i++) {
			literals[i] = createEEnumLiteral(statusEnum, "LITERAL_" + i, i);
		}

		// Get all literals in round-robin order
		for (int i = 0; i < 10; i++) {
			var result = selector.getNextCandidate(context, statusEnum);
			assertThat(result).isEqualTo(literals[i]);
		}

		// Should wrap around
		var result = selector.getNextCandidate(context, statusEnum);
		assertThat(result).isEqualTo(literals[0]);
	}

	// ========== Test getOrCompute method directly through public interface ==========

	@Test
	void shouldReturnLiteralsFromGetOrComputeThroughPublicInterface() {
		var active = createEEnumLiteral(statusEnum, "ACTIVE", 0);
		var inactive = createEEnumLiteral(statusEnum, "INACTIVE", 1);

		// getOrCompute is tested indirectly through getNextCandidate
		var result1 = selector.getNextCandidate(context, statusEnum);
		var result2 = selector.getNextCandidate(context, statusEnum);

		assertThat(result1).isEqualTo(active);
		assertThat(result2).isEqualTo(inactive);
	}

	// ========== Edge case: literals added after first call ==========

	@Test
	void shouldSeeLiteralsAddedDynamically() {
		var active = createEEnumLiteral(statusEnum, "ACTIVE", 0);
		var inactive = createEEnumLiteral(statusEnum, "INACTIVE", 1);

		// First call
		var result1 = selector.getNextCandidate(context, statusEnum);
		assertThat(result1).isEqualTo(active);

		// Add a new literal after first call
		var pending = createEEnumLiteral(statusEnum, "PENDING", 2);

		// Second and third calls - will see the new literal because getELiterals() is not cached
		var result2 = selector.getNextCandidate(context, statusEnum);
		var result3 = selector.getNextCandidate(context, statusEnum);

		assertThat(result2).isEqualTo(inactive);
		assertThat(result3).isEqualTo(pending);
	}
}
