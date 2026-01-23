package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.validateModel;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for EMFContainmentReferenceSetter.
 * This class achieves 100% coverage of the containment reference setter functionality.
 */
class EMFContainmentReferenceSetterTest {

	private static final EcoreFactory ECORE_FACTORY = EcoreFactory.eINSTANCE;

	private EMFContainmentReferenceSetter setter;
	private EPackage testPackage;
	private EClass ownerClass;
	private EClass containedClass;
	private ResourceSet resourceSet;
	private Resource resource;

	@BeforeEach
	void setUp() {
		setter = new EMFContainmentReferenceSetter();

		// Create a ResourceSet with XMI factory registered
		resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put("*", new XMIResourceFactoryImpl());

		// Create a Resource in the ResourceSet
		URI uri = URI.createURI("test://test.ecore");
		resource = resourceSet.createResource(uri);

		// Create a test package and add it to the resource
		testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		resource.getContents().add(testPackage);

		// Register the package in the global registry
		EMFTestUtils.registerPackageForTest(testPackage);

		// Create owner class
		ownerClass = ECORE_FACTORY.createEClass();
		ownerClass.setName("Owner");
		testPackage.getEClassifiers().add(ownerClass);

		// Create contained class
		containedClass = ECORE_FACTORY.createEClass();
		containedClass.setName("Contained");
		testPackage.getEClassifiers().add(containedClass);
	}

	@AfterEach
	void tearDown() {
		EMFTestUtils.cleanupRegisteredPackages();
	}

	// ========== Configuration tests ==========

	@Test
	void testSetDefaultMaxCount() {
		setter.setDefaultMaxCount(5);
		// We'll verify this works by checking the behavior in subsequent tests
		
		EReference reference = createContainmentReference("children", true);
		EObject owner = createOwner();

		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		assertThat(created).hasSize(5);
	}

	// ========== Invalid reference handling ==========

	@Test
	void testSetContainmentReference_WithAbstractReferenceType() {
		// Create an abstract class as the reference type (no instantiable subclasses)
		EClass abstractClass = ECORE_FACTORY.createEClass();
		abstractClass.setName("AbstractClass");
		abstractClass.setAbstract(true);
		testPackage.getEClassifiers().add(abstractClass);

		// Create a reference to the abstract class
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("abstractRef");
		reference.setEType(abstractClass);
		reference.setContainment(true);
		reference.setChangeable(true);
		ownerClass.getEStructuralFeatures().add(reference);

		EObject owner = createOwner();
		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		// Should return empty because abstract class has no instantiable subclasses
		assertThat(created).isEmpty();
		assertThat(owner.eGet(reference)).isNull();
	}

	// ========== Single-valued containment reference tests ==========

	@Test
	void testSetContainmentReference_SingleValued() {
		EReference reference = createContainmentReference("child", false);

		EObject owner = createOwner();
		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		// Should create exactly one contained object
		assertThat(created).hasSize(1);

		// The reference should be set
		EObject child = (EObject) owner.eGet(reference);
		assertThat(child).isNotNull();
		assertThat(child.eClass()).isEqualTo(containedClass);

		// The created object should be the same as the one in the reference
		assertThat(created).containsExactly(child);
		validateModel(owner);
	}

	@Test
	void testSetContainmentReference_SingleValued_ContainerIsSet() {
		EReference reference = createContainmentReference("child", false);

		EObject owner = createOwner();
		var created = setter.setContainmentReference(owner, reference);
		assertThat(created).hasSize(1);

		EObject child = (EObject) owner.eGet(reference);

		// Verify that the container is automatically set by EMF
		assertThat(child.eContainer()).isEqualTo(owner);
		assertThat(child.eContainmentFeature()).isEqualTo(reference);
		validateModel(owner);
	}

	// ========== Multi-valued containment reference tests ==========

	@Test
	void testSetContainmentReference_MultiValued_DefaultCount() {
		EReference reference = createContainmentReference("children", true);

		EObject owner = createOwner();
		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		// Default count is 2
		assertThat(created).hasSize(2);

		List<EObject> children = EMFUtils.getAsEObjectsList(owner, reference);
		assertThat(children)
			.hasSize(2)
			.allMatch(child -> child.eClass().equals(containedClass))
			.containsExactlyElementsOf(created);
		validateModel(owner);
	}

