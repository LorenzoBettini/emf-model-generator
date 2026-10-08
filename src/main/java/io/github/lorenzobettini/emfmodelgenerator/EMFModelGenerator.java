package io.github.lorenzobettini.emfmodelgenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;

import io.github.lorenzobettini.emfmodelgenerator.EMFModelValidator.Factory;

/**
 * Main entry point for programmatically generating EMF model instances.
 * 
 * <p>This class provides methods to generate model instances from Ecore metamodels,
 * with support for automatic attribute population, reference handling, and XMI serialization.
 * 
 * <p><b>Basic Usage:</b>
 * {@snippet :
 * EMFModelGenerator generator = new EMFModelGenerator();
 * EPackage ePackage = generator.loadEcoreModel("model.ecore");
 * EClass personClass = (EClass) ePackage.getEClassifier("Person");
 * 
 * generator.setOutputDirectory("output/");
 * EObject person = generator.generateFrom(personClass);
 * generator.save();
 * 
 * generator.unloadEcoreModels(); // Clean up
 * }
 * 
 * <p><b>Loading Ecore Models:</b> Use {@link #loadEcoreModel(String)} to load the first package
 * from an Ecore file, or {@link #loadEcoreModelPackages(String)} to access every top-level and
 * nested package. These methods automatically register the resource factory and package registry
 * entries.
 * Call {@link #unloadEcoreModels()} when done to clean up resources and registry entries.
 * 
 * <p><b>Generation Methods:</b>
 * <ul>
 * <li>{@link #generateFrom(EClass)} - Generate a single instance from an EClass</li>
 * <li>{@link #generateFrom(EPackage)} - Generate from the first instantiable EClass in a package</li>
 * <li>{@link #generateFromSeveral(EClass...)} - Generate instances from multiple EClasses</li>
 * <li>{@link #generateAllFrom(EPackage)} - Generate instances of all EClasses in a package</li>
 * <li>{@link #generateAllFrom(EClass)} - Generate instances of all subclasses of an EClass</li>
 * </ul>
 * 
 * <p><b>Customization:</b> For population-related customization (custom setters, per-feature
 * functions, multiplicities, depth, self-reference policies, etc.), obtain the
 * {@link EMFInstancePopulator} via {@link #getInstancePopulator()} and configure it directly.
 *
 * <p><b>Post-generation validation:</b> Generation fills features where suitable values are
 * available. In particular, a required non-containment reference can remain unset when the
 * generated population has no assignable target. Use {@link #validate()} to inspect and handle the
 * validation result:
 * {@snippet :
 * generator.generateFrom(personClass);
 * EMFValidationResult result = generator.validate();
 * if (!result.isValid()) {
 *     result.rejectedDiagnostics().forEach(diagnostic ->
 *         System.err.println(diagnostic.getMessage()));
 * }
 * }
 * Alternatively, use {@link #validateOrThrow()} when an invalid candidate should stop the
 * workflow:
 * {@snippet :
 * generator.generateFrom(personClass);
 * generator.validateOrThrow();
 * }
 * Validation before saving is optional and disabled by default. Enabling it prevents any resource
 * from being serialized when validation fails:
 * {@snippet :
 * generator.enableValidationBeforeSave();
 * generator.save();
 * }
 * To validate the serialized form instead, save and reload the generated resources in a fresh
 * resource set:
 * {@snippet :
 * EMFValidationResult roundTripResult = generator.saveAndValidateRoundTrip();
 * EMFValidationResult customRoundTripResult = generator.saveAndValidateRoundTrip(
 *     options, resourceSet -> new MyProjectModelValidator(resourceSet));
 * }
 * A custom implementation can be supplied through {@link EMFModelValidator.Factory}:
 * {@snippet :
 * EMFModelValidator.Factory factory = resourceSet ->
 *     new MyProjectModelValidator(resourceSet);
 * EMFValidationResult customResult = generator.validate(factory);
 * generator.enableValidationBeforeSave(factory);
 * }
 * 
 * @see #loadEcoreModel(String)
 * @see #loadEcoreModelPackages(String)
 * @see #unloadEcoreModels()
 * @see #generateFrom(EClass)
 * @see #save()
 * @see #saveAndValidateRoundTrip()
 *
 * @author Lorenzo Bettini
 */
public class EMFModelGenerator {

