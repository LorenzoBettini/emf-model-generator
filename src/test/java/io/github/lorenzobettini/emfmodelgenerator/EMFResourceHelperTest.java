package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResourceSet;
import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for EMFResourceHelper.
 */
class EMFResourceHelperTest {

	private EMFResourceHelper helper;
	private ResourceSet resourceSet;
	private EPackage testPackage;
	private EClass testClass1;
	private EClass testClass2;

	@BeforeEach
	void setup() {
		resourceSet = createResourceSet();
		helper = new EMFResourceHelper(resourceSet, "target/test-output");
		
		// Create test package and classes with unique nsURI per test run
		testPackage = EcoreFactory.eINSTANCE.createEPackage();
		testPackage.setName("testpkg");
		testPackage.setNsURI("http://test.example.com/testpkg/" + System.currentTimeMillis());
		testPackage.setNsPrefix("testpkg");
		
		testClass1 = EcoreFactory.eINSTANCE.createEClass();
		testClass1.setName("TestClass");
		testPackage.getEClassifiers().add(testClass1);
		
		testClass2 = EcoreFactory.eINSTANCE.createEClass();
		testClass2.setName("AnotherClass");
		testPackage.getEClassifiers().add(testClass2);
	}

	@Test
	void shouldReturnConfiguredOutputDirectory() {
		assertThat(helper.getOutputDirectory()).isEqualTo("target/test-output");
	}

	@Test
	void shouldAllowCustomOutputDirectory() {
		var customResourceSet = createResourceSet();
		var customHelper = new EMFResourceHelper(customResourceSet, "custom/output");
		assertThat(customHelper.getOutputDirectory()).isEqualTo("custom/output");
	}

	@Test
	void shouldReturnConfiguredResourceSet() {
		assertThat(helper.getResourceSet()).isSameAs(resourceSet);
	}

	@Test
	void shouldGenerateFileNameWithCounter() {
		String fileName = helper.generateFileName(testPackage, testClass1);
		assertThat(fileName).isEqualTo("testpkg_TestClass_1.xmi");
	}

	@Test
	void shouldGenerateFileNameWithPrefix() {
		helper.setFilePrefix("prefix_");
		String fileName = helper.generateFileName(testPackage, testClass1);
		assertThat(fileName).isEqualTo("prefix_testpkg_TestClass_1.xmi");
	}

	@Test
	void shouldIncrementCounterOnSubsequentCalls() {
		String fileName1 = helper.generateFileName(testPackage, testClass1);
		String fileName2 = helper.generateFileName(testPackage, testClass1);
		String fileName3 = helper.generateFileName(testPackage, testClass1);
		
		assertThat(fileName1).isEqualTo("testpkg_TestClass_1.xmi");
		assertThat(fileName2).isEqualTo("testpkg_TestClass_2.xmi");
		assertThat(fileName3).isEqualTo("testpkg_TestClass_3.xmi");
	}

	@Test
	void shouldMaintainSeparateCountersForDifferentEClasses() {
		String fileName1 = helper.generateFileName(testPackage, testClass1);
		String fileName2 = helper.generateFileName(testPackage, testClass2);
		String fileName3 = helper.generateFileName(testPackage, testClass1);
		String fileName4 = helper.generateFileName(testPackage, testClass2);
		
		assertThat(fileName1).isEqualTo("testpkg_TestClass_1.xmi");
		assertThat(fileName2).isEqualTo("testpkg_AnotherClass_1.xmi");
		assertThat(fileName3).isEqualTo("testpkg_TestClass_2.xmi");
		assertThat(fileName4).isEqualTo("testpkg_AnotherClass_2.xmi");
	}

	@Test
	void shouldResetCounters() {
		helper.generateFileName(testPackage, testClass1);
		helper.generateFileName(testPackage, testClass1);
		
		helper.resetCounters();
		
		String fileName = helper.generateFileName(testPackage, testClass1);
		assertThat(fileName).isEqualTo("testpkg_TestClass_1.xmi");
	}

