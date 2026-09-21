package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEClassExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEReferenceExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.loadEcoreModel;
import static io.github.lorenzobettini.emfmodelgenerator.EMFValidationResultTest.diagnostic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EMFStandardModelValidatorTest {

	private static final String TEST_INPUTS_DIR = "target/inputs";
	private static final EObject FIRST_ROOT = EcoreFactory.eINSTANCE.createEObject();
	private static final EObject SECOND_ROOT = EcoreFactory.eINSTANCE.createEObject();

	@AfterEach
	void cleanupRegisteredPackages() {
		EMFTestUtils.cleanupRegisteredPackages();
	}

	@Test
	void standardFactoryCreatesValidator() {
		assertThat(EMFModelValidator.standard())
				.isInstanceOf(EMFStandardModelValidator.class);
	}

	@Test
	void validatesOneValidRoot() {
		var root = EcoreFactory.eINSTANCE.createEObject();

		var result = EMFModelValidator.standard().validate(root);

		assertThat(result.isValid()).isTrue();
		assertThat(result.rejectedSeverity()).isEqualTo(Diagnostic.ERROR);
		assertThat(result.diagnostic().getSeverity()).isEqualTo(Diagnostic.OK);
	}

	@Test
	void requiredBookAuthorFailsWithPreservedDiagnosticTree() {
		var resourceSet = new ResourceSetImpl();
		var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore", resourceSet);
		var bookClass = assertEClassExists(ePackage, "Book");
		var authorReference = assertEReferenceExists(bookClass, "author");
		var book = ePackage.getEFactoryInstance().create(bookClass);

		var result = EMFModelValidator.standard().validate(book);

		assertThat(book.eIsSet(authorReference)).isFalse();
		assertThat(result.kind()).isEqualTo(EMFValidationKind.VALIDATION_FAILURE);
		assertThat(result.flattenedDiagnostics())
				.extracting(Diagnostic::getMessage)
				.anySatisfy(message -> assertThat(message).containsAnyOf("author", "lower bound"));
	}

	@Test
	void optionalMissingBookOnTapeReaderIsValid() {
		var resourceSet = new ResourceSetImpl();
		var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore", resourceSet);
		var bookOnTapeClass = assertEClassExists(ePackage, "BookOnTape");
		var readerReference = assertEReferenceExists(bookOnTapeClass, "reader");
		var bookOnTape = ePackage.getEFactoryInstance().create(bookOnTapeClass);

		var result = EMFModelValidator.standard().validate(bookOnTape);

		assertThat(bookOnTape.eIsSet(readerReference)).isFalse();
		assertThat(result.isValid()).isTrue();
	}

	@Test
	void aggregatesEveryRootInOrderIncludingValidAndInvalidDiagnostics() {
		var validated = new ArrayList<EObject>();
		var validator = validator(root -> {
			validated.add(root);
			return root == FIRST_ROOT
					? diagnostic(Diagnostic.ERROR, "invalid first")
					: diagnostic(Diagnostic.WARNING, "warning second");
		});

		var result = validator.validateAll(List.of(FIRST_ROOT, SECOND_ROOT));

		assertThat(validated).containsExactly(FIRST_ROOT, SECOND_ROOT);
		assertThat(result.kind()).isEqualTo(EMFValidationKind.VALIDATION_FAILURE);
		assertThat(result.diagnostic().getChildren())
				.extracting(Diagnostic::getMessage)
				.containsExactly("invalid first", "warning second");
	}

	@Test
	void warningsDoNotInvalidateSingleOrMultipleRootResults() {
		var warning = diagnostic(Diagnostic.WARNING, "warning");
		var validator = validator(root -> warning);

		assertThat(validator.validate(FIRST_ROOT).isValid()).isTrue();
		var aggregate = validator.validateAll(List.of(FIRST_ROOT, SECOND_ROOT));
		assertThat(aggregate.isValid()).isTrue();
		assertThat(aggregate.diagnostic().getSeverity()).isEqualTo(Diagnostic.WARNING);
	}

	@Test
	void emptyRootsProduceValidAggregate() {
		var result = EMFModelValidator.standard().validateAll(List.of());

		assertThat(result.isValid()).isTrue();
		assertThat(result.diagnostic().getSeverity()).isEqualTo(Diagnostic.OK);
		assertThat(result.diagnostic().getChildren()).isEmpty();
	}

	@Test
	void standardValidationAggregatesSeveralValidRoots() {
		var result = EMFModelValidator.standard()
				.validateAll(List.of(FIRST_ROOT, SECOND_ROOT));

		assertThat(result.isValid()).isTrue();
		assertThat(result.diagnostic().getChildren()).hasSize(2);
		assertThat(result.diagnostic().getChildren())
				.allSatisfy(diagnostic -> assertThat(diagnostic.getSeverity())
						.isEqualTo(Diagnostic.OK));
	}

	@Test
	void runtimeFailureIsClassifiedAndDoesNotStopLaterRoots() {
		var validated = new ArrayList<EObject>();
		var validator = validator(root -> {
			validated.add(root);
			if (root == FIRST_ROOT) {
				throw new IllegalStateException("broken validator");
			}
			return diagnostic(Diagnostic.OK, "valid second");
		});

		var singleResult = validator.validate(FIRST_ROOT);
		assertThat(singleResult.kind()).isEqualTo(EMFValidationKind.VALIDATOR_FAILURE);
		assertThat(singleResult.diagnostic().getMessage()).contains("broken validator");
		assertThat(singleResult.diagnostic().getData())
				.singleElement().isInstanceOf(IllegalStateException.class);

		validated.clear();
		var aggregate = validator.validateAll(List.of(FIRST_ROOT, SECOND_ROOT));
		assertThat(validated).containsExactly(FIRST_ROOT, SECOND_ROOT);
		assertThat(aggregate.kind()).isEqualTo(EMFValidationKind.VALIDATOR_FAILURE);
		assertThat(aggregate.diagnostic().getChildren()).hasSize(2);
	}

	@Test
	void rejectsNullInputsWithClearMessages() {
		var validator = EMFModelValidator.standard();

		assertThatNullPointerException().isThrownBy(() -> validator.validate(null))
				.withMessage("root");
		assertThatNullPointerException().isThrownBy(() -> validator.validateAll(null))
				.withMessage("roots");
		assertThatNullPointerException()
				.isThrownBy(() -> validator.validateAll(java.util.Arrays.asList(FIRST_ROOT, null)))
				.withMessage("root");
	}

	@Test
	void closeIsIdempotent() {
		var validator = EMFModelValidator.standard();

		validator.validate(FIRST_ROOT);
		assertThatNoException().isThrownBy(validator::close);
		assertThatNoException().isThrownBy(validator::close);
	}

	private static EMFStandardModelValidator validator(
			final java.util.function.Function<EObject, Diagnostic> diagnosticFunction) {
		return new EMFStandardModelValidator(diagnosticFunction);
	}
}