	private static final EMFModelValidator.Factory STANDARD_VALIDATOR_FACTORY =
			ignored -> EMFModelValidator.standard();

	private String outputDirectory = "target/test-output";
	private final ResourceSet sharedResourceSet;
	private EMFResourceHelper resourceHelper;
	private EMFInstancePopulator instancePopulator = new EMFInstancePopulator();
	private int numberOfInstances = 1;
	private final List<Resource> loadedEcoreResources = new ArrayList<>();
	private final List<PackageRegistration> loadedEcoreRegistrations = new ArrayList<>();
	private EMFModelValidator.Factory validationBeforeSaveFactory;

	private record PackageRegistration(
			EPackage.Registry registry, String nsURI, EPackage ePackage) {
		void removeIfOwned() {
			registry.remove(nsURI, ePackage);
		}
	}

	/**
	 * Create a new EMFModelGenerator with default settings.
	 * A new ResourceSet will be created and reused for all generation calls.
	 */
	public EMFModelGenerator() {
		this.sharedResourceSet = EMFResourceSetHelper.createResourceSet();
		this.resourceHelper = new EMFResourceHelper(sharedResourceSet, outputDirectory);
	}

	/**
	 * Create a new EMFModelGenerator with an external ResourceSet.
	 * The provided ResourceSet will be reused across multiple generation calls,
	 * and state (generated instances, counters) will be preserved between calls.
	 * 
	 * <p>Note: When using this constructor, you may still use {@link #loadEcoreModel(String)} or
	 * {@link #loadEcoreModelPackages(String)}, which automatically register the
	 * EcoreResourceFactoryImpl if needed.
	 * 
	 * <p>Example usage:
	 * {@snippet :
	 * ResourceSet resourceSet = new ResourceSetImpl();
	 * resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
	 *     .put("xmi", new XMIResourceFactoryImpl());
	 * 
	 * EMFModelGenerator generator = new EMFModelGenerator(resourceSet);
	 * EPackage ePackage = generator.loadEcoreModel("model.ecore");
	 * EClass personClass = (EClass) ePackage.getEClassifier("Person");
	 * 
	 * generator.setOutputDirectory("output");
	 * 
	 * // First generation
	 * EObject person1 = generator.generateFrom(personClass);
	 * 
	 * // Second generation - reuses same ResourceSet and can reference
	 * // instances from first generation
	 * EObject person2 = generator.generateFrom(personClass);
	 * 
	 * // All resources are in the same ResourceSet
	 * generator.save(); // Saves all generated resources
	 * 
	 * generator.unloadEcoreModels(); // Clean up
	 * }
	 * 
	 * @param resourceSet the ResourceSet to use for all generations
	 */
	public EMFModelGenerator(final ResourceSet resourceSet) {
		this.sharedResourceSet = resourceSet;
		this.resourceHelper = new EMFResourceHelper(sharedResourceSet, outputDirectory);
	}

	public void setOutputDirectory(String outputDirectory) {
		this.outputDirectory = outputDirectory;
		// Recreate resource helper with new output directory
		this.resourceHelper = new EMFResourceHelper(sharedResourceSet, outputDirectory);
	}

	/**
	 * Set a prefix to be added to all generated file names.
	 * @param filePrefix
	 */
	public void setFilePrefix(String filePrefix) {
		resourceHelper.setFilePrefix(filePrefix);
	}

	/**
	 * Set the global file extension to be used for all generated files
	 * when no specific extension is defined for an EClass or EPackage.
	 * 
	 * @param extension the file extension (without the dot)
	 */
	public void setGlobalFileExtension(String extension) {
		resourceHelper.setGlobalFileExtension(extension);
	}

	/**
	 * Set a custom file extension for a specific EClass.
	 * This takes precedence over EPackage and global extensions.
	 * 
	 * @param eClass the EClass
	 * @param extension the file extension (without the dot)
	 */
	public void setFileExtensionForEClass(EClass eClass, String extension) {
		resourceHelper.setFileExtensionForEClass(eClass, extension);
	}

	/**
	 * Set a custom file extension for all EClasses in a specific EPackage.
	 * This takes precedence over the global extension but is overridden by EClass-specific extensions.
	 * 
	 * @param ePackage the EPackage
	 * @param extension the file extension (without the dot)
	 */
	public void setFileExtensionForEPackage(EPackage ePackage, String extension) {
		resourceHelper.setFileExtensionForEPackage(ePackage, extension);
	}

