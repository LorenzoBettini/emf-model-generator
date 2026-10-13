package io.github.lorenzobettini.emfmodelgenerator;

import java.util.function.BiPredicate;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

/**
 * Selects an existing target for an actual cross-reference. Implementations own
 * candidate traversal and exhaustion. The self policy is called only when a
 * candidate is the owner; it must be side-effect free. The selector checks live
 * opposite capacity separately, so a rejected self candidate is never retired.
 *
 * <p>The generic {@link EMFCandidateSelectorStrategy} remains suitable for
 * non-reference uses. Existing generic EObject selectors can still be installed
 * through {@link EMFInstancePopulator#setCrossReferenceCandidateSelectorStrategy(
 * EMFCandidateSelectorStrategy)}; the setter adapts them at configuration time.
 * A custom reference-aware implementation must also implement
 * {@code EMFCandidateSelectorStrategy<EClass, EObject>} to use that entry point.</p>
 *
 * @author Lorenzo Bettini
 */
public interface EMFCrossReferenceCandidateSelector {
	/**
	 * Select one target without assigning it to the owner.
	 *
	 * @param owner the object whose reference is being populated
	 * @param reference the actual cross-reference, including its opposite
	 * @param allowSelf the configured policy, called only for a self candidate
	 * @return an eligible target, or {@code null} after exhaustion
	 */
	EObject selectCandidate(EObject owner, EReference reference,
			BiPredicate<EObject, EReference> allowSelf);

	/** Clear cached candidate and cursor state before another selection epoch. */
	void reset();
}
