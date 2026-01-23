package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createAbstractEClass;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEClass;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEPackage;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createInstance;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createInterfaceEClass;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResource;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResourceSet;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createSubclass;
import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for EMFRoundRobinEClassCandidateSelector.
 * This class achieves 100% coverage of the round-robin EClass candidate selection functionality.
 */
class EMFRoundRobinEClassCandidateSelectorTest {

	private EMFRoundRobinEClassCandidateSelector selector;
	private ResourceSet resourceSet;
	private EPackage testPackage;
	private EClass baseClass;
	private EClass abstractClass;
	private EClass interfaceClass;
	private Resource contextResource;
	private EObject context;

	@BeforeEach
	void setUp() {
		resourceSet = createResourceSet();

		testPackage = createEPackage("test", "http://test", "test");

		// Register the package in the global registry
		EMFTestUtils.registerPackageForTest(testPackage);

		// Create Base class (concrete)
		baseClass = createEClass(testPackage, "Base");

		// Create Abstract class
		abstractClass = createAbstractEClass(testPackage, "AbstractClass");

		// Create Interface class
		interfaceClass = createInterfaceEClass(testPackage, "InterfaceClass");

		// Create a Resource for the context in the ResourceSet
		contextResource = createResource(resourceSet, "context.xmi");
		contextResource.getContents().add(testPackage);

		// Create a context instance
		context = createInstance(baseClass);
		contextResource.getContents().add(context);

		// Create selector
		selector = new EMFRoundRobinEClassCandidateSelector();
	}

	@AfterEach
	void tearDown() {
		EMFTestUtils.cleanupRegisteredPackages();
	}

	// ========== Tests with no candidates ==========

	@Test
	void shouldReturnNullWhenNoInstantiableSubclassesExist() {
		// Abstract class has no concrete subclasses
		var result = selector.getNextCandidate(context, abstractClass);

		assertThat(result).isNull();
	}

	@Test
	void shouldReturnNullWhenOnlyInterfaceExists() {
		// Interface class cannot be instantiated
		var result = selector.getNextCandidate(context, interfaceClass);

		assertThat(result).isNull();
	}

	// ========== Tests with single candidate ==========

	@Test
	void shouldReturnSelfWhenConcreteClassHasNoSubclasses() {
		var result = selector.getNextCandidate(context, baseClass);

		assertThat(result).isEqualTo(baseClass);
	}

	@Test
	void shouldReturnSameCandidateOnMultipleCalls() {
		var result1 = selector.getNextCandidate(context, baseClass);
		var result2 = selector.getNextCandidate(context, baseClass);
		var result3 = selector.getNextCandidate(context, baseClass);

		assertThat(result1).isEqualTo(baseClass);
		assertThat(result2).isEqualTo(baseClass);
		assertThat(result3).isEqualTo(baseClass);
	}

	@Test
	void shouldReturnOnlyConcreteSubclassWhenAbstractHasOneSubclass() {
		var concrete1 = createSubclass(testPackage, "Concrete1", abstractClass);

		var result = selector.getNextCandidate(context, abstractClass);

		assertThat(result).isEqualTo(concrete1);
	}

	// ========== Tests with multiple candidates - basic round-robin ==========

	@Test
	void shouldReturnCandidatesInRoundRobinOrder() {
		var sub1 = createSubclass(testPackage, "Sub1", baseClass);
		var sub2 = createSubclass(testPackage, "Sub2", baseClass);
		var sub3 = createSubclass(testPackage, "Sub3", baseClass);

		// First call: should return baseClass
		var result1 = selector.getNextCandidate(context, baseClass);
		assertThat(result1).isEqualTo(baseClass);

		// Second call: should return sub1
		var result2 = selector.getNextCandidate(context, baseClass);
		assertThat(result2).isEqualTo(sub1);

		// Third call: should return sub2
		var result3 = selector.getNextCandidate(context, baseClass);
		assertThat(result3).isEqualTo(sub2);

		// Fourth call: should return sub3
		var result4 = selector.getNextCandidate(context, baseClass);
		assertThat(result4).isEqualTo(sub3);
	}

	@Test
	void shouldWrapAroundWhenReachingEndOfCandidates() {
		var sub1 = createSubclass(testPackage, "Sub1", baseClass);
		var sub2 = createSubclass(testPackage, "Sub2", baseClass);

		var r1 = selector.getNextCandidate(context, baseClass);
		var r2 = selector.getNextCandidate(context, baseClass);
		var r3 = selector.getNextCandidate(context, baseClass);
		var r4 = selector.getNextCandidate(context, baseClass); // Should wrap to baseClass

		assertThat(r1).isEqualTo(baseClass);
		assertThat(r2).isEqualTo(sub1);
		assertThat(r3).isEqualTo(sub2);
		assertThat(r4).isEqualTo(baseClass); // Wrapped around
	}