	/**
	 * Set the number of instances to generate per EClass.
	 * @param numberOfInstances
	 */
	public void setNumberOfInstances(int numberOfInstances) {
		this.numberOfInstances = numberOfInstances;
	}

	/**
	 * Get the {@link EMFInstancePopulator} used to populate generated instances.
	 * Use this to configure population behavior such as custom setters, functions,
	 * multiplicities, depth limits, and self-reference policies.
	 *
	 * <p>Example:
	 * {@snippet :
	 * generator.getInstancePopulator().setMaxDepth(3);
	 * generator.getInstancePopulator().setContainmentReferenceDefaultMaxCount(4);
	 * generator.getInstancePopulator().setAttributeSetter(myCustomSetter);
	 * }
	 *
	 * @return the instance populator
	 */
	public final EMFInstancePopulator getInstancePopulator() {
		return instancePopulator;
	}

	/**
	 * Register the given package in the provided ResourceSet and in the global
	 * EPackage registry when it has a nonblank namespace URI. This method is
	 * idempotent and safe to call multiple times.
	 */
	private void registerPackage(ResourceSet rs, EPackage pkg) {
		String nsURI = pkg.getNsURI();
		if (!EMFUtils.hasUsableNsURI(pkg)) {
			return;
		}
		rs.getPackageRegistry().computeIfAbsent(nsURI, k -> pkg);
		EPackage.Registry.INSTANCE.computeIfAbsent(nsURI, k -> pkg);
	}

	private void registerLoadedPackage(final EPackage ePackage) {
		final var nsURI = ePackage.getNsURI();
		if (!EMFUtils.hasUsableNsURI(ePackage)) {
			return;
		}
		registerLoadedPackage(sharedResourceSet.getPackageRegistry(), nsURI, ePackage);
		registerLoadedPackage(EPackage.Registry.INSTANCE, nsURI, ePackage);
	}

	private void registerLoadedPackage(final EPackage.Registry registry, final String nsURI,
			final EPackage ePackage) {
		registry.computeIfAbsent(nsURI, ignored -> {
			loadedEcoreRegistrations.add(new PackageRegistration(registry, nsURI, ePackage));
			return ePackage;
		});
	}

	public String getOutputDirectory() {
		return outputDirectory;
	}

	/**
	 * Get the ResourceSet currently in use.
	 * This may be null if no generation has been performed yet with the default constructor.
	 * 
	 * @return the ResourceSet currently in use, or null if not yet initialized
	 */
	public ResourceSet getResourceSet() {
		return sharedResourceSet;
	}

	/**
	 * Load the first package from an Ecore model at the given file path.
	 * This method:
	 * <ul>
	 * <li>Registers the EcoreResourceFactoryImpl if not already registered</li>
	 * <li>Loads the Ecore file into the shared ResourceSet</li>
	 * <li>Registers discovered EPackages with nonblank namespace URIs in the shared and
	 * global registries</li>
	 * <li>Tracks the loaded resource and owned registrations for cleanup via
	 * {@link #unloadEcoreModels()}</li>
	 * </ul>
	 * 
	 * <p>The first package in resource and depth-first subpackage traversal order is returned.
	 * Use {@link #loadEcoreModelPackages(String)} when access to all packages is required.
	 * 
	 * <p>Example usage:
	 * {@snippet :
	 * EMFModelGenerator generator = new EMFModelGenerator();
	 * EPackage myPackage = generator.loadEcoreModel("models/mymodel.ecore");
	 * EClass myClass = (EClass) myPackage.getEClassifier("MyClass");
	 * EObject instance = generator.generateFrom(myClass);
	 * generator.save();
	 * generator.unloadEcoreModels(); // Clean up
	 * }
	 * 
	 * @param ecoreFilePath the path to the Ecore file (absolute or relative)
	 * @return the first loaded EPackage
	 * @throws IOException if the file cannot be loaded or has no top-level EPackage
	 */
	public EPackage loadEcoreModel(String ecoreFilePath) throws IOException {
		return loadEcoreModelPackages(ecoreFilePath).getFirst();
	}