	@Test
	void shouldGetCounterValue() {
		helper.generateFileName(testPackage, testClass1);
		helper.generateFileName(testPackage, testClass1);
		
		assertThat(helper.getCounter("TestClass")).isEqualTo(2);
	}

	@Test
	void shouldReturnZeroForUnusedClassName() {
		assertThat(helper.getCounter("NonExistent")).isZero();
	}

	@Test
	void shouldCreateFileURIInOutputDirectory() {
		URI uri = helper.createFileURI("test.xmi");
		
		assertThat(uri.isFile()).isTrue();
		assertThat(uri.toString()).endsWith("target/test-output/test.xmi");
	}

	@Test
	void shouldCreateFileURIWithCustomOutputDirectory() {
		var customResourceSet = createResourceSet();
		var customHelper = new EMFResourceHelper(customResourceSet, "custom/path");
		URI uri = customHelper.createFileURI("test.xmi");
		
		assertThat(uri.toString()).endsWith("custom/path/test.xmi");
	}

	@Test
	void shouldCreateResourceWithGeneratedName() {
		Resource resource = helper.createResource(testClass1);
		
		assertThat(resource).isNotNull();
		assertThat(resource.getURI().toString()).endsWith("testpkg_TestClass_1.xmi");
	}

	@Test
	void shouldAddCreatedResourceToResourceSet() {
		Resource resource = helper.createResource(testClass1);
		
		assertThat(helper.getResourceSet().getResources()).contains(resource);
	}

	@Test
	void shouldCreateMultipleResourcesWithIncrementingCounters() {
		Resource resource1 = helper.createResource(testClass1);
		Resource resource2 = helper.createResource(testClass1);
		
		assertThat(resource1.getURI().toString()).endsWith("testpkg_TestClass_1.xmi");
		assertThat(resource2.getURI().toString()).endsWith("testpkg_TestClass_2.xmi");
	}

	@Test
	void shouldUseProvidedResourceSet() {
		Resource resource = helper.createResource(testClass1);
		
		assertThat(resource).isNotNull();
		assertThat(helper.getResourceSet()).isSameAs(resourceSet);
	}

	@Test
	void shouldUseGlobalFileExtension() {
		helper.setGlobalFileExtension("json");
		String fileName = helper.generateFileName(testPackage, testClass1);
		assertThat(fileName).isEqualTo("testpkg_TestClass_1.json");
	}

	@Test
	void shouldUseEPackageFileExtension() {
		helper.setFileExtensionForEPackage(testPackage, "xml");
		String fileName = helper.generateFileName(testPackage, testClass1);
		assertThat(fileName).isEqualTo("testpkg_TestClass_1.xml");
	}

	@Test
	void shouldUseEClassFileExtension() {
		helper.setFileExtensionForEClass(testClass1, "custom");
		String fileName = helper.generateFileName(testPackage, testClass1);
		assertThat(fileName).isEqualTo("testpkg_TestClass_1.custom");
	}

	@Test
	void shouldPrioritizeEClassExtensionOverEPackage() {
		helper.setFileExtensionForEPackage(testPackage, "xml");
		helper.setFileExtensionForEClass(testClass1, "custom");
		
		String fileName1 = helper.generateFileName(testPackage, testClass1);
		String fileName2 = helper.generateFileName(testPackage, testClass2);
		
		assertThat(fileName1).isEqualTo("testpkg_TestClass_1.custom");
		assertThat(fileName2).isEqualTo("testpkg_AnotherClass_1.xml");
	}

	@Test
	void shouldPrioritizeEClassExtensionOverGlobal() {
		helper.setGlobalFileExtension("json");
		helper.setFileExtensionForEClass(testClass1, "custom");
		
		String fileName = helper.generateFileName(testPackage, testClass1);
		assertThat(fileName).isEqualTo("testpkg_TestClass_1.custom");
	}

