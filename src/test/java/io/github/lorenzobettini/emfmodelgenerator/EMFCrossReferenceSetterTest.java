package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createInstance;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResourceSet;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.loadEPackage;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.validateModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for EMFCrossReferenceSetter.
 * This class achieves 100% coverage of the cross reference setter functionality.
 */
class EMFCrossReferenceSetterTest {

	private static final EcoreFactory ECORE_FACTORY = EcoreFactory.eINSTANCE;

	private EMFCrossReferenceSetter setter;
	private EPackage testPackage;
	private EClass ownerClass;
	private EClass referencedClass;
	private ResourceSet resourceSet;
	private Resource ownerResource;

	@BeforeEach
	void setUp() {
		// Create a ResourceSet with XMI factory registered
		resourceSet = createResourceSet();

		// Create a test package first
		testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");

		// Create a separate Resource for the EPackage (Ecore resource)
		URI ecoreUri = URI.createURI("temp://test.ecore");
		Resource ecoreResource = resourceSet.createResource(ecoreUri);
		if (ecoreResource != null) {
			ecoreResource.getContents().add(testPackage);
		}

		// Create a Resource for the owner instances (non-Ecore resource)
		URI uri = URI.createURI("temp://owner.xmi");
		ownerResource = resourceSet.createResource(uri);

		setter = new EMFCrossReferenceSetter();

		// Create owner class
		ownerClass = ECORE_FACTORY.createEClass();
		ownerClass.setName("Owner");
		testPackage.getEClassifiers().add(ownerClass);

		// Create referenced class
		referencedClass = ECORE_FACTORY.createEClass();
		referencedClass.setName("Referenced");
		testPackage.getEClassifiers().add(referencedClass);
	}

	// ========== Candidate selector strategy tests ==========

	@Test
	void testSetCandidateSelectorStrategy() {
		// Create existing instances
		EObject existing1 = createExistingInstance(referencedClass);
		createExistingInstance(referencedClass);

		// Create a custom strategy that always returns the first instance
		EMFCandidateSelectorStrategy<EClass, EObject> customStrategy = new EMFCandidateSelectorStrategy<EClass, EObject>() {
			@Override
			public EObject getNextCandidate(EObject context, EClass type) {
				return existing1;
			}

			@Override
			public boolean hasCandidates(EObject context, EClass type) {
				return true;
			}
		};

		setter.setCandidateSelectorStrategy(customStrategy);

		EReference reference = createNonContainmentReference("refs", true);
		EObject owner = createOwner();

		setter.setCrossReference(owner, reference);

		List<EObject> refs = EMFUtils.getAsEObjectsList(owner, reference);
		// Only one reference can be set since the strategy always returns the same object
		// and multi-valued references don't allow duplicates
		assertThat(refs).hasSize(1)
			.allMatch(obj -> obj == existing1);
	}

	// ========== Helper methods ==========

	private EReference createNonContainmentReference(String name, boolean isMany) {
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName(name);
		reference.setEType(referencedClass);
		reference.setContainment(false);
		reference.setLowerBound(0);
		reference.setUpperBound(isMany ? -1 : 1);
		ownerClass.getEStructuralFeatures().add(reference);
		return reference;
	}

	private EObject createOwner() {
		EObject owner = EcoreUtil.create(ownerClass);
		ownerResource.getContents().add(owner);
		return owner;
	}

	private EObject createExistingInstance(EClass eClass) {
		EObject instance = EcoreUtil.create(eClass);
		// Add to a separate resource in the same ResourceSet
		URI uri = URI.createURI("temp://existing_" + System.nanoTime() + ".xmi");
		Resource existingResource = resourceSet.createResource(uri);
		existingResource.getContents().add(instance);
		return instance;
	}

	// ========== Configuration tests ==========

	@Test
	void shouldSetDefaultMaxCount() {
		setter.setDefaultMaxCount(5);

		EReference reference = createNonContainmentReference("refs", true);
		EObject owner = createOwner();

		// Create 5 existing instances
		for (int i = 0; i < 5; i++) {
			createExistingInstance(referencedClass);
		}

		setter.setCrossReference(owner, reference);

		assertThat(owner.eGet(reference)).asInstanceOf(InstanceOfAssertFactories.list(EObject.class)).hasSize(5);
	}

	// ========== Invalid reference handling ==========

	@Test
	void shouldSkipAlreadySetSingleValuedReference() {
		EReference reference = createNonContainmentReference("ref", false);
		EObject owner = createOwner();
		EObject existingRef = createExistingInstance(referencedClass);

		// Set the reference first
		owner.eSet(reference, existingRef);

		// Try to set it again
		setter.setCrossReference(owner, reference);

		assertThat(owner.eGet(reference)).isSameAs(existingRef);
	}

	@Test
	void shouldSkipAlreadySetMultiValuedReference() {
		EReference reference = createNonContainmentReference("refs", true);
		EObject owner = createOwner();
		EObject existingRef1 = createExistingInstance(referencedClass);
		EObject existingRef2 = createExistingInstance(referencedClass);

		// Add to the reference first
		var list = EMFUtils.getAsEObjectsList(owner, reference);
		list.add(existingRef1);
		list.add(existingRef2);

		// Try to set it again
		setter.setCrossReference(owner, reference);

		assertThat(owner.eGet(reference)).asInstanceOf(InstanceOfAssertFactories.list(EObject.class)).containsExactly(existingRef1, existingRef2);
	}

	// ========== Custom function tests ==========

	@Test
	void shouldUseCustomFunctionForSingleValuedReference() {
		EReference reference = createNonContainmentReference("ref", false);
		EObject owner = createOwner();
		EObject customCandidate = createExistingInstance(referencedClass);
		createExistingInstance(referencedClass);

		setter.setFunctionFor(reference, o -> customCandidate);
		setter.setCrossReference(owner, reference);

		assertThat(owner.eGet(reference)).isSameAs(customCandidate);
	}

