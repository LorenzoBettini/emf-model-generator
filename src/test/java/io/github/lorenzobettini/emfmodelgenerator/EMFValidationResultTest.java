package io.github.lorenzobettini.emfmodelgenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.junit.jupiter.api.Test;

class EMFValidationResultTest {

	@Test
	void supportsEveryDocumentedRejectionThreshold() {
		assertThat(validResult(Diagnostic.INFO, Diagnostic.WARNING).isValid()).isTrue();
		assertThat(validResult(Diagnostic.WARNING, Diagnostic.ERROR).isValid()).isTrue();
		assertThat(validResult(Diagnostic.ERROR, Diagnostic.CANCEL).isValid()).isTrue();
		assertThat(invalidResult(Diagnostic.INFO, Diagnostic.INFO,
				EMFValidationKind.VALIDATION_FAILURE).isValid()).isFalse();
		assertThat(invalidResult(Diagnostic.CANCEL, Diagnostic.CANCEL,
				EMFValidationKind.VALIDATOR_FAILURE).isValid()).isFalse();
		assertThat(EMFValidationResult.DEFAULT_REJECTED_SEVERITY).isEqualTo(Diagnostic.ERROR);
	}

	@Test
	void rejectsNullAndUnsupportedConstructorArguments() {
		var diagnostic = diagnostic(Diagnostic.OK, "valid");

		assertThatNullPointerException()
				.isThrownBy(() -> new EMFValidationResult(null, Diagnostic.ERROR,
						EMFValidationKind.VALID));
		assertThatNullPointerException()
				.isThrownBy(() -> new EMFValidationResult(diagnostic, Diagnostic.ERROR, null));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new EMFValidationResult(diagnostic, Diagnostic.OK,
						EMFValidationKind.VALID))
				.withMessageContaining("Unsupported");
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new EMFValidationResult(diagnostic, 3,
						EMFValidationKind.VALID));
	}

	@Test
	void rejectsKindsInconsistentWithRootSeverity() {
		var valid = diagnostic(Diagnostic.WARNING, "warning");
		var invalid = diagnostic(Diagnostic.ERROR, "error");

		assertThatIllegalArgumentException()
				.isThrownBy(() -> new EMFValidationResult(invalid, Diagnostic.ERROR,
						EMFValidationKind.VALID));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new EMFValidationResult(valid, Diagnostic.ERROR,
						EMFValidationKind.VALIDATION_FAILURE));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new EMFValidationResult(valid, Diagnostic.ERROR,
						EMFValidationKind.VALIDATOR_FAILURE));
	}

	@Test
	void flattensDepthFirstAndReturnsAnImmutableView() {
		var root = diagnostic(Diagnostic.ERROR, "root");
		var first = diagnostic(Diagnostic.WARNING, "first");
		var grandchild = diagnostic(Diagnostic.ERROR, "grandchild");
		var second = diagnostic(Diagnostic.INFO, "second");
		first.add(grandchild);
		root.add(first);
		root.add(second);
		var result = new EMFValidationResult(root, Diagnostic.ERROR,
				EMFValidationKind.VALIDATION_FAILURE);
		var newDiagnostic = diagnostic(Diagnostic.OK, "new");

		var flattened = result.flattenedDiagnostics();

		assertThat(result.diagnostic()).isSameAs(root);
		assertThat(flattened).containsExactly(root, first, grandchild, second);
		assertThatThrownBy(() -> flattened.add(newDiagnostic))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void filtersRejectedDiagnosticsInDepthFirstOrderIntoAnImmutableView() {
		var root = diagnostic(Diagnostic.ERROR, "root");
		var warning = diagnostic(Diagnostic.WARNING, "warning");
		var error = diagnostic(Diagnostic.ERROR, "error");
		var information = diagnostic(Diagnostic.INFO, "information");
		var cancel = diagnostic(Diagnostic.CANCEL, "cancel");
		root.add(warning);
		error.add(information);
		root.add(error);
		root.add(cancel);
		var result = new EMFValidationResult(root, Diagnostic.ERROR,
				EMFValidationKind.VALIDATION_FAILURE);

		var rejected = result.rejectedDiagnostics();

		assertThat(rejected).containsExactly(root, error, cancel);
		assertThatThrownBy(rejected::clear).isInstanceOf(UnsupportedOperationException.class);
	}

	private static EMFValidationResult validResult(final int severity, final int threshold) {
		return new EMFValidationResult(diagnostic(severity, "valid"), threshold,
				EMFValidationKind.VALID);
	}

	private static EMFValidationResult invalidResult(final int severity, final int threshold,
			final EMFValidationKind kind) {
		return new EMFValidationResult(diagnostic(severity, "invalid"), threshold, kind);
	}

	static BasicDiagnostic diagnostic(final int severity, final String message) {
		return new BasicDiagnostic(severity, "test", 0, message, new Object[0]);
	}
}
