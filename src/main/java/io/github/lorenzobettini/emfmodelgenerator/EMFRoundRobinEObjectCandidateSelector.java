package io.github.lorenzobettini.emfmodelgenerator;

import java.util.List;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

/**
 * Provides stateful round-robin selection of assignable candidates for cross-references.
 * 
 * <p>This class maintains state across multiple calls to ensure that when selecting
 * candidates from a pool, we start from where we left off rather than always selecting
 * the same candidates. This provides better distribution of cross-references across
 * available instances.</p>
 * 
 * <p>Example:
 * <pre>{@code
 * EMFRoundRobinEObjectCandidateSelector selector = new EMFRoundRobinEObjectCandidateSelector();
 * 
 * // First call for PersonClass returns person1
 * EObject candidate1 = selector.getNextCandidate(context, personClass);
 * 
 * // Second call for PersonClass returns person2 (continuing from where we left off)
 * EObject candidate2 = selector.getNextCandidate(context, personClass);
 * 
 * // If no candidates available, returns null
 * EObject noCandidate = selector.getNextCandidate(context, unknownClass); // returns null
 * }</pre>
 * </p>
 */
public class EMFRoundRobinEObjectCandidateSelector extends EMFAbstractCachedRoundRobinCandidateSelector<EClass, EObject> {

	@Override
	protected List<EObject> compute(final EObject context, final EClass key) {
		return EMFUtils.findAllAssignableInstances(context, key);
	}
}