	@Test
	void shouldUseCustomFunctionForMultiValuedReference() {
		EReference reference = createNonContainmentReference("refs", true);
		EObject owner = createOwner();
		EObject customCandidate1 = createExistingInstance(referencedClass);
		EObject customCandidate2 = createExistingInstance(referencedClass);
		var candidateIterator = List.of(customCandidate1, customCandidate2).iterator();

		setter.setFunctionFor(reference, o -> candidateIterator.next());
		setter.setCrossReference(owner, reference);

		assertThat(owner.eGet(reference))
			.asInstanceOf(InstanceOfAssertFactories.list(EObject.class))
			.containsExactly(customCandidate1, customCandidate2);
	}

	@Test
	void shouldFallBackToRoundRobinWhenCustomFunctionReturnsNullForSingleValued() {
		EReference reference = createNonContainmentReference("ref", false);
		EObject owner = createOwner();
		EObject defaultCandidate = createExistingInstance(referencedClass);

		setter.setFunctionFor(reference, o -> null);
		setter.setCrossReference(owner, reference);

		assertThat(owner.eGet(reference)).isSameAs(defaultCandidate);
	}

	@Test
	void shouldFallBackToRoundRobinWhenCustomFunctionReturnsNullForMultiValued() {
		EReference reference = createNonContainmentReference("refs", true);
		EObject owner = createOwner();
		EObject defaultCandidate1 = createExistingInstance(referencedClass);
		EObject defaultCandidate2 = createExistingInstance(referencedClass);

		setter.setFunctionFor(reference, o -> null);
		setter.setCrossReference(owner, reference);

		assertThat(owner.eGet(reference))
			.asInstanceOf(InstanceOfAssertFactories.list(EObject.class))
			.containsExactly(defaultCandidate1, defaultCandidate2);
	}

	@Test
	void shouldFallBackToRoundRobinWhenCustomFunctionReturnsInvalidCandidate() {
		EReference reference = createNonContainmentReference("ref", false);
		reference.setEType(ownerClass); // Self-reference type
		EObject owner = createOwner();
		EObject validCandidate = createExistingInstance(ownerClass);

		// Custom function returns the owner itself (invalid self-reference)
		setter.setFunctionFor(reference, o -> owner);
		setter.setCrossReference(owner, reference);

		// Should use the valid candidate from round-robin, not the owner
		assertThat(owner.eGet(reference)).isSameAs(validCandidate);
	}

	// ========== Single-valued reference tests ==========

	@Test
	void shouldNotSetSingleValuedReferenceWhenNoExistingInstance() {
		EReference reference = createNonContainmentReference("ref", false);
		EObject owner = createOwner();

		setter.setCrossReference(owner, reference);

		// Reference should remain unset since no existing instances are available
		assertThat(owner.eGet(reference)).isNull();
	}

	@Test
	void shouldSetSingleValuedReferenceWithExistingInstance() {
		EReference reference = createNonContainmentReference("ref", false);
		EObject owner = createOwner();
		EObject existingRef = createExistingInstance(referencedClass);

		setter.setCrossReference(owner, reference);

		assertThat(owner.eGet(reference)).isSameAs(existingRef);
	}

	// ========== Multi-valued reference tests ==========

	@Test
	void shouldNotSetMultiValuedReferenceWhenNoExistingInstances() {
		EReference reference = createNonContainmentReference("refs", true);
		EObject owner = createOwner();

		setter.setCrossReference(owner, reference);

		assertThat(owner.eGet(reference)).asInstanceOf(InstanceOfAssertFactories.list(EObject.class)).isEmpty();
	}

	@Test
	void shouldSetMultiValuedReferenceWithExistingInstances() {
		EReference reference = createNonContainmentReference("refs", true);
		EObject owner = createOwner();

		// Create 3 existing instances
		EObject existing1 = createExistingInstance(referencedClass);
		EObject existing2 = createExistingInstance(referencedClass);
		createExistingInstance(referencedClass);

		setter.setCrossReference(owner, reference);

		// Should use existing instances (default count is 2)
		assertThat(owner.eGet(reference)).asInstanceOf(InstanceOfAssertFactories.list(EObject.class)).hasSize(2);
		assertThat(owner.eGet(reference)).asInstanceOf(InstanceOfAssertFactories.list(EObject.class)).containsExactly(existing1, existing2);
	}

	// ========== Bounds tests ==========

	@Test
	void shouldRespectUpperBound() {
		EReference reference = createNonContainmentReference("refs", true);
		reference.setUpperBound(3);
		setter.setDefaultMaxCount(5); // Set higher than upper bound

		EObject owner = createOwner();

		// Create 5 existing instances (more than upper bound)
		for (int i = 0; i < 5; i++) {
			createExistingInstance(referencedClass);
		}

		setter.setCrossReference(owner, reference);

		// Should use upper bound (3) instead of multiValuedCount (5)
		var list = EMFUtils.getAsList(owner, reference);
		assertThat(list).hasSize(3);
	}

	@Test
	void shouldRespectLowerBound() {
		EReference reference = createNonContainmentReference("refs", true);
		reference.setLowerBound(5);

		EObject owner = createOwner();

		// Create 5 existing instances to satisfy lower bound
		for (int i = 0; i < 5; i++) {
			createExistingInstance(referencedClass);
		}

		setter.setCrossReference(owner, reference);

		// Should use lower bound (5) instead of default (2)
		assertThat(owner.eGet(reference)).asInstanceOf(InstanceOfAssertFactories.list(EObject.class)).hasSize(5);
	}

