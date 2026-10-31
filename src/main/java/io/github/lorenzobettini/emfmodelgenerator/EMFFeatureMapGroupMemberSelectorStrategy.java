package io.github.lorenzobettini.emfmodelgenerator;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Selects a structural feature belonging to a FeatureMap group attribute.
 *
 * @author Lorenzo Bettini
 */
public interface EMFFeatureMapGroupMemberSelectorStrategy
		extends EMFCandidateSelectorStrategy<EAttribute, EStructuralFeature> {
}
