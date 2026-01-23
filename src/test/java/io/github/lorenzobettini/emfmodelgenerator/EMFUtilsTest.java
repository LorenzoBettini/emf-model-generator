package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResource;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResourceSet;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEAttributeExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEClassExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.loadEcoreModel;
import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for EMFUtils.
 * This class achieves 100% coverage of all the utility methods.
 */
class EMFUtilsTest {

	private static final EcoreFactory ECORE_FACTORY = EcoreFactory.eINSTANCE;
	private static final EcorePackage ECORE_PACKAGE = EcorePackage.eINSTANCE;

	@AfterEach
	void tearDown() {
		EMFTestUtils.cleanupRegisteredPackages();
	}

	/**
	 * Helper method to create an EPackage and add it to a resource.
	 * This simulates a real Ecore resource that has an EPackage as its root.
	 * Also registers the package in the global registry.
	 */
	private static EPackage createEPackageInResource(ResourceSet resourceSet, String packageName, String resourceName) {
		var pkg = ECORE_FACTORY.createEPackage();
		pkg.setName(packageName);
		pkg.setNsURI("http://test/" + packageName);
		pkg.setNsPrefix(packageName);
		
		var resource = createResource(resourceSet, resourceName);
		resource.getContents().add(pkg);
		
		// Register the package in the global registry
		EMFTestUtils.registerPackageForTest(pkg);
		
		return pkg;
	}
	
	/**
	 * Helper method to create an EPackage in a standalone resource (not in a resource set).
	 * Also registers the package in the global registry.
	 */
	private static EPackage createEPackageInStandaloneResource(String packageName, String resourceName) {
		var pkg = ECORE_FACTORY.createEPackage();
		pkg.setName(packageName);
		pkg.setNsURI("http://test/" + packageName);
		pkg.setNsPrefix(packageName);
		
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, resourceName);
		resource.getContents().add(pkg);
		
		// Register the package in the global registry
		EMFTestUtils.registerPackageForTest(pkg);
		
