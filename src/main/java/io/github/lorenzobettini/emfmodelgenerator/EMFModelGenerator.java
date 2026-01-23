package io.github.lorenzobettini.emfmodelgenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;

import io.github.lorenzobettini.emfmodelgenerator.EMFAttributeSetter.EMFAttributeValueFunction;
import io.github.lorenzobettini.emfmodelgenerator.EMFContainmentReferenceSetter.EMFContainmentReferenceValueFunction;
import io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceSetter.EMFCrossReferenceValueFunction;

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
 * <p><b>Customization:</b> The generator provides extensive customization options including
 * custom setters for attributes, containment references, and cross-references; per-feature
 * custom functions; configurable multiplicities; and hierarchical file extension settings.
 * 
 * @see #loadEcoreModel(String)
 * @see #unloadEcoreModels()
 * @see #generateFrom(EClass)
 * @see #save()
 */
public class EMFModelGenerator {

	private String outputDirectory = "target/test-output";
	private final ResourceSet sharedResourceSet;
	private EMFResourceHelper resourceHelper;
	private EMFInstancePopulator instancePopulator = new EMFInstancePopulator();
	private int numberOfInstances = 1;
	private final List<Resource> loadedEcoreResources = new ArrayList<>();
	private final List<String> loadedEcoreNsURIs = new ArrayList<>();

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
	 * Set the maximum depth for containment references when populating instances.
	 * @param maxDepth
	 */
	public void setMaxDepth(int maxDepth) {
		instancePopulator.setMaxDepth(maxDepth);
	}

	/**
	 * Set the number of instances to generate per EClass.
	 * @param numberOfInstances
	 */
	public void setNumberOfInstances(int numberOfInstances) {
		this.numberOfInstances = numberOfInstances;
	}

	public void setAttributeDefaultMaxCount(int count) {
		instancePopulator.setAttributeDefaultMaxCount(count);
	}

	public void setCrossReferenceDefaultMaxCount(int count) {
		instancePopulator.setCrossReferenceDefaultMaxCount(count);
	}

	public void setContainmentReferenceDefaultMaxCount(int count) {
		instancePopulator.setContainmentReferenceDefaultMaxCount(count);
	}

	public void setAttributeMaxCountFor(EAttribute attribute, int count) {
		instancePopulator.setAttributeMaxCountFor(attribute, count);
	}

	public void setCrossReferenceMaxCountFor(EReference reference, int count) {
		instancePopulator.setCrossReferenceMaxCountFor(reference, count);
	}

	public void setContainmentReferenceMaxCountFor(EReference reference, int count) {
		instancePopulator.setContainmentReferenceMaxCountFor(reference, count);
	}

	public void setFeatureMapDefaultMaxCount(int count) {
		instancePopulator.setFeatureMapDefaultMaxCount(count);
	}

	public void setFeatureMapMaxCountFor(EAttribute featureMapAttribute, int count) {
		instancePopulator.setFeatureMapMaxCountFor(featureMapAttribute, count);
	}

	/**
	 * Set a custom function for generating values for the given EAttribute.
	 * 
	 * @param attribute the EAttribute for which to set the function
	 * @param function  the function to generate values for the attribute
	 */
	public void functionForAttribute(EAttribute attribute,
			EMFAttributeValueFunction function) {
		instancePopulator.functionForAttribute(attribute, function);
	}

	/**
	 * Set a custom function for generating values for the given cross EReference.
	 * 
	 * @param reference the cross EReference for which to set the function
	 * @param function  the function to generate values for the cross reference
	 */
	public void functionForCrossReference(EReference reference,
			EMFCrossReferenceValueFunction function) {
		instancePopulator.functionForCrossReference(reference, function);
	}

	/**
	 * Set a custom function for generating values for the given containment EReference.
	 * 
	 * @param reference the containment EReference for which to set the function
	 * @param function  the function to generate values for the containment reference
	 */
	public void functionForContainmentReference(EReference reference,
			EMFContainmentReferenceValueFunction function) {
		instancePopulator.functionForContainmentReference(reference, function);
	}

	/**
	 * Set a custom function for generating values for the given feature map group member.
	 * 
	 * @param groupMember the feature map group member EReference for which to set the function
	 * @param function    the function to generate values for the group member
	 */
	public void functionForFeatureMapGroupMember(EReference groupMember,
			EMFFeatureMapSetter.EMFFeatureMapValueFunction function) {
		instancePopulator.functionForFeatureMapGroupMember(groupMember, function);
	}

