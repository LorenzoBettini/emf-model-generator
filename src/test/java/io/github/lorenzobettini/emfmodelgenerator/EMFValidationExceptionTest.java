package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFValidationResultTest.diagnostic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.eclipse.emf.common.util.Diagnostic;
import org.junit.jupiter.api.Test;

class EMFValidationExceptionTest {

	@Test
	void retainsResultAndUsesFirstRejectedDescendantMessage() {
		var root = diagnostic(Diagnostic.ERROR, "aggregate");
		root.add(diagnostic(Diagnostic.WARNING, "ignored warning"));
		root.add(diagnostic(Diagnostic.ERROR, "specific failure"));
		root.add(diagnostic(Diagnostic.ERROR, "later failure"));
		var result = new EMFValidationResult(root, Diagnostic.ERROR,
				EMFValidationKind.VALIDATION_FAILURE);

		var exception = new EMFValidationException(result);

		assertThat(exception.getResult()).isSameAs(result);
		assertThat(exception).hasMessage("specific failure");
	}

	@Test
	void fallsBackToRootMessageWithoutRejectedDescendant() {
		var result = new EMFValidationResult(diagnostic(Diagnostic.ERROR, "root failure"),
				Diagnostic.ERROR, EMFValidationKind.VALIDATOR_FAILURE);

		assertThat(new EMFValidationException(result)).hasMessage("root failure");
	}

	@Test
	void rejectsNullAndValidResults() {
		assertThatNullPointerException().isThrownBy(() -> new EMFValidationException(null));
		var valid = new EMFValidationResult(diagnostic(Diagnostic.OK, "valid"),
				Diagnostic.ERROR, EMFValidationKind.VALID);
		assertThatIllegalArgumentException().isThrownBy(() -> new EMFValidationException(valid));
	}
}