	@Test
	void shouldHandleUnboundedMultiplicity() {
		EReference reference = createNonContainmentReference("refs", true);
		reference.setUpperBound(-1); // Unbounded

		EObject owner = createOwner();

		// Create 2 existing instances
		createExistingInstance(referencedClass);
		createExistingInstance(referencedClass);

		setter.setCrossReference(owner, reference);

		// Should use default count (2) when unbounded
		assertThat(owner.eGet(reference)).asInstanceOf(InstanceOfAssertFactories.list(EObject.class)).hasSize(2);
	}

	// ========== Subclass tests ==========

	@Test
	void shouldNotSetWhenNoInstantiableSubclasses() {
		// Make the referenced class abstract with no concrete subclasses
		referencedClass.setAbstract(true);

		EReference reference = createNonContainmentReference("ref", false);
		EObject owner = createOwner();

		setter.setCrossReference(owner, reference);

		assertThat(owner.eGet(reference)).isNull();
	}

	@Test
	void shouldUseExistingSubclassInstances() {
		// Make the referenced class abstract
		referencedClass.setAbstract(true);

		// Create multiple concrete subclasses
		EClass subclass1 = ECORE_FACTORY.createEClass();
		subclass1.setName("Subclass1");
		subclass1.getESuperTypes().add(referencedClass);
		testPackage.getEClassifiers().add(subclass1);

		EClass subclass2 = ECORE_FACTORY.createEClass();
		subclass2.setName("Subclass2");
		subclass2.getESuperTypes().add(referencedClass);
		testPackage.getEClassifiers().add(subclass2);

		// Create existing instances of both subclasses
		EObject existingSubclass1 = createExistingInstance(subclass1);
		EObject existingSubclass2 = createExistingInstance(subclass2);

		setter.setDefaultMaxCount(2);
		EReference reference = createNonContainmentReference("refs", true);
		EObject owner = createOwner();

		setter.setCrossReference(owner, reference);

		// Should use existing instances
		assertThat(owner.eGet(reference)).asInstanceOf(InstanceOfAssertFactories.list(EObject.class)).hasSize(2);

		var list = EMFUtils.getAsList(owner, reference);
		assertThat(list.get(0)).isSameAs(existingSubclass1);
		assertThat(list.get(1)).isSameAs(existingSubclass2);
	}

	// ========== Caching tests ==========

	@Test
	void shouldCacheInstantiableSubclasses() {
		EReference reference1 = createNonContainmentReference("ref1", false);
		EReference reference2 = createNonContainmentReference("ref2", false);

		EObject owner1 = createOwner();
		EObject owner2 = createOwner();

		// Create existing instances
		createExistingInstance(referencedClass);
		createExistingInstance(referencedClass);

		// First call - should compute and cache
		setter.setCrossReference(owner1, reference1);

		// Second call with different reference but same type - should use cache
		setter.setCrossReference(owner2, reference2);

		// Both should have references set
		assertThat(owner1.eGet(reference1)).isNotNull();
		assertThat(owner2.eGet(reference2)).isNotNull();
	}

	// ========== Opposite reference constraint tests ==========

	@Test
	void shouldSkipExistingInstanceWithMultiValuedOppositeAtUpperBound() {
		// Create bidirectional reference
		EClass targetClass = ECORE_FACTORY.createEClass();
		targetClass.setName("Target");
		testPackage.getEClassifiers().add(targetClass);

		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("target");
		reference.setEType(targetClass);
		reference.setContainment(false);
		ownerClass.getEStructuralFeatures().add(reference);

		EReference opposite = ECORE_FACTORY.createEReference();
		opposite.setName("owner");
		opposite.setEType(ownerClass);
		opposite.setContainment(false);
		opposite.setUpperBound(1); // Single-valued opposite
		targetClass.getEStructuralFeatures().add(opposite);

		reference.setEOpposite(opposite);
		opposite.setEOpposite(reference);

		// Create multiple existing instances - first two invalid, third valid
		EObject invalidTarget1 = createExistingInstance(targetClass);
		EObject otherOwner1 = EcoreUtil.create(ownerClass);
		invalidTarget1.eSet(opposite, otherOwner1);

		EObject invalidTarget2 = createExistingInstance(targetClass);
		EObject otherOwner2 = EcoreUtil.create(ownerClass);
		invalidTarget2.eSet(opposite, otherOwner2);

		EObject validTarget = createExistingInstance(targetClass);
		// validTarget's opposite is not set

		// Try to set reference - should skip first two and use the third
		EObject owner = createOwner();
		setter.setCrossReference(owner, reference);

		EObject referenced = (EObject) owner.eGet(reference);
		assertThat(referenced).isSameAs(validTarget);
	}

	@Test
	void shouldUseMultipleValidExistingInstancesForMultiValuedReference() {
		// Create bidirectional reference
		EClass targetClass = ECORE_FACTORY.createEClass();
		targetClass.setName("Target");
		testPackage.getEClassifiers().add(targetClass);

		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("targets");
		reference.setEType(targetClass);
		reference.setContainment(false);
		reference.setUpperBound(-1); // Multi-valued
		ownerClass.getEStructuralFeatures().add(reference);

		EReference opposite = ECORE_FACTORY.createEReference();
		opposite.setName("owners");
		opposite.setEType(ownerClass);
		opposite.setContainment(false);
		opposite.setUpperBound(2); // Upper bound of 2
		targetClass.getEStructuralFeatures().add(opposite);

		reference.setEOpposite(opposite);
		opposite.setEOpposite(reference);

		// Create mix of valid and invalid existing instances
		EObject invalidTarget = createExistingInstance(targetClass);
		EObject otherOwner1 = EcoreUtil.create(ownerClass);
		EObject otherOwner2 = EcoreUtil.create(ownerClass);
		var ownersList = EMFUtils.getAsList(invalidTarget, opposite);
		ownersList.add(otherOwner1);
		ownersList.add(otherOwner2); // At upper bound

		EObject validTarget1 = createExistingInstance(targetClass);
		// validTarget1's opposite is empty (valid)

		EObject validTarget2 = createExistingInstance(targetClass);
		// validTarget2's opposite is empty (valid)

		// Try to set multi-valued reference needing 2 instances
		EObject owner = createOwner();
		setter.setCrossReference(owner, reference);

		var targetsList = EMFUtils.getAsEObjectsList(owner, reference);
		assertThat(targetsList).containsExactlyInAnyOrder(validTarget1, validTarget2);
	}

