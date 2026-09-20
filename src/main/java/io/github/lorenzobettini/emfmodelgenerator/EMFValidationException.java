package io.github.lorenzobettini.emfmodelgenerator;

import java.io.Serial;
import java.util.Objects;

import org.eclipse.emf.common.util.Diagnostic;

/**
 * Indicates that model validation produced a non-valid result.
 */
public final class EMFValidationException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	private final EMFValidationResult result;

	/**
	 * Creates an exception for a non-valid validation result.
	 *
	 * @param result the complete validation result
	 * @throws NullPointerException if {@code result} is {@code null}
	 * @throws IllegalArgumentException if {@code result} is valid
	 */
	public EMFValidationException(final EMFValidationResult result) {
		super(messageFor(requireInvalid(result)));
		this.result = result;
	}

	/**
	 * Returns the complete validation result.
	 *
	 * @return the result that caused this exception
	 */
	public EMFValidationResult getResult() {
		return result;
	}

	private static EMFValidationResult requireInvalid(final EMFValidationResult result) {
		Objects.requireNonNull(result, "result");
		if (result.isValid()) {
			throw new IllegalArgumentException("A valid result cannot cause a validation exception");
		}
		return result;
	}

	private static String messageFor(final EMFValidationResult result) {
		var root = result.diagnostic();
		return result.rejectedDiagnostics().stream()
				.filter(diagnostic -> diagnostic != root)
				.findFirst()
				.orElse(root)
				.getMessage();
	}
}
