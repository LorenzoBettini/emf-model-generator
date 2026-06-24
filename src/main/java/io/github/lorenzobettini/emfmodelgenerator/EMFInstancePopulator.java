package io.github.lorenzobettini.emfmodelgenerator;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

import io.github.lorenzobettini.emfmodelgenerator.EMFAttributeSetter.EMFAttributeValueFunction;
import io.github.lorenzobettini.emfmodelgenerator.EMFContainmentReferenceSetter.EMFContainmentReferenceValueFunction;
import io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceSetter.EMFCrossReferenceValueFunction;
import io.github.lorenzobettini.emfmodelgenerator.EMFFeatureMapSetter.EMFFeatureMapValueFunction;

/**
 * Responsible for populating EMF EObjects with sample data.
 * This includes setting attribute values and populating both containment
 * and cross references, with support for configurable multi-valued
 * counts and maximum depth for recursive population.
 * Also handles feature maps, which allow heterogeneous collections.
 */
public class EMFInstancePopulator {

	private static final int DEFAULT_MAX_DEPTH = 5;

	private int maxDepth = DEFAULT_MAX_DEPTH;

	private EMFAttributeSetter attributeSetter;

	private EMFCrossReferenceSetter crossReferenceSetter;

	private EMFContainmentReferenceSetter containmentReferenceSetter;

	private EMFFeatureMapSetter featureMapSetter;

	/**
	 * Create a new EMFInstancePopulator with default settings.
	 * All setter components are initialised with their default strategies.
	 */
	public EMFInstancePopulator() {
		this.attributeSetter = new EMFAttributeSetter();
		this.crossReferenceSetter = new EMFCrossReferenceSetter();
		this.containmentReferenceSetter = new EMFContainmentReferenceSetter();
		this.featureMapSetter = new EMFFeatureMapSetter();
	}

	/**
	 * Returns the attribute setter used for populating EAttribute values.
	 *
	 * @return the attribute setter
	 */
	public EMFAttributeSetter getAttributeSetter() {
		return attributeSetter;
	}

	/**
	 * Returns the cross-reference setter used for populating non-containment references.
	 *
	 * @return the cross-reference setter
	 */
	public EMFCrossReferenceSetter getCrossReferenceSetter() {
		return crossReferenceSetter;
	}

	/**
	 * Returns the containment reference setter used for populating containment references.
	 *
	 * @return the containment reference setter
	 */
	public EMFContainmentReferenceSetter getContainmentReferenceSetter() {
		return containmentReferenceSetter;
	}

	/**
	 * Returns the feature map setter used for populating EMF feature maps.
	 *
	 * @return the feature map setter
	 */
	public EMFFeatureMapSetter getFeatureMapSetter() {
		return featureMapSetter;
	}

	/**
	 * Replace the attribute setter.
	 *
	 * @param attributeSetter the attribute setter to use
	 */
	public void setAttributeSetter(EMFAttributeSetter attributeSetter) {
		this.attributeSetter = attributeSetter;
	}

	/**
	 * Replace the cross-reference setter.
	 *
	 * @param crossReferenceSetter the cross-reference setter to use
	 */
	public void setCrossReferenceSetter(EMFCrossReferenceSetter crossReferenceSetter) {
		this.crossReferenceSetter = crossReferenceSetter;
	}

	/**
	 * Replace the containment reference setter.
	 *
	 * @param containmentReferenceSetter the containment reference setter to use
	 */
	public void setContainmentReferenceSetter(EMFContainmentReferenceSetter containmentReferenceSetter) {
		this.containmentReferenceSetter = containmentReferenceSetter;
	}

	/**
	 * Replace the feature map setter.
	 *
	 * @param featureMapSetter the feature map setter to use
	 */
	public void setFeatureMapSetter(EMFFeatureMapSetter featureMapSetter) {
		this.featureMapSetter = featureMapSetter;
	}

	/**
	 * Set a custom function for generating values for the given EAttribute.
	 * 
	 * @param attribute the EAttribute for which to set the function
	 * @param function  the function to generate values for the attribute
	 */
	public void functionForAttribute(EAttribute attribute,
			EMFAttributeValueFunction function) {
		attributeSetter.setFunctionFor(attribute, function);
	}

