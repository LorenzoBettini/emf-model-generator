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
 * @param <E> the type of ENamedElement supplied as a selection request
 * @param <V> the type of EObject to be selected
 *
 * @author Lorenzo Bettini
 */
public abstract class EMFRoundRobinCandidateSelector<K extends ENamedElement,
		E extends ENamedElement, V extends EObject>
		implements EMFCandidateSelectorStrategy<E, V> {

	private final Map<K, Cursor> nextIndexMap = new HashMap<>();

	/**
	 * @author Lorenzo Bettini
	 */
	private static final class Cursor {
		private int index;
	}

	/**
	 * Resolve the cache and cursor key for a request.
	 *
	 * @param request the selection request
	 * @return the key shared by requests with the same candidate pool
	 */
	protected abstract K keyFor(E request);

	/**
	 * Get or compute the list of possible candidates for the specified key within
	 * the resource set of the provided context EObject.
	 * 
	 * @param context the EObject providing the resource set context
	 * @param type    the type key to retrieve candidates for
	 * @return the list of possible candidates for the given key
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
	 * @param request the selection request
	 * @return the next candidate in round-robin order, or null if no candidates are
	 *         available
	 */
	@Override
	public V getNextCandidate(final EObject context, final E request) {
		final K key = keyFor(request);
		final List<V> allCandidates = getOrCompute(context, key);

		// If no candidates available, return null
		if (allCandidates.isEmpty()) {
			return null;
		}

		final Cursor cursor = nextIndexMap.computeIfAbsent(key, ignored -> new Cursor());
		final V candidate = allCandidates.get(cursor.index);
		cursor.index++;
		if (cursor.index == allCandidates.size()) {
			cursor.index = 0;
		}

		return candidate;
	}

	/**
	 * Check if there are any candidates available for the specified type.
	 * 
	 * It relies on the {@link #getOrCompute(EObject, ENamedElement)} method to
	 * retrieve the list of all possible candidates for the given type within the
	 * resource set of the provided context EObject.
	 * 
	 * @param context the EObject providing the resource set context
	 * @param request the selection request
	 * @return true if there are candidates available, false otherwise
	 */
	@Override
	public boolean hasCandidates(final EObject context, final E request) {
		final List<V> allCandidates = getOrCompute(context, keyFor(request));
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
