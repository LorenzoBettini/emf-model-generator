package io.github.lorenzobettini.emfmodelgenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Abstract base class for setting containment references by creating new EObject instances.
 * 
 * This class extends EMFConfigurableFeatureSetter to provide functionality for creating
 * and assigning new EObjects to containment references on a given owner EObject.
 * 
 * The created EObjects are tracked and can be retrieved after setting the reference.
 * 
 * @see EMFConfigurableFeatureSetter
 */
public abstract class EMFInstanceCreatorFeatureSetter<T extends EStructuralFeature> extends EMFConfigurableFeatureSetter<T, EReference, EObject> {

	private EMFCandidateSelectorStrategy<EClass, EClass> instantiableSubclassSelectorStrategy = new EMFRoundRobinEClassCandidateSelector();
	private List<EObject> createdEObjects = new ArrayList<>();

	protected EMFInstanceCreatorFeatureSetter(int defaultMaxCount) {
		super(defaultMaxCount);
	}

	/**
	 * Set the candidate selector strategy for selecting instantiable subclasses.
	 * 
	 * @param strategy the candidate selector strategy to use
	 */
	public void setInstantiableSubclassSelectorStrategy(EMFCandidateSelectorStrategy<EClass, EClass> strategy) {
		this.instantiableSubclassSelectorStrategy = strategy;
	}

	/**
	 * Set the feature on the given owner EObject by creating new EObject instances.
	 * 
	 * Each time the method is called, the list of created EObjects is cleared.
	 * So the returned collection only contains the EObjects created during this call.
	 * 
	 * @param owner   the EObject owning the feature
	 * @param feature the feature to set
	 * @return a collection of created EObjects assigned to the feature
	 */
	protected Collection<EObject> setFeatureCreatingEObjects(EObject owner, T feature) {
		createdEObjects = new ArrayList<>();
	
		setFeature(owner, feature);
	
		return createdEObjects;
	}

	/**
	 * Create an instance of the given type for the specified owner and reference.
	 * 
	 * If a custom function is defined for the reference, it is used to create the instance.
	 * Otherwise, an instantiable subclass of the type is selected using the configured strategy,
	 * and a new EObject of that subclass is created.
	 * If no instantiable subclass is available, null is returned.
	 * 
	 * @param owner
	 * @param reference
	 * @param type
	 * @return
	 */
	protected EObject createInstance(EObject owner, EReference reference, EClass type) {
		var function = getFunctionFor(reference);
		if (function != null) {
			final EObject instance = function.apply(owner);
			if (instance != null) {
				createdEObjects.add(instance);
				return instance;
			}
		}
		var instantiableSubClass = instantiableSubclassSelectorStrategy.getNextCandidate(owner, type);
		if (instantiableSubClass == null) {
			return null;
		}
		final EObject instance = EcoreUtil.create(instantiableSubClass);
		createdEObjects.add(instance);
		return instance;
	}

}
