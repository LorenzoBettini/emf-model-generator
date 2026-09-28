package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEAttribute;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEClass;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEPackage;
import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EcorePackage;
import org.junit.jupiter.api.Test;

class EMFCountConfigurableFeatureSetterTest {

	private static final int DEFAULT_MAX_COUNT = 2;

	private static final class TestableCountSetter
			extends EMFCountConfigurableFeatureSetter<EAttribute> {

		private TestableCountSetter() {
			super(DEFAULT_MAX_COUNT);
		}
	}

	@Test
	void defaultCountCanBeReconfigured() {
		final var setter = new TestableCountSetter();

		assertThat(setter.getMaxCountFor(null, null)).isEqualTo(DEFAULT_MAX_COUNT);

		setter.setDefaultMaxCount(4);

		assertThat(setter.getMaxCountFor(null, null)).isEqualTo(4);
	}

	@Test
	void featureSpecificCountsOverrideTheDefaultIndependently() {
		final var ePackage = createEPackage("test", "http://test", "test");
		final var ownerClass = createEClass(ePackage, "Owner");
		final var first = createEAttribute(
				ownerClass, "first", EcorePackage.eINSTANCE.getEString());
		final var second = createEAttribute(
				ownerClass, "second", EcorePackage.eINSTANCE.getEString());
		final var unconfigured = createEAttribute(
				ownerClass, "unconfigured", EcorePackage.eINSTANCE.getEString());
		final var setter = new TestableCountSetter();

		setter.setMaxCountFor(first, 3);
		setter.setMaxCountFor(second, 5);

		assertThat(setter.getMaxCountFor(null, first)).isEqualTo(3);
		assertThat(setter.getMaxCountFor(null, second)).isEqualTo(5);
		assertThat(setter.getMaxCountFor(null, unconfigured)).isEqualTo(DEFAULT_MAX_COUNT);
	}
}
