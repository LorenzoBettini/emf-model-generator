package io.github.lorenzobettini.emfmodelgenerator.performance;

import java.util.Map;
import java.util.TreeMap;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

import io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceSetter;
import io.github.lorenzobettini.emfmodelgenerator.EMFRoundRobinEObjectCandidateSelector;

/** Test-only observer around the unchanged selection algorithm and round-robin selector. */
final class SelectionAttemptProbe extends EMFCrossReferenceSetter {
	private final Selector selector = new Selector();
	private final Map<String, Counters> features = new TreeMap<>();
	private int callsInSelection;
	private long retrievalNanosInSelection;
	private boolean firstWasNull;
	private boolean selecting;

	SelectionAttemptProbe() {
		setCandidateSelectorStrategy(selector);
	}

	@Override
	public EObject selectValue(final EObject owner, final EReference reference) {
		final String name = reference.getEContainingClass().getEPackage().getName() + "::"
				+ reference.getEContainingClass().getName() + "." + reference.getName();
		final long start = System.nanoTime();
		callsInSelection = 0;
		retrievalNanosInSelection = 0;
		firstWasNull = false;
		selecting = true;
		EObject result = null;
		try {
			result = super.selectValue(owner, reference);
			return result;
		} finally {
			selecting = false;
			final boolean exhausted = result == null && callsInSelection > 1;
			final int attempts = callsInSelection - (exhausted ? 1 : 0)
					- (firstWasNull ? 1 : 0);
			final int accepted = result != null && callsInSelection > 0 ? 1 : 0;
			features.computeIfAbsent(name, ignored -> new Counters()).add(
					attempts, attempts - accepted, result == null, exhausted,
					firstWasNull, result != null && callsInSelection == 0,
					System.nanoTime() - start, retrievalNanosInSelection);
		}
	}

	Map<String, Counters> features() {
		return features;
	}

	InstrumentedEObjectCandidateSelector.Snapshot discovery() {
		return selector.discovery();
	}

	final class Selector extends InstrumentedEObjectCandidateSelector {
		@Override
		public EObject getNextCandidate(final EObject context, final EClass type) {
			final long start = System.nanoTime();
			try {
				final EObject candidate = super.getNextCandidate(context, type);
				if (selecting) {
					callsInSelection++;
					if (callsInSelection == 1 && candidate == null) {
						firstWasNull = true;
					}
				}
				return candidate;
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

	static final class Counters {
		long calls;
		long selected;
		long customSelected;
		long nullSelections;
		long noCandidates;
		long exhausted;
		long attempts;
		long rejectedUnknown;
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
