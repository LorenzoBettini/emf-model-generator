package io.github.lorenzobettini.emfmodelgenerator;

import java.util.Collection;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;

/**
 * Validates generated EMF model roots.
 *
 * <p>Implementations may own resources and release them from {@link #close()}.</p>
 */
public interface EMFModelValidator extends AutoCloseable {

	/**
	 * Factory for validators used by an {@link EMFModelGenerator}.
	 *
	 * <p>The generator supplies its exact {@link ResourceSet} as validation context.
	 * Implementations that depend on resource-set-local state may use it; validators that
	 * do not require such context may ignore it. The model roots to validate are supplied
	 * separately through {@link #validate(EObject)} or {@link #validateAll(Collection)}.</p>
	 */
	@FunctionalInterface
	interface Factory {
		/**
		 * Creates a validator.
		 *
		 * @param resourceSet the generator's non-null resource set, available as context
		 *        for validators that need resource-set-local state
		 * @return a non-null validator
		 */
		EMFModelValidator create(ResourceSet resourceSet);
	}

	/**
	 * Creates a validator using standard EMF validation.
	 *
	 * @return a standard EMF validator
	 */
	static EMFModelValidator standard() {
		return new EMFStandardModelValidator();
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
	 * @throws EMFValidationException if validation is not valid
	 */
	default void validateOrThrow(final EObject root) {
		var result = validate(root);
		if (!result.isValid()) {
			throw new EMFValidationException(result);
		}
	}

	/**
	 * Validates all roots and throws if the aggregate result is invalid.
	 *
	 * @param roots the non-null roots to validate
	 * @throws EMFValidationException if validation is not valid
	 */
	default void validateAllOrThrow(final Collection<? extends EObject> roots) {
		var result = validateAll(roots);
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
