package io.github.lorenzobettini.emfmodelgenerator;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

/**
 * Responsible for setting cross reference (non-containment) values on EMF EObjects.
 * This class handles both single-valued and multi-valued cross references.
 */
public class EMFCrossReferenceSetter extends EMFConfigurableFeatureSetter<EReference, EReference, EObject> {

	private static final int DEFAULT_MULTI_VALUED_COUNT = 2;
	private EMFCandidateSelectorStrategy<EClass, EObject> candidateSelectorStrategy = new EMFRoundRobinEObjectCandidateSelector();
	private CyclePolicy cyclePolicy = (owner, reference) -> false;

	/**
	 * Function interface for cross reference operations.
	 */
	@FunctionalInterface
	public static interface EMFCrossReferenceValueFunction extends FeatureFunction<EObject> {
	}

	/**
	 * Functional interface for determining whether cycles (self-references) are allowed.
	 */
	@FunctionalInterface
	public static interface CyclePolicy {
		/**
		 * Determines whether an EObject is allowed to reference itself through the given reference.
		 * 
		 * @param owner the EObject that would reference itself
		 * @param reference the reference through which the cycle would be created
		 * @return true if the owner is allowed to reference itself, false otherwise
		 */
		boolean allowCycleFor(EObject owner, EReference reference);
	}

	public EMFCrossReferenceSetter() {
		super(DEFAULT_MULTI_VALUED_COUNT);
	}

	/**
	 * Set the candidate selector strategy for selecting existing EObject instances.
	 * 
	 * @param strategy the candidate selector strategy to use
	 */
	public void setCandidateSelectorStrategy(EMFCandidateSelectorStrategy<EClass, EObject> strategy) {
		this.candidateSelectorStrategy = strategy;
	}

	/**
	 * Set the cycle policy for determining whether self-references are allowed.
	 * 
	 * @param cyclePolicy the cycle policy to use
	 */
	public void setAllowCyclePolicy(CyclePolicy cyclePolicy) {
		this.cyclePolicy = cyclePolicy;
	}

	/**
	 * Reset the round-robin candidate selection state.
	 * 
	 * <p>This clears the internal state of the candidate selector, causing it to
	 * restart from the beginning on the next selection. This is useful when new
	 * elements are added to the resource set and you want them to be considered
	 * in subsequent selections.</p>
	 */
	public void reset() {
		candidateSelectorStrategy.reset();
	}

	/**
	 * Determines whether an EObject is allowed to reference itself through the given reference.
	 * Uses the configured cycle policy.
	 * 
	 * @param owner the EObject that would reference itself
	 * @param reference the reference through which the cycle would be created
	 * @return true if the owner is allowed to reference itself, false otherwise
	 */
	protected boolean allowCycleFor(EObject owner, EReference reference) {
		return cyclePolicy.allowCycleFor(owner, reference);
	}

	/**
	 * Determine whether the given feature should be set on the given owner EObject.
	 * By default, the feature should be set if it is not already set.
	 * For multi-valued references with opposites, the feature is considered not set
	 * even if it has values, allowing to add more values.
	 * 
	 * @param owner  the owner EObject
	 * @param feature the feature to set
	 * @return true if the feature should be set, false otherwise
	 */
	@Override
	protected boolean shouldSetFeature(EObject owner, EReference feature) {
		return super.shouldSetFeature(owner, feature) ||
				(feature.isMany() && feature.getEOpposite() != null);
	}

	/**
	 * Set the cross reference on the given owner EObject.
	 * 
	 * If the reference is already set, then it will be skipped: if it is an opposite reference,
	 * the existing referenced EObject will not be modified.
	 * 
	 * For setting the cross reference, the resource set of the owner EObject is scanned for all existing EObjects that can be assigned to the reference.
	 *
	 * @param owner     the EObject owning the reference (assumed to be in a resource, which is assumed to be in a resource set)
	 * @param reference the assumed cross EReference to set (its type is assumed to be in a resource, which is assumed to be in a resource set)
	 */
	public void setCrossReference(EObject owner, EReference reference) {
		setFeature(owner, reference);
	}

	@Override
	protected void setSingleFeature(EObject owner, EReference reference) {
		EClass eReferenceType = reference.getEReferenceType();
		// For single-valued references, use an existing assignable instance
		EObject referencedEObject = nextAssignableExistingInstance(owner, eReferenceType, reference);
		if (referencedEObject == null) {
			return;
		}
		owner.eSet(reference, referencedEObject);
	}

	@Override
	protected void setMultiFeature(EObject owner, EReference reference) {
		EClass eReferenceType = reference.getEReferenceType();
		final var list = EMFUtils.getAsList(owner, reference);

		// For multi-valued references, add multiple EObjects
		// Respect upper and lower bounds
		final int count = EMFUtils.getEffectiveCount(reference, getMaxCountFor(owner, reference));
		// if some values are already set, we only add the missing ones
		// (this could happen in the presence of opposite references)
		final int missingCount = count - list.size();

		for (int i = 0; i < missingCount; i++) {
			// Get next candidate using round-robin selection
			EObject referencedEObject = nextAssignableExistingInstance(owner, eReferenceType, reference);
			// but skip already present ones (only if unique is true), avoiding infinite loops
			if (reference.isUnique()) {
				EObject firstCandidate = referencedEObject;
				while (referencedEObject != null &&
						list.contains(referencedEObject)) {
					referencedEObject = nextAssignableExistingInstance(owner, eReferenceType, reference);
					if (referencedEObject == firstCandidate) {
						// we've looped through all candidates and found no new one
						return;
					}
				}
			}
			if (referencedEObject == null) {
				return;
			}

			list.add(referencedEObject);
		}
	}

	private EObject nextAssignableExistingInstance(EObject owner, EClass eReferenceType, EReference reference) {
		var function = getFunctionFor(reference);
		if (function != null) {
			final EObject candidate = function.apply(owner);
			if (candidate != null &&
					isCandidateValid(owner, reference, candidate)) {
				return candidate;
			}
		}

		// Try to get candidates using round-robin until we find one that passes the
		// opposite reference check and is not the owner itself (unless cycles are allowed)
		// Since the selector wraps around, we track the first candidate to detect when
		// we've checked all
		final EObject firstCandidate = candidateSelectorStrategy.getNextCandidate(owner, eReferenceType);
		if (firstCandidate == null) {
			return null;
		}

		// Note: at this point, getNextCandidate never returns null (it wraps around)
		EObject candidate = firstCandidate;
		do {
			if (isCandidateValid(owner, reference, candidate)) {
				return candidate;
			}
			candidate = candidateSelectorStrategy.getNextCandidate(owner, eReferenceType);
		} while (candidate != firstCandidate);

		// We've checked all candidates and none passed the filter
		return null;
	}

	private boolean isCandidateValid(EObject owner, EReference reference, EObject candidate) {
		return (owner != candidate || allowCycleFor(owner, reference)) &&
				EMFUtils.canSetInThePresenceOfOppositeReference(reference, candidate);
	}
}
