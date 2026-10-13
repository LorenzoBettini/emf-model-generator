package io.github.lorenzobettini.emfmodelgenerator;

import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;

/**
 * Strategy interface for selecting EMF EObject candidates of a specified type.
 * @param <E> the type of ENamedElement supplied as a selection request
 * @param <V> the type of EObject to be selected
 *
 * @author Lorenzo Bettini
 */
public interface EMFCandidateSelectorStrategy<E extends ENamedElement, V extends EObject> {

	/**
	 * Get the next assignable candidate for the specified type.
	 * 
	 * @param context the EObject whose resource set will be scanned for candidates
	 * @param request the selection request
	 * @return the next candidate, or null if no candidates are available
	 */
	V getNextCandidate(EObject context, E request);

	/**
	 * Check if there are any candidates available for the specified type.
	 * 
	 * @param context the EObject providing the resource set context
	 * @param request the selection request
	 * @return true if there are candidates available, false otherwise
	 */
	boolean hasCandidates(EObject context, E request);

	/**
	 * Reset the internal state of the candidate selector.
	 * The default implementation does nothing.
	 */
	default void reset() {
		// Default implementation does nothing
	}

}
