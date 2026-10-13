package io.github.lorenzobettini.emfmodelgenerator;

import java.util.List;
import java.util.function.Function;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

import io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceCandidateSelectorStrategy.CandidateDecision;

/**
 * Default conservative round-robin selector for cross-references. Requests use the actual
 * {@link EReference}; references with the same target {@link EClass} share a cache and cursor.
 * Rejected candidates remain visible when the model changes. The inherited
 * {@link #getNextCandidate(EObject, EReference)} returns a raw type-compatible candidate,
 * which need not be a valid assignment; the setter uses {@link #selectCandidate} instead.
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

	/** Find the next active stable index at or after {@code from}, wrapping if needed. */
	protected int nextActiveIndex(final EReference reference, final int from, final int size) {
		return from % size;
	}

	/** React to one rejected candidate. The default never retires candidates. */
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
		while (true) {
			final int index = nextActiveIndex(reference, from, size);
			if (index < 0) {
				break;
			}
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
		}
		// The historical failed cycle retrieved its first candidate once more.
		setCursorIndexFor(key, (start + 1) % size);
		return null;
	}
}
