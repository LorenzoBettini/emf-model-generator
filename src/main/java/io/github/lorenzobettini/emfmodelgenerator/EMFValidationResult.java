package io.github.lorenzobettini.emfmodelgenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.emf.common.util.Diagnostic;

/**
 * The outcome of model validation, retaining the complete diagnostic tree.
 *
 * <p>The result components are fixed at construction time. The original
 * {@link Diagnostic} instance is retained and may itself be mutable.</p>
 *
 * @param diagnostic the root diagnostic; the original instance is retained
 * @param rejectedSeverity the minimum severity that makes the result invalid
 * @param kind the validation outcome
 *
 * @author Lorenzo Bettini
 */
public record EMFValidationResult(
		Diagnostic diagnostic,
		int rejectedSeverity,
		EMFValidationKind kind) {

	/** The default minimum rejected severity. */
	public static final int DEFAULT_REJECTED_SEVERITY = Diagnostic.ERROR;

	/**
	 * Creates a validation result and verifies that its outcome is consistent with the
	 * root diagnostic severity.
	 */
	public EMFValidationResult {
		Objects.requireNonNull(diagnostic, "diagnostic");
		Objects.requireNonNull(kind, "kind");
		if (!isSupportedThreshold(rejectedSeverity)) {
			throw new IllegalArgumentException("Unsupported rejected severity: " + rejectedSeverity);
		}
		var rejected = diagnostic.getSeverity() >= rejectedSeverity;
		if ((kind == EMFValidationKind.VALID) == rejected) {
			throw new IllegalArgumentException(
					"Validation kind is inconsistent with the diagnostic severity");
		}
	}

	/**
	 * Returns whether validation completed without a rejected diagnostic.
	 *
	 * @return {@code true} exactly when the kind is {@link EMFValidationKind#VALID}
	 */
	public boolean isValid() {
		return kind == EMFValidationKind.VALID;
	}

	/**
	 * Returns an immutable depth-first view of the complete diagnostic tree.
	 *
	 * @return the root diagnostic followed recursively by each child's subtree
	 */
	public List<Diagnostic> flattenedDiagnostics() {
		var flattened = new ArrayList<Diagnostic>();
		addDepthFirst(diagnostic, flattened);
		return List.copyOf(flattened);
	}

	/**
	 * Returns an immutable depth-first view of diagnostics at or above the rejection threshold.
	 *
	 * @return the rejected diagnostics, including the root when applicable
	 */
	public List<Diagnostic> rejectedDiagnostics() {
		return flattenedDiagnostics().stream()
				.filter(element -> element.getSeverity() >= rejectedSeverity)
				.toList();
	}

	private static boolean isSupportedThreshold(final int severity) {
		return severity == Diagnostic.INFO
				|| severity == Diagnostic.WARNING
				|| severity == Diagnostic.ERROR
				|| severity == Diagnostic.CANCEL;
	}

	private static void addDepthFirst(final Diagnostic current, final List<Diagnostic> target) {
		target.add(current);
		current.getChildren().forEach(child -> addDepthFirst(child, target));
	}
}