	@Test
	void shouldContinueRoundRobinAcrossMultipleCycles() {
		var sub1 = createSubclass(testPackage, "Sub1", baseClass);
		var sub2 = createSubclass(testPackage, "Sub2", baseClass);

		// First cycle
		assertThat(selector.getNextCandidate(context, baseClass)).isEqualTo(baseClass);
		assertThat(selector.getNextCandidate(context, baseClass)).isEqualTo(sub1);
		assertThat(selector.getNextCandidate(context, baseClass)).isEqualTo(sub2);

		// Second cycle
		assertThat(selector.getNextCandidate(context, baseClass)).isEqualTo(baseClass);
		assertThat(selector.getNextCandidate(context, baseClass)).isEqualTo(sub1);
		assertThat(selector.getNextCandidate(context, baseClass)).isEqualTo(sub2);

		// Third cycle
		assertThat(selector.getNextCandidate(context, baseClass)).isEqualTo(baseClass);
	}

	// ========== Tests with multiple types (state isolation) ==========

	@Test
	void shouldMaintainSeparateStateForDifferentTypes() {
		var sub1 = createSubclass(testPackage, "Sub1", baseClass);
		var sub2 = createSubclass(testPackage, "Sub2", baseClass);

		var otherBase = createEClass(testPackage, "OtherBase");
		var otherSub1 = createSubclass(testPackage, "OtherSub1", otherBase);

		// Request for baseClass
		var resultBase1 = selector.getNextCandidate(context, baseClass);
		assertThat(resultBase1).isEqualTo(baseClass);

		var resultBase2 = selector.getNextCandidate(context, baseClass);
		assertThat(resultBase2).isEqualTo(sub1);

		// Request for otherBase (should start from index 0 for otherBase)
		var resultOther1 = selector.getNextCandidate(context, otherBase);
		assertThat(resultOther1).isEqualTo(otherBase);

		// Request for baseClass again (should continue from sub2)
		var resultBase3 = selector.getNextCandidate(context, baseClass);
		assertThat(resultBase3).isEqualTo(sub2);

		// Request for otherBase again (should continue from otherSub1)
		var resultOther2 = selector.getNextCandidate(context, otherBase);
		assertThat(resultOther2).isEqualTo(otherSub1);
	}

	@Test
	void shouldHandleInterleavedRequestsForDifferentTypes() {
		var sub1 = createSubclass(testPackage, "Sub1", baseClass);
		var otherBase = createEClass(testPackage, "OtherBase");

		var r1 = selector.getNextCandidate(context, baseClass);
		var r2 = selector.getNextCandidate(context, otherBase);
		var r3 = selector.getNextCandidate(context, baseClass);
		var r4 = selector.getNextCandidate(context, otherBase);

		assertThat(r1).isEqualTo(baseClass);
		assertThat(r2).isEqualTo(otherBase);
		assertThat(r3).isEqualTo(sub1);
		assertThat(r4).isEqualTo(otherBase);
	}

	// ========== Test caching behavior ==========

	@Test
	void shouldCacheCandidatesAcrossCalls() {
		var sub1 = createSubclass(testPackage, "Sub1", baseClass);

		// First call
		var result1 = selector.getNextCandidate(context, baseClass);
		assertThat(result1).isEqualTo(baseClass);

		// Create a new subclass after first call
		createSubclass(testPackage, "Sub2", baseClass);

		// Second call - should not see Sub2 because candidates are cached
		var result2 = selector.getNextCandidate(context, baseClass);
		assertThat(result2).isEqualTo(sub1);

		// Third call - should wrap back to baseClass, still not seeing Sub2
		var result3 = selector.getNextCandidate(context, baseClass);
		assertThat(result3).isEqualTo(baseClass);
	}

	// ========== Test with abstract classes and multiple inheritance levels ==========

	@Test
	void shouldIncludeAllConcreteSubclassesInHierarchy() {
		// Create hierarchy: abstractClass <- concrete1
		//                                  <- abstractSub <- concrete2
		var concrete1 = createSubclass(testPackage, "Concrete1", abstractClass);
		
		var abstractSub = createAbstractEClass(testPackage, "AbstractSub");
		abstractSub.getESuperTypes().add(abstractClass);
		
		var concrete2 = createSubclass(testPackage, "Concrete2", abstractSub);

		// Request abstract class candidates - should include both concrete subclasses
		var r1 = selector.getNextCandidate(context, abstractClass);
		var r2 = selector.getNextCandidate(context, abstractClass);
		var r3 = selector.getNextCandidate(context, abstractClass);

		assertThat(r1).isEqualTo(concrete1);
		assertThat(r2).isEqualTo(concrete2);
		assertThat(r3).isEqualTo(concrete1); // Wraps around
	}

