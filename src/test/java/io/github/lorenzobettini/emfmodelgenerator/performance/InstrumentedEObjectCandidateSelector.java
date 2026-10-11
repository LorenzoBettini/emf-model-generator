package io.github.lorenzobettini.emfmodelgenerator.performance;

import java.util.List;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

import io.github.lorenzobettini.emfmodelgenerator.EMFRoundRobinEObjectCandidateSelector;

/** Counts actual candidate-list computations without changing the selector's cache or order. */
class InstrumentedEObjectCandidateSelector extends EMFRoundRobinEObjectCandidateSelector {

	private long computeCalls;
	private long candidateEntries;
	private long computeNanos;

	@Override
	protected List<EObject> compute(final EObject context, final EClass key) {
		final long start = System.nanoTime();
		final List<EObject> candidates;
		try {
			candidates = super.compute(context, key);
		} finally {
			computeNanos += System.nanoTime() - start;
		}
		computeCalls++;
		candidateEntries += candidates.size();
		return candidates;
	}

	Snapshot snapshot() {
		return new Snapshot(computeCalls, candidateEntries, computeNanos);
	}

	record Snapshot(long computeCalls, long candidateEntries, long computeNanos) {
	}
}
