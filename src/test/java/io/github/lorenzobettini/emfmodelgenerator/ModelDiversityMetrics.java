package io.github.lorenzobettini.emfmodelgenerator;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Identity-independent measurements in containment-feature/name and list-index order.
 * Signatures use length-prefixed tokens, so delimiters inside values cannot collide.
 * Attribute values use their EFactory lexical representation. Non-containment references
 * are outside these measurements; these are containment trees, not graph signatures.
 *
 * @author Lorenzo Bettini
 */
final class ModelDiversityMetrics {

	/**
	 * Immutable measurements suitable for comparison after unloading a metamodel.
	 *
	 * @author Lorenzo Bettini
	 */
	record Snapshot(String values, String types, String topology, int objectCount,
			Map<String, Integer> typeCounts) {
	}

	private ModelDiversityMetrics() {
	}

	static String classifierId(final EClass type) {
		return type.getEPackage().getNsURI() + "#//" + type.getName();
	}

	static Snapshot measure(final List<? extends EObject> roots) {
		var values = new StringBuilder();
		var types = new StringBuilder();
		var topology = new StringBuilder();
		var counts = new TreeMap<String, Integer>();
		token(topology, "roots=" + roots.size());
		for (int i = 0; i < roots.size(); i++) {
			visit(roots.get(i), "/" + i, values, types, topology, counts);
		}
		var total = counts.values().stream().mapToInt(Integer::intValue).sum();
		token(topology, "objects=" + total);
		return new Snapshot(values.toString(), types.toString(), topology.toString(), total,
				Map.copyOf(counts));
	}

	private static void visit(final EObject object, final String path,
			final StringBuilder values, final StringBuilder types,
			final StringBuilder topology, final Map<String, Integer> counts) {
		var classId = classifierId(object.eClass());
		counts.merge(classId, 1, Integer::sum);
		token(types, path);
		token(types, classId);
		token(topology, path);
		for (var attribute : object.eClass().getEAllAttributes().stream()
				.sorted(Comparator.comparing(EStructuralFeature::getName)).toList()) {
			token(values, path);
			token(values, classifierId(attribute.getEContainingClass()) + "/" + attribute.getName());
			token(values, Boolean.toString(object.eIsSet(attribute)));
			var value = object.eGet(attribute);
			var elements = attribute.isMany() ? (Collection<?>) value
					: java.util.Collections.singletonList(value);
			token(values, Integer.toString(elements.size()));
			for (var element : elements) {
				var type = attribute.getEAttributeType();
				token(values, element == null ? null
						: type.getEPackage().getEFactoryInstance().convertToString(type, element));
			}
		}
		for (var reference : object.eClass().getEAllContainments().stream()
				.sorted(Comparator.comparing(EStructuralFeature::getName)).toList()) {
			var value = object.eGet(reference);
			var children = reference.isMany() ? EMFUtils.getAsEObjectsList(object, reference)
					: value == null ? List.<EObject>of() : List.of((EObject) value);
			token(topology, reference.getName());
			token(topology, Integer.toString(children.size()));
			for (int i = 0; i < children.size(); i++) {
				visit(children.get(i), path + "/" + reference.getName() + "[" + i + "]",
						values, types, topology, counts);
			}
		}
	}

	private static void token(final StringBuilder target, final String value) {
		if (value == null) {
			target.append("-1:");
		} else {
			target.append(value.length()).append(':').append(value);
		}
	}
}
