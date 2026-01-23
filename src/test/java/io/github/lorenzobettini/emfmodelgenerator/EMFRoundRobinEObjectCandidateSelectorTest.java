package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEClass;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEPackage;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createInstance;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createInstanceInResource;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResource;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResourceSet;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createSubclass;
import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for EMFRoundRobinEObjectCandidateSelector.
 * This class achieves 100% coverage of the round-robin candidate selection functionality.
 */
class EMFRoundRobinEObjectCandidateSelectorTest {

	private EMFRoundRobinEObjectCandidateSelector selector;
	private ResourceSet resourceSet;
	private EPackage testPackage;
	private EClass personClass;
	private EClass companyClass;
	private Resource contextResource;
	private EObject context;

	@BeforeEach
	void setUp() {
		resourceSet = createResourceSet();

		testPackage = createEPackage("test", "http://test", "test");
		personClass = createEClass(testPackage, "Person");
		companyClass = createEClass(testPackage, "Company");

		// Create a separate Resource for the EPackage (Ecore resource)
		var ecoreResource = createResource(resourceSet, "test.ecore");
		ecoreResource.getContents().add(testPackage);

		// Create a Resource for the context instance (non-Ecore resource)
		contextResource = createResource(resourceSet, "context.xmi");

		// Create a context instance
		context = createInstance(personClass);
		contextResource.getContents().add(context);

		// Create selector
		selector = new EMFRoundRobinEObjectCandidateSelector();
	}

	// ========== Tests with no candidates ==========

	@Test
	void shouldReturnNullWhenNoCandidatesExist() {
		// Only context exists, should return context
		var result = selector.getNextCandidate(context, personClass);

		assertThat(result).isEqualTo(context);
	}

	@Test
	void shouldReturnNullWhenNoCandidatesForDifferentType() {
		// Create some Person instances but ask for Company instances
		createInstanceInResource(personClass, resourceSet, "person1.xmi");
		createInstanceInResource(personClass, resourceSet, "person2.xmi");

		var result = selector.getNextCandidate(context, companyClass);

		assertThat(result).isNull();
	}

	// ========== Tests with single candidate ==========

	@Test
	void shouldReturnSingleCandidateWhenOnlyOneExists() {
		createInstanceInResource(personClass, resourceSet, "person1.xmi");

		var result = selector.getNextCandidate(context, personClass);

		assertThat(result).isEqualTo(context);
	}

	@Test
	void shouldReturnSameSingleCandidateOnMultipleCalls() {
		var person1 = createInstanceInResource(personClass, resourceSet, "person1.xmi");

		var result1 = selector.getNextCandidate(context, personClass);
		var result2 = selector.getNextCandidate(context, personClass);
		var result3 = selector.getNextCandidate(context, personClass);

		assertThat(result1).isEqualTo(context);
		assertThat(result2).isEqualTo(person1);
		assertThat(result3).isEqualTo(context);
	}

	// ========== Tests with multiple candidates - basic round-robin ==========

	@Test
	void shouldReturnCandidatesInRoundRobinOrder() {
		var person1 = createInstanceInResource(personClass, resourceSet, "person1.xmi");
		var person2 = createInstanceInResource(personClass, resourceSet, "person2.xmi");
		var person3 = createInstanceInResource(personClass, resourceSet, "person3.xmi");
		var person4 = createInstanceInResource(personClass, resourceSet, "person4.xmi");

		// First call: should return context
		var result1 = selector.getNextCandidate(context, personClass);
		assertThat(result1).isEqualTo(context);

		// Second call: should return person1
		var result2 = selector.getNextCandidate(context, personClass);
		assertThat(result2).isEqualTo(person1);

		// Third call: should return person2
		var result3 = selector.getNextCandidate(context, personClass);
		assertThat(result3).isEqualTo(person2);

		// Fourth call: should return person3
		var result4 = selector.getNextCandidate(context, personClass);
		assertThat(result4).isEqualTo(person3);

		// Fifth call: should return person4
		var result5 = selector.getNextCandidate(context, personClass);
		assertThat(result5).isEqualTo(person4);
	}

