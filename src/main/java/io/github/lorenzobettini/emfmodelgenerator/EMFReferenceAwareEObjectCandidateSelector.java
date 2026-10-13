package io.github.lorenzobettini.emfmodelgenerator;

/**
 * Previous name for {@link EMFReferenceAwareRoundRobinEObjectCandidateSelector}.
 * This compatibility facade shares its implementation and has no separate state.
 * Use the longer name in new code to make its round-robin cursor behavior clear.
 *
 * @deprecated use {@link EMFReferenceAwareRoundRobinEObjectCandidateSelector}
 *
 * @author Lorenzo Bettini
 */
@Deprecated
public class EMFReferenceAwareEObjectCandidateSelector
		extends EMFReferenceAwareRoundRobinEObjectCandidateSelector {
}
