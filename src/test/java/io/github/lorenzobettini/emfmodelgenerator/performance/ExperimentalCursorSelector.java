package io.github.lorenzobettini.emfmodelgenerator.performance;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

import io.github.lorenzobettini.emfmodelgenerator.EMFRoundRobinEObjectCandidateSelector;

/** Experimental cursor bookkeeping only. Candidate discovery and validation stay unchanged. */
final class ExperimentalCursorSelector extends EMFRoundRobinEObjectCandidateSelector {

	private final Map<EClass, Cursor> cursors = new HashMap<>();

	private static final class Cursor {
		int index;
	}

	@Override
	public EObject getNextCandidate(final EObject context, final EClass type) {
		final var candidates = getOrCompute(context, type);
		if (candidates.isEmpty()) {
			return null;
		}
		final var cursor = cursors.computeIfAbsent(type, ignored -> new Cursor());
		final var candidate = candidates.get(cursor.index);
		cursor.index++;
		if (cursor.index == candidates.size()) {
			cursor.index = 0;
		}
		return candidate;
	}

	@Override
	public void reset() {
		super.reset();
		cursors.clear();
	}
}