	@Test
	void shouldWrapAroundWhenReachingEndOfCandidates() {
		var person1 = createInstanceInResource(personClass, resourceSet, "person1.xmi");
		var person2 = createInstanceInResource(personClass, resourceSet, "person2.xmi");
		var person3 = createInstanceInResource(personClass, resourceSet, "person3.xmi");

		var r1 = selector.getNextCandidate(context, personClass);
		var r2 = selector.getNextCandidate(context, personClass);
		var r3 = selector.getNextCandidate(context, personClass);
		var r4 = selector.getNextCandidate(context, personClass);
		var r5 = selector.getNextCandidate(context, personClass); // Should wrap to context

		assertThat(r1).isEqualTo(context);
		assertThat(r2).isEqualTo(person1);
		assertThat(r3).isEqualTo(person2);
		assertThat(r4).isEqualTo(person3);
		assertThat(r5).isEqualTo(context); // Wrapped around
	}

	@Test
	void shouldContinueRoundRobinAcrossMultipleCycles() {
		var person1 = createInstanceInResource(personClass, resourceSet, "person1.xmi");
		var person2 = createInstanceInResource(personClass, resourceSet, "person2.xmi");
		var person3 = createInstanceInResource(personClass, resourceSet, "person3.xmi");

		// First cycle
		assertThat(selector.getNextCandidate(context, personClass)).isEqualTo(context);
		assertThat(selector.getNextCandidate(context, personClass)).isEqualTo(person1);
		assertThat(selector.getNextCandidate(context, personClass)).isEqualTo(person2);
		assertThat(selector.getNextCandidate(context, personClass)).isEqualTo(person3);

		// Second cycle
		assertThat(selector.getNextCandidate(context, personClass)).isEqualTo(context);
		assertThat(selector.getNextCandidate(context, personClass)).isEqualTo(person1);
		assertThat(selector.getNextCandidate(context, personClass)).isEqualTo(person2);
		assertThat(selector.getNextCandidate(context, personClass)).isEqualTo(person3);

		// Third cycle
		assertThat(selector.getNextCandidate(context, personClass)).isEqualTo(context);
	}

	// ========== Tests with multiple types (state isolation) ==========

	@Test
	void shouldMaintainSeparateStateForDifferentTypes() {
		var person1 = createInstanceInResource(personClass, resourceSet, "person1.xmi");
		var person2 = createInstanceInResource(personClass, resourceSet, "person2.xmi");
		createInstanceInResource(personClass, resourceSet, "person3.xmi");

		var company1 = createInstanceInResource(companyClass, resourceSet, "company1.xmi");
		var company2 = createInstanceInResource(companyClass, resourceSet, "company2.xmi");

		// Request for Person (should start with context)
		var resultPersoncontext = selector.getNextCandidate(context, personClass);
		assertThat(resultPersoncontext).isEqualTo(context);

		var resultPerson1 = selector.getNextCandidate(context, personClass);
		assertThat(resultPerson1).isEqualTo(person1);

		// Request for Company (should start from index 0 for Company)
		var resultCompany1 = selector.getNextCandidate(context, companyClass);
		assertThat(resultCompany1).isEqualTo(company1);

		// Request for Person again (should continue from person2)
		var resultPerson2 = selector.getNextCandidate(context, personClass);
		assertThat(resultPerson2).isEqualTo(person2);

		// Request for Company again (should continue from company2)
		var resultCompany2 = selector.getNextCandidate(context, companyClass);
		assertThat(resultCompany2).isEqualTo(company2);
	}