	@Test
	void shouldPrioritizeEPackageExtensionOverGlobal() {
		helper.setGlobalFileExtension("json");
		helper.setFileExtensionForEPackage(testPackage, "xml");
		
		String fileName = helper.generateFileName(testPackage, testClass1);
		assertThat(fileName).isEqualTo("testpkg_TestClass_1.xml");
	}

	@Test
	void shouldFollowCompleteHierarchy() {
		// Setup all levels
		helper.setGlobalFileExtension("global");
		helper.setFileExtensionForEPackage(testPackage, "package");
		helper.setFileExtensionForEClass(testClass1, "class");
		
		// testClass1 has EClass-specific extension
		String fileName1 = helper.generateFileName(testPackage, testClass1);
		assertThat(fileName1).isEqualTo("testpkg_TestClass_1.class");
		
		// testClass2 falls back to EPackage extension
		String fileName2 = helper.generateFileName(testPackage, testClass2);
		assertThat(fileName2).isEqualTo("testpkg_AnotherClass_1.package");
		
		// Create a new package without extension - should use global
		EPackage otherPackage = EcoreFactory.eINSTANCE.createEPackage();
		otherPackage.setName("otherpkg");
		EClass otherClass = EcoreFactory.eINSTANCE.createEClass();
		otherClass.setName("OtherClass");
		otherPackage.getEClassifiers().add(otherClass);
		
		String fileName3 = helper.generateFileName(otherPackage, otherClass);
		assertThat(fileName3).isEqualTo("otherpkg_OtherClass_1.global");
	}

	@Test
	void shouldGetFileExtensionForEClass() {
		helper.setFileExtensionForEClass(testClass1, "custom");
		assertThat(helper.getFileExtension(testClass1)).isEqualTo("custom");
	}

	@Test
	void shouldGetFileExtensionForEPackage() {
		helper.setFileExtensionForEPackage(testPackage, "xml");
		assertThat(helper.getFileExtension(testClass1)).isEqualTo("xml");
	}

	@Test
	void shouldGetGlobalFileExtension() {
		helper.setGlobalFileExtension("json");
		assertThat(helper.getFileExtension(testClass1)).isEqualTo("json");
	}

	@Test
	void shouldGetDefaultFileExtension() {
		assertThat(helper.getFileExtension(testClass1)).isEqualTo("xmi");
	}

	@Test
	void shouldCreateResourceWithCustomExtension() {
		helper.setGlobalFileExtension("json");
		Resource resource = helper.createResource(testClass1);
		
		assertThat(resource.getURI().toString()).endsWith("testpkg_TestClass_1.json");
	}

	@Test
	void shouldCreateResourceWithEClassSpecificExtension() {
		helper.setFileExtensionForEClass(testClass1, "custom");
		Resource resource = helper.createResource(testClass1);
		
		assertThat(resource.getURI().toString()).endsWith("testpkg_TestClass_1.custom");
	}

	@Test
	void shouldCreateResourceWithEPackageSpecificExtension() {
		helper.setFileExtensionForEPackage(testPackage, "xml");
		Resource resource = helper.createResource(testClass1);
		
		assertThat(resource.getURI().toString()).endsWith("testpkg_TestClass_1.xml");
	}

	@Test
	void shouldUseCustomExtensionWithFilePrefix() {
		helper.setFilePrefix("prefix_");
		helper.setGlobalFileExtension("json");
		
		String fileName = helper.generateFileName(testPackage, testClass1);
		assertThat(fileName).isEqualTo("prefix_testpkg_TestClass_1.json");
	}

	@Test
	void shouldMaintainCountersAcrossDifferentExtensions() {
		helper.generateFileName(testPackage, testClass1);
		helper.setGlobalFileExtension("json");
		String fileName = helper.generateFileName(testPackage, testClass1);
		
		assertThat(fileName).isEqualTo("testpkg_TestClass_2.json");
	}
}
