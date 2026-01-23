package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.loadEcoreModel;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Learning tests for EMF mechanisms.
 */
class EMFLearningTest {

	private ResourceSet resourceSet;

	@BeforeEach
	void setUp() {
		// Create a ResourceSet with XMI factory registered
		resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put("*", new XMIResourceFactoryImpl());
	}

	/**
	 * This test demonstrates the critical behavior of EMF containment references across resources.
	 * 
	 * KEY FINDINGS:
	 * 1. Container references (opposites of containment) cannot be set directly by the setter
	 * 2. You must set containment from the containing side (add to the collection)
	 * 3. EMF does NOT automatically remove objects from their resource when contained
	 * 4. While the object remains in its original resource, EMF serializes containment as a cross-reference
	 * 5. Only after manual removal from the original resource does EMF serialize true inline containment
	 */
	@Test
	void testSettingBidirectionalContainmentReferenceAcrossResources() throws Exception {
		// Load the simplelibrary ecore model
		EPackage libraryPackage = loadEcoreModel("src/test/resources/inputs", "simplelibrary.ecore");
		resourceSet.getPackageRegistry().put(libraryPackage.getNsURI(), libraryPackage);

		// Get the EClasses
		EClass libraryClass = (EClass) libraryPackage.getEClassifier("Library");
		EClass bookClass = (EClass) libraryPackage.getEClassifier("Book");

		// Create a Library in one resource
		Resource libraryResource = resourceSet.createResource(URI.createURI("library.xmi"));
		EObject library = EcoreUtil.create(libraryClass);
		library.eSet(libraryClass.getEStructuralFeature("name"), "My Library");
		libraryResource.getContents().add(library);

		// Create a Book in another resource
		Resource bookResource = resourceSet.createResource(URI.createURI("book.xmi"));
		EObject book = EcoreUtil.create(bookClass);
		book.eSet(bookClass.getEStructuralFeature("title"), "My Book");
		bookResource.getContents().add(book);

		// Verify initial state
		assertThat(bookResource.getContents()).contains(book);
		assertThat(libraryResource.getContents()).contains(library);

		// Get the "library" reference on Book (the opposite of Library.books containment)
		EReference libraryRef = (EReference) bookClass.getEStructuralFeature("library");
		assertThat(libraryRef.isContainment()).isFalse();
		assertThat(libraryRef.getEOpposite()).isNotNull();
		assertThat(libraryRef.getEOpposite().isContainment()).isTrue();

		// IMPORTANT: The "library" reference is marked as a container reference 
		// (isContainer() == true) because it's the opposite of a containment reference
		assertThat(libraryRef.isContainer()).isTrue();

		// Container references are invalid and should not be set manually
		// They are managed automatically by EMF when you set the containment side
		assertThat(EMFUtils.isValidReference(libraryRef)).isFalse();

		// NOTE: The setter no longer validates features (validation is done in the populator)
		// If you call the setter directly with an invalid reference, it may attempt to set it
		// For this reason, we should NOT call the setter with a container reference
		// When using the populator, invalid references are filtered out before reaching the setter

		// Verify the reference is not set initially
		assertThat(book.eGet(libraryRef)).isNull();
		assertThat(book.eContainer()).isNull();

		// To properly set this relationship, you need to set it from the containment side:
		// Add the Book to the Library's books collection
		var booksInLibrary = EMFUtils.getAsList(library, libraryClass.getEStructuralFeature("books"));
		booksInLibrary.add(book);

		// NOW the opposite reference is automatically set by EMF
		assertThat(book.eGet(libraryRef)).isSameAs(library);

		// Verify the Book's container is now the Library
		assertThat(book.eContainer()).isSameAs(library);

		// IMPORTANT DISCOVERY: The Book is NOT automatically removed from its original resource!
		// It remains in bookResource even though it's now contained by the Library.
		// This is a quirk of EMF - containment doesn't automatically manage resource membership.
		assertThat(bookResource.getContents()).contains(book);

		// However, the Book is ALSO accessible through the Library's resource
		// (via the containment hierarchy)
		assertThat(book.eResource()).isSameAs(bookResource); // Still in its original resource!

		// CHECKPOINT 1: Let's examine the XMI files at this stage
		// The book is in both places: as a root in bookResource AND contained in the library
		ByteArrayOutputStream libraryOut1 = new ByteArrayOutputStream();
		ByteArrayOutputStream bookOut1 = new ByteArrayOutputStream();
		libraryResource.save(libraryOut1, null);
		bookResource.save(bookOut1, null);

		String libraryXml1 = libraryOut1.toString();
		String bookXml1 = bookOut1.toString();

		// IMPORTANT: The library XMI uses a CROSS-DOCUMENT REFERENCE, not inline containment!
		// EMF serializes it as an href because the book is still in its own resource
		String expectedLibraryXml1 = """
				<?xml version="1.0" encoding="ASCII"?>
				<simplelibrary:Library xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI" xmlns:simplelibrary="http://www.example.org/simplelibrary" name="My Library">
				  <books href="book.xmi#/"/>
				</simplelibrary:Library>
				""";
		assertThat(libraryXml1).isEqualToIgnoringNewLines(expectedLibraryXml1);

		// The book XMI still contains the book as a root element
		String expectedBookXml1 = """
				<?xml version="1.0" encoding="ASCII"?>
				<simplelibrary:Book xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI" xmlns:simplelibrary="http://www.example.org/simplelibrary" title="My Book"/>
				""";
		assertThat(bookXml1).isEqualToIgnoringNewLines(expectedBookXml1);

		// Both resources should be valid according to EMF
		EMFTestUtils.validateModel(library);
		EMFTestUtils.validateModel(book);

		// To properly complete the move, you need to manually remove it from the original resource:
		bookResource.getContents().remove(book);

		// NOW it's only accessible through the Library's resource
		assertThat(bookResource.getContents()).isEmpty();
		assertThat(book.eResource()).isSameAs(libraryResource);

		// CHECKPOINT 2: Let's examine the XMI files after removing from bookResource
		ByteArrayOutputStream libraryOut2 = new ByteArrayOutputStream();
		ByteArrayOutputStream bookOut2 = new ByteArrayOutputStream();
		libraryResource.save(libraryOut2, null);
		bookResource.save(bookOut2, null);

		String libraryXml2 = libraryOut2.toString();
		String bookXml2 = bookOut2.toString();

		// NOW the library XMI contains the book INLINE (true containment serialization)
		String expectedLibraryXml2 = """
				<?xml version="1.0" encoding="ASCII"?>
				<simplelibrary:Library xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI" xmlns:simplelibrary="http://www.example.org/simplelibrary" name="My Library">
				  <books title="My Book"/>
				</simplelibrary:Library>
				""";
		assertThat(libraryXml2).isEqualToIgnoringNewLines(expectedLibraryXml2);

		// The book XMI is now empty (just the XMI header)
		String expectedBookXml2 = """
				<?xml version="1.0" encoding="ASCII"?>
				<xmi:XMI xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI"/>
				""";
		assertThat(bookXml2).isEqualToIgnoringNewLines(expectedBookXml2);

		// Both resources should still be valid
		EMFTestUtils.validateModel(library);
		// book is now contained, so we validate through its container
		assertThat(booksInLibrary).contains(book);
	}