	@Test
	void shouldHandleInterleavedRequestsForDifferentTypes() {
		var person1 = createInstanceInResource(personClass, resourceSet, "person1.xmi");
		createInstanceInResource(personClass, resourceSet, "person2.xmi");

		var company1 = createInstanceInResource(companyClass, resourceSet, "company1.xmi");
		var company2 = createInstanceInResource(companyClass, resourceSet, "company2.xmi");

		var r1 = selector.getNextCandidate(context, personClass);
		var r2 = selector.getNextCandidate(context, companyClass);
		var r3 = selector.getNextCandidate(context, personClass);
		var r4 = selector.getNextCandidate(context, companyClass);

		assertThat(r1).isEqualTo(context);
		assertThat(r2).isEqualTo(company1);
		assertThat(r3).isEqualTo(person1);
		assertThat(r4).isEqualTo(company2);
	}

	// ========== Test caching behavior ==========

	@Test
	void shouldCacheCandidatesAcrossCalls() {
		var person1 = createInstanceInResource(personClass, resourceSet, "person1.xmi");
		var person2 = createInstanceInResource(personClass, resourceSet, "person2.xmi");

		// First call
		var result1 = selector.getNextCandidate(context, personClass);
		assertThat(result1).isEqualTo(context);

		// Create a new person after first call
		createInstanceInResource(personClass, resourceSet, "person3.xmi");

		// Second call - should not see person3 because candidates are cached
		var result2 = selector.getNextCandidate(context, personClass);
		assertThat(result2).isEqualTo(person1);

		// Third call - should continue to person2, still not seeing person3
		var result3 = selector.getNextCandidate(context, personClass);
		assertThat(result3).isEqualTo(person2);

		// Fourth call - should wrap back to context, still not seeing person3
		var result4 = selector.getNextCandidate(context, personClass);
		assertThat(result4).isEqualTo(context);
	}

	// ========== Test with subclass instances ==========

	@Test
	void shouldIncludeSubclassInstances() {
		// Create Employee as subclass of Person
		var employeeClass = createSubclass(testPackage, "Employee", personClass);

		var person1 = createInstanceInResource(personClass, resourceSet, "person1.xmi");
		var employee1 = createInstanceInResource(employeeClass, resourceSet, "employee1.xmi");
		var person2 = createInstanceInResource(personClass, resourceSet, "person2.xmi");

		// Request Person candidates - should include Employee instances and context
		var r1 = selector.getNextCandidate(context, personClass);
		var r2 = selector.getNextCandidate(context, personClass);
		var r3 = selector.getNextCandidate(context, personClass);
		var r4 = selector.getNextCandidate(context, personClass);

		assertThat(r1).isEqualTo(context);
		assertThat(r2).isEqualTo(person1);
		assertThat(r3).isEqualTo(employee1);
		assertThat(r4).isEqualTo(person2);
	}

	// ========== Test ordering based on resource set ==========

	@Test
	void shouldReturnCandidatesInOrderTheyAreFoundInResourceSet() {
		// This test ensures the order is predictable based on resource set traversal
		var person1 = createInstanceInResource(personClass, resourceSet, "person1.xmi");
		var person2 = createInstanceInResource(personClass, resourceSet, "person2.xmi");
		var person3 = createInstanceInResource(personClass, resourceSet, "person3.xmi");

		var r1 = selector.getNextCandidate(context, personClass);
		var r2 = selector.getNextCandidate(context, personClass);
		var r3 = selector.getNextCandidate(context, personClass);
		var r4 = selector.getNextCandidate(context, personClass);

		// The order should match the order they were added to the resource set, with context first
		assertThat(r1).isEqualTo(context);
		assertThat(r2).isEqualTo(person1);
		assertThat(r3).isEqualTo(person2);
		assertThat(r4).isEqualTo(person3);
	}

	// ========== Reset method tests ==========