	/**
	 * Set a custom function for generating values for the given cross EReference.
	 * 
	 * @param reference the cross EReference for which to set the function
	 * @param function  the function to generate values for the cross reference
	 */
	public void functionForCrossReference(EReference reference,
			EMFCrossReferenceValueFunction function) {
		crossReferenceSetter.setFunctionFor(reference, function);
	}

	/**
	 * Set a custom function for generating values for the given containment EReference.
	 * 
	 * @param reference the containment EReference for which to set the function
	 * @param function  the function to generate values for the containment reference
	 */
	public void functionForContainmentReference(EReference reference,
			EMFContainmentReferenceValueFunction function) {
		containmentReferenceSetter.setFunctionFor(reference, function);
	}

	/**
	 * Set a custom function for generating values for the given feature map group member.
	 * 
	 * @param groupMember the feature map group member EReference for which to set the function
	 * @param function    the function to generate values for the group member
	 */
	public void functionForFeatureMapGroupMember(EReference groupMember,
			EMFFeatureMapValueFunction function) {
		featureMapSetter.setFunctionFor(groupMember, function);
	}

	/**
	 * Set the strategy for selecting instantiable subclasses when creating instances
	 * for types in containment references.
	 * 
	 * @param strategy the candidate selector strategy
	 */
	public void setInstantiableSubclassSelectorStrategy(EMFCandidateSelectorStrategy<EClass, EClass> strategy) {
		containmentReferenceSetter.setInstantiableSubclassSelectorStrategy(strategy);
	}

	/**
	 * Set the strategy for selecting candidate EObjects for cross-references.
	 * 
	 * @param strategy the candidate selector strategy
	 */
	public void setCrossReferenceCandidateSelectorStrategy(EMFCandidateSelectorStrategy<EClass, EObject> strategy) {
		crossReferenceSetter.setCandidateSelectorStrategy(strategy);
	}

	/**
	 * Set the strategy for selecting enum literals when generating enum attribute values.
	 * 
	 * @param strategy the candidate selector strategy
	 */
	public void setEnumLiteralSelectorStrategy(EMFCandidateSelectorStrategy<EEnum, EEnumLiteral> strategy) {
		attributeSetter.setEnumLiteralSelectorStrategy(strategy);
	}

	/**
	 * Set the strategy for selecting feature map group members when populating feature maps.
	 * 
	 * @param strategy the candidate selector strategy
	 */
	public void setGroupMemberSelectorStrategy(EMFCandidateSelectorStrategy<EAttribute, EReference> strategy) {
		featureMapSetter.setGroupMemberSelectorStrategy(strategy);
	}

	/**
	 * Set the cycle policy for determining whether self-references are allowed in cross references.
	 * 
	 * @param cyclePolicy the cycle policy to use
	 */
	public void setAllowCyclePolicy(EMFCrossReferenceSetter.CyclePolicy cyclePolicy) {
		crossReferenceSetter.setAllowCyclePolicy(cyclePolicy);
	}

	/**
	 * Set the maximum recursion depth for populating containment references.
	 * Attributes are always populated regardless of depth.
	 *
	 * @param maxDepth the maximum depth
	 */
	public void setMaxDepth(final int maxDepth) {
		this.maxDepth = maxDepth;
	}

	/**
	 * Set the default maximum number of values generated for multi-valued attributes.
	 *
	 * @param count the default maximum count
	 */
	public void setAttributeDefaultMaxCount(int count) {
		attributeSetter.setDefaultMaxCount(count);
	}

	/**
	 * Set the default maximum number of values generated for multi-valued cross references.
	 *
	 * @param count the default maximum count
	 */
	public void setCrossReferenceDefaultMaxCount(int count) {
		crossReferenceSetter.setDefaultMaxCount(count);
	}

	/**
	 * Set the default maximum number of values generated for multi-valued containment references.
	 *
	 * @param count the default maximum count
	 */
	public void setContainmentReferenceDefaultMaxCount(int count) {
		containmentReferenceSetter.setDefaultMaxCount(count);
	}

	/**
	 * Set the maximum number of values generated for the given multi-valued attribute.
	 *
	 * @param attribute the attribute to configure
	 * @param count     the maximum count
	 */
	public void setAttributeMaxCountFor(EAttribute attribute, int count) {
		attributeSetter.setMaxCountFor(attribute, count);
	}

