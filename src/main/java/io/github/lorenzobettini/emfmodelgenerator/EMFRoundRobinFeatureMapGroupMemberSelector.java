package io.github.lorenzobettini.emfmodelgenerator;

import java.util.List;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

/**
 * Round-robin selector for feature map group members.
 * Selects EReferences that belong to a feature map group in a round-robin fashion.
 * 
 * <p>This class maintains state across multiple calls to ensure that when selecting
 * group members from a feature map, we start from where we left off rather than always
 * selecting the same members. This provides better distribution of selections across
 * available group members.</p>
 * 
 * <p>Example:
 * <pre>{@code
 * EMFRoundRobinFeatureMapGroupMemberSelector selector = new EMFRoundRobinFeatureMapGroupMemberSelector();
 * 
 * // First call returns first group member (e.g., sections)
 * EReference member1 = selector.getNextCandidate(context, featureMapAttribute);
 * 
 * // Second call returns second group member (e.g., figures)
 * EReference member2 = selector.getNextCandidate(context, featureMapAttribute);
 * 
 * // Third call wraps around to first member again
 * EReference member3 = selector.getNextCandidate(context, featureMapAttribute);
 * 
 * // If no group members available, returns null
 * EReference noMember = selector.getNextCandidate(context, emptyFeatureMap); // returns null
 * }</pre>
 */
public class EMFRoundRobinFeatureMapGroupMemberSelector extends EMFAbstractCachedRoundRobinCandidateSelector<EAttribute, EReference> {

	@Override
	protected List<EReference> compute(final EObject context, final EAttribute key) {
		// Context is not needed for finding feature map group members
		return EMFUtils.findFeatureMapGroupMembers(key);
	}
}