	/**
	 * Key learning about eIsSet behavior for single-valued feature:
	 * <ul>
	 * <li>For single-valued attributes/references, eIsSet returns false if the
	 * value is null.</li>
	 * <li>Setting the value to null keeps eIsSet false.</li>
	 * <li>Setting a non-null value makes eIsSet return true.</li>
	 * <li>Setting back to null makes eIsSet return false again.</li>
	 * </ul>
	 */
	@Test
	void testEIsSetSingleFeature() {
		// Load the simplelibrary ecore model
		EPackage libraryPackage = loadEcoreModel("src/test/resources/inputs", "simplelibrary.ecore");
		resourceSet.getPackageRegistry().put(libraryPackage.getNsURI(), libraryPackage);

		// Get the EClasses
		EClass libraryClass = (EClass) libraryPackage.getEClassifier("Library");

		// Create a Library in one resource
		resourceSet.createResource(URI.createURI("library.xmi"));
		EObject library = EcoreUtil.create(libraryClass);

		// Test eIsSet on name attribute before setting
		var libraryName = libraryClass.getEStructuralFeature("name");
		assertThat(library.eIsSet(libraryName)).isFalse();
		// Set name attribute to null: still not set
		library.eSet(libraryName, null);
		assertThat(library.eIsSet(libraryName)).isFalse();
		// Set name attribute to a value: now it's set
		library.eSet(libraryName, "My Library");
		assertThat(library.eIsSet(libraryName)).isTrue();
		// Set name attribute back to null: now it's unset
		library.eSet(libraryName, null);
		assertThat(library.eIsSet(libraryName)).isFalse();
	}

