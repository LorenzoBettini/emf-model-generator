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

class StandardEMFModelValidatorTest {

	private static final String TEST_INPUTS_DIR = "target/inputs";
	private static final EObject FIRST_ROOT = EcoreFactory.eINSTANCE.createEObject();
	private static final EObject SECOND_ROOT = EcoreFactory.eINSTANCE.createEObject();

	@AfterEach
	void cleanupRegisteredPackages() {
		EMFTestUtils.cleanupRegisteredPackages();
	}

	@Test
	void standardFactoryCreatesValidatorAndRejectsNullResourceSet() {
		assertThat(EMFModelValidator.standard(new ResourceSetImpl()))
				.isInstanceOf(StandardEMFModelValidator.class);
		assertThatNullPointerException()
				.isThrownBy(() -> EMFModelValidator.standard(null))
				.withMessage("resourceSet");
	}

	@Test
	void validatesOneValidRoot() {
		var root = EcoreFactory.eINSTANCE.createEObject();

		var result = EMFModelValidator.standard(new ResourceSetImpl()).validate(root);

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

		var result = EMFModelValidator.standard(resourceSet).validate(book);

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

		var result = EMFModelValidator.standard(resourceSet).validate(bookOnTape);

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
		var result = EMFModelValidator.standard(new ResourceSetImpl()).validateAll(List.of());

		assertThat(result.isValid()).isTrue();
		assertThat(result.diagnostic().getSeverity()).isEqualTo(Diagnostic.OK);
		assertThat(result.diagnostic().getChildren()).isEmpty();
	}

	@Test
	void standardValidationAggregatesSeveralValidRoots() {
		var result = EMFModelValidator.standard(new ResourceSetImpl())
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
		var validator = EMFModelValidator.standard(new ResourceSetImpl());

		assertThatNullPointerException().isThrownBy(() -> validator.validate(null))
				.withMessage("root");
		assertThatNullPointerException().isThrownBy(() -> validator.validateAll(null))
				.withMessage("roots");
		assertThatNullPointerException()
				.isThrownBy(() -> validator.validateAll(java.util.Arrays.asList(FIRST_ROOT, null)))
				.withMessage("roots must not contain null elements");
	}

	@Test
	void doesNotMutateOrRetainCallerResourceSetAndCloseIsIdempotent() {
		var resourceSet = new ResourceSetImpl();
		var resources = List.copyOf(resourceSet.getResources());
		var packageRegistry = new java.util.HashMap<>(resourceSet.getPackageRegistry());
		var factoryRegistry = new java.util.HashMap<>(
				resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap());
		var validator = EMFModelValidator.standard(resourceSet);

		validator.validate(FIRST_ROOT);
		assertThatNoException().isThrownBy(validator::close);
		assertThatNoException().isThrownBy(validator::close);

		assertThat(resourceSet.getResources()).containsExactlyElementsOf(resources);
		assertThat(resourceSet.getPackageRegistry()).containsExactlyInAnyOrderEntriesOf(packageRegistry);
		assertThat(resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap())
				.containsExactlyInAnyOrderEntriesOf(factoryRegistry);
	}

	@Test
	void convertsNullDiagnosticIntoValidatorFailure() {
		var result = validator(root -> null).validate(FIRST_ROOT);

		assertThat(result.kind()).isEqualTo(EMFValidationKind.VALIDATOR_FAILURE);
		assertThat(result.diagnostic().getMessage()).contains("null diagnostic");
	}

	private static StandardEMFModelValidator validator(
			final java.util.function.Function<EObject, Diagnostic> diagnosticFunction) {
		return new StandardEMFModelValidator(new ResourceSetImpl(), diagnosticFunction);
	}
}
