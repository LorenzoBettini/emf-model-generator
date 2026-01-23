package io.github.lorenzobettini.emfmodelgenerator;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;

/**
 * Helper class for creating and configuring EMF ResourceSets.
 * This class centralizes the logic for setting up ResourceSets with proper
 * resource factories and save options.
 */
public class EMFResourceSetHelper {

	private static final int DEFAULT_LINE_WIDTH = 10;

	private EMFResourceSetHelper() {
		// Utility class, prevent instantiation
	}

	/**
	 * Creates a new ResourceSet configured with XMI resource factory and save options.
	 * The XMI resources will be saved with a line width of 10 for better readability
	 * on smaller screens.
	 *
	 * @return a newly configured ResourceSet
	 */
	public static ResourceSet createResourceSet() {
		return createResourceSet(DEFAULT_LINE_WIDTH);
	}

	/**
	 * Creates a new ResourceSet configured with XMI resource factory and custom line width.
	 *
	 * @param lineWidth the line width to use when saving XMI resources
	 * @return a newly configured ResourceSet
	 */
	public static ResourceSet createResourceSet(int lineWidth) {
		ResourceSet resourceSet = new ResourceSetImpl();
		configureResourceSet(resourceSet, lineWidth);
		return resourceSet;
	}

	/**
	 * Configures an existing ResourceSet with XMI resource factory and save options.
	 * The XMI resources will be saved with a line width of 10 for better readability
	 * on smaller screens.
	 *
	 * @param resourceSet the ResourceSet to configure
	 */
	public static void configureResourceSet(ResourceSet resourceSet) {
		configureResourceSet(resourceSet, DEFAULT_LINE_WIDTH);
	}

	/**
	 * Configures an existing ResourceSet with XMI resource factory and custom line width.
	 *
	 * @param resourceSet the ResourceSet to configure
	 * @param lineWidth the line width to use when saving XMI resources
	 */
	public static void configureResourceSet(ResourceSet resourceSet, int lineWidth) {
		// Create a custom XMI resource factory that sets line width
		Resource.Factory factory = new XMIResourceFactoryImpl() {
			@Override
			public Resource createResource(URI uri) {
				var resource = new XMIResourceImpl(uri);
				// this simulates the behavior of the
				// "Sample Reflective Ecore Model Editor" when saving
				resource.getDefaultSaveOptions().put(XMLResource.OPTION_LINE_WIDTH, lineWidth);
				return resource;
			}
		};

		// Register for both xmi extension and wildcard
		resourceSet.getResourceFactoryRegistry()
			.getExtensionToFactoryMap()
			.put("xmi", factory);
		resourceSet.getResourceFactoryRegistry()
			.getExtensionToFactoryMap()
			.put("*", factory);
	}

	/**
	 * Gets the default line width used for XMI resource formatting.
	 *
	 * @return the default line width
	 */
	public static int getDefaultLineWidth() {
		return DEFAULT_LINE_WIDTH;
	}
}