	@Test
	void shouldExcludeAbstractAndInterfaceClasses() {
		// Create mixed hierarchy with abstract and interface classes
		var concrete1 = createSubclass(testPackage, "Concrete1", abstractClass);
		
		var abstractSub = createAbstractEClass(testPackage, "AbstractSub");
		abstractSub.getESuperTypes().add(abstractClass);
		
		var interfaceSub = createInterfaceEClass(testPackage, "InterfaceSub");
		interfaceSub.getESuperTypes().add(abstractClass);
		
		var concrete2 = createSubclass(testPackage, "Concrete2", abstractClass);

		// Should only return concrete classes
		var r1 = selector.getNextCandidate(context, abstractClass);
		var r2 = selector.getNextCandidate(context, abstractClass);
		var r3 = selector.getNextCandidate(context, abstractClass);

		assertThat(r1).isEqualTo(concrete1);
		assertThat(r2).isEqualTo(concrete2);
		assertThat(r3).isEqualTo(concrete1); // Wraps, no abstract or interface
	}

	@Test
	void shouldHandleDeepInheritanceHierarchy() {
		// Create deep hierarchy: base <- sub1 <- sub2 <- sub3
		var sub1 = createSubclass(testPackage, "Sub1", baseClass);
		var sub2 = createSubclass(testPackage, "Sub2", sub1);
		var sub3 = createSubclass(testPackage, "Sub3", sub2);

		// All should be included when requesting base
		var r1 = selector.getNextCandidate(context, baseClass);
		var r2 = selector.getNextCandidate(context, baseClass);
		var r3 = selector.getNextCandidate(context, baseClass);
		var r4 = selector.getNextCandidate(context, baseClass);
		var r5 = selector.getNextCandidate(context, baseClass);

		assertThat(r1).isEqualTo(baseClass);
		assertThat(r2).isEqualTo(sub1);
		assertThat(r3).isEqualTo(sub2);
		assertThat(r4).isEqualTo(sub3);
		assertThat(r5).isEqualTo(baseClass); // Wraps
	}

	// ========== Reset method tests ==========

	@Test
	void shouldResetStateAndRecomputeCandidatesAfterReset() {
		var sub1 = createSubclass(testPackage, "Sub1", baseClass);

		// Get first two candidates
		var result1 = selector.getNextCandidate(context, baseClass);
		var result2 = selector.getNextCandidate(context, baseClass);
		assertThat(result1).isEqualTo(baseClass);
		assertThat(result2).isEqualTo(sub1);

		// Reset
		selector.reset();

		// After reset, should start from baseClass again
		var result3 = selector.getNextCandidate(context, baseClass);
		assertThat(result3).isEqualTo(baseClass);
	}

	@Test
	void shouldRecomputeCandidatesAfterResetWhenNewSubclassesAdded() {
		var sub1 = createSubclass(testPackage, "Sub1", baseClass);

		// Get first two candidates
		var result1 = selector.getNextCandidate(context, baseClass);
		var result2 = selector.getNextCandidate(context, baseClass);
		assertThat(result1).isEqualTo(baseClass);
		assertThat(result2).isEqualTo(sub1);

		// Add a new subclass
		var sub2 = createSubclass(testPackage, "Sub2", baseClass);

		// Without reset, new subclass is not seen (cached) - wraps back to baseClass
		var result3 = selector.getNextCandidate(context, baseClass);
		assertThat(result3).isEqualTo(baseClass);

		// Reset to clear cache
		selector.reset();

		// After reset, should see all three candidates
		var result4 = selector.getNextCandidate(context, baseClass);
		var result5 = selector.getNextCandidate(context, baseClass);
		var result6 = selector.getNextCandidate(context, baseClass);
		assertThat(result4).isEqualTo(baseClass);
		assertThat(result5).isEqualTo(sub1);
		assertThat(result6).isEqualTo(sub2);
	}

