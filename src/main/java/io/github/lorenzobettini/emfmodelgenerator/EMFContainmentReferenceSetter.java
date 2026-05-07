package io.github.lorenzobettini.emfmodelgenerator;

import java.util.Collection;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

/**
 * Responsible for setting containment reference values on EMF EObjects.
 * This class handles both single-valued and multi-valued containment references,
 * creating appropriate EObjects based on the reference's type.
 * The created EObjects are returned as a collection.
 */
public class EMFContainmentReferenceSetter extends EMFInstanceCreatorFeatureSetter<EReference> {

	private static final int DEFAULT_MULTI_VALUED_COUNT = 2;

	/**
	 * Function interface for containment reference operations.
	 */
	@FunctionalInterface
	public static interface EMFContainmentReferenceValueFunction extends FeatureFunction<EObject> {
	}

	public EMFContainmentReferenceSetter() {
		super(DEFAULT_MULTI_VALUED_COUNT);
	}

	/**
	 * Set the containment reference on the given owner EObject.
	 * 
	 * For setting the containment reference, new EObjects are created.
	 * For creating the EObjects, instantiable subclasses of the reference type are used in round-robin fashion. 
	 *
	 * @param owner     the EObject owning the reference
	 * @param reference the assumed containment EReference to set (its type is assumed to be in a resource, which is assumed to be in a resource set)
	 * @return a collection of created EObjects assigned to the containment reference
	 */
	public Collection<EObject> setContainmentReference(EObject owner, EReference reference) {
		return setFeatureCreatingEObjects(owner, reference);
	}

	@Override
	protected void setSingleFeature(EObject owner, EReference reference) {
		var eReferenceType = reference.getEReferenceType();
		// For single-valued references, create and set one EObject
		final EObject created = createInstance(owner, reference, eReferenceType);
		if (created != null) {
			owner.eSet(reference, created);
		}
	}

	@Override
	protected void setMultiFeature(EObject owner, EReference reference) {
		final int count = EMFUtils.getEffectiveCount(reference, getMaxCountFor(owner, reference));
		final var list = EMFUtils.getAsList(owner, reference);
		var eReferenceType = reference.getEReferenceType();
		// For multi-valued references, add multiple EObjects
		// Respect upper and lower bounds
		for (int i = 0; i < count; i++) {
			final EObject created = createInstance(owner, reference, eReferenceType);
			if (created != null) {
				list.add(created);
			}
		}
	}
}