	// ========== Round-robin selection tests ==========

	@Test
	void shouldUseRoundRobinSelectionForMultipleReferences() {
		// Create multiple referenced instances
		EObject existing1 = createExistingInstance(referencedClass);
		EObject existing2 = createExistingInstance(referencedClass);
		EObject existing3 = createExistingInstance(referencedClass);

		// Create three owners with single-valued references
		EReference reference1 = createNonContainmentReference("ref1", false);
		EObject owner1 = createOwner();
		
		EReference reference2 = createNonContainmentReference("ref2", false);
		EObject owner2 = createOwner();
		
		EReference reference3 = createNonContainmentReference("ref3", false);
		EObject owner3 = createOwner();

		// Set references - should use round-robin
		setter.setCrossReference(owner1, reference1);
		setter.setCrossReference(owner2, reference2);
		setter.setCrossReference(owner3, reference3);

		// Should get different instances in round-robin order
		assertThat(owner1.eGet(reference1)).isSameAs(existing1);
		assertThat(owner2.eGet(reference2)).isSameAs(existing2);
		assertThat(owner3.eGet(reference3)).isSameAs(existing3);
	}

	@Test
	void shouldWrapAroundInRoundRobinSelection() {
		// Create two referenced instances
		EObject existing1 = createExistingInstance(referencedClass);
		EObject existing2 = createExistingInstance(referencedClass);

		// Create three owners with single-valued references
		EReference reference1 = createNonContainmentReference("ref1", false);
		EObject owner1 = createOwner();
		
		EReference reference2 = createNonContainmentReference("ref2", false);
		EObject owner2 = createOwner();
		
		EReference reference3 = createNonContainmentReference("ref3", false);
		EObject owner3 = createOwner();

		// Set references - should use round-robin and wrap around
		setter.setCrossReference(owner1, reference1);
		setter.setCrossReference(owner2, reference2);
		setter.setCrossReference(owner3, reference3);

		// Should get instances in round-robin order with wrap-around
		assertThat(owner1.eGet(reference1)).isSameAs(existing1);
		assertThat(owner2.eGet(reference2)).isSameAs(existing2);
		assertThat(owner3.eGet(reference3)).isSameAs(existing1); // Wrapped around
	}

	@Test
	void shouldUseRoundRobinForMultiValuedReferences() {
		// Create three referenced instances
		EObject existing1 = createExistingInstance(referencedClass);
		EObject existing2 = createExistingInstance(referencedClass);
		EObject existing3 = createExistingInstance(referencedClass);

		EReference reference = createNonContainmentReference("refs", true);
		EObject owner = createOwner();

		// Set multi-valued reference (default count is 2)
		setter.setCrossReference(owner, reference);

		// Should get first two instances in round-robin order
		var list = EMFUtils.getAsList(owner, reference);
		assertThat(list).hasSize(2);
		assertThat(list.get(0)).isSameAs(existing1);
		assertThat(list.get(1)).isSameAs(existing2);

		// Create another owner and set reference
		EObject owner2 = createOwner();
		setter.setCrossReference(owner2, reference);

		// Should continue round-robin from where it left off
		var list2 = EMFUtils.getAsList(owner2, reference);
		assertThat(list2).hasSize(2);
		assertThat(list2.get(0)).isSameAs(existing3);
		assertThat(list2.get(1)).isSameAs(existing1); // Wrapped around
	}

	// ========== Reset method tests ==========

	@Test
	void shouldResetRoundRobinSelection() {
		// Create two referenced instances
		EObject existing1 = createExistingInstance(referencedClass);
		createExistingInstance(referencedClass);

		// Create first owner and set reference
		EReference reference1 = createNonContainmentReference("ref1", false);
		EObject owner1 = createOwner();
		setter.setCrossReference(owner1, reference1);
		assertThat(owner1.eGet(reference1)).isSameAs(existing1);

		// Reset the selector
		setter.reset();

		// Create second owner and set reference - should start from beginning again
		EReference reference2 = createNonContainmentReference("ref2", false);
		EObject owner2 = createOwner();
		setter.setCrossReference(owner2, reference2);
		assertThat(owner2.eGet(reference2)).isSameAs(existing1); // Started from beginning
	}

	@Test
	void shouldRecomputeCandidatesAfterReset() {
		// Create two referenced instances
		EObject existing1 = createExistingInstance(referencedClass);
		EObject existing2 = createExistingInstance(referencedClass);

		// Create first owner and set reference
		EReference reference1 = createNonContainmentReference("ref1", false);
		EObject owner1 = createOwner();
		setter.setCrossReference(owner1, reference1);
		assertThat(owner1.eGet(reference1)).isSameAs(existing1);

		// Add a new instance
		createExistingInstance(referencedClass);

		// Without reset, new instance won't be seen (it's cached)
		EReference reference2 = createNonContainmentReference("ref2", false);
		EObject owner2 = createOwner();
		setter.setCrossReference(owner2, reference2);
		assertThat(owner2.eGet(reference2)).isSameAs(existing2); // Gets existing2

		// Reset to clear cache
		setter.reset();

		// After reset, should see all three instances
		EReference reference3 = createNonContainmentReference("ref3", false);
		EObject owner3 = createOwner();
		setter.setCrossReference(owner3, reference3);
		assertThat(owner3.eGet(reference3)).isSameAs(existing1); // Starts from beginning with refreshed candidates
	}

