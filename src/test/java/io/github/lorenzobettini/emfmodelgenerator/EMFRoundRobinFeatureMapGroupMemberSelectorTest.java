package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResourceSet;
import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.ExtendedMetaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for EMFRoundRobinFeatureMapGroupMemberSelector.
 * This class achieves 100% coverage of the round-robin feature map group member selection functionality.
 */
class EMFRoundRobinFeatureMapGroupMemberSelectorTest {

	private static final EcoreFactory ECORE_FACTORY = EcoreFactory.eINSTANCE;

	private EMFRoundRobinFeatureMapGroupMemberSelector selector;
	private ResourceSet resourceSet;
	private EPackage testPackage;
	private EClass testClass;
	private EAttribute featureMapAttribute;
	private EReference member1;
	private EReference member2;
	private EReference member3;
	private Resource contextResource;
	private EObject context;

	@BeforeEach
	void setUp() {
		resourceSet = createResourceSet();

		// Create a test package
		testPackage = ECORE_FACTORY.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");

		// Register the package in the global registry
		EMFTestUtils.registerPackageForTest(testPackage);

		// Create a test class with a feature map
		testClass = ECORE_FACTORY.createEClass();
		testClass.setName("TestClass");
		testPackage.getEClassifiers().add(testClass);

		// Create feature map attribute
		featureMapAttribute = ECORE_FACTORY.createEAttribute();
		featureMapAttribute.setName("featureMap");
		featureMapAttribute.setEType(EcorePackage.Literals.EFEATURE_MAP_ENTRY);
		testClass.getEStructuralFeatures().add(featureMapAttribute);

		// Create some target classes for references
		EClass targetClass1 = ECORE_FACTORY.createEClass();
		targetClass1.setName("Target1");
		testPackage.getEClassifiers().add(targetClass1);

		EClass targetClass2 = ECORE_FACTORY.createEClass();
		targetClass2.setName("Target2");
		testPackage.getEClassifiers().add(targetClass2);

		EClass targetClass3 = ECORE_FACTORY.createEClass();
		targetClass3.setName("Target3");
		testPackage.getEClassifiers().add(targetClass3);

		// Create group member references
		member1 = ECORE_FACTORY.createEReference();
		member1.setName("member1");
		member1.setEType(targetClass1);
		testClass.getEStructuralFeatures().add(member1);

		member2 = ECORE_FACTORY.createEReference();
		member2.setName("member2");
		member2.setEType(targetClass2);
		testClass.getEStructuralFeatures().add(member2);

		member3 = ECORE_FACTORY.createEReference();
		member3.setName("member3");
		member3.setEType(targetClass3);
		testClass.getEStructuralFeatures().add(member3);

		// Set ExtendedMetaData to link references to feature map
		var extendedMetaData = ExtendedMetaData.INSTANCE;
		extendedMetaData.setGroup(member1, featureMapAttribute);
		extendedMetaData.setGroup(member2, featureMapAttribute);
		extendedMetaData.setGroup(member3, featureMapAttribute);

		// Create a Resource for the context in the ResourceSet
		contextResource = resourceSet.createResource(
			org.eclipse.emf.common.util.URI.createURI("context.xmi"));
		contextResource.getContents().add(testPackage);

		// Create a context instance
		context = EcoreUtil.create(testClass);
		contextResource.getContents().add(context);

		// Create selector
		selector = new EMFRoundRobinFeatureMapGroupMemberSelector();
	}

	@AfterEach
	void tearDown() {
		EMFTestUtils.cleanupRegisteredPackages();
	}

	@Test
	void testGetNextCandidate_RoundRobinSelection() {
		// First call should return member1
		var first = selector.getNextCandidate(context, featureMapAttribute);
		assertThat(first).isEqualTo(member1);

		// Second call should return member2
		var second = selector.getNextCandidate(context, featureMapAttribute);
		assertThat(second).isEqualTo(member2);

		// Third call should return member3
		var third = selector.getNextCandidate(context, featureMapAttribute);
		assertThat(third).isEqualTo(member3);

		// Fourth call should wrap around to member1
		var fourth = selector.getNextCandidate(context, featureMapAttribute);
		assertThat(fourth).isEqualTo(member1);

		// Fifth call should return member2 again
		var fifth = selector.getNextCandidate(context, featureMapAttribute);
		assertThat(fifth).isEqualTo(member2);
	}

	@Test
	void testGetNextCandidate_EmptyGroupMembers() {
		// Create a feature map with no group members
		EAttribute emptyFeatureMap = ECORE_FACTORY.createEAttribute();
		emptyFeatureMap.setName("emptyFeatureMap");
		emptyFeatureMap.setEType(EcorePackage.Literals.EFEATURE_MAP_ENTRY);
		testClass.getEStructuralFeatures().add(emptyFeatureMap);

		var result = selector.getNextCandidate(context, emptyFeatureMap);
		assertThat(result).isNull();
	}

