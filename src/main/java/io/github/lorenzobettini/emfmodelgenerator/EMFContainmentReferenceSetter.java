package io.github.lorenzobettini.emfmodelgenerator;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Responsible for setting containment reference values on EMF EObjects.
 * This class handles both single-valued and multi-valued containment references,
 * creating appropriate EObjects based on the reference's type.
 * The created EObjects are returned as a collection.
 *
 * @author Lorenzo Bettini
 */
public class EMFContainmentReferenceSetter
		extends EMFConfigurableFeatureSetter<EReference, EReference, EObject> {

	private static final int DEFAULT_MULTI_VALUED_COUNT = 2;
	private EMFCandidateSelectorStrategy<EClass, EClass> instantiableSubclassSelectorStrategy =
			new EMFRoundRobinEClassCandidateSelector();
	private Collection<EObject> assignedEObjects;

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
	 * Set the candidate selector strategy for selecting instantiable subclasses.
	 *
	 * @param strategy the candidate selector strategy to use
	 */
	public void setInstantiableSubclassSelectorStrategy(
			final EMFCandidateSelectorStrategy<EClass, EClass> strategy) {
		this.instantiableSubclassSelectorStrategy = strategy;
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
		final var result = new ArrayList<EObject>();
		assignedEObjects = result;
		try {
			setFeature(owner, reference);
		} finally {
			assignedEObjects = null;
		}
		return result;
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
		final var function = getFunctionFor(containmentReference);
		if (function != null) {
			final EObject instance = function.apply(owner);
			if (instance != null && instance.eContainer() == null) {
				return instance;
			}
		}
		final var instantiableSubclass = instantiableSubclassSelectorStrategy
				.getNextCandidate(owner, containmentReference.getEReferenceType());
		return instantiableSubclass == null ? null : EcoreUtil.create(instantiableSubclass);
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

	private void trackAssignedEObject(final EObject assignedEObject) {
		if (assignedEObjects != null) {
			assignedEObjects.add(assignedEObject);
		}
	}
}
