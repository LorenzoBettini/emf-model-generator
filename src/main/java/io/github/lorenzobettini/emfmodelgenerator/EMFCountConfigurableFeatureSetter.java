package io.github.lorenzobettini.emfmodelgenerator;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Base class for components that configure how many values are generated for
 * multi-valued structural features.
 *
 * @param <T> the type of structural feature whose count can be configured
 *
 * @author Lorenzo Bettini
 */
public abstract class EMFCountConfigurableFeatureSetter<T extends EStructuralFeature> {

	private int defaultMaxCount;
	private Map<T, Integer> featureMaxCountMap;

	protected EMFCountConfigurableFeatureSetter(final int defaultMaxCount) {
		this.defaultMaxCount = defaultMaxCount;
	}

	/**
	 * Set the default maximum count of values to generate for multi-valued features.
	 * Used when no specific count has been configured for a feature via
	 * {@link #setMaxCountFor(EStructuralFeature, int)}.
	 *
	 * @param defaultMaxCount the default maximum count
	 */
	public void setDefaultMaxCount(final int defaultMaxCount) {
		this.defaultMaxCount = defaultMaxCount;
	}

	/**
	 * Returns the maximum count of values to set for the given feature on the given
	 * owner.
	 *
	 * @param owner the owner EObject
	 * @param feature the feature for which to get the max count
	 * @return the maximum count of values to generate for the feature
	 */
	protected int getMaxCountFor(final EObject owner, final T feature) {
		return featureMaxCountMap == null
				? defaultMaxCount
				: featureMaxCountMap.getOrDefault(feature, defaultMaxCount);
	}

	/**
	 * Sets the maximum count of values to set for the given feature.
	 *
	 * @param feature the feature to configure
	 * @param maxCount the maximum count of values to generate
	 */
	public void setMaxCountFor(final T feature, final int maxCount) {
		if (featureMaxCountMap == null) {
			featureMaxCountMap = new HashMap<>();
		}
		featureMaxCountMap.put(feature, maxCount);
	}
}
