package io.github.lorenzobettini.emfmodelgenerator.performance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.FeatureMap;

/**
 * Stable resource-order, feature-order and reference-target signature for A/B checks.
 *
 * @author Lorenzo Bettini
 */
final class GraphFingerprint {
	private GraphFingerprint() {
	}

	static String compute(final ResourceSet resourceSet) {
		return compute(resourceSet, true);
	}

	static String computeLogical(final ResourceSet resourceSet) {
		return compute(resourceSet, false);
	}

	private static String compute(final ResourceSet resourceSet, final boolean physical) {
		final MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		final var objects = new ArrayList<EObject>();
		final var indexes = new IdentityHashMap<EObject, Integer>();
		for (final Resource resource : resourceSet.getResources()) {
			if ("ecore".equals(resource.getURI().fileExtension())) {
				continue;
			}
			if (physical) {
				add(digest, "resource");
				add(digest, resource.getURI().lastSegment());
				add(digest, Integer.toString(resource.getContents().size()));
			}
			final var iterator = resource.getAllContents();
			while (iterator.hasNext()) {
				final var object = iterator.next();
				indexes.put(object, objects.size());
				objects.add(object);
			}
		}
		for (final var object : objects) {
			add(digest, object.eClass().getEPackage().getNsURI());
			add(digest, object.eClass().getName());
			for (final var feature : object.eClass().getEAllStructuralFeatures()) {
				add(digest, feature.getName());
				add(digest, Boolean.toString(object.eIsSet(feature)));
				value(digest, indexes, object.eGet(feature, false));
			}
		}
		return java.util.HexFormat.of().formatHex(digest.digest());
	}

	private static void value(final MessageDigest digest,
			final IdentityHashMap<EObject, Integer> indexes, final Object value) {
		if (value == null) {
			add(digest, "null");
		} else if (value instanceof EObject object) {
			final var index = indexes.get(object);
			add(digest, index == null ? "external:" + EcoreUtil.getURI(object) : "object:" + index);
		} else if (value instanceof FeatureMap.Entry entry) {
			add(digest, "entry:" + entry.getEStructuralFeature().getName());
			value(digest, indexes, entry.getValue());
		} else if (value instanceof List<?> list) {
			add(digest, "list:" + list.size());
			for (final var member : list) {
				value(digest, indexes, member);
			}
		} else {
			add(digest, value.getClass().getName() + ":" + value);
		}
	}

	private static void add(final MessageDigest digest, final String text) {
		final var bytes = String.valueOf(text).getBytes(StandardCharsets.UTF_8);
		digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
		digest.update(bytes);
	}
}