	@Test
	void testSetContainmentReference_MultiValued_ContainersAreSet() {
		EReference reference = createContainmentReference("children", true);

		EObject owner = createOwner();
		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		// Verify that all children have their container set
		for (EObject child : created) {
			assertThat(child.eContainer()).isEqualTo(owner);
			assertThat(child.eContainmentFeature()).isEqualTo(reference);
		}
		validateModel(owner);
	}

	// ========== Round-robin subclass tests ==========

	@Test
	void testSetContainmentReference_MultiValued_RoundRobinWithTwoSubclasses() {
		// Create an abstract base class and two concrete subclasses
		EClass baseClass = ECORE_FACTORY.createEClass();
		baseClass.setName("BaseClass");
		baseClass.setAbstract(true);
		testPackage.getEClassifiers().add(baseClass);

		EClass subclass1 = ECORE_FACTORY.createEClass();
		subclass1.setName("SubClass1");
		subclass1.getESuperTypes().add(baseClass);
		testPackage.getEClassifiers().add(subclass1);

		EClass subclass2 = ECORE_FACTORY.createEClass();
		subclass2.setName("SubClass2");
		subclass2.getESuperTypes().add(baseClass);
		testPackage.getEClassifiers().add(subclass2);

		// Create a containment reference to the abstract base class
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("items");
		reference.setEType(baseClass);
		reference.setContainment(true);
		reference.setUpperBound(-1);
		ownerClass.getEStructuralFeatures().add(reference);

		// Set count to 5 to verify round-robin: sub1, sub2, sub1, sub2, sub1
		setter.setDefaultMaxCount(5);

		EObject owner = createOwner();
		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		assertThat(created).hasSize(5);

		// Verify round-robin pattern
		List<EObject> createdList = new ArrayList<>(created);
		assertThat(createdList.get(0).eClass()).isEqualTo(subclass1);
		assertThat(createdList.get(1).eClass()).isEqualTo(subclass2);
		assertThat(createdList.get(2).eClass()).isEqualTo(subclass1);
		assertThat(createdList.get(3).eClass()).isEqualTo(subclass2);
		assertThat(createdList.get(4).eClass()).isEqualTo(subclass1);
		validateModel(owner);
	}

	@Test
	void testSetContainmentReference_MultiValued_RoundRobinWithThreeSubclasses() {
		// Create an abstract base class and three concrete subclasses
		EClass baseClass = ECORE_FACTORY.createEClass();
		baseClass.setName("BaseClass");
		baseClass.setAbstract(true);
		testPackage.getEClassifiers().add(baseClass);

		EClass subclass1 = ECORE_FACTORY.createEClass();
		subclass1.setName("SubClass1");
		subclass1.getESuperTypes().add(baseClass);
		testPackage.getEClassifiers().add(subclass1);

		EClass subclass2 = ECORE_FACTORY.createEClass();
		subclass2.setName("SubClass2");
		subclass2.getESuperTypes().add(baseClass);
		testPackage.getEClassifiers().add(subclass2);

		EClass subclass3 = ECORE_FACTORY.createEClass();
		subclass3.setName("SubClass3");
		subclass3.getESuperTypes().add(baseClass);
		testPackage.getEClassifiers().add(subclass3);

		// Create a containment reference to the abstract base class
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("items");
		reference.setEType(baseClass);
		reference.setContainment(true);
		reference.setUpperBound(-1);
		ownerClass.getEStructuralFeatures().add(reference);

		// Set count to 7 to verify multiple cycles
		setter.setDefaultMaxCount(7);

		EObject owner = createOwner();
		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		assertThat(created).hasSize(7);

		// Verify round-robin pattern: sub1, sub2, sub3, sub1, sub2, sub3, sub1
		List<EObject> createdList = new ArrayList<>(created);
		assertThat(createdList.get(0).eClass()).isEqualTo(subclass1);
		assertThat(createdList.get(1).eClass()).isEqualTo(subclass2);
		assertThat(createdList.get(2).eClass()).isEqualTo(subclass3);
		assertThat(createdList.get(3).eClass()).isEqualTo(subclass1);
		assertThat(createdList.get(4).eClass()).isEqualTo(subclass2);
		assertThat(createdList.get(5).eClass()).isEqualTo(subclass3);
		assertThat(createdList.get(6).eClass()).isEqualTo(subclass1);
		validateModel(owner);
	}

