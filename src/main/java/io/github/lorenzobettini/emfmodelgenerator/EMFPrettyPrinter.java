package io.github.lorenzobettini.emfmodelgenerator;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

/**
 * Pretty-prints EObjects, Resources, and ResourceSets as a tree structure.
 * 
 * <p>Cross-references within the same resource show only the containment path,
 * while cross-references to objects in different resources include the resource name.
 * 
 * <p>Example output for same-resource reference:
 * <pre>{@code
 * Library
 *   name: "City Library"
 *   books (1):
 *     [0] Book
 *       title: "1984"
 *       authors (opposite) (1):
 *         [0] -> Author (authors[0])
 *   authors (1):
 *     [0] Author
 *       name: "Orwell"
 * }</pre>
 * 
 * <p>Example output for cross-resource reference:
 * <pre>{@code
 * Library
 *   name: "Central Library"
 *   books (1):
 *     [0] Book
 *       title: "Brave New World"
 *       authors (opposite) (1):
 *         [0] -> Author (other_Library.xmi > authors[0])
 * }</pre>
 */
public class EMFPrettyPrinter {

	private static final String INDENT = "  ";
	private static final String OPPOSITE_MARKER = " (opposite)";

	/**
	 * Pretty-prints a single EObject.
	 * 
	 * @param eObject the EObject to print
	 * @return the pretty-printed string representation
	 */
	public String prettyPrint(final EObject eObject) {
		final var sb = new StringBuilder();
		printEObject(eObject, sb, 0);
		return sb.toString();
	}

	/**
	 * Pretty-prints a Resource.
	 * 
	 * @param resource the Resource to print
	 * @return the pretty-printed string representation
	 */
	public String prettyPrint(final Resource resource) {
		final var sb = new StringBuilder();
		sb.append("Resource: ").append(getResourceName(resource)).append("\n");
		for (final EObject root : resource.getContents()) {
			printEObject(root, sb, 1);
		}
		return sb.toString();
	}

	/**
	 * Pretty-prints a ResourceSet.
	 * 
	 * @param resourceSet the ResourceSet to print
	 * @return the pretty-printed string representation
	 */
	public String prettyPrint(final ResourceSet resourceSet) {
		final var sb = new StringBuilder();
		for (final Resource resource : resourceSet.getResources()) {
			sb.append("Resource: ").append(getResourceName(resource)).append("\n");
			for (final EObject root : resource.getContents()) {
				printEObject(root, sb, 1);
			}
		}
		return sb.toString();
	}

	private void printEObject(final EObject eObject, final StringBuilder sb, final int depth) {
		indent(sb, depth);
		sb.append(eObject.eClass().getName());
		sb.append("\n");

		for (final EStructuralFeature feature : eObject.eClass().getEAllStructuralFeatures()) {
			if (!eObject.eIsSet(feature)) {
				continue;
			}

			if (feature instanceof EAttribute attribute) {
				printAttribute(eObject, attribute, sb, depth + 1);
			} else {
				final var ref = (EReference) feature;
				if (ref.isContainment()) {
					printContainmentReference(eObject, ref, sb, depth + 1);
				} else {
					printCrossReference(eObject, ref, sb, depth + 1);
				}
			}
		}
	}

	private void printAttribute(final EObject eObject, final EAttribute attribute, final StringBuilder sb, final int depth) {
		final var value = eObject.eGet(attribute);
		
		if (attribute.isMany()) {
			final var values = (List<?>) value;
			indent(sb, depth);
			sb.append(attribute.getName()).append(" (").append(values.size()).append("):");
			for (final Object val : values) {
				sb.append("\n");
				indent(sb, depth + 1);
				sb.append(formatValue(val));
			}
			sb.append("\n");
		} else {
			indent(sb, depth);
			sb.append(attribute.getName()).append(": ").append(formatValue(value)).append("\n");
		}
	}

