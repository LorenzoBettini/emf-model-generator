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
	 * Creates an instance of the given type for the specified owner and reference.
	 *
	 * <p>If a custom function is defined for the reference, it is invoked first.
	 * A non-null object returned by the function is used only if it is not already
	 * contained by another object. Reusing an already-contained object could make
	 * EMF move it from its current container. In that case, or if the function
	 * returns {@code null}, the default creation strategy is used instead.</p>
	 *
	 * <p>The default strategy selects an instantiable subclass of the requested
	 * type using the configured strategy and creates a new {@link EObject} of that
	 * subclass. If no instantiable subclass is available, {@code null} is
	 * returned.</p>
	 *
	 * @param owner the EObject owning the reference
	 * @param reference the reference for which to create an instance
	 * @param type the EClass of the instance to create
	 * @return the created or selected EObject, or {@code null} if no suitable
	 *         instance can be obtained
	 */
	protected EObject createInstance(EObject owner, EReference reference, EClass type) {
		var function = getFunctionFor(reference);
		if (function != null) {
			final EObject instance = function.apply(owner);
			if (instance != null && instance.eContainer() == null) {
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