	@Test
	void testSetContainmentReference_MultiValued_RoundRobinExactMatch() {
		// Create an abstract base class and three concrete subclasses
		EClass baseClass = ECORE_FACTORY.createEClass();
		baseClass.setName("BaseClass");
		baseClass.setAbstract(true);
		testPackage.getEClassifiers().add(baseClass);

		EClass subclass1 = ECORE_FACTORY.createEClass();
		subclass1.setName("SubClass1");
		subclass1.getESuperTypes().add(baseClass);
		testPackage.getEClassifiers().add(subclass1);

		EClass subclass2 = ECORE_FACTORY.createEClass();
		subclass2.setName("SubClass2");
		subclass2.getESuperTypes().add(baseClass);
		testPackage.getEClassifiers().add(subclass2);

		EClass subclass3 = ECORE_FACTORY.createEClass();
		subclass3.setName("SubClass3");
		subclass3.getESuperTypes().add(baseClass);
		testPackage.getEClassifiers().add(subclass3);

		// Create a containment reference to the abstract base class
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("items");
		reference.setEType(baseClass);
		reference.setContainment(true);
		reference.setUpperBound(-1);
		ownerClass.getEStructuralFeatures().add(reference);

		// Set count to exactly 3 (matching number of subclasses)
		setter.setDefaultMaxCount(3);

		EObject owner = createOwner();
		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		assertThat(created).hasSize(3);

		// Each subclass should be used exactly once
		List<EObject> createdList = new ArrayList<>(created);
		assertThat(createdList.get(0).eClass()).isEqualTo(subclass1);
		assertThat(createdList.get(1).eClass()).isEqualTo(subclass2);
		assertThat(createdList.get(2).eClass()).isEqualTo(subclass3);
		validateModel(owner);
	}

	@Test
	void testSetContainmentReference_SingleValued_UsesFirstSubclass() {
		// Create an abstract base class and two concrete subclasses
		EClass baseClass = ECORE_FACTORY.createEClass();
		baseClass.setName("BaseClass");
		baseClass.setAbstract(true);
		testPackage.getEClassifiers().add(baseClass);

		EClass subclass1 = ECORE_FACTORY.createEClass();
		subclass1.setName("SubClass1");
		subclass1.getESuperTypes().add(baseClass);
		testPackage.getEClassifiers().add(subclass1);

		EClass subclass2 = ECORE_FACTORY.createEClass();
		subclass2.setName("SubClass2");
		subclass2.getESuperTypes().add(baseClass);
		testPackage.getEClassifiers().add(subclass2);

		// Create a single-valued containment reference to the abstract base class
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("item");
		reference.setEType(baseClass);
		reference.setContainment(true);
		reference.setUpperBound(1);
		ownerClass.getEStructuralFeatures().add(reference);

		EObject owner = createOwner();
		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		assertThat(created).hasSize(1);

		// Should use the first subclass
		EObject item = created.iterator().next();
		assertThat(item.eClass()).isEqualTo(subclass1);
		validateModel(owner);
	}

	@Test
	void testSetContainmentReference_SingleValued_WithRoundRobin() {
		// Create an abstract base class and two concrete subclasses
		EClass baseClass = ECORE_FACTORY.createEClass();
		baseClass.setName("BaseClass");
		baseClass.setAbstract(true);
		testPackage.getEClassifiers().add(baseClass);

		EClass subclass1 = ECORE_FACTORY.createEClass();
		subclass1.setName("SubClass1");
		subclass1.getESuperTypes().add(baseClass);
		testPackage.getEClassifiers().add(subclass1);

		EClass subclass2 = ECORE_FACTORY.createEClass();
		subclass2.setName("SubClass2");
		subclass2.getESuperTypes().add(baseClass);
		testPackage.getEClassifiers().add(subclass2);

		// Create a single-valued containment reference to the abstract base class
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("item");
		reference.setEType(baseClass);
		reference.setContainment(true);
		reference.setUpperBound(1);
		ownerClass.getEStructuralFeatures().add(reference);

		EObject owner = createOwner();
		Collection<EObject> created = setter.setContainmentReference(owner, reference);
		assertThat(created).hasSize(1);
		// Should use the first subclass
		EObject item = created.iterator().next();
		assertThat(item.eClass()).isEqualTo(subclass1);
		validateModel(owner);

		// Second invocation should use the second subclass
		owner.eUnset(reference); // Clear previous value
		created = setter.setContainmentReference(owner, reference);
		assertThat(created).hasSize(1);
		item = created.iterator().next();
		assertThat(item.eClass()).isEqualTo(subclass2);

		// Third invocation should cycle back to the first subclass
		owner.eUnset(reference); // Clear previous value
		created = setter.setContainmentReference(owner, reference);
		assertThat(created).hasSize(1);
		item = created.iterator().next();
		assertThat(item.eClass()).isEqualTo(subclass1);
	}

