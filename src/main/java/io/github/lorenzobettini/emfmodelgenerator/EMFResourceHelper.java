package io.github.lorenzobettini.emfmodelgenerator;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

/**
 * Helper class for managing EMF resources in a given ResourceSet,
 * including resource creation with proper naming schemes and output directory management.
 */
public final class EMFResourceHelper {

	private static final String DEFAULT_FILE_EXTENSION = "xmi";

	private final String outputDirectory;
	private final Map<String, Integer> fileCounters = new HashMap<>();
	private final ResourceSet resourceSet;
	private String filePrefix = "";
	private String globalFileExtension = DEFAULT_FILE_EXTENSION;
	private final Map<EClass, String> eClassExtensions = new HashMap<>();
	private final Map<EPackage, String> ePackageExtensions = new HashMap<>();

	/**
	 * Create a new EMFResourceHelper with a specific ResourceSet and output directory.
	 * 
	 * @param resourceSet the ResourceSet to use for creating resources
	 * @param outputDirectory the directory where XMI files will be created
	 */
	public EMFResourceHelper(final ResourceSet resourceSet, final String outputDirectory) {
		this.resourceSet = resourceSet;
		this.outputDirectory = outputDirectory;
	}

	/**
	 * Set a prefix to be added to all generated file names.
	 * @param string
	 */
	public void setFilePrefix(String string) {
		this.filePrefix = string;
	}

	/**
	 * Set the global file extension to be used for all generated files
	 * when no specific extension is defined for an EClass or EPackage.
	 * 
	 * @param extension the file extension (without the dot)
	 */
	public void setGlobalFileExtension(String extension) {
		this.globalFileExtension = extension;
	}

	/**
	 * Set a custom file extension for a specific EClass.
	 * This takes precedence over EPackage and global extensions.
	 * 
	 * @param eClass the EClass
	 * @param extension the file extension (without the dot)
	 */
	public void setFileExtensionForEClass(EClass eClass, String extension) {
		eClassExtensions.put(eClass, extension);
	}

	/**
	 * Set a custom file extension for all EClasses in a specific EPackage.
	 * This takes precedence over the global extension but is overridden by EClass-specific extensions.
	 * 
	 * @param ePackage the EPackage
	 * @param extension the file extension (without the dot)
	 */
	public void setFileExtensionForEPackage(EPackage ePackage, String extension) {
		ePackageExtensions.put(ePackage, extension);
	}

	/**
	 * Get the file extension for a given EClass, using the hierarchical lookup:
	 * EClass-specific -> EPackage-specific -> global -> default.
	 * 
	 * @param eClass the EClass
	 * @return the file extension (without the dot)
	 */
	public String getFileExtension(EClass eClass) {
		// Check EClass-specific extension
		var fileExtension = eClassExtensions.get(eClass);
		if (fileExtension != null) {
			return fileExtension;
		}

		// Check EPackage-specific extension
		EPackage ePackage = eClass.getEPackage();
		fileExtension = ePackageExtensions.get(ePackage);
		if (fileExtension != null) {
			return fileExtension;
		}

		// Fall back to global extension
		return globalFileExtension;
	}

	/**
	 * Get the output directory.
	 * 
	 * @return the output directory path
	 */
	public String getOutputDirectory() {
		return outputDirectory;
	}

	/**
	 * Get the ResourceSet.
	 * 
	 * @return the ResourceSet
	 */
	public ResourceSet getResourceSet() {
		return resourceSet;
	}

	/**
	 * Create a new Resource in the ResourceSet with a file URI based on the EClass.
	 * The file name follows the pattern: {packageName}_{className}_{counter}.xmi
	 * 
	 * @param eClass the EClass for which to create a resource
	 * @return the created Resource
	 */
	public Resource createResource(final EClass eClass) {
		EPackage ePackage = eClass.getEPackage();
		String fileName = generateFileName(ePackage, eClass);
		URI uri = createFileURI(fileName);
		
		return resourceSet.createResource(uri);
	}

	/**
	 * Generate a file name for the given EClass following the naming convention.
	 * Pattern: {filePrefix}{packageName}_{className}_{counter}.{extension}
	 * The extension is determined using hierarchical lookup: EClass -> EPackage -> global -> default.
	 * 
	 * @param ePackage the EPackage
	 * @param eClass the EClass
	 * @return the generated file name
	 */
	public String generateFileName(final EPackage ePackage, final EClass eClass) {
		String eClassName = eClass.getName();
		int counter = fileCounters.getOrDefault(eClassName, 0) + 1;
		fileCounters.put(eClassName, counter);
		String extension = getFileExtension(eClass);

		return String.format("%s%s_%s_%d.%s", filePrefix, ePackage.getName(), eClassName, counter, extension);
	}

	/**
	 * Create a file URI for the given file name in the output directory.
	 * The path is normalized to avoid "." and ".." segments and converted to an absolute path.
	 * 
	 * @param fileName the file name (including extension)
	 * @return the file URI
	 */
	public URI createFileURI(final String fileName) {
		Path filePath = Paths.get(outputDirectory, fileName);
		return URI.createFileURI(filePath.toAbsolutePath()
				.normalize() // "avoid "." and ".."
				.toString());
	}

	/**
	 * Reset the file counters. Useful when starting a new generation cycle.
	 */
	public void resetCounters() {
		fileCounters.clear();
	}

	/**
	 * Get the current counter value for a specific EClass name.
	 * 
	 * @param eClassName the name of the EClass
	 * @return the current counter value, or 0 if not yet set
	 */
	public int getCounter(final String eClassName) {
		return fileCounters.getOrDefault(eClassName, 0);
	}
}
