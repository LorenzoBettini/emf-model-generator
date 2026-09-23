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
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;

/**
 * Main entry point for programmatically generating EMF model instances.
 * 
 * <p>This class provides methods to generate model instances from Ecore metamodels,
 * with support for automatic attribute population, reference handling, and XMI serialization.
 * 
 * <p><b>Basic Usage:</b>
 * <pre>{@code
 * EMFModelGenerator generator = new EMFModelGenerator();
 * EPackage ePackage = generator.loadEcoreModel("model.ecore");
 * EClass personClass = (EClass) ePackage.getEClassifier("Person");
 * 
 * generator.setOutputDirectory("output/");
 * EObject person = generator.generateFrom(personClass);
 * generator.save();
 * 
 * generator.unloadEcoreModels(); // Clean up
 * }</pre>
 * 
 * <p><b>Loading Ecore Models:</b> Use {@link #loadEcoreModel(String)} to load Ecore files.
 * This automatically registers the resource factory and package registry entries.
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
 * <pre>{@code
 * generator.generateFrom(personClass);
 * EMFValidationResult result = generator.validate();
 * if (!result.isValid()) {
 *     result.rejectedDiagnostics().forEach(diagnostic ->
 *         System.err.println(diagnostic.getMessage()));
 * }
 * }</pre>
 * Alternatively, use {@link #validateOrThrow()} when an invalid candidate should stop the
 * workflow:
 * <pre>{@code
 * generator.generateFrom(personClass);
 * generator.validateOrThrow();
 * }</pre>
 * Validation before saving is optional and disabled by default. Enabling it prevents any resource
 * from being serialized when validation fails:
 * <pre>{@code
 * generator.enableValidationBeforeSave();
 * generator.save();
 * }</pre>
 * A custom implementation can be supplied through {@link EMFModelValidator.Factory}:
 * <pre>{@code
 * EMFModelValidator.Factory factory = resourceSet ->
 *     new MyProjectModelValidator(resourceSet);
 * EMFValidationResult customResult = generator.validate(factory);
 * generator.enableValidationBeforeSave(factory);
 * }</pre>
 * 
 * @see #loadEcoreModel(String)
 * @see #unloadEcoreModels()
 * @see #generateFrom(EClass)
 * @see #save()
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
	private final List<String> loadedEcoreNsURIs = new ArrayList<>();
	private EMFModelValidator.Factory validationBeforeSaveFactory;

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
	 * <p>Note: When using this constructor, you may still use {@link #loadEcoreModel(String)}
	 * which will automatically register the EcoreResourceFactoryImpl if needed.
	 * 
	 * <p>Example usage:
	 * <pre>{@code
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
	 * }</pre>
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
	 * <pre>{@code
	 * generator.getInstancePopulator().setMaxDepth(3);
	 * generator.getInstancePopulator().setContainmentReferenceDefaultMaxCount(4);
	 * generator.getInstancePopulator().setAttributeSetter(myCustomSetter);
	 * }</pre>
	 *
	 * @return the instance populator
	 */
	public final EMFInstancePopulator getInstancePopulator() {
		return instancePopulator;
	}

	/**
	 * Register the given package in the provided ResourceSet and in the global
	 * EPackage registry. This method is idempotent and safe to call multiple times.
	 */
	private void registerPackage(ResourceSet rs, EPackage pkg) {
		String nsURI = pkg.getNsURI();
		rs.getPackageRegistry().computeIfAbsent(nsURI, k -> pkg);
		EPackage.Registry.INSTANCE.computeIfAbsent(nsURI, k -> pkg);
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
	 * Load an Ecore model from a file path.
	 * This method:
	 * <ul>
	 * <li>Registers the EcoreResourceFactoryImpl if not already registered</li>
	 * <li>Loads the Ecore file into the shared ResourceSet</li>
	 * <li>Registers the EPackage in the EMF global registry</li>
	 * <li>Tracks the loaded resource and nsURI for cleanup via {@link #unloadEcoreModels()}</li>
	 * </ul>
	 * 
	 * The Ecore file is assumed to contain a single EPackage as its root element and
	 * is a valid Ecore model.
	 * 
	 * <p>Example usage:
	 * <pre>{@code
	 * EMFModelGenerator generator = new EMFModelGenerator();
	 * EPackage myPackage = generator.loadEcoreModel("models/mymodel.ecore");
	 * EClass myClass = (EClass) myPackage.getEClassifier("MyClass");
	 * EObject instance = generator.generateFrom(myClass);
	 * generator.save();
	 * generator.unloadEcoreModels(); // Clean up
	 * }</pre>
	 * 
	 * @param ecoreFilePath the path to the Ecore file (absolute or relative)
	 * @return the loaded EPackage
	 * @throws IOException if the file cannot be read
	 */
	public EPackage loadEcoreModel(String ecoreFilePath) throws IOException {
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
		Resource resource;
		try {
			resource = sharedResourceSet.getResource(uri, true);
		} catch (Exception e) {
			throw new IOException("Failed to load Ecore file: " + ecoreFilePath, e);
		}

		final EPackage ePackage = (EPackage) resource.getContents().get(0);
		
		// Register the package in both the ResourceSet and global registry
		registerPackage(sharedResourceSet, ePackage);
		
		// Track for cleanup
		loadedEcoreResources.add(resource);
		loadedEcoreNsURIs.add(ePackage.getNsURI());
		
		return ePackage;
	}

	/**
	 * Unload all Ecore models loaded via {@link #loadEcoreModel(String)}.
	 * This method:
	 * <ul>
	 * <li>Unloads the Ecore resources from the shared ResourceSet</li>
	 * <li>Unregisters the EPackages from the EMF global registry</li>
	 * <li>Clears the tracking lists</li>
	 * </ul>
	 * 
	 * <p>This method is safe to call multiple times. It only affects Ecore models
	 * loaded through {@link #loadEcoreModel(String)}, not other packages or resources.
	 */
	public void unloadEcoreModels() {
		// Unload resources from the ResourceSet
		for (Resource resource : loadedEcoreResources) {
			resource.unload();
			sharedResourceSet.getResources().remove(resource);
		}
		
		// Unregister packages from the global registry
		for (String nsURI : loadedEcoreNsURIs) {
			EPackage.Registry.INSTANCE.remove(nsURI);
		}
		
		// Clear tracking lists
		loadedEcoreResources.clear();
		loadedEcoreNsURIs.clear();
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
		List<EClass> instantiableClasses = new ArrayList<>();
		for (var classifier : ePackage.getEClassifiers()) {
			if (classifier instanceof EClass eClass && EMFUtils.canBeInstantiated(eClass)) {
				instantiableClasses.add(eClass);
			}
		}
		
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
	 * <p>Validation covers every root in every non-Ecore resource in this generator's
	 * resource set, preserving resource and root order.</p>
	 *
	 * @param validatorFactory the factory used to create one validator for this call
	 * @return the aggregate validation result
	 * @throws NullPointerException if the factory, validator, or result is {@code null}
	 */
	public EMFValidationResult validate(final EMFModelValidator.Factory validatorFactory) {
		Objects.requireNonNull(validatorFactory, "validatorFactory");
		try (var validator = Objects.requireNonNull(validatorFactory.create(sharedResourceSet),
				"Validator factory returned null")) {
			return Objects.requireNonNull(validator.validateAll(modelRoots()),
					"Validator returned a null result");
		}
	}

	/**
	 * Validates all model roots with standard EMF validation and throws when invalid.
	 *
	 * @throws EMFValidationException if validation is not valid
	 */
	public void validateOrThrow() {
		validateOrThrow(STANDARD_VALIDATOR_FACTORY);
	}

	/**
	 * Validates all model roots with a custom validator and throws when invalid.
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
		return modelResources().stream()
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
		validationBeforeSaveFactory = Objects.requireNonNull(validatorFactory, "validatorFactory");
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
	 * <pre>{@code
	 * Map<Object, Object> options = new HashMap<>();
	 * options.put(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE);
	 * generator.save(options);
	 * }</pre>
	 *
	 * @param options the save options to pass to EMF resources, or null for default options
	 * @throws IOException if the files cannot be written
	 * @throws EMFValidationException if validation before saving is enabled
		and the generated model fails validation
	 */
	public void save(final Map<Object, Object> options) throws IOException {
		if (validationBeforeSaveFactory != null) {
			validateOrThrow(validationBeforeSaveFactory);
		}

		// Ensure output directory exists
		String outputDir = resourceHelper.getOutputDirectory();
		Path outputPath = Paths.get(outputDir);
		Files.createDirectories(outputPath);

		for (Resource resource : modelResources()) {
			resource.save(options);
		}
	}
}
