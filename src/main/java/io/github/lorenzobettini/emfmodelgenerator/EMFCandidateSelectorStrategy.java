package io.github.lorenzobettini.emfmodelgenerator;

import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;

/**
 * Strategy for selecting EMF objects identified by a request element.
 * Implementations may cache candidates and maintain selection state between calls.
 * @param <E> the type of ENamedElement supplied as a selection request
 * @param <V> the type of EObject to be selected
 *
 * @author Lorenzo Bettini
 */
public interface EMFCandidateSelectorStrategy<E extends ENamedElement, V extends EObject> {

	/**
	 * Get the next candidate for the request. The meaning of the request and the
	 * candidate search scope depend on the implementation.
	 * 
	 * @param context the owning EObject, if the selector needs model context
	 * @param request the selection request
	 * @return the next candidate, or null if no candidates are available
	 */
	V getNextCandidate(EObject context, E request);

	/**
	 * Check whether the selector has candidates for the request.
	 * 
	 * @param context the owning EObject, if the selector needs model context
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