	/**
	 * Set a custom attribute setter to control how attribute values are generated.
	 * 
	 * @param attributeSetter the custom EMFAttributeSetter to use
	 */
	public void setAttributeSetter(EMFAttributeSetter attributeSetter) {
		instancePopulator.setAttributeSetter(attributeSetter);
	}

	/**
	 * Set a custom cross reference setter to control how cross references are populated.
	 * 
	 * @param crossReferenceSetter the custom EMFCrossReferenceSetter to use
	 */
	public void setCrossReferenceSetter(EMFCrossReferenceSetter crossReferenceSetter) {
		instancePopulator.setCrossReferenceSetter(crossReferenceSetter);
	}

	/**
	 * Set a custom containment reference setter to control how containment references are created.
	 * 
	 * @param containmentReferenceSetter the custom EMFContainmentReferenceSetter to use
	 */
	public void setContainmentReferenceSetter(EMFContainmentReferenceSetter containmentReferenceSetter) {
		instancePopulator.setContainmentReferenceSetter(containmentReferenceSetter);
	}

	/**
	 * Set a custom feature map setter to control how feature maps are populated.
	 * 
	 * @param featureMapSetter the custom EMFFeatureMapSetter to use
	 */
	public void setFeatureMapSetter(EMFFeatureMapSetter featureMapSetter) {
		instancePopulator.setFeatureMapSetter(featureMapSetter);
	}

	/**
	 * Set the candidate selector strategy for selecting instantiable subclasses in containment references.
	 * 
	 * @param strategy the candidate selector strategy to use
	 */
	public void setInstantiableSubclassSelectorStrategy(EMFCandidateSelectorStrategy<EClass, EClass> strategy) {
		instancePopulator.setInstantiableSubclassSelectorStrategy(strategy);
	}

	/**
	 * Set the candidate selector strategy for selecting existing EObject instances in cross references.
	 * 
	 * @param strategy the candidate selector strategy to use
	 */
	public void setCrossReferenceCandidateSelectorStrategy(EMFCandidateSelectorStrategy<EClass, EObject> strategy) {
		instancePopulator.setCrossReferenceCandidateSelectorStrategy(strategy);
	}

	/**
	 * Set the candidate selector strategy for selecting enum literals in attributes.
	 * 
	 * @param strategy the candidate selector strategy to use
	 */
	public void setEnumLiteralSelectorStrategy(EMFCandidateSelectorStrategy<EEnum, EEnumLiteral> strategy) {
		instancePopulator.setEnumLiteralSelectorStrategy(strategy);
	}

	/**
	 * Set the candidate selector strategy for selecting feature map group members.
	 * 
	 * @param strategy the candidate selector strategy to use
	 */
	public void setGroupMemberSelectorStrategy(EMFCandidateSelectorStrategy<EAttribute, EReference> strategy) {
		instancePopulator.setGroupMemberSelectorStrategy(strategy);
	}

	/**
	 * Set the cycle policy for determining whether self-references are allowed in cross references.
	 * 
	 * <p>Example usage:
	 * <pre>{@code
	 * generator.setAllowCyclePolicy((owner, ref) -> {
	 *     // Allow cycles only for the first Node
	 *     return "Node".equals(owner.eClass().getName()) &&
	 *            owner.eGet(owner.eClass().getEStructuralFeature("id")).equals(1);
	 * });
	 * }</pre>
	 * 
	 * @param cyclePolicy a functional interface that determines if cycles are allowed for specific owner/reference combinations
	 */
	public void setAllowCyclePolicy(EMFCrossReferenceSetter.CyclePolicy cyclePolicy) {
		instancePopulator.setAllowCyclePolicy(cyclePolicy);
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
	 * Save all generated models to XMI files.
	 * The file names are determined by the resources created during generation.
	 * Ecore files are automatically skipped.
	 *
	 * @throws IOException if the files cannot be written
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
	 */
	public void save(final Map<Object, Object> options) throws IOException {
		// Ensure output directory exists
		String outputDir = resourceHelper.getOutputDirectory();
		Path outputPath = Paths.get(outputDir);
		Files.createDirectories(outputPath);

		for (Resource resource : sharedResourceSet.getResources()) {
			// Skip resources that correspond to Ecore files
			if (EMFUtils.isEcoreResource(resource)) {
				continue;
			}
			resource.save(options);
		}
	}
}
