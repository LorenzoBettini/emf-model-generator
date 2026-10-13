package io.github.lorenzobettini.emfmodelgenerator;

import java.util.List;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

/**
 * Provides stateful round-robin selection of assignable candidates for cross-references.
 * 
 * <p>This is the default cross-reference selector. It keeps one cached ordered
 * candidate list and wrapping cursor per target EClass. The setter examines
 * candidates until one passes its self policy and live opposite-capacity check.
 * No candidate is retired: capacity freed later remains visible without a reset,
 * although newly added resources require one. Repeated calls start where the
 * previous call left off; this does not guarantee even distribution when
 * different references share an EClass or candidates fail validation.</p>
 * 
 * <p>Direct round-robin selection from a context and target EClass:</p>
 * {@snippet :
 * EMFRoundRobinEObjectCandidateSelector selector = new EMFRoundRobinEObjectCandidateSelector();
 *
 * // First call for personClass returns person1
 * EObject candidate1 = selector.getNextCandidate(context, personClass);
 *
 * // Second call continues from where the first left off
 * EObject candidate2 = selector.getNextCandidate(context, personClass);
 *
 * // If no candidates are available, returns null
 * EObject noCandidate = selector.getNextCandidate(context, unknownClass);
 * }
 *
 * <p>To configure the generator explicitly (this selector is already the default):</p>
 * {@snippet :
 * EMFModelGenerator generator = new EMFModelGenerator();
 * EClass rootClass = (EClass) generator.loadEcoreModel("model.ecore")
 *     .getEClassifier("Person");
 * generator.getInstancePopulator().setCrossReferenceCandidateSelectorStrategy(
 *     new EMFRoundRobinEObjectCandidateSelector());
 * generator.generateFrom(rootClass);
 * generator.unloadEcoreModels();
 * }
 *
 * @author Lorenzo Bettini
 */
public class EMFRoundRobinEObjectCandidateSelector extends EMFAbstractCachedRoundRobinCandidateSelector<EClass, EObject> {

	@Override
	protected List<EObject> compute(final EObject context, final EClass key) {
		return EMFUtils.findAllAssignableInstances(context, key);
	}
}