	@Test
	void shouldHandleResetWithNoReferences() {
		// Reset when no references have been set should not cause issues
		setter.reset();

		// Should work normally after reset
		EObject existing1 = createExistingInstance(referencedClass);
		EReference reference = createNonContainmentReference("ref", false);
		EObject owner = createOwner();
		setter.setCrossReference(owner, reference);

		assertThat(owner.eGet(reference)).isSameAs(existing1);
	}

	@Test
	void shouldHandleAllCandidatesFilteredByOppositeReference() {
		// Create bidirectional reference with single-valued opposite
		EClass targetClass = ECORE_FACTORY.createEClass();
		targetClass.setName("Target");
		testPackage.getEClassifiers().add(targetClass);

		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("target");
		reference.setEType(targetClass);
		reference.setContainment(false);
		ownerClass.getEStructuralFeatures().add(reference);

		EReference opposite = ECORE_FACTORY.createEReference();
		opposite.setName("owner");
		opposite.setEType(ownerClass);
		opposite.setContainment(false);
		opposite.setUpperBound(1); // Single-valued opposite
		targetClass.getEStructuralFeatures().add(opposite);

		reference.setEOpposite(opposite);
		opposite.setEOpposite(reference);

		// Create instances where all have their opposite already set
		EObject invalidTarget1 = createExistingInstance(targetClass);
		EObject otherOwner1 = EcoreUtil.create(ownerClass);
		invalidTarget1.eSet(opposite, otherOwner1);

		EObject invalidTarget2 = createExistingInstance(targetClass);
		EObject otherOwner2 = EcoreUtil.create(ownerClass);
		invalidTarget2.eSet(opposite, otherOwner2);

		// Try to set reference - should return null since all candidates are filtered
		EObject owner = createOwner();
		setter.setCrossReference(owner, reference);

		// Reference should remain unset
		assertThat(owner.eGet(reference)).isNull();
	}

	// ========== Documentation/Example tests with simplelibrary.ecore ==========

	@Test
	void shouldSetCrossReferencesInSingleResourceWithLibraryBooksAndAuthors() {
		final var ePackage = loadEPackage("inputs/simplelibrary.ecore");
		final ResourceSet libraryResourceSet = createResourceSet();
		final var libraryResource = libraryResourceSet.createResource(URI.createURI("temp://library.xmi"));

		// Create library with 3 books
		final var library = createInstance(ePackage, "Library");
		library.eSet(library.eClass().getEStructuralFeature("name"), "City Library");

		final var book1 = createInstance(ePackage, "Book");
		book1.eSet(book1.eClass().getEStructuralFeature("title"), "1984");

		final var book2 = createInstance(ePackage, "Book");
		book2.eSet(book2.eClass().getEStructuralFeature("title"), "Animal Farm");

		final var book3 = createInstance(ePackage, "Book");
		book3.eSet(book3.eClass().getEStructuralFeature("title"), "Brave New World");

		// Create 2 authors
		final var author1 = createInstance(ePackage, "Author");
		author1.eSet(author1.eClass().getEStructuralFeature("name"), "George Orwell");

		final var author2 = createInstance(ePackage, "Author");
		author2.eSet(author2.eClass().getEStructuralFeature("name"), "Aldous Huxley");

		// Set containment references
		library.eSet(library.eClass().getEStructuralFeature("books"), java.util.List.of(book1, book2, book3));
		library.eSet(library.eClass().getEStructuralFeature("authors"), java.util.List.of(author1, author2));
		libraryResource.getContents().add(library);

		// Now set the cross-references using the setter
		final var crossRefSetter = new EMFCrossReferenceSetter();
		final var bookAuthorsRef = (EReference) book1.eClass().getEStructuralFeature("authors");
		crossRefSetter.setCrossReference(book1, bookAuthorsRef);
		crossRefSetter.setCrossReference(book2, bookAuthorsRef);
		crossRefSetter.setCrossReference(book3, bookAuthorsRef);

		// Verify using pretty printer
		final var printer = new EMFPrettyPrinter();
		final var result = printer.prettyPrint(library);

		assertThat(result).isEqualTo("""
			Library
			  name: "City Library"
			  books (3):
			    [0] Book
			      title: "1984"
			      authors (opposite) (2):
			        [0] -> Author (authors[0])
			        [1] -> Author (authors[1])
			    [1] Book
			      title: "Animal Farm"
			      authors (opposite) (2):
			        [0] -> Author (authors[0])
			        [1] -> Author (authors[1])
			    [2] Book
			      title: "Brave New World"
			      authors (opposite) (2):
			        [0] -> Author (authors[0])
			        [1] -> Author (authors[1])
			  authors (2):
			    [0] Author
			      name: "George Orwell"
			      books (opposite) (3):
			        [0] -> Book (books[0])
			        [1] -> Book (books[1])
			        [2] -> Book (books[2])
			    [1] Author
			      name: "Aldous Huxley"
			      books (opposite) (3):
			        [0] -> Book (books[0])
			        [1] -> Book (books[1])
			        [2] -> Book (books[2])
			""");

		// Validate the model
		validateModel(library);
	}

