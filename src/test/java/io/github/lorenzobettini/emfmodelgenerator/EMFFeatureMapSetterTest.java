package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEAttributeExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEClassExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEReferenceExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.loadEcoreModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for EMFFeatureMapSetter.
 * This class achieves 100% coverage of the feature map setter functionality.
 */
class EMFFeatureMapSetterTest {

	private static final String TEST_INPUTS_DIR = "src/test/resources/inputs";
	
	private EMFFeatureMapSetter setter;
	private EPackage ePackage;
	private EClass libraryClass;
	private EAttribute peopleAttr;

	@BeforeEach
	void setUp() {
		setter = new EMFFeatureMapSetter();
		ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore");
		libraryClass = assertEClassExists(ePackage, "Library");
		peopleAttr = assertEAttributeExists(libraryClass, "people");
		
		// Register the package for test
		EMFTestUtils.registerPackageForTest(ePackage);
	}

	@AfterEach
	void tearDown() {
		EMFTestUtils.cleanupRegisteredPackages();
	}

	@Test
	void testSetFeatureMapPopulatesFeatureMap() {
		final var library = EcoreUtil.create(libraryClass);
		
		setter.setFeatureMap(library, peopleAttr);
		
		final var featureMap = (FeatureMap) library.eGet(peopleAttr);
		
		assertThat(featureMap)
			.as("Feature map should be populated")
			.isNotEmpty();
		
		// Check that derived references are automatically populated through the feature map
		final var writersRef = libraryClass.getEStructuralFeature("writers");
		final var writers = EMFUtils.getAsEObjectsList(library, writersRef);
		
		assertThat(writers)
			.as("Writers should be accessible through derived reference")
			.isNotEmpty();
	}

	@Test
	void testFeatureMapEntriesHaveCorrectTypes() {
		final var library = EcoreUtil.create(libraryClass);
		setter.setDefaultMaxCount(3);
		
		setter.setFeatureMap(library, peopleAttr);
		
		final var featureMap = (FeatureMap) library.eGet(peopleAttr);
		
		// Check that entries in the feature map have the correct EStructuralFeature set
		for (var entry : featureMap) {
			assertThat(entry.getEStructuralFeature())
				.as("Each feature map entry should have a structural feature")
				.isNotNull();
			
			assertThat(entry.getValue())
				.as("Each feature map entry should have a value")
				.isNotNull()
				.isInstanceOf(EObject.class);
		}
	}

	@Test
	void testFeatureMapMaxCountConfiguration() {
		final var library = EcoreUtil.create(libraryClass);
		
		// Set specific max count for the feature map
		setter.setMaxCountFor(peopleAttr, 5);
		
		setter.setFeatureMap(library, peopleAttr);
		
		final var featureMap = (FeatureMap) library.eGet(peopleAttr);
		
		assertThat(featureMap)
			.as("Feature map should have 5 entries as configured")
			.hasSize(5);
	}

	@Test
	void testFeatureMapWithDifferentGroupMembers() {
		final var library = EcoreUtil.create(libraryClass);
		
		// Set feature map to create 6 entries
		setter.setMaxCountFor(peopleAttr, 6);
		
		setter.setFeatureMap(library, peopleAttr);
		
		// Check the feature map itself
		final var featureMap = (FeatureMap) library.eGet(peopleAttr);
		assertThat(featureMap)
			.as("Feature map should have 6 entries")
			.hasSize(6);
		
		// Get derived references
		final var writersRef = libraryClass.getEStructuralFeature("writers");
		final var employeesRef = libraryClass.getEStructuralFeature("employees");
		final var borrowersRef = libraryClass.getEStructuralFeature("borrowers");
		
		final var writers = EMFUtils.getAsEObjectsList(library, writersRef);
		final var employees = EMFUtils.getAsEObjectsList(library, employeesRef);
		final var borrowers = EMFUtils.getAsEObjectsList(library, borrowersRef);
		
		// With 6 items and 3 group members, we should get 2 of each type (round-robin)
		assertThat(writers).hasSize(2);
		assertThat(employees).hasSize(2);
		assertThat(borrowers).hasSize(2);
	}

	@Test
	void testSetSingleFeatureThrowsException() {
		final var library = EcoreUtil.create(libraryClass);
		
		final var featureMapSetter = new EMFFeatureMapSetter() {
			public void testSetSingle(EObject owner, EAttribute attr) {
				setSingleFeature(owner, attr);
			}
		};

		assertThatThrownBy(() -> featureMapSetter.testSetSingle(library, peopleAttr))
			.isInstanceOf(UnsupportedOperationException.class)
			.hasMessage("Feature maps are always multi-valued");
	}

