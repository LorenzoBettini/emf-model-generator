package io.github.lorenzobettini.emfmodelgenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * Utility class containing common test helper methods for EMF model testing.
 */
public class EMFTestUtils {

	/**
	 * ThreadLocal storage for tracking nsURIs of EPackages registered during tests.
	 * This allows cleanup after each test without affecting EMF's pre-registered packages.
	 */
	private static final ThreadLocal<Set<String>> registeredNsURIs = ThreadLocal.withInitial(HashSet::new);

	/**
	 * Register an EPackage in the global EMF registry and track it for cleanup.
	 * 
	 * @param ePackage the package to register
	 */
	public static void registerPackageForTest(EPackage ePackage) {
		String nsURI = ePackage.getNsURI();
		EPackage.Registry.INSTANCE.put(nsURI, ePackage);
		registeredNsURIs.get().add(nsURI);
	}

	/**
	 * Track an nsURI for cleanup without registering it.
	 * Useful when testing error handling with non-EPackage registry entries.
	 * 
	 * @param nsURI the nsURI to track for cleanup
	 */
	public static void trackNsURIForTest(String nsURI) {
		registeredNsURIs.get().add(nsURI);
	}

	/**
	 * Clean up all EPackages registered during the current test.
	 * This should be called in @AfterEach to ensure test isolation.
	 * Only removes packages that were registered via registerPackageForTest().
	 */
	public static void cleanupRegisteredPackages() {
		Set<String> nsURIs = registeredNsURIs.get();
		for (String nsURI : nsURIs) {
			EPackage.Registry.INSTANCE.remove(nsURI);
		}
		nsURIs.clear();
	}

	/**
	 * Load an Ecore model from a file in the test resources directory.
	 *
	 * @param inputDir the directory containing the input file
	 * @param fileName the name of the Ecore file
	 * @return the loaded EPackage
	 * @throws IOException if the file cannot be read
	 */
	public static EPackage loadEcoreModel(String inputDir, String fileName) {
		ResourceSet resourceSet = EMFResourceSetHelper.createResourceSet();
		return loadEcoreModel(inputDir, fileName, resourceSet);
	}

	/**
	 * Load an Ecore model from a file using a provided ResourceSet.
	 * @param inputDir
	 * @param fileName
	 * @param resourceSet
	 * @return
	 */
	public static EPackage loadEcoreModel(String inputDir, String fileName, ResourceSet resourceSet) {
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
			.put("ecore", new EcoreResourceFactoryImpl());

		File ecoreFile = new File(inputDir, fileName);
		URI uri = URI.createFileURI(ecoreFile.getAbsolutePath());
		Resource resource = resourceSet.getResource(uri, true);

		final EPackage ePackage = (EPackage) resource.getContents().get(0);
		
		// Register the package in the global registry with tracking for cleanup
		registerPackageForTest(ePackage);
		
		return ePackage;
	}

	/**
	 * Load a generated XMI model from a file in a new ResourceSet.
	 *
	 * @param xmiFile the XMI file to load
	 * @param ePackage the EPackage to register
	 * @return the loaded EObject
	 * @throws IOException if the file cannot be read
	 */
	public static EObject loadGeneratedModel(File xmiFile, EPackage ePackage) {
		ResourceSet resourceSet = EMFResourceSetHelper.createResourceSet();
		return loadGeneratedModel(xmiFile, ePackage, resourceSet);
	}

	/**
	 * Load a generated XMI model from a file using a provided ResourceSet.
	 * 
	 * @param xmiFile
	 * @param ePackage
	 * @param resourceSet
	 * @return
	 */
	public static EObject loadGeneratedModel(File xmiFile, EPackage ePackage, ResourceSet resourceSet) {
		resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);

		URI uri = URI.createFileURI(xmiFile.getAbsolutePath());
		Resource resource = resourceSet.getResource(uri, true);

