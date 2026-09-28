package io.github.lorenzobettini.emfmodelgenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.eclipse.emf.ecore.util.FeatureMapUtil;

/**
 * Coordinates population of EMF FeatureMaps. FeatureMaps allow heterogeneous
 * collections where different structural-feature types can be mixed. This
 * coordinator owns group-level count, member-selection, planning, ordering,
 * and entry insertion while delegating value semantics to the ordinary
 * attribute, containment-reference, and cross-reference setters.
 *
 * <p>Each physical FeatureMap is represented by a per-population-call
 * {@link FeatureMapPlan}. Planning freezes the complete heterogeneous member
 * sequence without generating values. Structural materialization then handles
 * attribute and containment-reference members. After containment expansion,
 * cross-reference materialization handles non-containment-reference members.</p>
 *
 * @author Lorenzo Bettini
 */
public class EMFFeatureMapSetter extends EMFCountConfigurableFeatureSetter<EAttribute> {

	/**
	 * An ordered plan for one physical FeatureMap. The plan records which selected
	 * members have been materialized so entries produced in different phases can
	 * retain their original relative order.
	 */
	public static final class FeatureMapPlan {
		private final EObject owner;
		private final EAttribute featureMapAttribute;
		private final List<EStructuralFeature> groupMembers;
		private final boolean[] materialized;

		/**
		 * Creates an ordered plan. The selected member list is copied so later caller
		 * changes cannot alter the frozen sequence.
		 *
		 * @param owner the EObject owning the physical FeatureMap
		 * @param featureMapAttribute the physical FeatureMap attribute
		 * @param groupMembers the selected member sequence in ordinal order
		 */
		public FeatureMapPlan(final EObject owner, final EAttribute featureMapAttribute,
				final List<? extends EStructuralFeature> groupMembers) {
			this.owner = owner;
			this.featureMapAttribute = featureMapAttribute;
			this.groupMembers = List.copyOf(groupMembers);
			this.materialized = new boolean[groupMembers.size()];
		}

		/**
		 * @return the EObject owning the physical FeatureMap
		 */
		public EObject owner() {
			return owner;
		}

		/**
		 * @return the physical FeatureMap attribute
		 */
		public EAttribute featureMapAttribute() {
			return featureMapAttribute;
		}

		/**
		 * @return the immutable selected member sequence
		 */
		public List<EStructuralFeature> groupMembers() {
			return groupMembers;
		}

		/**
		 * Reports whether the selected member at the given ordinal has produced a
		 * physical FeatureMap entry.
		 *
		 * @param ordinal the zero-based planned ordinal
		 * @return whether that ordinal has been materialized
		 */
		public boolean isMaterialized(final int ordinal) {
			return materialized[ordinal];
		}