	@Test
	void shouldSetCrossReferencesAcrossMultipleResources() {
		final var ePackage = loadEPackage("inputs/simplelibrary.ecore");
		final ResourceSet libraryResourceSet = createResourceSet();

		// Create first library with a book in one resource
		final var library1Resource = libraryResourceSet.createResource(URI.createFileURI("library1.xmi"));
		final var library1 = createInstance(ePackage, "Library");
		library1.eSet(library1.eClass().getEStructuralFeature("name"), "Public Library");

		final var book = createInstance(ePackage, "Book");
		book.eSet(book.eClass().getEStructuralFeature("title"), "The Great Gatsby");

		library1.eSet(library1.eClass().getEStructuralFeature("books"), java.util.List.of(book));
		library1Resource.getContents().add(library1);

		// Create second library with an author in another resource
		final var library2Resource = libraryResourceSet.createResource(URI.createFileURI("library2.xmi"));
		final var library2 = createInstance(ePackage, "Library");
		library2.eSet(library2.eClass().getEStructuralFeature("name"), "University Library");

		final var author = createInstance(ePackage, "Author");
		author.eSet(author.eClass().getEStructuralFeature("name"), "F. Scott Fitzgerald");

		library2.eSet(library2.eClass().getEStructuralFeature("authors"), java.util.List.of(author));
		library2Resource.getContents().add(library2);

		// Set cross-references using the setter
		// Book (in library1) references author (in library2) across resources
		final var crossRefSetter = new EMFCrossReferenceSetter();
		final var bookAuthorsRef = (EReference) book.eClass().getEStructuralFeature("authors");
		crossRefSetter.setCrossReference(book, bookAuthorsRef);

		// Verify using pretty printer on the resource set
		final var printer = new EMFPrettyPrinter();
		final var result = printer.prettyPrint(libraryResourceSet);

		assertThat(result).isEqualTo("""
			Resource: library1.xmi
			  Library
			    name: "Public Library"
			    books (1):
			      [0] Book
			        title: "The Great Gatsby"
			        authors (opposite) (1):
			          [0] -> Author (library2.xmi > authors[0])
			Resource: library2.xmi
			  Library
			    name: "University Library"
			    authors (1):
			      [0] Author
			        name: "F. Scott Fitzgerald"
			        books (opposite) (1):
			          [0] -> Book (library1.xmi > books[0])
			""");

		// Validate both models
		validateModel(library1);
		validateModel(library2);
	}

	@Test
	void shouldSetCrossReferencesWithBookInSeparateResourceFromLibrary() {
		final var ePackage = loadEPackage("inputs/simplelibrary.ecore");
		final ResourceSet libraryResourceSet = createResourceSet();

		// Create book in its own resource (NOT contained in a library)
		final var bookResource = libraryResourceSet.createResource(URI.createFileURI("book.xmi"));
		final var book = createInstance(ePackage, "Book");
		book.eSet(book.eClass().getEStructuralFeature("title"), "The Great Gatsby");
		bookResource.getContents().add(book);

		// Create library with an author in another resource
		final var libraryResource = libraryResourceSet.createResource(URI.createFileURI("library.xmi"));
		final var library = createInstance(ePackage, "Library");
		library.eSet(library.eClass().getEStructuralFeature("name"), "Public Library");

		final var author = createInstance(ePackage, "Author");
		author.eSet(author.eClass().getEStructuralFeature("name"), "F. Scott Fitzgerald");

		library.eSet(library.eClass().getEStructuralFeature("authors"), java.util.List.of(author));
		libraryResource.getContents().add(library);

		// Set cross-references using the setter
		final var crossRefSetter = new EMFCrossReferenceSetter();
		
		// Book references author in different resource (this will work - bidirectional cross)
		final var bookAuthorsRef = (EReference) book.eClass().getEStructuralFeature("authors");
		crossRefSetter.setCrossReference(book, bookAuthorsRef);
		
		// Note: book.library is a container reference (opposite of containment)
		// When using the populator, such references are filtered out by EMFUtils.isValidReference
		// However, when calling the setter directly, the setter doesn't validate
		// We skip calling the setter with the container reference since it's invalid
		// See the comment in testSettingBidirectionalContainmentReferenceAcrossResources

		// Verify using pretty printer on the resource set
		final var printer = new EMFPrettyPrinter();
		final var result = printer.prettyPrint(libraryResourceSet);

		assertThat(result).isEqualTo("""
			Resource: book.xmi
			  Book
			    title: "The Great Gatsby"
			    authors (opposite) (1):
			      [0] -> Author (library.xmi > authors[0])
			Resource: library.xmi
			  Library
			    name: "Public Library"
			    authors (1):
			      [0] Author
			        name: "F. Scott Fitzgerald"
			        books (opposite) (1):
			          [0] -> Book (book.xmi)
			""");

		// Validate both models
		validateModel(book);
		validateModel(library);
	}

	@Test
	void shouldSetCrossReferencesWithBookInSeparateResourceFromAuthor() {
		final var ePackage = loadEPackage("inputs/simplelibrary.ecore");
		final ResourceSet libraryResourceSet = createResourceSet();

		// Create author in its own resource (NOT contained in a library)
		final var authorResource = libraryResourceSet.createResource(URI.createFileURI("author.xmi"));
		final var author = createInstance(ePackage, "Author");
		author.eSet(author.eClass().getEStructuralFeature("name"), "F. Scott Fitzgerald");
		authorResource.getContents().add(author);

		// Create book in its own resource (NOT contained in a library)
		final var bookResource = libraryResourceSet.createResource(URI.createFileURI("book.xmi"));
		final var book = createInstance(ePackage, "Book");
		book.eSet(book.eClass().getEStructuralFeature("title"), "The Great Gatsby");
		bookResource.getContents().add(book);

		// Set cross-references using the setter
		final var crossRefSetter = new EMFCrossReferenceSetter();
		final var bookAuthorsRef = (EReference) book.eClass().getEStructuralFeature("authors");
		crossRefSetter.setCrossReference(book, bookAuthorsRef);

		// Verify using pretty printer on the resource set
		final var printer = new EMFPrettyPrinter();
		final var result = printer.prettyPrint(libraryResourceSet);
		assertThat(result).isEqualTo("""
			Resource: author.xmi
			  Author
			    name: "F. Scott Fitzgerald"
			    books (opposite) (1):
			      [0] -> Book (book.xmi)
			Resource: book.xmi
			  Book
			    title: "The Great Gatsby"
			    authors (opposite) (1):
			      [0] -> Author (author.xmi)
			""");
		// Validate both models
		validateModel(author);
		validateModel(book);
	}

