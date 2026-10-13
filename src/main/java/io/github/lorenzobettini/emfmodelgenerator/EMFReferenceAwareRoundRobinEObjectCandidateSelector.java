package io.github.lorenzobettini.emfmodelgenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.BiPredicate;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Opt-in reference-aware round-robin selector for a caller-guaranteed monotonic
 * capacity epoch.
 *
 * <p>From the first selection until {@link #reset()}, a candidate whose opposite
 * reaches its upper bound must never become available again for that reference.
 * This is a caller guarantee: {@code eUnset}, reassignment, EMF inverse updates,
 * FeatureMap changes, callbacks, adapters and custom functions can violate it.
 * Self-reference policies must be side-effect free: skipped retired candidates
 * are not passed to the policy, even if a legacy scan would have visited them.
 * Reset after changing resources, metamodels, or capacity; use the ordinary
 * {@link EMFRoundRobinEObjectCandidateSelector} when this guarantee cannot be made.
 * A selection from a different resource scope without reset is rejected.</p>
 *
 * <p>One source list and cursor are held per target {@link EClass}, just as in the
 * ordinary selector. This is round-robin over the stable ordered source list,
 * with a wrapping cursor shared by all references to that EClass. Each actual
 * reference has its own retired candidates, so interleaved references can have
 * different active sets. It does not promise evenly distributed targets.
 * A bounded opposite additionally has an ordered set of stable
 * candidate indices per actual {@link EReference}. Construction costs
 * O(N log N) time and O(N) extra memory per such reference; navigation and
 * retirement cost O(log N). With many bounded references to a large common
 * target class, prefer the ordinary selector if this extra memory is excessive.
 * Owner-dependent self policy and uniqueness never retire candidates. Custom
 * cross-reference functions bypass the pool and are still checked by the setter
 * against the live opposite and self-reference rules. The legacy
 * {@link #getNextCandidate(EObject, EClass)} and
 * {@link #hasCandidates(EObject, EClass)} methods describe the complete source
 * list, including indices retired for a particular reference.</p>
 *
 * {@snippet :
 * EMFModelGenerator generator = new EMFModelGenerator();
 * EClass rootClass = (EClass) generator.loadEcoreModel("model.ecore")
 *     .getEClassifier("Person");
 * generator.getInstancePopulator().setCrossReferenceCandidateSelectorStrategy(
 *     new EMFReferenceAwareRoundRobinEObjectCandidateSelector());
 * generator.generateFrom(rootClass);
 * generator.unloadEcoreModels();
 * }
 * A target retired for {@code Library.books} can remain available to
 * {@code Author.books} when both target {@code Book} but have different opposites.
 * After freeing a saturated opposite through {@code eUnset} or reassignment,
 * call {@link #reset()} before selecting again.
 *
 * @author Lorenzo Bettini
 */
public class EMFReferenceAwareRoundRobinEObjectCandidateSelector
		implements EMFCandidateSelectorStrategy<EClass, EObject>, EMFCrossReferenceCandidateSelector {

	private final Map<EClass, List<EObject>> candidates = new HashMap<>();
	private final Map<EClass, Object> scopes = new HashMap<>();
	private final Map<EClass, Integer> cursors = new HashMap<>();
	private final Map<EReference, NavigableSet<Integer>> active = new HashMap<>();

	private List<EObject> candidates(final EObject context, final EClass type) {
		final Object scope = scope(context);
		final Object previous = scopes.putIfAbsent(type, scope);
		if (previous != null && previous != scope) {
			throw new IllegalStateException("Resource scope changed; reset the selector");
		}
		return candidates.computeIfAbsent(type,
				ignored -> EMFUtils.findAllAssignableInstances(context, type));
	}

	private Object scope(final EObject context) {
		final Resource resource = context.eResource();
		if (resource == null) {
			return EcoreUtil.getRootContainer(context);
		}
		return resource.getResourceSet() != null ? resource.getResourceSet() : resource;
	}

	@Override
	public EObject getNextCandidate(final EObject context, final EClass type) {
		final var values = candidates(context, type);
		if (values.isEmpty()) {
			return null;
		}
		final int index = cursors.getOrDefault(type, 0);
		cursors.put(type, (index + 1) % values.size());
		return values.get(index);
	}

	@Override
	public boolean hasCandidates(final EObject context, final EClass type) {
		return !candidates(context, type).isEmpty();
	}

	/**
	 * Select for the actual reference, preserving the shared EClass cursor.
	 * Called by the cross-reference setter after any custom function has failed.
	 * A failed full cycle advances the cursor once, matching the ordinary setter's
	 * extra retrieval of its first candidate.
	 */
	@Override
	public EObject selectCandidate(final EObject owner, final EReference reference,
			final BiPredicate<EObject, EReference> allowSelf) {
		final EClass type = reference.getEReferenceType();
		final var values = candidates(owner, type);
		final int size = values.size();
		if (size == 0) {
			return null;
		}
		final int start = cursors.getOrDefault(type, 0);
		final EReference opposite = reference.getEOpposite();
		final boolean bounded = opposite != null && opposite.getUpperBound() != -1;
		final NavigableSet<Integer> pool = bounded
				? active.computeIfAbsent(reference, ignored -> allIndices(size)) : null;
		if (pool != null && pool.isEmpty()) {
			cursors.put(type, (start + 1) % size);
			return null;
		}
		int index = start;
		int lastDistance = -1;
		while (true) {
			if (pool != null) {
				final Integer next = pool.ceiling(index);
				index = next != null ? next : pool.first();
			}
			final int distance = (index - start + size) % size;
			if (distance <= lastDistance) {
				break;
			}
			lastDistance = distance;
			final EObject candidate = values.get(index);
			final int nextIndex = (index + 1) % size;
			if (owner != candidate || allowSelf.test(owner, reference)) {
				if (EMFUtils.canSetInThePresenceOfOppositeReference(reference, candidate)) {
					cursors.put(type, nextIndex);
					return candidate;
				}
				// A failed opposite check implies a bounded opposite and therefore a pool.
				pool.remove(index);
				if (pool.isEmpty()) {
					break;
				}
			}
			index = nextIndex;
		}
		cursors.put(type, (start + 1) % size);
		return null;
	}

	private NavigableSet<Integer> allIndices(final int size) {
		final NavigableSet<Integer> indices = new TreeSet<>();
		for (int i = 0; i < size; i++) {
			indices.add(i);
		}
		return indices;
	}

	@Override
	public void reset() {
		candidates.clear();
		scopes.clear();
		cursors.clear();
		active.clear();
	}
}
