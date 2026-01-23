package io.github.lorenzobettini.emfmodelgenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Base class for setting configurable features on EMF EObjects.
 * Supports setting maximum counts for multi-valued features and associating
 * operations with features.
 * 
 * @param <T1> The type of EStructuralFeature (e.g., EAttribute, EReference) used for setting.
 * @param <T2> The type of EStructuralFeature (e.g., EAttribute, EReference) used for creating
 * the feature value.
 * @param <V> The type of value returned for the feature
 */
public abstract class EMFConfigurableFeatureSetter<T1 extends EStructuralFeature, T2 extends EStructuralFeature, V> {

	/**
	 * Function interface for feature operations.
	 */
	@FunctionalInterface
	public static interface FeatureFunction<R> extends Function<EObject, R> {
	}

	private int defaultMaxCount;
	private Map<T1, Integer> featureMaxCountMap;
	private Map<T2, FeatureFunction<V>> featureFunctionMap;

	protected EMFConfigurableFeatureSetter(int defaultMaxCount) {
		this.defaultMaxCount = defaultMaxCount;
	}

	public void setDefaultMaxCount(int defaultMaxCount) {
		this.defaultMaxCount = defaultMaxCount;
	}

	/**
	 * Returns the maximum count of values to set for the given feature on the given
	 * owner.
	 * 
	 * @param owner   The owner of the feature
	 * @param feature The feature for the max count
	 * @return
	 */
	protected int getMaxCountFor(EObject owner, T1 feature) {
		return (featureMaxCountMap == null)
				? defaultMaxCount
				: featureMaxCountMap.getOrDefault(feature, defaultMaxCount);
	}

	/**
	 * Sets the maximum count of values to set for the given feature.
	 * 
	 * @param feature
	 * @param maxCount
	 */
	public void setMaxCountFor(T1 feature, int maxCount) {
		if (featureMaxCountMap == null) {
			featureMaxCountMap = new HashMap<>();
		}
		featureMaxCountMap.put(feature, maxCount);
	}

	/**
	 * Returns the function associated with the given feature.
	 * 
	 * @param feature The feature for the operation
	 * @return
	 */
	protected FeatureFunction<V> getFunctionFor(T2 feature) {
		return (featureFunctionMap == null)
				? null
				: featureFunctionMap.get(feature);
	}

	/**
	 * Sets the function associated with the given feature.
	 * 
	 * @param feature
	 * @param function
	 */
	public void setFunctionFor(T2 feature, FeatureFunction<V> function) {
		if (featureFunctionMap == null) {
			featureFunctionMap = new HashMap<>();
		}
		featureFunctionMap.put(feature, function);
	}

	/**
	 * Determines whether the feature should be set on the given owner EObject. By
	 * default, the feature should be set if it is not already set.
	 * 
	 * @param owner
	 * @param feature
	 * @return
	 */
	protected boolean shouldSetFeature(EObject owner, T1 feature) {
		return !owner.eIsSet(feature);
	}

	/**
	 * Template method to set the feature on the given owner EObject.
	 * 
	 * It checks whether the feature should be set, and delegates to the appropriate
	 * method for single-valued
	 * ({@link #setSingleFeature(EObject, EStructuralFeature)}) or multi-valued
	 * features ({@link #setMultiFeature(EObject, EStructuralFeature)}).
	 * 
	 * @param owner
	 * @param feature
	 */
	public void setFeature(EObject owner, T1 feature) {
		if (!shouldSetFeature(owner, feature)) {
			return;
		}

		if (feature.isMany()) {
			setMultiFeature(owner, feature);
		} else {
			setSingleFeature(owner, feature);
		}
	}

	/**
	 * Set a single-valued feature on the given owner EObject.
	 * 
	 * @param owner
	 * @param feature
	 */
	protected abstract void setSingleFeature(EObject owner, T1 feature);

	/**
	 * Set a multi-valued feature on the given owner EObject.
	 * 
	 * @param owner
	 * @param feature
	 */
	protected abstract void setMultiFeature(EObject owner, T1 feature);
}