	/**
	 * Load all packages contained in one Ecore resource.
	 *
	 * <p>Top-level packages are inspected in resource order. Each package is followed by its
	 * subpackages recursively in their natural order. Every package with a nonblank namespace URI
	 * is registered without replacing existing entries in either the shared ResourceSet registry or
	 * the global registry. The returned list is immutable.
	 *
	 * @param ecoreFilePath the path to the Ecore file (absolute or relative)
	 * @return all loaded top-level and nested EPackages in deterministic traversal order
	 * @throws IOException if the file cannot be loaded or has no top-level EPackage
	 */
	public List<EPackage> loadEcoreModelPackages(final String ecoreFilePath) throws IOException {
		// Register EcoreResourceFactoryImpl if not already registered
		if (!sharedResourceSet.getResourceFactoryRegistry()
				.getExtensionToFactoryMap().containsKey("ecore")) {
			sharedResourceSet.getResourceFactoryRegistry()
				.getExtensionToFactoryMap()
				.put("ecore", new EcoreResourceFactoryImpl());
		}

		// Load the Ecore file
		File ecoreFile = new File(ecoreFilePath);
		URI uri = URI.createFileURI(ecoreFile.getAbsolutePath());
		final Resource existingResource = sharedResourceSet.getResource(uri, false);
		final Resource resource;
		try {
			resource = sharedResourceSet.getResource(uri, true);
		} catch (Exception e) {
			throw new IOException("Failed to load Ecore file: " + ecoreFilePath, e);
		}

		final var packages = new ArrayList<EPackage>();
		for (var root : resource.getContents()) {
			if (root instanceof EPackage ePackage) {
				collectPackages(ePackage, packages);
			}
		}

		if (packages.isEmpty()) {
			if (existingResource == null) {
				resource.unload();
				sharedResourceSet.getResources().remove(resource);
			}
			throw new IOException("Ecore file contains no top-level EPackage: " + ecoreFilePath);
		}

		packages.forEach(this::registerLoadedPackage);
		if (existingResource == null) {
			loadedEcoreResources.add(resource);
		}
		return List.copyOf(packages);
	}

	private void collectPackages(final EPackage ePackage, final List<EPackage> packages) {
		packages.add(ePackage);
		for (var subpackage : ePackage.getESubpackages()) {
			collectPackages(subpackage, packages);
		}
	}

	/**
	 * Unload all Ecore models loaded via {@link #loadEcoreModel(String)} or
	 * {@link #loadEcoreModelPackages(String)}.
	 * This method:
	 * <ul>
	 * <li>Unloads the Ecore resources from the shared ResourceSet</li>
	 * <li>Removes only package registrations inserted by this generator</li>
	 * <li>Clears the tracking lists</li>
	 * </ul>
	 * 
	 * <p>This method is safe to call multiple times. It only affects Ecore models
	 * loaded through these loading methods, not other packages or resources.
	 */
	public void unloadEcoreModels() {
		// Unload resources from the ResourceSet
		for (Resource resource : loadedEcoreResources) {
			resource.unload();
			sharedResourceSet.getResources().remove(resource);
		}
		
		// Remove only entries that still contain the packages registered by this generator
		for (PackageRegistration registration : loadedEcoreRegistrations) {
			registration.removeIfOwned();
		}
		
		// Clear tracking lists
		loadedEcoreResources.clear();
		loadedEcoreRegistrations.clear();
	}

	public EObject generateFrom(EPackage ePackage) {
		// Search for the first instantiable EClass in the package
		for (var classifier : ePackage.getEClassifiers()) {
			if (classifier instanceof EClass eClass && EMFUtils.canBeInstantiated(eClass)) {
				return generate(eClass).get(0);
			}
		}
		// No instantiable EClass found in the package
		throw new IllegalArgumentException(
				"No instantiable EClass found in EPackage: " + ePackage.getName());
	}

	public EObject generateFrom(EClass eClass) {
		if (!EMFUtils.canBeInstantiated(eClass)) {
			throw new IllegalArgumentException(
					"Cannot instantiate EClass: " + eClass.getName() + 
					" (it is " + (eClass.isAbstract() ? "abstract" : "an interface") + ")");
		}
		List<EObject> models = generate(eClass);
		return models.get(0);
	}

