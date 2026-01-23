package io.github.lorenzobettini.emfmodelgenerator;

import java.util.List;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

/**
 * Provides stateful round-robin selection of instantiable EClass candidates.
 * 
 * <p>This class maintains state across multiple calls to ensure that when selecting
 * candidates from a pool, we start from where we left off rather than always selecting
 * the same candidates. This provides better distribution of selections across
 * available EClasses.</p>
 * 
 * <p>Example:
 * <pre>{@code
 * EMFRoundRobinEClassCandidateSelector selector = new EMFRoundRobinEClassCandidateSelector();
 * 
 * // First call for BaseClass returns SubClass1
 * EClass candidate1 = selector.getNextCandidate(context, baseClass);
 * 
 * // Second call for BaseClass returns SubClass2 (continuing from where we left off)
 * EClass candidate2 = selector.getNextCandidate(context, baseClass);
 * 
 * // If no candidates available, returns null
 * EClass noCandidate = selector.getNextCandidate(context, unknownClass); // returns null
 * }</pre>
 */
public class EMFRoundRobinEClassCandidateSelector extends EMFAbstractCachedRoundRobinCandidateSelector<EClass, EClass> {

	@Override
	protected List<EClass> compute(final EObject context, final EClass key) {
		// Context is not needed for finding instantiable subclasses
		return EMFUtils.findAllInstantiableSubclasses(key);
	}

}
