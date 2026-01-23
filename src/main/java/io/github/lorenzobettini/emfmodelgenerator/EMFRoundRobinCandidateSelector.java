package io.github.lorenzobettini.emfmodelgenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;

/**
 * Abstract round-robin selector for EMF EObjects of a specified type.
 * 
 * @param <K> the type of ENamedElement used as key (e.g., EClassifier, EAttribute)
 * @param <V> the type of EObject to be selected
 */
public abstract class EMFRoundRobinCandidateSelector<K extends ENamedElement, V extends EObject> implements EMFCandidateSelectorStrategy<K, V> {

	private final Map<K, Integer> nextIndexMap = new HashMap<>();

	/**
	 * Get or compute the list of possible candidates for the specified key within
	 * the resource set of the provided context EObject.
	 * 
	 * @param context
	 * @param type
	 * @return
	 */
	protected abstract List<V> getOrCompute(final EObject context, final K type);

	/**
	 * Get the next assignable candidate for the specified type using round-robin
	 * selection.
	 * 
	 * <p>
	 * The method maintains state to ensure that subsequent calls continue from
	 * where the previous call left off, providing better distribution of candidates
	 * across multiple invocations.
	 * </p>
	 * 
	 * The method relies on the {@link #getOrCompute(EObject, ENamedElement)} method
	 * to retrieve the list of all possible candidates for the given type within the
	 * resource set of the provided context EObject.
	 * 
	 * @param context the EObject whose resource set will be scanned for candidates
	 * @param type    the type of candidate to select
	 * @return the next candidate in round-robin order, or null if no candidates are
	 *         available
	 */
	@Override
	public V getNextCandidate(final EObject context, final K type) {
		final List<V> allCandidates = getOrCompute(context, type);

		// If no candidates available, return null
		if (allCandidates.isEmpty()) {
			return null;
		}

		// Get the current index for this type, defaulting to 0
		final int currentIndex = nextIndexMap.getOrDefault(type, 0);

		// Get the next candidate
		final V candidate = allCandidates.get(currentIndex);

		// Update the next index for this type (wrap around)
		final int nextIndex = (currentIndex + 1) % allCandidates.size();
		nextIndexMap.put(type, nextIndex);

		return candidate;
	}

	/**
	 * Check if there are any candidates available for the specified type.
	 * 
	 * It relies on the {@link #getOrCompute(EObject, ENamedElement)} method to
	 * retrieve the list of all possible candidates for the given type within the
	 * resource set of the provided context EObject.
	 * 
	 * @param context
	 * @param type
	 * @return
	 */
	@Override
	public boolean hasCandidates(final EObject context, final K type) {
		final List<V> allCandidates = getOrCompute(context, type);
		return !allCandidates.isEmpty();
	}

	/**
	 * Reset the round-robin state.
	 */
	@Override
	public void reset() {
		nextIndexMap.clear();
	}
}
