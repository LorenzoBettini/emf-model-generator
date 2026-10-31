package io.github.lorenzobettini.emfmodelgenerator;

import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;

/**
 * Selects an EEnumLiteral for an EEnum request. This nominal contract is the
 * configuration type for enum attribute values.
 * {@snippet :
 * EMFModelGenerator generator = new EMFModelGenerator();
 * EMFEEnumLiteralCandidateSelectorStrategy selector =
 *     new EMFRoundRobinEEnumLiteralCandidateSelector();
 * generator.getInstancePopulator().setEnumLiteralSelectorStrategy(selector);
 * }
 *
 * @author Lorenzo Bettini
 */
public interface EMFEEnumLiteralCandidateSelectorStrategy
		extends EMFCandidateSelectorStrategy<EEnum, EEnumLiteral> {
}
