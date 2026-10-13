package io.github.lorenzobettini.emfmodelgenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/** Test-only reference for the original boxed-index algorithm. */
/**
 * @author Lorenzo Bettini
 */
public final class LegacyRoundRobinSelectors {
	private LegacyRoundRobinSelectors() {
	}

	/**
	 * @author Lorenzo Bettini
	 */
	private static final class Index<K extends ENamedElement> {
		private final Map<K, Integer> nextIndexMap = new HashMap<>();

		private <V> V next(final K key, final List<V> candidates) {
			if (candidates.isEmpty()) {
				return null;
			}
			final int currentIndex = nextIndexMap.getOrDefault(key, 0);
			final V candidate = candidates.get(currentIndex);
			final int nextIndex = (currentIndex + 1) % candidates.size();
			nextIndexMap.put(key, nextIndex);
			return candidate;
		}

		private void reset() {
			nextIndexMap.clear();
		}
	}

	/**
	 * @author Lorenzo Bettini
	 */
	public static final class EObjects extends EMFRoundRobinEObjectCandidateSelector {
		private final Index<EClass> index = new Index<>();

		@Override
		public EObject getNextCandidate(final EObject context, final EClass type) {
			return index.next(type, getOrCompute(context, type));
		}

		@Override
		public void reset() {
			super.reset();
			index.reset();
		}
	}

	/**
	 * @author Lorenzo Bettini
	 */
	public static final class EClasses extends EMFRoundRobinEClassCandidateSelector {
		private final Index<EClass> index = new Index<>();

		@Override
		public EClass getNextCandidate(final EObject context, final EClass type) {
			return index.next(type, getOrCompute(context, type));
		}

		@Override
		public void reset() {
			super.reset();
			index.reset();
		}
	}

	/**
	 * @author Lorenzo Bettini
	 */
	public static final class EEnumLiterals extends EMFRoundRobinEEnumLiteralCandidateSelector {
		private final Index<EEnum> index = new Index<>();

		@Override
		public EEnumLiteral getNextCandidate(final EObject context, final EEnum type) {
			return index.next(type, getOrCompute(context, type));
		}

		@Override
		public void reset() {
			super.reset();
			index.reset();
		}
	}

	/**
	 * @author Lorenzo Bettini
	 */
	public static final class FeatureMapMembers
			extends EMFRoundRobinFeatureMapGroupMemberSelector {
		private final Index<EAttribute> index = new Index<>();

		@Override
		public EStructuralFeature getNextCandidate(final EObject context, final EAttribute type) {
			return index.next(type, getOrCompute(context, type));
		}

		@Override
		public void reset() {
			super.reset();
			index.reset();
		}
	}
}
