package io.github.lorenzobettini.emfmodelgenerator;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

import io.github.lorenzobettini.emfmodelgenerator.EMFAttributeSetter.EMFAttributeValueFunction;
import io.github.lorenzobettini.emfmodelgenerator.EMFContainmentReferenceSetter.EMFContainmentReferenceValueFunction;
import io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceSetter.EMFCrossReferenceValueFunction;
import io.github.lorenzobettini.emfmodelgenerator.EMFFeatureMapSetter.FeatureMapPlan;

/**
 * Populates existing EMF {@link EObject EObjects} with sample data.
 * This includes setting attribute values and populating both containment
 * and cross references, with support for configurable multi-valued
 * counts and maximum depth for recursive containment expansion.
 *
 * <p>A FeatureMap is an ordered heterogeneous group: its members may be
 * attributes, containment references, or non-containment references. The
 * {@link EMFFeatureMapSetter} coordinator owns the total entry count, the
 * frozen member order, and entry insertion, while one value at a time is
 * obtained through the ordinary semantic setters
 * ({@link EMFAttributeSetter#generateValue(EObject, org.eclipse.emf.ecore.EAttribute)},
 * {@link EMFContainmentReferenceSetter#createValue(EObject, EReference)},
 * {@link EMFCrossReferenceSetter#selectValue(EObject, EReference)}).</p>
 *
 * <p>FeatureMap group members use the same customization as ordinary features:
 * {@link #functionForAttribute(EAttribute, EMFAttributeValueFunction)},
 * {@link #functionForContainmentReference(EReference, EMFContainmentReferenceValueFunction)},
 * and {@link #functionForCrossReference(EReference, EMFCrossReferenceValueFunction)}.
 * Replacing an ordinary setter also replaces the delegate used for the
 * corresponding FeatureMap member kind; replacing the FeatureMap setter installs
 * all three currently configured ordinary setters into it.</p>
 *
 * <p>FeatureMap plans are frozen before ordinary attribute population. Attribute
 * members materialize independently of depth, containment members only when
 * containment expansion is still allowed, and non-containment members after all
 * containment expansion, before ordinary cross-references, preserving the frozen
 * order. Maximum depth therefore limits containment creation only.</p>
 *
 * <p>This class performs only the population step: callers create the root objects and, when
 * required, put them in resources and save those resources themselves. For example, an object
 * whose metamodel has already been loaded can be populated directly:</p>
 * {@snippet :
 * EClass libraryClass = (EClass) ePackage.getEClassifier("Library");
 * EObject library = EcoreUtil.create(libraryClass);
 *
 * EMFInstancePopulator populator = new EMFInstancePopulator();
 * populator.populateEObjects(library);
 * }
 *
 * <p>Population can be tailored globally and for individual structural features. Per-feature
 * functions are useful when deterministic domain-shaped sample values are needed:</p>
 * {@snippet :
 * EAttribute name = (EAttribute) libraryClass.getEStructuralFeature("name");
 * EReference books = (EReference) libraryClass.getEStructuralFeature("books");
 *
 * EMFInstancePopulator populator = new EMFInstancePopulator();
 * populator.functionForAttribute(name,
 *     owner -> "Sample " + owner.eClass().getName());
 * populator.setContainmentReferenceMaxCountFor(books, 3);
 * populator.setMaxDepth(2);
 * populator.populateEObjects(library);
 * }
 *
 * <p>When roots may refer to one another, attach them to resources in the same resource set and
 * pass them to one {@link #populateEObjects(EObject...)} call. This lets cross-reference selection
 * see the complete population before references are assigned.</p>
 *
 * @see EMFModelGenerator
 * @see EMFResourceSetHelper
 *
 * @author Lorenzo Bettini
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
		featureMapSetter.setAttributeSetter(attributeSetter);
		featureMapSetter.setContainmentReferenceSetter(containmentReferenceSetter);
		featureMapSetter.setCrossReferenceSetter(crossReferenceSetter);
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
	 * Returns the feature map coordinator used for populating EMF feature maps.
	 *
	 * @return the feature map setter
	 */
	public EMFFeatureMapSetter getFeatureMapSetter() {
		return featureMapSetter;
	}

	/**
	 * Replace the attribute setter. The replacement also becomes the delegate
	 * used for attribute FeatureMap group members.
	 *
	 * @param attributeSetter the attribute setter to use
	 */
	public void setAttributeSetter(EMFAttributeSetter attributeSetter) {
		this.attributeSetter = attributeSetter;
		featureMapSetter.setAttributeSetter(attributeSetter);
	}

	/**
	 * Replace the cross-reference setter. The replacement also becomes the delegate
	 * used for non-containment FeatureMap group members.
	 *
	 * @param crossReferenceSetter the cross-reference setter to use
	 */
	public void setCrossReferenceSetter(EMFCrossReferenceSetter crossReferenceSetter) {
		this.crossReferenceSetter = crossReferenceSetter;
		featureMapSetter.setCrossReferenceSetter(crossReferenceSetter);
	}

	/**
	 * Replace the containment reference setter. The replacement also becomes the
	 * delegate used for containment FeatureMap group members.
	 *
	 * @param containmentReferenceSetter the containment reference setter to use
	 */
	public void setContainmentReferenceSetter(EMFContainmentReferenceSetter containmentReferenceSetter) {
		this.containmentReferenceSetter = containmentReferenceSetter;
		featureMapSetter.setContainmentReferenceSetter(containmentReferenceSetter);
	}

	/**
	 * Replace the feature map setter. All three currently installed ordinary
	 * setters are injected into the replacement as its value delegates.
	 *
	 * @param featureMapSetter the feature map setter to use
	 */
	public void setFeatureMapSetter(EMFFeatureMapSetter featureMapSetter) {
		this.featureMapSetter = featureMapSetter;
		featureMapSetter.setAttributeSetter(attributeSetter);
		featureMapSetter.setContainmentReferenceSetter(containmentReferenceSetter);
		featureMapSetter.setCrossReferenceSetter(crossReferenceSetter);
	}

	/**
	 * Set a custom function for generating values for the given EAttribute.
	 * For example, a name can be derived from the owning object's class:
	 * {@snippet :
	 * populator.functionForAttribute(nameAttribute,
	 *     owner -> "Sample " + owner.eClass().getName());
	 * }
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
	 * Returning {@code null} delegates that invocation to the default candidate selection:
	 * {@snippet :
	 * populator.functionForCrossReference(authorReference, book -> {
	 *     EObject preferredAuthor = findPreferredAuthor(book);
	 *     return preferredAuthor; // null means: use the default candidate
	 * });
	 * }
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
	 * Set the strategy for selecting instantiable subclasses when creating instances
	 * for types in containment references.
	 * 
	 * @param strategy the heterogeneous structural-feature selector strategy
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
	public void setGroupMemberSelectorStrategy(
			EMFCandidateSelectorStrategy<EAttribute, EStructuralFeature> strategy) {
		featureMapSetter.setGroupMemberSelectorStrategy(strategy);
	}

	/**
	 * Set the policy for determining whether direct self-references are allowed in cross references.
	 *
	 * <p>Delegates to the configured cross-reference setter. See
	 * {@link EMFCrossReferenceSetter.SelfReferencePolicy} for the default behavior,
	 * scope, and an example.</p>
	 *
	 * @param policy the self-reference policy to use
	 * @see EMFCrossReferenceSetter.SelfReferencePolicy
	 */
	public void setSelfReferencePolicy(EMFCrossReferenceSetter.SelfReferencePolicy policy) {
		crossReferenceSetter.setSelfReferencePolicy(policy);
	}

	/**
	 * Set the maximum recursion depth for containment expansion.
	 * Attributes are always populated regardless of depth. FeatureMap attribute
	 * members are also populated at the depth boundary, and eligible FeatureMap
	 * cross-references are still populated later; only containment creation,
	 * ordinary or through a FeatureMap, is suppressed there.
	 * A root is at depth {@code 0}; with a maximum depth of {@code 1}, its direct
	 * contained objects are created and have their attributes populated, but their
	 * containment references are not expanded.
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
	 * and cross references, up to the configured maximum containment depth.
	 * FeatureMap attribute and containment members materialize during structural
	 * population; FeatureMap cross-references materialize after containment
	 * expansion and before ordinary cross-references.
	 *
	 * <p>All roots are populated before cross-references are assigned. Supply related roots together
	 * so that each one can be selected as a cross-reference candidate for the others:</p>
	 * {@snippet :
	 * EObject book = EcoreUtil.create(bookClass);
	 * EObject author = EcoreUtil.create(authorClass);
	 * bookResource.getContents().add(book);
	 * authorResource.getContents().add(author);
	 *
	 * EMFInstancePopulator populator = new EMFInstancePopulator();
	 * populator.populateEObjects(book, author);
	 * }
	 * The resources in this example must belong to the same {@code ResourceSet}. Objects created
	 * through containment during the call are recursively populated as well.
	 *
	 * @param rootInstances the EObjects to populate
	 */
	public void populateEObjects(EObject... rootInstances) {
		final var createdEObjects = new ArrayList<EObject>();
		final var featureMapPlans = new ArrayList<FeatureMapPlan>();
		for (var root : rootInstances) {
			createdEObjects.addAll(populateEObject(root, 0, featureMapPlans));
		}

		// reset cross reference setter state
		// so that candidates for cross-references are recomputed
		crossReferenceSetter.reset();
		for (var plan : featureMapPlans) {
			featureMapSetter.materializeCrossReferences(plan);
		}

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
	private Collection<EObject> populateEObject(final EObject eObject, final int depth,
			final Collection<FeatureMapPlan> featureMapPlans) {
		final var createdEObjects = new ArrayList<EObject>();
		final var currentFeatureMapPlans = createFeatureMapPlans(eObject);
		featureMapPlans.addAll(currentFeatureMapPlans);
		// attributes are populated always
		populateAttributes(eObject);
		materializeFeatureMaps(currentFeatureMapPlans, depth < maxDepth, createdEObjects);
		if (depth < maxDepth) {
			populateContainmentReferences(eObject, createdEObjects);
			// recursively populate created EObjects
			final var recursiveCreatedEObjects = new ArrayList<EObject>();
			for (var createdEObject : createdEObjects) {
				recursiveCreatedEObjects.addAll(
						populateEObject(createdEObject, depth + 1, featureMapPlans));
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

	private Collection<FeatureMapPlan> createFeatureMapPlans(final EObject eObject) {
		return eObject.eClass().getEAllAttributes().stream()
				.filter(EMFUtils::isFeatureMap)
				.map(attribute -> featureMapSetter.createPlan(eObject, attribute))
				.toList();
	}

	private void materializeFeatureMaps(final Collection<FeatureMapPlan> plans,
			final boolean containmentAllowed, final Collection<EObject> createdEObjects) {
		for (var plan : plans) {
			createdEObjects.addAll(
					featureMapSetter.materializeStructuralFeatures(plan, containmentAllowed));
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
