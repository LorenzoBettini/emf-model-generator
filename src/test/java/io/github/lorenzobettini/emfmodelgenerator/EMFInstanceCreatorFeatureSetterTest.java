package io.github.lorenzobettini.emfmodelgenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collection;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EMFInstanceCreatorFeatureSetterTest {

	static class TestableEMFInstanceCreatorFeatureSetter
			extends EMFInstanceCreatorFeatureSetter<EReference> {
		int setSingleFeatureCalls = 0;
		int setMultiFeatureCalls = 0;

		public TestableEMFInstanceCreatorFeatureSetter() {
			super(2);
		}

		@Override
		protected void setSingleFeature(EObject owner, EReference feature) {
			setSingleFeatureCalls++;
			createInstance(owner, feature, feature.getEReferenceType());
		}

		@Override
		protected void setMultiFeature(EObject owner, EReference feature) {
			setMultiFeatureCalls++;
			createInstance(owner, feature, feature.getEReferenceType());
			createInstance(owner, feature, feature.getEReferenceType());
		}
	}

	private TestableEMFInstanceCreatorFeatureSetter setter;
	private EcoreFactory ecoreFactory;
	private EClass targetClass;
	private EClass subClass1;
	private EClass subClass2;
	private EReference reference;
	private EObject owner;

	@BeforeEach
	void setUp() {
		setter = new TestableEMFInstanceCreatorFeatureSetter();
		ecoreFactory = EcoreFactory.eINSTANCE;

		var testPackage = ecoreFactory.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");

		var ownerClass = ecoreFactory.createEClass();
		ownerClass.setName("Owner");
		testPackage.getEClassifiers().add(ownerClass);

		targetClass = ecoreFactory.createEClass();
		targetClass.setName("Target");
		targetClass.setAbstract(true);
		testPackage.getEClassifiers().add(targetClass);

		subClass1 = ecoreFactory.createEClass();
		subClass1.setName("SubClass1");
		subClass1.getESuperTypes().add(targetClass);
		testPackage.getEClassifiers().add(subClass1);

		subClass2 = ecoreFactory.createEClass();
		subClass2.setName("SubClass2");
		subClass2.getESuperTypes().add(targetClass);
		testPackage.getEClassifiers().add(subClass2);

		reference = ecoreFactory.createEReference();
		reference.setName("targets");
		reference.setEType(targetClass);
		reference.setUpperBound(-1);
		ownerClass.getEStructuralFeatures().add(reference);

		EMFTestUtils.registerPackageForTest(testPackage);
		owner = EcoreUtil.create(ownerClass);
	}

	@AfterEach
	void tearDown() {
		EMFTestUtils.cleanupRegisteredPackages();
	}

	@Test
	void testSetInstantiableSubclassSelectorStrategy() {
		var strategy = new EMFRoundRobinEClassCandidateSelector();
		
		setter.setInstantiableSubclassSelectorStrategy(strategy);
		
		// Verify strategy is used by creating an instance
		var instance = setter.createInstance(owner, reference, targetClass);
		assertThat(instance).isNotNull();
		assertThat(instance.eClass()).isIn(subClass1, subClass2);
	}

	@Test
	void testSetFeatureCreatingEObjectsClearsCreatedEObjects() {
		var firstCall = setter.setFeatureCreatingEObjects(owner, reference);
		assertThat(firstCall).hasSize(2);

		var secondCall = setter.setFeatureCreatingEObjects(owner, reference);

		assertThat(secondCall).hasSize(2);
		assertThat(firstCall).isNotSameAs(secondCall);
	}

	@Test
	void testSetFeatureCreatingEObjectsCallsSetFeature() {
		setter.setFeatureCreatingEObjects(owner, reference);

		assertThat(setter.setMultiFeatureCalls).isEqualTo(1);
	}

	@Test
	void testSetFeatureCreatingEObjectsReturnsCreatedEObjects() {
		Collection<EObject> result = setter.setFeatureCreatingEObjects(owner, reference);

		assertThat(result)
				.isNotNull()
				.hasSize(2);
	}

	@Test
	void testCreateInstanceWithCustomFunctionReturningInstance() {
		var customInstance = EcoreUtil.create(subClass1);
		setter.setFunctionFor(reference, o -> customInstance);

		var instance = setter.createInstance(owner, reference, targetClass);

		assertThat(instance).isSameAs(customInstance);
	}

	@Test
	void testCreateInstanceWithCustomFunctionReturningNull() {
		setter.setFunctionFor(reference, o -> null);
		var strategy = new EMFRoundRobinEClassCandidateSelector();
		setter.setInstantiableSubclassSelectorStrategy(strategy);

		var instance = setter.createInstance(owner, reference, targetClass);

		assertThat(instance).isNotNull();
		assertThat(instance.eClass()).isIn(subClass1, subClass2);
	}

	@Test
	void testCreateInstanceWhenNoInstantiableSubclass() {
		EMFCandidateSelectorStrategy<EClass, EClass> customStrategy = new EMFCandidateSelectorStrategy<EClass, EClass>() {
			@Override
			public EClass getNextCandidate(EObject context, EClass type) {
				return null;
			}

			@Override
			public boolean hasCandidates(EObject context, EClass type) {
				return false;
			}
		};
		setter.setInstantiableSubclassSelectorStrategy(customStrategy);

		var instance = setter.createInstance(owner, reference, targetClass);

		assertNull(instance);
	}

	@Test
	void testCreateInstanceUsesStrategyToSelectSubclass() {
		EMFCandidateSelectorStrategy<EClass, EClass> customStrategy = new EMFCandidateSelectorStrategy<EClass, EClass>() {
			@Override
			public EClass getNextCandidate(EObject context, EClass type) {
				return subClass2;
			}

			@Override
			public boolean hasCandidates(EObject context, EClass type) {
				return true;
			}
		};
		setter.setInstantiableSubclassSelectorStrategy(customStrategy);

		var instance = setter.createInstance(owner, reference, targetClass);

		assertThat(instance.eClass()).isEqualTo(subClass2);
	}

	@Test
	void testSetFeatureCreatingEObjectsWithMultiValuedReference() {
		var result = setter.setFeatureCreatingEObjects(owner, reference);

		assertThat(result).hasSize(2);
		assertThat(setter.setMultiFeatureCalls).isEqualTo(1);
		assertThat(setter.setSingleFeatureCalls).isZero();
	}

	@Test
	void testSetFeatureCreatingEObjectsWithSingleValuedReference() {
		reference.setUpperBound(1);

		var result = setter.setFeatureCreatingEObjects(owner, reference);

		assertThat(result).hasSize(1);
		assertThat(setter.setSingleFeatureCalls).isEqualTo(1);
		assertThat(setter.setMultiFeatureCalls).isZero();
	}

	@Test
	void testCreateInstanceWithCustomFunctionAddsToReturnedCollection() {
		var customInstance = EcoreUtil.create(subClass1);
		setter.setFunctionFor(reference, o -> customInstance);

		var result = setter.setFeatureCreatingEObjects(owner, reference);

		assertThat(result).contains(customInstance);
	}
}