	/**
	 * This works for multi-valued references with opposite references.
	 */
	@Test
	void shouldSkipAlreadyPresentCandidatesAndContinueWithOthers() {
		// This test covers the false branch of referencedEObject == firstCandidate
		// in the setMultiReference method's duplicate detection loop
		
		// Create 3 existing instances (enough to fill the multi-valued reference)
		EObject existing1 = createExistingInstance(referencedClass);
		EObject existing2 = createExistingInstance(referencedClass);
		EObject existing3 = createExistingInstance(referencedClass);

		// Set multiValuedCount to 3
		setter.setDefaultMaxCount(3);

		EReference reference = createNonContainmentReference("refs", true);
		EReference opposite = createNonContainmentReference("owners", false);
		reference.setEOpposite(opposite);
		opposite.setEOpposite(reference);
		EObject owner = createOwner();

		// Manually add existing1 to the list first (simulating it's already there)
		var list = EMFUtils.getAsList(owner, reference);
		list.add(existing1);

		// Now call setReference - it should skip existing1 (which is already in the list)
		// and add existing2 and existing3 instead
		// The round-robin selector would give us: existing1, existing2, existing3, existing1 (wrap around)
		// But existing1 is already in the list, so we skip it and continue with existing2, existing3
		setter.setCrossReference(owner, reference);

		// Should have 3 items total: existing1 (manually added), existing2, existing3
		assertThat(list).containsExactly(existing1, existing2, existing3);
		
		// This test ensures that the condition that referencedEObject == firstCandidate
		// evaluates to false when we encounter existing1 again in the loop but have
		// successfully added other candidates (existing2, existing3) before wrapping around
	}

	@Test
	void shouldNotAllowSelfReferenceByDefault() {
		// Create a reference that points to the same type as the owner
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("selfRef");
		reference.setEType(ownerClass); // Reference points to Owner type
		reference.setContainment(false);
		reference.setLowerBound(0);
		reference.setUpperBound(1);
		ownerClass.getEStructuralFeatures().add(reference);

		EObject owner = createOwner();

		// The only candidate is the owner itself
		setter.setCrossReference(owner, reference);

		// Should not set the reference since self-reference is not allowed by default
		assertThat(owner.eIsSet(reference)).isFalse();
	}

	@Test
	void shouldAllowSelfReferenceWhenOverridden() {
		// Create a reference that points to the same type as the owner
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("selfRef");
		reference.setEType(ownerClass); // Reference points to Owner type
		reference.setContainment(false);
		reference.setLowerBound(0);
		reference.setUpperBound(1);
		ownerClass.getEStructuralFeatures().add(reference);

		EObject owner = createOwner();

		// Create a custom setter that allows cycles
		var customSetter = new EMFCrossReferenceSetter() {
			@Override
			protected boolean allowCycleFor(EObject owner, EReference reference) {
				return true;
			}
		};

		// The only candidate is the owner itself
		customSetter.setCrossReference(owner, reference);

		// Should set the reference to itself
		assertThat(owner.eGet(reference)).isEqualTo(owner);
	}

	@Test
	void shouldAllowSelfReferenceForMultiValuedWhenOverridden() {
		// Create a reference that points to the same type as the owner
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("selfRefs");
		reference.setEType(ownerClass); // Reference points to Owner type
		reference.setContainment(false);
		reference.setLowerBound(0);
		reference.setUpperBound(-1); // Multi-valued
		ownerClass.getEStructuralFeatures().add(reference);

		EObject owner = createOwner();

		// Create a custom setter that allows cycles
		var customSetter = new EMFCrossReferenceSetter() {
			@Override
			protected boolean allowCycleFor(EObject owner, EReference reference) {
				return true;
			}
		};

		// The only candidate is the owner itself
		customSetter.setCrossReference(owner, reference);

		// Should add the owner once (duplicates are still avoided)
		var list = EMFUtils.getAsList(owner, reference);
		assertThat(list).hasSize(1).containsExactly(owner);
	}

	@Test
	void shouldMixSelfReferenceWithOthersInMultiValued() {
		// Create another Owner instance
		EObject existing = createExistingInstance(ownerClass);
		
		// Create a reference that points to the same type as the owner
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("selfRefs");
		reference.setEType(ownerClass); // Reference points to Owner type
		reference.setContainment(false);
		reference.setLowerBound(0);
		reference.setUpperBound(-1); // Multi-valued
		ownerClass.getEStructuralFeatures().add(reference);

		EObject owner = createOwner();

		// Create a custom setter that allows cycles
		var customSetter = new EMFCrossReferenceSetter() {
			@Override
			protected boolean allowCycleFor(EObject owner, EReference reference) {
				return true;
			}
		};

		// Set count to 2
		customSetter.setDefaultMaxCount(2);

		customSetter.setCrossReference(owner, reference);

		// Should have both the existing instance and the owner itself
		var list = EMFUtils.getAsList(owner, reference);
		assertThat(list).hasSize(2).contains(owner, existing);
	}