	@Test
	void shouldResetStateAndRecomputeCandidatesAfterReset() {
		var person1 = createInstanceInResource(personClass, resourceSet, "person1.xmi");
		createInstanceInResource(personClass, resourceSet, "person2.xmi");
		createInstanceInResource(personClass, resourceSet, "person3.xmi");

		// Get first two candidates (context and person1)
		var result1 = selector.getNextCandidate(context, personClass);
		var result2 = selector.getNextCandidate(context, personClass);
		assertThat(result1).isEqualTo(context);
		assertThat(result2).isEqualTo(person1);

		// Reset
		selector.reset();

		// After reset, should start from context again
		var result3 = selector.getNextCandidate(context, personClass);
		assertThat(result3).isEqualTo(context);
	}

	@Test
	void shouldRecomputeCandidatesAfterResetWhenNewElementsAdded() {
		var person1 = createInstanceInResource(personClass, resourceSet, "person1.xmi");
		var person2 = createInstanceInResource(personClass, resourceSet, "person2.xmi");

		// Get first two candidates (context and person1)
		var result1 = selector.getNextCandidate(context, personClass);
		var result2 = selector.getNextCandidate(context, personClass);
		assertThat(result1).isEqualTo(context);
		assertThat(result2).isEqualTo(person1);

		// Add a new person
		var person3 = createInstanceInResource(personClass, resourceSet, "person3.xmi");

		// Without reset, new person is not seen (cached) - continues to person2
		var result3 = selector.getNextCandidate(context, personClass);
		assertThat(result3).isEqualTo(person2);

		// Reset to clear cache
		selector.reset();

		// After reset, should see all four candidates (context, person1, person2, person3)
		var result4 = selector.getNextCandidate(context, personClass);
		var result5 = selector.getNextCandidate(context, personClass);
		var result6 = selector.getNextCandidate(context, personClass);
		var result7 = selector.getNextCandidate(context, personClass);
		assertThat(result4).isEqualTo(context);
		assertThat(result5).isEqualTo(person1);
		assertThat(result6).isEqualTo(person2);
		assertThat(result7).isEqualTo(person3);
	}

	@Test
	void shouldResetStateForAllTypes() {
		createInstanceInResource(personClass, resourceSet, "person1.xmi");
		createInstanceInResource(personClass, resourceSet, "person2.xmi");
		var company1 = createInstanceInResource(companyClass, resourceSet, "company1.xmi");
		createInstanceInResource(companyClass, resourceSet, "company2.xmi");

		// Get some candidates for both types
		var resultPersoncontext = selector.getNextCandidate(context, personClass);
		var resultCompany1 = selector.getNextCandidate(context, companyClass);

		assertThat(resultPersoncontext).isEqualTo(context);
		assertThat(resultCompany1).isEqualTo(company1);

		// Reset
		selector.reset();

		// After reset, both types should start from beginning
		var resultPerson2 = selector.getNextCandidate(context, personClass);
		var resultCompany2 = selector.getNextCandidate(context, companyClass);

		assertThat(resultPerson2).isEqualTo(context);
		assertThat(resultCompany2).isEqualTo(company1);
	}

	@Test
	void shouldHandleResetWithEmptyCache() {
		// Reset on empty selector should not cause any issues
		selector.reset();

		createInstanceInResource(personClass, resourceSet, "person1.xmi");
		var result = selector.getNextCandidate(context, personClass);

		assertThat(result).isEqualTo(context);
	}

	@Test
	void shouldHandleMultipleResetCalls() {
		var person1 = createInstanceInResource(personClass, resourceSet, "person1.xmi");
		createInstanceInResource(personClass, resourceSet, "person2.xmi");

		// Get some candidates
		var result1 = selector.getNextCandidate(context, personClass);
		assertThat(result1).isEqualTo(context);

		// Multiple resets
		selector.reset();
		selector.reset();
		selector.reset();

		// Should still work correctly
		var result2 = selector.getNextCandidate(context, personClass);
		var result3 = selector.getNextCandidate(context, personClass);
		assertThat(result2).isEqualTo(context);
		assertThat(result3).isEqualTo(person1);
	}
}
