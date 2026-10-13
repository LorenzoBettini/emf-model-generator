package io.github.lorenzobettini.emfmodelgenerator;

import java.util.List;
import java.util.function.Function;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

/**
 * Default conservative round-robin selector for cross-references. Requests use the actual
 * {@link EReference}; references with the same target {@link EClass} share a cache and cursor.
 * Rejected candidates remain visible when the model changes. The inherited
 * {@code getNextCandidate(owner, reference)} returns a raw type-compatible candidate,
 * which need not be a valid assignment; the setter uses {@link #selectCandidate} instead.
 * Round-robin means an ordered wrapping traversal; owner-specific skips and exhausted
 * opposites can make the resulting assignments uneven. An unsuccessful traversal is finite.
 * {@snippet :
 * EMFModelGenerator generator = new EMFModelGenerator();
 * generator.getInstancePopulator().setCrossReferenceCandidateSelectorStrategy(
 *     new EMFRoundRobinEObjectCandidateSelector());
 * }
 *
 * @author Lorenzo Bettini
 */
public class EMFRoundRobinEObjectCandidateSelector
		extends EMFAbstractCachedRoundRobinCandidateSelector<EClass, EReference, EObject>
		implements EMFCrossReferenceCandidateSelectorStrategy {

	@Override
	protected EClass keyFor(final EReference request) {
		return request.getEReferenceType();
	}

	@Override
	protected List<EObject> compute(final EObject context, final EClass key) {
		return EMFUtils.findAllAssignableInstances(context, key);
	}

	/**
	 * Find the next active index in the original candidate list, starting at {@code from}
	 * and wrapping if necessary. The default keeps every index active; subclasses may
	 * return {@code -1} when no active index remains for this reference.
	 *
	 * @param reference the actual cross-reference being populated
	 * @param from the first index to consider, between zero and {@code size - 1}
	 * @param size the positive size of the original candidate list
	 * @return the next active index, or {@code -1} if none remains
	 */
	protected int nextActiveIndex(final EReference reference, final int from, final int size) {
		return from % size;
	}

	/**
	 * React to one candidate rejected by the caller's evaluator. The default leaves the
	 * index active so a later request can check the model again. A subclass may retire
	 * an index for this reference after {@link CandidateDecision#OPPOSITE_CAPACITY_EXHAUSTED}.
	 *
	 * @param reference the actual cross-reference being populated
	 * @param index the rejected candidate's index in the original list
	 * @param size the positive size of the original candidate list
	 * @param decision the evaluator's reason for rejecting the candidate
	 */
	protected void candidateRejected(final EReference reference, final int index,
			final int size, final CandidateDecision decision) {
		// Conservative selection checks live model state on the next request.
	}

	@Override
	public EObject selectCandidate(final EObject owner, final EReference reference,
			final Function<EObject, CandidateDecision> evaluator) {
		final EClass key = keyFor(reference);
		final List<EObject> values = getOrCompute(owner, key);
		final int size = values.size();
		if (size == 0) {
			return null;
		}
		final int start = cursorIndexFor(key);
		int from = start;
		int lastDistance = -1;
		int index = nextActiveIndex(reference, from, size);
		while (index >= 0) {
			// Distance from the starting cursor increases through one logical cycle,
			// even when the reference-specific active pool shrinks during this scan.
			final int distance = (index - start + size) % size;
			if (distance <= lastDistance) {
				break;
			}
			lastDistance = distance;
			final EObject candidate = values.get(index);
			final CandidateDecision decision = evaluator.apply(candidate);
			if (decision == CandidateDecision.ACCEPT) {
				setCursorIndexFor(key, (index + 1) % size);
				return candidate;
			}
			candidateRejected(reference, index, size, decision);
			from = (index + 1) % size;
			index = nextActiveIndex(reference, from, size);
		}
		// A failed scan advances the shared type cursor by one position for the next request.
		setCursorIndexFor(key, (start + 1) % size);
		return null;
	}
}
