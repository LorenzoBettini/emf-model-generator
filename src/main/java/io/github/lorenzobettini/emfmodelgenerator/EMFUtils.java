package io.github.lorenzobettini.emfmodelgenerator;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.ExtendedMetaData;

/**
 * Utility class for EMF models: validation, checks, and other utility methods.
 */
public final class EMFUtils {

	private EMFUtils() {
		// Utility class, prevent instantiation
	}

	/**
	 * Check if a resource is an Ecore resource.
	 * A resource is considered an Ecore resource if it is not empty and its first content
	 * element is an EPackage.
	 *
	 * @param resource the resource to check
	 * @return true if the resource is an Ecore resource, false otherwise
	 */
	public static boolean isEcoreResource(final Resource resource) {
		return !resource.getContents().isEmpty() && resource.getContents().get(0) instanceof EPackage;
	}

	/**
	 * Check if an EClass can be instantiated.
	 * An EClass can be instantiated if it is neither abstract nor an interface.
	 *
	 * @param eClass the EClass to check (must not be null)
	 * @return true if the EClass can be instantiated, false otherwise
	 */
	public static boolean canBeInstantiated(final EClass eClass) {
		return !eClass.isAbstract() && !eClass.isInterface();
	}

	/**
	 * Check if an EReference is valid for processing.
	 * A reference is valid if it is changeable, not derived, and not a container reference.
	 *
	 * @param reference the EReference to check
	 * @return true if the reference is valid for processing, false otherwise
	 */
	public static boolean isValidReference(final EReference reference) {
		return isValidCommon(reference)
				&& !reference.isContainer();
	}

	/**
	 * Check if an EAttribute is valid for processing.
	 * An attribute is valid if it is changeable, not derived, and not a feature map.
	 * Feature maps require special handling and should not be processed as regular attributes.
	 *
	 * @param attribute the EAttribute to check
	 * @return true if the attribute is valid for processing, false otherwise
	 */
	public static boolean isValidAttribute(final EAttribute attribute) {
		return isValidCommon(attribute) && !isFeatureMap(attribute);
	}

	/**
	 * Check if an EStructuralFeature is a feature map.
	 * A feature map is an attribute with type EFeatureMapEntry.
	 *
	 * @param feature the EStructuralFeature to check
	 * @return true if the feature is a feature map, false otherwise
	 */
	public static boolean isFeatureMap(final EStructuralFeature feature) {
		return feature instanceof EAttribute attribute &&
				attribute.getEAttributeType() == EcorePackage.Literals.EFEATURE_MAP_ENTRY;
	}

	private static boolean isValidCommon(final EStructuralFeature feature) {
		return feature != null 
				&& feature.isChangeable() 
				&& !feature.isDerived();
	}

	/**
	 * Get the value of a multi-valued feature as a list of EObjects.
	 * 
	 * @param instance the EObject instance
	 * @param feature the multi-valued EStructuralFeature
	 * @return the list of EObjects for the feature
	 */
	public static List<EObject> getAsEObjectsList(EObject instance, EStructuralFeature feature) {
		@SuppressWarnings("unchecked")
		var list = (List<EObject>) instance.eGet(feature);
		return list;
	}

	/**
	 * Get the value of a multi-valued feature as a list.
	 *
	 * @param instance the EObject instance
	 * @param feature the multi-valued EStructuralFeature
	 * @return the list of values for the feature
	 */
	public static List<Object> getAsList(EObject instance, EStructuralFeature feature) {
		@SuppressWarnings("unchecked")
		var list = (List<Object>) instance.eGet(feature);
		return list;
	}

	/**
	 * Calculate the effective count of values to generate for a multi-valued feature.
	 * This respects both the lower and upper bounds of the feature.
	 * 
	 * <p>The method returns a count that satisfies the following constraints:
	 * <ul>
	 * <li>If the feature has an upper bound (not -1), the count will not exceed it</li>
	 * <li>If the feature has a lower bound, the count will be at least that value</li>
	 * <li>If both bounds are set, the count will be within the range [lowerBound, upperBound]</li>
	 * </ul>
	 * </p>
	 * 
	 * <p>Examples:
	 * <ul>
	 * <li>defaultCount=5, lowerBound=0, upperBound=3 → returns 3</li>
	 * <li>defaultCount=2, lowerBound=5, upperBound=-1 → returns 5</li>
	 * <li>defaultCount=10, lowerBound=2, upperBound=4 → returns 4</li>
	 * </ul>
	 * </p>
	 * 
	 * @param feature the feature to calculate the count for
	 * @param defaultCount the default count to use if bounds do not restrict it
	 * @return the number of values to generate, respecting the feature's bounds
	 */
	public static int getEffectiveCount(final EStructuralFeature feature, final int defaultCount) {
		final int lowerBound = feature.getLowerBound();
		final int upperBound = feature.getUpperBound();
		
		int count = defaultCount;
		
		// Respect upper bound if it's set (not -1 for unbounded)
		if (upperBound > 0) {
			count = Math.min(count, upperBound);
		}
		
		// Respect lower bound
		count = Math.max(count, lowerBound);
		
		return count;
	}