	@Test
	void testShouldSetFeatureWithReference() {
		EcoreFactory ecoreFactory = EcoreFactory.eINSTANCE;
		// Create a valid reference feature
		var singleReference = ecoreFactory.createEReference();
		singleReference.setName("reference");
		singleReference.setEType(ownerClass);
		ownerClass.getEStructuralFeatures().add(singleReference);
		// Create a valid multi-valued reference feature
		var multiReference = ecoreFactory.createEReference();
		multiReference.setName("multiReference");
		multiReference.setEType(ownerClass);
		multiReference.setUpperBound(EStructuralFeature.UNBOUNDED_MULTIPLICITY);
		multiReference.setLowerBound(0);
		ownerClass.getEStructuralFeatures().add(multiReference);
		// create two multi references with opposite
		var oppositeReference1 = ecoreFactory.createEReference();
		oppositeReference1.setName("opposite1");
		oppositeReference1.setEType(ownerClass);
		oppositeReference1.setUpperBound(EStructuralFeature.UNBOUNDED_MULTIPLICITY);
		oppositeReference1.setLowerBound(0);
		var oppositeReference2 = ecoreFactory.createEReference();
		oppositeReference2.setName("opposite2");
		oppositeReference2.setEType(ownerClass);
		oppositeReference1.setEOpposite(oppositeReference2);
		oppositeReference2.setEOpposite(oppositeReference1);
		ownerClass.getEStructuralFeatures().add(oppositeReference1);
		ownerClass.getEStructuralFeatures().add(oppositeReference2);

		var owner = EcoreUtil.create(ownerClass);

		// Initially, the feature is not set, so it should be set
		assertTrue(setter.shouldSetFeature(owner, singleReference));
		// Set the feature value
		owner.eSet(singleReference, EcoreUtil.create(ownerClass));
		// Now, the feature is set, so it should not be set again
		assertFalse(setter.shouldSetFeature(owner, singleReference));
		// unset the feature
		owner.eUnset(singleReference);
		// After unset, it should be set again (validity checking is done in the populator)
		assertTrue(setter.shouldSetFeature(owner, singleReference));

		// Initially, the multi-valued feature is not set, so it should be set
		assertTrue(setter.shouldSetFeature(owner, multiReference));
		// Set the feature value with one element
		var list = EMFUtils.getAsList(owner, multiReference);
		list.add(EcoreUtil.create(ownerClass));
		// Now, the feature is set, so it should not be set again
		assertFalse(setter.shouldSetFeature(owner, multiReference));

		// Initially, the opposite feature is not set, so it should be set
		assertTrue(setter.shouldSetFeature(owner, oppositeReference1));
		// Set the feature value with one element
		var oppositeList = EMFUtils.getAsList(owner, oppositeReference1);
		var oppositeInstance = EcoreUtil.create(ownerClass);
		oppositeList.add(oppositeInstance);
		// Now, the feature is set, but it is multi-valued and has an opposite, so it should be set again
		assertTrue(setter.shouldSetFeature(owner, oppositeReference1));
		// The other side of the opposite should also be set
		assertTrue(oppositeInstance.eIsSet(oppositeReference2));
		// since it is a single-valued reference, it should not be set again
		assertFalse(setter.shouldSetFeature(oppositeInstance, oppositeReference2));
	}

	@Test
	void shouldOnlyAddMissingItemsWhenSomeAlreadyPresent() {
		// This test kills the mutation on line (count - list.size())
		// If mutated to (count + list.size()), the test would fail
		
		// Create 4 existing instances
		EObject existing1 = createExistingInstance(referencedClass);
		EObject existing2 = createExistingInstance(referencedClass);
		EObject existing3 = createExistingInstance(referencedClass);
		createExistingInstance(referencedClass);

		// Set multiValuedCount to 3
		setter.setDefaultMaxCount(3);

		EReference reference = createNonContainmentReference("refs", true);
		EReference opposite = createNonContainmentReference("owners", false);
		reference.setEOpposite(opposite);
		opposite.setEOpposite(reference);
		EObject owner = createOwner();

		// Manually add existing1 to the list first (simulating it's already there via opposite reference)
		var list = EMFUtils.getAsList(owner, reference);
		list.add(existing1);
		assertThat(list).hasSize(1);

		// Now call setReference - it should only add 2 more items (not 3 or 4)
		// to reach the total of 3 (count)
		setter.setCrossReference(owner, reference);

		// Should have exactly 3 items total: the one we added manually plus 2 more
		assertThat(list).hasSize(3)
			.containsExactly(existing1, existing2, existing3);
	}

	// ========== Non-unique reference tests ==========

	@Test
	void shouldAllowDuplicatesForNonUniqueReferences() {
		// Create a non-unique multi-valued reference
		EReference reference = createNonContainmentReference("refs", true);
		reference.setUnique(false); // Allow duplicates
		
		EObject owner = createOwner();
		
		// Create only one existing instance
		EObject existing1 = createExistingInstance(referencedClass);
		
		// Set default count to 3 (more than available unique instances)
		setter.setDefaultMaxCount(3);
		
		setter.setCrossReference(owner, reference);
		
		// Should allow the same instance multiple times since unique is false
		var list = EMFUtils.getAsList(owner, reference);
		assertThat(list).hasSize(3)
			.allMatch(obj -> obj == existing1);
	}

	@Test
	void shouldNotAllowDuplicatesForUniqueReferences() {
		// Create a unique multi-valued reference (default is unique=true)
		EReference reference = createNonContainmentReference("refs", true);
		// reference.setUnique(true) is the default
		
		EObject owner = createOwner();
		
		// Create only one existing instance
		EObject existing1 = createExistingInstance(referencedClass);
		
		// Set default count to 3 (more than available unique instances)
		setter.setDefaultMaxCount(3);
		
		setter.setCrossReference(owner, reference);
		
		// Should only add the instance once since unique is true
		var list = EMFUtils.getAsList(owner, reference);
		assertThat(list).hasSize(1)
			.containsExactly(existing1);
	}

	@Test
	void testSetAllowCyclePolicy() {
		// Create reference with the same type as owner (allowing self-reference)
		EReference selfReference = ECORE_FACTORY.createEReference();
		selfReference.setName("self");
		selfReference.setEType(ownerClass);
		ownerClass.getEStructuralFeatures().add(selfReference);
		
		EObject owner = createOwner();
		
		// By default, cycles are not allowed
		setter.setCrossReference(owner, selfReference);
		assertThat(owner.eGet(selfReference)).isNull();
		
		// Set a cycle policy that allows cycles
		setter.setAllowCyclePolicy((o, ref) -> true);
		
		setter.setCrossReference(owner, selfReference);
		assertThat(owner.eGet(selfReference)).isEqualTo(owner);
	}

}
