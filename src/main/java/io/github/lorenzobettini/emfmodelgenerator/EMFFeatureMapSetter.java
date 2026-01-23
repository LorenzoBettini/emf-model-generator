package io.github.lorenzobettini.emfmodelgenerator;

import java.util.Collection;
import java.util.List;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.eclipse.emf.ecore.util.FeatureMapUtil;

/**
 * Responsible for populating EMF feature maps.
 * Feature maps allow heterogeneous collections where different types can be mixed.
 * This class creates entries in the feature map for each group member defined via ExtendedMetaData.
 */
public class EMFFeatureMapSetter extends EMFInstanceCreatorFeatureSetter<EAttribute> {

	private static final int DEFAULT_MULTI_VALUED_COUNT = 2;
	private EMFCandidateSelectorStrategy<EAttribute, EReference> groupMemberSelector = new EMFRoundRobinFeatureMapGroupMemberSelector();

	@FunctionalInterface
	public static interface EMFFeatureMapValueFunction extends FeatureFunction<EObject> {
	}

	protected EMFFeatureMapSetter() {
		super(DEFAULT_MULTI_VALUED_COUNT);
	}

	/**
	 * Set the group member selector strategy for selecting feature map group members.
	 * 
	 * @param strategy the candidate selector strategy to use
	 */
	public void setGroupMemberSelectorStrategy(EMFCandidateSelectorStrategy<EAttribute, EReference> strategy) {
		this.groupMemberSelector = strategy;
	}

	/**
	 * Reset the state of the group member selector.
	 * 
	 * <p>This clears the internal state of the candidate selector, causing it to
	 * restart from the beginning on the next selection. This is useful when new
	 * elements are added to the resource set and you want them to be considered
	 * in subsequent selections.</p>
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
	public Collection<EObject> setFeatureMap(final EObject owner, final EAttribute featureMapAttribute) {
		return setFeatureCreatingEObjects(owner, featureMapAttribute);
	}

	@Override
	protected void setSingleFeature(EObject owner, EAttribute feature) {
		// Feature maps are always multi-valued
		throw new UnsupportedOperationException("Feature maps are always multi-valued");
	}

	/**
	 * Populate the feature map by finding all group members and creating instances for each.
	 * Group members are references that have an ExtendedMetaData annotation pointing to this feature map.
	 */
	@Override
	protected void setMultiFeature(EObject owner, EAttribute featureMapAttribute) {
		final FeatureMap featureMap = (FeatureMap) owner.eGet(featureMapAttribute);
		
		// Find all features that are part of this feature map group
		final List<EReference> groupMembers = EMFUtils.findFeatureMapGroupMembers(featureMapAttribute);
		
		if (groupMembers.isEmpty()) {
			return;
		}
		
		// For each group member, create instances and add them to the feature map
		final int count = EMFUtils.getEffectiveCount(featureMapAttribute, getMaxCountFor(owner, featureMapAttribute));
		
		for (int i = 0; i < count; i++) {
			// Select the next group member using the selector strategy
			final EReference groupMember = groupMemberSelector.getNextCandidate(owner, featureMapAttribute);

			// Create a single instance directly (pass owner as context for selector)
			final EObject instance = createInstance(owner, groupMember, groupMember.getEReferenceType());
			featureMap.add(FeatureMapUtil.createEntry(groupMember, instance));
		}
	}
}
