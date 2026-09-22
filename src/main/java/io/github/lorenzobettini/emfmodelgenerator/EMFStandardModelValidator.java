package io.github.lorenzobettini.emfmodelgenerator;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.Diagnostician;

final class EMFStandardModelValidator implements EMFModelValidator {

	private static final String DIAGNOSTIC_SOURCE = EMFStandardModelValidator.class.getName();
	private static final String AGGREGATE_MESSAGE = "Standard EMF validation";

	private final Function<EObject, Diagnostic> diagnosticFunction;

	EMFStandardModelValidator() {
		this(Diagnostician.INSTANCE::validate);
	}

	EMFStandardModelValidator(final Function<EObject, Diagnostic> diagnosticFunction) {
		this.diagnosticFunction = diagnosticFunction;
	}

	@Override
	public EMFValidationResult validate(final EObject root) {
		Objects.requireNonNull(root, "root");
		try {
			var diagnostic = diagnosticFunction.apply(root);
			return result(diagnostic, EMFValidationKind.VALIDATION_FAILURE);
		} catch (RuntimeException exception) {
			return validatorFailure(exception);
		}
	}

	@Override
	public EMFValidationResult validateAll(final Collection<? extends EObject> roots) {
		Objects.requireNonNull(roots, "roots");
		var aggregate = new BasicDiagnostic(DIAGNOSTIC_SOURCE, 0, AGGREGATE_MESSAGE,
				new Object[0]);
		var validatorFailed = false;
		for (var root : roots) {
			var result = validate(root);
			aggregate.add(result.diagnostic());
			validatorFailed |= result.kind() == EMFValidationKind.VALIDATOR_FAILURE;
		}
		var invalidKind = validatorFailed
				? EMFValidationKind.VALIDATOR_FAILURE
				: EMFValidationKind.VALIDATION_FAILURE;
		return result(aggregate, invalidKind);
	}

	private static EMFValidationResult result(final Diagnostic diagnostic,
			final EMFValidationKind invalidKind) {
		var kind = diagnostic.getSeverity() >= EMFValidationResult.DEFAULT_REJECTED_SEVERITY
				? invalidKind
				: EMFValidationKind.VALID;
		return new EMFValidationResult(diagnostic,
				EMFValidationResult.DEFAULT_REJECTED_SEVERITY, kind);
	}

	private static EMFValidationResult validatorFailure(final RuntimeException exception) {
		var diagnostic = new BasicDiagnostic(Diagnostic.ERROR, DIAGNOSTIC_SOURCE, 0,
				"Standard EMF validation failed: " + exception.getMessage(),
				new Object[] { exception });
		return result(diagnostic, EMFValidationKind.VALIDATOR_FAILURE);
	}
}
