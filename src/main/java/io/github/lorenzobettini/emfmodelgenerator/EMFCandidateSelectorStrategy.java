package io.github.lorenzobettini.emfmodelgenerator;

import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;

/**
 * Strategy interface for selecting EMF EObject candidates of a specified type.
 * @param <K> the type of ENamedElement used as key (e.g., EClassifier, EAttribute)
 * @param <V> the type of EObject to be selected
 */
public interface EMFCandidateSelectorStrategy<K extends ENamedElement, V extends EObject> {

	/**
	 * Get the next assignable candidate for the specified type.
	 * 
	 * @param context the EObject whose resource set will be scanned for candidates
	 * @param type    the type of candidate to select
	 * @return the next candidate, or null if no candidates are available
	 */
	V getNextCandidate(EObject context, K type);

	/**
	 * Check if there are any candidates available for the specified type.
	 * 
	 * @param context
	 * @param type
	 * @return
	 */
	boolean hasCandidates(EObject context, K type);

	/**
	 * Reset the internal state of the candidate selector.
	 * The default implementation does nothing.
	 */
	default void reset() {
		// Default implementation does nothing
	}

}