	@Test
	void shouldResetStateForAllTypes() {
		createSubclass(testPackage, "Sub1", baseClass);
		var otherBase = createEClass(testPackage, "OtherBase");

		// Get some candidates for both types
		var resultBase1 = selector.getNextCandidate(context, baseClass);
		var resultOther1 = selector.getNextCandidate(context, otherBase);

		assertThat(resultBase1).isEqualTo(baseClass);
		assertThat(resultOther1).isEqualTo(otherBase);

		// Reset
		selector.reset();

		// After reset, both types should start from beginning
		var resultBase2 = selector.getNextCandidate(context, baseClass);
		var resultOther2 = selector.getNextCandidate(context, otherBase);

		assertThat(resultBase2).isEqualTo(baseClass);
		assertThat(resultOther2).isEqualTo(otherBase);
	}

	@Test
	void shouldHandleResetWithEmptyCache() {
		// Reset on empty selector should not cause any issues
		selector.reset();

		// Should work normally after reset
		var result = selector.getNextCandidate(context, baseClass);

		assertThat(result).isEqualTo(baseClass);
	}

	@Test
	void shouldHandleMultipleResetCalls() {
		var sub1 = createSubclass(testPackage, "Sub1", baseClass);

		// Get some candidates
		var result1 = selector.getNextCandidate(context, baseClass);
		assertThat(result1).isEqualTo(baseClass);

		// Multiple resets
		selector.reset();
		selector.reset();
		selector.reset();

		// Should still work correctly
		var result2 = selector.getNextCandidate(context, baseClass);
		var result3 = selector.getNextCandidate(context, baseClass);
		assertThat(result2).isEqualTo(baseClass);
		assertThat(result3).isEqualTo(sub1);
	}

	// ========== Edge cases ==========

	@Test
	void shouldHandleClassWithOnlyAbstractSubclasses() {
		var abstractSub1 = createAbstractEClass(testPackage, "AbstractSub1");
		abstractSub1.getESuperTypes().add(baseClass);
		
		var abstractSub2 = createAbstractEClass(testPackage, "AbstractSub2");
		abstractSub2.getESuperTypes().add(baseClass);

		// Should still return baseClass itself (it's concrete)
		var result = selector.getNextCandidate(context, baseClass);

		assertThat(result).isEqualTo(baseClass);
	}

	@Test
	void shouldHandleMultipleInheritance() {
		// Create a class that inherits from both baseClass and abstractClass
		var multiSub = createEClass(testPackage, "MultiSub");
		multiSub.getESuperTypes().add(baseClass);
		multiSub.getESuperTypes().add(abstractClass);

		// When querying baseClass, should include multiSub
		var r1 = selector.getNextCandidate(context, baseClass);
		var r2 = selector.getNextCandidate(context, baseClass);

		assertThat(r1).isEqualTo(baseClass);
		assertThat(r2).isEqualTo(multiSub);
	}

	@Test
	void shouldHandleDiamondInheritance() {
		// Create diamond: base <- sub1, sub2 <- diamond (inherits from both sub1 and sub2)
		var sub1 = createSubclass(testPackage, "Sub1", baseClass);
		var sub2 = createSubclass(testPackage, "Sub2", baseClass);
		
		var diamond = createEClass(testPackage, "Diamond");
		diamond.getESuperTypes().add(sub1);
		diamond.getESuperTypes().add(sub2);

		// All four should be included
		var r1 = selector.getNextCandidate(context, baseClass);
		var r2 = selector.getNextCandidate(context, baseClass);
		var r3 = selector.getNextCandidate(context, baseClass);
		var r4 = selector.getNextCandidate(context, baseClass);
		var r5 = selector.getNextCandidate(context, baseClass);

		assertThat(r1).isEqualTo(baseClass);
		assertThat(r2).isEqualTo(sub1);
		assertThat(r3).isEqualTo(sub2);
		assertThat(r4).isEqualTo(diamond);
		assertThat(r5).isEqualTo(baseClass); // Wraps
	}

	@Test
	void shouldReturnConsistentOrderAcrossMultipleCalls() {
		createSubclass(testPackage, "Sub1", baseClass);
		createSubclass(testPackage, "Sub2", baseClass);

		// First round
		var r1 = selector.getNextCandidate(context, baseClass);
		var r2 = selector.getNextCandidate(context, baseClass);
		var r3 = selector.getNextCandidate(context, baseClass);

		// Second round (after wrapping)
		var r4 = selector.getNextCandidate(context, baseClass);
		var r5 = selector.getNextCandidate(context, baseClass);
		var r6 = selector.getNextCandidate(context, baseClass);

		// Order should be consistent
		assertThat(r1).isEqualTo(r4);
		assertThat(r2).isEqualTo(r5);
		assertThat(r3).isEqualTo(r6);
	}
}