	/**
	 * Key learning about eIsSet behavior for multi-valued feature:
	 * <ul>
	 * <li>For multi-valued attributes/references, eIsSet returns false if the
	 * collection is empty.</li>
	 * <li>Adding elements to the collection makes eIsSet return true.</li>
	 * <li>Clearing the collection makes eIsSet return false again.</li>
	 * </ul>
	 */
	@Test
	void testEIsSetMultiFeature() {
		// Load the simplelibrary ecore model
		EPackage libraryPackage = loadEcoreModel("src/test/resources/inputs", "simplelibrary.ecore");
		resourceSet.getPackageRegistry().put(libraryPackage.getNsURI(), libraryPackage);

		// Get the EClasses
		EClass libraryClass = (EClass) libraryPackage.getEClassifier("Library");
		EClass bookClass = (EClass) libraryPackage.getEClassifier("Book");

		// Create a Library in one resource
		resourceSet.createResource(URI.createURI("library.xmi"));
		EObject library = EcoreUtil.create(libraryClass);

		// Test eIsSet on books reference before setting
		var libraryBooks = libraryClass.getEStructuralFeature("books");
		assertThat(library.eIsSet(libraryBooks)).isFalse();
		// Retrieve books collection
		var booksCollection = EMFUtils.getAsList(library, libraryBooks);
		// still unset
		assertThat(library.eIsSet(libraryBooks)).isFalse();
		// Add a book to the collection: now it's set
		EObject book1 = EcoreUtil.create(bookClass);
		booksCollection.add(book1);
		assertThat(library.eIsSet(libraryBooks)).isTrue();
		// clear the collection: now it's unset
		booksCollection.clear();
		assertThat(library.eIsSet(libraryBooks)).isFalse();
	}

	/**
	 * This test demonstrates that adding the same cross-reference multiple times
	 * does not create duplicate references in EMF.
	 * 
	 * For example, if we add the same authors to a Book's authors reference multiple times,
	 * the resulting collection will only contain unique references.
	 * 
	 * @throws Exception
	 */
	@Test
	void testAddingTheSameCrossReferenceMultipleTimes() {
		// Load the simplelibrary ecore model
		EPackage libraryPackage = loadEcoreModel("src/test/resources/inputs", "simplelibrary.ecore");
		resourceSet.getPackageRegistry().put(libraryPackage.getNsURI(), libraryPackage);

		// Get the EClasses
		EClass bookClass = (EClass) libraryPackage.getEClassifier("Book");
		EClass authorClass = (EClass) libraryPackage.getEClassifier("Author");
		// Create a Book
		EObject book = EcoreUtil.create(bookClass);
		book.eSet(bookClass.getEStructuralFeature("title"), "My Book");
		// Create an Author
		EObject author1 = EcoreUtil.create(authorClass);
		author1.eSet(authorClass.getEStructuralFeature("name"), "John Doe");
		// Create another Author
		EObject author2 = EcoreUtil.create(authorClass);
		author2.eSet(authorClass.getEStructuralFeature("name"), "Jane Smith");

		// Get the authors reference on Book
		var authorsRef = EMFUtils.getAsEObjectsList(book, bookClass.getEStructuralFeature("authors"));
		// Add the same author multiple times
		authorsRef.add(author1);
		authorsRef.add(author2);
		authorsRef.add(author1);
		// Verify that the authors collection contains only one instance of the author
		assertThat(authorsRef).containsExactlyInAnyOrder(author1, author2);
	}	

	/**
	 * This test demonstrates saving a resource with the schemaLocation option enabled.
	 * 
	 * When saving with this option, EMF includes the xsi:schemaLocation attribute
	 * in the root element, pointing to the Ecore model location.
	 * 
	 * Note that the Ecore model must be registered in the ResourceSet's package registry
	 * and must be in the same resource set as the resource being saved.
	 * 
	 * @throws Exception
	 */
	@Test
	void testSaveXMIWithSchemaLocation() throws Exception {
		// Load the simplelibrary ecore model
		EPackage libraryPackage = loadEcoreModel("src/test/resources/inputs", "simplelibrary.ecore");
		resourceSet.getPackageRegistry().put(libraryPackage.getNsURI(), libraryPackage);

		// Create a Library in a resource
		// IMPORTANT: with an absolute URI to test schemaLocation
		// by making the URI absolute, EMF will include it in the schemaLocation attribute
		// relative to the file path of the Ecore file
		Resource libraryResource = resourceSet.createResource(URI.createFileURI(
			Paths.get("library.xmi")
				.toAbsolutePath()
				.normalize() // "avoid "." and ".."
				.toString()));

		EObject library = EcoreUtil.create((EClass) libraryPackage.getEClassifier("Library"));
		library.eSet(library.eClass().getEStructuralFeature("name"), "My Library");
		libraryResource.getContents().add(library);

		// Save with schemaLocation option
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Map<Object, Object> options = new HashMap<>();
		options.put(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE);
		// use 10 to make output more readable
		// that's also the default option of our EMFResourceSetHelper
		options.put(XMLResource.OPTION_LINE_WIDTH, 10);
		libraryResource.save(out, options);

		String xmiOutput = out.toString();
		String expectedXmiOutput = """
				<?xml version="1.0" encoding="ASCII"?>
				<simplelibrary:Library
				    xmi:version="2.0"
				    xmlns:xmi="http://www.omg.org/XMI"
				    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				    xmlns:simplelibrary="http://www.example.org/simplelibrary"
				    xsi:schemaLocation="http://www.example.org/simplelibrary src/test/resources/inputs/simplelibrary.ecore"
				    name="My Library"/>
				""";
		assertThat(xmiOutput).isEqualToIgnoringNewLines(expectedXmiOutput);
	}

