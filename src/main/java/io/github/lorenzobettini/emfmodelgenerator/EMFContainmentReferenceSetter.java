package io.github.lorenzobettini.emfmodelgenerator;

import java.util.Collection;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

/**
 * Responsible for setting containment reference values on EMF EObjects.
 * This class handles both single-valued and multi-valued containment references,
 * creating appropriate EObjects based on the reference's type.
 * The created EObjects are returned as a collection.
 *
 * @author Lorenzo Bettini
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
	 * For creating the EObjects, instantiable subclasses of the reference type are
	 * selected using the configured strategy (by default, round-robin).
	 *
	 * @param owner     the EObject owning the reference
	 * @param reference the assumed containment EReference to set (its type is assumed to be in a resource, which is assumed to be in a resource set)
	 * @return a collection of created EObjects assigned to the containment reference
	 */
	public Collection<EObject> setContainmentReference(EObject owner, EReference reference) {
		return setFeatureCreatingEObjects(owner, reference);
	}

	/**
	 * Creates or obtains one value for a containment reference without assigning it
	 * to the owner. A configured containment function is tried first. If it returns
	 * {@code null} or an already-contained EObject, the configured instantiable
	 * subclass selector is used for default creation. The FeatureMap coordinator
	 * reuses this operation for containment group members.
	 *
	 * @param owner the EObject owning the containment reference
	 * @param containmentReference the containment reference for which to create a value
	 * @return one unassigned EObject, or {@code null} if no instantiable type is available
	 */
	public EObject createValue(final EObject owner, final EReference containmentReference) {
		return createInstance(owner, containmentReference,
				containmentReference.getEReferenceType());
	}

	@Override
	protected void setSingleFeature(EObject owner, EReference reference) {
		// For single-valued references, create and set one EObject
		final EObject created = createValue(owner, reference);
		if (created != null) {
			owner.eSet(reference, created);
			trackAssignedEObject(created);
		}
	}

	@Override
	protected void setMultiFeature(EObject owner, EReference reference) {
		final int count = EMFUtils.getEffectiveCount(reference, getMaxCountFor(owner, reference));
		final var list = EMFUtils.getAsList(owner, reference);
		// For multi-valued references, add multiple EObjects
		// Respect upper and lower bounds
		for (int i = 0; i < count; i++) {
			final EObject created = createValue(owner, reference);
			if (created != null) {
				list.add(created);
				trackAssignedEObject(created);
			}
		}
	}
}
