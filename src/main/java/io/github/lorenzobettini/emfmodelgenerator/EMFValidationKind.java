package io.github.lorenzobettini.emfmodelgenerator;

/**
 * Describes the outcome of model validation.
 */
public enum EMFValidationKind {
	/** Validation completed without a diagnostic at the rejection threshold. */
	VALID,
	/** Validation completed and reported a diagnostic at the rejection threshold. */
	VALIDATION_FAILURE,
	/** The validator failed to evaluate the model normally. */
	VALIDATOR_FAILURE
}
