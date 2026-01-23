package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createCrossEReference;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEAttribute;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEClass;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEPackage;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createInstance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.impl.EStructuralFeatureImpl;
import org.junit.jupiter.api.Test;

class EMFConfigurableFeatureSetterTest {

	static final int DEFAULT_MAX = 5;

	static class TestableEMFConfigurableFeatureSetter<F1 extends EStructuralFeature, F2 extends EStructuralFeature, V>
			extends EMFConfigurableFeatureSetter<F1, F2, V> {
		public TestableEMFConfigurableFeatureSetter() {
			super(DEFAULT_MAX);
		}

		@Override
		protected void setSingleFeature(EObject owner, F1 feature) {
			// intentionally left blank
		}

		@Override
		protected void setMultiFeature(EObject owner, F1 feature) {
			// intentionally left blank
		}
	}

	@Test
	void testDefaultMax() {
		var setter = new TestableEMFConfigurableFeatureSetter<EStructuralFeature, EStructuralFeature, Object>();

		assertEquals(DEFAULT_MAX, setter.getMaxCountFor(null, null));

		int newDefaultMax = 10;
		setter.setDefaultMaxCount(newDefaultMax);
		assertEquals(newDefaultMax, setter.getMaxCountFor(null, null));
	}

	@Test
	void testFeatureSpecificMax() {
		var setter = new TestableEMFConfigurableFeatureSetter<EStructuralFeature, EStructuralFeature, Object>();

		EStructuralFeature feature1 = new EStructuralFeatureImpl() {
		};
		EStructuralFeature feature2 = new EStructuralFeatureImpl() {
		};

		int specificMax1 = 10;
		setter.setMaxCountFor(feature1, specificMax1);
		int specificMax2 = 15;
		setter.setMaxCountFor(feature2, specificMax2);

		assertEquals(specificMax1, setter.getMaxCountFor(null, feature1));
		assertEquals(specificMax2, setter.getMaxCountFor(null, feature2));
		assertEquals(DEFAULT_MAX, setter.getMaxCountFor(null, null));
	}

	@Test
	void testFeatureFunction() {
		var setter = new TestableEMFConfigurableFeatureSetter<EStructuralFeature, EStructuralFeature, Object>();

		EStructuralFeature feature = new EStructuralFeatureImpl() {
		};

		// Initially, no value should be associated
		assertEquals(null, setter.getFunctionFor(feature));

		// Set value for the feature
		String value = "TestValue";
		setter.setFunctionFor(feature, owner -> value);
		assertEquals(value, setter.getFunctionFor(feature).apply(null));
		String newValue = "NewValue";
		setter.setFunctionFor(feature, owner -> newValue);
		assertEquals(newValue, setter.getFunctionFor(feature).apply(null));
	}

	@Test
	void testShouldSetAttribute() {
		var setter = new TestableEMFConfigurableFeatureSetter<EStructuralFeature, EStructuralFeature, Object>();

		EPackage testPackage = createEPackage("test", "http://test", "test");
		EClass ownerClass = createEClass(testPackage, "Owner");
		var attribute = createEAttribute(ownerClass, "attribute", EcorePackage.eINSTANCE.getEString());
		var owner = createInstance(ownerClass);

		// Initially, the feature is not set, so it should be set
		assertTrue(setter.shouldSetFeature(owner, attribute));
		// Set the feature value
		owner.eSet(attribute, "value");
		// Now, the feature is set, so it should not be set again
		assertFalse(setter.shouldSetFeature(owner, attribute));
		// unset the feature
		owner.eUnset(attribute);
		// After unset, it should be set again (validity checking is done in the populator)
		assertTrue(setter.shouldSetFeature(owner, attribute));
	}

	@Test
	void testShouldSetFeature() {
		var setter = new TestableEMFConfigurableFeatureSetter<EStructuralFeature, EStructuralFeature, Object>();

		EPackage testPackage = createEPackage("test", "http://test", "test");
		EClass ownerClass = createEClass(testPackage, "Owner");

		// Create a valid reference feature
		var singleReference = createCrossEReference(ownerClass, "reference", ownerClass, 1);
		// Create a valid multi-valued reference feature
		var multiReference = createCrossEReference(ownerClass, "multiReference", ownerClass, -1);
		// create two multi references with opposite
		var oppositeReference1 = createCrossEReference(ownerClass, "opposite1", ownerClass, 0, EStructuralFeature.UNBOUNDED_MULTIPLICITY);
		var oppositeReference2 = createCrossEReference(ownerClass, "opposite2", ownerClass, 0, EStructuralFeature.UNBOUNDED_MULTIPLICITY);
		oppositeReference1.setEOpposite(oppositeReference2);
		oppositeReference2.setEOpposite(oppositeReference1);

		var owner = createInstance(ownerClass);

		// Initially, the feature is not set, so it should be set
		assertTrue(setter.shouldSetFeature(owner, singleReference));
		// Set the feature value
		owner.eSet(singleReference, createInstance(ownerClass));
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
		list.add(createInstance(ownerClass));
		// Now, the feature is set, so it should not be set again
		assertFalse(setter.shouldSetFeature(owner, multiReference));
	}

	@Test
	void testSetFeature() {
		var setter = new TestableEMFConfigurableFeatureSetter<EStructuralFeature, EStructuralFeature, Object>() {
			@Override
			protected void setSingleFeature(EObject owner, EStructuralFeature feature) {
				owner.eSet(feature, "single");
			}

			@Override
			protected void setMultiFeature(EObject owner, EStructuralFeature feature) {
				var list = EMFUtils.getAsList(owner, feature);
				list.add("multi1");
				list.add("multi2");
			}
		};

		EPackage testPackage = createEPackage("test", "http://test", "test");
		EClass ownerClass = createEClass(testPackage, "Owner");

		// Create a valid single-valued attribute feature
		var singleAttribute = createEAttribute(ownerClass, "singleAttribute", EcorePackage.eINSTANCE.getEString());
		// Create a valid multi-valued attribute feature
		var multiAttribute = createEAttribute(ownerClass, "multiAttribute", EcorePackage.eINSTANCE.getEString(), 0, EStructuralFeature.UNBOUNDED_MULTIPLICITY);
		// Create an invalid attribute feature
		var invalidAttribute = createEAttribute(ownerClass, "invalidAttribute", EcorePackage.eINSTANCE.getEString());
		invalidAttribute.setChangeable(false);
		invalidAttribute.setDefaultValueLiteral("invalid");
		var alreadySetAttribute = createEAttribute(ownerClass, "alreadySetAttribute", EcorePackage.eINSTANCE.getEString());

		var owner = createInstance(ownerClass);

		setter.setFeature(owner, singleAttribute);
		assertEquals("single", owner.eGet(singleAttribute));

		setter.setFeature(owner, multiAttribute);
		var list = EMFUtils.getAsList(owner, multiAttribute);
		assertEquals(2, list.size());
		assertEquals("multi1", list.get(0));
		assertEquals("multi2", list.get(1));

		owner.eSet(alreadySetAttribute, "alreadySet");
		setter.setFeature(owner, alreadySetAttribute);
		// value should not be changed
		assertEquals("alreadySet", owner.eGet(alreadySetAttribute));
	}
}