	/**
	 * Set the maximum number of values generated for the given multi-valued cross reference.
	 *
	 * @param reference the cross reference to configure
	 * @param count     the maximum count
	 */
	public void setCrossReferenceMaxCountFor(EReference reference, int count) {
		crossReferenceSetter.setMaxCountFor(reference, count);
	}

	/**
	 * Set the maximum number of values generated for the given multi-valued containment reference.
	 *
	 * @param reference the containment reference to configure
	 * @param count     the maximum count
	 */
	public void setContainmentReferenceMaxCountFor(EReference reference, int count) {
		containmentReferenceSetter.setMaxCountFor(reference, count);
	}

	/**
	 * Set the default maximum number of entries generated for feature maps.
	 *
	 * @param count the default maximum count
	 */
	public void setFeatureMapDefaultMaxCount(int count) {
		featureMapSetter.setDefaultMaxCount(count);
	}

	/**
	 * Set the maximum number of entries generated for the given feature map attribute.
	 *
	 * @param featureMapAttribute the feature map attribute to configure
	 * @param count               the maximum count
	 */
	public void setFeatureMapMaxCountFor(EAttribute featureMapAttribute, int count) {
		featureMapSetter.setMaxCountFor(featureMapAttribute, count);
	}

	/**
	 * Populate the given EObjects with sample data.
	 * This includes setting attribute values and populating both containment
	 * and cross references, up to the configured maximum depth.
	 *
	 * @param rootInstances the EObjects to populate
	 */
	public void populateEObjects(EObject... rootInstances) {
		var createdEObjects = new ArrayList<EObject>();
		for (var root : rootInstances) {
			createdEObjects.addAll(populateEObject(root, 0));
		}

		// reset cross reference setter state
		// so that candidates for cross-references are recomputed
		crossReferenceSetter.reset();

		// after having populated all containments up to max depth, populate cross references
		// for all root instances...
		for (var root : rootInstances) {
			populateCrossReferences(root);
		}
		// ... and for all created EObjects
		for (var createdEObject : createdEObjects) {
			populateCrossReferences(createdEObject);
		}
	}

	/**
	 * Recursively populate the given EObject up to the configured maximum depth.
	 * 
	 * @param eObject the EObject to populate
	 * @param depth  the current depth of recursion
	 * @return the list of created EObjects during population
	 */
	private Collection<EObject> populateEObject(EObject eObject, int depth) {
		var createdEObjects = new ArrayList<EObject>();
		// attributes are populated always
		populateAttributes(eObject);
		if (depth < maxDepth) {
			populateFeatureMaps(eObject, createdEObjects);
			populateContainmentReferences(eObject, createdEObjects);
			// recursively populate created EObjects
			var recursiveCreatedEObjects = new ArrayList<EObject>();
			for (var createdEObject : createdEObjects) {
				recursiveCreatedEObjects.addAll(populateEObject(createdEObject, depth + 1));
			}
			createdEObjects.addAll(recursiveCreatedEObjects);
		}
		return createdEObjects;
	}

	private void populateAttributes(EObject eObject) {
		for (var attribute : eObject.eClass().getEAllAttributes()) {
			if (EMFUtils.isValidAttribute(attribute)) {
				attributeSetter.setAttribute(eObject, attribute);
			}
		}
	}

	private void populateFeatureMaps(EObject eObject, Collection<EObject> createdEObjects) {
		for (var attribute : eObject.eClass().getEAllAttributes()) {
			if (EMFUtils.isFeatureMap(attribute)) {
				createdEObjects.addAll(featureMapSetter.setFeatureMap(eObject, attribute));
			}
		}
	}

	private void populateContainmentReferences(EObject eObject, Collection<EObject> createdEObjects) {
		for (var reference : eObject.eClass().getEAllContainments()) {
			if (EMFUtils.isValidReference(reference)) {
				createdEObjects.addAll(containmentReferenceSetter.setContainmentReference(eObject, reference));
			}
		}
	}

	private void populateCrossReferences(EObject eObject) {
		for (var reference : eObject.eClass().getEAllReferences()) {
			if (!reference.isContainment() && EMFUtils.isValidReference(reference)) {
				crossReferenceSetter.setCrossReference(eObject, reference);
			}
		}
	}

}