	/**
	 * We load the 3 Ecore files in different resources, with references
	 * based on nsURIs; thanks to the EMF registry, they are properly linked based on
	 * their NsURI and the referred superclasses are exactly the original ones.
	 */
	@Test
	void testEcoreCrossReferencesBasedOnNsURIs() {
		// Load the 3 ecore models
		EPackage basePackage = loadEcoreModel("src/test/resources/inputs", "hierarchy-base.ecore");

		EPackage intermediatePackage = loadEcoreModel("src/test/resources/inputs", "hierarchy-middle.ecore");

		EPackage finalPackage = loadEcoreModel("src/test/resources/inputs", "hierarchy-leaf.ecore");

		// Get the EClasses
		EClass baseClass = (EClass) basePackage.getEClassifier("BaseItem");
		EClass middleClass = (EClass) intermediatePackage.getEClassifier("MiddleItem");
		EClass leafClass = (EClass) finalPackage.getEClassifier("LeafItem");
		// Verify the inheritance hierarchy
		assertThat(middleClass.getESuperTypes()).containsExactly(baseClass);
		assertThat(leafClass.getESuperTypes()).containsExactly(middleClass);
		// Verify all supertypes
		assertThat(leafClass.getEAllSuperTypes()).containsExactlyInAnyOrder(middleClass, baseClass);
	}

	/**
	 * We load the 3 Ecore files in different resources, with references
	 * based on file URIs; thanks to the EMF they are properly resolved, but the referred superclasses are
	 * not the original ones: they EPackages are loaded in new resources of the resource set of the
	 * EClass being resolved.
	 */
	@Test
	void testEcoreCrossReferencesBasedOnFileURIs() {
		// Load the 3 ecore models
		EPackage basePackage = loadEcoreModel("src/test/resources/inputs", "hierarchy-base-fileuri.ecore");

		EPackage intermediatePackage = loadEcoreModel("src/test/resources/inputs", "hierarchy-middle-fileuri.ecore");

		EPackage finalPackage = loadEcoreModel("src/test/resources/inputs", "hierarchy-leaf-fileuri.ecore");

		// Get the EClasses
		EClass baseClass = (EClass) basePackage.getEClassifier("BaseItem");
		EClass middleClass = (EClass) intermediatePackage.getEClassifier("MiddleItem");
		EClass leafClass = (EClass) finalPackage.getEClassifier("LeafItem");
		// Verify the inheritance hierarchy
		assertThat(middleClass.getESuperTypes()).hasSize(1);
		assertThat(middleClass.getESuperTypes().get(0)).isNotSameAs(baseClass);
		assertThat(leafClass.getESuperTypes()).hasSize(1);
		assertThat(leafClass.getESuperTypes().get(0)).isNotSameAs(middleClass);
		// Verify all supertypes
		assertThat(leafClass.getEAllSuperTypes()).hasSize(2);
		// verify that the supertypes are not the original ones
		for (var superType : leafClass.getEAllSuperTypes()) {
			assertThat(superType).isNotIn(middleClass, baseClass);
		}
		// verify the resource set of the middleClass contains also a resource for the basePackage
		ResourceSet middleResourceSet = middleClass.eResource().getResourceSet();
		assertThat(middleResourceSet.getResources()).anySatisfy(res -> {
			assertThat(res.getContents()).anySatisfy(eObj -> {
				if (eObj instanceof EPackage ePackage) {
					assertThat(ePackage.getNsURI()).isEqualTo(basePackage.getNsURI());
				}
			});
		});
		// verify the resource set of the leafClass contains also a resource for the middlePackage
		ResourceSet leafResourceSet = leafClass.eResource().getResourceSet();
		assertThat(leafResourceSet.getResources()).anySatisfy(res -> {
			assertThat(res.getContents()).anySatisfy(eObj -> {
				if (eObj instanceof EPackage ePackage) {
					assertThat(ePackage.getNsURI()).isEqualTo(intermediatePackage.getNsURI());
				}
			});
		});
		// and for the basePackage as well
		assertThat(leafResourceSet.getResources()).anySatisfy(res -> {
			assertThat(res.getContents()).anySatisfy(eObj -> {
				if (eObj instanceof EPackage ePackage) {
					assertThat(ePackage.getNsURI()).isEqualTo(basePackage.getNsURI());
				}
			});
		});
	}