	/**
	 * Finds all instantiable subclasses of the given EClass by scanning the EMF global
	 * package registry. This includes the EClass itself if it is instantiable (not
	 * abstract and not an interface). 
	 * 
	 * <p>
	 * This method uses the global {@link EPackage.Registry#INSTANCE} to find all registered
	 * EPackages and scans their contents for subclasses, including all nested subpackages
	 * recursively. This approach works even when EClasses are in different resources or
	 * not in a resource set.
	 * </p>
	 * 
	 * <p>
	 * The method identifies subclasses based on both direct inheritance and conceptual
	 * identity (i.e., same conceptual ID, {@link #conceptualId(EClass)}). This allows it to find
	 * subclasses for which the object identity is not the same due to different resource loading contexts.
	 * For example, when the inheritance hierarchy spans multiple resources or when EClasses are
	 * loaded in different resource sets (the Ecores use file-based URIs).
	 * </p>
	 * 
	 * <p>
	 * For example, if C extends B and B extends A (all instantiable), calling this
	 * method with A will return a collection containing [A, B, C].
	 * </p>
	 * 
	 * @param eClass the EClass for which to find subclasses
	 * @return a list of all instantiable subclasses in the hierarchy, including the
	 *         EClass itself if instantiable
	 */
	public static List<EClass> findAllInstantiableSubclasses(final EClass eClass) {
		final Set<EClass> result = new LinkedHashSet<>();

		// First, add the EClass itself if it's instantiable
		if (canBeInstantiated(eClass)) {
			result.add(eClass);
		}

		var conceptualId = conceptualId(eClass);

		// Iterate over all registered EPackages in the global registry
		// Sort keys to ensure deterministic order
		final var registry = EPackage.Registry.INSTANCE;
		registry.keySet().stream()
			.sorted()
			.forEach(nsURI -> {
				var ePackage = registry.getEPackage(nsURI);
				// Scan the EPackage's resource for subclasses
				scanResourceForSubclasses(ePackage, eClass, conceptualId, result);
			});

		return List.copyOf(result);
	}

	/**
	 * Helper method to scan an EPackage's EClasses for subclasses of the given EClass.
	 * This method recursively scans all subpackages.
	 * 
	 * @param ePackage the EPackage to scan
	 * @param eClass the base EClass
	 * @param conceptualId the conceptual ID of the base EClass
	 * @param result the set to add found subclasses to
	 */
	private static void scanResourceForSubclasses(final EPackage ePackage, final EClass eClass, 
			String conceptualId, final Set<EClass> result) {
		for (var classifier : ePackage.getEClassifiers()) {
			// Check if this candidate is a subclass of the given eClass
			// isSuperTypeOf returns true if eClass is a supertype of candidate (or they are equal)
			// We need to exclude the eClass itself since we already added it
			// We also check conceptual identity to catch cases where object identity differs
			// due to different resource loading contexts
			if (classifier instanceof final EClass candidate &&
					candidate != eClass &&
					canBeInstantiated(candidate)) {
				var allSuperTypes = candidate.getEAllSuperTypes();
				if (allSuperTypes.stream()
						.anyMatch(superType -> superType == eClass ||
								conceptualId(superType).equals(conceptualId))) {
					result.add(candidate);
				}
			}
		}
		
		// Recursively scan subpackages
		for (var subPackage : ePackage.getESubpackages()) {
			scanResourceForSubclasses(subPackage, eClass, conceptualId, result);
		}
	}

	/**
	 * Get the conceptual ID of an EClass, defined as "EPackageNsURI::EClassName".
	 * 
	 * @param eClass the EClass for which to compute the conceptual ID
	 * @return the conceptual ID
	 */
	private static String conceptualId(EClass eClass) {
		return String.format("%s::%s", eClass.getEPackage().getNsURI(), eClass.getName());
	}

	/**
	 * Find all instances of the given EClass (or its subclasses) based on the
	 * context EObject's containment situation.
	 * 
	 * <p>
	 * This method scans for instances based on where the context EObject is stored:
	 * </p>
	 * <ul>
	 * <li><b>Context in a resource set</b>: Scans all non-Ecore resources in the
	 * resource set. Ecore resources (metamodel definitions whose first content is
	 * an EPackage) are skipped.</li>
	 * <li><b>Context in a single resource (not in a resource set)</b>: Scans only
	 * that resource's contents.</li>
	 * <li><b>Context not in a resource</b>: Scans the root container's (obtained via
	 * {@link EcoreUtil#getRootContainer(EObject)}) direct and indirect contents.</li>
	 * </ul>
	 * 
	 * <p>
	 * When the context is in a resource set, this method scans the resource set
	 * where the context EObject is stored, not the resource set of the EClass.
	 * This is important because EObjects (model instances) and their EClasses
	 * (metamodel) can be stored in different resource sets, which is a common
	 * pattern in EMF.
	 * </p>
	 * 
	 * @param context the EObject that defines the search scope
	 * @param eClass  the EClass to search instances for (including subclass
	 *                instances)
	 * @return a list of all EObjects in the context's scope that are instances of
	 *         the given EClass
	 */
	public static List<EObject> findAllAssignableInstances(final EObject context, final EClass eClass) {
		final Set<EObject> result = new LinkedHashSet<>();

		final Resource contextResource = context.eResource();

		if (contextResource == null) {
			// Context not in a resource: scan the root container's contents
			scanEObjectRootContainerContents(context, eClass, result);
		} else {
			final ResourceSet resourceSet = contextResource.getResourceSet();
			if (resourceSet == null) {
				// Context in a single resource (not in a resource set): scan only that resource
				scanResource(contextResource, eClass, result);
			} else {
				// Context in a resource set: scan all non-Ecore resources
				for (Resource resource : resourceSet.getResources()) {
					if (isEcoreResource(resource)) {
						continue;
					}
					scanResource(resource, eClass, result);
				}
			}
		}

		return List.copyOf(result);
	}

