package io.github.lorenzobettini.emfmodelgenerator.performance;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

import io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceSetter;
import io.github.lorenzobettini.emfmodelgenerator.EMFRoundRobinEObjectCandidateSelector;
import io.github.lorenzobettini.emfmodelgenerator.EMFUtils;

/** Diagnostic wrapper that keeps the production setter's assignment loops intact. */
final class ReferenceAwareBaselineProbe extends EMFCrossReferenceSetter {
	private final Selector selector = new Selector();
	private EObject previous;
	private EObject currentOwner;
	private EReference currentReference;
	private long retrievals;
	private long attempts;
	private long oppositeRejections;

	ReferenceAwareBaselineProbe() {
		setCandidateSelectorStrategy(selector);
	}

	@Override
	public EObject selectValue(final EObject owner, final EReference reference) {
		previous = null;
		currentOwner = owner;
		currentReference = reference;
		retrievals = 0;
		try {
			final EObject result = super.selectValue(owner, reference);
			if (retrievals > 0 && previous != null) {
				attempts += retrievals - (result == null ? 1 : 0);
			}
			return result;
		} finally {
			previous = null;
			currentOwner = null;
			currentReference = null;
		}
	}

	long attempts() {
		return attempts;
	}

	long oppositeRejections() {
		return oppositeRejections;
	}

	private final class Selector extends EMFRoundRobinEObjectCandidateSelector {
		@Override
		public EObject getNextCandidate(final EObject context, final EClass type) {
			if (previous != null && previous != currentOwner &&
					!EMFUtils.canSetInThePresenceOfOppositeReference(currentReference, previous)) {
				oppositeRejections++;
			}
			previous = super.getNextCandidate(context, type);
			retrievals++;
			return previous;
		}
	}
}