	@Test
	void testFeatureMapExample() throws IOException {
		// Load the featuremap ecore model
		EPackage pkg = loadEcoreModel("src/test/resources/inputs", "featuremap-example.ecore");
		resourceSet.getPackageRegistry().put(pkg.getNsURI(), pkg);

		// Create a Library in a resource
		// IMPORTANT: with an absolute URI to test schemaLocation
		// by making the URI absolute, EMF will include it in the schemaLocation attribute
		// relative to the file path of the Ecore file
		Resource resource = resourceSet.createResource(URI.createFileURI(
			Paths.get("library.xmi")
				.toAbsolutePath()
				.normalize() // "avoid "." and ".."
				.toString()));

		// Look up EClasses
		EClass modelRootClass = (EClass) pkg.getEClassifier("ModelRoot");
		EClass documentClass = (EClass) pkg.getEClassifier("Document");
		EClass sectionClass = (EClass) pkg.getEClassifier("Section");
		EClass figureClass = (EClass) pkg.getEClassifier("Figure");

		// Look up the FeatureMap features and the grouped features
		EAttribute contentGroupAttr = (EAttribute) documentClass.getEStructuralFeature("contentGroup");
		EStructuralFeature sectionsRef = documentClass.getEStructuralFeature("sections");
		EStructuralFeature figuresRef = documentClass.getEStructuralFeature("figures");

		// Create instances
		EObject root = pkg.getEFactoryInstance().create(modelRootClass);
		EObject doc = pkg.getEFactoryInstance().create(documentClass);

		EObject sec1 = pkg.getEFactoryInstance().create(sectionClass);
		EObject sec2 = pkg.getEFactoryInstance().create(sectionClass);
		EObject fig1 = pkg.getEFactoryInstance().create(figureClass);

		// Set basic attributes on Section / Figure
		sec1.eSet(sectionClass.getEStructuralFeature("title"), "Some title");
		sec1.eSet(sectionClass.getEStructuralFeature("text"), "some text");

		sec2.eSet(sectionClass.getEStructuralFeature("title"), "Another title");
		sec2.eSet(sectionClass.getEStructuralFeature("text"), "Some text");

		fig1.eSet(figureClass.getEStructuralFeature("caption"), "A figure");

		// Use the Document.contentGroup FeatureMap to add sections and figure
		FeatureMap contentGroup = (FeatureMap) doc.eGet(contentGroupAttr);

		// Order matters: this is the order they’ll appear in XMI
		contentGroup.add(sectionsRef, sec1);
		contentGroup.add(figuresRef, fig1);
		contentGroup.add(sectionsRef, sec2);

		// Wire Document into ModelRoot
		root.eSet(modelRootClass.getEStructuralFeature("document"), doc);

		resource.getContents().add(root);

		// Save with schemaLocation option
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Map<Object, Object> options = new HashMap<>();
		options.put(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE);
		// use 10 to make output more readable
		// that's also the default option of our EMFResourceSetHelper
		options.put(XMLResource.OPTION_LINE_WIDTH, 10);
		resource.save(out, options);

		String xmiOutput = out.toString();
		String expectedXmiOutput = """
				<?xml version="1.0" encoding="ASCII"?>
				<fm:ModelRoot
				    xmi:version="2.0"
				    xmlns:xmi="http://www.omg.org/XMI"
				    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				    xmlns:fm="http://example.org/featuremap"
				    xsi:schemaLocation="http://example.org/featuremap src/test/resources/inputs/featuremap-example.ecore">
				  <document>
				    <sections
				        title="Some title"
				        text="some text"/>
				    <figures
				        caption="A figure"/>
				    <sections
				        title="Another title"
				        text="Some text"/>
				  </document>
				</fm:ModelRoot>
				""";
		assertThat(xmiOutput).isEqualToIgnoringNewLines(expectedXmiOutput);
	}
}
