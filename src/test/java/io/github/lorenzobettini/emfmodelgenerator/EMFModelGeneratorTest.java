package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertXMIMatchesExpected;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createContainmentEReference;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEClass;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEPackage;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEAttributeExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEClassExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEReferenceExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.loadEcoreModel;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.loadGeneratedModel;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.validateModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EMFModelGeneratorTest {

	private EMFModelGenerator generator;
	private static final String TEST_OUTPUT_DIR = "target/test-output";
	private static final String TEST_INPUTS_DIR = "target/inputs";
	private static final String EXPECTED_OUTPUTS_DIR = "src/test/resources/expected-outputs";
	
	@BeforeEach
	void setUp() throws IOException {
		generator = new EMFModelGenerator();
		generator.setOutputDirectory(TEST_OUTPUT_DIR);
		
		// Clean output directory before each test
		Path outputPath = Paths.get(TEST_OUTPUT_DIR);
		if (Files.exists(outputPath)) {
			Files.walk(outputPath)
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
		}
		Files.createDirectories(outputPath);
	}
	
	@AfterEach
	void tearDown() {
		// Clean up registered EPackages
		EMFTestUtils.cleanupRegisteredPackages();
		generator.unloadEcoreModels();
		// Optionally keep output files for inspection
	}
	
	@Test
	void testGenerateFromSimpleEClass() throws Exception {
		// Load the simple.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simple.ecore");
		assertNotNull(ePackage, "EPackage should be loaded");
		
		// Get the Person EClass
		EClass personClass = assertEClassExists(ePackage, "Person");
		
		// Generate model
		EObject generatedObject = generator.generateFrom(personClass);
		assertNotNull(generatedObject, "Generated object should not be null");
		assertThat(generatedObject.eClass().getName()).isEqualTo("Person");
		
		// Save all generated models
		generator.save();
		
		// Verify output file exists
		File outputFile = new File(TEST_OUTPUT_DIR, "simple_Person_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and verify the generated model
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject, "Generated object should not be null");
		assertThat(loadedObject.eClass().getName()).isEqualTo("Person");
		
		// Validate the generated model
		validateModel(loadedObject);
		
		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "simple_Person_1.xmi", "simple_Person_1.xmi");
		
		// Verify attributes
		Object nameValue = loadedObject.eGet(assertEAttributeExists(personClass, "name"));
		assertNotNull(nameValue, "Name attribute should be set");
		assertThat(nameValue).isEqualTo("Person_name_1");
		
		Object ageValue = loadedObject.eGet(assertEAttributeExists(personClass, "age"));
		assertNotNull(ageValue, "Age attribute should be set");
		assertThat(ageValue).isEqualTo(20);
	}
	
	@Test
	void testGenerateFromEPackage() throws Exception {
		// Load the simple.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simple.ecore");
		
		// Generate from package (should generate first classifier)
		EObject generatedObject = generator.generateFrom(ePackage);
		assertNotNull(generatedObject);
		
		// Save all generated models
		generator.save();
		
		// Verify output file exists
		File outputFile = new File(TEST_OUTPUT_DIR, "simple_Person_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and validate the generated model
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject);
		validateModel(loadedObject);
		
		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "simple_Person_1.xmi", "simple_Person_from_package.xmi");
	}
	
	/**
	 * The Ecore could be improved because Employees can end up being
	 * their own managers.
	 * 
	 * @throws Exception
	 */
	@Test
	void testGenerateFromMultipleEClasses() throws Exception {
		// Load the references.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "references.ecore");
		
		EClass companyClass = assertEClassExists(ePackage, "Company");
		EClass employeeClass = assertEClassExists(ePackage, "Employee");
		
		// Generate models for both classes
		List<EObject> generatedModels = generator.generateFromSeveral(companyClass, employeeClass);
		assertNotNull(generatedModels);
		assertThat(generatedModels).hasSize(2);
		
		/*
		 * After object generation, we have two root objects:
		 * - Company
		 * - Employee
		 * During population, the Company will create its own Employees.
		 * During cross-reference resolution, the assignable Employee instances are:
		 * - Company.employee[0], Company.employee[1], Employee in its own resource
		 * the objects are processed as follows:
		 * - Company: its ceo is set to Company.employee[0]
		 * - Employee (root): its manager is set to Company.employee[1] (due to round-robin)
		 * - Company.employee[0]: its manager is set to Employee (root) (avoiding self-reference)
		 * - Company.employee[1]: its manager is set to Company.employee[0] (due to round-robin)
		 */
		
		// Save all generated models
		generator.save();
		
		// Verify both output files exist
		File companyFile = new File(TEST_OUTPUT_DIR, "company_Company_1.xmi");
		File employeeFile = new File(TEST_OUTPUT_DIR, "company_Employee_1.xmi");
		
		assertThat(companyFile).exists();
		assertThat(employeeFile).exists();
		
		// Load and validate both models
		EObject companyObject = loadGeneratedModel(companyFile, ePackage);
		EObject employeeObject = loadGeneratedModel(employeeFile, ePackage);
		assertNotNull(companyObject);
		assertNotNull(employeeObject);
		validateModel(companyObject);
		validateModel(employeeObject);
		
		// Compare with expected outputs
		assertXMIMatchesExpected(TEST_OUTPUT_DIR,
				EXPECTED_OUTPUTS_DIR,
				"company_Company_1.xmi",
				"company_Company_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR,
				EXPECTED_OUTPUTS_DIR,
				"company_Employee_1.xmi",
				"company_Employee_1.xmi");
	}
	
	@Test
	void testGenerateWithReferences() throws Exception {
		// Load the references.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "references.ecore");
		
		EClass companyClass = assertEClassExists(ePackage, "Company");
		
		// Generate Company model (should include employees via containment)
		EObject generatedObject = generator.generateFrom(companyClass);
		assertNotNull(generatedObject);
		
		// Save all generated models
		generator.save();

		File outputFile = new File(TEST_OUTPUT_DIR, "company_Company_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and verify
		EObject companyObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(companyObject);
		
		// Validate the model
		validateModel(companyObject);
		
		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"company_Company_1.xmi",
				"company_with_references.xmi");
		
		// Verify name attribute
		Object nameValue = companyObject.eGet(assertEAttributeExists(companyClass, "name"));
		assertThat(nameValue).isEqualTo("Company_name_1");
		
		// Verify employees reference (containment, many)
		var employees = EMFUtils.getAsEObjectsList(companyObject, assertEReferenceExists(companyClass, "employees"));
		assertNotNull(employees, "Employees should be set");
		assertThat(employees).hasSize(2);
		
		// Verify employee attributes
		EObject employee = employees.get(0);
		assertThat(employee.eClass().getName()).isEqualTo("Employee");
		Object empName = employee.eGet(employee.eClass().getEStructuralFeature("name"));
		assertThat(empName).isEqualTo("Employee_name_1");
	}
	
	@Test
	void testGenerateWithDifferentDataTypes() throws Exception {
		// Load the datatypes.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "datatypes.ecore");
		
		EClass dataHolderClass = assertEClassExists(ePackage, "DataHolder");
		
		// Generate model
		EObject generatedObject = generator.generateFrom(dataHolderClass);
		assertNotNull(generatedObject);
		
		// Save all generated models
		generator.save();
		
		File outputFile = new File(TEST_OUTPUT_DIR, "datatypes_DataHolder_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and verify
		EObject dataHolder = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(dataHolder);
		
		// Validate the model
		validateModel(dataHolder);
		
		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "datatypes_DataHolder_1.xmi", "datatypes_DataHolder_1.xmi");
		
		// Verify string value
		Object stringValue = dataHolder.eGet(dataHolderClass.getEStructuralFeature("stringValue"));
		assertThat(stringValue).isEqualTo("DataHolder_stringValue_1");
		
		// Verify int value
		Object intValue = dataHolder.eGet(dataHolderClass.getEStructuralFeature("intValue"));
		assertThat(intValue).isEqualTo(20);
		
		// Verify boolean value
		Object boolValue = dataHolder.eGet(dataHolderClass.getEStructuralFeature("boolValue"));
		assertThat(boolValue).isEqualTo(true);
		
		// Verify double value
		Object doubleValue = dataHolder.eGet(dataHolderClass.getEStructuralFeature("doubleValue"));
		assertThat(doubleValue).isEqualTo(20.5);
		
		// Verify date value
		Object dateValue = dataHolder.eGet(dataHolderClass.getEStructuralFeature("dateValue"));
		assertNotNull(dateValue);
		
		// Verify multi-valued attribute (tags)
		var tags = EMFUtils.getAsList(dataHolder, assertEAttributeExists(dataHolderClass, "tags"));
		assertNotNull(tags);
		assertThat(tags)
			.hasSize(2)
			.containsExactly("DataHolder_tags_1", "DataHolder_tags_2");
	}
	
	@Test
	void testOutputDirectoryConfiguration() throws Exception {
		// Test custom output directory
		String customDir = "target/custom-output";
		generator.setOutputDirectory(customDir);
		
		assertThat(generator.getOutputDirectory()).isEqualTo(customDir);
		
		// Generate model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simple.ecore");
		EClass personClass = assertEClassExists(ePackage, "Person");
		EObject generatedObject = generator.generateFrom(personClass);
		assertNotNull(generatedObject);
		
		// Save all generated models
		generator.save();
		
		// Verify file in custom directory
		File outputFile = new File(customDir, "simple_Person_1.xmi");
		assertThat(outputFile).exists();
		
		// Cleanup
		Files.walk(Paths.get(customDir))
			.sorted(Comparator.reverseOrder())
			.map(Path::toFile)
			.forEach(File::delete);
	}

	@Test
	void testGenerateAttributeValue_FullCoverage() throws Exception {
		// Load the alltypes.ecore model which exercises all generateAttributeValue branches
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "alltypes.ecore");
		assertNotNull(ePackage, "alltypes EPackage should be loaded");
		
		EClass allTypesHolderClass = assertEClassExists(ePackage, "AllTypesHolder");
		
		// Generate model
		EObject generatedObject = generator.generateFrom(allTypesHolderClass);
		assertNotNull(generatedObject, "Generated object should not be null");
		assertThat(generatedObject.eClass().getName()).isEqualTo("AllTypesHolder");
		
		// Save all generated models
		generator.save();
		
		// Verify output file exists
		File outputFile = new File(TEST_OUTPUT_DIR, "alltypes_AllTypesHolder_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and verify the generated model
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject, "Generated object should not be null");
		assertThat(loadedObject.eClass().getName()).isEqualTo("AllTypesHolder");
		
		// Validate the generated model
		validateModel(loadedObject);
		
		// Compare with expected output - this is the main verification
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "alltypes_AllTypesHolder_1.xmi", "alltypes_AllTypesHolder_1.xmi");
	}

	/**
	 * Test that generateFrom(EPackage) searches for the first EClass
	 * when the first classifier is not an EClass (it's an EDataType).
	 */
	@Test
	void testGenerateFromEPackageWithNonEClassFirst() throws Exception {
		// Load the alltypes.ecore model where the first classifiers are EDataTypes
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "alltypes.ecore");
		assertNotNull(ePackage, "alltypes EPackage should be loaded");
		
		// Generate from package (should skip EDataTypes and find AllTypesHolder EClass)
		EObject generatedObject = generator.generateFrom(ePackage);
		assertNotNull(generatedObject, "Generated object should not be null");
		assertThat(generatedObject.eClass().getName()).isEqualTo("AllTypesHolder");
		
		// Save all generated models
		generator.save();
		
		// Verify output file exists
		File outputFile = new File(TEST_OUTPUT_DIR, "alltypes_AllTypesHolder_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and validate the generated model
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject, "Generated object should not be null");
		assertThat(loadedObject.eClass().getName()).isEqualTo("AllTypesHolder");
		validateModel(loadedObject);
	}

	/**
	 * Test that generateFrom(EPackage) throws an exception when no EClass is found.
	 */
	@Test
	void testGenerateFromEPackageWithNoEClass() {
		// Load the noeclass.ecore model which has only EDataTypes and EEnum
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "noeclass.ecore");
		assertNotNull(ePackage, "noeclass EPackage should be loaded");
		
		// Verify the package has classifiers but no EClass
		assertThat(ePackage.getEClassifiers()).isNotEmpty();
		
		// Attempt to generate from package should throw IllegalArgumentException
		assertThatThrownBy(() -> generator.generateFrom(ePackage))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("No instantiable EClass found in EPackage")
			.hasMessageContaining("noeclass");
	}

	/**
	 * Test that generateFrom(EPackage) correctly finds EClass when it's not the first classifier.
	 */
	@Test
	void testGenerateFromEPackageFindsEClassNotFirst() throws Exception {
		// Load the alltypes.ecore model where EClass is after many EDataTypes
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "alltypes.ecore");
		
		// Verify the first classifier is NOT an EClass
		assertThat(ePackage.getEClassifiers().get(0)).isNotInstanceOf(EClass.class);
		
		// Generate from package should find the EClass (AllTypesHolder)
		EObject generatedObject = generator.generateFrom(ePackage);
		assertNotNull(generatedObject);
		assertThat(generatedObject.eClass().getName()).isEqualTo("AllTypesHolder");
		
		// Save and verify
		generator.save();
		File outputFile = new File(TEST_OUTPUT_DIR, "alltypes_AllTypesHolder_1.xmi");
		assertThat(outputFile).exists();
	}

	/**
	 * Test that generateFrom(EPackage) skips abstract classes and finds first instantiable EClass.
	 */
	@Test
	void testGenerateFromEPackageSkipsAbstractClass() throws Exception {
		// Load the abstract-first.ecore model where first EClass is abstract
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "abstract-first.ecore");
		assertNotNull(ePackage, "abstract-first EPackage should be loaded");
		
		// Verify the first classifier is an abstract EClass
		assertThat(ePackage.getEClassifiers().get(0)).isInstanceOf(EClass.class);
		EClass firstClass = (EClass) ePackage.getEClassifiers().get(0);
		assertThat(firstClass.isAbstract()).isTrue();
		assertThat(firstClass.getName()).isEqualTo("AbstractBase");
		
		// Generate from package should skip abstract class and find ConcreteClass
		EObject generatedObject = generator.generateFrom(ePackage);
		assertNotNull(generatedObject);
		assertThat(generatedObject.eClass().getName()).isEqualTo("ConcreteClass");
		
		// Save and verify
		generator.save();
		File outputFile = new File(TEST_OUTPUT_DIR, "abstractfirst_ConcreteClass_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and validate
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject);
		assertThat(loadedObject.eClass().getName()).isEqualTo("ConcreteClass");
		validateModel(loadedObject);
		
		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "abstractfirst_ConcreteClass_1.xmi", "abstractfirst_ConcreteClass_1.xmi");
	}

	/**
	 * Test that generateFrom(EPackage) throws exception when only abstract/interface classes exist.
	 */
	@Test
	void testGenerateFromEPackageWithOnlyAbstractClasses() {
		// Create an in-memory Ecore package with only abstract/interface classes
		EPackage ePackage = org.eclipse.emf.ecore.EcoreFactory.eINSTANCE.createEPackage();
		ePackage.setName("onlyabstract");
		ePackage.setNsURI("http://www.example.org/onlyabstract");
		ePackage.setNsPrefix("onlyabstract");
		
		// Add an abstract EClass
		EClass abstractClass = org.eclipse.emf.ecore.EcoreFactory.eINSTANCE.createEClass();
		abstractClass.setName("AbstractClass");
		abstractClass.setAbstract(true);
		ePackage.getEClassifiers().add(abstractClass);
		
		// Add an interface EClass
		EClass interfaceClass = org.eclipse.emf.ecore.EcoreFactory.eINSTANCE.createEClass();
		interfaceClass.setName("InterfaceClass");
		interfaceClass.setInterface(true);
		ePackage.getEClassifiers().add(interfaceClass);
		
		// Attempt to generate from package should throw IllegalArgumentException
		assertThatThrownBy(() -> generator.generateFrom(ePackage))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("No instantiable EClass found in EPackage")
			.hasMessageContaining("onlyabstract");
	}

	/**
	 * Department has nested containments
	 * 
	 * @throws Exception
	 */
	@Test
	void testGenerateWithNestedContainmentAndCrossReferences() throws Exception {
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "department.ecore");
		
		EClass organizationClass = assertEClassExists(ePackage, "Organization");
		
		// Generate model
		EObject generatedObject = generator.generateFrom(organizationClass);
		assertNotNull(generatedObject);
		
		// Save the generated models
		generator.save();
		
		// Verify output file exists
		File outputFile = new File(TEST_OUTPUT_DIR, "dept_Organization_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and validate
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject);
		assertThat(loadedObject.eClass().getName()).isEqualTo("Organization");
		validateModel(loadedObject);
		
		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "dept_Organization_1.xmi", "dept_Organization_1.xmi");
		
		// Verify the structure was created correctly
		// Organization should have departments
		var departments = EMFUtils.getAsEObjectsList(loadedObject, assertEReferenceExists(organizationClass, "departments"));
		assertThat(departments).hasSize(2);
		
		// Each department should have teams and a manager reference
		EClass departmentClass = assertEClassExists(ePackage, "Department");
		EObject dept = departments.get(0);
		
		var teams = EMFUtils.getAsEObjectsList(dept, assertEReferenceExists(departmentClass, "teams"));
		assertThat(teams).hasSize(2);
		
		// Manager reference should be set
		Object manager = dept.eGet(assertEReferenceExists(departmentClass, "manager"));
		assertThat(manager).isNotNull();
	}
	
	@Test
	void testGenerateWithNestedContainmentAndCrossReferencesMultipleFiles() throws Exception {
		// Test with multiple unrelated classes to trigger container search logic
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "department.ecore");
		
		EClass departmentClass = assertEClassExists(ePackage, "Department");
		EClass personClass = assertEClassExists(ePackage, "Person");
		
		// Generate both Person and Department independently
		// but during cross-reference resolutions they will be connected
		// by generating the Person resource first, it will be used for resolving
		// the first cross-reference of Department (i.e., manager)
		List<EObject> models = generator.generateFromSeveral(personClass, departmentClass);
		assertThat(models).hasSize(2);
		
		// Save the generated models
		generator.save();
		
		// Each class gets its own counter, so both should be _1
		File deptFile = new File(TEST_OUTPUT_DIR, "dept_Department_1.xmi");
		File personFile = new File(TEST_OUTPUT_DIR, "dept_Person_1.xmi");
		assertThat(deptFile).exists();
		assertThat(personFile).exists();
		
		// Load and validate both
		EObject deptObject = loadGeneratedModel(deptFile, ePackage);
		EObject personObject = loadGeneratedModel(personFile, ePackage);
		
		assertNotNull(deptObject);
		assertNotNull(personObject);
		assertThat(deptObject.eClass().getName()).isEqualTo("Department");
		assertThat(personObject.eClass().getName()).isEqualTo("Person");
		
		validateModel(deptObject);
		validateModel(personObject);
		
		// Compare with expected outputs
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "dept_Department_1.xmi", "dept_Department_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "dept_Person_1.xmi", "dept_Person_1.xmi");
	}

	@Test
	void testNodeDoesNotReferToItself() throws Exception {
		// Test graph model with Nodes and Edges
		// Graph contains both Nodes (containment) and Edges (containment)
		// Edge has target reference to Node (non-containment)
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "graph.ecore");
		
		EClass nodeClass = assertEClassExists(ePackage, "Node");
		EClass edgeClass = assertEClassExists(ePackage, "Edge");
		
		// Generate Node and Edge independently
		List<EObject> models = generator.generateFromSeveral(nodeClass, edgeClass);
		assertThat(models).hasSize(2);
		
		// Save the generated models
		generator.save();
		
		// Verify both output files exist
		File nodeFile = new File(TEST_OUTPUT_DIR, "graph_Node_1.xmi");
		File edgeFile = new File(TEST_OUTPUT_DIR, "graph_Edge_1.xmi");
		assertThat(nodeFile).exists();
		assertThat(edgeFile).exists();
		
		// Load and validate both
		EObject node = loadGeneratedModel(nodeFile, ePackage);
		EObject edge = loadGeneratedModel(edgeFile, ePackage);
		
		assertNotNull(node);
		assertNotNull(edge);
		assertThat(node.eClass().getName()).isEqualTo("Node");
		assertThat(edge.eClass().getName()).isEqualTo("Edge");
		
		validateModel(node);
		validateModel(edge);
		
		// Compare with expected outputs
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "graph_Node_1.xmi", "graph_Node_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "graph_Edge_1.xmi", "graph_Edge_1.xmi");
		
		// Edge should have a target Node reference
		Object target = edge.eGet(assertEReferenceExists(edgeClass, "target"));
		assertThat(target).isInstanceOf(EObject.class);
		
		// Node could only has itself as outgoing reference, which should be skipped
		var outgoing = EMFUtils.getAsEObjectsList(node, assertEReferenceExists(nodeClass, "outgoing"));
		assertThat(outgoing).isEmpty();
	}

	/**
	 * Corner case test: Abstract and Interface references should be skipped.
	 * 
	 * The Ecore model contains references to abstract classes and interfaces,
	 * which cannot be instantiated. The generator should skip these references.
	 * 
	 * Expected behavior:
	 * - abstractItem reference (to AbstractItem abstract class) should be null
	 * - interfaceItem reference (to InterfaceItem interface) should be null
	 * - concreteItem reference (to ConcreteItem concrete class) should be instantiated
	 */
	@Test
	void testSkipAbstractAndInterfaceReferences() throws Exception {
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "abstract-references.ecore");
		
		EClass containerClass = assertEClassExists(ePackage, "Container");
		
		// Generate Container model
		EObject generatedObject = generator.generateFrom(containerClass);
		assertNotNull(generatedObject);
		
		// Save the generated model
		generator.save();
		
		// Verify output file exists
		File outputFile = new File(TEST_OUTPUT_DIR, "abstractrefs_Container_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and validate
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject);
		assertThat(loadedObject.eClass().getName()).isEqualTo("Container");
		validateModel(loadedObject);
		
		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "abstractrefs_Container_1.xmi", "abstractrefs_Container_1.xmi");
		
		// Verify that abstract and interface references are NOT instantiated
		Object abstractItem = loadedObject.eGet(containerClass.getEStructuralFeature("abstractItem"));
		assertThat(abstractItem).as("Abstract class reference should not be instantiated").isNull();
		
		Object interfaceItem = loadedObject.eGet(containerClass.getEStructuralFeature("interfaceItem"));
		assertThat(interfaceItem).as("Interface reference should not be instantiated").isNull();
		
		// Verify that unchangeable reference is NOT set
		Object unchangeableItem = loadedObject.eGet(containerClass.getEStructuralFeature("unchangeableItem"));
		assertThat(unchangeableItem).as("Unchangeable reference should not be set").isNull();
		
		// Verify that concrete containment reference IS instantiated
		Object concreteItem = loadedObject.eGet(containerClass.getEStructuralFeature("concreteItem"));
		assertThat(concreteItem).as("Concrete class reference should be instantiated").isNotNull();
	}

	@Test
	void testWarehouse() throws Exception {
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "warehouse.ecore");
		
		EClass warehouseClass = assertEClassExists(ePackage, "Warehouse");
		
		// Generate Warehouse model
		EObject generatedObject = generator.generateFrom(warehouseClass);
		assertNotNull(generatedObject);
		
		// Save the generated model
		generator.save();
		
		// Verify output file exists
		File outputFile = new File(TEST_OUTPUT_DIR, "warehouse_Warehouse_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and validate
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject);
		assertThat(loadedObject.eClass().getName()).isEqualTo("Warehouse");
		validateModel(loadedObject);
		
		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "warehouse_Warehouse_1.xmi", "warehouse_Warehouse_1.xmi");
		
		// Verify the structure
		var products = EMFUtils.getAsEObjectsList(loadedObject, assertEReferenceExists(warehouseClass, "products"));
		assertThat(products).hasSize(2);
		
		// Verify that mainProduct reference reuses an existing product from the pool
		Object mainProduct = loadedObject.eGet(assertEReferenceExists(warehouseClass, "mainProduct"));
		assertThat(mainProduct).isNotNull();
		
		// The mainProduct should be one of the products in the containment list
		assertThat(products).contains((EObject) mainProduct);
	}

	/**
	 * The standalone-reference model has:
	 * - Document has relatedDoc reference (non-containment to RelatedDocument)
	 * - RelatedDocument is NOT contained anywhere as a containment
	 */
	@Test
	void testReferenceToNonContainedType() throws Exception {
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "standalone-reference.ecore");
		
		EClass documentClass = assertEClassExists(ePackage, "Document");
		
		assertEClassExists(ePackage, "RelatedDocument");
		
		// Generate Document model and RelatedDocument model
		// Since RelatedDocument has no containment
		List<EObject> allFrom = generator.generateAllFrom(ePackage);
		assertThat(allFrom).hasSize(2);
		
		// Save the generated model
		generator.save();
		
		// Verify output file exists
		File documentFile = new File(TEST_OUTPUT_DIR, "standalone_Document_1.xmi");
		assertThat(documentFile).exists();
		File relatedDocFile = new File(TEST_OUTPUT_DIR, "standalone_RelatedDocument_1.xmi");
		assertThat(relatedDocFile).exists();
		
		// Load and validate
		EObject loadedDocument = loadGeneratedModel(documentFile, ePackage);
		assertNotNull(loadedDocument);
		assertThat(loadedDocument.eClass().getName()).isEqualTo("Document");
		// use the same resource set or the referenced object would be different
		EObject loadedRelatedDoc = loadGeneratedModel(relatedDocFile, ePackage,
				loadedDocument.eResource().getResourceSet());
		assertNotNull(loadedRelatedDoc);
		assertThat(loadedRelatedDoc.eClass().getName()).isEqualTo("RelatedDocument");
		validateModel(loadedDocument);
		validateModel(loadedRelatedDoc);
		
		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "standalone_Document_1.xmi", "standalone_Document_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "standalone_RelatedDocument_1.xmi", "standalone_RelatedDocument_1.xmi");
		
		// Verify that Document has a relatedDoc reference
		Object relatedDoc = loadedDocument.eGet(documentClass.getEStructuralFeature("relatedDoc"));
		assertThat(relatedDoc).isSameAs(loadedRelatedDoc);
		
		// The RelatedDocument should NOT be contained
		EObject relatedDocObj = (EObject) relatedDoc;
		assertThat(relatedDocObj.eContainer()).as("RelatedDocument should not be contained").isNull();
	}

	/**
	 * The standalone-references model has:
	 * - Document has "relatedDocs" reference (non-containment to RelatedDocument)
	 * - RelatedDocument is NOT contained anywhere as a containment has opposite
	 * reference "document" to Document
	 */
	@Test
	void testReferenceToNonContainedTypeMulti() throws Exception {
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "standalone-references.ecore");
		
		EClass documentClass = assertEClassExists(ePackage, "Document");
		
		EClass relatedDocumentClass = assertEClassExists(ePackage, "RelatedDocument");
		
		// Generate a Document model and two RelatedDocument models
		// Since RelatedDocument has no containment
		List<EObject> allFrom = generator.generateFromSeveral(documentClass, relatedDocumentClass, relatedDocumentClass);
		assertThat(allFrom).hasSize(3); // 1 Document + 2 RelatedDocuments
		
		// Save the generated model
		generator.save();
		
		// Verify output file exists
		File documentFile = new File(TEST_OUTPUT_DIR, "standalones_Document_1.xmi");
		assertThat(documentFile).exists();
		File relatedDocFile1 = new File(TEST_OUTPUT_DIR, "standalones_RelatedDocument_1.xmi");
		assertThat(relatedDocFile1).exists();
		File relatedDocFile2 = new File(TEST_OUTPUT_DIR, "standalones_RelatedDocument_2.xmi");
		assertThat(relatedDocFile2).exists();
		
		// Load and validate
		EObject loadedDocument = loadGeneratedModel(documentFile, ePackage);
		assertNotNull(loadedDocument);
		assertThat(loadedDocument.eClass().getName()).isEqualTo("Document");
		
		EObject loadedRelatedDoc1 = loadGeneratedModel(relatedDocFile1, ePackage,
				loadedDocument.eResource().getResourceSet());
		assertNotNull(loadedRelatedDoc1);
		assertThat(loadedRelatedDoc1.eClass().getName()).isEqualTo("RelatedDocument");
		
		EObject loadedRelatedDoc2 = loadGeneratedModel(relatedDocFile2, ePackage,
				loadedDocument.eResource().getResourceSet());
		assertNotNull(loadedRelatedDoc2);
		assertThat(loadedRelatedDoc2.eClass().getName()).isEqualTo("RelatedDocument");
		
		validateModel(loadedDocument);
		validateModel(loadedRelatedDoc1);
		validateModel(loadedRelatedDoc2);
		
		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"standalones_Document_1.xmi", "standalonemulti_Document_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"standalones_RelatedDocument_1.xmi", "standalonemulti_RelatedDocument_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"standalones_RelatedDocument_2.xmi", "standalonemulti_RelatedDocument_2.xmi");
	}

	/**
	 * The container-search model has:
	 * - Registry has entries (multi-valued containment for Entry)
	 * - Registry has entry (single-valued containment for Entry)
	 * - Link has target (cross reference to Entry)
	 * - Link has targets (cross multi-reference to Entries)
	 * 
	 * When generating both Registry and Link:
	 * - Registry is generated first, creating Entry instances in containment
	 * - Link is generated second, and its target/targets references will reuse
	 *   existing Entry instances from the Registry
	 * 
	 * This tests the round-robin reuse mechanism and verifies that the
	 * instance pool is properly managed across multiple root objects.
	 */
	@Test
	void testSingleAndMultiReferencesOfTheSameType() throws Exception {
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "container-search.ecore");
		
		EClass registryClass = assertEClassExists(ePackage, "Registry");
		EClass linkClass = assertEClassExists(ePackage, "Link");
		
		// Generate both Registry and Link
		// Registry will create Entry instances in containment
		// Link will reference those existing Entries
		List<EObject> models = generator.generateFromSeveral(registryClass, linkClass);
		assertThat(models).hasSize(2);
		
		// Save the generated models
		generator.save();
		
		// Verify both output files exist
		File registryFile = new File(TEST_OUTPUT_DIR, "container_Registry_1.xmi");
		File linkFile = new File(TEST_OUTPUT_DIR, "container_Link_1.xmi");
		assertThat(registryFile).exists();
		assertThat(linkFile).exists();
		
		// Load and validate both
		EObject registry = loadGeneratedModel(registryFile, ePackage);
		EObject link = loadGeneratedModel(linkFile, ePackage);
		
		assertNotNull(registry);
		assertNotNull(link);
		assertThat(registry.eClass().getName()).isEqualTo("Registry");
		assertThat(link.eClass().getName()).isEqualTo("Link");
		
		validateModel(registry);
		validateModel(link);
		
		// Compare with expected outputs
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "container_Registry_1.xmi", "container_Registry_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "container_Link_1.xmi", "container_Link_1.xmi");
		
		// Verify Registry has entries
		var entries = EMFUtils.getAsEObjectsList(registry, assertEReferenceExists(registryClass, "entries"));
		assertThat(entries).hasSize(2);
		
		Object singleEntry = registry.eGet(assertEReferenceExists(registryClass, "entry"));
		assertThat(singleEntry).isNotNull();
		
		Object target = link.eGet(assertEReferenceExists(linkClass, "target"));
		assertThat(target).as("Link should have a target reference").isNotNull();
		
		var targets = EMFUtils.getAsEObjectsList(link, assertEReferenceExists(linkClass, "targets"));
		assertThat(targets).as("Link should have targets references").hasSize(2);
	}
	
	/**
	 * Test that a generator can accept an external ResourceSet and reuse it across multiple calls.
	 */
	@Test
	void testGeneratorWithExternalResourceSet() throws Exception {
		// Load the simple.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simple.ecore");
		
		// Create an external ResourceSet
		ResourceSet externalResourceSet = EMFResourceSetHelper.createResourceSet();
		externalResourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
		
		// Create generator with external ResourceSet
		EMFModelGenerator generatorWithExternal = new EMFModelGenerator(externalResourceSet);
		generatorWithExternal.setOutputDirectory(TEST_OUTPUT_DIR);
		
		// Get the Person EClass
		EClass personClass = assertEClassExists(ePackage, "Person");
		
		// Generate first model
		EObject person1 = generatorWithExternal.generateFrom(personClass);
		assertNotNull(person1);
		assertThat(person1.eClass().getName()).isEqualTo("Person");
		
		// Generate second model - should use same ResourceSet
		EObject person2 = generatorWithExternal.generateFrom(personClass);
		assertNotNull(person2);
		assertThat(person2.eClass().getName()).isEqualTo("Person");
		
		// Save all generated models
		generatorWithExternal.save();
		
		// Verify both resources are in the same ResourceSet
		assertThat(externalResourceSet.getResources()).hasSize(2);
		
		// Verify both output files exist
		File person1File = new File(TEST_OUTPUT_DIR, "simple_Person_1.xmi");
		File person2File = new File(TEST_OUTPUT_DIR, "simple_Person_2.xmi");
		assertThat(person1File).exists();
		assertThat(person2File).exists();
		
		// Load and validate both models
		EObject loadedPerson1 = loadGeneratedModel(person1File, ePackage);
		EObject loadedPerson2 = loadGeneratedModel(person2File, ePackage);
		assertNotNull(loadedPerson1);
		assertNotNull(loadedPerson2);
		validateModel(loadedPerson1);
		validateModel(loadedPerson2);
	}
	
	/**
	 * Test that getResourceSet returns the ResourceSet provided in the constructor.
	 */
	@Test
	void testGetResourceSetWithExternalResourceSet() {
		// Create an external ResourceSet
		ResourceSet externalResourceSet = EMFResourceSetHelper.createResourceSet();
		
		// Create generator with external ResourceSet
		EMFModelGenerator generatorWithExternal = new EMFModelGenerator(externalResourceSet);
		
		// Verify the same ResourceSet is returned
		assertThat(generatorWithExternal.getResourceSet()).isSameAs(externalResourceSet);
	}
	

	/**
	 * Deep hierarchy with single reference that has multi-opposite.
	 * @throws Exception
	 */
	@Test
	void testDeepHierarchyWithSingleReferenceWithMultiOpposite() throws Exception {
		// With maxDepth=5 (default), this test creates a deep hierarchy that exercises
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "deep-hierarchy.ecore");
		
		EClass rootClass = assertEClassExists(ePackage, "Root");
		EClass standaloneClass = assertEClassExists(ePackage, "Standalone");
		// Generate Root model with deep hierarchy
		List<EObject> fromSeveral = generator.generateFromSeveral(rootClass, standaloneClass);
		assertThat(fromSeveral).hasSize(2);

		// Save the generated model
		generator.save();
		
		// Verify output files exist
		File outputFile = new File(TEST_OUTPUT_DIR, "deephier_Root_1.xmi");
		assertThat(outputFile).exists();
		File standaloneFile = new File(TEST_OUTPUT_DIR, "deephier_Standalone_1.xmi");
		assertThat(standaloneFile).exists();
		
		// Load and validate
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject);
		assertThat(loadedObject.eClass().getName()).isEqualTo("Root");
		validateModel(loadedObject);
		EObject loadedStandalone = loadGeneratedModel(standaloneFile, ePackage,
				loadedObject.eResource().getResourceSet());
		assertNotNull(loadedStandalone);
		assertThat(loadedStandalone.eClass().getName()).isEqualTo("Standalone");
		validateModel(loadedStandalone);

		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"deephier_Root_1.xmi", "deephier_Root_1.xmi");		// Verify the deep structure was created
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"deephier_Standalone_1.xmi", "deephier_Standalone_1.xmi");
	}

	/**
	 * Test generateAllFrom method with a package containing multiple EClasses.
	 * Should generate an instance for each instantiable EClass.
	 */
	@Test
	void testGenerateAllFromEPackageWithMultipleEClasses() throws Exception {
		// Load the references.ecore model which has Company and Employee
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "references.ecore");
		assertNotNull(ePackage, "EPackage should be loaded");
		
		// Generate all instances from the package
		List<EObject> generatedObjects = generator.generateAllFrom(ePackage);
		assertNotNull(generatedObjects, "Generated objects should not be null");
		assertThat(generatedObjects).hasSize(2);
		
		// Save all generated models
		generator.save();
		
		// Verify both output files exist
		File companyFile = new File(TEST_OUTPUT_DIR, "company_Company_1.xmi");
		File employeeFile = new File(TEST_OUTPUT_DIR, "company_Employee_1.xmi");
		
		assertThat(companyFile).exists();
		assertThat(employeeFile).exists();
		
		// Load and validate both models
		EObject companyObject = loadGeneratedModel(companyFile, ePackage);
		EObject employeeObject = loadGeneratedModel(employeeFile, ePackage);
		assertNotNull(companyObject);
		assertNotNull(employeeObject);
		assertThat(companyObject.eClass().getName()).isEqualTo("Company");
		assertThat(employeeObject.eClass().getName()).isEqualTo("Employee");
		validateModel(companyObject);
		validateModel(employeeObject);
	}

	/**
	 * Test generateAllFrom with a package containing only one instantiable EClass.
	 */
	@Test
	void testGenerateAllFromEPackageWithSingleEClass() throws Exception {
		// Load the simple.ecore model which has only Person
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simple.ecore");
		assertNotNull(ePackage, "EPackage should be loaded");
		
		// Generate all instances from the package
		List<EObject> generatedObjects = generator.generateAllFrom(ePackage);
		assertNotNull(generatedObjects, "Generated objects should not be null");
		assertThat(generatedObjects).hasSize(1);
		
		// Save all generated models
		generator.save();
		
		// Verify output file exists
		File outputFile = new File(TEST_OUTPUT_DIR, "simple_Person_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and validate the model
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject);
		assertThat(loadedObject.eClass().getName()).isEqualTo("Person");
		validateModel(loadedObject);
	}

	/**
	 * Test generateAllFrom skips abstract classes and only generates instantiable ones.
	 */
	@Test
	void testGenerateAllFromEPackageSkipsAbstractClasses() throws Exception {
		// Load the abstract-first.ecore model where first EClass is abstract
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "abstract-first.ecore");
		assertNotNull(ePackage, "abstract-first EPackage should be loaded");
		
		// Verify the first classifier is an abstract EClass
		assertThat(ePackage.getEClassifiers().get(0)).isInstanceOf(EClass.class);
		var firstClass = (EClass) ePackage.getEClassifiers().get(0);
		assertThat(firstClass.isAbstract()).isTrue();
		assertThat(firstClass.getName()).isEqualTo("AbstractBase");
		
		// Generate all instances from the package
		// Should only generate ConcreteClass, not AbstractBase
		List<EObject> generatedObjects = generator.generateAllFrom(ePackage);
		assertNotNull(generatedObjects);
		assertThat(generatedObjects).hasSize(1);
		assertThat(generatedObjects.get(0).eClass().getName()).isEqualTo("ConcreteClass");
		
		// Save and verify
		generator.save();
		File outputFile = new File(TEST_OUTPUT_DIR, "abstractfirst_ConcreteClass_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and validate
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject);
		assertThat(loadedObject.eClass().getName()).isEqualTo("ConcreteClass");
		validateModel(loadedObject);
	}

	/**
	 * Test generateAllFrom with a package containing no instantiable EClasses.
	 * Should throw IllegalArgumentException.
	 */
	@Test
	void testGenerateAllFromEPackageWithNoInstantiableEClasses() {
		// Load the noeclass.ecore model which has only EDataTypes and EEnum
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "noeclass.ecore");
		assertNotNull(ePackage, "noeclass EPackage should be loaded");
		
		// Verify the package has classifiers but no EClass
		assertThat(ePackage.getEClassifiers()).isNotEmpty();
		
		// Attempt to generate all should throw IllegalArgumentException
		assertThatThrownBy(() -> generator.generateAllFrom(ePackage))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("No instantiable EClass found in EPackage")
			.hasMessageContaining("noeclass");
	}

	/**
	 * Test generateAllFrom with a package containing only abstract/interface classes.
	 * Should throw IllegalArgumentException.
	 */
	@Test
	void testGenerateAllFromEPackageWithOnlyAbstractClasses() {
		// Create an in-memory Ecore package with only abstract/interface classes
		EPackage ePackage = createEPackage("onlyabstract", "http://www.example.org/onlyabstract", "onlyabstract");
		
		createEClass(ePackage, "AbstractClass", true, false);
		
		createEClass(ePackage, "InterfaceClass", false, true);
		
		// Attempt to generate all should throw IllegalArgumentException
		assertThatThrownBy(() -> generator.generateAllFrom(ePackage))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("No instantiable EClass found in EPackage")
			.hasMessageContaining("onlyabstract");
	}

	/**
	 * Test generateAllFrom with a package containing many EClasses.
	 * Should generate an instance for each one in separate files.
	 */
	@Test
	void testGenerateAllFromEPackageWithManyEClasses() throws Exception {
		// Load the department.ecore model which has Organization, Department, Team, and Person
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "department.ecore");
		assertNotNull(ePackage, "department EPackage should be loaded");
		
		// Generate all instances from the package
		List<EObject> generatedObjects = generator.generateAllFrom(ePackage);
		assertNotNull(generatedObjects, "Generated objects should not be null");
		
		// Count instantiable EClasses in the package
		long instantiableCount = ePackage.getEClassifiers().stream()
			.filter(EClass.class::isInstance)
			.map(c -> (EClass) c)
			.filter(EMFUtils::canBeInstantiated)
			.count();
		
		assertThat(generatedObjects).hasSize((int) instantiableCount);
		
		// Save all generated models
		generator.save();
		
		// Verify that a file was created for each instantiable EClass
		for (EObject obj : generatedObjects) {
			String className = obj.eClass().getName();
			String fileName = String.format("dept_%s_1.xmi", className);
			File outputFile = new File(TEST_OUTPUT_DIR, fileName);
			assertThat(outputFile).as("File for " + className + " should exist").exists();
			
			// Load and validate
			EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
			assertNotNull(loadedObject);
			assertThat(loadedObject.eClass().getName()).isEqualTo(className);
			validateModel(loadedObject);
		}
	}

	/**
	 * Test generateAllFrom finds EClasses when they are not at the beginning of the package.
	 */
	@Test
	void testGenerateAllFromEPackageFindsEClassesNotFirst() throws Exception {
		// Load the alltypes.ecore model where EClass is after many EDataTypes
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "alltypes.ecore");
		assertNotNull(ePackage, "alltypes EPackage should be loaded");
		
		// Verify the first classifier is NOT an EClass
		assertThat(ePackage.getEClassifiers().get(0)).isNotInstanceOf(EClass.class);
		
		// Generate all instances from the package
		List<EObject> generatedObjects = generator.generateAllFrom(ePackage);
		assertNotNull(generatedObjects);
		assertThat(generatedObjects).hasSizeGreaterThan(0);
		
		// Should find the AllTypesHolder EClass
		assertThat(generatedObjects)
			.extracting(obj -> obj.eClass().getName())
			.contains("AllTypesHolder");
		
		// Save and verify
		generator.save();
		File outputFile = new File(TEST_OUTPUT_DIR, "alltypes_AllTypesHolder_1.xmi");
		assertThat(outputFile).exists();
	}

	/**
	 * Test generateAllFrom(EClass) with a concrete EClass that has no subclasses.
	 * Should generate only one instance of the EClass itself.
	 */
	@Test
	void testGenerateAllFromEClassWithNoSubclasses() throws Exception {
		// Load the simple.ecore model which has Person with no subclasses
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simple.ecore");
		assertNotNull(ePackage, "simple EPackage should be loaded");
		
		EClass personClass = assertEClassExists(ePackage, "Person");
		
		// Generate all instances from Person EClass
		List<EObject> generatedObjects = generator.generateAllFrom(personClass);
		assertNotNull(generatedObjects, "Generated objects should not be null");
		assertThat(generatedObjects).hasSize(1);
		assertThat(generatedObjects.get(0).eClass().getName()).isEqualTo("Person");
		
		// Save all generated models
		generator.save();
		
		// Verify output file exists
		File outputFile = new File(TEST_OUTPUT_DIR, "simple_Person_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and validate the model
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject);
		assertThat(loadedObject.eClass().getName()).isEqualTo("Person");
		validateModel(loadedObject);
	}

	/**
	 * Test generateAllFrom(EClass) with a concrete EClass that has multiple instantiable subclasses.
	 * Should generate instances for the base class and all subclasses.
	 */
	@Test
	void testGenerateAllFromEClassWithMultipleSubclasses() throws Exception {
		// Load the inheritance.ecore model which has Animal, Dog, Cat, Bird
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "inheritance.ecore");
		assertNotNull(ePackage, "inheritance EPackage should be loaded");
		
		EClass animalClass = assertEClassExists(ePackage, "Animal");
		
		// Generate all instances from Animal EClass (should include Animal, Dog, Cat, Bird)
		List<EObject> generatedObjects = generator.generateAllFrom(animalClass);
		assertNotNull(generatedObjects, "Generated objects should not be null");
		assertThat(generatedObjects).hasSize(4);
		
		// Verify that all expected classes are generated
		assertThat(generatedObjects)
			.extracting(obj -> obj.eClass().getName())
			.containsExactlyInAnyOrder("Animal", "Dog", "Cat", "Bird");
		
		// Save all generated models
		generator.save();
		
		// Verify all output files exist
		File animalFile = new File(TEST_OUTPUT_DIR, "inheritance_Animal_1.xmi");
		File dogFile = new File(TEST_OUTPUT_DIR, "inheritance_Dog_1.xmi");
		File catFile = new File(TEST_OUTPUT_DIR, "inheritance_Cat_1.xmi");
		File birdFile = new File(TEST_OUTPUT_DIR, "inheritance_Bird_1.xmi");
		
		assertThat(animalFile).exists();
		assertThat(dogFile).exists();
		assertThat(catFile).exists();
		assertThat(birdFile).exists();
		
		// Load and validate all models
		EObject animal = loadGeneratedModel(animalFile, ePackage);
		EObject dog = loadGeneratedModel(dogFile, ePackage);
		EObject cat = loadGeneratedModel(catFile, ePackage);
		EObject bird = loadGeneratedModel(birdFile, ePackage);
		
		assertNotNull(animal);
		assertNotNull(dog);
		assertNotNull(cat);
		assertNotNull(bird);
		
		assertThat(animal.eClass().getName()).isEqualTo("Animal");
		assertThat(dog.eClass().getName()).isEqualTo("Dog");
		assertThat(cat.eClass().getName()).isEqualTo("Cat");
		assertThat(bird.eClass().getName()).isEqualTo("Bird");
		
		validateModel(animal);
		validateModel(dog);
		validateModel(cat);
		validateModel(bird);
	}

	/**
	 * Test generateAllFrom(EClass) with an abstract EClass that has instantiable subclasses.
	 * Should generate instances only for the concrete subclasses, not the abstract base.
	 */
	@Test
	void testGenerateAllFromAbstractEClassWithConcreteSubclasses() throws Exception {
		// Load the abstract-hierarchy.ecore model which has abstract Shape with Circle, Rectangle, Triangle
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "abstract-hierarchy.ecore");
		assertNotNull(ePackage, "abstract-hierarchy EPackage should be loaded");
		
		EClass shapeClass = assertEClassExists(ePackage, "Shape");
		assertThat(shapeClass.isAbstract()).isTrue();
		
		// Generate all instances from Shape EClass (should only include Circle, Rectangle, Triangle)
		List<EObject> generatedObjects = generator.generateAllFrom(shapeClass);
		assertNotNull(generatedObjects, "Generated objects should not be null");
		assertThat(generatedObjects).hasSize(3);
		
		// Verify that only concrete subclasses are generated
		assertThat(generatedObjects)
			.extracting(obj -> obj.eClass().getName())
			.containsExactlyInAnyOrder("Circle", "Rectangle", "Triangle")
			.doesNotContain("Shape");
		
		// Save all generated models
		generator.save();
		
		// Verify all output files exist
		File circleFile = new File(TEST_OUTPUT_DIR, "abstracthier_Circle_1.xmi");
		File rectangleFile = new File(TEST_OUTPUT_DIR, "abstracthier_Rectangle_1.xmi");
		File triangleFile = new File(TEST_OUTPUT_DIR, "abstracthier_Triangle_1.xmi");
		
		assertThat(circleFile).exists();
		assertThat(rectangleFile).exists();
		assertThat(triangleFile).exists();
		
		// Load and validate all models
		EObject circle = loadGeneratedModel(circleFile, ePackage);
		EObject rectangle = loadGeneratedModel(rectangleFile, ePackage);
		EObject triangle = loadGeneratedModel(triangleFile, ePackage);
		
		assertNotNull(circle);
		assertNotNull(rectangle);
		assertNotNull(triangle);
		
		assertThat(circle.eClass().getName()).isEqualTo("Circle");
		assertThat(rectangle.eClass().getName()).isEqualTo("Rectangle");
		assertThat(triangle.eClass().getName()).isEqualTo("Triangle");
		
		validateModel(circle);
		validateModel(rectangle);
		validateModel(triangle);
	}

	/**
	 * Test generateAllFrom(EClass) with an abstract EClass that has no instantiable subclasses.
	 * Should throw IllegalArgumentException.
	 */
	@Test
	void testGenerateAllFromAbstractEClassWithNoConcreteSubclasses() {
		// Create an in-memory Ecore package with an abstract EClass that has no concrete subclasses
		EPackage ePackage = createEPackage("abstractonly", "http://www.example.org/abstractonly", "abstractonly");
		
		// Add an abstract EClass
		EClass abstractClass = createEClass(ePackage, "AbstractClass", true, false);
		
		// Create a resource and add the package to it
		ResourceSet rs = new ResourceSetImpl();
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl());
		Resource resource = rs.createResource(URI.createURI("temp:///test.ecore"));
		resource.getContents().add(ePackage);
		
		// Attempt to generate all should throw IllegalArgumentException
		assertThatThrownBy(() -> generator.generateAllFrom(abstractClass))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("No instantiable EClass found for EClass")
			.hasMessageContaining("AbstractClass");
	}

	/**
	 * Test generateAllFrom(EClass) with an interface EClass.
	 * Should throw IllegalArgumentException since interfaces cannot be instantiated.
	 */
	@Test
	void testGenerateAllFromInterfaceEClass() {
		// Create an in-memory Ecore package with an interface EClass
		EPackage ePackage = createEPackage("interfaceonly", "http://www.example.org/interfaceonly", "interfaceonly");
		
		// Add an interface EClass
		EClass interfaceClass = createEClass(ePackage, "InterfaceClass", false, true);
		
		// Create a resource and add the package to it
		ResourceSet rs = new ResourceSetImpl();
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl());
		Resource resource = rs.createResource(URI.createURI("temp:///test.ecore"));
		resource.getContents().add(ePackage);
		
		// Attempt to generate all should throw IllegalArgumentException
		assertThatThrownBy(() -> generator.generateAllFrom(interfaceClass))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("No instantiable EClass found for EClass")
			.hasMessageContaining("InterfaceClass");
	}

	/**
	 * Test generateFrom with a 3-level hierarchy across separate Ecore resources.
	 * The hierarchy is as follows:
	 * 
	 * <ul>
	 * <li>hierarchy-base.ecore: Container with items reference to abstract BaseItem</li>
	 * <li>hierarchy-middle.ecore: MiddleItem extends BaseItem</li>
	 * <li>hierarchy-leaf.ecore: LeafItem extends MiddleItem</li>
	 * </ul>
	 * 
	 * Each Ecore is loaded in a separate standalone resource (not in a resource set).
	 * The generator should find subclasses across all packages via the global registry.
	 */
	@Test
	void testGenerateFromWithCrossPackageHierarchyUsingNsURI() throws Exception {
		// Load all three Ecore files in separate standalone resources
		EPackage basePackage = loadEcoreModel(TEST_INPUTS_DIR, "hierarchy-base.ecore");
		EPackage middlePackage = loadEcoreModel(TEST_INPUTS_DIR, "hierarchy-middle.ecore");
		EPackage leafPackage = loadEcoreModel(TEST_INPUTS_DIR, "hierarchy-leaf.ecore");
		
		assertNotNull(basePackage, "Base package should be loaded");
		assertNotNull(middlePackage, "Middle package should be loaded");
		assertNotNull(leafPackage, "Leaf package should be loaded");
		
		// Get the Container class from the base package
		EClass containerClass = assertEClassExists(basePackage, "Container");
		
		// Verify the hierarchy is set up correctly
		assertEClassExists(basePackage, "BaseItem");
		assertEClassExists(middlePackage, "MiddleItem");
		assertEClassExists(leafPackage, "LeafItem");

		// Generate Container model (should populate items with MiddleItem and LeafItem instances)
		EObject generatedObject = generator.generateFrom(containerClass);
		assertNotNull(generatedObject);
		validateModel(generatedObject);
		
		generator.save();
		
		// Verify the generated XMI matches expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"hierarchybase_Container_1.xmi", "hierarchybase_Container_1.xmi");
	}

	/**
	 * Test generateFrom with a 3-level hierarchy across separate Ecore resources using file URIs.
	 * The hierarchy is as follows:
	 * 
	 * <ul>
	 * <li>hierarchy-base-fileuri.ecore: Container with items reference to abstract BaseItem</li>
	 * <li>hierarchy-middle-fileuri.ecore: MiddleItem extends BaseItem</li>
	 * ><li>hierarchy-leaf-fileuri.ecore: LeafItem extends MiddleItem</li>
	 * </ul>
	 * 
	 * Each Ecore is loaded in a separate standalone resource using file URIs.
	 * The generator should find subclasses across all packages via the global registry.
	 * However, it will use "conceptual identifiers" to detect the subtype property.
	 * 
	 * @throws Exception
	 */
	@Test
	void testGenerateFromWithCrossPackageHierarchyUsingFileURI() throws Exception {
		// Load all three Ecore files in separate standalone resources using file URIs
		EPackage basePackage = loadEcoreModel(TEST_INPUTS_DIR, "hierarchy-base-fileuri.ecore");
		EPackage middlePackage = loadEcoreModel(TEST_INPUTS_DIR, "hierarchy-middle-fileuri.ecore");
		EPackage leafPackage = loadEcoreModel(TEST_INPUTS_DIR, "hierarchy-leaf-fileuri.ecore");

		assertNotNull(basePackage, "Base package should be loaded");
		assertNotNull(middlePackage, "Middle package should be loaded");
		assertNotNull(leafPackage, "Leaf package should be loaded");
		
		// Get the Container class from the base package
		EClass containerClass = assertEClassExists(basePackage, "Container");
		
		// Verify the hierarchy is set up correctly
		assertEClassExists(basePackage, "BaseItem");
		assertEClassExists(middlePackage, "MiddleItem");
		assertEClassExists(leafPackage, "LeafItem");
		
		// Generate Container model (should populate items with MiddleItem and LeafItem instances)
		generator.setFilePrefix("filebaseduri_");
		EObject generatedObject = generator.generateFrom(containerClass);
		assertNotNull(generatedObject);
		validateModel(generatedObject);
		
		// Save with schemaLocation option
		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));
		
		// Verify the generated XMI matches expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"filebaseduri_hierarchybase_Container_1.xmi", "filebaseduri_hierarchybase_Container_1.xmi");
	}

	/**
	 * Test that generateFrom(EClass) throws an exception for abstract EClass.
	 */
	@Test
	void testGenerateFromAbstractEClassThrowsException() {
		// Load the abstract-first.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "abstract-first.ecore");
		
		// Get the abstract EClass
		EClass abstractClass = assertEClassExists(ePackage, "AbstractBase");
		assertThat(abstractClass.isAbstract()).isTrue();
		
		// Attempt to generate from abstract class should throw IllegalArgumentException
		assertThatThrownBy(() -> generator.generateFrom(abstractClass))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Cannot instantiate EClass")
			.hasMessageContaining("AbstractBase")
			.hasMessageContaining("abstract");
	}

	/**
	 * Test that generateFrom(EClass) throws an exception for interface EClass.
	 */
	@Test
	void testGenerateFromInterfaceEClassThrowsException() {
		// Create an in-memory interface EClass
		EPackage ePackage = createEPackage("testpkg", "http://www.example.org/testpkg", "testpkg");
		
		EClass interfaceClass = createEClass(ePackage, "MyInterface", false, true);
		
		// Attempt to generate from interface should throw IllegalArgumentException
		assertThatThrownBy(() -> generator.generateFrom(interfaceClass))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Cannot instantiate EClass")
			.hasMessageContaining("MyInterface")
			.hasMessageContaining("interface");
	}

	/**
	 * Test that generateFromSeveral throws an exception when one EClass is abstract.
	 */
	@Test
	void testGenerateFromSeveralWithOneAbstractEClassThrowsException() {
		// Load the abstract-first.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "abstract-first.ecore");
		
		EClass abstractClass = assertEClassExists(ePackage, "AbstractBase");
		EClass concreteClass = assertEClassExists(ePackage, "ConcreteClass");
		
		assertNotNull(abstractClass);
		assertNotNull(concreteClass);
		
		// Attempt to generate from both classes should throw IllegalArgumentException
		assertThatThrownBy(() -> generator.generateFromSeveral(abstractClass, concreteClass))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Cannot instantiate the following EClasses")
			.hasMessageContaining("AbstractBase")
			.hasMessageContaining("abstract");
	}

	/**
	 * Test that generateFromSeveral throws an exception when multiple EClasses are invalid.
	 */
	@Test
	void testGenerateFromSeveralWithMultipleInvalidEClassesThrowsException() {
		// Create in-memory EClasses (one abstract, one interface)
		EPackage ePackage = org.eclipse.emf.ecore.EcoreFactory.eINSTANCE.createEPackage();
		ePackage.setName("testpkg");
		ePackage.setNsURI("http://www.example.org/testpkg");
		ePackage.setNsPrefix("testpkg");
		
		EClass abstractClass = org.eclipse.emf.ecore.EcoreFactory.eINSTANCE.createEClass();
		abstractClass.setName("AbstractClass");
		abstractClass.setAbstract(true);
		ePackage.getEClassifiers().add(abstractClass);
		
		EClass interfaceClass = org.eclipse.emf.ecore.EcoreFactory.eINSTANCE.createEClass();
		interfaceClass.setName("InterfaceClass");
		interfaceClass.setInterface(true);
		ePackage.getEClassifiers().add(interfaceClass);
		
		// Attempt to generate from both classes should throw IllegalArgumentException with both names
		assertThatThrownBy(() -> generator.generateFromSeveral(abstractClass, interfaceClass))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Cannot instantiate the following EClasses")
			.hasMessageContaining("AbstractClass")
			.hasMessageContaining("abstract")
			.hasMessageContaining("InterfaceClass")
			.hasMessageContaining("interface");
	}

	@Test
	void testSaveSkipsEcoreFiles() throws Exception {
		// Load the simple.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simple.ecore");
		assertNotNull(ePackage, "EPackage should be loaded");

		// Create a custom resource set and add BOTH an ecore file and xmi file to the output directory
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put("*", new XMIResourceFactoryImpl());
		
		// Register Ecore resource factory to handle .ecore files
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
			.put("ecore", new EcoreResourceFactoryImpl());
		
		// Manually add an ecore resource pointing to the output directory
		// This simulates a scenario where an Ecore file is in the same resource set as generated models
		Resource ecoreResource = resourceSet.createResource(URI.createFileURI(
				new File(TEST_OUTPUT_DIR + "/simple_model.ecore").getAbsolutePath()));
		ecoreResource.getContents().add(ePackage);

		// Create generator with the shared resource set
		EMFModelGenerator customGenerator = new EMFModelGenerator(resourceSet);
		customGenerator.setOutputDirectory(TEST_OUTPUT_DIR);

		// Get the Person EClass
		EClass personClass = assertEClassExists(ePackage, "Person");

		// Generate model
		EObject generatedObject = customGenerator.generateFrom(personClass);
		assertNotNull(generatedObject, "Generated object should not be null");

		// Before saving, verify the output directory exists
		Path outputPath = Paths.get(TEST_OUTPUT_DIR);
		Files.createDirectories(outputPath);

		// Save - should skip the ecore file but save the generated model
		customGenerator.save();

		// Verify XMI file was created for the generated model
		File xmiFile = new File(TEST_OUTPUT_DIR, "simple_Person_1.xmi");
		assertThat(xmiFile).exists();

		// Verify the ecore file was NOT saved to the output directory
		// even though it's in the resource set
		File ecoreFile = new File(TEST_OUTPUT_DIR, "simple_model.ecore");
		assertThat(ecoreFile).doesNotExist();

		// This proves that ecore files are being skipped and not saved
	}

	@Test
	void testSaveWithOptions() throws Exception {
		// Load the simple.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simple.ecore");
		assertNotNull(ePackage, "EPackage should be loaded");

		// Create a custom resource set with absolute file URIs
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put("*", new XMIResourceFactoryImpl());
		resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);

		// Register Ecore resource factory to handle .ecore files
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
			.put("ecore", new org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl());

		// Manually add the ecore resource to the resource set
		Resource ecoreResource = resourceSet.createResource(URI.createFileURI(
				new File(TEST_INPUTS_DIR + "/simple.ecore").getAbsolutePath()));
		ecoreResource.getContents().add(ePackage);

		// Create generator with the shared resource set
		EMFModelGenerator customGenerator = new EMFModelGenerator(resourceSet);
		customGenerator.setOutputDirectory(TEST_OUTPUT_DIR);

		// Get the Person EClass
		EClass personClass = assertEClassExists(ePackage, "Person");

		// Generate model
		EObject generatedObject = customGenerator.generateFrom(personClass);
		assertNotNull(generatedObject, "Generated object should not be null");

		// Save with schemaLocation option
		final Map<Object, Object> options = new HashMap<>();
		options.put(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE);
		options.put(XMLResource.OPTION_LINE_WIDTH, 10);
		customGenerator.save(options);

		// Verify XMI file was created with schemaLocation
		File xmiFile = new File(TEST_OUTPUT_DIR, "simple_Person_1.xmi");
		assertThat(xmiFile).exists();

		// Read the generated XMI and verify schemaLocation is present
		String xmiContent = Files.readString(xmiFile.toPath());
		assertThat(xmiContent)
			.contains("xsi:schemaLocation")
			.contains("simple.ecore");
	}

	@Test
	void testSetMaxDepth() throws Exception {
		// Load the deep-hierarchy.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "deep-hierarchy.ecore");
		
		EClass rootClass = assertEClassExists(ePackage, "Root");
		
		// Set maxDepth to 2 (less than default 5, which goes up to Level5)
		generator.getInstancePopulator().setMaxDepth(2);
		
		// Generate Root model
		EObject generatedObject = generator.generateFrom(rootClass);
		assertNotNull(generatedObject);
		
		// Save the generated model
		generator.save();
		
		// Verify output file exists
		File outputFile = new File(TEST_OUTPUT_DIR, "deephier_Root_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and verify
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject);
		
		// With maxDepth=2, should have level1 and level2, but not level3
		// Root has "level1" containment reference
		@SuppressWarnings("unchecked")
		var level1List = (List<EObject>) loadedObject.eGet(rootClass.getEStructuralFeature("level1"));
		assertThat(level1List).as("Should have level1 children at depth 0").isNotEmpty();
		
		// Level1 should have level2 children (depth 1)
		EObject level1Obj = level1List.get(0);
		EClass level1Class = assertEClassExists(ePackage, "Level1");
		@SuppressWarnings("unchecked")
		var level2List = (List<EObject>) level1Obj.eGet(level1Class.getEStructuralFeature("level2"));
		assertThat(level2List).as("Should have level2 children at depth 1").isNotEmpty();
		
		// Level2 should NOT have level3 children (depth 2 exceeds maxDepth=2)
		EObject level2Obj = level2List.get(0);
		EClass level2Class = assertEClassExists(ePackage, "Level2");
		@SuppressWarnings("unchecked")
		var level3List = (List<EObject>) level2Obj.eGet(level2Class.getEStructuralFeature("level3"));
		assertThat(level3List).as("Should NOT have level3 children at depth 2 with maxDepth=2").isEmpty();
	}

	@Test
	void testSetAttributeMultiValuedCount() throws Exception {
		// Load the datatypes.ecore model which has multi-valued "tags" attribute
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "datatypes.ecore");
		
		EClass dataHolderClass = assertEClassExists(ePackage, "DataHolder");
		
		// Set attributeMultiValuedCount to 5 (more than default 2)
		generator.getInstancePopulator().setAttributeDefaultMaxCount(5);
		
		// Generate model
		EObject generatedObject = generator.generateFrom(dataHolderClass);
		assertNotNull(generatedObject);
		
		// Save the generated model
		generator.save();
		
		// Verify output file exists
		File outputFile = new File(TEST_OUTPUT_DIR, "datatypes_DataHolder_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and verify
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject);
		
		// Verify multi-valued attribute has 5 values instead of default 2
		@SuppressWarnings("unchecked")
		var tags = (java.util.List<String>) loadedObject.eGet(
			dataHolderClass.getEStructuralFeature("tags"));
		assertNotNull(tags);
		assertThat(tags).hasSize(5);
	}

	/**
	 * Same as above but setting for a specific attribute.
	 * 
	 * @throws Exception
	 */
	@Test
	void testSetAttributeMaxCountFor() throws Exception {
		// Load the datatypes.ecore model which has multi-valued "tags" attribute
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "datatypes.ecore");
		
		EClass dataHolderClass = assertEClassExists(ePackage, "DataHolder");
		EAttribute tagsFeature = assertEAttributeExists(dataHolderClass, "tags");
		assertNotNull(tagsFeature, "tags feature should exist");
		
		// Set attributeMultiValuedCount to 5 (more than default 2)
		generator.getInstancePopulator().setAttributeMaxCountFor(tagsFeature, 5);
		
		// Generate model
		EObject generatedObject = generator.generateFrom(dataHolderClass);
		assertNotNull(generatedObject);
		
		// Save the generated model
		generator.save();
		
		// Verify output file exists
		File outputFile = new File(TEST_OUTPUT_DIR, "datatypes_DataHolder_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and verify
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject);
		
		// Verify multi-valued attribute has 5 values instead of default 2
		@SuppressWarnings("unchecked")
		var tags = (java.util.List<String>) loadedObject.eGet(
			dataHolderClass.getEStructuralFeature("tags"));
		assertNotNull(tags);
		assertThat(tags).hasSize(5);
	}

	@Test
	void testSetNonContainmentReferenceMultiValuedCount() throws Exception {
		// Load the container-search.ecore model which has multi-valued "targets" reference
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "container-search.ecore");
		
		EClass registryClass = assertEClassExists(ePackage, "Registry");
		EClass linkClass = assertEClassExists(ePackage, "Link");
		
		// Set nonContainmentReferenceMultiValuedCount to 1 (less than default 2)
		generator.getInstancePopulator().setCrossReferenceDefaultMaxCount(1);
		
		// Generate both Registry and Link
		List<EObject> models = generator.generateFromSeveral(registryClass, linkClass);
		assertThat(models).hasSize(2);
		
		// Save the generated models
		generator.save();
		
		// Verify both output files exist
		File linkFile = new File(TEST_OUTPUT_DIR, "container_Link_1.xmi");
		assertThat(linkFile).exists();
		
		// Load and verify
		EObject link = loadGeneratedModel(linkFile, ePackage);
		assertNotNull(link);
		
		// Verify multi-valued cross reference has 1 value instead of default 2
		@SuppressWarnings("unchecked")
		var targets = (List<EObject>) link.eGet(linkClass.getEStructuralFeature("targets"));
		assertThat(targets).as("Link should have 1 target with nonContainmentReferenceMultiValuedCount=1").hasSize(1);
	}

	/**
	 * Same as above but setting for a specific reference.
	 * 
	 * @throws Exception
	 */
	@Test
	void testSetNonContainmentReferenceMaxCountFor() throws Exception {
		// Load the container-search.ecore model which has multi-valued "targets" reference
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "container-search.ecore");
		
		EClass registryClass = assertEClassExists(ePackage, "Registry");
		EClass linkClass = assertEClassExists(ePackage, "Link");
		
		EReference targetsFeature = assertEReferenceExists(linkClass, "targets");
		assertNotNull(targetsFeature, "targets feature should exist");
		
		// Set nonContainmentReferenceMultiValuedCount to 1 (less than default 2)
		generator.getInstancePopulator().setCrossReferenceMaxCountFor(targetsFeature, 1);
		
		// Generate both Registry and Link
		List<EObject> models = generator.generateFromSeveral(registryClass, linkClass);
		assertThat(models).hasSize(2);
		
		// Save the generated models
		generator.save();
		
		// Verify both output files exist
		File linkFile = new File(TEST_OUTPUT_DIR, "container_Link_1.xmi");
		assertThat(linkFile).exists();
		
		// Load and verify
		EObject link = loadGeneratedModel(linkFile, ePackage);
		assertNotNull(link);
		
		// Verify multi-valued cross reference has 1 value instead of default 2
		@SuppressWarnings("unchecked")
		var targets = (List<EObject>) link.eGet(linkClass.getEStructuralFeature("targets"));
		assertThat(targets).as("Link should have 1 target with nonContainmentReferenceMultiValuedCount=1").hasSize(1);
	}

	@Test
	void testSetContainmentReferenceMultiValuedCount() throws Exception {
		// Load the references.ecore model which has multi-valued "employees" containment
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "references.ecore");
		
		EClass companyClass = assertEClassExists(ePackage, "Company");
		
		// Set containmentReferenceMultiValuedCount to 4 (more than default 2)
		generator.getInstancePopulator().setContainmentReferenceDefaultMaxCount(4);
		
		// Generate Company model
		EObject generatedObject = generator.generateFrom(companyClass);
		assertNotNull(generatedObject);
		
		// Save the generated model
		generator.save();
		
		// Verify output file exists
		File outputFile = new File(TEST_OUTPUT_DIR, "company_Company_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and verify
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject);
		
		// Verify multi-valued containment reference has 4 values instead of default 2
		var employees = EMFUtils.getAsEObjectsList(loadedObject, assertEReferenceExists(companyClass, "employees"));
		assertNotNull(employees);
		assertThat(employees).hasSize(4);
	}

	@Test
	void testEXTLibraryWithSchemaLocation() throws Exception {
		// Load the extlibrary.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore");
		assertNotNull(ePackage, "extlibrary EPackage should be loaded");

		EClass libraryClass = assertEClassExists(ePackage, "Library");
		EReference branchesReference = assertEReferenceExists(libraryClass, "branches");
		assertNotNull(branchesReference, "branches reference should exist");

		// Generate Library model
		generator.getInstancePopulator().setContainmentReferenceMaxCountFor(branchesReference, 1);
		generator.getInstancePopulator().setContainmentReferenceDefaultMaxCount(5);
		generator.getInstancePopulator().setFeatureMapDefaultMaxCount(4);
		generator.getInstancePopulator().setMaxDepth(2);
		EObject generatedObject = generator.generateFrom(libraryClass);
		assertNotNull(generatedObject);
		validateModel(generatedObject);

		// Save with schemaLocation option
		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));

		// Verify XMI file was created with schemaLocation
		File xmiFile = new File(TEST_OUTPUT_DIR, "extlibrary_Library_1.xmi");
		assertThat(xmiFile).exists();

		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"extlibrary_Library_1.xmi", "extlibrary_Library_1.xmi");		// Verify the deep structure was created
	}

	@Test
	void testSimpleLibraryWithSchemaLocation() throws Exception {
		// Load the simplelibrary.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simplelibrary.ecore");
		assertNotNull(ePackage, "library EPackage should be loaded");

		EClass libraryClass = assertEClassExists(ePackage, "Library");

		// Generate Library model
		EObject generatedObject = generator.generateFrom(libraryClass);
		assertNotNull(generatedObject);
		validateModel(generatedObject);

		// Save with schemaLocation option
		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));

		// Verify XMI file was created with schemaLocation
		File xmiFile = new File(TEST_OUTPUT_DIR, "simplelibrary_Library_1.xmi");
		assertThat(xmiFile).exists();

		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"simplelibrary_Library_1.xmi", "simplelibrary_Library_1.xmi");		// Verify the deep structure was created
	}

	@Test
	void testSimpleLibraryWithSchemaLocationSeveralResources() throws Exception {
		// Load the simplelibrary.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simplelibrary.ecore");
		assertNotNull(ePackage, "library EPackage should be loaded");

		generator.setFilePrefix("schemaloc_");
		List<EObject> allFrom = generator.generateAllFrom(ePackage);
		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));
		// Validate all generated models
		for (EObject obj : allFrom) {
			validateModel(obj);
		}
		// Verify XMI files were created with schemaLocation
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"schemaloc_simplelibrary_Library_1.xmi", "schemaloc_simplelibrary_Library_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"schemaloc_simplelibrary_Book_1.xmi", "schemaloc_simplelibrary_Book_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"schemaloc_simplelibrary_Author_1.xmi", "schemaloc_simplelibrary_Author_1.xmi");
	}

	/**
	 * Test generating simplelibrary with schemaLocation, but customizing the containment
	 * reference setter so that Books and Authors (created in their own resources),
	 * are added to the Library's containment references.
	 * 
	 * It sets a custom EMFContainmentReferenceSetter for the customized behavior.
	 * 
	 * @throws Exception
	 */
	@Test
	void testSimpleLibraryCustomContainmentsAcrossResourcesWithCustomSetter() throws Exception {
		// Load the simplelibrary.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simplelibrary.ecore");
		assertNotNull(ePackage, "library EPackage should be loaded");

		// Customize containment references to force multiple resources
		EClass bookClass = assertEClassExists(ePackage, "Book");
		EClass authorClass = assertEClassExists(ePackage, "Author");
		EClass libraryClass = assertEClassExists(ePackage, "Library");

		generator.setFilePrefix("schemaloc_customcont_");
		// Custom containment reference setter to add existing Books and Authors in their own resources
		// to the Library's containment references
		generator.getInstancePopulator().setContainmentReferenceSetter(new EMFContainmentReferenceSetter() {
			@Override
			public Collection<EObject> setContainmentReference(EObject owner, EReference reference) {
				if (owner.eClass() == libraryClass && reference.getName().equals("books")) {
					var books = EMFUtils.getAsEObjectsList(owner, reference);
					// find existing books without container in the resource set
					EMFUtils.findAllAssignableInstances(owner, bookClass).stream()
							.filter(b -> b.eContainer() == null)
							.forEach(books::add);
					return Collections.emptyList();
				} else if (owner.eClass() == libraryClass && reference.getName().equals("authors")) {
					var authors = EMFUtils.getAsEObjectsList(owner, reference);
					// find existing authors without container in the resource set
					EMFUtils.findAllAssignableInstances(owner, authorClass).stream()
							.filter(a -> a.eContainer() == null)
							.forEach(authors::add);
					return Collections.emptyList();
				}
				return super.setContainmentReference(owner, reference);
			}
		});

		// generate the Library as the last one, so that we already have a Book and Author in their own resources
		var allFrom = generator.generateFromSeveral(bookClass, authorClass, libraryClass);

		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));
		// Validate all generated models
		for (EObject obj : allFrom) {
			validateModel(obj);
		}

		var book = allFrom.stream().filter(e -> e.eClass() == bookClass).findFirst().orElse(null);
		var author = allFrom.stream().filter(e -> e.eClass() == authorClass).findFirst().orElse(null);
		var library = allFrom.stream().filter(e -> e.eClass() == libraryClass).findFirst().orElse(null);
		assertNotNull(book, "Book should be generated");
		assertNotNull(author, "Author should be generated");
		assertNotNull(library, "Library should be generated");
		assertThat(book.eContainer()).isEqualTo(library);
		assertThat(author.eContainer()).isEqualTo(library);
		// also check opposite references
		assertThat(book.eGet(bookClass.getEStructuralFeature("library"))).isSameAs(library);
		assertThat(author.eGet(authorClass.getEStructuralFeature("library"))).isSameAs(library);

		// Verify XMI files were created with schemaLocation
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"schemaloc_customcont_simplelibrary_Library_1.xmi", "schemaloc_customcont_simplelibrary_Library_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"schemaloc_customcont_simplelibrary_Book_1.xmi", "schemaloc_customcont_simplelibrary_Book_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"schemaloc_customcont_simplelibrary_Author_1.xmi", "schemaloc_customcont_simplelibrary_Author_1.xmi");
	}

	/**
	 * Test generating simplelibrary with schemaLocation, but customizing the containment
	 * reference setter so that Books and Authors (created in their own resources),
	 * are added to the Library's containment references.
	 * 
	 * It sets a custom function for the customized behavior.
	 * 
	 * @throws Exception
	 */
	@Test
	void testSimpleLibraryCustomContainmentsAcrossResourcesWithCustomFunction() throws Exception {
		// Load the simplelibrary.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simplelibrary.ecore");
		assertNotNull(ePackage, "library EPackage should be loaded");

		// Customize containment references to force multiple resources
		EClass bookClass = assertEClassExists(ePackage, "Book");
		EClass authorClass = assertEClassExists(ePackage, "Author");
		EClass libraryClass = assertEClassExists(ePackage, "Library");
		EReference libraryBooksRef = assertEReferenceExists(libraryClass, "books");
		EReference libraryAuthorsRef = assertEReferenceExists(libraryClass, "authors");

		generator.setFilePrefix("schemaloc_customcont_");
		generator.getInstancePopulator().setContainmentReferenceDefaultMaxCount(1);

		// Function to provide existing Books without container in the resource set
		var existingBooks = new ArrayList<EObject>();
		generator.getInstancePopulator().functionForContainmentReference(libraryBooksRef, owner -> {
			if (existingBooks.isEmpty()) {
				// find existing books without container in the resource set
				EMFUtils.findAllAssignableInstances(owner, bookClass).stream()
						.filter(b -> b.eContainer() == null)
						.forEach(existingBooks::add);
			}
			return existingBooks.removeFirst();
		});
		// Function to provide existing Authors without container in the resource set
		var existingAuthors = new ArrayList<EObject>();
		generator.getInstancePopulator().functionForContainmentReference(libraryAuthorsRef, owner -> {
			if (existingAuthors.isEmpty()) {
				// find existing authors without container in the resource set
				EMFUtils.findAllAssignableInstances(owner, authorClass).stream()
						.filter(a -> a.eContainer() == null)
						.forEach(existingAuthors::add);
			}
			return existingAuthors.removeFirst();
		});

		// generate the Library as the last one, so that we already have a Book and Author in their own resources
		var allFrom = generator.generateFromSeveral(bookClass, authorClass, libraryClass);

		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));
		// Validate all generated models
		for (EObject obj : allFrom) {
			validateModel(obj);
		}

		var book = allFrom.stream().filter(e -> e.eClass() == bookClass).findFirst().orElse(null);
		var author = allFrom.stream().filter(e -> e.eClass() == authorClass).findFirst().orElse(null);
		var library = allFrom.stream().filter(e -> e.eClass() == libraryClass).findFirst().orElse(null);
		assertNotNull(book, "Book should be generated");
		assertNotNull(author, "Author should be generated");
		assertNotNull(library, "Library should be generated");
		assertThat(book.eContainer()).isEqualTo(library);
		assertThat(author.eContainer()).isEqualTo(library);
		// also check opposite references
		assertThat(book.eGet(bookClass.getEStructuralFeature("library"))).isSameAs(library);
		assertThat(author.eGet(authorClass.getEStructuralFeature("library"))).isSameAs(library);

		// Verify XMI files were created with schemaLocation
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"schemaloc_customcont_simplelibrary_Library_1.xmi", "schemaloc_customcont_simplelibrary_Library_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"schemaloc_customcont_simplelibrary_Book_1.xmi", "schemaloc_customcont_simplelibrary_Book_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"schemaloc_customcont_simplelibrary_Author_1.xmi", "schemaloc_customcont_simplelibrary_Author_1.xmi");
	}

	@Test
	void testGraphimprovedWithSchemaLocation() throws Exception {
		// Load the graphimproved.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "graphimproved.ecore");
		assertNotNull(ePackage, "graphimproved EPackage should be loaded");

		EClass graphClass = assertEClassExists(ePackage, "Graph");
		assertEClassExists(ePackage, "Node");
		assertEClassExists(ePackage, "Edge");

		// due to the single back reference from Edge to Node, we need to
		// increase the number of edges to avoid
		// generating empty collections for nodes outgoing and incoming
		// The settings allow for a nice distribution of edges among nodes.
		generator.getInstancePopulator().setContainmentReferenceMaxCountFor(
				(EReference) graphClass.getEStructuralFeature("edges"), 9);
		generator.getInstancePopulator().setContainmentReferenceDefaultMaxCount(3);
		generator.getInstancePopulator().setCrossReferenceDefaultMaxCount(3);

		// Generate Graph model
		EObject generatedObject = generator.generateFrom(graphClass);
		assertNotNull(generatedObject);
		validateModel(generatedObject);

		// Save with schemaLocation option
		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));

		// Verify XMI file was created with schemaLocation
		File xmiFile = new File(TEST_OUTPUT_DIR, "graphimproved_Graph_1.xmi");
		assertThat(xmiFile).exists();

		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"graphimproved_Graph_1.xmi", "graphimproved_Graph_1.xmi");	
	}

	/**
	 * In this version, we set 2 as the max for cross references, but the number
	 * is not necessarily respected if it does not break a possible upper bound.
	 * 
	 * The first two outgoing/incoming references are set during Node cross reference
	 * resolution, while the third one while resolving Edge cross references.
	 * Remember that Edges are more than Nodes, thus while resolving Node references,
	 * Some Edges will not be referenced yet.
	 * 
	 * For example, for Node 1, outgoing is first set to Edge 1 and Edge 2 while resolving
	 * Node references. Then, while resolving Edge references, also Edge 7 is added.
	 * 
	 * @throws Exception
	 */
	@Test
	void testGraphimprovedWithSchemaLocation2() throws Exception {
		// Load the graphimproved.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "graphimproved.ecore");
		assertNotNull(ePackage, "graphimproved EPackage should be loaded");

		EClass graphClass = assertEClassExists(ePackage, "Graph");
		assertEClassExists(ePackage, "Node");
		assertEClassExists(ePackage, "Edge");

		// due to the single back reference from Edge to Node, we need to
		// increase the number of edges to avoid
		// generating empty collections for nodes outgoing and incoming
		// The settings allow for a nice distribution of edges among nodes.
		generator.getInstancePopulator().setContainmentReferenceMaxCountFor(
				(EReference) graphClass.getEStructuralFeature("edges"), 9);
		generator.getInstancePopulator().setContainmentReferenceDefaultMaxCount(3);
		generator.getInstancePopulator().setCrossReferenceDefaultMaxCount(2);

		// Generate Graph model
		generator.setFilePrefix("graph2_");
		EObject generatedObject = generator.generateFrom(graphClass);
		assertNotNull(generatedObject);
		validateModel(generatedObject);

		// Save with schemaLocation option
		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));

		// Verify XMI file was created with schemaLocation
		File xmiFile = new File(TEST_OUTPUT_DIR, "graph2_graphimproved_Graph_1.xmi");
		assertThat(xmiFile).exists();

		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"graph2_graphimproved_Graph_1.xmi", "graph2_graphimproved_Graph_1.xmi");	
	}

	@Test
	void testTaskManagerWithEnumsAndSchemaLocation() throws Exception {
		// Load the taskmanager.ecore model with enums
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "taskmanager.ecore");
		assertNotNull(ePackage, "taskmanager EPackage should be loaded");

		EClass projectClass = assertEClassExists(ePackage, "Project");

		// Generate Project model with tasks
		EObject generatedProject = generator.generateFrom(projectClass);
		assertNotNull(generatedProject);
		validateModel(generatedProject);

		// Save with schemaLocation option
		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));

		// Verify XMI file was created with schemaLocation
		File xmiFile = new File(TEST_OUTPUT_DIR, "taskmanager_Project_1.xmi");
		assertThat(xmiFile).exists();

		// Read the generated XMI and verify enum values are present
		String xmiContent = Files.readString(xmiFile.toPath(), StandardCharsets.UTF_8);
		assertThat(xmiContent)
			.contains("xsi:schemaLocation")
			.contains("status=")
			.contains("priority=");

		// Verify that generated project has the expected structure
		assertThat(generatedProject.eGet(generatedProject.eClass().getEStructuralFeature("name"))).isNotNull();
		assertThat(generatedProject.eGet(generatedProject.eClass().getEStructuralFeature("status"))).isNotNull();
		
		// Verify tasks were generated with enum values
		@SuppressWarnings("unchecked")
		List<EObject> tasks = (List<EObject>) generatedProject.eGet(generatedProject.eClass().getEStructuralFeature("tasks"));
		assertThat(tasks).isNotEmpty();
		
		EObject firstTask = tasks.get(0);
		assertThat(firstTask.eGet(firstTask.eClass().getEStructuralFeature("status"))).isNotNull();
		assertThat(firstTask.eGet(firstTask.eClass().getEStructuralFeature("priority"))).isNotNull();
		
		// Verify multi-valued enum attribute
		@SuppressWarnings("unchecked")
		List<Object> tags = (List<Object>) firstTask.eGet(firstTask.eClass().getEStructuralFeature("tags"));
		assertThat(tags).isNotEmpty();

		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"taskmanager_Project_1.xmi", "taskmanager_Project_1.xmi");
	}

	@Test
	void testMutualDependenciesWithSchemaLocation() throws Exception {
		// Load both ecore models with mutual dependencies
		// it is crucial to load both models into the same ResourceSet
		EPackage companyPackage = loadEcoreModel(TEST_INPUTS_DIR, "company-dept.ecore");
		EPackage employeePackage = loadEcoreModel(TEST_INPUTS_DIR, "employee-dept.ecore",
				companyPackage.eResource().getResourceSet());
		
		assertNotNull(companyPackage, "company EPackage should be loaded");
		assertNotNull(employeePackage, "employee EPackage should be loaded");

		EClass companyClass = assertEClassExists(companyPackage, "Company");
		EClass departmentClass = assertEClassExists(employeePackage, "Department");
		

		// Generate models from both classes
		generator.setFilePrefix("mutual_");
		generator.generateFromSeveral(companyClass, departmentClass);

		// Save with schemaLocation option
		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));

		// Verify both XMI files were created
		File companyFile = new File(TEST_OUTPUT_DIR, "mutual_company_Company_1.xmi");
		File deptFile = new File(TEST_OUTPUT_DIR, "mutual_employees_Department_1.xmi");
		
		assertThat(companyFile).exists();
		assertThat(deptFile).exists();

		// Compare with expected outputs
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"mutual_company_Company_1.xmi", "mutual_company_Company_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"mutual_employees_Department_1.xmi", "mutual_employees_Department_1.xmi");
	}

	@Test
	void testMutualDependenciesWithSchemaLocationMultipleInstances() throws Exception {
		// Load both ecore models with mutual dependencies
		// it is crucial to load both models into the same ResourceSet
		EPackage companyPackage = loadEcoreModel(TEST_INPUTS_DIR, "company-dept.ecore");
		EPackage employeePackage = loadEcoreModel(TEST_INPUTS_DIR, "employee-dept.ecore",
				companyPackage.eResource().getResourceSet());
		
		assertNotNull(companyPackage, "company EPackage should be loaded");
		assertNotNull(employeePackage, "employee EPackage should be loaded");

		EClass companyClass = assertEClassExists(companyPackage, "Company");
		EClass departmentClass = assertEClassExists(employeePackage, "Department");
		

		// Generate multiple models from both classes
		generator.setFilePrefix("mutualmulti_");
		generator.setNumberOfInstances(2);
		generator.generateFromSeveral(companyClass, departmentClass);

		// Save with schemaLocation option
		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));

		// Verify all XMI files were created
		File companyFile1 = new File(TEST_OUTPUT_DIR, "mutualmulti_company_Company_1.xmi");
		File companyFile2 = new File(TEST_OUTPUT_DIR, "mutualmulti_company_Company_2.xmi");
		File deptFile1 = new File(TEST_OUTPUT_DIR, "mutualmulti_employees_Department_1.xmi");
		File deptFile2 = new File(TEST_OUTPUT_DIR, "mutualmulti_employees_Department_2.xmi");
		
		assertThat(companyFile1).exists();
		assertThat(companyFile2).exists();
		assertThat(deptFile1).exists();
		assertThat(deptFile2).exists();

		// Compare with expected outputs
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"mutualmulti_company_Company_1.xmi", "mutualmulti_company_Company_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"mutualmulti_company_Company_2.xmi", "mutualmulti_company_Company_2.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"mutualmulti_employees_Department_1.xmi", "mutualmulti_employees_Department_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"mutualmulti_employees_Department_2.xmi", "mutualmulti_employees_Department_2.xmi");
	}

	@Test
	void shouldAllowCustomSettersAndGenerateValidXMI() throws IOException {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simplelibrary.ecore");
		final var libraryEClass = assertEClassExists(ePackage, "Library");

		// Custom attribute setter: set all "name" attributes to "CUSTOM_<ClassName>"
		generator.getInstancePopulator().setAttributeSetter(new EMFAttributeSetter() {
			@Override
			public void setAttribute(EObject eObject, EAttribute attribute) {
				if ("name".equals(attribute.getName())) {
					eObject.eSet(attribute, "CUSTOM_" + eObject.eClass().getName());
					return;
				}
				super.setAttribute(eObject, attribute);
			}
		});

		// Custom containment reference setter: create 3 books instead of default 2
		generator.getInstancePopulator().setContainmentReferenceSetter(new EMFContainmentReferenceSetter() {
			@Override
			public Collection<EObject> setContainmentReference(EObject owner, EReference reference) {
				if ("books".equals(reference.getName())) {
					// Create 3 books instead of 2
					final var createdEObjects = new ArrayList<EObject>();
					for (int i = 0; i < 3; i++) {
						final var book = EcoreUtil.create(reference.getEReferenceType());
						EMFUtils.getAsEObjectsList(owner, reference).add(book);
						createdEObjects.add(book);
					}
					return createdEObjects;
				}
				return super.setContainmentReference(owner, reference);
			}
		});

		// Custom cross reference setter: all books reference only the first author
		generator.getInstancePopulator().setCrossReferenceSetter(new EMFCrossReferenceSetter() {
			@Override
			public void setCrossReference(EObject owner, EReference reference) {
				if ("authors".equals(reference.getName()) && "Book".equals(owner.eClass().getName())) {
					// Books reference only the first author in the library
					final var library = owner.eContainer();
					if (library != null) {
						final var authors = EMFUtils.getAsEObjectsList(library, 
								library.eClass().getEStructuralFeature("authors"));
						if (!authors.isEmpty()) {
							final var bookAuthors = EMFUtils.getAsEObjectsList(owner, reference);
							if (bookAuthors.isEmpty()) {
								bookAuthors.add(authors.get(0));
							}
							return;
						}
					}
				}
				// Skip the opposite reference to avoid double-setting due to bidirectionality
				if ("books".equals(reference.getName()) && "Author".equals(owner.eClass().getName())) {
					return;
				}
				super.setCrossReference(owner, reference);
			}
		});

		generator.generateFrom(libraryEClass);
		generator.save();

		final var outputFile = new File(TEST_OUTPUT_DIR, "simplelibrary_Library_1.xmi");
		assertThat(outputFile).exists();

		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"simplelibrary_Library_1.xmi", "simplelibrary_custom_Library_1.xmi");
	}

	@Test
	void shouldAllowSelectiveSelfReferencesInGraph() throws IOException {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "graph.ecore");
		final var graphEClass = assertEClassExists(ePackage, "Graph");

		// Configure to generate 3 nodes
		generator.getInstancePopulator().setContainmentReferenceDefaultMaxCount(3);

		// Allow cycles (self-references) for nodes at specific positions:
		// Node 0: yes, Node 1: no, Node 2: yes
		generator.getInstancePopulator().setAllowCyclePolicy((owner, reference) -> {
			if ("outgoing".equals(reference.getName()) && "Node".equals(owner.eClass().getName())) {
				// Get the graph container
				final var graph = owner.eContainer();
				if (graph != null) {
					final var nodes = EMFUtils.getAsEObjectsList(graph,
							graph.eClass().getEStructuralFeature("nodes"));
					final var index = nodes.indexOf(owner);
					// Allow cycles for nodes at even indices (0, 2, 4, ...)
					return index % 2 == 0;
				}
			}
			return false;
		});

		generator.generateFrom(graphEClass);
		generator.save();

		final var outputFile = new File(TEST_OUTPUT_DIR, "graph_Graph_1.xmi");
		assertThat(outputFile).exists();

		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"graph_Graph_1.xmi", "graph_selective_cycles_Graph_1.xmi");
	}

	@Test
	void testFunctionForAttribute() throws Exception {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simple.ecore");
		final var personClass = assertEClassExists(ePackage, "Person");

		final var nameAttribute = (EAttribute) personClass.getEStructuralFeature("name");
		final var ageAttribute = (EAttribute) personClass.getEStructuralFeature("age");

		// Set custom function for name attribute
		generator.getInstancePopulator().functionForAttribute(nameAttribute,
				owner -> "FUNCTION_" + owner.eClass().getName());

		// Set custom function for age attribute
		generator.getInstancePopulator().functionForAttribute(ageAttribute,
				owner -> 42);

		generator.setFilePrefix("function_attr_");
		generator.generateFrom(personClass);
		generator.save();

		final var outputFile = new File(TEST_OUTPUT_DIR, "function_attr_simple_Person_1.xmi");
		assertThat(outputFile).exists();

		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"function_attr_simple_Person_1.xmi", "function_attr_simple_Person_1.xmi");
	}

	@Test
	void testFunctionForContainmentReference() throws Exception {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "library.ecore");
		final var libraryClass = assertEClassExists(ePackage, "Library");

		final var shelvesReference = (EReference) libraryClass.getEStructuralFeature("shelves");

		// Set custom function for shelves containment reference:
		// if already set, always return the same shelf instance
		generator.getInstancePopulator().functionForContainmentReference(shelvesReference, owner -> {
			var shelves = EMFUtils.getAsEObjectsList(owner, shelvesReference);
			if (!shelves.isEmpty()) {
				return shelves.get(0);
			}
			return EcoreUtil.create(shelvesReference.getEReferenceType());
		});

		// Configure to create 3 containment references
		generator.getInstancePopulator().setContainmentReferenceDefaultMaxCount(3);

		generator.setFilePrefix("function_cont_");
		var library = generator.generateFrom(libraryClass);
		generator.save();

		// just one shelve should be created (due to the custom function)
		var shelves = EMFUtils.getAsEObjectsList(library, shelvesReference);
		assertThat(shelves).hasSize(1);
		// but 3 books should be contained in that shelf (due to the set max count)
		var shelfClass = assertEClassExists(ePackage, "Shelf");
		var booksReference = (EReference) shelfClass.getEStructuralFeature("books");
		var books = EMFUtils.getAsEObjectsList(shelves.get(0), booksReference);
		assertThat(books).hasSize(3);

		final var outputFile = new File(TEST_OUTPUT_DIR, "function_cont_library_Library_1.xmi");
		assertThat(outputFile).exists();

		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"function_cont_library_Library_1.xmi", "function_cont_library_Library_1.xmi");
	}

	/**
	 * Set a custom function for a cross reference to alter the default
	 * round-robin behavior.
	 * 
	 * In this case, we set a function for Book.authors to return
	 * the authors in reverse order.
	 * 
	 * @throws Exception
	 */
	@Test
	void testSimpleLibraryCustomFunctionForBookAuthorsCrossReference() throws Exception {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simplelibrary.ecore");
		final var libraryClass = assertEClassExists(ePackage, "Library");

		// Set custom function for Book.authors - return them in reverse order
		// which is different from the default round-robin behavior
		final var bookClass = assertEClassExists(ePackage, "Book");
		final var authorsReference = (EReference) bookClass.getEStructuralFeature("authors");
		final var counter = new AtomicInteger(0);
		generator.getInstancePopulator().functionForCrossReference(authorsReference, owner -> {
			// Get the library containing this book
			final var library = owner.eContainer();
			final var authors = EMFUtils.getAsEObjectsList(library,
					library.eClass().getEStructuralFeature("authors"));
			if (authors.isEmpty()) {
				return null;
			}
			// Return authors in reverse order based on a counter
			final int index = authors.size() - 1 - (counter.getAndIncrement() % authors.size());
			return authors.get(index);
		});

		generator.setFilePrefix("function_cross_");
		generator.generateFrom(libraryClass);
		generator.save();

		final var outputFile = new File(TEST_OUTPUT_DIR, "function_cross_simplelibrary_Library_1.xmi");
		assertThat(outputFile).exists();

		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"function_cross_simplelibrary_Library_1.xmi", "function_cross_simplelibrary_Library_1.xmi");
	}

	/**
	 * Set a custom strategy for a cross reference to alter the default
	 * round-robin behavior.
	 * 
	 * In this case, we set a strategy for Book.authors to return
	 * the authors in reverse order.
	 * 
	 * @throws Exception
	 */
	@Test
	void testSimpleLibraryCustomStrategyForBookAuthorsCrossReference() throws Exception {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simplelibrary.ecore");
		final var libraryClass = assertEClassExists(ePackage, "Library");

		// Set custom function for Book.authors - return them in reverse order
		// which is different from the default round-robin behavior
		final var bookClass = assertEClassExists(ePackage, "Book");
		final var authorsClass = assertEClassExists(ePackage, "Author");
		final var counter = new AtomicInteger(0);
		generator.getInstancePopulator().setCrossReferenceCandidateSelectorStrategy(new EMFRoundRobinEObjectCandidateSelector() {
			@Override
			public EObject getNextCandidate(EObject context, EClass type) {
				if (context.eClass() == bookClass && authorsClass == type) {
					final var authors = getOrCompute(context, type);
					if (authors.isEmpty()) {
						return null;
					}
					// Return authors in reverse order based on a counter
					final int index = authors.size() - 1 - (counter.getAndIncrement() % authors.size());
					return authors.get(index);
				}
				// use the default round-robin for other cases
				return super.getNextCandidate(context, type);
			}
		});

		generator.setFilePrefix("function_cross_");
		generator.generateFrom(libraryClass);
		generator.save();

		final var outputFile = new File(TEST_OUTPUT_DIR, "function_cross_simplelibrary_Library_1.xmi");
		assertThat(outputFile).exists();

		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
				"function_cross_simplelibrary_Library_1.xmi", "function_cross_simplelibrary_Library_1.xmi");
	}

	@Test
	void shouldPreventSelfReferencesWhenAllowCyclePolicyReturnsFalse() throws IOException {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "graph.ecore");
		final var graphEClass = assertEClassExists(ePackage, "Graph");

		// Configure to generate 3 nodes
		generator.getInstancePopulator().setContainmentReferenceDefaultMaxCount(3);

		// Always return false for allowCyclePolicy - no self-references should be created
		generator.getInstancePopulator().setAllowCyclePolicy((owner, reference) -> false);

		generator.generateFrom(graphEClass);
		generator.save();

		final var outputFile = new File(TEST_OUTPUT_DIR, "graph_Graph_1.xmi");
		assertThat(outputFile).exists();

		// Load the generated model
		final var generatedGraph = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(generatedGraph);

		// Get all nodes in the graph
		final var nodes = EMFUtils.getAsEObjectsList(generatedGraph, graphEClass.getEStructuralFeature("nodes"));
		assertThat(nodes).isNotEmpty();

		// Verify that NO node has itself in its outgoing references
		final var nodeClass = assertEClassExists(ePackage, "Node");
		final var outgoingRef = nodeClass.getEStructuralFeature("outgoing");
		for (final var node : nodes) {
			final var outgoingNodes = EMFUtils.getAsEObjectsList(node, outgoingRef);
			assertThat(outgoingNodes)
				.isNotEmpty()
				.doesNotContain(node);
		}
	}

	/**
	 * Test that the generator properly registers a programmatically-created package
	 * that has NOT been pre-registered in any registry.
	 * This tests the registerPackage functionality.
	 */
	@Test
	void testGenerateWithProgrammaticPackageNotPreRegistered() {
		// Create a unique package that won't collide with any pre-registered packages
		String uniqueNsURI = "http://test.programmatic/" + System.nanoTime();
		
		EPackage testPackage = org.eclipse.emf.ecore.EcoreFactory.eINSTANCE.createEPackage();
		testPackage.setName("programmatic");
		testPackage.setNsURI(uniqueNsURI);
		testPackage.setNsPrefix("programmatic");
		
		// Create a simple EClass with one attribute
		EClass personClass = org.eclipse.emf.ecore.EcoreFactory.eINSTANCE.createEClass();
		personClass.setName("Person");
		testPackage.getEClassifiers().add(personClass);
		
		EAttribute nameAttr = org.eclipse.emf.ecore.EcoreFactory.eINSTANCE.createEAttribute();
		nameAttr.setName("name");
		nameAttr.setEType(org.eclipse.emf.ecore.EcorePackage.Literals.ESTRING);
		personClass.getEStructuralFeatures().add(nameAttr);
		
		// Verify the package is NOT registered in global registry
		assertThat(EPackage.Registry.INSTANCE.containsKey(uniqueNsURI))
			.as("Package should NOT be pre-registered in global registry")
			.isFalse();
		
		// Create a new generator with fresh ResourceSet to ensure no pre-registration
		EMFModelGenerator freshGenerator = new EMFModelGenerator();
		freshGenerator.setOutputDirectory(TEST_OUTPUT_DIR);
		
		// Verify the package is NOT registered in the generator's ResourceSet
		assertThat(freshGenerator.getResourceSet().getPackageRegistry().containsKey(uniqueNsURI))
			.as("Package should NOT be pre-registered in generator's ResourceSet")
			.isFalse();
		
		// Generate - this should register the package in both registries
		EObject generated = freshGenerator.generateFrom(personClass);
		assertNotNull(generated);
		
		// Verify the package IS NOW registered with the correct value (not null)
		// This catches the mutation that replaces "k -> pkg" with "k -> null"
		Object localRegistryValue = freshGenerator.getResourceSet().getPackageRegistry().get(uniqueNsURI);
		assertThat(localRegistryValue)
			.as("Package value in generator's ResourceSet registry should not be null")
			.isNotNull()
			.as("Package value in generator's ResourceSet registry should be the package")
			.isSameAs(testPackage);
		
		Object globalRegistryValue = EPackage.Registry.INSTANCE.get(uniqueNsURI);
		assertThat(globalRegistryValue)
			.as("Package value in global registry should not be null")
			.isNotNull()
			.as("Package value in global registry should be the package")
			.isSameAs(testPackage);
		
		// Clean up: remove from global registry to avoid affecting other tests
		EPackage.Registry.INSTANCE.remove(uniqueNsURI);
	}

	@Test
	void testSetInstantiableSubclassSelectorStrategy() {
		generator.setOutputDirectory(TEST_OUTPUT_DIR);
		EPackage testPackage = createEPackage("test", "http://test", "test");
		
		EClass abstractBase = createEClass(testPackage, "AbstractBase", true, false);
		
		EClass subClass1 = createEClass(testPackage, "SubClass1");
		subClass1.getESuperTypes().add(abstractBase);
		
		EClass subClass2 = createEClass(testPackage, "SubClass2");
		subClass2.getESuperTypes().add(abstractBase);
		
		EClass container = createEClass(testPackage, "Container");
		
		EReference children = createContainmentEReference(container, "children", abstractBase, -1);
		
		var customStrategy = new EMFCandidateSelectorStrategy<EClass, EClass>() {
			@Override
			public EClass getNextCandidate(EObject context, EClass type) {
				return subClass2;
			}

			@Override
			public boolean hasCandidates(EObject context, EClass type) {
				return true;
			}
		};
		generator.getInstancePopulator().setInstantiableSubclassSelectorStrategy(customStrategy);
		var result = generator.generateFrom(container);
		var childrenList = EMFUtils.getAsEObjectsList(result, children);
		assertThat(childrenList).hasSize(2)
			.allMatch(child -> child.eClass().equals(subClass2));
	}

	@Test
	void testSetCrossReferenceCandidateSelectorStrategy() {
		generator.setOutputDirectory(TEST_OUTPUT_DIR);
		var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "container-search.ecore");
		var registryClass = assertEClassExists(ePackage, "Registry");
		var linkClass = assertEClassExists(ePackage, "Link");
		var entryClass = assertEClassExists(ePackage, "Entry");
		var entry1 = EcoreUtil.create(entryClass);
		entry1.eSet(entryClass.getEStructuralFeature("key"), "entry1");
		var entry2 = EcoreUtil.create(entryClass);
		entry2.eSet(entryClass.getEStructuralFeature("key"), "entry2");
		var customStrategy = new EMFCandidateSelectorStrategy<EClass, EObject>() {
			@Override
			public EObject getNextCandidate(EObject context, EClass type) {
				return entry2;
			}

			@Override
			public boolean hasCandidates(EObject context, EClass type) {
				return true;
			}
		};
		generator.getInstancePopulator().setCrossReferenceCandidateSelectorStrategy(customStrategy);
		var registry = generator.generateFrom(registryClass);
		registry.eResource().getContents().add(entry1);
		registry.eResource().getContents().add(entry2);
		var link = generator.generateFrom(linkClass);
		var targets = EMFUtils.getAsEObjectsList(link, linkClass.getEStructuralFeature("targets"));
		// Only one reference can be set since the strategy always returns the same object
		// and multi-valued references don't allow duplicates
		assertThat(targets).hasSize(1)
			.allMatch(target -> target.equals(entry2));
	}

	@Test
	void testSetEnumLiteralSelectorStrategy() {
		generator.setOutputDirectory(TEST_OUTPUT_DIR);
		var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "taskmanager.ecore");
		var taskClass = assertEClassExists(ePackage, "Task");
		var priorityEnum = (EEnum) ePackage.getEClassifier("Priority");
		var statusEnum = (EEnum) ePackage.getEClassifier("Status");
		var highLiteral = priorityEnum.getEEnumLiteral("HIGH");
		var completedLiteral = statusEnum.getEEnumLiteral("COMPLETED");
		var customStrategy = new EMFCandidateSelectorStrategy<EEnum, EEnumLiteral>() {
			@Override
			public EEnumLiteral getNextCandidate(EObject context, EEnum type) {
				if (type == priorityEnum) {
					return highLiteral;
				} else if (type == statusEnum) {
					return completedLiteral;
				}
				return null;
			}

			@Override
			public boolean hasCandidates(EObject context, EEnum type) {
				return type == priorityEnum || type == statusEnum;
			}
		};
		generator.getInstancePopulator().setEnumLiteralSelectorStrategy(customStrategy);
		var result = generator.generateFrom(taskClass);
		var priorityValue = result.eGet(taskClass.getEStructuralFeature("priority"));
		assertThat(priorityValue).isEqualTo(highLiteral.getInstance());
		var statusValue = result.eGet(taskClass.getEStructuralFeature("status"));
		assertThat(statusValue).isEqualTo(completedLiteral.getInstance());
		var tags = EMFUtils.getAsList(result, taskClass.getEStructuralFeature("tags"));
		// Only one value can be set since the strategy always returns the same object
		// and multi-valued enum attributes don't allow duplicates by default
		assertThat(tags).hasSize(1)
			.allMatch(tag -> tag.equals(highLiteral.getInstance()));
	}

	@Test
	void testSetGlobalFileExtension() throws Exception {
		generator.setOutputDirectory(TEST_OUTPUT_DIR);
		generator.setGlobalFileExtension("json");
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simple.ecore");
		EClass personClass = assertEClassExists(ePackage, "Person");
		
		generator.generateFrom(personClass);
		generator.save();
		
		File outputFile = new File(TEST_OUTPUT_DIR, "simple_Person_1.json");
		assertThat(outputFile).exists();
	}

	@Test
	void testSetFileExtensionForEClass() throws Exception {
		generator.setOutputDirectory(TEST_OUTPUT_DIR);
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simple.ecore");
		EClass personClass = assertEClassExists(ePackage, "Person");
		
		generator.setFileExtensionForEClass(personClass, "xml");
		generator.generateFrom(personClass);
		generator.save();
		
		File outputFile = new File(TEST_OUTPUT_DIR, "simple_Person_1.xml");
		assertThat(outputFile).exists();
	}

	@Test
	void testSetFileExtensionForEPackage() throws Exception {
		generator.setOutputDirectory(TEST_OUTPUT_DIR);
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simple.ecore");
		EClass personClass = assertEClassExists(ePackage, "Person");
		
		generator.setFileExtensionForEPackage(ePackage, "custom");
		generator.generateFrom(personClass);
		generator.save();
		
		File outputFile = new File(TEST_OUTPUT_DIR, "simple_Person_1.custom");
		assertThat(outputFile).exists();
	}

	@Test
	void testFileExtensionHierarchy() throws Exception {
		generator.setOutputDirectory(TEST_OUTPUT_DIR);
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "references.ecore");
		EClass companyClass = assertEClassExists(ePackage, "Company");
		EClass employeeClass = assertEClassExists(ePackage, "Employee");
		
		// Set global and package extensions
		generator.setGlobalFileExtension("global");
		generator.setFileExtensionForEPackage(ePackage, "package");
		// Override for specific EClass
		generator.setFileExtensionForEClass(companyClass, "company");
		
		generator.generateFromSeveral(companyClass, employeeClass);
		generator.save();
		
		// Company should use EClass-specific extension
		File companyFile = new File(TEST_OUTPUT_DIR, "company_Company_1.company");
		assertThat(companyFile).exists();
		
		// Employee should use EPackage extension
		File employeeFile = new File(TEST_OUTPUT_DIR, "company_Employee_1.package");
		assertThat(employeeFile).exists();
	}

	@Test
	void testGenerateFromClassInNestedSubpackage() throws Exception {
		// Load the nested-packages.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "nested-packages.ecore");
		assertNotNull(ePackage, "EPackage should be loaded");

		EClass containerClass = (EClass) ePackage.getEClassifier("Container");
		assertNotNull(containerClass, "Container should exist");

		// Generate model from ContainerClass
		generator.getInstancePopulator().setContainmentReferenceDefaultMaxCount(5);
		EObject generatedObject = generator.generateFrom(containerClass);
		assertNotNull(generatedObject, "Generated object should not be null");
		assertThat(generatedObject.eClass().getName()).isEqualTo("Container");
		
		// Save all generated models
		generator.save();
		
		// Verify output file exists
		File outputFile = new File(TEST_OUTPUT_DIR, "nested_Container_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and validate the generated model
		// using the resource set of the generator to ensure proper package resolution
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage,
				generator.getResourceSet());
		assertNotNull(loadedObject, "Loaded object should not be null");
		validateModel(loadedObject);
		
		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, 
			"nested_Container_1.xmi", "nested_Container_1.xmi");
	}

	@Test
	void testGenerateFromClassInNestedSubpackageWithSchemaLocation() throws Exception {
		// Load the nested-packages.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "nested-packages.ecore");
		assertNotNull(ePackage, "EPackage should be loaded");

		EClass containerClass = (EClass) ePackage.getEClassifier("Container");
		assertNotNull(containerClass, "Container should exist");

		// Generate model from ContainerClass
		generator.getInstancePopulator().setContainmentReferenceDefaultMaxCount(6);
		generator.setFilePrefix("schemaloc_");
		EObject generatedObject = generator.generateFrom(containerClass);
		assertNotNull(generatedObject, "Generated object should not be null");
		assertThat(generatedObject.eClass().getName()).isEqualTo("Container");
		
		// Save all generated models
		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));
		
		// Verify output file exists
		File outputFile = new File(TEST_OUTPUT_DIR, "schemaloc_nested_Container_1.xmi");
		assertThat(outputFile).exists();
		
		// Load and validate the generated model
		// using the resource set of the generator to ensure proper package resolution
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage,
				generator.getResourceSet());
		assertNotNull(loadedObject, "Loaded object should not be null");
		validateModel(loadedObject);
		
		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, 
			"schemaloc_nested_Container_1.xmi", "schemaloc_nested_Container_1.xmi");
	}

	@Test
	void testGenerateWithNonUniqueReferences() throws Exception {
		// Load the nonunique-references.ecore model
		EPackage ePackage = loadEcoreModel(TEST_INPUTS_DIR, "nonunique-references.ecore");
		assertNotNull(ePackage, "EPackage should be loaded");

		// Get the Playlist EClass
		EClass playlistClass = assertEClassExists(ePackage, "Playlist");

		// Generate model with duplicates in the queue
		generator.getInstancePopulator().setCrossReferenceDefaultMaxCount(5);
		EObject generatedObject = generator.generateFrom(playlistClass);
		assertNotNull(generatedObject, "Generated object should not be null");
		assertThat(generatedObject.eClass().getName()).isEqualTo("Playlist");

		// Save all generated models
		generator.save();

		// Verify output file exists
		File outputFile = new File(TEST_OUTPUT_DIR, "playlist_Playlist_1.xmi");
		assertThat(outputFile).exists();

		// Load and validate the generated model
		EObject loadedObject = loadGeneratedModel(outputFile, ePackage);
		assertNotNull(loadedObject, "Generated object should not be null");
		validateModel(loadedObject);

		// Compare with expected output
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR,
			"playlist_Playlist_1.xmi", "playlist_Playlist_1.xmi");

		// Verify the queue reference allows duplicates
		EReference queueRef = (EReference) playlistClass.getEStructuralFeature("queue");
		assertThat(queueRef.isUnique()).isFalse();

		// Verify the queue contains duplicates
		var queue = EMFUtils.getAsList(loadedObject, queueRef);
		assertThat(queue).hasSize(5);
		// With only 2 songs in the containment, we should see duplicates in the queue
		assertThat(queue.stream().distinct().count()).isLessThan(queue.size());
	}

	@Test
	void testSetFeatureMapSetter() {
		generator.setOutputDirectory(TEST_OUTPUT_DIR);
		var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore");
		var libraryClass = assertEClassExists(ePackage, "Library");
		var writersRef = (EReference) libraryClass.getEStructuralFeature("writers");
		var employeesRef = (EReference) libraryClass.getEStructuralFeature("employees");
		
		var counter = new java.util.concurrent.atomic.AtomicInteger(0);
		
		var customSetter = new EMFFeatureMapSetter() {
			@Override
			public java.util.Collection<EObject> setFeatureMap(EObject owner, EAttribute featureMapAttribute) {
				counter.incrementAndGet();
				return super.setFeatureMap(owner, featureMapAttribute);
			}
		};
		
		generator.getInstancePopulator().setFeatureMapSetter(customSetter);
		generator.getInstancePopulator().setMaxDepth(1);
		
		var result = generator.generateFrom(libraryClass);
		
		assertThat(counter.get()).isPositive();
		
		var writers = EMFUtils.getAsEObjectsList(result, writersRef);
		var employees = EMFUtils.getAsEObjectsList(result, employeesRef);
		assertThat(writers).isNotEmpty();
		assertThat(employees).isNotEmpty();
	}

	@Test
	void testSetGroupMemberSelectorStrategy() {
		generator.setOutputDirectory(TEST_OUTPUT_DIR);
		var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore");
		var libraryClass = assertEClassExists(ePackage, "Library");
		var writersRef = (EReference) libraryClass.getEStructuralFeature("writers");
		var employeesRef = (EReference) libraryClass.getEStructuralFeature("employees");
		var borrowersRef = (EReference) libraryClass.getEStructuralFeature("borrowers");
		
		var customStrategy = new EMFCandidateSelectorStrategy<EAttribute, EReference>() {
			@Override
			public EReference getNextCandidate(EObject context, EAttribute type) {
				return writersRef;
			}

			@Override
			public boolean hasCandidates(EObject context, EAttribute type) {
				return true;
			}
		};
		
		generator.getInstancePopulator().setGroupMemberSelectorStrategy(customStrategy);
		
		var result = generator.generateFrom(libraryClass);
		
		var writers = EMFUtils.getAsEObjectsList(result, writersRef);
		var employees = EMFUtils.getAsEObjectsList(result, employeesRef);
		var borrowers = EMFUtils.getAsEObjectsList(result, borrowersRef);
		
		assertThat(writers).isNotEmpty();
		assertThat(employees).isEmpty();
		assertThat(borrowers).isEmpty();
	}

	@Test
	void testSetFeatureMapDefaultMaxCount() {
		generator.setOutputDirectory(TEST_OUTPUT_DIR);
		var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore");
		var libraryClass = assertEClassExists(ePackage, "Library");
		var writersRef = (EReference) libraryClass.getEStructuralFeature("writers");
		var employeesRef = (EReference) libraryClass.getEStructuralFeature("employees");
		var borrowersRef = (EReference) libraryClass.getEStructuralFeature("borrowers");
		
		generator.getInstancePopulator().setFeatureMapDefaultMaxCount(5);
		
		var result = generator.generateFrom(libraryClass);
		
		var writers = EMFUtils.getAsEObjectsList(result, writersRef);
		var employees = EMFUtils.getAsEObjectsList(result, employeesRef);
		var borrowers = EMFUtils.getAsEObjectsList(result, borrowersRef);
		var totalPeople = writers.size() + employees.size() + borrowers.size();
		
		assertThat(totalPeople).isEqualTo(5);
	}

	@Test
	void testSetFeatureMapMaxCountFor() {
		generator.setOutputDirectory(TEST_OUTPUT_DIR);
		var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore");
		var libraryClass = assertEClassExists(ePackage, "Library");
		var peopleAttr = (EAttribute) libraryClass.getEStructuralFeature("people");
		var writersRef = (EReference) libraryClass.getEStructuralFeature("writers");
		var employeesRef = (EReference) libraryClass.getEStructuralFeature("employees");
		var borrowersRef = (EReference) libraryClass.getEStructuralFeature("borrowers");
		
		generator.getInstancePopulator().setFeatureMapMaxCountFor(peopleAttr, 7);
		
		var result = generator.generateFrom(libraryClass);
		
		var writers = EMFUtils.getAsEObjectsList(result, writersRef);
		var employees = EMFUtils.getAsEObjectsList(result, employeesRef);
		var borrowers = EMFUtils.getAsEObjectsList(result, borrowersRef);
		var totalPeople = writers.size() + employees.size() + borrowers.size();
		
		assertThat(totalPeople).isEqualTo(7);
	}

	@Test
	void testFunctionForFeatureMapGroupMember() {
		generator.setOutputDirectory(TEST_OUTPUT_DIR);
		var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore");
		var libraryClass = assertEClassExists(ePackage, "Library");
		var writerClass = (EClass) ePackage.getEClassifier("Writer");
		var writersRef = (EReference) libraryClass.getEStructuralFeature("writers");
		var employeesRef = (EReference) libraryClass.getEStructuralFeature("employees");
		
		var counter = new java.util.concurrent.atomic.AtomicInteger(0);
		
		generator.getInstancePopulator().functionForFeatureMapGroupMember(writersRef, owner -> {
			counter.incrementAndGet();
			var writer = EcoreUtil.create(writerClass);
			writer.eSet(writerClass.getEStructuralFeature("firstName"), "CustomWriter");
			return writer;
		});
		
		var result = generator.generateFrom(libraryClass);
		
		var writers = EMFUtils.getAsEObjectsList(result, writersRef);
		var employees = EMFUtils.getAsEObjectsList(result, employeesRef);
		
		assertThat(counter.get()).isPositive();
		
		assertThat(writers).isNotEmpty()
			.allMatch(writer -> "CustomWriter".equals(writer.eGet(writerClass.getEStructuralFeature("firstName"))));
		
		assertThat(employees).isNotEmpty()
			.noneMatch(employee -> "CustomWriter".equals(employee.eGet(employee.eClass().getEStructuralFeature("firstName"))));
	}

	/**
	 * Test the loadEcoreModel and unloadEcoreModels methods.
	 * This test loads two independent Ecore files, generates models from both,
	 * verifies the output, and then cleans up.
	 */
	@Test
	void testLoadEcoreModelAndUnload() throws Exception {
		generator.setOutputDirectory(TEST_OUTPUT_DIR);
		
		// Load first Ecore model (simple.ecore)
		EPackage simplePackage = generator.loadEcoreModel(TEST_INPUTS_DIR + "/simple.ecore");
		
		// Verify the package is registered in the global registry
		assertThat(EPackage.Registry.INSTANCE).containsKey(simplePackage.getNsURI()); // NOSONAR: false positive
		
		// Load second Ecore model (datatypes.ecore)
		EPackage datatypesPackage = generator.loadEcoreModel(TEST_INPUTS_DIR + "/datatypes.ecore");
		assertNotNull(datatypesPackage, "Datatypes package should be loaded");
		assertThat(datatypesPackage.getName()).isEqualTo("datatypes");
		
		// Verify both packages are registered
		assertThat(EPackage.Registry.INSTANCE).containsKey(simplePackage.getNsURI()).containsKey(datatypesPackage.getNsURI());
		
		// Generate model from first package
		EClass personClass = assertEClassExists(simplePackage, "Person");
		EObject person = generator.generateFrom(personClass);
		assertNotNull(person);
		assertThat(person.eClass().getName()).isEqualTo("Person");
		
		// Generate model from second package
		EClass dataHolderClass = assertEClassExists(datatypesPackage, "DataHolder");
		EObject dataHolder = generator.generateFrom(dataHolderClass);
		assertNotNull(dataHolder);
		assertThat(dataHolder.eClass().getName()).isEqualTo("DataHolder");
		
		// Save all generated models
		generator.save();
		
		// Verify output files exist
		File personFile = new File(TEST_OUTPUT_DIR, "simple_Person_1.xmi");
		File dataHolderFile = new File(TEST_OUTPUT_DIR, "datatypes_DataHolder_1.xmi");
		assertThat(personFile).exists();
		assertThat(dataHolderFile).exists();
		
		// Load and validate the generated models
		EObject loadedPerson = loadGeneratedModel(personFile, simplePackage);
		EObject loadedDataHolder = loadGeneratedModel(dataHolderFile, datatypesPackage);
		assertNotNull(loadedPerson);
		assertNotNull(loadedDataHolder);
		validateModel(loadedPerson);
		validateModel(loadedDataHolder);
		
		// Compare with expected outputs
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "simple_Person_1.xmi", "simple_Person_1.xmi");
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXPECTED_OUTPUTS_DIR, "datatypes_DataHolder_1.xmi", "datatypes_DataHolder_1.xmi");
		
		// Verify attributes of Person
		Object nameValue = loadedPerson.eGet(personClass.getEStructuralFeature("name"));
		assertThat(nameValue).isEqualTo("Person_name_1");
		Object ageValue = loadedPerson.eGet(personClass.getEStructuralFeature("age"));
		assertThat(ageValue).isEqualTo(20);
		
		// Verify attributes of DataHolder
		Object stringValue = loadedDataHolder.eGet(dataHolderClass.getEStructuralFeature("stringValue"));
		assertThat(stringValue).isEqualTo("DataHolder_stringValue_1");
		Object intValue = loadedDataHolder.eGet(dataHolderClass.getEStructuralFeature("intValue"));
		assertThat(intValue).isEqualTo(20);
		
		// Store nsURIs for verification after unload
		String simpleNsURI = simplePackage.getNsURI();
		String datatypesNsURI = datatypesPackage.getNsURI();
		
		// Unload the Ecore models
		generator.unloadEcoreModels();
		
		// Verify packages are unregistered from global registry
		assertThat(EPackage.Registry.INSTANCE.containsKey(simpleNsURI)).isFalse();
		assertThat(EPackage.Registry.INSTANCE.containsKey(datatypesNsURI)).isFalse();
		
		// Verify calling unload again is safe (should not throw)
		generator.unloadEcoreModels();
	}

	/**
	 * Test that loadEcoreModel throws IOException for invalid file.
	 */
	@Test
	void testLoadEcoreModelWithInvalidFile() {
		// Test with non-existent file
		assertThatThrownBy(() -> generator.loadEcoreModel("nonexistent.ecore"))
			.isInstanceOf(IOException.class);
	}

	/**
	 * Test that loadEcoreModel throws IOException for file that doesn't contain an EPackage.
	 */
	@Test
	void testLoadEcoreModelWithNonEPackageFile() throws Exception {
		// Create a temporary XMI file with valid content but not an EPackage
		File tempFile = new File(TEST_OUTPUT_DIR, "temp-not-ecore.ecore");
		Files.createDirectories(tempFile.getParentFile().toPath());
		// Create a simple XMI file with a Person instance instead of an EPackage
		Files.writeString(tempFile.toPath(), 
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<simple:Person xmi:version=\"2.0\" xmlns:xmi=\"http://www.omg.org/XMI\" " +
			"xmlns:simple=\"http://www.example.org/simple\" name=\"Test\" age=\"25\"/>", 
			StandardCharsets.UTF_8);
		
		// Should throw IOException because it doesn't contain an EPackage
		assertThatThrownBy(() -> generator.loadEcoreModel(tempFile.getAbsolutePath()))
			.isInstanceOf(IOException.class)
			.hasMessageContaining("Failed to load Ecore file");
		
		// Clean up
		tempFile.delete();
	}

	/**
	 * Test that EcoreResourceFactoryImpl is registered only once.
	 */
	@Test
	void testLoadEcoreModelRegistersFactoryOnlyOnce() throws Exception {
		// Load first Ecore model
		EPackage package1 = generator.loadEcoreModel(TEST_INPUTS_DIR + "/simple.ecore");
		assertNotNull(package1);
		
		// Check that the factory is registered
		assertThat(generator.getResourceSet().getResourceFactoryRegistry()
				.getExtensionToFactoryMap()).containsKey("ecore");
		
		// Get the factory instance
		Object factory1 = generator.getResourceSet().getResourceFactoryRegistry()
				.getExtensionToFactoryMap().get("ecore");
		
		// Load second Ecore model
		EPackage package2 = generator.loadEcoreModel(TEST_INPUTS_DIR + "/datatypes.ecore");
		assertNotNull(package2);
		
		// Verify the factory is still the same instance (not replaced)
		Object factory2 = generator.getResourceSet().getResourceFactoryRegistry()
				.getExtensionToFactoryMap().get("ecore");
		assertThat(factory2).isSameAs(factory1);
	}

	/**
	 * Test that loadEcoreModel throws IOException for an empty file.
	 */
	@Test
	void testLoadEcoreModelWithEmptyFile() throws Exception {
		// Create an empty file
		File tempFile = new File(TEST_OUTPUT_DIR, "empty.ecore");
		Files.createDirectories(tempFile.getParentFile().toPath());
		Files.writeString(tempFile.toPath(), "", StandardCharsets.UTF_8);
		
		// Should throw IOException for empty file
		assertThatThrownBy(() -> generator.loadEcoreModel(tempFile.getAbsolutePath()))
			.isInstanceOf(IOException.class)
			.hasMessageContaining("Failed to load Ecore file");
		
		// Clean up
		tempFile.delete();
	}

	/**
	 * Test that unloadEcoreModels properly unloads resources
	 */
	@Test
	void testUnloadEcoreModelsProperlyUnloadsAndClearsState() throws Exception {
		// Load an Ecore model
		EPackage simplePackage = generator.loadEcoreModel(TEST_INPUTS_DIR + "/simple.ecore");
		assertNotNull(simplePackage);
		String nsURI = simplePackage.getNsURI();
		
		// Get the resource that was loaded
		Resource ecoreResource = simplePackage.eResource();
		assertNotNull(ecoreResource);
		
		// Verify resource is loaded (has contents)
		assertThat(ecoreResource.isLoaded()).isTrue();
		assertThat(ecoreResource.getContents()).isNotEmpty();
		
		// Unload the Ecore models
		generator.unloadEcoreModels();
		
		// Verify the resource is actually unloaded (contents cleared)
		assertThat(ecoreResource.isLoaded()).isFalse();
		assertThat(ecoreResource.getContents()).isEmpty();
		
		// Verify the package is unregistered from global registry
		assertThat(EPackage.Registry.INSTANCE.containsKey(nsURI)).isFalse();
		
		// Verify we can load the same model again (proving lists were cleared)
		EPackage reloadedPackage = generator.loadEcoreModel(TEST_INPUTS_DIR + "/simple.ecore");
		assertNotNull(reloadedPackage);
		assertThat(reloadedPackage.getNsURI()).isEqualTo(nsURI);
		
		// Get the new resource
		Resource newResource = reloadedPackage.eResource();
		assertNotNull(newResource);
		
		// Verify it's a different resource (new load)
		assertThat(newResource).isNotSameAs(ecoreResource);
		assertThat(newResource.isLoaded()).isTrue();
		
		// Verify the reloaded package is registered
		assertThat(EPackage.Registry.INSTANCE).containsKey(nsURI); // NOSONAR: false positive
		
		// Unload again
		generator.unloadEcoreModels();
		
		// Verify second unload also worked
		assertThat(newResource.isLoaded()).isFalse();
		assertThat(EPackage.Registry.INSTANCE.containsKey(nsURI)).isFalse();
	}

	/**
	 * Test that verifies tracking lists are cleared.
	 */
	@Test
	void testUnloadEcoreModelsClearsTrackingForSubsequentCycles() throws Exception {
		ResourceSet resourceSet = generator.getResourceSet();
		
		// First cycle: load package A
		EPackage pkgA = generator.loadEcoreModel(TEST_INPUTS_DIR + "/simple.ecore");
		String nsURIA = pkgA.getNsURI();
		Resource resourceA = pkgA.eResource();
		assertThat(EPackage.Registry.INSTANCE).containsKey(nsURIA); // NOSONAR: false positive
		assertThat(resourceSet.getResources()).contains(resourceA);
		
		// Unload package A - should clear tracking lists
		generator.unloadEcoreModels();
		assertThat(EPackage.Registry.INSTANCE.containsKey(nsURIA)).isFalse();
		assertThat(resourceSet.getResources()).doesNotContain(resourceA);
		
		// Second cycle: load package B (different nsURI)
		EPackage pkgB = generator.loadEcoreModel(TEST_INPUTS_DIR + "/library.ecore");
		String nsURIB = pkgB.getNsURI();
		Resource resourceB = pkgB.eResource();
		assertThat(nsURIB).isNotEqualTo(nsURIA);
		assertThat(EPackage.Registry.INSTANCE).containsKey(nsURIB); // NOSONAR: false positive
		
		// Manually add resourceA back to the ResourceSet (simulating external modification)
		// and register package A in the global registry
		resourceSet.getResources().add(resourceA);
		EPackage.Registry.INSTANCE.put(nsURIA, pkgA);
		assertThat(resourceSet.getResources()).contains(resourceA);
		assertThat(resourceSet.getResources()).contains(resourceB);
		
		// Unload package B - if loadedEcoreResources wasn't cleared after first cycle,
		// it would still contain resourceA and would incorrectly remove it from ResourceSet
		generator.unloadEcoreModels();
		
		// Package B should be unregistered, resource B should be removed
		assertThat(EPackage.Registry.INSTANCE.containsKey(nsURIB)).isFalse();
		assertThat(resourceSet.getResources()).doesNotContain(resourceB);
		
		// Package A should STILL be registered, and resourceA should STILL be in ResourceSet
		// (unload should only affect package B and resourceB)
		assertThat(EPackage.Registry.INSTANCE).containsKey(nsURIA); // NOSONAR: false positive
		assertThat(resourceSet.getResources()).contains(resourceA);
		
		// Clean up
		EPackage.Registry.INSTANCE.remove(nsURIA);
		resourceSet.getResources().remove(resourceA);
	}
}
