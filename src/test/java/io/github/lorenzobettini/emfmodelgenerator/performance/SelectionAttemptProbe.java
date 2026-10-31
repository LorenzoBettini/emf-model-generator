package io.github.lorenzobettini.emfmodelgenerator.performance;

import java.util.Map;
import java.util.TreeMap;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

import io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceSetter;
import io.github.lorenzobettini.emfmodelgenerator.EMFUtils;

/**
 * Test-only observer around the selection algorithm and round-robin selector.
 *
 * @author Lorenzo Bettini
 */
final class SelectionAttemptProbe extends EMFCrossReferenceSetter {
	private final Selector selector = new Selector();
	private final Map<String, Counters> features = new TreeMap<>();
	private int callsInSelection;
	private long retrievalNanosInSelection;
	private boolean firstWasNull;
	private boolean selecting;
	private EObject currentOwner;
	private EReference currentReference;
	private Counters currentCounters;
	private int classifiedInSelection;

	SelectionAttemptProbe() {
		setCandidateSelectorStrategy(selector);
	}

	@Override
	public EObject selectValue(final EObject owner, final EReference reference) {
		final var counters = counters(reference);
		final long start = System.nanoTime();
		callsInSelection = 0;
		retrievalNanosInSelection = 0;
		firstWasNull = false;
		selecting = true;
		currentOwner = owner;
		currentReference = reference;
		currentCounters = counters;
		classifiedInSelection = 0;
		EObject result = null;
		try {
			result = super.selectValue(owner, reference);
			return result;
		} finally {
			selecting = false;
			final boolean exhausted = result == null && callsInSelection > 0;
			final int attempts = callsInSelection;
			counters.add(
					attempts, attempts - classifiedInSelection -
							(result != null && attempts > 0 ? 1 : 0),
					result == null, exhausted,
					firstWasNull, result != null && callsInSelection == 0,
					System.nanoTime() - start, retrievalNanosInSelection);
				currentOwner = null;
			currentReference = null;
			currentCounters = null;
		}
	}

	private Counters counters(final EReference reference) {
		final String name = reference.getEContainingClass().getEPackage().getName() + "::"
				+ reference.getEContainingClass().getName() + "." + reference.getName();
		return features.computeIfAbsent(name, ignored -> new Counters());
	}

	/* Mirrors the production loops solely to isolate membership and inverse assignment cost. */
	@Override
	protected void setSingleFeature(final EObject owner, final EReference reference) {
		final EObject value = selectValue(owner, reference);
		if (value != null) {
			final long start = System.nanoTime();
			owner.eSet(reference, value);
			final var c = counters(reference);
			c.singleAssignments++;
			c.singleAssignmentNanos += System.nanoTime() - start;
		}
	}

	@Override
	protected void setMultiFeature(final EObject owner, final EReference reference) {
		final var list = EMFUtils.getAsList(owner, reference);
		final int count = EMFUtils.getEffectiveCount(reference, getMaxCountFor(owner, reference));
		final int missingCount = count - list.size();
		final var c = counters(reference);
		for (int i = 0; i < missingCount; i++) {
			c.multiSelectionCalls++;
			final EObject value = selectValue(owner, reference);
			if (value == null) {
				return;
			}
			final long start = System.nanoTime();
			list.add(value);
			c.multiAssignments++;
			c.multiAssignmentNanos += System.nanoTime() - start;
		}
	}

	Map<String, Counters> features() {
		return features;
	}

	InstrumentedEObjectCandidateSelector.Snapshot discovery() {
		return selector.discovery();
	}

	/**
	 * @author Lorenzo Bettini
	 */
	final class Selector extends InstrumentedEObjectCandidateSelector {
		@Override
		public EObject selectCandidate(final EObject owner, final EReference reference,
				final java.util.function.Function<EObject, CandidateDecision> evaluator) {
			final long start = System.nanoTime();
			try {
				final var observed = (java.util.function.Function<EObject, CandidateDecision>) candidate -> {
					callsInSelection++;
					final CandidateDecision decision = evaluator.apply(candidate);
					if (decision != CandidateDecision.ACCEPT) {
						classifyRejected(candidate);
					}
					return decision;
				};
				final EObject result = super.selectCandidate(owner, reference, observed);
				if (callsInSelection == 0) {
					firstWasNull = true;
				}
				return result;
			} finally {
				if (selecting) {
					retrievalNanosInSelection += System.nanoTime() - start;
				}
			}
		}

		InstrumentedEObjectCandidateSelector.Snapshot discovery() {
			return snapshot();
		}
	}

	private void classifyRejected(final EObject candidate) {
		classifiedInSelection++;
		final var opposite = currentReference.getEOpposite();
		if (candidate == currentOwner) {
			if (opposite == null) {
				currentCounters.selfRejected++;
			} else {
				// The private self policy may have allowed it before the opposite rejected it.
				currentCounters.rejectedUnknown++;
			}
		} else if (opposite != null && candidate.eClass().getEAllReferences().contains(opposite)) {
			if (opposite.isMany()) {
				currentCounters.oppositeManyFull++;
			} else {
				currentCounters.oppositeSingleOccupied++;
			}
		} else {
			currentCounters.rejectedUnknown++;
		}
	}

	/**
	 * @author Lorenzo Bettini
	 */
	static final class Counters {
		long calls;
		long selected;
		long customSelected;
		long nullSelections;
		long noCandidates;
		long exhausted;
		long attempts;
		long rejectedUnknown;
		long selfRejected;
		long oppositeSingleOccupied;
		long oppositeManyFull;
		long multiSelectionCalls;
		long uniqueRetries;
		long uniqueExhaustions;
		long containsChecks;
		long containsListElements;
		long containsNanos;
		long singleAssignments;
		long singleAssignmentNanos;
		long multiAssignments;
		long multiAssignmentNanos;
		long selectionNanos;
		long retrievalNanos;
		int maximumAttempts;
		final Map<Integer, Long> histogram = new TreeMap<>();

		void add(final int count, final int rejected, final boolean wasNull,
				final boolean cycle, final boolean empty, final boolean custom,
				final long elapsed, final long retrieval) {
			calls++;
			selected += wasNull ? 0 : 1;
			customSelected += custom ? 1 : 0;
			nullSelections += wasNull ? 1 : 0;
			noCandidates += empty ? 1 : 0;
			exhausted += cycle ? 1 : 0;
			attempts += count;
			rejectedUnknown += rejected;
			selectionNanos += elapsed;
			retrievalNanos += retrieval;
			maximumAttempts = Math.max(maximumAttempts, count);
			histogram.merge(count, 1L, Long::sum);
		}

		int percentile(final double fraction) {
			final long rank = Math.max(1, (long) Math.ceil(calls * fraction));
			long cumulative = 0;
			for (final var entry : histogram.entrySet()) {
				cumulative += entry.getValue();
				if (cumulative >= rank) {
					return entry.getKey();
				}
			}
			return 0;
		}

		String histogramText() {
			final var result = new StringBuilder();
			for (final var entry : histogram.entrySet()) {
				if (!result.isEmpty()) {
					result.append(';');
				}
				result.append(entry.getKey()).append(':').append(entry.getValue());
			}
			return result.toString();
		}
	}
}
