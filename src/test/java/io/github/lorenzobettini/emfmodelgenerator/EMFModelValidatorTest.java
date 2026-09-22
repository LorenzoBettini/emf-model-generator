package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFValidationResultTest.diagnostic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.Collection;
import java.util.List;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.jupiter.api.Test;

class EMFModelValidatorTest {

	private static final EObject ROOT = EcoreFactory.eINSTANCE.createEObject();

	@Test
	void validateOrThrowReturnsNormallyForValidResult() {
		try (var validator = new StubValidator(validResult(), validResult())) {
			assertThatNoException().isThrownBy(() -> validator.validateOrThrow(ROOT));
			assertThat(validator.validatedRoot).isSameAs(ROOT);
		}
	}

	@Test
	void validateOrThrowRetainsInvalidResultInException() {
		var invalid = invalidResult();
		try (var validator = new StubValidator(invalid, validResult())) {
			assertThatExceptionOfType(EMFValidationException.class)
					.isThrownBy(() -> validator.validateOrThrow(ROOT))
					.satisfies(exception -> assertThat(exception.getResult()).isSameAs(invalid));
		}
	}

	@Test
	void validateAllOrThrowHandlesValidAndInvalidResults() {
		var roots = List.of(ROOT);
		try (var validValidator = new StubValidator(validResult(), validResult())) {
			assertThatNoException().isThrownBy(() -> validValidator.validateAllOrThrow(roots));
			assertThat(validValidator.validatedRoots).isSameAs(roots);
		}
		var invalid = invalidResult();
		try (var invalidValidator = new StubValidator(validResult(), invalid)) {
			assertThatExceptionOfType(EMFValidationException.class)
					.isThrownBy(() -> invalidValidator.validateAllOrThrow(roots))
					.satisfies(exception -> assertThat(exception.getResult()).isSameAs(invalid));
		}
	}

	@Test
	void closeIsANoOpByDefault() {
		var validator = new StubValidator(validResult(), validResult());

		assertThatNoException().isThrownBy(validator::close);
	}

	@Test
	void factoryReceivesResourceSet() {
		ResourceSet resourceSet = new ResourceSetImpl();
		var validator = new StubValidator(validResult(), validResult());
		EMFModelValidator.Factory factory = supplied -> {
			assertThat(supplied).isSameAs(resourceSet);
			return validator;
		};

		assertThat(factory.create(resourceSet)).isSameAs(validator);
	}

	private static EMFValidationResult validResult() {
		return new EMFValidationResult(diagnostic(Diagnostic.OK, "valid"), Diagnostic.ERROR,
				EMFValidationKind.VALID);
	}

	private static EMFValidationResult invalidResult() {
		return new EMFValidationResult(diagnostic(Diagnostic.ERROR, "invalid"), Diagnostic.ERROR,
				EMFValidationKind.VALIDATION_FAILURE);
	}

	private static final class StubValidator implements EMFModelValidator {
		private final EMFValidationResult singleResult;
		private final EMFValidationResult allResult;
		private EObject validatedRoot;
		private Collection<? extends EObject> validatedRoots;

		private StubValidator(final EMFValidationResult singleResult,
				final EMFValidationResult allResult) {
			this.singleResult = singleResult;
			this.allResult = allResult;
		}

		@Override
		public EMFValidationResult validate(final EObject root) {
			validatedRoot = root;
			return singleResult;
		}

		@Override
		public EMFValidationResult validateAll(final Collection<? extends EObject> roots) {
			validatedRoots = roots;
			return allResult;
		}
	}
}