	public List<EObject> generateFromSeveral(EClass... eClasses) {
		// Check which EClasses cannot be instantiated
		List<String> invalidClasses = new ArrayList<>();
		for (EClass eClass : eClasses) {
			if (!EMFUtils.canBeInstantiated(eClass)) {
				String reason = eClass.isAbstract() ? "abstract" : "an interface";
				invalidClasses.add(eClass.getName() + " (it is " + reason + ")");
			}
		}
		
		// If any invalid classes found, throw exception
		if (!invalidClasses.isEmpty()) {
			throw new IllegalArgumentException(
					"Cannot instantiate the following EClasses: " + String.join(", ", invalidClasses));
		}
		
		return generate(eClasses);
	}

	public List<EObject> generateAllFrom(EPackage ePackage) {
		// Collect all instantiable EClasses from the package
		var instantiableClasses = ePackage.getEClassifiers().stream()
				.filter(classifier -> classifier instanceof EClass eClass &&
						EMFUtils.canBeInstantiated(eClass))
				.map(EClass.class::cast)
				.toList();
		
		// If no instantiable classes found, throw exception
		if (instantiableClasses.isEmpty()) {
			throw new IllegalArgumentException(
					"No instantiable EClass found in EPackage: " + ePackage.getName());
		}
		
		// Generate instances for all instantiable classes
		return generate(instantiableClasses.toArray(new EClass[0]));
	}

	public List<EObject> generateAllFrom(EClass eClass) {
		// Find all instantiable subclasses (including the EClass itself if instantiable)
		var instantiableSubclasses = EMFUtils.findAllInstantiableSubclasses(eClass);
		
		// If no instantiable classes found, throw exception
		if (instantiableSubclasses.isEmpty()) {
			throw new IllegalArgumentException(
					"No instantiable EClass found for EClass: " + eClass.getName());
		}
		
		// Generate instances for all instantiable subclasses
		return generate(instantiableSubclasses.toArray(new EClass[0]));
	}

	private List<EObject> generate(EClass... eClasses) {
		// Register all involved packages in the shared ResourceSet and globally
		for (EClass eClass : eClasses) {
			EPackage pkg = eClass.getEPackage();
			registerPackage(sharedResourceSet, pkg);
		}

		// Generate instances for each EClass using the shared ResourceSet
		List<EObject> generatedModels = new ArrayList<>();
		for (EClass eClass : eClasses) {
			for (int i = 0; i < numberOfInstances; i++) {
				EObject model = generateModel(eClass);
				generatedModels.add(model);
			}
		}

		// populate all generated models together to set attributes and references correctly
		instancePopulator.populateEObjects(generatedModels.toArray(new EObject[0]));

		return generatedModels;
	}

	private EObject generateModel(EClass eClass) {
		// Create resource using helper
		Resource resource = resourceHelper.createResource(eClass);

		// Create root instance and add it to the resource
		EObject rootInstance = EcoreUtil.create(eClass);
		resource.getContents().add(rootInstance);

		return rootInstance;
	}

	/**
	 * Performs post-generation validation of all model roots using standard EMF validation.
	 *
	 * <p>This method validates the current in-memory objects. It does not save or reload them.
	 * Use {@link #saveAndValidateRoundTrip()} to validate the serialized form.</p>
	 *
	 * <p>Validation covers every root in every non-Ecore resource in this generator's
	 * resource set. When an external {@link ResourceSet} was supplied, this includes
	 * its existing non-Ecore resources, matching the scope of {@link #save()}.</p>
	 *
	 * @return the aggregate validation result
	 */
	public EMFValidationResult validate() {
		return validate(STANDARD_VALIDATOR_FACTORY);
	}

	/**
	 * Validates all model roots using a validator created for this generator's exact
	 * resource set.
	 *
	 * <p>Validation covers the current in-memory roots in every non-Ecore resource in this
	 * generator's resource set, preserving resource and root order. It does not save or reload
	 * them.</p>
	 *
	 * @param validatorFactory the factory used to create one validator for this call
	 * @return the aggregate validation result
	 * @throws NullPointerException if the factory, validator, or result is {@code null}
	 */
	public EMFValidationResult validate(final EMFModelValidator.Factory validatorFactory) {
		return validateRoots(validatorFactory, sharedResourceSet, modelRoots());
	}

