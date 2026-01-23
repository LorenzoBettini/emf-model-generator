package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createInstance;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEAttributeExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEClassExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEReferenceExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.loadEcoreModel;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.validateModel;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EMFInstancePopulatorTest {

	private static final String TEST_INPUTS_DIR = "target/inputs";
	private static final String TEST_OUTPUT_DIR = "target/test-output";

	private ResourceSet resourceSet;
	private EMFInstancePopulator populator;
	private EMFPrettyPrinter printer;

	@BeforeEach
	void setUp() {
		// Create and configure ResourceSet
		resourceSet = EMFResourceSetHelper.createResourceSet();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put("*", new XMIResourceFactoryImpl());
		
		// Create populator with proper configuration
		populator = new EMFInstancePopulator();
		
		// Create pretty printer for assertions
		printer = new EMFPrettyPrinter();
		
		// Clean test output directory
		final File testOutputDir = new File(TEST_OUTPUT_DIR);
		if (testOutputDir.exists()) {
			for (File file : testOutputDir.listFiles()) {
				if (!file.isDirectory()) {
					file.delete();
				}
			}
		}
	}

	private EObject createInstanceInResource(final EObject instance, final String fileName) {
		final Resource resource = resourceSet.createResource(
				URI.createFileURI(new File(TEST_OUTPUT_DIR, fileName).getAbsolutePath()));
		resource.getContents().add(instance);
		return instance;
	}

	@Test
	void shouldPopulateSimpleEObjectWithAttributes() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simple.ecore");
		final var person = createInstance(ePackage, "Person");
		createInstanceInResource(person, "person.xmi");
		
		populator.populateEObjects(person);
		
		// Verify specific attribute values
		assertThat(person.eGet(person.eClass().getEStructuralFeature("name")))
				.isEqualTo("Person_name_1");
		assertThat(person.eGet(person.eClass().getEStructuralFeature("age")))
				.isEqualTo(20);
		
		validateModel(person);
	}

	@Test
	void shouldPopulateEObjectWithAttributesAndPrintStructure() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simple.ecore");
		final var person = createInstance(ePackage, "Person");
		createInstanceInResource(person, "person.xmi");
		
		populator.populateEObjects(person);
		
		final var result = printer.prettyPrint(person);
		
		assertThat(result).isEqualTo("""
			Person
			  name: "Person_name_1"
			  age: 20
			""");
		
		validateModel(person);
	}

	@Test
	void shouldPopulateLibraryWithContainmentReferences() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "library.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library.xmi");
		
		populator.populateEObjects(library);
		
		// Verify library name
		assertThat(library.eGet(library.eClass().getEStructuralFeature("name")))
				.isEqualTo("Library_name_1");
		
		// Verify shelves were populated (default count is 2)
		final var shelves = EMFUtils.getAsList(library,
				library.eClass().getEStructuralFeature("shelves"));
		assertThat(shelves).hasSize(2);
		
		// Verify first shelf has books
		final var shelf = (EObject) shelves.get(0);
		assertThat(shelf.eGet(shelf.eClass().getEStructuralFeature("name")))
				.isEqualTo("Shelf_name_1");
		
		final var books = EMFUtils.getAsList(shelf, shelf.eClass().getEStructuralFeature("books"));
		assertThat(books).hasSize(2);
		
		// Verify first book
		final var book = (EObject) books.get(0);
		assertThat(book.eGet(book.eClass().getEStructuralFeature("title")))
				.isEqualTo("Book_title_1");
		
		validateModel(library);
	}

	@Test
	void shouldPopulateLibraryWithCrossReferences() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "library.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library.xmi");
		
		populator.populateEObjects(library);
		
		// Verify director is populated
		final var director = (EObject) library.eGet(
				library.eClass().getEStructuralFeature("director"));
		assertThat(director).isNotNull();
		assertThat(director.eGet(director.eClass().getEStructuralFeature("name")))
				.isEqualTo("Person_name_1");
		
		// Books should have cross-references to the director (author)
		final var shelves = EMFUtils.getAsList(library,
				library.eClass().getEStructuralFeature("shelves"));
		final var shelf = (EObject) shelves.get(0);
		final var books = EMFUtils.getAsList(shelf, shelf.eClass().getEStructuralFeature("books"));
		final var book = (EObject) books.get(0);
		final var author = book.eGet(book.eClass().getEStructuralFeature("author"));
		
		assertThat(author).isNotNull().isSameAs(director);
		
		validateModel(library);
	}

	@Test
	void shouldRespectConfiguredContainmentReferenceMultiValuedCount() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "library.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library.xmi");
		
		populator.getContainmentReferenceSetter().setDefaultMaxCount(3);
		populator.populateEObjects(library);
		
		final var shelves = EMFUtils.getAsList(library,
				library.eClass().getEStructuralFeature("shelves"));
		
		assertThat(shelves).hasSize(3);
		
		validateModel(library);
	}

	@Test
	void shouldRespectMaxDepthZero() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "library.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library.xmi");
		
		// Set max depth to 0: only attributes at root, no references
		// This ensures depth=0 check 0 < 0 is false, hitting the else branch
		populator.setMaxDepth(0);
		populator.populateEObjects(library);
		
		final var shelves = EMFUtils.getAsList(library,
				library.eClass().getEStructuralFeature("shelves"));
		
		// With max depth 0, we only get attributes, no references
		assertThat(shelves).isEmpty();
		
		// But the library itself should have attributes set
		assertThat(library.eGet(library.eClass().getEStructuralFeature("name")))
				.isNotNull();
		
		validateModel(library);
	}

	@Test
	void shouldRespectMaxDepthOne() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "library.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library.xmi");
		
		// Set max depth to 1: Library gets populated (depth 0), its shelves get populated (depth 1)
		// but books inside shelves won't get populated (would be depth 2)
		populator.setMaxDepth(1);
		populator.populateEObjects(library);
		
		final var shelves = EMFUtils.getAsList(library,
				library.eClass().getEStructuralFeature("shelves"));
		
		// With max depth 1, shelves should be populated
		assertThat(shelves).isNotEmpty();
		
		// But books inside shelves should not be populated (depth limit reached)
		final var shelf = (EObject) shelves.get(0);
		final var books = EMFUtils.getAsList(shelf, shelf.eClass().getEStructuralFeature("books"));
		assertThat(books).isEmpty();
		
		validateModel(library);
	}

	@Test
	void shouldHitMaxDepthBranchWithHighMaxDepth() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "library.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library.xmi");
		
		// Set a very low max depth to ensure the depth limit check is hit during recursion
		populator.setMaxDepth(2);
		populator.populateEObjects(library);
		
		// Verify the structure is still valid
		final var shelves = EMFUtils.getAsList(library,
				library.eClass().getEStructuralFeature("shelves"));
		assertThat(shelves).isNotEmpty();
		
		final var shelf = (EObject) shelves.get(0);
		final var books = EMFUtils.getAsList(shelf, shelf.eClass().getEStructuralFeature("books"));
		// At depth 2, books should be populated (depth 0: library, depth 1: shelf, depth 2: books)
		assertThat(books).isNotEmpty();
		
		validateModel(library);
	}

	@Test
	void shouldHandleAllDataTypes() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "alltypes.ecore");
		final var allTypes = createInstance(ePackage, "AllTypesHolder");
		createInstanceInResource(allTypes, "alltypes.xmi");
		
		populator.populateEObjects(allTypes);
		
		// Verify that various attributes are populated
		assertThat(allTypes.eGet(allTypes.eClass().getEStructuralFeature("stringAttr")))
				.isNotNull();
		assertThat(allTypes.eGet(allTypes.eClass().getEStructuralFeature("intAttr")))
				.isNotNull();
		assertThat(allTypes.eGet(allTypes.eClass().getEStructuralFeature("boolAttr")))
				.isNotNull();
		
		validateModel(allTypes);
	}

	@Test
	void shouldPopulateEObjectAndPreserveContainmentReferences() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "library.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library.xmi");
		
		populator.populateEObjects(library);
		
		final var shelves = EMFUtils.getAsList(library,
				library.eClass().getEStructuralFeature("shelves"));
		
		// All shelves should be contained in library
		for (Object shelf : shelves) {
			final var eShelf = (EObject) shelf;
			assertThat(eShelf.eContainer()).isSameAs(library);
		}
		
		validateModel(library);
	}

	@Test
	void shouldPopulateDifferentEClassesIndependently() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "library.ecore");
		
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library.xmi");
		final var person = createInstance(ePackage, "Person");
		createInstanceInResource(person, "person.xmi");
		
		populator.populateEObjects(library);
		populator.populateEObjects(person);
		
		// Verify library was populated
		assertThat(library.eGet(library.eClass().getEStructuralFeature("name")))
				.isEqualTo("Library_name_1");
		final var shelves = EMFUtils.getAsList(library,
				library.eClass().getEStructuralFeature("shelves"));
		assertThat(shelves).isNotEmpty();
		
		// Verify person was populated independently
		assertThat(person.eGet(person.eClass().getEStructuralFeature("name")))
				.isEqualTo("Person_name_2");
		
		validateModel(library);
		validateModel(person);
	}

	// ============= Tests with simplelibrary.ecore =============

	@Test
	void shouldPopulateSimpleLibraryWithMultipleBooks() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simplelibrary.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library.xmi");
		
		populator.getAttributeSetter().setDefaultMaxCount(2);
		populator.getContainmentReferenceSetter().setDefaultMaxCount(2);
		populator.getCrossReferenceSetter().setDefaultMaxCount(2);
		populator.populateEObjects(library);
		
		// Verify library was populated
		assertThat(library.eGet(library.eClass().getEStructuralFeature("name")))
				.isEqualTo("Library_name_1");
		
		// Verify books count (should be 2 as configured)
		final var books = EMFUtils.getAsList(library,
				library.eClass().getEStructuralFeature("books"));
		assertThat(books).hasSize(2);
		
		// Verify authors count (should be 2 as configured)
		final var authors = EMFUtils.getAsList(library,
				library.eClass().getEStructuralFeature("authors"));
		assertThat(authors).hasSize(2);
		
		// Verify first book has title and authors
		final var book = (EObject) books.get(0);
		assertThat(book.eGet(book.eClass().getEStructuralFeature("title")))
				.isEqualTo("Book_title_1");
		final var bookAuthors = EMFUtils.getAsList(book,
				book.eClass().getEStructuralFeature("authors"));
		assertThat(bookAuthors).hasSize(2);
		
		// Verify first author has name and books
		final var author = (EObject) authors.get(0);
		assertThat(author.eGet(author.eClass().getEStructuralFeature("name")))
				.isEqualTo("Author_name_1");
		final var authorBooks = EMFUtils.getAsList(author,
				author.eClass().getEStructuralFeature("books"));
		assertThat(authorBooks).hasSize(2);
		
		validateModel(library);
	}

	@Test
	void shouldPopulateSimpleLibrarySingleResourceWithFullTextBlock() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simplelibrary.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library_single.xmi");
		
		populator.getContainmentReferenceSetter().setDefaultMaxCount(3);
		populator.getCrossReferenceSetter().setDefaultMaxCount(2);
		populator.populateEObjects(library);
		
		final var result = printer.prettyPrint(library);
		
		assertThat(result).isEqualTo("""
				Library
				  name: "Library_name_1"
				  books (3):
				    [0] Book
				      title: "Book_title_1"
				      authors (opposite) (2):
				        [0] -> Author (authors[0])
				        [1] -> Author (authors[1])
				    [1] Book
				      title: "Book_title_2"
				      authors (opposite) (2):
				        [0] -> Author (authors[2])
				        [1] -> Author (authors[0])
				    [2] Book
				      title: "Book_title_3"
				      authors (opposite) (2):
				        [0] -> Author (authors[1])
				        [1] -> Author (authors[2])
				  authors (3):
				    [0] Author
				      name: "Author_name_1"
				      books (opposite) (2):
				        [0] -> Book (books[0])
				        [1] -> Book (books[1])
				    [1] Author
				      name: "Author_name_2"
				      books (opposite) (2):
				        [0] -> Book (books[0])
				        [1] -> Book (books[2])
				    [2] Author
				      name: "Author_name_3"
				      books (opposite) (2):
				        [0] -> Book (books[1])
				        [1] -> Book (books[2])
				""");
		
		validateModel(library);
	}

	@Test
	void shouldPopulateSimpleLibraryMultipleResourcesWithFullTextBlock() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simplelibrary.ecore");
		
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library.xmi");
		
		final var book = createInstance(ePackage, "Book");
		createInstanceInResource(book, "book.xmi");
		
		final var author = createInstance(ePackage, "Author");
		createInstanceInResource(author, "author.xmi");

		populator.getContainmentReferenceSetter().setDefaultMaxCount(1);
		populator.getCrossReferenceSetter().setDefaultMaxCount(1);

		populator.populateEObjects(library);
		populator.populateEObjects(book);
		populator.populateEObjects(author);
		
		final var libraryOutput = printer.prettyPrint(library);
		final var bookOutput = printer.prettyPrint(book);
		final var authorOutput = printer.prettyPrint(author);
		
		// note that although we set multi-valued count to 1, due to cross-references,
		// some opposite references may have more than one value
		// the first book has 2 authors because of the separate author.xmi
		// the first author has 2 books because of the separate book.xmi
		assertThat(libraryOutput).isEqualTo("""
			Library
			  name: "Library_name_1"
			  books (1):
			    [0] Book
			      title: "Book_title_1"
			      authors (opposite) (2):
			        [0] -> Author (authors[0])
			        [1] -> Author (author.xmi)
			  authors (1):
			    [0] Author
			      name: "Author_name_1"
			      books (opposite) (2):
			        [0] -> Book (books[0])
			        [1] -> Book (book.xmi)
			""");
		
		assertThat(bookOutput).isEqualTo("""
			Book
			  title: "Book_title_2"
			  authors (opposite) (1):
			    [0] -> Author (library.xmi > authors[0])
			""");

		assertThat(authorOutput).isEqualTo("""
			Author
			  name: "Author_name_2"
			  books (opposite) (1):
			    [0] -> Book (library.xmi > books[0])
			""");

		validateModel(library);
		validateModel(book);
		validateModel(author);
	}

	@Test
	void shouldPopulateSimpleLibraryWithCrossReferences() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simplelibrary.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library.xmi");
		
		populator.populateEObjects(library);
		
		final var books = EMFUtils.getAsList(library,
				library.eClass().getEStructuralFeature("books"));
		
		assertThat(books).isNotEmpty();
		
		// Check that at least one book has an author reference
		final var book = (EObject) books.get(0);
		final var bookAuthors = book.eGet(book.eClass().getEStructuralFeature("authors"));
		
		// Authors should be a list (many-to-many in simplelibrary)
		assertThat(bookAuthors).isNotNull();
		
		validateModel(library);
	}

	@Test
	void shouldResetNonContainmentReferenceSelectorBetweenPopulations() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simplelibrary.ecore");
		
		final var library1 = createInstance(ePackage, "Library");
		createInstanceInResource(library1, "library_1.xmi");
		final var library2 = createInstance(ePackage, "Library");
		createInstanceInResource(library2, "library_2.xmi");
		
		populator.getCrossReferenceSetter().setDefaultMaxCount(1);
		
		populator.populateEObjects(library1);
		populator.populateEObjects(library2);
		
		// Both should be populated without issues
		validateModel(library1);
		validateModel(library2);
		
		final var books1 = EMFUtils.getAsList(library1,
				library1.eClass().getEStructuralFeature("books"));
		final var books2 = EMFUtils.getAsList(library2,
				library2.eClass().getEStructuralFeature("books"));
		
		// Both should have books
		assertThat(books1).isNotEmpty();
		assertThat(books2).isNotEmpty();
	}

	@Test
	void shouldPopulateSimpleLibraryMultipleResourcesFullTextBlock() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simplelibrary.ecore");
		
		final var library1 = createInstance(ePackage, "Library");
		createInstanceInResource(library1, "lib_1.xmi");
		
		final var library2 = createInstance(ePackage, "Library");
		createInstanceInResource(library2, "lib_2.xmi");
		
		populator.getContainmentReferenceSetter().setDefaultMaxCount(1);
		populator.getCrossReferenceSetter().setDefaultMaxCount(1);
		
		populator.populateEObjects(library1);
		populator.populateEObjects(library2);
		
		// Verify library1 structure
		assertThat(library1.eGet(library1.eClass().getEStructuralFeature("name")))
				.isEqualTo("Library_name_1");
		final var books1 = EMFUtils.getAsList(library1,
				library1.eClass().getEStructuralFeature("books"));
		assertThat(books1).hasSize(1);
		final var authors1 = EMFUtils.getAsList(library1,
				library1.eClass().getEStructuralFeature("authors"));
		assertThat(authors1).hasSize(1);
		
		// Verify library2 structure
		assertThat(library2.eGet(library2.eClass().getEStructuralFeature("name")))
				.isEqualTo("Library_name_2");
		final var books2 = EMFUtils.getAsList(library2,
				library2.eClass().getEStructuralFeature("books"));
		assertThat(books2).hasSize(1);
		final var authors2 = EMFUtils.getAsList(library2,
				library2.eClass().getEStructuralFeature("authors"));
		assertThat(authors2).hasSize(1);
		
		// Verify cross-references between resources
		final var book2 = (EObject) books2.get(0);
		final var bookAuthors2 = EMFUtils.getAsList(book2,
				book2.eClass().getEStructuralFeature("authors"));
		assertThat(bookAuthors2).hasSize(1);
		// The book in library2 should reference an author from library1 due to cross-resource references
		final var author = (EObject) bookAuthors2.get(0);
		assertThat(author.eResource()).isSameAs(library1.eResource());
		
		validateModel(library1);
		validateModel(library2);
	}

	@Test
	void shouldPopulateEObjectWithComplexStructure() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simplelibrary.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library.xmi");
		
		populator.getContainmentReferenceSetter().setDefaultMaxCount(2);
		populator.getCrossReferenceSetter().setDefaultMaxCount(2);
		populator.populateEObjects(library);
		
		// Verify library name
		assertThat(library.eGet(library.eClass().getEStructuralFeature("name")))
				.isEqualTo("Library_name_1");
		
		// Verify books and authors counts
		final var books = EMFUtils.getAsList(library,
				library.eClass().getEStructuralFeature("books"));
		assertThat(books).hasSize(2);
		final var authors = EMFUtils.getAsList(library,
				library.eClass().getEStructuralFeature("authors"));
		assertThat(authors).hasSize(2);
		
		// Verify bidirectional references between books and authors
		final var book1 = (EObject) books.get(0);
		final var book1Authors = EMFUtils.getAsEObjectsList(book1,
				book1.eClass().getEStructuralFeature("authors"));
		assertThat(book1Authors).hasSize(2);
		
		final var author1 = (EObject) authors.get(0);
		final var author1Books = EMFUtils.getAsEObjectsList(author1,
				author1.eClass().getEStructuralFeature("books"));
		assertThat(author1Books).hasSize(2);
		
		// Verify opposite references are consistent
		assertThat(book1Authors).contains(author1);
		assertThat(author1Books).contains(book1);
		
		validateModel(library);
	}

	@Test
	void shouldPopulateGraphNodesWithDifferentOutgoingReferences() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "graph.ecore");
		
		// Part 1: Create 3 nodes and populate each one individually
		final var node1 = createInstance(ePackage, "Node");
		createInstanceInResource(node1, "node1.xmi");
		populator.populateEObjects(node1);
		
		final var node2 = createInstance(ePackage, "Node");
		createInstanceInResource(node2, "node2.xmi");
		populator.populateEObjects(node2);
		
		final var node3 = createInstance(ePackage, "Node");
		createInstanceInResource(node3, "node3.xmi");
		populator.populateEObjects(node3);
		
		// Verify: first node has no outgoing node set
		final var node1Outgoing = EMFUtils.getAsEObjectsList(node1,
				node1.eClass().getEStructuralFeature("outgoing"));
		assertThat(node1Outgoing).isEmpty();
		
		// Verify: second node has the first one as outgoing
		final var node2Outgoing = EMFUtils.getAsEObjectsList(node2,
				node2.eClass().getEStructuralFeature("outgoing"));
		assertThat(node2Outgoing).hasSize(1).contains(node1);
		
		// Verify: third node has the first two nodes as outgoing
		final var node3Outgoing = EMFUtils.getAsEObjectsList(node3,
				node3.eClass().getEStructuralFeature("outgoing"));
		assertThat(node3Outgoing).hasSize(2).contains(node1, node2);
		
		validateModel(node1);
		validateModel(node2);
		validateModel(node3);
		
		// Part 2: Recreate 3 nodes, reset the populator and populate them together
		// First, remove the resources from Part 1 to avoid them being used as candidates
		resourceSet.getResources().remove(node1.eResource());
		resourceSet.getResources().remove(node2.eResource());
		resourceSet.getResources().remove(node3.eResource());
		
		final var newNode1 = createInstance(ePackage, "Node");
		createInstanceInResource(newNode1, "newNode1.xmi");
		
		final var newNode2 = createInstance(ePackage, "Node");
		createInstanceInResource(newNode2, "newNode2.xmi");
		
		final var newNode3 = createInstance(ePackage, "Node");
		createInstanceInResource(newNode3, "newNode3.xmi");
		
		// Reset the populator by calling populateEObjects again (it resets internal state)
		populator.populateEObjects(newNode1, newNode2, newNode3);
		
		// Verify: all nodes have outgoing set to the other two
		final var newNode1Outgoing = EMFUtils.getAsEObjectsList(newNode1,
				newNode1.eClass().getEStructuralFeature("outgoing"));
		assertThat(newNode1Outgoing).hasSize(2).contains(newNode2, newNode3);
		
		final var newNode2Outgoing = EMFUtils.getAsEObjectsList(newNode2,
				newNode2.eClass().getEStructuralFeature("outgoing"));
		assertThat(newNode2Outgoing).hasSize(2).contains(newNode3, newNode1);
		
		final var newNode3Outgoing = EMFUtils.getAsEObjectsList(newNode3,
				newNode3.eClass().getEStructuralFeature("outgoing"));
		assertThat(newNode3Outgoing).hasSize(2).contains(newNode1, newNode2);
		
		validateModel(newNode1);
		validateModel(newNode2);
		validateModel(newNode3);
	}

	@Test
	void shouldAllowCustomAttributeSetter() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simple.ecore");
		final var person = createInstance(ePackage, "Person");
		createInstanceInResource(person, "person_custom.xmi");
		
		// Create a custom attribute setter that uses custom logic for specific attributes
		populator.setAttributeSetter(new EMFAttributeSetter() {
			@Override
			public void setAttribute(EObject instance, org.eclipse.emf.ecore.EAttribute attribute) {
				if ("name".equals(attribute.getName())) {
					// Custom logic: use a special prefix for names
					instance.eSet(attribute, "CUSTOM_" + instance.eClass().getName());
				} else if ("age".equals(attribute.getName())) {
					// Custom logic: set age to 99 instead of default 20
					instance.eSet(attribute, 99);
				} else {
					// Use default behavior for other attributes
					super.setAttribute(instance, attribute);
				}
			}
		});
		
		populator.populateEObjects(person);
		
		// Verify custom values were set
		final var name = person.eGet(person.eClass().getEStructuralFeature("name"));
		final var age = person.eGet(person.eClass().getEStructuralFeature("age"));
		assertThat(name).isEqualTo("CUSTOM_Person");
		assertThat(age).isEqualTo(99);
		
		validateModel(person);
	}

	@Test
	void shouldAllowCustomContainmentReferenceSetter() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "library.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library_custom.xmi");
		
		// Create a custom containment reference setter that creates exactly 3 shelves
		populator.setContainmentReferenceSetter(new EMFContainmentReferenceSetter() {
			@Override
			public Collection<EObject> setContainmentReference(EObject owner, EReference reference) {
				if ("shelves".equals(reference.getName()) && "Library".equals(owner.eClass().getName())) {
					// Custom logic: always create 3 shelves for Library
					final var createdObjects = new java.util.ArrayList<EObject>();
					final var shelvesList = EMFUtils.getAsList(owner, reference);
					
					for (int i = 0; i < 3; i++) {
						final var shelf = org.eclipse.emf.ecore.util.EcoreUtil.create(reference.getEReferenceType());
						shelvesList.add(shelf);
						createdObjects.add(shelf);
					}
					return createdObjects;
				}
				// Use default behavior for other references
				return super.setContainmentReference(owner, reference);
			}
		});
		
		populator.populateEObjects(library);
		
		// Verify custom logic: 3 shelves instead of default 2
		final var shelves = (List<?>) library.eGet(
				library.eClass().getEStructuralFeature("shelves"));
		assertThat(shelves).hasSize(3);
		
		validateModel(library);
	}

	@Test
	void shouldAllowCustomCrossReferenceSetter() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simplelibrary.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "simplelibrary_custom.xmi");
		
		// Create a custom cross reference setter that connects books to the first author only
		populator.setCrossReferenceSetter(new EMFCrossReferenceSetter() {
			@Override
			public void setCrossReference(EObject owner, EReference reference) {
				if ("authors".equals(reference.getName()) && "Book".equals(owner.eClass().getName())) {
					// Custom logic: Books reference only the first author in the library
					final var library = owner.eContainer();
					if (library != null) {
						final var authors = EMFUtils.getAsEObjectsList(library,
								library.eClass().getEStructuralFeature("authors"));
						if (authors != null && !authors.isEmpty()) {
							final var bookAuthors = EMFUtils.getAsList(owner, reference);
							// Only add first author if list is empty (avoid double-add from opposite)
							if (bookAuthors.isEmpty()) {
								bookAuthors.add(authors.get(0));
							}
							return;
						}
					}
				}
				// Skip the opposite reference "books" on Author - it's set automatically
				if ("books".equals(reference.getName()) && "Author".equals(owner.eClass().getName())) {
					return;
				}
				// Use default behavior for other references
				super.setCrossReference(owner, reference);
			}
		});
		
		populator.populateEObjects(library);
		
		// Verify custom logic: all books reference only the first author
		final var books = EMFUtils.getAsEObjectsList(library,
				library.eClass().getEStructuralFeature("books"));
		final var authors = EMFUtils.getAsEObjectsList(library,
				library.eClass().getEStructuralFeature("authors"));
		
		assertThat(books).isNotEmpty();
		assertThat(authors).isNotEmpty();
		
		final var firstAuthor = authors.get(0);
		for (final var book : books) {
			final var bookAuthors = EMFUtils.getAsEObjectsList((EObject) book,
					((EObject) book).eClass().getEStructuralFeature("authors"));
			assertThat(bookAuthors).hasSize(1).containsExactly(firstAuthor);
		}
		
		validateModel(library);
	}

	@Test
	void shouldAllowCustomFunctionForAttribute() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simple.ecore");
		final var personClass = assertEClassExists(ePackage, "Person");
		final var person = createInstance(ePackage, "Person");
		createInstanceInResource(person, "person_function_attribute.xmi");
		
		final var nameAttribute = assertEAttributeExists(personClass, "name");
		final var ageAttribute = assertEAttributeExists(personClass, "age");
		
		// Set custom function for name attribute
		populator.functionForAttribute(nameAttribute,
				owner -> "FUNCTION_" + owner.eClass().getName());
		
		// Set custom function for age attribute
		populator.functionForAttribute(ageAttribute,
				owner -> 42);
		
		populator.populateEObjects(person);
		
		// Verify custom function values were used
		assertThat(person.eGet(nameAttribute)).isEqualTo("FUNCTION_Person");
		assertThat(person.eGet(ageAttribute)).isEqualTo(42);
		
		validateModel(person);
	}

	@Test
	void shouldAllowCustomFunctionForContainmentReference() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "library.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library_function_containment.xmi");
		
		final var shelvesReference = (EReference) library.eClass()
				.getEStructuralFeature("shelves");
		
		// Set custom function for shelves containment reference:
		// if already set, always return the same shelf instance
		populator.functionForContainmentReference(shelvesReference, owner -> {
			var shelves = EMFUtils.getAsEObjectsList(owner, shelvesReference);
			if (!shelves.isEmpty()) {
				return shelves.get(0);
			}
			return EcoreUtil.create(shelvesReference.getEReferenceType());
		});
		
		// Configure to create 3 shelves
		populator.getContainmentReferenceSetter().setDefaultMaxCount(3);
		
		populator.populateEObjects(library);
		
		// Verify just one shelf was created using the custom containment function
		final var shelves = EMFUtils.getAsEObjectsList(library, shelvesReference);
		assertThat(shelves).hasSize(1);
		
		validateModel(library);
	}

	@Test
	void shouldAllowCustomFunctionForCrossReference() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "simplelibrary.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library_function_cross.xmi");
		
		// Set custom function for Book.authors - return them in reverse order
		// which is different from the default round-robin behavior
		final var bookClass = assertEClassExists(ePackage, "Book");
		final var authorsReference = assertEReferenceExists(bookClass, "authors");
		final var counter = new AtomicInteger(0);
		populator.functionForCrossReference(authorsReference, owner -> {
			final var authors = EMFUtils.getAsEObjectsList(library,
					library.eClass().getEStructuralFeature("authors"));
			if (authors.isEmpty()) {
				return null;
			}
			// Return authors in reverse order based on a counter
			final int index = authors.size() - 1 - (counter.getAndIncrement() % authors.size());
			return authors.get(index);
		});
		
		populator.populateEObjects(library);
		
		final var books = EMFUtils.getAsEObjectsList(library,
				library.eClass().getEStructuralFeature("books"));
		final var authors = EMFUtils.getAsEObjectsList(library,
				library.eClass().getEStructuralFeature("authors"));
		
		assertThat(books).isNotEmpty();
		assertThat(authors).isNotEmpty();

		// verify books authors are assigned in reverse order
		for (int i = 0; i < books.size(); i++) {
			final var book = books.get(i);
			final var authorsList = EMFUtils.getAsEObjectsList(book, authorsReference);
			assertThat(authorsList).containsExactlyElementsOf(authors.reversed());
		}

		validateModel(library);
	}

	@Test
	void shouldAllowCombinationOfCustomSetters() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "library.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library_combined_custom.xmi");
		
		// Custom attribute setter: capitalize all names
		populator.setAttributeSetter(new EMFAttributeSetter() {
			@Override
			public void setAttribute(EObject instance, org.eclipse.emf.ecore.EAttribute attribute) {
				if ("name".equals(attribute.getName()) || "title".equals(attribute.getName())) {
					super.setAttribute(instance, attribute);
					// Post-process: capitalize
					final var value = instance.eGet(attribute);
					if (value instanceof String) {
						instance.eSet(attribute, ((String) value).toUpperCase());
					}
				} else {
					super.setAttribute(instance, attribute);
				}
			}
		});
		
		// Custom containment setter: create only 1 shelf but with 3 books
		populator.setContainmentReferenceSetter(new EMFContainmentReferenceSetter() {
			@Override
			public Collection<EObject> setContainmentReference(EObject owner, EReference reference) {
				if ("shelves".equals(reference.getName()) && "Library".equals(owner.eClass().getName())) {
					// Create 1 shelf instead of 2
					final var createdObjects = new java.util.ArrayList<EObject>();
					final var shelvesList = EMFUtils.getAsEObjectsList(owner, reference);
					final var shelf = org.eclipse.emf.ecore.util.EcoreUtil.create(reference.getEReferenceType());
					shelvesList.add(shelf);
					createdObjects.add(shelf);
					return createdObjects;
				} else if ("books".equals(reference.getName()) && "Shelf".equals(owner.eClass().getName())) {
					// Create 3 books instead of 2
					final var createdObjects = new java.util.ArrayList<EObject>();
					final var booksList = EMFUtils.getAsEObjectsList(owner, reference);
					for (int i = 0; i < 3; i++) {
						final var book = org.eclipse.emf.ecore.util.EcoreUtil.create(reference.getEReferenceType());
						booksList.add(book);
						createdObjects.add(book);
					}
					return createdObjects;
				}
				return super.setContainmentReference(owner, reference);
			}
		});
		
		// Custom cross reference setter: director is always the shelf's book's author
		populator.setCrossReferenceSetter(new EMFCrossReferenceSetter() {
			@Override
			public void setCrossReference(EObject owner, EReference reference) {
				if ("director".equals(reference.getName()) && "Library".equals(owner.eClass().getName())) {
					// Set director to be the first shelf's first book's author
					final var shelves = EMFUtils.getAsEObjectsList(owner,
							owner.eClass().getEStructuralFeature("shelves"));
					if (shelves != null && !shelves.isEmpty()) {
						final var shelf = shelves.get(0);
						final var books = EMFUtils.getAsEObjectsList(shelf,
								shelf.eClass().getEStructuralFeature("books"));
						if (books != null && !books.isEmpty()) {
							final var book = books.get(0);
							final var author = book.eGet(book.eClass().getEStructuralFeature("author"));
							if (author != null) {
								owner.eSet(reference, author);
								return;
							}
						}
					}
				}
				super.setCrossReference(owner, reference);
			}
		});
		
		populator.populateEObjects(library);
		
		// Verify custom attribute setter: names are uppercase
		final var libraryName = (String) library.eGet(
				library.eClass().getEStructuralFeature("name"));
		assertThat(libraryName).isEqualTo("LIBRARY_NAME_1");
		
		// Verify custom containment setter: 1 shelf with 3 books
		final var shelves = EMFUtils.getAsEObjectsList(library,
				library.eClass().getEStructuralFeature("shelves"));
		assertThat(shelves).hasSize(1);
		
		final var shelf = shelves.get(0);
		final var books = EMFUtils.getAsEObjectsList(shelf,
				shelf.eClass().getEStructuralFeature("books"));
		assertThat(books).hasSize(3);
		
		// Verify all book titles are uppercase
		for (final var book : books) {
			final var title = (String) ((EObject) book).eGet(
					((EObject) book).eClass().getEStructuralFeature("title"));
			assertThat(title).matches("[A-Z_0-9]+");
		}
		
		// Verify custom cross reference setter: director is the first book's author
		final var director = (EObject) library.eGet(
				library.eClass().getEStructuralFeature("director"));
		final var firstBook = (EObject) books.get(0);
		final var firstBookAuthor = firstBook.eGet(
				firstBook.eClass().getEStructuralFeature("author"));
		assertThat(director).isSameAs(firstBookAuthor);
		
		validateModel(library);
	}

	// ============= Tests for filtering invalid features =============

	@Test
	void shouldFilterOutInvalidAttribute() {
		// Create a test class with a non-changeable attribute
		final var ecoreFactory = EcoreFactory.eINSTANCE;
		final var testPackage = ecoreFactory.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		final var testClass = ecoreFactory.createEClass();
		testClass.setName("TestClass");
		testPackage.getEClassifiers().add(testClass);
		
		// Valid attribute
		final var validAttr = ecoreFactory.createEAttribute();
		validAttr.setName("validAttr");
		validAttr.setEType(EcorePackage.Literals.ESTRING);
		testClass.getEStructuralFeatures().add(validAttr);
		
		// Invalid attribute (non-changeable)
		final var invalidAttr = ecoreFactory.createEAttribute();
		invalidAttr.setName("invalidAttr");
		invalidAttr.setEType(EcorePackage.Literals.ESTRING);
		invalidAttr.setChangeable(false);
		testClass.getEStructuralFeatures().add(invalidAttr);
		
		final var instance = EcoreUtil.create(testClass);
		createInstanceInResource(instance, "test_nonchangeable.xmi");
		
		populator.populateEObjects(instance);
		
		// Valid attribute should be set
		assertThat(instance.eGet(validAttr)).isNotNull();
		
		// Invalid attribute should NOT be set (filtered by populator)
		assertThat(instance.eGet(invalidAttr)).isNull();
		
		validateModel(instance);
	}

	@Test
	void shouldFilterOutInvalidContainmentReference() {
		// Create a test class with a non-changeable containment reference
		final var ecoreFactory = EcoreFactory.eINSTANCE;
		final var testPackage = ecoreFactory.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		final var parentClass = ecoreFactory.createEClass();
		parentClass.setName("Parent");
		testPackage.getEClassifiers().add(parentClass);
		
		final var childClass = ecoreFactory.createEClass();
		childClass.setName("Child");
		testPackage.getEClassifiers().add(childClass);
		
		// Valid containment reference
		final var validRef = ecoreFactory.createEReference();
		validRef.setName("validChildren");
		validRef.setEType(childClass);
		validRef.setContainment(true);
		validRef.setUpperBound(-1);
		parentClass.getEStructuralFeatures().add(validRef);
		
		// Invalid containment reference (non-changeable)
		final var invalidRef = ecoreFactory.createEReference();
		invalidRef.setName("invalidChildren");
		invalidRef.setEType(childClass);
		invalidRef.setContainment(true);
		invalidRef.setUpperBound(-1);
		invalidRef.setChangeable(false);
		parentClass.getEStructuralFeatures().add(invalidRef);
		
		final var parent = EcoreUtil.create(parentClass);
		createInstanceInResource(parent, "test_containment_nonchangeable.xmi");
		
		populator.populateEObjects(parent);
		
		// Valid reference should have children
		final var validChildren = EMFUtils.getAsEObjectsList(parent, validRef);
		assertThat(validChildren).isNotEmpty();
		
		// Invalid reference should NOT have children (filtered by populator)
		final var invalidChildren = EMFUtils.getAsEObjectsList(parent, invalidRef);
		assertThat(invalidChildren).isEmpty();
		
		validateModel(parent);
	}

	@Test
	void shouldFilterOutInvalidCrossReference() {
		// Create test classes with a non-changeable cross reference
		final var ecoreFactory = EcoreFactory.eINSTANCE;
		final var testPackage = ecoreFactory.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		final var ownerClass = ecoreFactory.createEClass();
		ownerClass.setName("Owner");
		testPackage.getEClassifiers().add(ownerClass);
		
		final var targetClass = ecoreFactory.createEClass();
		targetClass.setName("Target");
		testPackage.getEClassifiers().add(targetClass);
		
		// Valid cross reference
		final var validRef = ecoreFactory.createEReference();
		validRef.setName("validTargets");
		validRef.setEType(targetClass);
		validRef.setContainment(false);
		validRef.setUpperBound(-1);
		ownerClass.getEStructuralFeatures().add(validRef);
		
		// Invalid cross reference (non-changeable)
		final var invalidRef = ecoreFactory.createEReference();
		invalidRef.setName("invalidTargets");
		invalidRef.setEType(targetClass);
		invalidRef.setContainment(false);
		invalidRef.setUpperBound(-1);
		invalidRef.setChangeable(false);
		ownerClass.getEStructuralFeatures().add(invalidRef);
		
		// Create owner and target instances
		final var owner = EcoreUtil.create(ownerClass);
		createInstanceInResource(owner, "test_cross_nonchangeable.xmi");

		final var target = EcoreUtil.create(targetClass);
		createInstanceInResource(target, "test_target.xmi");
		
		populator.populateEObjects(owner);
		
		// Valid reference should have targets
		final var validTargets = EMFUtils.getAsEObjectsList(owner, validRef);
		assertThat(validTargets).isNotEmpty();
		
		// Invalid reference should NOT have targets (filtered by populator)
		final var invalidTargets = EMFUtils.getAsEObjectsList(owner, invalidRef);
		assertThat(invalidTargets).isEmpty();
		
		validateModel(owner);
		validateModel(target);
	}

	@Test
	void shouldReturnCreatedObjectsAtMaxDepthForCrossReferences() {
		// This test documents the behavior when max depth is reached.
		// At max depth, containment references are not populated (handled by
		// the if-block in populateEObject), so the returned collection is empty.
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "library.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library_maxdepth.xmi");
		
		// Set max depth to 1 so that:
		// - depth 0: library populates shelves
		// - depth 1: shelves get attributes only (no containments due to max depth)
		populator.setMaxDepth(1);
		populator.populateEObjects(library);
		
		// Verify shelves were populated (at depth 0)
		final var shelves = EMFUtils.getAsEObjectsList(library,
				library.eClass().getEStructuralFeature("shelves"));
		assertThat(shelves).isNotEmpty();
		
		// Books should be empty since we're at depth 1 and books would be depth 2
		final var shelf = shelves.get(0);
		final var books = EMFUtils.getAsEObjectsList(shelf, shelf.eClass().getEStructuralFeature("books"));
		assertThat(books).isEmpty();
		
		// Shelves should still have their attributes populated
		assertThat(shelf.eGet(shelf.eClass().getEStructuralFeature("name")))
				.isNotNull();
		
		// The director cross-reference on library should still work
		// since director references Person which was created as a containment
		final var director = library.eGet(library.eClass().getEStructuralFeature("director"));
		assertThat(director).isNotNull();
		
		validateModel(library);
	}

	@Test
	void testSetInstantiableSubclassSelectorStrategy() {
		// Create an abstract base class with two concrete subclasses
		final var testPackage = EcoreFactory.eINSTANCE.createEPackage();
		testPackage.setName("test");
		testPackage.setNsURI("http://test");
		testPackage.setNsPrefix("test");
		
		final var abstractBase = EcoreFactory.eINSTANCE.createEClass();
		abstractBase.setName("AbstractBase");
		abstractBase.setAbstract(true);
		testPackage.getEClassifiers().add(abstractBase);
		
		final var subClass1 = EcoreFactory.eINSTANCE.createEClass();
		subClass1.setName("SubClass1");
		subClass1.getESuperTypes().add(abstractBase);
		testPackage.getEClassifiers().add(subClass1);
		
		final var subClass2 = EcoreFactory.eINSTANCE.createEClass();
		subClass2.setName("SubClass2");
		subClass2.getESuperTypes().add(abstractBase);
		testPackage.getEClassifiers().add(subClass2);
		
		final var container = EcoreFactory.eINSTANCE.createEClass();
		container.setName("Container");
		testPackage.getEClassifiers().add(container);
		
		final var children = EcoreFactory.eINSTANCE.createEReference();
		children.setName("children");
		children.setEType(abstractBase);
		children.setContainment(true);
		children.setUpperBound(-1);
		container.getEStructuralFeatures().add(children);
		
		// Create a custom strategy that always returns SubClass2
		final var customStrategy = new EMFCandidateSelectorStrategy<EClass, EClass>() {
			@Override
			public EClass getNextCandidate(EObject context, EClass type) {
				return subClass2;
			}

			@Override
			public boolean hasCandidates(EObject context, EClass type) {
				return true;
			}
		};
		
		populator.setInstantiableSubclassSelectorStrategy(customStrategy);
		
		final var instance = EcoreUtil.create(container);
		createInstanceInResource(instance, "test_strategy.xmi");
		populator.populateEObjects(instance);
		
		final var childrenList = EMFUtils.getAsEObjectsList(instance, children);
		assertThat(childrenList).hasSize(2)
			.allMatch(child -> child.eClass().equals(subClass2));
	}

	@Test
	void testSetCrossReferenceCandidateSelectorStrategy() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "container-search.ecore");
		final var linkClass = assertEClassExists(ePackage, "Link");
		final var entryClass = assertEClassExists(ePackage, "Entry");
		
		final var entry1 = EcoreUtil.create(entryClass);
		entry1.eSet(entryClass.getEStructuralFeature("key"), "entry1");
		final var entry2 = EcoreUtil.create(entryClass);
		entry2.eSet(entryClass.getEStructuralFeature("key"), "entry2");
		
		// Create a custom strategy that always returns entry2
		final var customStrategy = new EMFCandidateSelectorStrategy<EClass, EObject>() {
			@Override
			public EObject getNextCandidate(EObject context, EClass type) {
				return entry2;
			}

			@Override
			public boolean hasCandidates(EObject context, EClass type) {
				return true;
			}
		};
		
		populator.setCrossReferenceCandidateSelectorStrategy(customStrategy);
		
		final var registry = createInstance(ePackage, "Registry");
		createInstanceInResource(registry, "test_registry.xmi");
		registry.eResource().getContents().add(entry1);
		registry.eResource().getContents().add(entry2);
		
		final var link = createInstance(ePackage, "Link");
		createInstanceInResource(link, "test_link.xmi");
		
		populator.populateEObjects(link);
		
		final var targets = EMFUtils.getAsEObjectsList(link, linkClass.getEStructuralFeature("targets"));
		// Only one reference can be set since the strategy always returns the same object
		// and multi-valued references don't allow duplicates
		assertThat(targets).hasSize(1)
			.allMatch(target -> target.equals(entry2));
	}

	@Test
	void testSetEnumLiteralSelectorStrategy() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "taskmanager.ecore");
		final var taskClass = assertEClassExists(ePackage, "Task");
		final var priorityEnum = (EEnum) ePackage.getEClassifier("Priority");
		final var statusEnum = (EEnum) ePackage.getEClassifier("Status");
		final var highLiteral = priorityEnum.getEEnumLiteral("HIGH");
		final var completedLiteral = statusEnum.getEEnumLiteral("COMPLETED");
		
		// Create a custom strategy that returns specific enum literals
		final var customStrategy = new EMFCandidateSelectorStrategy<EEnum, EEnumLiteral>() {
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
		
		populator.setEnumLiteralSelectorStrategy(customStrategy);
		
		final var task = createInstance(ePackage, "Task");
		createInstanceInResource(task, "test_task.xmi");
		populator.populateEObjects(task);
		
		final var priorityValue = task.eGet(taskClass.getEStructuralFeature("priority"));
		assertThat(priorityValue).isEqualTo(highLiteral.getInstance());
		
		final var statusValue = task.eGet(taskClass.getEStructuralFeature("status"));
		assertThat(statusValue).isEqualTo(completedLiteral.getInstance());
		
		final var tags = EMFUtils.getAsList(task, taskClass.getEStructuralFeature("tags"));
		// Only one value can be set since the strategy always returns the same object
		// and multi-valued enum attributes don't allow duplicates by default
		assertThat(tags).hasSize(1)
			.allMatch(tag -> tag.equals(highLiteral.getInstance()));
	}

	@Test
	void testGetFeatureMapSetter() {
		assertThat(populator.getFeatureMapSetter()).isNotNull();
	}

	@Test
	void testSetFeatureMapSetter() {
		final var customSetter = new EMFFeatureMapSetter();
		populator.setFeatureMapSetter(customSetter);
		assertThat(populator.getFeatureMapSetter()).isSameAs(customSetter);
	}

	@Test
	void testSetGroupMemberSelectorStrategy() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore");
		final var libraryClass = assertEClassExists(ePackage, "Library");
		final var peopleAttr = assertEAttributeExists(libraryClass, "people");
		final var writersRef = assertEReferenceExists(libraryClass, "writers");
		final var employeesRef = assertEReferenceExists(libraryClass, "employees");
		final var borrowersRef = assertEReferenceExists(libraryClass, "borrowers");
		
		// Create a custom strategy that always returns the writers reference
		final var customStrategy = new EMFCandidateSelectorStrategy<EAttribute, EReference>() {
			@Override
			public EReference getNextCandidate(EObject context, EAttribute type) {
				return writersRef;
			}

			@Override
			public boolean hasCandidates(EObject context, EAttribute type) {
				return true;
			}
		};
		
		populator.setGroupMemberSelectorStrategy(customStrategy);
		populator.getFeatureMapSetter().setMaxCountFor(peopleAttr, 3);
		
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "test_library_strategy.xmi");
		populator.populateEObjects(library);
		
		final var writers = EMFUtils.getAsEObjectsList(library, writersRef);
		final var employees = EMFUtils.getAsEObjectsList(library, employeesRef);
		final var borrowers = EMFUtils.getAsEObjectsList(library, borrowersRef);
		
		// All 3 items should be writers since the custom strategy always returns writers
		assertThat(writers).hasSize(3);
		assertThat(employees).isEmpty();
		assertThat(borrowers).isEmpty();
	}

	@Test
	void shouldPopulateFeatureMaps() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore");
		final var libraryClass = assertEClassExists(ePackage, "Library");
		final var writersRef = assertEReferenceExists(libraryClass, "writers");
		final var employeesRef = assertEReferenceExists(libraryClass, "employees");
		final var borrowersRef = assertEReferenceExists(libraryClass, "borrowers");
		
		populator.getFeatureMapSetter().setDefaultMaxCount(6);
		
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "test_library_featuremap.xmi");
		populator.populateEObjects(library);
		
		final var writers = EMFUtils.getAsEObjectsList(library, writersRef);
		final var employees = EMFUtils.getAsEObjectsList(library, employeesRef);
		final var borrowers = EMFUtils.getAsEObjectsList(library, borrowersRef);
		
		// With 6 items and 3 group members (round-robin), we should get 2 of each
		assertThat(writers).hasSize(2);
		assertThat(employees).hasSize(2);
		assertThat(borrowers).hasSize(2);
		
		// Verify the instances are properly populated with attributes
		// All inherit from Person which has firstName
		assertThat((String) writers.get(0).eGet(writers.get(0).eClass().getEStructuralFeature("firstName")))
			.isNotEmpty();
		assertThat((String) employees.get(0).eGet(employees.get(0).eClass().getEStructuralFeature("firstName")))
			.isNotEmpty();
		assertThat((String) borrowers.get(0).eGet(borrowers.get(0).eClass().getEStructuralFeature("firstName")))
			.isNotEmpty();
		
		validateModel(library);
	}

	@Test
	void shouldPopulateFeatureMapsWithCustomMaxCount() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore");
		final var libraryClass = assertEClassExists(ePackage, "Library");
		final var peopleAttr = assertEAttributeExists(libraryClass, "people");
		final var writersRef = assertEReferenceExists(libraryClass, "writers");
		
		populator.getFeatureMapSetter().setMaxCountFor(peopleAttr, 1);
		
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "test_library_maxcount.xmi");
		populator.populateEObjects(library);
		
		final var writers = EMFUtils.getAsEObjectsList(library, writersRef);
		
		// Only 1 item due to custom max count
		assertThat(writers).hasSize(1);
		
		validateModel(library);
	}

	@Test
	void shouldUseCustomFeatureMapSetter() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore");
		final var libraryClass = assertEClassExists(ePackage, "Library");
		final var writersRef = assertEReferenceExists(libraryClass, "writers");
		final var employeesRef = assertEReferenceExists(libraryClass, "employees");
		
		final var counter = new AtomicInteger(0);
		
		populator.setFeatureMapSetter(new EMFFeatureMapSetter() {
			@Override
			public Collection<EObject> setFeatureMap(EObject owner, EAttribute featureMapAttribute) {
				counter.incrementAndGet();
				return super.setFeatureMap(owner, featureMapAttribute);
			}
		});
		
		// Set maxDepth to 1 to limit recursive population but still populate feature maps
		populator.setMaxDepth(1);
		
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "test_custom_setter.xmi");
		populator.populateEObjects(library);
		
		// Verify custom setter was called (at least once for the library's people feature map)
		assertThat(counter.get()).isPositive();
		
		// Verify feature map was still populated
		final var writers = EMFUtils.getAsEObjectsList(library, writersRef);
		final var employees = EMFUtils.getAsEObjectsList(library, employeesRef);
		assertThat(writers).isNotEmpty();
		assertThat(employees).isNotEmpty();
	}

	@Test
	void testFunctionForFeatureMapGroupMember() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore");
		final var libraryClass = assertEClassExists(ePackage, "Library");
		final var writerClass = assertEClassExists(ePackage, "Writer");
		final var peopleAttr = assertEAttributeExists(libraryClass, "people");
		final var writersRef = assertEReferenceExists(libraryClass, "writers");
		final var employeesRef = assertEReferenceExists(libraryClass, "employees");
		
		final var counter = new AtomicInteger(0);
		
		// Set a custom function for the writers group member
		populator.functionForFeatureMapGroupMember(writersRef, owner -> {
			counter.incrementAndGet();
			final var writer = EcoreUtil.create(writerClass);
			writer.eSet(writerClass.getEStructuralFeature("firstName"), "CustomWriter");
			return writer;
		});
		
		populator.getFeatureMapSetter().setMaxCountFor(peopleAttr, 3);
		
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "test_custom_function.xmi");
		populator.populateEObjects(library);
		
		final var writers = EMFUtils.getAsEObjectsList(library, writersRef);
		final var employees = EMFUtils.getAsEObjectsList(library, employeesRef);
		
		// Verify custom function was called for writers
		assertThat(counter.get()).isPositive();
		
		// Verify writers have custom firstName
		assertThat(writers).isNotEmpty()
			.allMatch(writer -> "CustomWriter".equals(writer.eGet(writerClass.getEStructuralFeature("firstName"))));
		
		// Verify employees were created normally (not using custom function)
		assertThat(employees).isNotEmpty()
			.noneMatch(employee -> "CustomWriter".equals(employee.eGet(employee.eClass().getEStructuralFeature("firstName"))));
		
		validateModel(library);
	}

	// ============= Tests for setXXXDefaultMaxCount and setXXXMaxCountFor methods =============

	@Test
	void testSetAttributeDefaultMaxCount() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "multivalued.ecore");
		final var containerClass = assertEClassExists(ePackage, "Container");
		
		final var container = createInstance(ePackage, "Container");
		createInstanceInResource(container, "container_attr_default.xmi");
		
		populator.setAttributeDefaultMaxCount(5);
		populator.populateEObjects(container);
		
		final var tags = EMFUtils.getAsList(container, containerClass.getEStructuralFeature("tags"));
		assertThat(tags).hasSize(5);
		
		validateModel(container);
	}

	@Test
	void testSetAttributeMaxCountFor() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "multivalued.ecore");
		final var containerClass = assertEClassExists(ePackage, "Container");
		final var tagsAttribute = assertEAttributeExists(containerClass, "tags");
		
		final var container = createInstance(ePackage, "Container");
		createInstanceInResource(container, "container_attr_specific.xmi");
		
		populator.setAttributeMaxCountFor(tagsAttribute, 3);
		populator.populateEObjects(container);
		
		final var tags = EMFUtils.getAsList(container, tagsAttribute);
		assertThat(tags).hasSize(3);
		
		validateModel(container);
	}

	@Test
	void testSetCrossReferenceDefaultMaxCount() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "graph.ecore");
		
		final var node1 = createInstance(ePackage, "Node");
		createInstanceInResource(node1, "cross_ref_default_1.xmi");
		
		final var node2 = createInstance(ePackage, "Node");
		createInstanceInResource(node2, "cross_ref_default_2.xmi");
		
		final var node3 = createInstance(ePackage, "Node");
		createInstanceInResource(node3, "cross_ref_default_3.xmi");
		
		populator.setCrossReferenceDefaultMaxCount(1);
		populator.populateEObjects(node1, node2, node3);
		
		final var node3Outgoing = EMFUtils.getAsEObjectsList(node3,
				node3.eClass().getEStructuralFeature("outgoing"));
		assertThat(node3Outgoing).hasSize(1);
		
		validateModel(node1);
		validateModel(node2);
		validateModel(node3);
	}

	@Test
	void testSetCrossReferenceMaxCountFor() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "graph.ecore");
		final var nodeClass = assertEClassExists(ePackage, "Node");
		final var outgoingRef = assertEReferenceExists(nodeClass, "outgoing");
		
		final var node1 = createInstance(ePackage, "Node");
		createInstanceInResource(node1, "cross_ref_specific_1.xmi");
		
		final var node2 = createInstance(ePackage, "Node");
		createInstanceInResource(node2, "cross_ref_specific_2.xmi");
		
		final var node3 = createInstance(ePackage, "Node");
		createInstanceInResource(node3, "cross_ref_specific_3.xmi");
		
		populator.setCrossReferenceMaxCountFor(outgoingRef, 1);
		populator.populateEObjects(node1, node2, node3);
		
		final var node3Outgoing = EMFUtils.getAsEObjectsList(node3, outgoingRef);
		assertThat(node3Outgoing).hasSize(1);
		
		validateModel(node1);
		validateModel(node2);
		validateModel(node3);
	}

	@Test
	void testSetContainmentReferenceDefaultMaxCount() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "library.ecore");
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library_containment_default.xmi");
		
		populator.setContainmentReferenceDefaultMaxCount(4);
		populator.populateEObjects(library);
		
		final var shelves = EMFUtils.getAsList(library,
				library.eClass().getEStructuralFeature("shelves"));
		assertThat(shelves).hasSize(4);
		
		validateModel(library);
	}

	@Test
	void testSetContainmentReferenceMaxCountFor() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "library.ecore");
		final var libraryClass = assertEClassExists(ePackage, "Library");
		final var shelvesRef = assertEReferenceExists(libraryClass, "shelves");
		
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "library_containment_specific.xmi");
		
		populator.setContainmentReferenceMaxCountFor(shelvesRef, 3);
		populator.populateEObjects(library);
		
		final var shelves = EMFUtils.getAsList(library, shelvesRef);
		assertThat(shelves).hasSize(3);
		
		validateModel(library);
	}

	@Test
	void testSetFeatureMapDefaultMaxCount() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore");
		final var libraryClass = assertEClassExists(ePackage, "Library");
		final var writersRef = assertEReferenceExists(libraryClass, "writers");
		final var employeesRef = assertEReferenceExists(libraryClass, "employees");
		final var borrowersRef = assertEReferenceExists(libraryClass, "borrowers");
		
		populator.setFeatureMapDefaultMaxCount(9);
		
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "test_featuremap_default.xmi");
		populator.populateEObjects(library);
		
		final var writers = EMFUtils.getAsEObjectsList(library, writersRef);
		final var employees = EMFUtils.getAsEObjectsList(library, employeesRef);
		final var borrowers = EMFUtils.getAsEObjectsList(library, borrowersRef);
		
		// With 9 items and 3 group members (round-robin), we should get 3 of each
		assertThat(writers).hasSize(3);
		assertThat(employees).hasSize(3);
		assertThat(borrowers).hasSize(3);
		
		validateModel(library);
	}

	@Test
	void testSetFeatureMapMaxCountFor() {
		final var ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore");
		final var libraryClass = assertEClassExists(ePackage, "Library");
		final var peopleAttr = assertEAttributeExists(libraryClass, "people");
		final var writersRef = assertEReferenceExists(libraryClass, "writers");
		
		populator.setFeatureMapMaxCountFor(peopleAttr, 4);
		
		final var library = createInstance(ePackage, "Library");
		createInstanceInResource(library, "test_featuremap_specific.xmi");
		populator.populateEObjects(library);
		
		final var writers = EMFUtils.getAsEObjectsList(library, writersRef);
		
		// With round-robin and 3 group members, 4 items means some will get more than others
		// But the total across all groups should be at most 4
		final var employees = EMFUtils.getAsEObjectsList(library,
				libraryClass.getEStructuralFeature("employees"));
		final var borrowers = EMFUtils.getAsEObjectsList(library,
				libraryClass.getEStructuralFeature("borrowers"));
		
		final int total = writers.size() + employees.size() + borrowers.size();
		assertThat(total).isEqualTo(4);
		
		validateModel(library);
	}

	@Test
	void testSetAllowCyclePolicy() {
		// Create a package and an EClass with a self-reference
		final var testPkg = EcoreFactory.eINSTANCE.createEPackage();
		testPkg.setName("testpkg");
		testPkg.setNsURI("http://test");
		testPkg.setNsPrefix("test");
		
		final var nodeClass = EcoreFactory.eINSTANCE.createEClass();
		nodeClass.setName("Node");
		testPkg.getEClassifiers().add(nodeClass);
		
		final var selfRef = EcoreFactory.eINSTANCE.createEReference();
		selfRef.setName("self");
		selfRef.setEType(nodeClass);
		nodeClass.getEStructuralFeatures().add(selfRef);
		
		// Create node and set custom policy that allows cycles
		populator.setAllowCyclePolicy((owner, ref) -> true);
		
		final var node = EcoreUtil.create(nodeClass);
		createInstanceInResource(node, "test_cycle_policy.xmi");
		populator.populateEObjects(node);
		
		// Verify the policy was applied - node should reference itself
		final var referencedObject = node.eGet(selfRef);
		assertThat(referencedObject).isSameAs(node);
	}

}