		return pkg;
	}

	// ========== isEcoreResource tests ==========

	@Test
	void testIsEcoreResource_WithEPackageAsFirstContent() {
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, "test.ecore");
		var pkg = ECORE_FACTORY.createEPackage();
		pkg.setName("test");
		resource.getContents().add(pkg);

		assertThat(EMFUtils.isEcoreResource(resource))
				.as("Resource with EPackage as first content should be an Ecore resource")
				.isTrue();
	}

	@Test
	void testIsEcoreResource_WithEmptyResource() {
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, "test.ecore");

		assertThat(EMFUtils.isEcoreResource(resource))
				.as("Empty resource should not be an Ecore resource")
				.isFalse();
	}

	@Test
	void testIsEcoreResource_WithNonEPackageAsFirstContent() {
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, "test.xmi");
		
		var pkg = ECORE_FACTORY.createEPackage();
		pkg.setName("test");
		pkg.setNsURI("http://test");
		pkg.setNsPrefix("test");
		
		var eClass = ECORE_FACTORY.createEClass();
		eClass.setName("TestClass");
		pkg.getEClassifiers().add(eClass);
		
		var instance = EcoreUtil.create(eClass);
		resource.getContents().add(instance);

		assertThat(EMFUtils.isEcoreResource(resource))
				.as("Resource with non-EPackage as first content should not be an Ecore resource")
				.isFalse();
	}

	// ========== canBeInstantiated tests ==========

	@Test
	void testCanBeInstantiatedWithConcreteClass() {
		EClass concreteClass = ECORE_FACTORY.createEClass();
		concreteClass.setName("ConcreteClass");
		concreteClass.setAbstract(false);
		concreteClass.setInterface(false);

		assertThat(EMFUtils.canBeInstantiated(concreteClass))
				.as("Concrete class should be instantiable")
				.isTrue();
	}

	@Test
	void testCanBeInstantiatedWithAbstractClass() {
		EClass abstractClass = ECORE_FACTORY.createEClass();
		abstractClass.setName("AbstractClass");
		abstractClass.setAbstract(true);
		abstractClass.setInterface(false);

		assertThat(EMFUtils.canBeInstantiated(abstractClass))
				.as("Abstract class should not be instantiable")
				.isFalse();
	}

	@Test
	void testCanBeInstantiatedWithInterface() {
		EClass interfaceClass = ECORE_FACTORY.createEClass();
		interfaceClass.setName("InterfaceClass");
		interfaceClass.setAbstract(false);
		interfaceClass.setInterface(true);

		assertThat(EMFUtils.canBeInstantiated(interfaceClass))
				.as("Interface should not be instantiable")
				.isFalse();
	}

	@Test
	void testCanBeInstantiatedWithAbstractInterface() {
		EClass abstractInterface = ECORE_FACTORY.createEClass();
		abstractInterface.setName("AbstractInterface");
		abstractInterface.setAbstract(true);
		abstractInterface.setInterface(true);

		assertThat(EMFUtils.canBeInstantiated(abstractInterface))
				.as("Abstract interface should not be instantiable")
				.isFalse();
	}

	// ========== isValidReference tests ==========

	@Test
	void testIsValidReferenceWithValidReference() {
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("validReference");
		reference.setChangeable(true);
		reference.setDerived(false);
		reference.setEType(EcorePackage.Literals.EOBJECT);
		// Container is false by default

		assertThat(EMFUtils.isValidReference(reference))
				.as("Valid reference should return true")
				.isTrue();
	}

	@Test
	void testIsValidReferenceWithUnchangeableReference() {
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("unchangeableReference");
		reference.setChangeable(false);
		reference.setDerived(false);
		reference.setEType(EcorePackage.Literals.EOBJECT);

		assertThat(EMFUtils.isValidReference(reference))
				.as("Unchangeable reference should not be valid")
				.isFalse();
	}

	@Test
	void testIsValidReferenceWithDerivedReference() {
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("derivedReference");
		reference.setChangeable(true);
		reference.setDerived(true);
		reference.setEType(EcorePackage.Literals.EOBJECT);

		assertThat(EMFUtils.isValidReference(reference))
				.as("Derived reference should not be valid")
				.isFalse();
	}

	@Test
	void testIsValidReferenceWithContainerReference() {
		EClass ownerClass = ECORE_FACTORY.createEClass();
		ownerClass.setName("Owner");
		
		EClass targetClass = ECORE_FACTORY.createEClass();
		targetClass.setName("Target");

		EReference containmentRef = ECORE_FACTORY.createEReference();
		containmentRef.setName("children");
		containmentRef.setContainment(true);
		containmentRef.setEType(targetClass);
		ownerClass.getEStructuralFeatures().add(containmentRef);

		EReference containerRef = ECORE_FACTORY.createEReference();
		containerRef.setName("parent");
		containerRef.setEOpposite(containmentRef);
		containerRef.setEType(ownerClass);
		targetClass.getEStructuralFeatures().add(containerRef);

		// Update the opposite
		containmentRef.setEOpposite(containerRef);

		assertThat(EMFUtils.isValidReference(containerRef))
				.as("Container reference should not be valid")
				.isFalse();
	}

	@Test
	void testIsValidReferenceWithNull() {
		assertThat(EMFUtils.isValidReference(null))
				.as("Null reference should not be valid")
				.isFalse();
	}

	@Test
	void testIsValidReferenceWithMultipleInvalidConditions() {
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("multiInvalidReference");
		reference.setChangeable(false);
		reference.setDerived(true);
		reference.setEType(EcorePackage.Literals.EOBJECT);

		assertThat(EMFUtils.isValidReference(reference))
				.as("Reference with multiple invalid conditions should not be valid")
				.isFalse();
	}

	// ========== isValidAttribute tests ==========

	@Test
	void testIsValidAttributeWithValidAttribute() {
		EAttribute attribute = ECORE_FACTORY.createEAttribute();
		attribute.setName("validAttribute");
		attribute.setChangeable(true);
		attribute.setDerived(false);
		attribute.setEType(EcorePackage.Literals.ESTRING);

		assertThat(EMFUtils.isValidAttribute(attribute))
				.as("Valid attribute should return true")
				.isTrue();
	}

	@Test
	void testIsValidAttributeWithUnchangeableAttribute() {
		EAttribute attribute = ECORE_FACTORY.createEAttribute();
		attribute.setName("unchangeableAttribute");
		attribute.setChangeable(false);
		attribute.setDerived(false);
		attribute.setEType(EcorePackage.Literals.ESTRING);

		assertThat(EMFUtils.isValidAttribute(attribute))
				.as("Unchangeable attribute should not be valid")
				.isFalse();
	}

	@Test
	void testIsValidAttributeWithDerivedAttribute() {
		EAttribute attribute = ECORE_FACTORY.createEAttribute();
		attribute.setName("derivedAttribute");
		attribute.setChangeable(true);
		attribute.setDerived(true);
		attribute.setEType(EcorePackage.Literals.ESTRING);

		assertThat(EMFUtils.isValidAttribute(attribute))
				.as("Derived attribute should not be valid")
				.isFalse();
	}

	@Test
	void testIsValidAttributeWithNull() {
		assertThat(EMFUtils.isValidAttribute(null))
				.as("Null attribute should not be valid")
				.isFalse();
	}

	@Test
	void testIsValidAttributeWithMultipleInvalidConditions() {
		EAttribute attribute = ECORE_FACTORY.createEAttribute();
		attribute.setName("multiInvalidAttribute");
		attribute.setChangeable(false);
		attribute.setDerived(true);
		attribute.setEType(EcorePackage.Literals.ESTRING);

		assertThat(EMFUtils.isValidAttribute(attribute))
				.as("Attribute with multiple invalid conditions should not be valid")
				.isFalse();
	}

	@Test
	void testIsValidAttributeWithFeatureMapAttribute() {
		EAttribute featureMapAttr = ECORE_FACTORY.createEAttribute();
		featureMapAttr.setName("featureMapAttribute");
		featureMapAttr.setEType(EcorePackage.Literals.EFEATURE_MAP_ENTRY);

		assertThat(EMFUtils.isValidAttribute(featureMapAttr))
				.as("Feature map attribute should not be a valid attribute")
				.isFalse();
	}

	// ========== isFeatureMap tests ==========

	@Test
	void testIsFeatureMapWithFeatureMapAttribute() {
		EAttribute featureMapAttr = ECORE_FACTORY.createEAttribute();
		featureMapAttr.setName("featureMapAttribute");
		featureMapAttr.setEType(EcorePackage.Literals.EFEATURE_MAP_ENTRY);

		assertThat(EMFUtils.isFeatureMap(featureMapAttr))
				.as("Feature map attribute should be detected as feature map")
				.isTrue();
	}

	@Test
	void testIsFeatureMapWithNonFeatureMapAttribute() {
		EAttribute normalAttr = ECORE_FACTORY.createEAttribute();
		normalAttr.setName("normalAttribute");
		normalAttr.setEType(EcorePackage.Literals.ESTRING);

		assertThat(EMFUtils.isFeatureMap(normalAttr))
				.as("Non-feature map attribute should not be detected as feature map")
				.isFalse();
	}

	/**
	 * Should also cover the case when the feature is not an EAttribute.
	 */
	@Test
	void testIsFeatureMapWithNull() {
		assertThat(EMFUtils.isFeatureMap(null))
				.as("Null attribute should not be detected as feature map")
				.isFalse();
	}

	// ========== getAsList tests ==========

	@Test
	void testGetAsList() {
		// retrieve an Ecore model EObject with multi-valued features
		EObject ePackageInstance = EcoreUtil.create(ECORE_PACKAGE.getEPackage());
		EReference eClassifiersRef = ECORE_PACKAGE.getEPackage_EClassifiers();
		var list = EMFUtils.getAsList(ePackageInstance, eClassifiersRef);
		assertThat(list)
				.as("The list retrieved from EPackage.eClassifiers should not be null and empty")
				.isNotNull()
				.isEmpty();
	}

	@Test
	void testGetAsEObjectsList() {
		// retrieve an Ecore model EObject with multi-valued features
		EObject ePackageInstance = EcoreUtil.create(ECORE_PACKAGE.getEPackage());
		EReference eClassifiersRef = ECORE_PACKAGE.getEPackage_EClassifiers();
		var list = EMFUtils.getAsEObjectsList(ePackageInstance, eClassifiersRef);
		assertThat(list)
				.as("The EObjects list retrieved from EPackage.eClassifiers should not be null and empty")
				.isNotNull()
				.isEmpty();
	}

	@Test
	void testGetAsEObjectsList_WithNonEmptyList() {
		// Create an EPackage with some EClassifiers to test non-empty list
		EPackage ePackage = ECORE_FACTORY.createEPackage();
		EClass eClass1 = ECORE_FACTORY.createEClass();
		eClass1.setName("Class1");
		EClass eClass2 = ECORE_FACTORY.createEClass();
		eClass2.setName("Class2");
		
		ePackage.getEClassifiers().add(eClass1);
		ePackage.getEClassifiers().add(eClass2);
		
		EReference eClassifiersRef = ECORE_PACKAGE.getEPackage_EClassifiers();
		var list = EMFUtils.getAsEObjectsList(ePackage, eClassifiersRef);
		
		assertThat(list)
				.as("The EObjects list should contain the added classifiers")
				.hasSize(2)
				.containsExactly(eClass1, eClass2);
	}

	// ========== getEffectiveCount tests ==========

	@Test
	void testGetEffectiveCount_WithNoRestrictions() {
		// Feature with no bounds restrictions (lowerBound=0, upperBound=-1)
		EAttribute attribute = ECORE_FACTORY.createEAttribute();
		attribute.setLowerBound(0);
		attribute.setUpperBound(-1); // unbounded
		
		int result = EMFUtils.getEffectiveCount(attribute, 5);
		
		assertThat(result).isEqualTo(5);
	}

	@Test
	void testGetEffectiveCount_WithUpperBoundRestriction() {
		// Feature with upper bound smaller than default count
		EAttribute attribute = ECORE_FACTORY.createEAttribute();
		attribute.setLowerBound(0);
		attribute.setUpperBound(3);
		
		int result = EMFUtils.getEffectiveCount(attribute, 5);
		
		assertThat(result)
				.as("Should respect upper bound of 3")
				.isEqualTo(3);
	}

	@Test
	void testGetEffectiveCount_WithLowerBoundRestriction() {
		// Feature with lower bound greater than default count
		EAttribute attribute = ECORE_FACTORY.createEAttribute();
		attribute.setLowerBound(5);
		attribute.setUpperBound(-1); // unbounded
		
		int result = EMFUtils.getEffectiveCount(attribute, 2);
		
		assertThat(result)
				.as("Should respect lower bound of 5")
				.isEqualTo(5);
	}

	@Test
	void testGetEffectiveCount_WithBothBounds() {
		// Feature with both bounds set, default count exceeds upper bound
		EAttribute attribute = ECORE_FACTORY.createEAttribute();
		attribute.setLowerBound(2);
		attribute.setUpperBound(4);
		
		int result = EMFUtils.getEffectiveCount(attribute, 10);
		
		assertThat(result)
				.as("Should respect upper bound of 4")
				.isEqualTo(4);
	}

	@Test
	void testGetEffectiveCount_WithBothBounds_DefaultBelowLower() {
		// Feature with both bounds set, default count below lower bound
		EAttribute attribute = ECORE_FACTORY.createEAttribute();
		attribute.setLowerBound(5);
		attribute.setUpperBound(10);
		
		int result = EMFUtils.getEffectiveCount(attribute, 2);
		
		assertThat(result)
				.as("Should respect lower bound of 5")
				.isEqualTo(5);
	}

	@Test
	void testGetEffectiveCount_WithBothBounds_DefaultInRange() {
		// Feature with both bounds set, default count within range
		EAttribute attribute = ECORE_FACTORY.createEAttribute();
		attribute.setLowerBound(2);
		attribute.setUpperBound(10);
		
		int result = EMFUtils.getEffectiveCount(attribute, 5);
		
		assertThat(result)
				.as("Should return default count since it's within bounds")
				.isEqualTo(5);
	}

	@Test
	void testGetEffectiveCount_WithUpperBoundOne() {
		// Edge case: upper bound of 1 (single-valued in terms of multiplicity)
		EAttribute attribute = ECORE_FACTORY.createEAttribute();
		attribute.setLowerBound(0);
		attribute.setUpperBound(1);
		
		int result = EMFUtils.getEffectiveCount(attribute, 5);
		
		assertThat(result)
				.as("Should respect upper bound of 1")
				.isEqualTo(1);
	}

	@Test
	void testGetEffectiveCount_WithUpperBoundZero() {
		// Edge case: upper bound of 0
		// The condition "upperBound > 0" is false when upperBound == 0,
		// so the upper bound restriction is NOT applied.
		// This documents the current behavior - semantically, one might expect
		// upperBound=0 to mean "no values allowed", but the current implementation
		// only restricts when upperBound is explicitly positive.
		EAttribute attribute = ECORE_FACTORY.createEAttribute();
		attribute.setLowerBound(0);
		attribute.setUpperBound(0);
		
		int result = EMFUtils.getEffectiveCount(attribute, 5);
		
		assertThat(result)
				.as("With upper bound 0, upper bound restriction should not apply (current behavior)")
				.isEqualTo(5);
	}

	@Test
	void testGetEffectiveCount_WithZeroDefault() {
		// Edge case: default count is 0
		EAttribute attribute = ECORE_FACTORY.createEAttribute();
		attribute.setLowerBound(2);
		attribute.setUpperBound(-1);
		
		int result = EMFUtils.getEffectiveCount(attribute, 0);
		
		assertThat(result)
				.as("Should respect lower bound of 2 even with default of 0")
				.isEqualTo(2);
	}

	@Test
	void testGetEffectiveCount_WithReference() {
		// Test that it works with EReference too, not just EAttribute
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setLowerBound(1);
		reference.setUpperBound(3);
		
		int result = EMFUtils.getEffectiveCount(reference, 5);
		
		assertThat(result)
				.as("Should work with EReference and respect upper bound")
				.isEqualTo(3);
	}

	// ========== findAllInstantiableSubclasses tests ==========

	@Test
	void testFindAllInstantiableSubclasses_WithSingleConcreteClass() {
		// Create a resource set with a single concrete class in an EPackage
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "test", "test.ecore");
		
		EClass baseClass = ECORE_FACTORY.createEClass();
		baseClass.setName("BaseClass");
		baseClass.setAbstract(false);
		baseClass.setInterface(false);
		pkg.getEClassifiers().add(baseClass);
		
		var result = EMFUtils.findAllInstantiableSubclasses(baseClass);
		
		assertThat(result)
				.as("Should contain only the base class itself")
				.hasSize(1)
				.containsExactly(baseClass);
	}

	@Test
	void testFindAllInstantiableSubclasses_WithAbstractBaseClass() {
		// Create a resource set with an abstract base class in an EPackage
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "test", "test.ecore");
		
		EClass abstractBase = ECORE_FACTORY.createEClass();
		abstractBase.setName("AbstractBase");
		abstractBase.setAbstract(true);
		abstractBase.setInterface(false);
		pkg.getEClassifiers().add(abstractBase);
		
		var result = EMFUtils.findAllInstantiableSubclasses(abstractBase);
		
		assertThat(result)
				.as("Should not contain the abstract base class")
				.isEmpty();
	}

	@Test
	void testFindAllInstantiableSubclasses_WithConcreteSubclass() {
		// Create hierarchy: AbstractBase <- ConcreteSubclass in an EPackage
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "test", "test.ecore");
		
		EClass abstractBase = ECORE_FACTORY.createEClass();
		abstractBase.setName("AbstractBase");
		abstractBase.setAbstract(true);
		pkg.getEClassifiers().add(abstractBase);
		
		EClass concreteSubclass = ECORE_FACTORY.createEClass();
		concreteSubclass.setName("ConcreteSubclass");
		concreteSubclass.setAbstract(false);
		concreteSubclass.getESuperTypes().add(abstractBase);
		pkg.getEClassifiers().add(concreteSubclass);
		
		var result = EMFUtils.findAllInstantiableSubclasses(abstractBase);
		
		assertThat(result)
				.as("Should contain only the concrete subclass")
				.hasSize(1)
				.containsExactly(concreteSubclass);
	}

	@Test
	void testFindAllInstantiableSubclasses_WithMultipleLevels() {
		// Create hierarchy: A <- B <- C (all concrete) in an EPackage
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "test", "test.ecore");
		
		EClass classA = ECORE_FACTORY.createEClass();
		classA.setName("A");
		classA.setAbstract(false);
		pkg.getEClassifiers().add(classA);
		
		EClass classB = ECORE_FACTORY.createEClass();
		classB.setName("B");
		classB.setAbstract(false);
		classB.getESuperTypes().add(classA);
		pkg.getEClassifiers().add(classB);
		
		EClass classC = ECORE_FACTORY.createEClass();
		classC.setName("C");
		classC.setAbstract(false);
		classC.getESuperTypes().add(classB);
		pkg.getEClassifiers().add(classC);
		
		var result = EMFUtils.findAllInstantiableSubclasses(classA);
		
		assertThat(result)
				.as("Should contain all three classes in the hierarchy")
				.hasSize(3)
				.containsExactlyInAnyOrder(classA, classB, classC);
	}

	@Test
	void testFindAllInstantiableSubclasses_WithMixedHierarchy() {
		// Create hierarchy: ConcreteA <- AbstractB <- ConcreteC in an EPackage
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "test", "test.ecore");
		
		EClass concreteA = ECORE_FACTORY.createEClass();
		concreteA.setName("ConcreteA");
		concreteA.setAbstract(false);
		pkg.getEClassifiers().add(concreteA);
		
		EClass abstractB = ECORE_FACTORY.createEClass();
		abstractB.setName("AbstractB");
		abstractB.setAbstract(true);
		abstractB.getESuperTypes().add(concreteA);
		pkg.getEClassifiers().add(abstractB);
		
		EClass concreteC = ECORE_FACTORY.createEClass();
		concreteC.setName("ConcreteC");
		concreteC.setAbstract(false);
		concreteC.getESuperTypes().add(abstractB);
		pkg.getEClassifiers().add(concreteC);
		
		var result = EMFUtils.findAllInstantiableSubclasses(concreteA);
		
		assertThat(result)
				.as("Should contain only concrete classes (A and C, not abstract B)")
				.hasSize(2)
				.containsExactlyInAnyOrder(concreteA, concreteC);
	}

	@Test
	void testFindAllInstantiableSubclasses_WithInterfaceInHierarchy() {
		// Create hierarchy: ConcreteA <- InterfaceB <- ConcreteC in an EPackage
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "test", "test.ecore");
		
		EClass concreteA = ECORE_FACTORY.createEClass();
		concreteA.setName("ConcreteA");
		concreteA.setAbstract(false);
		pkg.getEClassifiers().add(concreteA);
		
		EClass interfaceB = ECORE_FACTORY.createEClass();
		interfaceB.setName("InterfaceB");
		interfaceB.setInterface(true);
		interfaceB.getESuperTypes().add(concreteA);
		pkg.getEClassifiers().add(interfaceB);
		
		EClass concreteC = ECORE_FACTORY.createEClass();
		concreteC.setName("ConcreteC");
		concreteC.setAbstract(false);
		concreteC.getESuperTypes().add(interfaceB);
		pkg.getEClassifiers().add(concreteC);
		
		var result = EMFUtils.findAllInstantiableSubclasses(concreteA);
		
		assertThat(result)
				.as("Should contain only concrete classes (A and C, not interface B)")
				.hasSize(2)
				.containsExactlyInAnyOrder(concreteA, concreteC);
	}

	@Test
	void testFindAllInstantiableSubclasses_WithMultipleResources() {
		// Create classes across multiple EPackages in the same resource set
		var resourceSet = createResourceSet();
		var pkg1 = createEPackageInResource(resourceSet, "test1", "resource1.ecore");
		var pkg2 = createEPackageInResource(resourceSet, "test2", "resource2.ecore");
		
		EClass baseClass = ECORE_FACTORY.createEClass();
		baseClass.setName("BaseClass");
		baseClass.setAbstract(false);
		pkg1.getEClassifiers().add(baseClass);
		
		EClass subclass1 = ECORE_FACTORY.createEClass();
		subclass1.setName("Subclass1");
		subclass1.setAbstract(false);
		subclass1.getESuperTypes().add(baseClass);
		pkg1.getEClassifiers().add(subclass1);
		
		EClass subclass2 = ECORE_FACTORY.createEClass();
		subclass2.setName("Subclass2");
		subclass2.setAbstract(false);
		subclass2.getESuperTypes().add(baseClass);
		pkg2.getEClassifiers().add(subclass2);
		
		var result = EMFUtils.findAllInstantiableSubclasses(baseClass);
		
		assertThat(result)
				.as("Should find subclasses across multiple resources")
				.hasSize(3)
				.containsExactlyInAnyOrder(baseClass, subclass1, subclass2);
	}

	@Test
	void testFindAllInstantiableSubclasses_WithUnrelatedClasses() {
		// Create hierarchy with unrelated classes that should not be included in an EPackage
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "test", "test.ecore");
		
		EClass baseClass = ECORE_FACTORY.createEClass();
		baseClass.setName("BaseClass");
		baseClass.setAbstract(false);
		pkg.getEClassifiers().add(baseClass);
		
		EClass subclass = ECORE_FACTORY.createEClass();
		subclass.setName("Subclass");
		subclass.setAbstract(false);
		subclass.getESuperTypes().add(baseClass);
		pkg.getEClassifiers().add(subclass);
		
		// Unrelated class
		EClass unrelatedClass = ECORE_FACTORY.createEClass();
		unrelatedClass.setName("UnrelatedClass");
		unrelatedClass.setAbstract(false);
		pkg.getEClassifiers().add(unrelatedClass);
		
		var result = EMFUtils.findAllInstantiableSubclasses(baseClass);
		
		assertThat(result)
				.as("Should not contain unrelated classes")
				.hasSize(2)
				.containsExactlyInAnyOrder(baseClass, subclass)
				.doesNotContain(unrelatedClass);
	}

	@Test
	void testFindAllInstantiableSubclasses_WithMultipleInheritance() {
		// Create diamond hierarchy: Base <- SubA, SubB <- Diamond (multiple inheritance) in an EPackage
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "test", "test.ecore");
		
		EClass base = ECORE_FACTORY.createEClass();
		base.setName("Base");
		base.setAbstract(false);
		pkg.getEClassifiers().add(base);
		
		EClass subA = ECORE_FACTORY.createEClass();
		subA.setName("SubA");
		subA.setAbstract(false);
		subA.getESuperTypes().add(base);
		pkg.getEClassifiers().add(subA);
		
		EClass subB = ECORE_FACTORY.createEClass();
		subB.setName("SubB");
		subB.setAbstract(false);
		subB.getESuperTypes().add(base);
		pkg.getEClassifiers().add(subB);
		
		EClass diamond = ECORE_FACTORY.createEClass();
		diamond.setName("Diamond");
		diamond.setAbstract(false);
		diamond.getESuperTypes().add(subA);
		diamond.getESuperTypes().add(subB);
		pkg.getEClassifiers().add(diamond);
		
		var result = EMFUtils.findAllInstantiableSubclasses(base);
		
		assertThat(result)
				.as("Should handle multiple inheritance correctly")
				.hasSize(4)
				.containsExactlyInAnyOrder(base, subA, subB, diamond);
	}

	@Test
	void testFindAllInstantiableSubclasses_WithNonEClassObjects() {
		// Ensure method correctly filters out non-EClass objects in an EPackage
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "test", "test.ecore");
		
		EClass baseClass = ECORE_FACTORY.createEClass();
		baseClass.setName("BaseClass");
		baseClass.setAbstract(false);
		pkg.getEClassifiers().add(baseClass);
		
		// Add some structural features to the class (non-EClass objects)
		EAttribute attribute = ECORE_FACTORY.createEAttribute();
		attribute.setName("someAttribute");
		baseClass.getEStructuralFeatures().add(attribute);
		
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("someReference");
		baseClass.getEStructuralFeatures().add(reference);
		
		var result = EMFUtils.findAllInstantiableSubclasses(baseClass);
		
		assertThat(result)
				.as("Should only include EClass objects, not attributes or references")
				.hasSize(1)
				.containsExactly(baseClass);
	}

	@Test
	void testFindAllInstantiableSubclasses_BaseClassNotFirst() {
		// Test that the base class is included even when it's not the first element in the EPackage
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "test", "test.ecore");
		
		// Add other EClasses first
		EClass unrelatedClass1 = ECORE_FACTORY.createEClass();
		unrelatedClass1.setName("UnrelatedClass1");
		unrelatedClass1.setAbstract(false);
		pkg.getEClassifiers().add(unrelatedClass1);
		
		EClass unrelatedClass2 = ECORE_FACTORY.createEClass();
		unrelatedClass2.setName("UnrelatedClass2");
		unrelatedClass2.setAbstract(false);
		pkg.getEClassifiers().add(unrelatedClass2);
		
		// Now add the base class (NOT first in the EPackage)
		EClass baseClass = ECORE_FACTORY.createEClass();
		baseClass.setName("BaseClass");
		baseClass.setAbstract(false);
		pkg.getEClassifiers().add(baseClass);
		
		// Add a subclass after the base class
		EClass subClass = ECORE_FACTORY.createEClass();
		subClass.setName("SubClass");
		subClass.setAbstract(false);
		subClass.getESuperTypes().add(baseClass);
		pkg.getEClassifiers().add(subClass);
		
		var result = EMFUtils.findAllInstantiableSubclasses(baseClass);
		
		assertThat(result)
				.as("Should include the base class even though it's not first in the EPackage")
				.hasSize(2)
				.containsExactly(baseClass, subClass);
	}

	@Test
	void testFindAllInstantiableSubclasses_ShouldSkipNonEcoreResources() {
		// Test that non-Ecore resources are skipped when looking for subclasses
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "test", "test.ecore");
		
		EClass baseClass = ECORE_FACTORY.createEClass();
		baseClass.setName("BaseClass");
		baseClass.setAbstract(false);
		pkg.getEClassifiers().add(baseClass);
		
		EClass subClass = ECORE_FACTORY.createEClass();
		subClass.setName("SubClass");
		subClass.setAbstract(false);
		subClass.getESuperTypes().add(baseClass);
		pkg.getEClassifiers().add(subClass);
		
		// Create a non-Ecore resource with instances (should be ignored by findAllInstantiableSubclasses)
		var instanceResource = createResource(resourceSet, "instances.xmi");
		var instance = EcoreUtil.create(baseClass);
		instanceResource.getContents().add(instance);
		
		var result = EMFUtils.findAllInstantiableSubclasses(baseClass);
		
		assertThat(result)
				.as("Should find subclasses only from Ecore resources")
				.hasSize(2)
				.containsExactlyInAnyOrder(baseClass, subClass);
	}

	@Test
	void testFindAllInstantiableSubclasses_WithThreeLevelHierarchyInSeparateResources() {
		// Test with 3-level hierarchy spread across 3 separate Ecore files:
		// base.ecore: BaseClass (abstract)
		// middle.ecore: MiddleClass extends BaseClass (concrete)
		// leaf.ecore: LeafClass extends MiddleClass (concrete)
		
		// Create first Ecore with base class
		var pkg1 = createEPackageInStandaloneResource("base", "base.ecore");
		EClass baseClass = ECORE_FACTORY.createEClass();
		baseClass.setName("BaseClass");
		baseClass.setAbstract(true);
		pkg1.getEClassifiers().add(baseClass);
		
		// Create second Ecore with middle class extending base
		var pkg2 = createEPackageInStandaloneResource("middle", "middle.ecore");
		EClass middleClass = ECORE_FACTORY.createEClass();
		middleClass.setName("MiddleClass");
		middleClass.setAbstract(false);
		middleClass.getESuperTypes().add(baseClass);
		pkg2.getEClassifiers().add(middleClass);
		
		// Create third Ecore with leaf class extending middle
		var pkg3 = createEPackageInStandaloneResource("leaf", "leaf.ecore");
		EClass leafClass = ECORE_FACTORY.createEClass();
		leafClass.setName("LeafClass");
		leafClass.setAbstract(false);
		leafClass.getESuperTypes().add(middleClass);
		pkg3.getEClassifiers().add(leafClass);
		
		var result = EMFUtils.findAllInstantiableSubclasses(baseClass);
		
		assertThat(result)
				.as("Should find all concrete subclasses across separate Ecore resources")
				.hasSize(2)
				.containsExactlyInAnyOrder(middleClass, leafClass)
				.doesNotContain(baseClass); // abstract should not be included
	}

	@Test
	void testFindAllInstantiableSubclasses_WithNsUriBasedCrossReferences() {
		// Load the 3 ecore models
		EPackage basePackage = loadEcoreModel("src/test/resources/inputs", "hierarchy-base.ecore");

		EPackage intermediatePackage = loadEcoreModel("src/test/resources/inputs", "hierarchy-middle.ecore");

		EPackage finalPackage = loadEcoreModel("src/test/resources/inputs", "hierarchy-leaf.ecore");

		EClass baseClass = assertEClassExists(basePackage, "BaseItem");
		EClass middleClass = assertEClassExists(intermediatePackage, "MiddleItem");
		EClass leafClass = assertEClassExists(finalPackage, "LeafItem");

		var result = EMFUtils.findAllInstantiableSubclasses(baseClass);
		assertThat(result)
				.as("Should find all concrete subclasses across cross-referenced Ecore models")
				.hasSize(2)
				.containsExactlyInAnyOrder(middleClass, leafClass)
				.doesNotContain(baseClass); // abstract should not be included
	}

	@Test
	void testFindAllInstantiableSubclasses_WithFileBasedCrossReferences() {
		// Load the 3 ecore models
		EPackage basePackage = loadEcoreModel("src/test/resources/inputs", "hierarchy-base-fileuri.ecore");

		EPackage intermediatePackage = loadEcoreModel("src/test/resources/inputs", "hierarchy-middle-fileuri.ecore");

		EPackage finalPackage = loadEcoreModel("src/test/resources/inputs", "hierarchy-leaf-fileuri.ecore");

		EClass baseClass = assertEClassExists(basePackage, "BaseItem");
		EClass middleClass = assertEClassExists(intermediatePackage, "MiddleItem");
		EClass leafClass = assertEClassExists(finalPackage, "LeafItem");

		var result = EMFUtils.findAllInstantiableSubclasses(baseClass);
		assertThat(result)
				.as("Should find all concrete subclasses across file-referenced Ecore models")
				.hasSize(2)
				.containsExactlyInAnyOrder(middleClass, leafClass)
				.doesNotContain(baseClass); // abstract should not be included
	}

	@Test
	void testFindAllInstantiableSubclasses_WithNonEPackageInRegistry() {
		var pkg = createEPackageInStandaloneResource("test", "test.ecore");
		EClass baseClass = ECORE_FACTORY.createEClass();
		baseClass.setName("Base");
		pkg.getEClassifiers().add(baseClass);
		
		// Add a non-EPackage entry to the registry
		final String nonEPackageKey = "http://test-non-epackage";
		EPackage.Registry.INSTANCE.put(nonEPackageKey, "NotAnEPackage");
		EMFTestUtils.trackNsURIForTest(nonEPackageKey);
		
		var result = EMFUtils.findAllInstantiableSubclasses(baseClass);
		
		assertThat(result)
				.as("Should handle non-EPackage registry entries gracefully")
				.containsExactly(baseClass);
	}

	@Test
	void testFindAllInstantiableSubclasses_WithSubpackages() {
		// Load Ecore model with nested subpackages
		EPackage nestedPackage = loadEcoreModel("src/test/resources/inputs", "nested-packages.ecore");
		
		EClass baseClass = assertEClassExists(nestedPackage, "BaseClass");
		EClass directSubclass = assertEClassExists(nestedPackage, "DirectSubclass");
		
		// Get subpackages
		EPackage sub1 = nestedPackage.getESubpackages().get(0);
		EPackage sub2 = nestedPackage.getESubpackages().get(1);
		
		EClass sub1Class = assertEClassExists(sub1, "Sub1Class");
		EClass sub2Class = assertEClassExists(sub2, "Sub2Class");
		
		// Get nested subpackage
		EPackage subsub = sub2.getESubpackages().get(0);
		EClass subSubClass = assertEClassExists(subsub, "SubSubClass");
		
		var result = EMFUtils.findAllInstantiableSubclasses(baseClass);
		
		assertThat(result)
				.as("Should find all concrete subclasses including those in subpackages at any depth")
				.containsExactlyInAnyOrder(directSubclass, sub1Class, sub2Class, subSubClass)
				.doesNotContain(baseClass); // abstract should not be included
	}

	@Test
	void testFindAllInstantiableSubclasses_WithSubpackages_UnrelatedClassesNotIncluded() {
		// Load Ecore model with nested subpackages
		EPackage nestedPackage = loadEcoreModel("src/test/resources/inputs", "nested-packages.ecore");
		
		EClass baseClass = assertEClassExists(nestedPackage, "BaseClass");
		
		// Get subpackages
		EPackage sub1 = nestedPackage.getESubpackages().get(0);
		EClass unrelatedSub1Class = assertEClassExists(sub1, "UnrelatedSub1Class");
		
		var result = EMFUtils.findAllInstantiableSubclasses(baseClass);
		
		assertThat(result)
				.as("Should not include unrelated classes from subpackages")
				.doesNotContain(unrelatedSub1Class)
				.isNotEmpty(); // since there are no subclasses of baseClass in this test
	}

	@Test
	void testFindAllInstantiableSubclasses_WithEmptySubpackages() {
		// Test with a package that has subpackages but none contain subclasses
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "testempty", "testempty.ecore");
		
		EClass baseClass = ECORE_FACTORY.createEClass();
		baseClass.setName("Base");
		pkg.getEClassifiers().add(baseClass);
		
		// Create empty subpackages
		var subPkg1 = ECORE_FACTORY.createEPackage();
		subPkg1.setName("sub1");
		subPkg1.setNsURI("http://test/testempty/sub1");
		subPkg1.setNsPrefix("sub1");
		pkg.getESubpackages().add(subPkg1);
		EMFTestUtils.registerPackageForTest(subPkg1);
		
		var subPkg2 = ECORE_FACTORY.createEPackage();
		subPkg2.setName("sub2");
		subPkg2.setNsURI("http://test/testempty/sub2");
		subPkg2.setNsPrefix("sub2");
		pkg.getESubpackages().add(subPkg2);
		EMFTestUtils.registerPackageForTest(subPkg2);
		
		var result = EMFUtils.findAllInstantiableSubclasses(baseClass);
		
		assertThat(result)
				.as("Should handle empty subpackages gracefully")
				.containsExactly(baseClass);
	}

	@Test
	void testFindAllInstantiableSubclasses_WithSubpackagesNotInRegistry() {
		// Test that recursive scanning finds classes in subpackages even when
		// those subpackages are NOT registered in the global registry.
		// This specifically tests the recursive call in scanResourceForSubclasses.
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "testrecursion", "testrecursion.ecore");
		
		EClass baseClass = ECORE_FACTORY.createEClass();
		baseClass.setName("Base");
		pkg.getEClassifiers().add(baseClass);
		
		// Create a subpackage with a subclass but DON'T register it in the global registry
		var subPkg = ECORE_FACTORY.createEPackage();
		subPkg.setName("sub");
		subPkg.setNsURI("http://test/testrecursion/sub");
		subPkg.setNsPrefix("sub");
		pkg.getESubpackages().add(subPkg);
		// NOTE: NOT registering subPkg in the global registry
		
		EClass subClass = ECORE_FACTORY.createEClass();
		subClass.setName("SubClass");
		subClass.getESuperTypes().add(baseClass);
		subPkg.getEClassifiers().add(subClass);
		
		// Create a nested subpackage with another subclass, also NOT registered
		var subSubPkg = ECORE_FACTORY.createEPackage();
		subSubPkg.setName("subsub");
		subSubPkg.setNsURI("http://test/testrecursion/sub/subsub");
		subSubPkg.setNsPrefix("subsub");
		subPkg.getESubpackages().add(subSubPkg);
		// NOTE: NOT registering subSubPkg in the global registry
		
		EClass subSubClass = ECORE_FACTORY.createEClass();
		subSubClass.setName("SubSubClass");
		subSubClass.getESuperTypes().add(baseClass);
		subSubPkg.getEClassifiers().add(subSubClass);
		
		var result = EMFUtils.findAllInstantiableSubclasses(baseClass);
		
		assertThat(result)
				.as("Should find subclasses in unregistered subpackages through recursive scanning")
				.containsExactlyInAnyOrder(baseClass, subClass, subSubClass);
	}

	// ========== findAllAssignableInstances tests ==========

	@Test
	void testFindAllAssignableInstances_WithExactTypeInstances() {
		// Test finding instances of a specific EClass type
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, "test.ecore");
		
		// EPackage only needed for creating instances, not required in resource
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		// Create some instances of Person
		EObject person1 = EcoreUtil.create(personClass);
		EObject person2 = EcoreUtil.create(personClass);
		EObject person3 = EcoreUtil.create(personClass);
		
		resource.getContents().add(person1);
		resource.getContents().add(person2);
		resource.getContents().add(person3);
		
		var result = EMFUtils.findAllAssignableInstances(person1, personClass);
		
		assertThat(result)
				.containsExactlyInAnyOrder(person1, person2, person3);
	}

	@Test
	void testFindAllAssignableInstances_WithSubclassInstances() {
		// Test that instances of subclasses are also found
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, "test.ecore");
		
		// EPackage only needed for creating instances, not required in resource
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		EClass employeeClass = ECORE_FACTORY.createEClass();
		employeeClass.setName("Employee");
		employeeClass.setAbstract(false);
		employeeClass.getESuperTypes().add(personClass);
		testPackage.getEClassifiers().add(employeeClass);
		
		// Create instances of both types
		EObject person1 = EcoreUtil.create(personClass);
		EObject person2 = EcoreUtil.create(personClass);
		EObject employee1 = EcoreUtil.create(employeeClass);
		EObject employee2 = EcoreUtil.create(employeeClass);
		
		resource.getContents().add(person1);
		resource.getContents().add(person2);
		resource.getContents().add(employee1);
		resource.getContents().add(employee2);
		
		var result = EMFUtils.findAllAssignableInstances(person1, personClass);
		
		assertThat(result)
				.containsExactlyInAnyOrder(person1, person2, employee1, employee2);
	}

	@Test
	void testFindAllAssignableInstances_WithNoInstances() {
		// Test with an EClass that has no instances
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, "test.ecore");
		
		// EPackage only needed for creating instances, not required in resource
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);

		EClass dummyClass = ECORE_FACTORY.createEClass();
		personClass.setName("Dummy");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(dummyClass);

		// Create a dummy owner to provide the resource set context
		EObject dummyOwner = EcoreUtil.create(dummyClass);
		resource.getContents().add(dummyOwner);
		
		var result = EMFUtils.findAllAssignableInstances(dummyOwner, personClass);
		
		assertThat(result)
				.isEmpty();
	}

	@Test
	void testFindAllAssignableInstances_WithUnrelatedInstances() {
		// Test that unrelated instances are not included
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, "test.ecore");
		
		// EPackage only needed for creating instances, not required in resource
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		EClass companyClass = ECORE_FACTORY.createEClass();
		companyClass.setName("Company");
		companyClass.setAbstract(false);
		testPackage.getEClassifiers().add(companyClass);
		
		// Create instances of both types
		EObject person1 = EcoreUtil.create(personClass);
		EObject person2 = EcoreUtil.create(personClass);
		EObject company1 = EcoreUtil.create(companyClass);
		EObject company2 = EcoreUtil.create(companyClass);
		
		resource.getContents().add(person1);
		resource.getContents().add(person2);
		resource.getContents().add(company1);
		resource.getContents().add(company2);
		
		var result = EMFUtils.findAllAssignableInstances(person1, personClass);
		
		assertThat(result)
				.containsExactlyInAnyOrder(person1, person2);
	}

	@Test
	void testFindAllAssignableInstances_AcrossMultipleResources() {
		// Test that instances are found across all resources in the resource set
		var resourceSet = createResourceSet();
		var resource1 = createResource(resourceSet, "resource1.ecore");
		var resource2 = createResource(resourceSet, "resource2.ecore");
		
		// EPackage only needed for creating instances, not required in resource
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		// Create instances in both resources
		EObject person1 = EcoreUtil.create(personClass);
		EObject person2 = EcoreUtil.create(personClass);
		EObject person3 = EcoreUtil.create(personClass);
		
		resource1.getContents().add(person1);
		resource1.getContents().add(person2);
		resource2.getContents().add(person3);
		
		var result = EMFUtils.findAllAssignableInstances(person1, personClass);
		
		assertThat(result)
				.containsExactlyInAnyOrder(person1, person2, person3);
	}

	@Test
	void testFindAllAssignableInstances_WithMultiLevelInheritance() {
		// Test with a deeper inheritance hierarchy
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, "test.ecore");
		
		// EPackage only needed for creating instances, not required in resource
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		EClass employeeClass = ECORE_FACTORY.createEClass();
		employeeClass.setName("Employee");
		employeeClass.setAbstract(false);
		employeeClass.getESuperTypes().add(personClass);
		testPackage.getEClassifiers().add(employeeClass);
		
		EClass managerClass = ECORE_FACTORY.createEClass();
		managerClass.setName("Manager");
		managerClass.setAbstract(false);
		managerClass.getESuperTypes().add(employeeClass);
		testPackage.getEClassifiers().add(managerClass);
		
		// Create instances at all levels
		EObject person = EcoreUtil.create(personClass);
		EObject employee = EcoreUtil.create(employeeClass);
		EObject manager = EcoreUtil.create(managerClass);
		
		resource.getContents().add(person);
		resource.getContents().add(employee);
		resource.getContents().add(manager);
		
		var result = EMFUtils.findAllAssignableInstances(person, personClass);
		
		assertThat(result)
				.containsExactlyInAnyOrder(person, employee, manager);
	}

	@Test
	void testFindAllAssignableInstances_OnlySubclassInstances() {
		// Test when only subclass instances exist, not base class instances
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, "test.ecore");
		
		// EPackage only needed for creating instances, not required in resource
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		EClass employeeClass = ECORE_FACTORY.createEClass();
		employeeClass.setName("Employee");
		employeeClass.setAbstract(false);
		employeeClass.getESuperTypes().add(personClass);
		testPackage.getEClassifiers().add(employeeClass);
		
		// Only create Employee instances, no Person instances
		EObject employee1 = EcoreUtil.create(employeeClass);
		EObject employee2 = EcoreUtil.create(employeeClass);
		
		resource.getContents().add(employee1);
		resource.getContents().add(employee2);
		
		var result = EMFUtils.findAllAssignableInstances(employee1, personClass);
		
		assertThat(result)
				.containsExactlyInAnyOrder(employee1, employee2);
	}

	@Test
	void testFindAllAssignableInstances_WithAbstractBaseClass() {
		// Test with an abstract base class (only subclass instances should exist)
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, "test.ecore");
		
		// EPackage only needed for creating instances, not required in resource
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass abstractPerson = ECORE_FACTORY.createEClass();
		abstractPerson.setName("AbstractPerson");
		abstractPerson.setAbstract(true);
		testPackage.getEClassifiers().add(abstractPerson);
		
		EClass concreteEmployee = ECORE_FACTORY.createEClass();
		concreteEmployee.setName("ConcreteEmployee");
		concreteEmployee.setAbstract(false);
		concreteEmployee.getESuperTypes().add(abstractPerson);
		testPackage.getEClassifiers().add(concreteEmployee);
		
		// Create instances of concrete subclass
		EObject employee1 = EcoreUtil.create(concreteEmployee);
		EObject employee2 = EcoreUtil.create(concreteEmployee);
		
		resource.getContents().add(employee1);
		resource.getContents().add(employee2);
		
		var result = EMFUtils.findAllAssignableInstances(employee1, abstractPerson);
		
		assertThat(result)
				.containsExactlyInAnyOrder(employee1, employee2);
	}

	@Test
	void testFindAllAssignableInstances_WithNonEObjectsInResource() {
		// Test that the method correctly filters out non-EObject items
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, "test.ecore");
		
		// EPackage only needed for creating instances, not required in resource
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		// Create Person instances
		EObject person1 = EcoreUtil.create(personClass);
		EObject person2 = EcoreUtil.create(personClass);
		
		resource.getContents().add(person1);
		resource.getContents().add(person2);
		
		// Add some EClass definitions (which are EObjects but not instances of personClass)
		EClass anotherClass = ECORE_FACTORY.createEClass();
		anotherClass.setName("AnotherClass");
		resource.getContents().add(anotherClass);
		
		var result = EMFUtils.findAllAssignableInstances(person1, personClass);
		
		assertThat(result)
				.containsExactlyInAnyOrder(person1, person2);
	}

	@Test
	void testFindAllAssignableInstances_InstancesNotFirst() {
		// Test that instances are found even when they're not the first elements in the resource
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, "test.xmi");
		
		// EPackage only needed for creating instances, not required in resource
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		// Add other unrelated EClasses first (simulating a mixed resource)
		EClass unrelatedClass1 = ECORE_FACTORY.createEClass();
		unrelatedClass1.setName("UnrelatedClass1");
		resource.getContents().add(unrelatedClass1);
		
		EClass unrelatedClass2 = ECORE_FACTORY.createEClass();
		unrelatedClass2.setName("UnrelatedClass2");
		resource.getContents().add(unrelatedClass2);
		
		// Now add Person instances (NOT first in the resource)
		EObject person1 = EcoreUtil.create(personClass);
		EObject person2 = EcoreUtil.create(personClass);
		
		resource.getContents().add(person1);
		resource.getContents().add(person2);
		
		var result = EMFUtils.findAllAssignableInstances(person1, personClass);
		
		assertThat(result)
				.containsExactlyInAnyOrder(person1, person2);
	}

	@Test
	void testFindAllAssignableInstances_ShouldSkipEcoreResources() {
		// Test that Ecore resources (with EPackage as first content) are skipped
		var resourceSet = createResourceSet();
		
		// Create an EPackage in a resource (Ecore resource)
		var pkg = createEPackageInResource(resourceSet, "test", "test.ecore");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		pkg.getEClassifiers().add(personClass);
		
		// Create Person instances in a non-Ecore resource
		var instanceResource = createResource(resourceSet, "instances.xmi");
		EObject person1 = EcoreUtil.create(personClass);
		EObject person2 = EcoreUtil.create(personClass);
		instanceResource.getContents().add(person1);
		instanceResource.getContents().add(person2);
		
		var result = EMFUtils.findAllAssignableInstances(person1, personClass);
		
		// Should find instances only in non-Ecore resources
		assertThat(result)
				.containsExactlyInAnyOrder(person1, person2);
	}

	// ========== findAllAssignableInstances - single resource (no resource set) tests ==========

	@Test
	void testFindAllAssignableInstances_InSingleResourceWithoutResourceSet() {
		// Test when context is in a single resource that is NOT in a resource set
		// Create a standalone resource without adding it to a resource set
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, "standalone.xmi");
		
		// Remove the resource from the resource set to simulate a standalone resource
		resourceSet.getResources().remove(resource);
		
		// EPackage only needed for creating instances
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		// Create instances in the standalone resource
		EObject person1 = EcoreUtil.create(personClass);
		EObject person2 = EcoreUtil.create(personClass);
		EObject person3 = EcoreUtil.create(personClass);
		
		resource.getContents().add(person1);
		resource.getContents().add(person2);
		resource.getContents().add(person3);
		
		// Verify the resource is not in a resource set
		assertThat(resource.getResourceSet()).isNull();
		
		var result = EMFUtils.findAllAssignableInstances(person1, personClass);
		
		assertThat(result)
				.as("Should find all instances in the standalone resource")
				.containsExactlyInAnyOrder(person1, person2, person3);
	}

	@Test
	void testFindAllAssignableInstances_InSingleResourceWithSubclasses() {
		// Test with subclasses in a single resource without resource set
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, "standalone.xmi");
		resourceSet.getResources().remove(resource);
		
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		EClass employeeClass = ECORE_FACTORY.createEClass();
		employeeClass.setName("Employee");
		employeeClass.setAbstract(false);
		employeeClass.getESuperTypes().add(personClass);
		testPackage.getEClassifiers().add(employeeClass);
		
		EObject person = EcoreUtil.create(personClass);
		EObject employee1 = EcoreUtil.create(employeeClass);
		EObject employee2 = EcoreUtil.create(employeeClass);
		
		resource.getContents().add(person);
		resource.getContents().add(employee1);
		resource.getContents().add(employee2);
		
		assertThat(resource.getResourceSet()).isNull();
		
		var result = EMFUtils.findAllAssignableInstances(person, personClass);
		
		assertThat(result)
				.as("Should find all instances including subclasses")
				.containsExactlyInAnyOrder(person, employee1, employee2);
	}

	@Test
	void testFindAllAssignableInstances_InSingleResourceEmpty() {
		// Test when single resource contains no matching instances
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, "standalone.xmi");
		resourceSet.getResources().remove(resource);
		
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		EClass companyClass = ECORE_FACTORY.createEClass();
		companyClass.setName("Company");
		companyClass.setAbstract(false);
		testPackage.getEClassifiers().add(companyClass);
		
		// Add only a company instance
		EObject company = EcoreUtil.create(companyClass);
		resource.getContents().add(company);
		
		assertThat(resource.getResourceSet()).isNull();
		
		var result = EMFUtils.findAllAssignableInstances(company, personClass);
		
		assertThat(result)
				.as("Should return empty list when no matching instances found")
				.isEmpty();
	}

	@Test
	void testFindAllAssignableInstances_InSingleResourceWithContainedInstances() {
		// Test when instances are contained within other objects in a single resource
		var resourceSet = createResourceSet();
		var resource = createResource(resourceSet, "standalone.xmi");
		resourceSet.getResources().remove(resource);
		
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass companyClass = ECORE_FACTORY.createEClass();
		companyClass.setName("Company");
		companyClass.setAbstract(false);
		testPackage.getEClassifiers().add(companyClass);
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		// Add containment reference
		EReference employeesRef = ECORE_FACTORY.createEReference();
		employeesRef.setName("employees");
		employeesRef.setEType(personClass);
		employeesRef.setContainment(true);
		employeesRef.setUpperBound(-1);
		companyClass.getEStructuralFeatures().add(employeesRef);
		
		EObject company = EcoreUtil.create(companyClass);
		EObject person1 = EcoreUtil.create(personClass);
		EObject person2 = EcoreUtil.create(personClass);
		
		// Add persons as contained children of company
		var employees = EMFUtils.getAsEObjectsList(company, employeesRef);
		employees.add(person1);
		employees.add(person2);
		
		resource.getContents().add(company);
		
		assertThat(resource.getResourceSet()).isNull();
		
		var result = EMFUtils.findAllAssignableInstances(company, personClass);
		
		assertThat(result)
				.as("Should find contained instances in a single resource")
				.containsExactlyInAnyOrder(person1, person2);
	}

	// ========== findAllAssignableInstances - not in resource tests ==========

	@Test
	void testFindAllAssignableInstances_ContextNotInResource() {
		// Test when context is not in any resource
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		// Create instances not in any resource
		EObject person1 = EcoreUtil.create(personClass);
		EObject person2 = EcoreUtil.create(personClass);
		EObject person3 = EcoreUtil.create(personClass);
		
		// Create a container to hold the persons
		EClass containerClass = ECORE_FACTORY.createEClass();
		containerClass.setName("Container");
		containerClass.setAbstract(false);
		testPackage.getEClassifiers().add(containerClass);
		
		EReference personsRef = ECORE_FACTORY.createEReference();
		personsRef.setName("persons");
		personsRef.setEType(personClass);
		personsRef.setContainment(true);
		personsRef.setUpperBound(-1);
		containerClass.getEStructuralFeatures().add(personsRef);
		
		EObject container = EcoreUtil.create(containerClass);
		var persons = EMFUtils.getAsEObjectsList(container, personsRef);
		persons.add(person1);
		persons.add(person2);
		persons.add(person3);
		
		// Verify not in any resource
		assertThat(container.eResource()).isNull();
		assertThat(person1.eResource()).isNull();
		
		var result = EMFUtils.findAllAssignableInstances(container, personClass);
		
		assertThat(result)
				.as("Should find all instances in context's contents")
				.containsExactlyInAnyOrder(person1, person2, person3);
	}

	@Test
	void testFindAllAssignableInstances_ContextNotInResourceWithSubclasses() {
		// Test with subclasses when context is not in any resource
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		EClass employeeClass = ECORE_FACTORY.createEClass();
		employeeClass.setName("Employee");
		employeeClass.setAbstract(false);
		employeeClass.getESuperTypes().add(personClass);
		testPackage.getEClassifiers().add(employeeClass);
		
		EObject person = EcoreUtil.create(personClass);
		EObject employee1 = EcoreUtil.create(employeeClass);
		EObject employee2 = EcoreUtil.create(employeeClass);
		
		// Create a container
		EClass containerClass = ECORE_FACTORY.createEClass();
		containerClass.setName("Container");
		testPackage.getEClassifiers().add(containerClass);
		
		EReference personsRef = ECORE_FACTORY.createEReference();
		personsRef.setName("persons");
		personsRef.setEType(personClass);
		personsRef.setContainment(true);
		personsRef.setUpperBound(-1);
		containerClass.getEStructuralFeatures().add(personsRef);
		
		EObject container = EcoreUtil.create(containerClass);
		var persons = EMFUtils.getAsEObjectsList(container, personsRef);
		persons.add(person);
		persons.add(employee1);
		persons.add(employee2);
		
		assertThat(container.eResource()).isNull();
		
		var result = EMFUtils.findAllAssignableInstances(container, personClass);
		
		assertThat(result)
				.as("Should find all instances including subclasses")
				.containsExactlyInAnyOrder(person, employee1, employee2);
	}

	@Test
	void testFindAllAssignableInstances_ContextNotInResourceEmpty() {
		// Test when context not in resource and has no matching instances
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		EClass containerClass = ECORE_FACTORY.createEClass();
		containerClass.setName("Container");
		testPackage.getEClassifiers().add(containerClass);
		
		EObject container = EcoreUtil.create(containerClass);
		
		assertThat(container.eResource()).isNull();
		
		var result = EMFUtils.findAllAssignableInstances(container, personClass);
		
		assertThat(result)
				.as("Should return empty list when no matching instances in context")
				.isEmpty();
	}

	@Test
	void testFindAllAssignableInstances_ContextNotInResourceIsInstance() {
		// Test when context itself is an instance of the searched EClass
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		EObject person1 = EcoreUtil.create(personClass);
		EObject person2 = EcoreUtil.create(personClass);
		
		// Create a containment reference in Person to hold other persons
		EReference childrenRef = ECORE_FACTORY.createEReference();
		childrenRef.setName("children");
		childrenRef.setEType(personClass);
		childrenRef.setContainment(true);
		childrenRef.setUpperBound(-1);
		personClass.getEStructuralFeatures().add(childrenRef);
		
		// person1 contains person2
		var children = EMFUtils.getAsEObjectsList(person1, childrenRef);
		children.add(person2);
		
		assertThat(person1.eResource()).isNull();
		
		var result = EMFUtils.findAllAssignableInstances(person1, personClass);
		
		assertThat(result)
				.as("Should include context itself if it's an instance of the EClass")
				.containsExactlyInAnyOrder(person1, person2);
	}

	@Test
	void testFindAllAssignableInstances_ContextNotInResourceNestedContainment() {
		// Test with deeply nested containment when context not in resource
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		personClass.setAbstract(false);
		testPackage.getEClassifiers().add(personClass);
		
		EClass departmentClass = ECORE_FACTORY.createEClass();
		departmentClass.setName("Department");
		testPackage.getEClassifiers().add(departmentClass);
		
		EClass companyClass = ECORE_FACTORY.createEClass();
		companyClass.setName("Company");
		testPackage.getEClassifiers().add(companyClass);
		
		// Company -> Departments -> Persons
		EReference departmentsRef = ECORE_FACTORY.createEReference();
		departmentsRef.setName("departments");
		departmentsRef.setEType(departmentClass);
		departmentsRef.setContainment(true);
		departmentsRef.setUpperBound(-1);
		companyClass.getEStructuralFeatures().add(departmentsRef);
		
		EReference employeesRef = ECORE_FACTORY.createEReference();
		employeesRef.setName("employees");
		employeesRef.setEType(personClass);
		employeesRef.setContainment(true);
		employeesRef.setUpperBound(-1);
		departmentClass.getEStructuralFeatures().add(employeesRef);
		
		EObject company = EcoreUtil.create(companyClass);
		EObject dept1 = EcoreUtil.create(departmentClass);
		EObject dept2 = EcoreUtil.create(departmentClass);
		EObject person1 = EcoreUtil.create(personClass);
		EObject person2 = EcoreUtil.create(personClass);
		EObject person3 = EcoreUtil.create(personClass);
		
		var departments = EMFUtils.getAsEObjectsList(company, departmentsRef);
		departments.add(dept1);
		departments.add(dept2);
		
		var dept1Employees = EMFUtils.getAsEObjectsList(dept1, employeesRef);
		dept1Employees.add(person1);
		dept1Employees.add(person2);
		
		var dept2Employees = EMFUtils.getAsEObjectsList(dept2, employeesRef);
		dept2Employees.add(person3);
		
		assertThat(company.eResource()).isNull();
		
		var result = EMFUtils.findAllAssignableInstances(company, personClass);
		
		assertThat(result)
				.as("Should find all instances in deeply nested containment")
				.containsExactlyInAnyOrder(person1, person2, person3);
	}

	@Test
	void testFindAllAssignableInstances_ContextNotInResourceUnrelatedInstances() {
		// Test filtering when context contains unrelated instances
		var testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		EClass personClass = ECORE_FACTORY.createEClass();
		personClass.setName("Person");
		testPackage.getEClassifiers().add(personClass);
		
		EClass companyClass = ECORE_FACTORY.createEClass();
		companyClass.setName("Company");
		testPackage.getEClassifiers().add(companyClass);
		
		EClass containerClass = ECORE_FACTORY.createEClass();
		containerClass.setName("Container");
		testPackage.getEClassifiers().add(containerClass);
		
		// Container can hold both persons and companies
		EReference personsRef = ECORE_FACTORY.createEReference();
		personsRef.setName("persons");
		personsRef.setEType(personClass);
		personsRef.setContainment(true);
		personsRef.setUpperBound(-1);
		containerClass.getEStructuralFeatures().add(personsRef);
		
		EReference companiesRef = ECORE_FACTORY.createEReference();
		companiesRef.setName("companies");
		companiesRef.setEType(companyClass);
		companiesRef.setContainment(true);
		companiesRef.setUpperBound(-1);
		containerClass.getEStructuralFeatures().add(companiesRef);
		
		EObject container = EcoreUtil.create(containerClass);
		EObject person1 = EcoreUtil.create(personClass);
		EObject person2 = EcoreUtil.create(personClass);
		EObject company1 = EcoreUtil.create(companyClass);
		EObject company2 = EcoreUtil.create(companyClass);
		
		var persons = EMFUtils.getAsEObjectsList(container, personsRef);
		persons.add(person1);
		persons.add(person2);
		
		var companies = EMFUtils.getAsEObjectsList(container, companiesRef);
		companies.add(company1);
		companies.add(company2);
		
		assertThat(container.eResource()).isNull();
		
		var result = EMFUtils.findAllAssignableInstances(container, personClass);
		
		assertThat(result)
				.as("Should only find Person instances, not Company instances")
				.containsExactlyInAnyOrder(person1, person2)
				.doesNotContain(company1, company2);
	}

	@Test
	void testFindAllAssignableInstances_ContextNotInResource_UsesRootContainer() {
		// Test that when context is not the root, it should scan from the root container
		// This tests the fix for the issue: scanEObjectContents should use getRootContainer
		var testPackage = EMFTestUtils.createEPackage("test", "http://test", "test");
		
		var personClass = EMFTestUtils.createEClass(testPackage, "Person");
		var departmentClass = EMFTestUtils.createEClass(testPackage, "Department");
		var companyClass = EMFTestUtils.createEClass(testPackage, "Company");
		
		// Company -> Departments -> Persons
		var departmentsRef = EMFTestUtils.createContainmentEReference(companyClass, "departments", departmentClass, -1);
		var employeesRef = EMFTestUtils.createContainmentEReference(departmentClass, "employees", personClass, -1);
		
		// Create structure:
		// company
		//   - dept1 (contains person1, person2)
		//   - dept2 (contains person3)
		var company = EMFTestUtils.createInstance(companyClass);
		var dept1 = EMFTestUtils.createInstance(departmentClass);
		var dept2 = EMFTestUtils.createInstance(departmentClass);
		var person1 = EMFTestUtils.createInstance(personClass);
		var person2 = EMFTestUtils.createInstance(personClass);
		var person3 = EMFTestUtils.createInstance(personClass);
		
		var departments = EMFUtils.getAsEObjectsList(company, departmentsRef);
		departments.add(dept1);
		departments.add(dept2);
		
		var dept1Employees = EMFUtils.getAsEObjectsList(dept1, employeesRef);
		dept1Employees.add(person1);
		dept1Employees.add(person2);
		
		var dept2Employees = EMFUtils.getAsEObjectsList(dept2, employeesRef);
		dept2Employees.add(person3);
		
		assertThat(company.eResource()).isNull();
		assertThat(dept1.eContainer()).isEqualTo(company);
		assertThat(person1.eContainer()).isEqualTo(dept1);
		
		// Key test: when we call findAllAssignableInstances with dept1 as context,
		// it should scan from the root container (company) and find ALL persons across the entire tree
		// (person1, person2 in dept1, and person3 in dept2),
		// not just those in the dept1 subtree (person1, person2)
		var result = EMFUtils.findAllAssignableInstances(dept1, personClass);
		
		assertThat(result)
				.as("Should find all persons in the root container, not just in dept1")
				.containsExactlyInAnyOrder(person1, person2, person3);
	}

	// ========== canSetInThePresenceOfOppositeReference tests ==========

	/**
	 * Helper method to create a test EPackage with classes.
	 */
	private static EPackage createTestPackage(String name, EClass... classes) {
		EPackage pkg = ECORE_FACTORY.createEPackage();
		pkg.setName(name);
		pkg.setNsURI("http://test/" + name);
		pkg.setNsPrefix(name);
		for (EClass eClass : classes) {
			pkg.getEClassifiers().add(eClass);
		}
		return pkg;
	}

	@Test
	void testCanSetWithNoOppositeReference() {
		// Create a reference without an opposite
		EClass ownerClass = ECORE_FACTORY.createEClass();
		ownerClass.setName("Owner");
		EClass targetClass = ECORE_FACTORY.createEClass();
		targetClass.setName("Target");
		
		createTestPackage("test", ownerClass, targetClass);
		
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("target");
		reference.setEType(targetClass);
		reference.setContainment(false);
		// No opposite set
		
		ownerClass.getEStructuralFeatures().add(reference);
		
		EObject value = EcoreUtil.create(targetClass);
		
		boolean result = EMFUtils.canSetInThePresenceOfOppositeReference(reference, value);
		
		assertThat(result).isTrue();
	}

	@Test
	void testCanSetWithOppositeNotInValueEClass() {
		// Create a scenario where the value's EClass doesn't contain the opposite reference
		// This can happen with metamodel evolution or when using EObject types dynamically
		
		EClass ownerClass = ECORE_FACTORY.createEClass();
		ownerClass.setName("Owner");
		
		// Create two separate target classes with different structures
		EClass targetClassWithOpposite = ECORE_FACTORY.createEClass();
		targetClassWithOpposite.setName("TargetWithOpposite");
		
		EClass targetClassWithoutOpposite = ECORE_FACTORY.createEClass();
		targetClassWithoutOpposite.setName("TargetWithoutOpposite");
		
		createTestPackage("test", ownerClass, targetClassWithOpposite, targetClassWithoutOpposite);
		
		// Create reference that expects TargetWithOpposite
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("target");
		reference.setEType(ECORE_PACKAGE.getEObject()); // Use EObject as type to accept any EObject
		reference.setContainment(false);
		
		EReference opposite = ECORE_FACTORY.createEReference();
		opposite.setName("owner");
		opposite.setEType(ownerClass);
		opposite.setContainment(false);
		
		reference.setEOpposite(opposite);
		opposite.setEOpposite(reference);
		
		ownerClass.getEStructuralFeatures().add(reference);
		targetClassWithOpposite.getEStructuralFeatures().add(opposite);
		// Note: targetClassWithoutOpposite does NOT have the opposite reference
		
		// Create instance of targetClassWithoutOpposite
		EObject value = EcoreUtil.create(targetClassWithoutOpposite);
		
		// The value's EClass doesn't contain the opposite reference
		boolean result = EMFUtils.canSetInThePresenceOfOppositeReference(reference, value);
		
		assertThat(result).isTrue();
	}

	@Test
	void testCanSetWithSingleValuedOppositeNotSet() {
		// Create bidirectional reference with single-valued opposite
		EClass ownerClass = ECORE_FACTORY.createEClass();
		ownerClass.setName("Owner");
		EClass targetClass = ECORE_FACTORY.createEClass();
		targetClass.setName("Target");
		
		createTestPackage("test", ownerClass, targetClass);
		
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("target");
		reference.setEType(targetClass);
		reference.setContainment(false);
		
		EReference opposite = ECORE_FACTORY.createEReference();
		opposite.setName("owner");
		opposite.setEType(ownerClass);
		opposite.setContainment(false);
		opposite.setUpperBound(1); // Single-valued
		
		reference.setEOpposite(opposite);
		opposite.setEOpposite(reference);
		
		ownerClass.getEStructuralFeatures().add(reference);
		targetClass.getEStructuralFeatures().add(opposite);
		
		EObject value = EcoreUtil.create(targetClass);
		// opposite is not set on value
		
		boolean result = EMFUtils.canSetInThePresenceOfOppositeReference(reference, value);
		
		assertThat(result).isTrue();
	}

	@Test
	void testCannotSetWithSingleValuedOppositeAlreadySet() {
		// Create bidirectional reference with single-valued opposite
		EClass ownerClass = ECORE_FACTORY.createEClass();
		ownerClass.setName("Owner");
		EClass targetClass = ECORE_FACTORY.createEClass();
		targetClass.setName("Target");
		
		createTestPackage("test", ownerClass, targetClass);
		
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("target");
		reference.setEType(targetClass);
		reference.setContainment(false);
		
		EReference opposite = ECORE_FACTORY.createEReference();
		opposite.setName("owner");
		opposite.setEType(ownerClass);
		opposite.setContainment(false);
		opposite.setUpperBound(1); // Single-valued
		
		reference.setEOpposite(opposite);
		opposite.setEOpposite(reference);
		
		ownerClass.getEStructuralFeatures().add(reference);
		targetClass.getEStructuralFeatures().add(opposite);
		
		EObject existingOwner = EcoreUtil.create(ownerClass);
		EObject value = EcoreUtil.create(targetClass);
		
		// Set the opposite - value already has an owner
		value.eSet(opposite, existingOwner);
		
		boolean result = EMFUtils.canSetInThePresenceOfOppositeReference(reference, value);
		
		assertThat(result).isFalse();
	}

	@Test
	void testCanSetWithMultiValuedOppositeNotReachedUpperBound() {
		// Create bidirectional reference with multi-valued opposite
		EClass ownerClass = ECORE_FACTORY.createEClass();
		ownerClass.setName("Owner");
		EClass targetClass = ECORE_FACTORY.createEClass();
		targetClass.setName("Target");
		
		createTestPackage("test", ownerClass, targetClass);
		
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("targets");
		reference.setEType(targetClass);
		reference.setContainment(false);
		reference.setUpperBound(-1); // Many
		
		EReference opposite = ECORE_FACTORY.createEReference();
		opposite.setName("owners");
		opposite.setEType(ownerClass);
		opposite.setContainment(false);
		opposite.setUpperBound(3); // Multi-valued with upper bound of 3
		
		reference.setEOpposite(opposite);
		opposite.setEOpposite(reference);
		
		ownerClass.getEStructuralFeatures().add(reference);
		targetClass.getEStructuralFeatures().add(opposite);
		
		EObject owner1 = EcoreUtil.create(ownerClass);
		EObject owner2 = EcoreUtil.create(ownerClass);
		EObject value = EcoreUtil.create(targetClass);
		
		// Add 2 owners to value (below upper bound of 3)
		var ownersList = EMFUtils.getAsList(value, opposite);
		ownersList.add(owner1);
		ownersList.add(owner2);
		
		boolean result = EMFUtils.canSetInThePresenceOfOppositeReference(reference, value);
		
		assertThat(result).isTrue();
	}

	@Test
	void testCannotSetWithMultiValuedOppositeReachedUpperBound() {
		// Create bidirectional reference with multi-valued opposite
		EClass ownerClass = ECORE_FACTORY.createEClass();
		ownerClass.setName("Owner");
		EClass targetClass = ECORE_FACTORY.createEClass();
		targetClass.setName("Target");
		
		createTestPackage("test", ownerClass, targetClass);
		
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("targets");
		reference.setEType(targetClass);
		reference.setContainment(false);
		reference.setUpperBound(-1); // Many
		
		EReference opposite = ECORE_FACTORY.createEReference();
		opposite.setName("owners");
		opposite.setEType(ownerClass);
		opposite.setContainment(false);
		opposite.setUpperBound(2); // Multi-valued with upper bound of 2
		
		reference.setEOpposite(opposite);
		opposite.setEOpposite(reference);
		
		ownerClass.getEStructuralFeatures().add(reference);
		targetClass.getEStructuralFeatures().add(opposite);
		
		EObject owner1 = EcoreUtil.create(ownerClass);
		EObject owner2 = EcoreUtil.create(ownerClass);
		EObject value = EcoreUtil.create(targetClass);
		
		// Add 2 owners to value (reached upper bound of 2)
		var ownersList = EMFUtils.getAsList(value, opposite);
		ownersList.add(owner1);
		ownersList.add(owner2);
		
		boolean result = EMFUtils.canSetInThePresenceOfOppositeReference(reference, value);
		
		assertThat(result).isFalse();
	}

	@Test
	void testCanSetWithMultiValuedOppositeAtUpperBoundMinusOne() {
		// Test boundary condition: when list.size() == upperBound - 1
		// This ensures the condition list.size() < upperBound is properly tested
		EClass ownerClass = ECORE_FACTORY.createEClass();
		ownerClass.setName("Owner");
		EClass targetClass = ECORE_FACTORY.createEClass();
		targetClass.setName("Target");
		
		createTestPackage("test", ownerClass, targetClass);
		
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("targets");
		reference.setEType(targetClass);
		reference.setContainment(false);
		reference.setUpperBound(-1); // Many
		
		EReference opposite = ECORE_FACTORY.createEReference();
		opposite.setName("owners");
		opposite.setEType(ownerClass);
		opposite.setContainment(false);
		opposite.setUpperBound(2); // Multi-valued with upper bound of 2
		
		reference.setEOpposite(opposite);
		opposite.setEOpposite(reference);
		
		ownerClass.getEStructuralFeatures().add(reference);
		targetClass.getEStructuralFeatures().add(opposite);
		
		EObject owner1 = EcoreUtil.create(ownerClass);
		EObject value = EcoreUtil.create(targetClass);
		
		// Add 1 owner to value (one less than upper bound of 2)
		var ownersList = EMFUtils.getAsList(value, opposite);
		ownersList.add(owner1);
		
		boolean result = EMFUtils.canSetInThePresenceOfOppositeReference(reference, value);
		
		// With size=1 and upperBound=2, we can still add more (1 < 2 is true)
		assertThat(result).isTrue();
	}

	@Test
	void testCannotSetWithMultiValuedOppositeExactlyAtUpperBound() {
		// Test the exact boundary: when list.size() == upperBound
		// The condition "list.size() < upperBound" should be false
		// If mutated to "list.size() <= upperBound", it would incorrectly be true
		EClass ownerClass = ECORE_FACTORY.createEClass();
		ownerClass.setName("Owner");
		EClass targetClass = ECORE_FACTORY.createEClass();
		targetClass.setName("Target");
		
		createTestPackage("testexact", ownerClass, targetClass);
		
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("targets");
		reference.setEType(targetClass);
		reference.setContainment(false);
		reference.setUpperBound(-1); // Many
		
		EReference opposite = ECORE_FACTORY.createEReference();
		opposite.setName("owners");
		opposite.setEType(ownerClass);
		opposite.setContainment(false);
		opposite.setUpperBound(2); // Upper bound of 2 (isMany is true for > 1)
		
		reference.setEOpposite(opposite);
		opposite.setEOpposite(reference);
		
		ownerClass.getEStructuralFeatures().add(reference);
		targetClass.getEStructuralFeatures().add(opposite);
		
		// Verify the opposite is many-valued (upperBound > 1)
		assertThat(opposite.isMany()).isTrue();
		assertThat(opposite.getUpperBound()).isEqualTo(2);
		
		EObject owner1 = EcoreUtil.create(ownerClass);
		EObject owner2 = EcoreUtil.create(ownerClass);
		EObject value = EcoreUtil.create(targetClass);
		
		// Add exactly 2 owners to reach the upper bound
		var ownersList = EMFUtils.getAsList(value, opposite);
		ownersList.add(owner1);
		ownersList.add(owner2);
		
		// Verify preconditions
		assertThat(ownersList).hasSize(2);
		assertThat(value.eClass().getEAllReferences()).contains(opposite);
		
		boolean result = EMFUtils.canSetInThePresenceOfOppositeReference(reference, value);
		
		// With size=2 and upperBound=2: 2 < 2 is false, so result should be false
		// If mutated to <=, 2 <= 2 is true, which would be wrong
		assertThat(result)
			.as("Should return false when list.size() == upperBound")
			.isFalse();
	}

	@Test
	void testCanSetWithMultiValuedOppositeUnbounded() {
		// Create bidirectional reference with unbounded multi-valued opposite
		EClass ownerClass = ECORE_FACTORY.createEClass();
		ownerClass.setName("Owner");
		EClass targetClass = ECORE_FACTORY.createEClass();
		targetClass.setName("Target");
		
		createTestPackage("test", ownerClass, targetClass);
		
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("targets");
		reference.setEType(targetClass);
		reference.setContainment(false);
		reference.setUpperBound(-1); // Many
		
		EReference opposite = ECORE_FACTORY.createEReference();
		opposite.setName("owners");
		opposite.setEType(ownerClass);
		opposite.setContainment(false);
		opposite.setUpperBound(-1); // Unbounded
		
		reference.setEOpposite(opposite);
		opposite.setEOpposite(reference);
		
		ownerClass.getEStructuralFeatures().add(reference);
		targetClass.getEStructuralFeatures().add(opposite);
		
		EObject owner1 = EcoreUtil.create(ownerClass);
		EObject owner2 = EcoreUtil.create(ownerClass);
		EObject owner3 = EcoreUtil.create(ownerClass);
		EObject value = EcoreUtil.create(targetClass);
		
		// Add many owners to value (unbounded, so always OK)
		var ownersList = EMFUtils.getAsList(value, opposite);
		ownersList.add(owner1);
		ownersList.add(owner2);
		ownersList.add(owner3);
		
		boolean result = EMFUtils.canSetInThePresenceOfOppositeReference(reference, value);
		
		assertThat(result).isTrue();
	}

	@Test
	void testCanSetWithMultiValuedOppositeEmpty() {
		// Create bidirectional reference with multi-valued opposite
		EClass ownerClass = ECORE_FACTORY.createEClass();
		ownerClass.setName("Owner");
		EClass targetClass = ECORE_FACTORY.createEClass();
		targetClass.setName("Target");
		
		createTestPackage("test", ownerClass, targetClass);
		
		EReference reference = ECORE_FACTORY.createEReference();
		reference.setName("targets");
		reference.setEType(targetClass);
		reference.setContainment(false);
		reference.setUpperBound(-1); // Many
		
		EReference opposite = ECORE_FACTORY.createEReference();
		opposite.setName("owners");
		opposite.setEType(ownerClass);
		opposite.setContainment(false);
		opposite.setUpperBound(2); // Multi-valued with upper bound of 2
		
		reference.setEOpposite(opposite);
		opposite.setEOpposite(reference);
		
		ownerClass.getEStructuralFeatures().add(reference);
		targetClass.getEStructuralFeatures().add(opposite);
		
		EObject value = EcoreUtil.create(targetClass);
		// opposite list is empty
		
		boolean result = EMFUtils.canSetInThePresenceOfOppositeReference(reference, value);
		
		assertThat(result).isTrue();
	}

	// ========== findFeatureMapGroupMembers tests ==========

	@Test
	void testFindFeatureMapGroupMembers_WithRealFeatureMap() {
		// Load the featuremap-example.ecore model
		EPackage pkg = loadEcoreModel("src/test/resources/inputs", "featuremap-example.ecore");
		
		EClass documentClass = assertEClassExists(pkg, "Document");
		EAttribute contentGroupAttr = assertEAttributeExists(documentClass, "contentGroup");
		
		var groupMembers = EMFUtils.findFeatureMapGroupMembers(contentGroupAttr);
		
		assertThat(groupMembers)
			.as("Should find sections and figures as group members")
			.hasSize(2)
			.extracting(EReference::getName)
			.containsExactlyInAnyOrder("sections", "figures");
	}

	@Test
	void testFindFeatureMapGroupMembers_WithNoGroupMembers() {
		// Create a simple EClass with an attribute but no feature map group
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "test", "test.ecore");
		
		EClass testClass = ECORE_FACTORY.createEClass();
		testClass.setName("TestClass");
		pkg.getEClassifiers().add(testClass);
		
		EAttribute normalAttr = ECORE_FACTORY.createEAttribute();
		normalAttr.setName("normalAttribute");
		normalAttr.setEType(EcorePackage.Literals.ESTRING);
		testClass.getEStructuralFeatures().add(normalAttr);
		
		var groupMembers = EMFUtils.findFeatureMapGroupMembers(normalAttr);
		
		assertThat(groupMembers)
			.as("Should return empty list when no references are part of the group")
			.isEmpty();
	}

	@Test
	void testFindFeatureMapGroupMembers_WithNoReferences() {
		// Create an EClass with only attributes, no references
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "test", "test.ecore");
		
		EClass testClass = ECORE_FACTORY.createEClass();
		testClass.setName("TestClass");
		pkg.getEClassifiers().add(testClass);
		
		EAttribute attr1 = ECORE_FACTORY.createEAttribute();
		attr1.setName("attr1");
		attr1.setEType(EcorePackage.Literals.ESTRING);
		testClass.getEStructuralFeatures().add(attr1);
		
		EAttribute featureMapAttr = ECORE_FACTORY.createEAttribute();
		featureMapAttr.setName("featureMapAttr");
		featureMapAttr.setEType(EcorePackage.Literals.EFEATURE_MAP_ENTRY);
		testClass.getEStructuralFeatures().add(featureMapAttr);
		
		var groupMembers = EMFUtils.findFeatureMapGroupMembers(featureMapAttr);
		
		assertThat(groupMembers)
			.as("Should return empty list when EClass has no references")
			.isEmpty();
	}

	@Test
	void testFindFeatureMapGroupMembers_WithReferencesNotInGroup() {
		// Create an EClass with references that are not part of the feature map group
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "test", "test.ecore");
		
		EClass testClass = ECORE_FACTORY.createEClass();
		testClass.setName("TestClass");
		pkg.getEClassifiers().add(testClass);
		
		EClass otherClass = ECORE_FACTORY.createEClass();
		otherClass.setName("OtherClass");
		pkg.getEClassifiers().add(otherClass);
		
		EAttribute featureMapAttr = ECORE_FACTORY.createEAttribute();
		featureMapAttr.setName("featureMapAttr");
		featureMapAttr.setEType(EcorePackage.Literals.EFEATURE_MAP_ENTRY);
		testClass.getEStructuralFeatures().add(featureMapAttr);
		
		// Add a regular reference (not part of the group)
		EReference regularRef = ECORE_FACTORY.createEReference();
		regularRef.setName("regularRef");
		regularRef.setEType(otherClass);
		testClass.getEStructuralFeatures().add(regularRef);
		
		var groupMembers = EMFUtils.findFeatureMapGroupMembers(featureMapAttr);
		
		assertThat(groupMembers)
			.as("Should return empty list when references are not part of the group")
			.isEmpty();
	}

	@Test
	void testFindFeatureMapGroupMembers_IncludesInheritedReferences() {
		// Test that getEAllReferences() is used, so inherited references are included
		EPackage pkg = loadEcoreModel("src/test/resources/inputs", "featuremap-example.ecore");
		
		EClass documentClass = assertEClassExists(pkg, "Document");
		EAttribute contentGroupAttr = assertEAttributeExists(documentClass, "contentGroup");
		
		createResourceSet();
		EClass extendedDocClass = ECORE_FACTORY.createEClass();
		extendedDocClass.setName("ExtendedDocument");
		extendedDocClass.getESuperTypes().add(documentClass);
		pkg.getEClassifiers().add(extendedDocClass);
		
		// The subclass should find the same group members as the parent
		var groupMembers = EMFUtils.findFeatureMapGroupMembers(contentGroupAttr);
		
		assertThat(groupMembers)
			.as("Should find inherited references that are part of the group")
			.hasSize(2)
			.extracting(EReference::getName)
			.containsExactlyInAnyOrder("sections", "figures");
	}

	@Test
	void testFindFeatureMapGroupMembers_PreservesOrder() {
		// Load the featuremap model and verify order is consistent
		EPackage pkg = loadEcoreModel("src/test/resources/inputs", "featuremap-example.ecore");
		
		EClass documentClass = assertEClassExists(pkg, "Document");
		EAttribute contentGroupAttr = assertEAttributeExists(documentClass, "contentGroup");
		
		// Call multiple times to ensure consistent order
		var groupMembers1 = EMFUtils.findFeatureMapGroupMembers(contentGroupAttr);
		var groupMembers2 = EMFUtils.findFeatureMapGroupMembers(contentGroupAttr);
		
		assertThat(groupMembers1)
			.as("Multiple calls should return consistent order")
			.containsExactlyElementsOf(groupMembers2);
	}

	@Test
	void testFindFeatureMapGroupMembers_WithMultipleFeatureMaps() {
		// Test that references pointing to different feature maps are not included
		// This tests the branch: group != null && !group.equals(featureMapAttribute)
		var resourceSet = createResourceSet();
		var pkg = createEPackageInResource(resourceSet, "test", "test.ecore");
		
		EClass testClass = ECORE_FACTORY.createEClass();
		testClass.setName("TestClass");
		pkg.getEClassifiers().add(testClass);
		
		EClass contentClass = ECORE_FACTORY.createEClass();
		contentClass.setName("ContentClass");
		pkg.getEClassifiers().add(contentClass);
		
		// Create first feature map
		EAttribute featureMap1 = ECORE_FACTORY.createEAttribute();
		featureMap1.setName("featureMap1");
		featureMap1.setEType(EcorePackage.Literals.EFEATURE_MAP_ENTRY);
		testClass.getEStructuralFeatures().add(featureMap1);
		
		// Create second feature map
		EAttribute featureMap2 = ECORE_FACTORY.createEAttribute();
		featureMap2.setName("featureMap2");
		featureMap2.setEType(EcorePackage.Literals.EFEATURE_MAP_ENTRY);
		testClass.getEStructuralFeatures().add(featureMap2);
		
		// Create references - one for each feature map
		EReference ref1 = ECORE_FACTORY.createEReference();
		ref1.setName("ref1");
		ref1.setEType(contentClass);
		testClass.getEStructuralFeatures().add(ref1);
		
		EReference ref2 = ECORE_FACTORY.createEReference();
		ref2.setName("ref2");
		ref2.setEType(contentClass);
		testClass.getEStructuralFeatures().add(ref2);
		
		// Set ExtendedMetaData annotations to link references to different feature maps
		var extendedMetaData = org.eclipse.emf.ecore.util.ExtendedMetaData.INSTANCE;
		extendedMetaData.setGroup(ref1, featureMap1);
		extendedMetaData.setGroup(ref2, featureMap2);
		
		// Query for members of featureMap1 - should only find ref1, not ref2
		var groupMembers = EMFUtils.findFeatureMapGroupMembers(featureMap1);
		
		assertThat(groupMembers)
			.as("Should only find references belonging to the specific feature map")
			.hasSize(1)
			.extracting(EReference::getName)
			.containsExactly("ref1");
		
		// Query for members of featureMap2 - should only find ref2, not ref1
		var groupMembers2 = EMFUtils.findFeatureMapGroupMembers(featureMap2);
		
		assertThat(groupMembers2)
			.as("Should only find references belonging to the second feature map")
			.hasSize(1)
			.extracting(EReference::getName)
			.containsExactly("ref2");
	}
}
