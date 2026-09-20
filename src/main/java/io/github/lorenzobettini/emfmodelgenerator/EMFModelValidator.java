package io.github.lorenzobettini.emfmodelgenerator;

import java.util.Collection;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;

/**
 * Validates generated EMF model roots.
 *
 * <p>Implementations may own resources and release them from {@link #close()}.</p>
 */
public interface EMFModelValidator extends AutoCloseable {

	/**
	 * Creates a validator for a generator's exact resource set.
	 */
	@FunctionalInterface
	interface Factory {
		/**
		 * Creates a validator.
		 *
		 * @param resourceSet the resource set containing the models to validate
		 * @return a validator for the resource set
		 */
		EMFModelValidator create(ResourceSet resourceSet);
	}

	/**
	 * Validates one model root.
	 *
	 * @param root the root to validate
	 * @return a non-null validation result
	 */
	EMFValidationResult validate(EObject root);

	/**
	 * Validates every supplied model root.
	 *
	 * @param roots the roots to validate
	 * @return a non-null aggregate validation result
	 */
	EMFValidationResult validateAll(Collection<? extends EObject> roots);

	/**
	 * Validates one root and throws if it is invalid.
	 *
	 * @param root the non-null root to validate
	 * @throws NullPointerException if the root or implementation result is {@code null}
	 * @throws EMFValidationException if validation is not valid
	 */
	default void validateOrThrow(final EObject root) {
		var result = Objects.requireNonNull(validate(Objects.requireNonNull(root, "root")),
				"Validator returned a null result");
		if (!result.isValid()) {
			throw new EMFValidationException(result);
		}
	}

	/**
	 * Validates all roots and throws if the aggregate result is invalid.
	 *
	 * @param roots the non-null roots to validate
	 * @throws NullPointerException if the collection or implementation result is {@code null}
	 * @throws EMFValidationException if validation is not valid
	 */
	default void validateAllOrThrow(final Collection<? extends EObject> roots) {
		var result = Objects.requireNonNull(validateAll(Objects.requireNonNull(roots, "roots")),
				"Validator returned a null result");
		if (!result.isValid()) {
			throw new EMFValidationException(result);
		}
	}

	/**
	 * Releases implementation resources. The default implementation does nothing.
	 */
	@Override
	default void close() {
		// No resources are owned by default.
	}
}