	// ========== Bounds handling tests ==========

	@Test
	void testSetContainmentReference_MultiValued_WithUpperBound() {
		setter.setDefaultMaxCount(10);

		EReference reference = createContainmentReference("children", true);
		reference.setUpperBound(3); // Limit to 3 children

		EObject owner = createOwner();
		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		// Should respect upper bound
		assertThat(created).hasSize(3);

		List<EObject> children = EMFUtils.getAsEObjectsList(owner, reference);
		assertThat(children).hasSize(3);
		validateModel(owner);
	}

	@Test
	void testSetContainmentReference_MultiValued_WithLowerBound() {
		setter.setDefaultMaxCount(1);

		EReference reference = createContainmentReference("children", true);
		reference.setLowerBound(5); // Require at least 5 children

		EObject owner = createOwner();
		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		// Should respect lower bound
		assertThat(created).hasSize(5);

		List<EObject> children = EMFUtils.getAsEObjectsList(owner, reference);
		assertThat(children).hasSize(5);
		validateModel(owner);
	}

	@Test
	void testSetContainmentReference_MultiValued_WithBothBounds() {
		setter.setDefaultMaxCount(10);

		EReference reference = createContainmentReference("children", true);
		reference.setLowerBound(2);
		reference.setUpperBound(4);

		EObject owner = createOwner();
		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		// Should respect upper bound (4 < 10)
		assertThat(created).hasSize(4);

		List<EObject> children = EMFUtils.getAsEObjectsList(owner, reference);
		assertThat(children).hasSize(4);
		validateModel(owner);
	}

	// ========== Opposite reference test ==========

	@Test
	void testSetContainmentReference_WithOppositeReference() {
		// Create a containment reference from Owner to Contained
		EReference containmentRef = ECORE_FACTORY.createEReference();
		containmentRef.setName("children");
		containmentRef.setEType(containedClass);
		containmentRef.setContainment(true);
		containmentRef.setUpperBound(-1); // Multi-valued
		ownerClass.getEStructuralFeatures().add(containmentRef);

		// Create an opposite (container) reference from Contained to Owner
		EReference oppositeRef = ECORE_FACTORY.createEReference();
		oppositeRef.setName("parent");
		oppositeRef.setEType(ownerClass);
		oppositeRef.setContainment(false);
		oppositeRef.setUpperBound(1); // Single-valued
		containedClass.getEStructuralFeatures().add(oppositeRef);

		// Set the opposite references
		containmentRef.setEOpposite(oppositeRef);
		oppositeRef.setEOpposite(containmentRef);

		// Create owner and set the containment reference
		EObject owner = createOwner();
		Collection<EObject> created = setter.setContainmentReference(owner, containmentRef);

		assertThat(created).hasSize(2); // Default count

		List<EObject> children = EMFUtils.getAsEObjectsList(owner, containmentRef);
		assertThat(children).hasSize(2);

		// Verify that the opposite reference is automatically set by EMF
		for (EObject child : children) {
			EObject parent = (EObject) child.eGet(oppositeRef);
			assertThat(parent)
					.as("Opposite reference should be automatically set by EMF")
					.isEqualTo(owner);
		}
		validateModel(owner);
	}

