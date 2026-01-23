package io.github.lorenzobettini.emfmodelgenerator;

import java.util.List;

import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;

/**
 * Round-robin selector for EEnumLiteral instances within a given EEnum type.
 */
public class EMFRoundRobinEEnumLiteralCandidateSelector extends EMFRoundRobinCandidateSelector<EEnum, EEnumLiteral> {

	@Override
	protected List<EEnumLiteral> getOrCompute(EObject context, EEnum type) {
		return type.getELiterals();
	}

}
