package io.github.lorenzobettini.emfmodelgenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;

/**
 * Abstract base class for cached round-robin candidate selectors.
 * 
 * <p>This class provides caching functionality for round-robin candidate selectors,
 * storing the computed candidate lists in an internal map to avoid redundant
 * computations. Subclasses only need to implement the {@link #compute(EObject, ENamedElement)}
 * method to define how candidates are computed for a given key.</p>
 * 
 * <p>The cache is not exposed to subclasses, maintaining encapsulation and ensuring
 * that all cache operations are managed by this base class.</p>
 * 
 * @param <K> the type of ENamedElement used as key (e.g., EClass, EAttribute)
 * @param <V> the type of EObject to be selected
 */
public abstract class EMFAbstractCachedRoundRobinCandidateSelector<K extends ENamedElement, V extends EObject>
		extends EMFRoundRobinCandidateSelector<K, V> {

	/**
	 * Internal cache storing computed candidate lists for each key.
	 * This cache is not exposed to subclasses.
	 */
	private final Map<K, List<V>> cache = new HashMap<>();

	/**
	 * Compute the list of candidates for the specified key.
	 * This method is called by {@link #getOrCompute(EObject, ENamedElement)}
	 * when the key is not present in the cache.
	 * 
	 * @param context the EObject providing context for the computation
	 * @param key the key for which to compute candidates
	 * @return the list of candidates for the given key
	 */
	protected abstract List<V> compute(EObject context, K key);

	/**
	 * Get or compute the list of candidates for the specified key.
	 * 
	 * <p>If candidates for the given key are already cached, they are returned
	 * immediately. Otherwise, the {@link #compute(EObject, ENamedElement)} method is
	 * called to compute the candidates, which are then cached and returned.</p>
	 * 
	 * <p>Note: The context is captured by the lambda passed to computeIfAbsent,
	 * but the caching is based solely on the key. This means that the same cached
	 * result is used regardless of which context is provided in subsequent calls
	 * with the same key.</p>
	 * 
	 * @param context the EObject providing context
	 * @param key     the key for which to get candidates
	 * @return the list of candidates for the given key
	 */
	@Override
	protected List<V> getOrCompute(final EObject context, final K key) {
		return cache.computeIfAbsent(key, k -> compute(context, k));
	}

	/**
	 * Reset all caches and state.
	 * 
	 * <p>Clears both the candidates cache and the round-robin state. This is useful
	 * when the model changes, ensuring that candidates are recomputed on the next call
	 * to {@link #getNextCandidate(EObject, ENamedElement)}.</p>
	 */
	@Override
	public void reset() {
		super.reset();
		cache.clear();
	}
}