	@Test
	void testSetContainmentReference_WithOppositeReference_SingleValued() {
		// Create a single-valued containment reference from Owner to Contained
		EReference containmentRef = ECORE_FACTORY.createEReference();
		containmentRef.setName("child");
		containmentRef.setEType(containedClass);
		containmentRef.setContainment(true);
		containmentRef.setUpperBound(1); // Single-valued
		ownerClass.getEStructuralFeatures().add(containmentRef);

		// Create an opposite (container) reference from Contained to Owner
		EReference oppositeRef = ECORE_FACTORY.createEReference();
		oppositeRef.setName("parent");
		oppositeRef.setEType(ownerClass);
		oppositeRef.setContainment(false);
		oppositeRef.setUpperBound(1); // Single-valued
		containedClass.getEStructuralFeatures().add(oppositeRef);

		// Set the opposite references
		containmentRef.setEOpposite(oppositeRef);
		oppositeRef.setEOpposite(containmentRef);

		// Create owner and set the containment reference
		EObject owner = createOwner();
		Collection<EObject> created = setter.setContainmentReference(owner, containmentRef);

		assertThat(created).hasSize(1);

		EObject child = (EObject) owner.eGet(containmentRef);
		assertThat(child).isNotNull();

		// Verify that the opposite reference is automatically set by EMF
		EObject parent = (EObject) child.eGet(oppositeRef);
		assertThat(parent)
				.as("Opposite reference should be automatically set by EMF for single-valued containment")
				.isEqualTo(owner);
		validateModel(owner);
	}

	// ========== Edge cases ==========

	@Test
	void testSetContainmentReference_MultipleInvocations() {
		EReference reference = createContainmentReference("children", true);

		EObject owner = createOwner();

		// First invocation
		Collection<EObject> created1 = setter.setContainmentReference(owner, reference);
		assertThat(created1).hasSize(2);

		// Second invocation should not add more children
		Collection<EObject> created2 = setter.setContainmentReference(owner, reference);
		assertThat(created2).isEmpty();

		// Total should be 2 children
		var children = EMFUtils.getAsList(owner, reference);
		assertThat(children).hasSize(2);
		validateModel(owner);
	}

	// ========== Custom function tests ==========

	@Test
	void testSetContainmentReference_WithCustomFunction_SingleValued() {
		EReference reference = createContainmentReference("child", false);
		EObject owner = createOwner();

		// Create a custom subclass of containedClass for testing
		EClass customClass = ECORE_FACTORY.createEClass();
		customClass.setName("CustomContained");
		customClass.getESuperTypes().add(containedClass); // Make it a subclass
		testPackage.getEClassifiers().add(customClass);

		// Set a custom function that creates an instance of the custom class
		setter.setFunctionFor(reference, o -> EcoreUtil.create(customClass));

		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		// Should create exactly one object using the custom function
		assertThat(created).hasSize(1);
		EObject child = (EObject) owner.eGet(reference);
		assertThat(child).isNotNull();
		assertThat(child.eClass()).isEqualTo(customClass);
		assertThat(created).containsExactly(child);
		validateModel(owner);
	}

	@Test
	void testSetContainmentReference_WithCustomFunction_MultiValued() {
		EReference reference = createContainmentReference("children", true);
		EObject owner = createOwner();

		// Create a custom subclass of containedClass for testing
		EClass customClass = ECORE_FACTORY.createEClass();
		customClass.setName("CustomContained");
		customClass.getESuperTypes().add(containedClass); // Make it a subclass
		testPackage.getEClassifiers().add(customClass);

		// Set a custom function that creates instances of the custom class
		setter.setFunctionFor(reference, o -> EcoreUtil.create(customClass));

		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		// Should create 2 objects (default max count) using the custom function
		assertThat(created).hasSize(2);
		List<EObject> children = EMFUtils.getAsEObjectsList(owner, reference);
		assertThat(children).hasSize(2)
			.allMatch(child -> child.eClass().equals(customClass));
		assertThat(created).containsExactlyElementsOf(children);
		validateModel(owner);
	}