		return resource.getContents().isEmpty() ? null : resource.getContents().get(0);
	}

	/**
	 * Validate an EMF model using the EMF Diagnostician.
	 * Fails the test if validation errors are found.
	 *
	 * @param eObject the EObject to validate
	 */
	public static void validateModel(EObject eObject) {
		Diagnostic diagnostic = Diagnostician.INSTANCE.validate(eObject);
		if (diagnostic.getSeverity() != Diagnostic.OK) {
			StringBuilder sb = new StringBuilder();
			sb.append("Validation errors:\n");
			diagnostic.getChildren().forEach(d -> {
				sb.append(" - ").append(d.getMessage()).append("\n");
			});
			fail(sb.toString());
		}
	}

	/**
	 * Compare a generated XMI file with an expected output file.
	 * If the expected file doesn't exist, it will be created from the generated file.
	 *
	 * @param outputDir the directory containing the generated file
	 * @param expectedDir the directory containing the expected file
	 * @param generatedFileName the name of the generated file
	 * @param expectedFileName the name of the expected file
	 * @throws IOException if the files cannot be read
	 */
	public static void assertXMIMatchesExpected(String outputDir, String expectedDir,
			String generatedFileName, String expectedFileName) throws IOException {
		File generatedFile = new File(outputDir, generatedFileName);
		File expectedFile = new File(expectedDir, expectedFileName);

		assertThat(generatedFile).exists();

		if (!expectedFile.exists()) {
			// If expected file doesn't exist, create it for the first run
			Files.createDirectories(expectedFile.toPath().getParent());
			Files.copy(generatedFile.toPath(), expectedFile.toPath());
			System.out.println("Created expected output file: " + expectedFile.getAbsolutePath());
			System.out.println("Please review and commit this file if it's correct.");
			return; // Skip comparison on first run
		}

		// Read both files and compare
		String generatedContent = Files.readString(generatedFile.toPath(), StandardCharsets.UTF_8);
		String expectedContent = Files.readString(expectedFile.toPath(), StandardCharsets.UTF_8);

		// Normalize timezone offsets for comparison
		// Dates are timezone-agnostic in Java, but their string representation depends on the system's default timezone
		// We normalize all timezone offsets to a common format so tests pass on any system regardless of timezone
		String normalizedGenerated = normalizeTimezones(generatedContent);
		String normalizedExpected = normalizeTimezones(expectedContent);

		assertThat(normalizedGenerated).isEqualTo(normalizedExpected);
	}

	/**
	 * Normalize timezone offsets in XMI content to a standard format for comparison.
	 * This allows tests to pass regardless of the local system's timezone.
	 *
	 * @param content the content to normalize
	 * @return the normalized content
	 */
	public static String normalizeTimezones(String content) {
		// Replace timezone offsets with a standard format for comparison
		// Timezone format in ISO8601: +HHMM or -HHMM or +HH:MM or -HH:MM
		return content.replaceAll("[+-]\\d{2}:?\\d{2}", "+TZ");
	}

	/**
	 * Load an EPackage from an Ecore file using a relative path.
	 * <p>
	 * This is a convenience method that combines path resolution with loadEcoreModel().
	 * 
	 * @param relativePath the path relative to src/test/resources (e.g., "inputs/simple.ecore")
	 * @return the loaded EPackage
	 */
	public static EPackage loadEPackage(final String relativePath) {
		String dir;
		String fileName;
		final int lastSlash = relativePath.lastIndexOf('/');
		if (lastSlash == -1) {
			dir = "src/test/resources";
			fileName = relativePath;
		} else {
			dir = "src/test/resources/" + relativePath.substring(0, lastSlash);
			fileName = relativePath.substring(lastSlash + 1);
		}
		return loadEcoreModel(dir, fileName);
	}

	/**
	 * Create an instance of an EClass by name from an EPackage.
	 * 
	 * @param ePackage the package containing the EClass
	 * @param eClassName the name of the EClass to instantiate
	 * @return the created EObject instance
	 */
	public static EObject createInstance(final EPackage ePackage, final String eClassName) {
		final EClass eClass = (EClass) ePackage.getEClassifier(eClassName);
		return ePackage.getEFactoryInstance().create(eClass);
	}

	/**
	 * Create a ResourceSet with XMI factory registered.
	 * 
	 * @return a configured ResourceSet
	 */
	public static ResourceSet createResourceSet() {
		final ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put("*", new XMIResourceFactoryImpl());
		return resourceSet;
	}

	/**
	 * Create a Resource in the given ResourceSet.
	 * 
	 * @param resourceSet the ResourceSet to create the resource in
	 * @param fileName the file name for the resource URI
	 * @return the created Resource
	 */
	public static Resource createResource(final ResourceSet resourceSet, final String fileName) {
		final URI uri = URI.createURI("test://" + fileName);
		return resourceSet.createResource(uri);
	}

	/**
	 * Get an EClass from an EPackage by name, with assertion that it exists and is an EClass.
	 * 
	 * @param ePackage the package to search in
	 * @param name the name of the EClass
	 * @return the EClass
	 * @throws AssertionError if the classifier is not found or is not an EClass
	 */
	public static EClass assertEClassExists(EPackage ePackage, String name) {
		EClassifier classifier = ePackage.getEClassifier(name);
		assertThat(classifier)
			.as("EClass '%s' should exist in package '%s'", name, ePackage.getName())
			.isNotNull()
			.isInstanceOf(EClass.class);
		return (EClass) classifier;
	}

	/**
	 * Get an EAttribute from an EClass by name, with assertion that it exists and is an EAttribute.
	 * 
	 * @param eClass the class to search in
	 * @param name the name of the attribute
	 * @return the EAttribute
	 * @throws AssertionError if the feature is not found or is not an EAttribute
	 */
	public static EAttribute assertEAttributeExists(EClass eClass, String name) {
		EStructuralFeature feature = eClass.getEStructuralFeature(name);
		assertThat(feature)
			.as("EAttribute '%s' should exist in EClass '%s'", name, eClass.getName())
			.isNotNull()
			.isInstanceOf(EAttribute.class);
		return (EAttribute) feature;
	}

	/**
	 * Get an EReference from an EClass by name, with assertion that it exists and is an EReference.
	 * 
	 * @param eClass the class to search in
	 * @param name the name of the reference
	 * @return the EReference
	 * @throws AssertionError if the feature is not found or is not an EReference
	 */
	public static EReference assertEReferenceExists(EClass eClass, String name) {
		EStructuralFeature feature = eClass.getEStructuralFeature(name);
		assertThat(feature)
			.as("EReference '%s' should exist in EClass '%s'", name, eClass.getName())
			.isNotNull()
			.isInstanceOf(EReference.class);
		return (EReference) feature;
	}

	/**
	 * Create a new EPackage with the given name, nsURI, and nsPrefix.
	 * 
	 * @param name the name of the package
	 * @param nsURI the namespace URI
	 * @param nsPrefix the namespace prefix
	 * @return the created EPackage
	 */
	public static EPackage createEPackage(String name, String nsURI, String nsPrefix) {
		EPackage ePackage = EcoreFactory.eINSTANCE.createEPackage();
		ePackage.setName(name);
		ePackage.setNsURI(nsURI);
		ePackage.setNsPrefix(nsPrefix);
		return ePackage;
	}

	/**
	 * Create a new concrete EClass with the given name and add it to the package.
	 * 
	 * @param ePackage the package to add the class to
	 * @param name the name of the class
	 * @return the created EClass
	 */
	public static EClass createEClass(EPackage ePackage, String name) {
		EClass eClass = EcoreFactory.eINSTANCE.createEClass();
		eClass.setName(name);
		ePackage.getEClassifiers().add(eClass);
		return eClass;
	}

	/**
	 * Create a new EClass with the given name, abstract/interface flags, and add it to the package.
	 * 
	 * @param ePackage the package to add the class to
	 * @param name the name of the class
	 * @param isAbstract whether the class is abstract
	 * @param isInterface whether the class is an interface
	 * @return the created EClass
	 */
	public static EClass createEClass(EPackage ePackage, String name, boolean isAbstract, boolean isInterface) {
		EClass eClass = EcoreFactory.eINSTANCE.createEClass();
		eClass.setName(name);
		eClass.setAbstract(isAbstract);
		eClass.setInterface(isInterface);
		ePackage.getEClassifiers().add(eClass);
		return eClass;
	}

	/**
	 * Create a new EAttribute with the given name and type, and add it to the EClass.
	 * 
	 * @param eClass the class to add the attribute to
	 * @param name the name of the attribute
	 * @param eType the type of the attribute (e.g., EcorePackage.Literals.ESTRING)
	 * @return the created EAttribute
	 */
	public static EAttribute createEAttribute(EClass eClass, String name, EClassifier eType) {
		EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName(name);
		attribute.setEType(eType);
		eClass.getEStructuralFeatures().add(attribute);
		return attribute;
	}

	/**
	 * Create a new containment EReference with the given name and type, and add it to the EClass.
	 * 
	 * @param eClass the class to add the reference to
	 * @param name the name of the reference
	 * @param eType the type of the reference
	 * @param upperBound the upper bound (-1 for unbounded, 1 for single)
	 * @return the created containment EReference
	 */
	public static EReference createContainmentEReference(EClass eClass, String name, EClass eType, int upperBound) {
		EReference reference = EcoreFactory.eINSTANCE.createEReference();
		reference.setName(name);
		reference.setEType(eType);
		reference.setContainment(true);
		reference.setUpperBound(upperBound);
		eClass.getEStructuralFeatures().add(reference);
		return reference;
	}

	/**
	 * Create a new containment EReference with full configuration options.
	 * 
	 * @param eClass the class to add the reference to
	 * @param name the name of the reference
	 * @param eType the type of the reference
	 * @param lowerBound the lower bound (0 for optional)
	 * @param upperBound the upper bound (-1 for unbounded, 1 for single)
	 * @return the created containment EReference
	 */
	public static EReference createContainmentEReference(EClass eClass, String name, EClass eType,
			int lowerBound, int upperBound) {
		EReference reference = EcoreFactory.eINSTANCE.createEReference();
		reference.setName(name);
		reference.setEType(eType);
		reference.setContainment(true);
		reference.setLowerBound(lowerBound);
		reference.setUpperBound(upperBound);
		eClass.getEStructuralFeatures().add(reference);
		return reference;
	}

	/**
	 * Create a new cross EReference (non-containment) with the given name and type, and add it to the EClass.
	 * 
	 * @param eClass the class to add the reference to
	 * @param name the name of the reference
	 * @param eType the type of the reference
	 * @param upperBound the upper bound (-1 for unbounded, 1 for single)
	 * @return the created cross EReference
	 */
	public static EReference createCrossEReference(EClass eClass, String name, EClass eType, int upperBound) {
		EReference reference = EcoreFactory.eINSTANCE.createEReference();
		reference.setName(name);
		reference.setEType(eType);
		reference.setContainment(false);
		reference.setUpperBound(upperBound);
		eClass.getEStructuralFeatures().add(reference);
		return reference;
	}

	/**
	 * Create a new cross EReference (non-containment) with full configuration options.
	 * 
	 * @param eClass the class to add the reference to
	 * @param name the name of the reference
	 * @param eType the type of the reference
	 * @param lowerBound the lower bound (0 for optional)
	 * @param upperBound the upper bound (-1 for unbounded, 1 for single)
	 * @return the created cross EReference
	 */
	public static EReference createCrossEReference(EClass eClass, String name, EClass eType,
			int lowerBound, int upperBound) {
		EReference reference = EcoreFactory.eINSTANCE.createEReference();
		reference.setName(name);
		reference.setEType(eType);
		reference.setContainment(false);
		reference.setLowerBound(lowerBound);
		reference.setUpperBound(upperBound);
		eClass.getEStructuralFeatures().add(reference);
		return reference;
	}

	/**
	 * Create a new EAttribute with extended configuration options.
	 * 
	 * @param eClass the class to add the attribute to
	 * @param name the name of the attribute
	 * @param eType the type of the attribute
	 * @param lowerBound the lower bound (0 for optional)
	 * @param upperBound the upper bound (-1 for unbounded, 1 for single)
	 * @return the created EAttribute
	 */
	public static EAttribute createEAttribute(EClass eClass, String name, EClassifier eType,
			int lowerBound, int upperBound) {
		EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName(name);
		attribute.setEType(eType);
		attribute.setLowerBound(lowerBound);
		attribute.setUpperBound(upperBound);
		eClass.getEStructuralFeatures().add(attribute);
		return attribute;
	}

	/**
	 * Create an abstract EClass with the given name and add it to the package.
	 * 
	 * @param ePackage the package to add the class to
	 * @param name the name of the class
	 * @return the created abstract EClass
	 */
	public static EClass createAbstractEClass(EPackage ePackage, String name) {
		return createEClass(ePackage, name, true, false);
	}

	/**
	 * Create an interface EClass with the given name and add it to the package.
	 * 
	 * @param ePackage the package to add the class to
	 * @param name the name of the class
	 * @return the created interface EClass
	 */
	public static EClass createInterfaceEClass(EPackage ePackage, String name) {
		return createEClass(ePackage, name, true, true);
	}

	/**
	 * Create a concrete EClass with the given name and supertype, and add it to the package.
	 * 
	 * @param ePackage the package to add the class to
	 * @param name the name of the class
	 * @param superType the super type of the class
	 * @return the created EClass
	 */
	public static EClass createSubclass(EPackage ePackage, String name, EClass superType) {
		EClass eClass = createEClass(ePackage, name);
		eClass.getESuperTypes().add(superType);
		return eClass;
	}

	/**
	 * Create a new EEnum with the given name and add it to the package.
	 * 
	 * @param ePackage the package to add the enum to
	 * @param name the name of the enum
	 * @return the created EEnum
	 */
	public static EEnum createEEnum(EPackage ePackage, String name) {
		EEnum eEnum = EcoreFactory.eINSTANCE.createEEnum();
		eEnum.setName(name);
		ePackage.getEClassifiers().add(eEnum);
		return eEnum;
	}

	/**
	 * Create a new EEnumLiteral with the given name and value, and add it to the enum.
	 * 
	 * @param eEnum the enum to add the literal to
	 * @param name the name of the literal
	 * @param value the value of the literal
	 * @return the created EEnumLiteral
	 */
	public static EEnumLiteral createEEnumLiteral(EEnum eEnum, String name, int value) {
		EEnumLiteral literal = EcoreFactory.eINSTANCE.createEEnumLiteral();
		literal.setName(name);
		literal.setValue(value);
		eEnum.getELiterals().add(literal);
		return literal;
	}

	/**
	 * Create an instance of the given EClass using EcoreUtil.
	 * 
	 * @param eClass the class to instantiate
	 * @return the created instance
	 */
	public static EObject createInstance(EClass eClass) {
		return EcoreUtil.create(eClass);
	}

	/**
	 * Create an instance of the given EClass and add it to the resource.
	 * 
	 * @param eClass the class to instantiate
	 * @param resource the resource to add the instance to
	 * @return the created instance
	 */
	public static EObject createInstanceInResource(EClass eClass, Resource resource) {
		EObject instance = createInstance(eClass);
		resource.getContents().add(instance);
		return instance;
	}

	/**
	 * Create an instance of the given EClass, create a resource in the ResourceSet, and add the instance to it.
	 * 
	 * @param eClass the class to instantiate
	 * @param resourceSet the ResourceSet to create the resource in
	 * @param fileName the file name for the resource URI
	 * @return the created instance
	 */
	public static EObject createInstanceInResource(EClass eClass, ResourceSet resourceSet, String fileName) {
		Resource resource = createResource(resourceSet, fileName);
		return createInstanceInResource(eClass, resource);
	}
}
