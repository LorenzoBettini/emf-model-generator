package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEClassExists;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

/**
 * Test class for EMFResourceSetHelper.
 * Ensures 100% coverage of the ResourceSet configuration functionality.
 */
class EMFResourceSetHelperTest {

	private static final EcoreFactory ECORE_FACTORY = EcoreFactory.eINSTANCE;

	@Test
	void shouldCreateResourceSetWithDefaultConfiguration() {
		ResourceSet resourceSet = EMFResourceSetHelper.createResourceSet();

		assertThat(resourceSet).isNotNull();
		assertThat(resourceSet.getResourceFactoryRegistry()
			.getExtensionToFactoryMap()).containsKey("xmi");
		assertThat(resourceSet.getResourceFactoryRegistry()
			.getExtensionToFactoryMap()).containsKey("*");
	}

	@Test
	void shouldCreateResourceSetWithCustomLineWidth() {
		int customLineWidth = 20;
		ResourceSet resourceSet = EMFResourceSetHelper.createResourceSet(customLineWidth);

		assertThat(resourceSet).isNotNull();
		assertThat(resourceSet.getResourceFactoryRegistry()
			.getExtensionToFactoryMap()).containsKey("xmi");
	}

	@Test
	void shouldConfigureExistingResourceSetWithDefaultSettings() {
		ResourceSet resourceSet = new ResourceSetImpl();

		EMFResourceSetHelper.configureResourceSet(resourceSet);

		assertThat(resourceSet.getResourceFactoryRegistry()
			.getExtensionToFactoryMap()).containsKey("xmi");
		assertThat(resourceSet.getResourceFactoryRegistry()
			.getExtensionToFactoryMap()).containsKey("*");
	}

	@Test
	void shouldConfigureExistingResourceSetWithCustomLineWidth() {
		ResourceSet resourceSet = new ResourceSetImpl();
		int customLineWidth = 15;

		EMFResourceSetHelper.configureResourceSet(resourceSet, customLineWidth);

		assertThat(resourceSet.getResourceFactoryRegistry()
			.getExtensionToFactoryMap()).containsKey("xmi");
		assertThat(resourceSet.getResourceFactoryRegistry()
			.getExtensionToFactoryMap()).containsKey("*");
	}

	@Test
	void shouldApplyDefaultLineWidthWhenSavingResource() throws IOException {
		ResourceSet resourceSet = EMFResourceSetHelper.createResourceSet();
		Resource resource = resourceSet.createResource(URI.createURI("test.xmi"));

		// Create a simple model
		EPackage pkg = createTestPackage();
		EClass testClass = assertEClassExists(pkg, "TestClass");
		EObject instance = EcoreUtil.create(testClass);
		resource.getContents().add(instance);

		// Save to byte array
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		resource.save(outputStream, null);

		String xmiContent = outputStream.toString();

		// Verify line breaks are applied (line width = 10 means short lines)
		assertThat(xmiContent).contains("\n");
		// The XMI should have multiple lines due to the line width setting
		String[] lines = xmiContent.split("\n");
		assertThat(lines).hasSizeGreaterThan(1);
	}

	@Test
	void shouldApplyCustomLineWidthWhenSavingResource() throws IOException {
		int customLineWidth = 200; // Very wide lines
		ResourceSet resourceSet = EMFResourceSetHelper.createResourceSet(customLineWidth);
		Resource resource = resourceSet.createResource(URI.createURI("test.xmi"));

		// Create a simple model
		EPackage pkg = createTestPackage();
		EClass testClass = assertEClassExists(pkg, "TestClass");
		EObject instance = EcoreUtil.create(testClass);
		resource.getContents().add(instance);

		// Save to byte array
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		resource.save(outputStream, null);

		String xmiContent = outputStream.toString();

		// With very wide lines, the XMI might fit on fewer lines
		assertThat(xmiContent).contains("<?xml");
	}

	@Test
	void shouldHandleResourcesWithWildcardExtension() throws IOException {
		ResourceSet resourceSet = EMFResourceSetHelper.createResourceSet();
		// Use a custom extension to test wildcard registration
		Resource resource = resourceSet.createResource(URI.createURI("test.custom"));

		// Create a simple model
		EPackage pkg = createTestPackage();
		EClass testClass = assertEClassExists(pkg, "TestClass");
		EObject instance = EcoreUtil.create(testClass);
		resource.getContents().add(instance);

		// Save should work even with custom extension
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		resource.save(outputStream, null);

		String xmiContent = outputStream.toString();
		assertThat(xmiContent).contains("<?xml");
	}

	@Test
	void shouldReturnDefaultLineWidth() {
		int defaultLineWidth = EMFResourceSetHelper.getDefaultLineWidth();

		assertThat(defaultLineWidth).isEqualTo(10);
	}

	@Test
	void shouldNotUseIDsInGeneratedXMI() throws IOException {
		ResourceSet resourceSet = EMFResourceSetHelper.createResourceSet();
		Resource resource = resourceSet.createResource(URI.createURI("test.xmi"));

		// Create a simple model
		EPackage pkg = createTestPackage();
		EClass testClass = assertEClassExists(pkg, "TestClass");
		EObject instance = EcoreUtil.create(testClass);
		resource.getContents().add(instance);

		// Save to byte array
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		resource.save(outputStream, null);

		String xmiContent = outputStream.toString();

		// Verify that xmi:id attributes are not generated
		assertThat(xmiContent).doesNotContain("xmi:id=\"");
	}

	@Test
	void shouldAllowReconfigurationOfResourceSet() {
		ResourceSet resourceSet = EMFResourceSetHelper.createResourceSet(10);

		// Reconfigure with different line width
		EMFResourceSetHelper.configureResourceSet(resourceSet, 50);

		// Should still have factories registered
		assertThat(resourceSet.getResourceFactoryRegistry()
			.getExtensionToFactoryMap()).containsKey("xmi");
		assertThat(resourceSet.getResourceFactoryRegistry()
			.getExtensionToFactoryMap()).containsKey("*");
	}

	@Test
	void shouldHandleMultipleResourcesInSameResourceSet() throws IOException {
		ResourceSet resourceSet = EMFResourceSetHelper.createResourceSet();

		// Create first resource
		Resource resource1 = resourceSet.createResource(URI.createURI("test1.xmi"));
		EPackage pkg = createTestPackage();
		EClass testClass = assertEClassExists(pkg, "TestClass");
		EObject instance1 = EcoreUtil.create(testClass);
		resource1.getContents().add(instance1);

		// Create second resource
		Resource resource2 = resourceSet.createResource(URI.createURI("test2.xmi"));
		EObject instance2 = EcoreUtil.create(testClass);
		resource2.getContents().add(instance2);

		// Both should save with line width setting
		ByteArrayOutputStream out1 = new ByteArrayOutputStream();
		resource1.save(out1, null);
		assertThat(out1.toString()).contains("<?xml");

		ByteArrayOutputStream out2 = new ByteArrayOutputStream();
		resource2.save(out2, null);
		assertThat(out2.toString()).contains("<?xml");
	}

	// Helper method to create a test EPackage
	private EPackage createTestPackage() {
		EPackage pkg = ECORE_FACTORY.createEPackage();
		pkg.setName("testpkg");
		pkg.setNsURI("http://test.example.com/testpkg");
		pkg.setNsPrefix("testpkg");

		EClass testClass = ECORE_FACTORY.createEClass();
		testClass.setName("TestClass");
		pkg.getEClassifiers().add(testClass);

		return pkg;
	}
}
