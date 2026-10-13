package io.github.lorenzobettini.emfmodelgenerator;

import io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceCandidateSelectorStrategy.CandidateDecision;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

/**
 * Responsible for setting cross reference (non-containment) values on EMF EObjects.
 * This class handles both single-valued and multi-valued cross references.
 * Non-containment references are assigned from existing compatible instances. If no
 * candidate exists, the reference is left unchanged; this is valid for optional references,
 * while required references may then fail structural validation. Call
 * {@link EMFModelGenerator#validate()} or enable validation before saving when structural
 * conformance is required.
 *
 * @author Lorenzo Bettini
 */
public class EMFCrossReferenceSetter extends EMFConfigurableFeatureSetter<EReference, EReference, EObject> {

	private static final int DEFAULT_MULTI_VALUED_COUNT = 2;
	private EMFCrossReferenceCandidateSelectorStrategy candidateSelectorStrategy =
			new EMFRoundRobinEObjectCandidateSelector();
	private SelfReferencePolicy selfReferencePolicy = (owner, reference) -> false;

	/**
	 * Function interface for cross reference operations.
	 *
	 * @author Lorenzo Bettini
	 */
	@FunctionalInterface
	public static interface EMFCrossReferenceValueFunction extends FeatureFunction<EObject> {
	}

	/**
	 * Functional interface for selectively allowing direct self-references.
	 *
	 * <p>By default, the population process prevents direct self-references, that is,
	 * assignments where an object references itself through a non-containment reference,
	 * to avoid trivial models. However, in some domains direct self-references are
	 * semantically meaningful. A {@code SelfReferencePolicy} selectively allows them.</p>
	 *
	 * <p>This policy concerns only the case where the owner and the referenced object are
	 * the same {@code EObject} (cycles of length one). It does not detect or prevent longer
	 * cycles among non-containment references.</p>
	 *
	 * <p>The policy filters both the default round-robin candidates and the values returned
	 * by custom cross-reference functions, including non-containment feature map group members,
	 * which reuse {@link #selectValue(EObject, EReference)}.</p>
	 *
	 * <p>For example, in a graph model where {@code Node.outgoing} refers to {@code Node},
	 * self-loops can be allowed only for nodes at even positions:</p>
	 * {@snippet :
	 * generator.getInstancePopulator().setContainmentReferenceDefaultMaxCount(3);
	 * generator.getInstancePopulator().setSelfReferencePolicy((owner, reference) -> {
	 *     if ("outgoing".equals(reference.getName())
	 *             && "Node".equals(owner.eClass().getName())) {
	 *         var graph = owner.eContainer();
	 *         if (graph != null) {
	 *             var nodes = EMFUtils.getAsEObjectsList(graph,
	 *                 graph.eClass().getEStructuralFeature("nodes"));
	 *             return nodes.indexOf(owner) % 2 == 0;
	 *         }
	 *     }
	 *     return false;
	 * });
	 * // Result: Node 0 and Node 2 may reference themselves, Node 1 may not
	 * }
	 *
	 * @author Lorenzo Bettini
	 */
	@FunctionalInterface
	public static interface SelfReferencePolicy {
		/**
		 * Determines whether an EObject is allowed to reference itself through the given reference.
		 * 
		 * @param owner the EObject that would reference itself
		 * @param reference the reference through which the owner would reference itself
		 * @return true if the owner is allowed to reference itself, false otherwise
		 */
		boolean allowSelfReferenceFor(EObject owner, EReference reference);
	}

	public EMFCrossReferenceSetter() {
		super(DEFAULT_MULTI_VALUED_COUNT);
	}

	/**
	 * Set the candidate selector strategy for selecting existing EObject instances.
	 * 
	 * @param strategy the candidate selector strategy to use
	 */
	public void setCandidateSelectorStrategy(EMFCrossReferenceCandidateSelectorStrategy strategy) {
		this.candidateSelectorStrategy = strategy;
	}

	/**
	 * Set the policy for determining whether direct self-references are allowed.
	 *
	 * <p>The default policy denies every direct self-reference. Install a custom
	 * {@link SelfReferencePolicy} to selectively allow an owner to reference itself
	 * through a non-containment reference where that is meaningful for the domain.</p>
	 *
	 * @param policy the self-reference policy to use
	 * @see SelfReferencePolicy
	 */
	public void setSelfReferencePolicy(SelfReferencePolicy policy) {
		this.selfReferencePolicy = policy;
	}

	/**
	 * Reset the candidate selection state.
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
	 * Uses the configured self-reference policy.
	 * 
	 * @param owner the EObject that would reference itself
	 * @param reference the reference through which the owner would reference itself
	 * @return true if the owner is allowed to reference itself, false otherwise
	 */
	protected boolean allowSelfReferenceFor(EObject owner, EReference reference) {
		return selfReferencePolicy.allowSelfReferenceFor(owner, reference);
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
		// For single-valued references, use an existing assignable instance
		EObject referencedEObject = selectValue(owner, reference);
		if (referencedEObject == null) {
			return;
		}
		owner.eSet(reference, referencedEObject);
	}

	@Override
	protected void setMultiFeature(EObject owner, EReference reference) {
		final var list = EMFUtils.getAsList(owner, reference);

		// For multi-valued references, add multiple EObjects
		// Respect upper and lower bounds
		final int count = EMFUtils.getEffectiveCount(reference, getMaxCountFor(owner, reference));
		// if some values are already set, we only add the missing ones
		// (this could happen in the presence of opposite references)
		final int missingCount = count - list.size();

		for (int i = 0; i < missingCount; i++) {
			// Get next candidate using the configured selector strategy
			final EObject referencedEObject = selectValue(owner, reference);
			if (referencedEObject == null) {
				return;
			}

			list.add(referencedEObject);
		}
	}

	/**
	 * Selects one valid existing value for a cross-reference without assigning it
	 * to the owner. A configured function is tried first. A {@code null} or invalid
	 * custom candidate falls back to the configured candidate selector. Default
	 * candidates are filtered by the cycle policy and opposite multiplicity.
	 * The FeatureMap coordinator reuses this operation for non-containment
	 * group members.
	 *
	 * @param owner the EObject owning the cross-reference
	 * @param crossReference the cross-reference for which to select a value
	 * @return one valid existing EObject, or {@code null} if none is available
	 */
	public EObject selectValue(final EObject owner, final EReference crossReference) {
		var function = getFunctionFor(crossReference);
		if (function != null) {
			final EObject candidate = function.apply(owner);
			if (candidate != null &&
					evaluateCandidate(owner, crossReference, candidate) == CandidateDecision.ACCEPT) {
				return candidate;
			}
		}
		return candidateSelectorStrategy.selectCandidate(owner, crossReference,
				candidate -> evaluateCandidate(owner, crossReference, candidate));
	}

	private CandidateDecision evaluateCandidate(final EObject owner, final EReference reference,
			final EObject candidate) {
		if (owner == candidate && !allowSelfReferenceFor(owner, reference)) {
			return CandidateDecision.SKIP;
		}
		if (reference.isMany() && reference.isUnique() &&
				EMFUtils.getAsEObjectsList(owner, reference).contains(candidate)) {
			return CandidateDecision.SKIP;
		}
		if (!EMFUtils.canSetInThePresenceOfOppositeReference(reference, candidate)) {
			return CandidateDecision.OPPOSITE_CAPACITY_EXHAUSTED;
		}
		return CandidateDecision.ACCEPT;
	}
}