		private void markMaterialized(final int ordinal) {
			materialized[ordinal] = true;
		}
	}

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
			final EMFCandidateSelectorStrategy<EAttribute, EStructuralFeature> strategy) {
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
	 * Creates an ordered plan for the given physical FeatureMap without generating
	 * values or changing the owner. An already-set map or a map with no members
	 * produces an empty plan.
	 *
	 * @param owner the EObject owning the FeatureMap
	 * @param featureMapAttribute the physical FeatureMap attribute
	 * @return the frozen plan for this map
	 */
	public FeatureMapPlan createPlan(final EObject owner,
			final EAttribute featureMapAttribute) {
		if (owner.eIsSet(featureMapAttribute)
				|| EMFUtils.findFeatureMapGroupMembers(featureMapAttribute).isEmpty()) {
			return new FeatureMapPlan(owner, featureMapAttribute, List.of());
		}

		final int count = EMFUtils.getEffectiveCount(featureMapAttribute,
				getMaxCountFor(owner, featureMapAttribute));
		final var selectedMembers = new ArrayList<EStructuralFeature>(count);
		for (int i = 0; i < count
				&& groupMemberSelector.hasCandidates(owner, featureMapAttribute); i++) {
			final var groupMember =
					groupMemberSelector.getNextCandidate(owner, featureMapAttribute);
			if (groupMember == null) {
				break;
			}
			selectedMembers.add(groupMember);
		}
		return new FeatureMapPlan(owner, featureMapAttribute, selectedMembers);
	}

	/**
	 * Materializes the structural portions of a plan. Attribute members are always
	 * attempted first. Containment-reference members are attempted only when
	 * {@code containmentAllowed} is {@code true}; non-containment references remain
	 * pending. Null delegated values do not produce placeholder entries.
	 *
	 * @param plan the ordered FeatureMap plan
	 * @param containmentAllowed whether containment expansion is permitted at the
	 *                           current depth
	 * @return contained EObjects inserted into the FeatureMap
	 */
	public Collection<EObject> materializeStructuralFeatures(final FeatureMapPlan plan,
			final boolean containmentAllowed) {
		final var createdEObjects = new ArrayList<EObject>();
		for (int i = 0; i < plan.groupMembers().size(); i++) {
			final var member = plan.groupMembers().get(i);
			if (!plan.isMaterialized(i) && member instanceof EAttribute attribute) {
				final var value = attributeSetter.generateValue(plan.owner(), attribute);
				insertIfNotNull(plan, i, member, value);
			}
		}
		if (containmentAllowed) {
			for (int i = 0; i < plan.groupMembers().size(); i++) {
				final var member = plan.groupMembers().get(i);
				if (!plan.isMaterialized(i) && member instanceof EReference reference
						&& reference.isContainment()) {
					final var value = containmentReferenceSetter.createValue(plan.owner(), reference);
					if (insertIfNotNull(plan, i, member, value)) {
						createdEObjects.add(value);
					}
				}
			}
		}
		return createdEObjects;
	}

	/**
	 * Materializes the non-containment-reference portions of a plan. Values are
	 * selected from existing EObjects by the current cross-reference setter. Null
	 * selections remain unmaterialized, and unique member views skip values already
	 * exposed by that same member while candidates remain available.
	 *
	 * @param plan the ordered FeatureMap plan
	 */
	public void materializeCrossReferences(final FeatureMapPlan plan) {
		for (int i = 0; i < plan.groupMembers().size(); i++) {
			final var member = plan.groupMembers().get(i);
			if (!plan.isMaterialized(i) && member instanceof EReference reference
					&& !reference.isContainment()) {
				insertIfNotNull(plan, i, member,
						selectUniqueCrossReferenceValue(plan.owner(), reference));
			}
		}
	}

	private EObject selectUniqueCrossReferenceValue(final EObject owner,
			final EReference reference) {
		EObject value = crossReferenceSetter.selectValue(owner, reference);
		if (!reference.isUnique()) {
			return value;
		}

		final var currentValues = EMFUtils.getAsEObjectsList(owner, reference);
		final EObject firstValue = value;
		while (value != null && currentValues.contains(value)) {
			value = crossReferenceSetter.selectValue(owner, reference);
			if (value == firstValue) {
				return null;
			}
		}
		return value;
	}

	private boolean insertIfNotNull(final FeatureMapPlan plan, final int ordinal,
			final EStructuralFeature member, final Object value) {
		if (value == null) {
			return false;
		}
		final FeatureMap featureMap =
				(FeatureMap) plan.owner().eGet(plan.featureMapAttribute());
		featureMap.add(insertionIndex(plan, ordinal),
				FeatureMapUtil.createEntry(member, value));
		plan.markMaterialized(ordinal);
		return true;
	}

	private int insertionIndex(final FeatureMapPlan plan, final int ordinal) {
		return (int) IntStream.range(0, ordinal)
				.filter(plan::isMaterialized)
				.count();
	}

	/**
	 * Convenience operation that plans and structurally materializes a FeatureMap
	 * with containment enabled. Population orchestration should use
	 * {@link #createPlan(EObject, EAttribute)} and
	 * {@link #materializeStructuralFeatures(FeatureMapPlan, boolean)} separately so
	 * planning occurs before ordinary attribute population.
	 *
	 * @param owner the EObject to set the feature map on
	 * @param featureMapAttribute the feature map attribute
	 * @return contained EObjects inserted into the FeatureMap
	 */
	public Collection<EObject> setFeatureMap(final EObject owner,
			final EAttribute featureMapAttribute) {
		return materializeStructuralFeatures(createPlan(owner, featureMapAttribute), true);
	}
}