	@Test
	void testSetContainmentReference_WithCustomFunctionReturningNull_SingleValued() {
		EReference reference = createContainmentReference("child", false);
		EObject owner = createOwner();

		// Set a custom function that returns null
		setter.setFunctionFor(reference, o -> null);

		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		// Should fall back to default creation when function returns null
		assertThat(created).hasSize(1);
		EObject child = (EObject) owner.eGet(reference);
		assertThat(child).isNotNull();
		assertThat(child.eClass()).isEqualTo(containedClass);
		assertThat(created).containsExactly(child);
		validateModel(owner);
	}

	@Test
	void testSetContainmentReference_WithCustomFunctionReturningNull_MultiValued() {
		EReference reference = createContainmentReference("children", true);
		EObject owner = createOwner();

		// Set a custom function that returns null
		setter.setFunctionFor(reference, o -> null);

		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		// Should fall back to default creation when function returns null
		assertThat(created).hasSize(2);
		List<EObject> children = EMFUtils.getAsEObjectsList(owner, reference);
		assertThat(children).hasSize(2)
			.allMatch(child -> child.eClass().equals(containedClass));
		assertThat(created).containsExactlyElementsOf(children);
		validateModel(owner);
	}

	// ========== Candidate selector strategy tests ==========

	@Test
	void testSetInstantiableSubclassSelectorStrategy() {
		// Create multiple subclasses
		EClass subClass1 = ECORE_FACTORY.createEClass();
		subClass1.setName("SubClass1");
		subClass1.getESuperTypes().add(containedClass);
		testPackage.getEClassifiers().add(subClass1);

		EClass subClass2 = ECORE_FACTORY.createEClass();
		subClass2.setName("SubClass2");
		subClass2.getESuperTypes().add(containedClass);
		testPackage.getEClassifiers().add(subClass2);

		// Create a custom strategy that always returns the first subclass
		EMFCandidateSelectorStrategy<EClass, EClass> customStrategy = new EMFCandidateSelectorStrategy<EClass, EClass>() {
			@Override
			public EClass getNextCandidate(EObject context, EClass type) {
				return subClass1;
			}

			@Override
			public boolean hasCandidates(EObject context, EClass type) {
				return true;
			}
		};

		setter.setInstantiableSubclassSelectorStrategy(customStrategy);

		EReference reference = createContainmentReference("children", true);
		EObject owner = createOwner();

		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		// All created instances should be of SubClass1 due to custom strategy
		assertThat(created).hasSize(2)
			.allMatch(obj -> obj.eClass().equals(subClass1));
	}

	@Test
	void testSetContainmentReference_MultiValued_WithAbstractReferenceType() {
		// Create an abstract class as the reference type (no instantiable subclasses)
		EClass abstractClass = ECORE_FACTORY.createEClass();
		abstractClass.setName("AbstractClass");
		abstractClass.setAbstract(true);
		testPackage.getEClassifiers().add(abstractClass);

		// Create a multi-valued reference to the abstract class
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("abstractRefs");
		reference.setEType(abstractClass);
		reference.setContainment(true);
		reference.setUpperBound(-1); // Multi-valued
		reference.setChangeable(true);
		ownerClass.getEStructuralFeatures().add(reference);

		EObject owner = createOwner();
		Collection<EObject> created = setter.setContainmentReference(owner, reference);

		// Should return empty because abstract class has no instantiable subclasses
		assertThat(created).isEmpty();
		var list = EMFUtils.getAsList(owner, reference);
		assertThat(list).isEmpty();
	}

	// ========== Helper methods ==========

	private EReference createContainmentReference(String name, boolean isMany) {
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName(name);
		reference.setEType(containedClass);
		reference.setContainment(true);
		reference.setChangeable(true);
		reference.setDerived(false);
		if (isMany) {
			reference.setUpperBound(-1); // Unbounded
		} else {
			reference.setUpperBound(1); // Single-valued
		}
		ownerClass.getEStructuralFeatures().add(reference);
		return reference;
	}

	/**
	 * Helper method to create an owner instance and add it to a resource in the resource set.
	 * This is necessary because the new findAllInstantiableSubclasses method requires the context
	 * EObject to be in a resource with a resource set.
	 */
	private EObject createOwner() {
		EObject owner = EcoreUtil.create(ownerClass);
		Resource ownerResource = resourceSet.createResource(URI.createURI("test://owner-" + System.nanoTime() + ".xmi"));
		ownerResource.getContents().add(owner);
		return owner;
	}

}
