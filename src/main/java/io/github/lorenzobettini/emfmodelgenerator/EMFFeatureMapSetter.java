package io.github.lorenzobettini.emfmodelgenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.eclipse.emf.ecore.util.FeatureMapUtil;

/**
 * Coordinates population of EMF FeatureMaps. FeatureMaps allow heterogeneous
 * collections where different structural-feature types can be mixed. This
 * coordinator owns group-level count and member-selection configuration and
 * delegates value semantics to the ordinary attribute, containment-reference,
 * and cross-reference setters.
 *
 * <p>The current population phase handles containment-reference members only.
 * Attribute and non-containment-reference members will be materialized by later
 * population phases.</p>
 *
 * @author Lorenzo Bettini
 */
public class EMFFeatureMapSetter extends EMFCountConfigurableFeatureSetter<EAttribute> {

	private static final int DEFAULT_MULTI_VALUED_COUNT = 2;
	private EMFCandidateSelectorStrategy<EAttribute, EStructuralFeature> groupMemberSelector =
		new EMFRoundRobinFeatureMapGroupMemberSelector();
	private EMFAttributeSetter attributeSetter;
	private EMFContainmentReferenceSetter containmentReferenceSetter;
	private EMFCrossReferenceSetter crossReferenceSetter;

	protected EMFFeatureMapSetter() {
		super(DEFAULT_MULTI_VALUED_COUNT);
	}

	/**
	 * Set the attribute setter used to generate values for attribute group members.
	 *
	 * @param attributeSetter the current ordinary attribute setter
	 */
	public void setAttributeSetter(final EMFAttributeSetter attributeSetter) {
		this.attributeSetter = attributeSetter;
	}

	/**
	 * Set the containment-reference setter used to create values for containment
	 * group members.
	 *
	 * @param containmentReferenceSetter the current ordinary containment setter
	 */
	public void setContainmentReferenceSetter(
			final EMFContainmentReferenceSetter containmentReferenceSetter) {
		this.containmentReferenceSetter = containmentReferenceSetter;
	}

	/**
	 * Set the cross-reference setter used to select values for non-containment
	 * reference group members.
	 *
	 * @param crossReferenceSetter the current ordinary cross-reference setter
	 */
	public void setCrossReferenceSetter(final EMFCrossReferenceSetter crossReferenceSetter) {
		this.crossReferenceSetter = crossReferenceSetter;
	}

	/**
	 * Set the group member selector strategy for selecting feature map group members.
	 * 
	 * @param strategy the heterogeneous structural-feature selector strategy to use
	 */
	public void setGroupMemberSelectorStrategy(
			EMFCandidateSelectorStrategy<EAttribute, EStructuralFeature> strategy) {
		this.groupMemberSelector = strategy;
	}

	/**
	 * Reset the state of the group member selector, causing it to restart from the
	 * beginning on the next selection.
	 */
	public void reset() {
		groupMemberSelector.reset();
	}

	/**
	 * Set the feature map on the given owner.
	 * This method populates the feature map with entries for each group member.
	 *
	 * @param owner the EObject to set the feature map on
	 * @param featureMapAttribute the feature map attribute
	 * @return collection of created EObjects
	 */
	public Collection<EObject> setFeatureMap(final EObject owner,
			final EAttribute featureMapAttribute) {
		if (owner.eIsSet(featureMapAttribute)) {
			return List.of();
		}
		return setMultiFeature(owner, featureMapAttribute);
	}

	/**
	 * Populate the feature map by finding all group members and creating instances for each.
	 * This population phase currently supports containment-reference members only.
	 */
	private Collection<EObject> setMultiFeature(final EObject owner,
			final EAttribute featureMapAttribute) {
		final var createdEObjects = new ArrayList<EObject>();
		final FeatureMap featureMap = (FeatureMap) owner.eGet(featureMapAttribute);
		
		// Find all features that are part of this feature map group
		final List<EStructuralFeature> groupMembers =
			EMFUtils.findFeatureMapGroupMembers(featureMapAttribute);
		
		if (groupMembers.isEmpty()) {
			return List.of();
		}
		
		// For each group member, create instances and add them to the feature map
		final int count = EMFUtils.getEffectiveCount(featureMapAttribute,
				getMaxCountFor(owner, featureMapAttribute));
		
		for (int i = 0; i < count; i++) {
			// Select the next group member using the selector strategy
			final EReference groupMember =
				(EReference) groupMemberSelector.getNextCandidate(owner, featureMapAttribute);

			// Create a single instance directly (pass owner as context for selector)
			final EObject instance = containmentReferenceSetter.createValue(owner, groupMember);
			featureMap.add(FeatureMapUtil.createEntry(groupMember, instance));
			if (instance != null) {
				createdEObjects.add(instance);
			}
		}
		return createdEObjects;
	}
}