	private static EMFValidationResult validateRoots(
			final EMFModelValidator.Factory validatorFactory,
			final ResourceSet resourceSet,
			final Collection<? extends EObject> roots) {
		requireValidatorFactoryNonNull(validatorFactory);
		try (var validator = Objects.requireNonNull(validatorFactory.create(resourceSet),
				"Validator factory returned null")) {
			return Objects.requireNonNull(validator.validateAll(roots),
					"Validator returned a null result");
		}
	}

	/**
	 * Validates all model roots with standard EMF validation and throws when invalid.
	 * This checks the current in-memory objects and does not save or reload them.
	 *
	 * @throws EMFValidationException if validation is not valid
	 */
	public void validateOrThrow() {
		validateOrThrow(STANDARD_VALIDATOR_FACTORY);
	}

	/**
	 * Validates all current in-memory model roots with a custom validator and throws when invalid.
	 * This method does not save or reload them.
	 *
	 * @param validatorFactory the factory used to create one validator for this call
	 * @throws NullPointerException if the factory, validator, or result is {@code null}
	 * @throws EMFValidationException if validation is not valid
	 */
	public void validateOrThrow(final EMFModelValidator.Factory validatorFactory) {
		var result = validate(validatorFactory);
		if (!result.isValid()) {
			throw new EMFValidationException(result);
		}
	}

	private Collection<Resource> modelResources() {
		return sharedResourceSet.getResources().stream()
				.filter(resource -> !EMFUtils.isEcoreResource(resource))
				.toList();
	}

	private List<EObject> modelRoots() {
		return modelRoots(modelResources());
	}

	private List<EObject> modelRoots(Collection<Resource> resources) {
		return resources.stream()
				.flatMap(resource -> resource.getContents().stream())
				.toList();
	}

	/**
	 * Enables standard EMF validation before each save operation.
	 *
	 * <p>Validation occurs before the output directory is created or any resource is
	 * serialized. An invalid result causes {@link EMFValidationException} to be thrown.
	 * Validation before saving is disabled by default.</p>
	 */
	public void enableValidationBeforeSave() {
		enableValidationBeforeSave(STANDARD_VALIDATOR_FACTORY);
	}

	/**
	 * Enables validation with a custom validator factory before each save operation.
	 *
	 * @param validatorFactory the factory used by subsequent save operations
	 * @throws NullPointerException if {@code validatorFactory} is {@code null}
	 */
	public void enableValidationBeforeSave(final EMFModelValidator.Factory validatorFactory) {
		validationBeforeSaveFactory = requireValidatorFactoryNonNull(validatorFactory);
	}

	private static Factory requireValidatorFactoryNonNull(final EMFModelValidator.Factory validatorFactory) {
		return Objects.requireNonNull(validatorFactory, "validatorFactory");
	}

	/**
	 * Disables validation before saving, restoring the default save behavior.
	 */
	public void disableValidationBeforeSave() {
		validationBeforeSaveFactory = null;
	}

	/**
	 * Reports whether validation before saving is enabled.
	 *
	 * @return {@code true} when subsequent saves validate before serialization
	 */
	public boolean isValidationBeforeSaveEnabled() {
		return validationBeforeSaveFactory != null;
	}

	/**
	 * Save all generated models to XMI files.
	 * The file names are determined by the resources created during generation.
	 * Ecore files are automatically skipped.
	 *
	 * @throws IOException if the files cannot be written
	 * @throws EMFValidationException if validation before saving is enabled
		and the generated model fails validation
	 */
	public void save() throws IOException {
		save(null);
	}

	/**
	 * Save all generated models to XMI files with custom options.
	 * The file names are determined by the resources created during generation.
	 * Ecore files are automatically skipped.
	 * 
	 * <p>Example usage with schemaLocation:
	 * {@snippet :
	 * Map<Object, Object> options = new HashMap<>();
	 * options.put(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE);
	 * generator.save(options);
	 * }
	 *
	 * @param options the save options to pass to EMF resources, or null for default options
	 * @throws IOException if the files cannot be written
	 * @throws EMFValidationException if validation before saving is enabled
		and the generated model fails validation
	 */
	public void save(final Map<Object, Object> options) throws IOException {
		saveModelResources(options);
	}