	@Test
	void testSetGroupMemberSelectorStrategy() {
		final var library = EcoreUtil.create(libraryClass);
		final var writersRef = assertEReferenceExists(libraryClass, "writers");
		
		final var customSelector = new EMFCandidateSelectorStrategy<EAttribute, EReference>() {
			@Override
			public EReference getNextCandidate(EObject context, EAttribute type) {
				return writersRef;
			}

			@Override
			public boolean hasCandidates(EObject context, EAttribute type) {
				return true;
			}
		};

		setter.setGroupMemberSelectorStrategy(customSelector);
		setter.setMaxCountFor(peopleAttr, 3);
		setter.setFeatureMap(library, peopleAttr);

		final var writers = EMFUtils.getAsEObjectsList(library, writersRef);
		final var employeesRef = libraryClass.getEStructuralFeature("employees");
		final var employees = EMFUtils.getAsEObjectsList(library, employeesRef);
		final var borrowersRef = libraryClass.getEStructuralFeature("borrowers");
		final var borrowers = EMFUtils.getAsEObjectsList(library, borrowersRef);

		assertThat(writers).hasSize(3);
		assertThat(employees).isEmpty();
		assertThat(borrowers).isEmpty();
	}

	@Test
	void testResetClearsGroupMemberSelectorState() {
		final var library1 = EcoreUtil.create(libraryClass);
		final var library2 = EcoreUtil.create(libraryClass);
		final var library3 = EcoreUtil.create(libraryClass);
		
		// Use count=1 so we get one item per call
		setter.setMaxCountFor(peopleAttr, 1);
		
		// First call - round-robin starts at writers (index 0)
		setter.setFeatureMap(library1, peopleAttr);
		
		// Second call WITHOUT reset - round-robin continues to employees (index 1)
		setter.setFeatureMap(library2, peopleAttr);
		
		// Call reset() - this should clear nextIndexMap so we restart from writers
		setter.reset();
		
		// Third call WITH reset - should restart at writers (index 0)
		setter.setFeatureMap(library3, peopleAttr);
		
		final var writersRef = assertEReferenceExists(libraryClass, "writers");
		final var employeesRef = assertEReferenceExists(libraryClass, "employees");
		
		// library1 should have writers (first call, index 0)
		assertThat(EMFUtils.getAsEObjectsList(library1, writersRef)).hasSize(1);
		assertThat(EMFUtils.getAsEObjectsList(library1, employeesRef)).isEmpty();
		
		// library2 should have employees (second call WITHOUT reset, index 1)
		assertThat(EMFUtils.getAsEObjectsList(library2, writersRef)).isEmpty();
		assertThat(EMFUtils.getAsEObjectsList(library2, employeesRef)).hasSize(1);
		
		// library3 should have writers again (third call WITH reset, back to index 0)
		assertThat(EMFUtils.getAsEObjectsList(library3, writersRef)).hasSize(1);
		assertThat(EMFUtils.getAsEObjectsList(library3, employeesRef)).isEmpty();
	}

	@Test
	void testSetFeatureMapClearsCreatedEObjects() {
		final var library1 = EcoreUtil.create(libraryClass);
		final var library2 = EcoreUtil.create(libraryClass);
		
		setter.setMaxCountFor(peopleAttr, 2);
		
		// First call - populate and get reference to created objects collection
		final var createdObjects1 = setter.setFeatureMap(library1, peopleAttr);
		final var firstCallSize = createdObjects1.size();
		assertThat(firstCallSize).isEqualTo(2);
		
		// Second call - should clear the collection before populating
		final var createdObjects2 = setter.setFeatureMap(library2, peopleAttr);
		
		// Both references point to the same collection (getCreatedEObjects returns the same list)
		// After clear() and second population, it should only have 2 objects, not 4
		assertThat(createdObjects2)
			.as("Second call should clear and repopulate, resulting in 2 objects, not 4")
			.hasSize(2);
	}

	@Test
	void testEmptyGroupMembersReturnsEarly() {
		final var emptyPackage = loadEcoreModel(TEST_INPUTS_DIR, "featuremap_no_members.ecore");
		EMFTestUtils.registerPackageForTest(emptyPackage);
		
		final var testClass = assertEClassExists(emptyPackage, "TestClass");
		final var emptyFeatureMapAttr = assertEAttributeExists(testClass, "emptyFeatureMap");
		final var testInstance = EcoreUtil.create(testClass);
		
		final var createdObjects = setter.setFeatureMap(testInstance, emptyFeatureMapAttr);
		
		assertThat(createdObjects)
			.as("No objects should be created when feature map has no group members")
			.isEmpty();
		
		final var featureMap = (FeatureMap) testInstance.eGet(emptyFeatureMapAttr);
		
		assertThat(featureMap)
			.as("Feature map should be empty when no group members exist")
			.isEmpty();
	}
}
