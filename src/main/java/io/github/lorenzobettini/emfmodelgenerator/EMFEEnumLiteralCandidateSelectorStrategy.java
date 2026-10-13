package io.github.lorenzobettini.emfmodelgenerator;

import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;

/**
 * Selects an EEnumLiteral for an EEnum request.
 *
 * @author Lorenzo Bettini
 */
public interface EMFEEnumLiteralCandidateSelectorStrategy
		extends EMFCandidateSelectorStrategy<EEnum, EEnumLiteral> {
}
