package io.github.lorenzobettini.emfmodelgenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

/**
 * Utility to regenerate expected XMI files with the new format (line width = 10).
 * Run this once to update all expected output files.
 */
public class XMIFormatUpdater {

	public static void main(String[] args) throws IOException {
		String inputsDir = "src/test/resources/inputs";
		String expectedOutputsDir = "src/test/resources/expected-outputs";
		
		// Create ResourceSet with new formatting
		ResourceSet resourceSet = EMFResourceSetHelper.createResourceSet();
		
		// Register ecore factory
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
			.put("ecore", new org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl());
		
		// First, load all Ecore files to register the EPackages
		System.out.println("Loading Ecore models from: " + inputsDir);
		try (Stream<Path> paths = Files.walk(Paths.get(inputsDir))) {
			paths.filter(Files::isRegularFile)
				.filter(p -> p.toString().endsWith(".ecore"))
				.forEach(path -> {
					try {
						System.out.println("  Loading: " + path.getFileName());
						File file = path.toFile();
						URI uri = URI.createFileURI(file.getAbsolutePath());
						Resource resource = resourceSet.getResource(uri, true);
						
						// Register all EPackages from this resource
						resource.getContents().forEach(obj -> {
							if (obj instanceof EPackage ePackage) {
								String nsURI = ePackage.getNsURI();
								if (nsURI != null) {
									resourceSet.getPackageRegistry().put(nsURI, ePackage);
									EPackage.Registry.INSTANCE.put(nsURI, ePackage);
									System.out.println("    Registered: " + nsURI);
								}
							}
						});
					} catch (Exception e) {
						System.err.println("  Error loading " + path + ": " + e.getMessage());
					}
				});
		}
		
		System.out.println("\nUpdating XMI files in: " + expectedOutputsDir);
		// Now process all XMI files in expected-outputs
		try (Stream<Path> paths = Files.walk(Paths.get(expectedOutputsDir))) {
			paths.filter(Files::isRegularFile)
				.filter(p -> p.toString().endsWith(".xmi"))
				.forEach(path -> {
					try {
						System.out.println("  Processing: " + path.getFileName());
						
						// Load the file
						File file = path.toFile();
						URI uri = URI.createFileURI(file.getAbsolutePath());
						Resource resource = resourceSet.getResource(uri, true);
						
						// Save it with new format
						resource.save(null);
						
						System.out.println("    ✓ Updated: " + path.getFileName());
					} catch (Exception e) {
						System.err.println("    ✗ Error processing " + path.getFileName() + ": " + e.getMessage());
					}
				});
		}
		
		System.out.println("\nDone updating all XMI files!");
	}
}
