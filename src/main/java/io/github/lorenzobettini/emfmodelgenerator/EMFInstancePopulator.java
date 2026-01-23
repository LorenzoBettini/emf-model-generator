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

	public EMFInstancePopulator() {
		this.attributeSetter = new EMFAttributeSetter();
		this.crossReferenceSetter = new EMFCrossReferenceSetter();
		this.containmentReferenceSetter = new EMFContainmentReferenceSetter();
		this.featureMapSetter = new EMFFeatureMapSetter();
	}

	public EMFAttributeSetter getAttributeSetter() {
		return attributeSetter;
	}

	public EMFCrossReferenceSetter getCrossReferenceSetter() {
		return crossReferenceSetter;
	}

	public EMFContainmentReferenceSetter getContainmentReferenceSetter() {
		return containmentReferenceSetter;
	}

	public EMFFeatureMapSetter getFeatureMapSetter() {
		return featureMapSetter;
	}

	public void setAttributeSetter(EMFAttributeSetter attributeSetter) {
		this.attributeSetter = attributeSetter;
	}

	public void setCrossReferenceSetter(EMFCrossReferenceSetter crossReferenceSetter) {
		this.crossReferenceSetter = crossReferenceSetter;
	}

	public void setContainmentReferenceSetter(EMFContainmentReferenceSetter containmentReferenceSetter) {
		this.containmentReferenceSetter = containmentReferenceSetter;
	}

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

	public void setMaxDepth(final int maxDepth) {
		this.maxDepth = maxDepth;
	}

	public void setAttributeDefaultMaxCount(int count) {
		attributeSetter.setDefaultMaxCount(count);
	}

	public void setCrossReferenceDefaultMaxCount(int count) {
		crossReferenceSetter.setDefaultMaxCount(count);
	}

	public void setContainmentReferenceDefaultMaxCount(int count) {
		containmentReferenceSetter.setDefaultMaxCount(count);
	}

	public void setAttributeMaxCountFor(EAttribute attribute, int count) {
		attributeSetter.setMaxCountFor(attribute, count);
	}

	public void setCrossReferenceMaxCountFor(EReference reference, int count) {
		crossReferenceSetter.setMaxCountFor(reference, count);
	}

	public void setContainmentReferenceMaxCountFor(EReference reference, int count) {
		containmentReferenceSetter.setMaxCountFor(reference, count);
	}

	public void setFeatureMapDefaultMaxCount(int count) {
		featureMapSetter.setDefaultMaxCount(count);
	}

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