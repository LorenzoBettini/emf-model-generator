package io.github.lorenzobettini.emfmodelgenerator;

import java.util.function.Function;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

/**
 * Selects a valid existing target for an actual cross-reference in one finite traversal.
 * The caller classifies each examined candidate; the selector owns traversal and cursor state.
 *
 * @author Lorenzo Bettini
 */
public interface EMFCrossReferenceCandidateSelectorStrategy {
	/**
	 * Classification of one candidate. Only opposite-capacity exhaustion may be retired
	 * by a selector whose caller guarantees monotonic capacity until reset.
	 *
	 * @author Lorenzo Bettini
	 */
	enum CandidateDecision {
		/** The candidate can be assigned. */
		ACCEPT,
		/** The candidate is unsuitable for this owner but may suit another owner. */
		SKIP,
		/** The candidate's opposite has no remaining capacity for this reference. */
		OPPOSITE_CAPACITY_EXHAUSTED
	}

	/**
	 * @param owner the owner being populated
	 * @param reference the actual cross-reference
	 * @param evaluator the caller's classification, invoked once per examined candidate
	 * @return an accepted target, or {@code null} after a bounded traversal
	 */
	EObject selectCandidate(EObject owner, EReference reference,
			Function<EObject, CandidateDecision> evaluator);

	/** Clear candidate, cursor and retirement state for a new selection epoch. */
	void reset();
}