	/**
	 * Saves all generated models, reloads exactly the saved resources in a fresh resource set,
	 * and validates every reloaded root using standard EMF validation.
	 *
	 * <p>All resources are created and loaded together before references are resolved and
	 * validation begins, so references between saved resources can resolve. The returned result
	 * describes only the reconstructed objects, not the current in-memory objects. Temporary
	 * reload resources are unloaded and are never added to this generator's resource set.</p>
	 *
	 * <p>If validation before saving is enabled, it runs first with its configured validator and
	 * can prevent serialization in the same way as {@link #save()}. Round-trip validation happens
	 * after a successful save; an invalid result does not remove files already written.</p>
	 *
	 * @return the aggregate standard-validation result for the reloaded roots
	 * @throws IOException if the files cannot be written or reloaded
	 * @throws EMFValidationException if validation before saving is enabled and the current
	 * model fails validation
	 */
	public EMFValidationResult saveAndValidateRoundTrip() throws IOException {
		return saveAndValidateRoundTrip(null, STANDARD_VALIDATOR_FACTORY);
	}

	/**
	 * Saves all generated models with custom options, reloads exactly the saved resources in a
	 * fresh resource set, and validates every reloaded root using a validator created by the
	 * supplied factory.
	 *
	 * <p>The save options are forwarded unchanged to the normal save path. All saved resources are
	 * loaded together before references are resolved and validation begins. The returned result
	 * describes only the reconstructed objects. Temporary reload resources are unloaded and are
	 * never added to this generator's resource set.</p>
	 *
	 * <p>The factory receives the fresh resource set containing the reloaded resources, not this
	 * generator's resource set. If validation before saving is enabled, its separately configured
	 * factory validates the current in-memory graph before serialization. The supplied factory
	 * applies only after a successful save to the reconstructed graph; an invalid round-trip result
	 * does not remove files already written.</p>
	 *
	 * @param options the save options to pass to EMF resources, or null for default options
	 * @param validatorFactory the factory used to create one validator for the fresh round-trip
	 * resource set
	 * @return the supplied validator's result for the reconstructed model only
	 * @throws NullPointerException if the factory, validator, or result is {@code null}; a null
	 * factory is rejected before any save-related filesystem side effect
	 * @throws IOException if the files cannot be written or reloaded
	 * @throws EMFValidationException if validation before saving is enabled and the current
	 * model fails validation
	 */
	public EMFValidationResult saveAndValidateRoundTrip(
			final Map<Object, Object> options,
			final EMFModelValidator.Factory validatorFactory)
			throws IOException {
		requireValidatorFactoryNonNull(validatorFactory);
		final var savedResources = saveModelResources(options);
		final var roundTripResourceSet = createRoundTripResourceSet();
		try {
			final var reloadedResources = savedResources.stream()
					.map(Resource::getURI)
					.map(roundTripResourceSet::createResource)
					.toList();
			for (var resource : reloadedResources) {
				resource.load(null);
			}
			final var roots = modelRoots(reloadedResources);
			return validateRoots(validatorFactory, roundTripResourceSet, roots);
		} finally {
			for (var resource : List.copyOf(roundTripResourceSet.getResources())) {
				resource.unload();
			}
		}
	}

	private List<Resource> saveModelResources(final Map<Object, Object> options) throws IOException {
		if (validationBeforeSaveFactory != null) {
			validateOrThrow(validationBeforeSaveFactory);
		}

		// Ensure output directory exists
		String outputDir = resourceHelper.getOutputDirectory();
		Path outputPath = Paths.get(outputDir);
		Files.createDirectories(outputPath);

		final var resources = List.copyOf(modelResources());
		for (Resource resource : resources) {
			resource.save(options);
		}
		return resources;
	}

	private ResourceSet createRoundTripResourceSet() {
		final var result = new ResourceSetImpl();
		result.getPackageRegistry().putAll(sharedResourceSet.getPackageRegistry());
		final var sourceRegistry = sharedResourceSet.getResourceFactoryRegistry();
		final var targetRegistry = result.getResourceFactoryRegistry();
		targetRegistry.getExtensionToFactoryMap()
				.putAll(sourceRegistry.getExtensionToFactoryMap());
		targetRegistry.getProtocolToFactoryMap()
				.putAll(sourceRegistry.getProtocolToFactoryMap());
		targetRegistry.getContentTypeToFactoryMap()
				.putAll(sourceRegistry.getContentTypeToFactoryMap());
		return result;
	}
}
