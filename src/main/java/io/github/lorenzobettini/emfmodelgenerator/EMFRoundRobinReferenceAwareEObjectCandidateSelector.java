package io.github.lorenzobettini.emfmodelgenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;

import io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceCandidateSelectorStrategy.CandidateDecision;

/**
 * Opt-in reference-aware selection for a caller-guaranteed monotonic capacity epoch.
 * A target retired after opposite-capacity exhaustion must remain unavailable for that
 * reference until {@link #reset()}. External {@code eUnset}, reassignment, inverse updates,
 * FeatureMap edits and callbacks can break this guarantee. Reset after such changes.
 * Owner-specific self and uniqueness skips never retire targets. References targeting the
 * same EClass share the source list and cursor but keep separate retirement pools.
 *
 * @author Lorenzo Bettini
 */
public class EMFRoundRobinReferenceAwareEObjectCandidateSelector
		extends EMFRoundRobinEObjectCandidateSelector {

	private final Map<EClass, Object> scopes = new HashMap<>();
	private final Map<EReference, NavigableSet<Integer>> active = new HashMap<>();

	@Override
	protected List<EObject> getOrCompute(final EObject context, final EClass key) {
		final Object current = scope(context);
		final Object previous = scopes.putIfAbsent(key, current);
		if (previous != null && previous != current) {
			throw new IllegalStateException("Resource scope changed; reset the selector");
		}
		return super.getOrCompute(context, key);
	}

	private Object scope(final EObject context) {
		final Resource resource = context.eResource();
		if (resource == null) {
			return EcoreUtil.getRootContainer(context);
		}
		return resource.getResourceSet() != null ? resource.getResourceSet() : resource;
	}

	@Override
	protected int nextActiveIndex(final EReference reference, final int from, final int size) {
		final NavigableSet<Integer> pool = active.get(reference);
		if (pool == null) {
			return super.nextActiveIndex(reference, from, size);
		}
		if (pool.isEmpty()) {
			return -1;
		}
		final Integer next = pool.ceiling(from);
		return next != null ? next : pool.first();
	}

	@Override
	protected void candidateRejected(final EReference reference, final int index,
			final int size, final CandidateDecision decision) {
		if (decision == CandidateDecision.OPPOSITE_CAPACITY_EXHAUSTED) {
			active.computeIfAbsent(reference, ignored -> allIndices(size)).remove(index);
		}
	}

	private NavigableSet<Integer> allIndices(final int size) {
		final NavigableSet<Integer> indices = new TreeSet<>();
		for (int i = 0; i < size; i++) {
			indices.add(i);
		}
		return indices;
	}

	@Override
	public void reset() {
		super.reset();
		scopes.clear();
		active.clear();
	}
}
