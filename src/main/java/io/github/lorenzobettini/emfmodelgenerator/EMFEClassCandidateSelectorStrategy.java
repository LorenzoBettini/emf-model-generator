package io.github.lorenzobettini.emfmodelgenerator;

import org.eclipse.emf.ecore.EClass;

/**
 * Selects an instantiable EClass for an EClass request.
 *
 * @author Lorenzo Bettini
 */
public interface EMFEClassCandidateSelectorStrategy
		extends EMFCandidateSelectorStrategy<EClass, EClass> {
}