	@Test
	void testGetNextCandidate_SingleGroupMember() {
		// Create a feature map with only one group member
		EAttribute singleFeatureMap = ECORE_FACTORY.createEAttribute();
		singleFeatureMap.setName("singleFeatureMap");
		singleFeatureMap.setEType(EcorePackage.Literals.EFEATURE_MAP_ENTRY);
		testClass.getEStructuralFeatures().add(singleFeatureMap);

		EClass targetClass = ECORE_FACTORY.createEClass();
		targetClass.setName("SingleTarget");
		testPackage.getEClassifiers().add(targetClass);

		EReference singleMember = ECORE_FACTORY.createEReference();
		singleMember.setName("singleMember");
		singleMember.setEType(targetClass);
		testClass.getEStructuralFeatures().add(singleMember);

		var extendedMetaData = ExtendedMetaData.INSTANCE;
		extendedMetaData.setGroup(singleMember, singleFeatureMap);

		// All calls should return the same member
		var first = selector.getNextCandidate(context, singleFeatureMap);
		assertThat(first).isEqualTo(singleMember);

		var second = selector.getNextCandidate(context, singleFeatureMap);
		assertThat(second).isEqualTo(singleMember);

		var third = selector.getNextCandidate(context, singleFeatureMap);
		assertThat(third).isEqualTo(singleMember);
	}

	@Test
	void testHasCandidates_WithGroupMembers() {
		assertThat(selector.hasCandidates(context, featureMapAttribute)).isTrue();
	}

	@Test
	void testHasCandidates_WithoutGroupMembers() {
		// Create a feature map with no group members
		EAttribute emptyFeatureMap = ECORE_FACTORY.createEAttribute();
		emptyFeatureMap.setName("emptyFeatureMap");
		emptyFeatureMap.setEType(EcorePackage.Literals.EFEATURE_MAP_ENTRY);
		testClass.getEStructuralFeatures().add(emptyFeatureMap);

		assertThat(selector.hasCandidates(context, emptyFeatureMap)).isFalse();
	}

	@Test
	void testReset_ClearsState() {
		// Advance through some selections
		selector.getNextCandidate(context, featureMapAttribute); // member1
		selector.getNextCandidate(context, featureMapAttribute); // member2

		// Reset should clear the state
		selector.reset();

		// Next call should start from member1 again
		var first = selector.getNextCandidate(context, featureMapAttribute);
		assertThat(first).isEqualTo(member1);
	}

	@Test
	void testReset_ClearsCache() {
		// Call getNextCandidate to populate the cache
		selector.getNextCandidate(context, featureMapAttribute);

		// Modify the group members by removing one
		var extendedMetaData = ExtendedMetaData.INSTANCE;
		extendedMetaData.setGroup(member3, null);

		// Reset should clear the cache
		selector.reset();

		// Now only 2 members should be found
		var first = selector.getNextCandidate(context, featureMapAttribute);
		assertThat(first).isEqualTo(member1);

		var second = selector.getNextCandidate(context, featureMapAttribute);
		assertThat(second).isEqualTo(member2);

		// Third call should wrap to member1 (not member3)
		var third = selector.getNextCandidate(context, featureMapAttribute);
		assertThat(third).isEqualTo(member1);
	}

	@Test
	void testMultipleFeatureMaps_IndependentState() {
		// Create a second feature map
		EAttribute featureMap2 = ECORE_FACTORY.createEAttribute();
		featureMap2.setName("featureMap2");
		featureMap2.setEType(EcorePackage.Literals.EFEATURE_MAP_ENTRY);
		testClass.getEStructuralFeatures().add(featureMap2);

		EClass targetClass = ECORE_FACTORY.createEClass();
		targetClass.setName("OtherTarget");
		testPackage.getEClassifiers().add(targetClass);

		EReference otherMember1 = ECORE_FACTORY.createEReference();
		otherMember1.setName("otherMember1");
		otherMember1.setEType(targetClass);
		testClass.getEStructuralFeatures().add(otherMember1);

		EReference otherMember2 = ECORE_FACTORY.createEReference();
		otherMember2.setName("otherMember2");
		otherMember2.setEType(targetClass);
		testClass.getEStructuralFeatures().add(otherMember2);

		var extendedMetaData = ExtendedMetaData.INSTANCE;
		extendedMetaData.setGroup(otherMember1, featureMap2);
		extendedMetaData.setGroup(otherMember2, featureMap2);

		// Advance first feature map to member2
		selector.getNextCandidate(context, featureMapAttribute); // member1
		var result1 = selector.getNextCandidate(context, featureMapAttribute); // member2
		assertThat(result1).isEqualTo(member2);

		// Second feature map should start from otherMember1
		var result2 = selector.getNextCandidate(context, featureMap2);
		assertThat(result2).isEqualTo(otherMember1);

		// First feature map should continue from member3
		var result3 = selector.getNextCandidate(context, featureMapAttribute);
		assertThat(result3).isEqualTo(member3);

		// Second feature map should continue from otherMember2
		var result4 = selector.getNextCandidate(context, featureMap2);
		assertThat(result4).isEqualTo(otherMember2);
	}
}