	/**
	 * Scan a resource for instances of the given EClass.
	 * 
	 * @param resource the resource to scan
	 * @param eClass   the EClass to search instances for
	 * @param result   the set to add found instances to
	 */
	private static void scanResource(final Resource resource, final EClass eClass, final Set<EObject> result) {
		scanIterator(EcoreUtil.getAllContents(resource, true), eClass, result);
	}

	/**
	 * Scan the root container of an EObject and its contents for instances of the given EClass.
	 * This method retrieves the root container of the context (using EcoreUtil.getRootContainer)
	 * and then scans its entire containment tree.
	 * 
	 * @param context the EObject whose root container will be scanned
	 * @param eClass  the EClass to search instances for
	 * @param result  the set to add found instances to
	 */
	private static void scanEObjectRootContainerContents(final EObject context, final EClass eClass, final Set<EObject> result) {
		final EObject rootContainer = EcoreUtil.getRootContainer(context);
		// Check the root container itself
		addIfInstance(rootContainer, eClass, result);
		// Iterate through all contents of the root container
		scanIterator(EcoreUtil.getAllContents(rootContainer, true), eClass, result);
	}

	/**
	 * Add an EObject to the result set if it is an instance of the given EClass.
	 * 
	 * @param candidate the EObject to check
	 * @param eClass    the EClass to check against
	 * @param result    the set to add the candidate to if it matches
	 */
	private static void addIfInstance(final EObject candidate, final EClass eClass, final Set<EObject> result) {
		if (eClass.isInstance(candidate)) {
			result.add(candidate);
		}
	}

	/**
	 * Scan an iterator of EObjects and add instances of the given EClass to the result set.
	 * 
	 * @param iterator the iterator of EObjects to scan
	 * @param eClass   the EClass to search instances for
	 * @param result   the set to add found instances to
	 */
	private static void scanIterator(final Iterator<EObject> iterator, final EClass eClass, final Set<EObject> result) {
		while (iterator.hasNext()) {
			addIfInstance(iterator.next(), eClass, result);
		}
	}

	/**
	 * Check if it is possible to set the given value to the given reference, based on the assumption that from the type system perspective it is already possible.
	 * 
	 * If the reference has no opposite, then it is always possible to set the value.
	 * 
	 * If the value's EClass has no such opposite reference, then it is always possible to set the value.
	 * 
	 * If the opposite reference is single-valued, then it is possible to set the value only if the value's opposite is not set.
	 * 
	 * If the opposite reference is multi-valued, then it is possible to add the value only if the value's list has not already reached
	 * the upper bound (if any).
	 * 
	 * @param reference
	 * @param value
	 * @return true if it is possible to set the value to the reference, false otherwise
	 */
	public static boolean canSetInThePresenceOfOppositeReference(EReference reference, EObject value) {
		EReference opposite = reference.getEOpposite();
		if (opposite == null) {
			return true;
		}
		// check whether the value's EClass has such an opposite reference
		if (!value.eClass().getEAllReferences().contains(opposite)) {
			return true;
		}
		if (opposite.isMany()) {
			// multi-valued opposite reference
			var list = getAsList(value, opposite);
			final int upperBound = opposite.getUpperBound();
			return (upperBound == -1 || list.size() < upperBound);
		} else {
			// single-valued opposite reference
			return !value.eIsSet(opposite);
		}
	}

	/**
	 * Find all references that are part of the given feature map group.
	 * These are references with ExtendedMetaData annotation pointing to the feature map.
	 *
	 * @param featureMapAttribute the feature map attribute
	 * @return list of references that are part of the group
	 */
	public static List<EReference> findFeatureMapGroupMembers(final EAttribute featureMapAttribute) {
		final var groupMembers = new java.util.ArrayList<EReference>();
		final var extendedMetaData = ExtendedMetaData.INSTANCE;
		final var eClass = featureMapAttribute.getEContainingClass();
		
		// Iterate through all references to find group members
		for (var reference : eClass.getEAllReferences()) {
			// Check if this feature is part of the feature map group
			final EStructuralFeature group = extendedMetaData.getGroup(reference);
			if (group != null && group.equals(featureMapAttribute)) {
				groupMembers.add(reference);
			}
		}
		
		return groupMembers;
	}
}