	private void printContainmentReference(final EObject eObject, final EReference reference, final StringBuilder sb, final int depth) {
		final var value = eObject.eGet(reference);
		
		if (reference.isMany()) {
			final var values = (List<?>) value;
			indent(sb, depth);
			sb.append(reference.getName());
			sb.append(" (").append(values.size()).append("):\n");
			
			for (int i = 0; i < values.size(); i++) {
				indent(sb, depth + 1);
				sb.append("[").append(i).append("] ");
				final var containedObject = (EObject) values.get(i);
				sb.append(containedObject.eClass().getName()).append("\n");
				printFeaturesOfEObject(containedObject, sb, depth + 2);
			}
		} else {
			indent(sb, depth);
			sb.append(reference.getName());
			sb.append(":\n");
			indent(sb, depth + 1);
			final var containedObject = (EObject) value;
			sb.append(containedObject.eClass().getName()).append("\n");
			printFeaturesOfEObject(containedObject, sb, depth + 2);
		}
	}

	private void printCrossReference(final EObject eObject, final EReference reference, final StringBuilder sb, final int depth) {
		final var value = eObject.eGet(reference);
		
		if (reference.isMany()) {
			final var values = (List<?>) value;
			indent(sb, depth);
			sb.append(reference.getName());
			if (reference.getEOpposite() != null) {
				sb.append(OPPOSITE_MARKER);
			}
			sb.append(" (").append(values.size()).append("):\n");
			
			for (int i = 0; i < values.size(); i++) {
				final var referencedObject = (EObject) values.get(i);
				indent(sb, depth + 1);
				sb.append("[").append(i).append("] -> ");
				sb.append(formatCrossReference(eObject, referencedObject)).append("\n");
			}
		} else {
			indent(sb, depth);
			sb.append(reference.getName());
			sb.append(" -> ");
			final var referencedObject = (EObject) value;
			sb.append(formatCrossReference(eObject, referencedObject)).append("\n");
		}
	}

	private void printFeaturesOfEObject(final EObject eObject, final StringBuilder sb, final int depth) {
		for (final EStructuralFeature feature : eObject.eClass().getEAllStructuralFeatures()) {
			if (!eObject.eIsSet(feature)) {
				continue;
			}

			if (feature instanceof EAttribute attribute) {
				printAttribute(eObject, attribute, sb, depth);
			} else {
				final var ref = (EReference) feature;
				if (ref.isContainment()) {
					printContainmentReference(eObject, ref, sb, depth);
				} else {
					printCrossReference(eObject, ref, sb, depth);
				}
			}
		}
	}

	private String formatCrossReference(final EObject contextObject, final EObject referencedObject) {
		final var sb = new StringBuilder();
		sb.append(referencedObject.eClass().getName());
		sb.append(" (");
		final var sameResource = contextObject.eResource() == referencedObject.eResource();
		sb.append(getContainmentPath(referencedObject, sameResource));
		sb.append(")");
		return sb.toString();
	}

	private String getContainmentPath(final EObject eObject, final boolean sameResource) {
		final var pathElements = new ArrayList<String>();
		
		// Add resource name if the object is in a different resource
		if (!sameResource && eObject.eResource() != null) {
			pathElements.add(getResourceName(eObject.eResource()));
		}
		
		// Build containment path by walking up the eContainer chain
		final var pathParts = new ArrayList<String>();
		var current = eObject;
		
		while (current.eContainmentFeature() != null) {
			final var feature = current.eContainmentFeature();
			final var parent = current.eContainer();
			
			if (feature.isMany()) {
				final var siblings = (EList<?>) parent.eGet(feature);
				final var index = siblings.indexOf(current);
				pathParts.add(0, feature.getName() + "[" + index + "]");
			} else {
				pathParts.add(0, feature.getName());
			}
			
			current = parent;
		}
		
		pathElements.addAll(pathParts);
		return String.join(" > ", pathElements);
	}

	private String getResourceName(final Resource resource) {
		return resource.getURI().lastSegment();
	}

	private String formatValue(final Object value) {
		if (value instanceof String) {
			return "\"" + value + "\"";
		}
		return String.valueOf(value);
	}

	private void indent(final StringBuilder sb, final int depth) {
		for (int i = 0; i < depth; i++) {
			sb.append(INDENT);
		}
	}
}
