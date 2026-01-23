package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createInstance;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createResourceSet;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.loadEPackage;
import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EMFPrettyPrinterTest {

	private EMFPrettyPrinter printer;

	@BeforeEach
	void setUp() {
		printer = new EMFPrettyPrinter();
		// Register Ecore resource factory for loading .ecore files
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
			.put("ecore", new EcoreResourceFactoryImpl());
		// Register XMI resource factory for creating test resources
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
			.put("xmi", new org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl());
	}

	@Test
	void testPrettyPrintSimpleEObject() {
		final var ePackage = loadEPackage("inputs/simple.ecore");
		final var person = createInstance(ePackage, "Person");
		person.eSet(person.eClass().getEStructuralFeature("name"), "John");

		final var result = printer.prettyPrint(person);

		assertThat(result).isEqualTo("""
			Person
			  name: "John"
			""");
	}

	@Test
	void testPrettyPrintEObjectWithResourceName() {
		final ResourceSet resourceSet = createResourceSet();
		final Resource resource = resourceSet.createResource(URI.createFileURI("test_Person_1.xmi"));
		
		final var ePackage = loadEPackage("inputs/simple.ecore");
		final var person = createInstance(ePackage, "Person");
		person.eSet(person.eClass().getEStructuralFeature("name"), "John");
		resource.getContents().add(person);

		final var result = printer.prettyPrint(person);

		assertThat(result).isEqualTo("""
			Person
			  name: "John"
			""");
	}

	@Test
	void testPrettyPrintEObjectWithUnsetFeatures() {
		final var ePackage = loadEPackage("inputs/simple.ecore");
		final var person = createInstance(ePackage, "Person");
		// Don't set name - should not appear in output

		final var result = printer.prettyPrint(person);

		assertThat(result).isEqualTo("""
			Person
			""");
	}

	@Test
	void testPrettyPrintEObjectWithMultipleAttributes() {
		final var ePackage = loadEPackage("inputs/alltypes.ecore");
		final var allTypes = createInstance(ePackage, "AllTypesHolder");
		allTypes.eSet(allTypes.eClass().getEStructuralFeature("stringAttr"), "test");
		allTypes.eSet(allTypes.eClass().getEStructuralFeature("intAttr"), 42);
		allTypes.eSet(allTypes.eClass().getEStructuralFeature("boolAttr"), true);

		final var result = printer.prettyPrint(allTypes);

		assertThat(result).isEqualTo("""
			AllTypesHolder
			  stringAttr: "test"
			  intAttr: 42
			  boolAttr: true
			""");
	}	@Test
	void testPrettyPrintEObjectWithSingleContainment() {
		final var ePackage = loadEPackage("inputs/library.ecore");
		final var library = createInstance(ePackage, "Library");
		library.eSet(library.eClass().getEStructuralFeature("name"), "City Library");
		
		final var shelf = createInstance(ePackage, "Shelf");
		shelf.eSet(shelf.eClass().getEStructuralFeature("name"), "Fiction");
		library.eSet(library.eClass().getEStructuralFeature("shelves"), java.util.List.of(shelf));

		final var result = printer.prettyPrint(library);

		assertThat(result).isEqualTo("""
			Library
			  name: "City Library"
			  shelves (1):
			    [0] Shelf
			      name: "Fiction"
			""");
	}

	@Test
	void testPrettyPrintEObjectWithMultipleContainments() {
		final var ePackage = loadEPackage("inputs/library.ecore");
		final var library = createInstance(ePackage, "Library");
		library.eSet(library.eClass().getEStructuralFeature("name"), "City Library");
		
		final var shelf1 = createInstance(ePackage, "Shelf");
		shelf1.eSet(shelf1.eClass().getEStructuralFeature("name"), "Fiction");
		
		final var shelf2 = createInstance(ePackage, "Shelf");
		shelf2.eSet(shelf2.eClass().getEStructuralFeature("name"), "Science");
		
		library.eSet(library.eClass().getEStructuralFeature("shelves"), java.util.List.of(shelf1, shelf2));

		final var result = printer.prettyPrint(library);

		assertThat(result).isEqualTo("""
			Library
			  name: "City Library"
			  shelves (2):
			    [0] Shelf
			      name: "Fiction"
			    [1] Shelf
			      name: "Science"
			""");
	}

	@Test
	void testPrettyPrintEObjectWithNestedContainments() {
		final var ePackage = loadEPackage("inputs/library.ecore");
		final var library = createInstance(ePackage, "Library");
		library.eSet(library.eClass().getEStructuralFeature("name"), "City Library");
		
		final var shelf = createInstance(ePackage, "Shelf");
		shelf.eSet(shelf.eClass().getEStructuralFeature("name"), "Fiction");
		
		final var book = createInstance(ePackage, "Book");
		book.eSet(book.eClass().getEStructuralFeature("title"), "1984");
		
		shelf.eSet(shelf.eClass().getEStructuralFeature("books"), java.util.List.of(book));
		library.eSet(library.eClass().getEStructuralFeature("shelves"), java.util.List.of(shelf));

		final var result = printer.prettyPrint(library);

		assertThat(result).isEqualTo("""
			Library
			  name: "City Library"
			  shelves (1):
			    [0] Shelf
			      name: "Fiction"
			      books (1):
			        [0] Book
			          title: "1984"
			""");
	}

	@Test
	void testPrettyPrintEObjectWithCrossReference() {
		final var resourceSet = createResourceSet();
		final var libraryResource = resourceSet.createResource(URI.createFileURI("test_Library_1.xmi"));
		final var authorResource = resourceSet.createResource(URI.createFileURI("test_Author_1.xmi"));
		
		final var ePackage = loadEPackage("inputs/simplelibrary.ecore");
		final var library = createInstance(ePackage, "Library");
		library.eSet(library.eClass().getEStructuralFeature("name"), "City Library");
		
		final var book = createInstance(ePackage, "Book");
		book.eSet(book.eClass().getEStructuralFeature("title"), "1984");
		
		final var author = createInstance(ePackage, "Author");
		author.eSet(author.eClass().getEStructuralFeature("name"), "Orwell");
		
		authorResource.getContents().add(author);
		library.eSet(library.eClass().getEStructuralFeature("books"), java.util.List.of(book));
		libraryResource.getContents().add(library);
		
		// Cross-reference from book (in library resource) to author (in different resource)
		book.eSet(book.eClass().getEStructuralFeature("authors"), java.util.List.of(author));

		final var result = printer.prettyPrint(library);

		assertThat(result).isEqualTo("""
			Library
			  name: "City Library"
			  books (1):
			    [0] Book
			      title: "1984"
			      authors (opposite) (1):
			        [0] -> Author (test_Author_1.xmi)
			""");
	}

	@Test
	void testPrettyPrintEObjectWithCrossReferenceToContainedObject() {
		final var resourceSet = createResourceSet();
		final var libraryResource = resourceSet.createResource(URI.createFileURI("test_Library_1.xmi"));
		
		final var ePackage = loadEPackage("inputs/library.ecore");
		final var library = createInstance(ePackage, "Library");
		library.eSet(library.eClass().getEStructuralFeature("name"), "City Library");
		
		final var shelf = createInstance(ePackage, "Shelf");
		shelf.eSet(shelf.eClass().getEStructuralFeature("name"), "Fiction");
		
		final var book = createInstance(ePackage, "Book");
		book.eSet(book.eClass().getEStructuralFeature("title"), "1984");
		
		final var person = createInstance(ePackage, "Person");
		person.eSet(person.eClass().getEStructuralFeature("name"), "Orwell");
		
		shelf.eSet(shelf.eClass().getEStructuralFeature("books"), java.util.List.of(book));
		library.eSet(library.eClass().getEStructuralFeature("shelves"), java.util.List.of(shelf));
		libraryResource.getContents().add(library);
		
		book.eSet(book.eClass().getEStructuralFeature("author"), person);
		library.eSet(library.eClass().getEStructuralFeature("director"), person);

		final var result = printer.prettyPrint(library);

		assertThat(result).isEqualTo("""
			Library
			  name: "City Library"
			  shelves (1):
			    [0] Shelf
			      name: "Fiction"
			      books (1):
			        [0] Book
			          title: "1984"
			          author -> Person (director)
			  director:
			    Person
			      name: "Orwell"
			""");
	}

	@Test
	void testPrettyPrintEObjectWithMultiValuedCrossReferences() {
		final var resourceSet = createResourceSet();
		final var resource = resourceSet.createResource(URI.createFileURI("test_Model_1.xmi"));
		
		final var ePackage = loadEPackage("inputs/references.ecore");
		final var company = createInstance(ePackage, "Company");
		company.eSet(company.eClass().getEStructuralFeature("name"), "TechCorp");
		
		final var emp1 = createInstance(ePackage, "Employee");
		emp1.eSet(emp1.eClass().getEStructuralFeature("name"), "Alice");
		
		final var emp2 = createInstance(ePackage, "Employee");
		emp2.eSet(emp2.eClass().getEStructuralFeature("name"), "Bob");
		
		company.eSet(company.eClass().getEStructuralFeature("employees"), java.util.List.of(emp1, emp2));
		company.eSet(company.eClass().getEStructuralFeature("ceo"), emp1);
		resource.getContents().add(company);

		final var result = printer.prettyPrint(company);

		assertThat(result).isEqualTo("""
			Company
			  name: "TechCorp"
			  employees (2):
			    [0] Employee
			      name: "Alice"
			    [1] Employee
			      name: "Bob"
			  ceo -> Employee (employees[0])
			""");
	}

	@Test
	void testPrettyPrintResource() {
		final var resourceSet = createResourceSet();
		final var resource = resourceSet.createResource(URI.createFileURI("test_Library_1.xmi"));
		
		final var ePackage = loadEPackage("inputs/simple.ecore");
		final var person1 = createInstance(ePackage, "Person");
		person1.eSet(person1.eClass().getEStructuralFeature("name"), "Alice");
		
		final var person2 = createInstance(ePackage, "Person");
		person2.eSet(person2.eClass().getEStructuralFeature("name"), "Bob");
		
		resource.getContents().add(person1);
		resource.getContents().add(person2);

		final var result = printer.prettyPrint(resource);

		assertThat(result).isEqualTo("""
			Resource: test_Library_1.xmi
			  Person
			    name: "Alice"
			  Person
			    name: "Bob"
			""");
	}

	@Test
	void testPrettyPrintResourceSet() {
		final var resourceSet = createResourceSet();
		final var resource1 = resourceSet.createResource(URI.createFileURI("test_Person_1.xmi"));
		final var resource2 = resourceSet.createResource(URI.createFileURI("test_Person_2.xmi"));
		
		final var ePackage = loadEPackage("inputs/simple.ecore");
		final var person1 = createInstance(ePackage, "Person");
		person1.eSet(person1.eClass().getEStructuralFeature("name"), "Alice");
		
		final var person2 = createInstance(ePackage, "Person");
		person2.eSet(person2.eClass().getEStructuralFeature("name"), "Bob");
		
		resource1.getContents().add(person1);
		resource2.getContents().add(person2);

		final var result = printer.prettyPrint(resourceSet);

		assertThat(result).isEqualTo("""
			Resource: test_Person_1.xmi
			  Person
			    name: "Alice"
			Resource: test_Person_2.xmi
			  Person
			    name: "Bob"
			""");
	}

	@Test
	void testPrettyPrintEObjectWithOppositeReferences() {
		final var resourceSet = createResourceSet();
		final var resource = resourceSet.createResource(URI.createFileURI("test_Model_1.xmi"));
		
		final var ePackage = loadEPackage("inputs/simplelibrary.ecore");
		final var library = createInstance(ePackage, "Library");
		library.eSet(library.eClass().getEStructuralFeature("name"), "City Library");
		
		final var book = createInstance(ePackage, "Book");
		book.eSet(book.eClass().getEStructuralFeature("title"), "1984");
		
		final var author = createInstance(ePackage, "Author");
		author.eSet(author.eClass().getEStructuralFeature("name"), "Orwell");
		
		// Set up opposite references: library.books <-> book.library (containment)
		library.eSet(library.eClass().getEStructuralFeature("books"), java.util.List.of(book));
		library.eSet(library.eClass().getEStructuralFeature("authors"), java.util.List.of(author));
		
		// Set up opposite references: book.authors <-> author.books
		book.eSet(book.eClass().getEStructuralFeature("authors"), java.util.List.of(author));
		
		resource.getContents().add(library);

		final var result = printer.prettyPrint(library);

		assertThat(result).isEqualTo("""
			Library
			  name: "City Library"
			  books (1):
			    [0] Book
			      title: "1984"
			      authors (opposite) (1):
			        [0] -> Author (authors[0])
			  authors (1):
			    [0] Author
			      name: "Orwell"
			      books (opposite) (1):
			        [0] -> Book (books[0])
			""");
	}

	@Test
	void testPrettyPrintEObjectWithMultiValuedAttributes() {
		final var ePackage = loadEPackage("inputs/multivalued.ecore");
		final var container = createInstance(ePackage, "Container");
		container.eSet(container.eClass().getEStructuralFeature("tags"), 
			java.util.List.of("tag1", "tag2", "tag3"));

		final var result = printer.prettyPrint(container);

		assertThat(result).isEqualTo("""
			Container
			  tags (3):
			    "tag1"
			    "tag2"
			    "tag3"
			""");
	}

	@Test
	void testPrettyPrintResourceWithNullURI() {
		final var resourceSet = createResourceSet();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
			.put(Resource.Factory.Registry.DEFAULT_EXTENSION, new org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl());
		// Create resource with dummy URI but then set it to null to simulate edge case
		final var resource = resourceSet.createResource(org.eclipse.emf.common.util.URI.createFileURI("temp.xmi"));
		resource.setURI(null); // Simulate null URI after creation
		
		final var ePackage = loadEPackage("inputs/simple.ecore");
		final var person = createInstance(ePackage, "Person");
		person.eSet(person.eClass().getEStructuralFeature("name"), "John");
		resource.getContents().add(person);

		final var result = printer.prettyPrint(person);

		assertThat(result).isEqualTo("""
			Person
			  name: "John"
			""");
	}

	@Test
	void testPrettyPrintEObjectWithSingleValuedContainment() {
		final var ePackage = loadEPackage("inputs/nested-containment.ecore");
		final var container = createInstance(ePackage, "Container");
		container.eSet(container.eClass().getEStructuralFeature("name"), "RootNode");
		
		final var section = createInstance(ePackage, "Section");
		section.eSet(section.eClass().getEStructuralFeature("value"), "SectionNode");
		
		container.eSet(container.eClass().getEStructuralFeature("section"), section);

		final var result = printer.prettyPrint(container);

		assertThat(result).isEqualTo("""
			Container
			  name: "RootNode"
			  section:
			    Section
			      value: "SectionNode"
			""");
	}

	@Test
	void testGetContainmentPathForDeeplyNestedObject() {
		final var resourceSet = createResourceSet();
		final var resource = resourceSet.createResource(URI.createFileURI("test_Library_1.xmi"));
		
		final var ePackage = loadEPackage("inputs/library.ecore");
		final var library = createInstance(ePackage, "Library");
		final var shelf = createInstance(ePackage, "Shelf");
		final var book1 = createInstance(ePackage, "Book");
		book1.eSet(book1.eClass().getEStructuralFeature("title"), "Book1");
		final var book2 = createInstance(ePackage, "Book");
		book2.eSet(book2.eClass().getEStructuralFeature("title"), "Book2");
		final var person = createInstance(ePackage, "Person");
		person.eSet(person.eClass().getEStructuralFeature("name"), "Author");
		
		shelf.eSet(shelf.eClass().getEStructuralFeature("books"), java.util.List.of(book1, book2));
		library.eSet(library.eClass().getEStructuralFeature("shelves"), java.util.List.of(shelf));
		resource.getContents().add(library);
		
		book2.eSet(book2.eClass().getEStructuralFeature("author"), person);
		library.eSet(library.eClass().getEStructuralFeature("director"), person);

		final var result = printer.prettyPrint(book2);

		assertThat(result).contains("author -> Person (director)");
	}

	@Test
	void testPrettyPrintComplexExample() {
		final var resourceSet = createResourceSet();
		final var resource = resourceSet.createResource(URI.createFileURI("library_Library_1.xmi"));
		
		final var ePackage = loadEPackage("inputs/library.ecore");
		final var library = createInstance(ePackage, "Library");
		library.eSet(library.eClass().getEStructuralFeature("name"), "City Library");
		
		final var shelf1 = createInstance(ePackage, "Shelf");
		shelf1.eSet(shelf1.eClass().getEStructuralFeature("name"), "Fiction");
		
		final var shelf2 = createInstance(ePackage, "Shelf");
		shelf2.eSet(shelf2.eClass().getEStructuralFeature("name"), "Science");
		
		final var book1 = createInstance(ePackage, "Book");
		book1.eSet(book1.eClass().getEStructuralFeature("title"), "1984");
		
		final var book2 = createInstance(ePackage, "Book");
		book2.eSet(book2.eClass().getEStructuralFeature("title"), "Brave New World");
		
		final var person = createInstance(ePackage, "Person");
		person.eSet(person.eClass().getEStructuralFeature("name"), "Director");
		
		shelf1.eSet(shelf1.eClass().getEStructuralFeature("books"), java.util.List.of(book1, book2));
		library.eSet(library.eClass().getEStructuralFeature("shelves"), java.util.List.of(shelf1, shelf2));
		library.eSet(library.eClass().getEStructuralFeature("director"), person);
		resource.getContents().add(library);
		
		book1.eSet(book1.eClass().getEStructuralFeature("author"), person);

		final var result = printer.prettyPrint(library);

		assertThat(result).isEqualTo("""
				Library
				  name: "City Library"
				  shelves (2):
				    [0] Shelf
				      name: "Fiction"
				      books (2):
				        [0] Book
				          title: "1984"
				          author -> Person (director)
				        [1] Book
				          title: "Brave New World"
				    [1] Shelf
				      name: "Science"
				  director:
				    Person
				      name: "Director"
					""");
	}



	@Test
	void testPrettyPrintEObjectWithoutResource() {
		final var ePackage = loadEPackage("inputs/simplelibrary.ecore");
		final var book = createInstance(ePackage, "Book");
		book.eSet(book.eClass().getEStructuralFeature("title"), "Test Book");
		
		final var author = createInstance(ePackage, "Author");
		author.eSet(author.eClass().getEStructuralFeature("name"), "Test Author");
		
		final var bookAuthors = EMFUtils.getAsEObjectsList(book, book.eClass().getEStructuralFeature("authors"));
		bookAuthors.add(author);

		final var result = printer.prettyPrint(book);

		assertThat(result)
			.contains("authors (opposite) (1):")
			.contains("-> Author ()");
	}

	@Test
	void testPrettyPrintEObjectWithMultiValuedCrossReferenceWithoutOpposite() {
		final var resourceSet = createResourceSet();
		final var resource = resourceSet.createResource(URI.createFileURI("test_Team_1.xmi"));
		
		final var ePackage = loadEPackage("inputs/teams.ecore");
		final var team = createInstance(ePackage, "Team");
		team.eSet(team.eClass().getEStructuralFeature("name"), "Dev Team");
		
		final var person1 = createInstance(ePackage, "Person");
		person1.eSet(person1.eClass().getEStructuralFeature("name"), "Alice");
		
		final var person2 = createInstance(ePackage, "Person");
		person2.eSet(person2.eClass().getEStructuralFeature("name"), "Bob");
		
		// members is a multi-valued cross-reference without opposite
		team.eSet(team.eClass().getEStructuralFeature("members"), java.util.List.of(person1, person2));
		
		resource.getContents().add(team);
		resource.getContents().add(person1);
		resource.getContents().add(person2);

		final var result = printer.prettyPrint(team);

		assertThat(result)
			.contains("members (2):")
			.doesNotContain("(opposite)")
			.contains("-> Person (")
			.doesNotContain("test_Team_1.xmi");
	}

	@Test
	void testPrettyPrintLibraryWithTwoBooksAndTwoAuthors() {
		final var resourceSet = createResourceSet();
		final var resource = resourceSet.createResource(URI.createFileURI("test_Library_1.xmi"));
		
		final var ePackage = loadEPackage("inputs/simplelibrary.ecore");
		final var library = createInstance(ePackage, "Library");
		library.eSet(library.eClass().getEStructuralFeature("name"), "City Library");
		
		final var book1 = createInstance(ePackage, "Book");
		book1.eSet(book1.eClass().getEStructuralFeature("title"), "1984");
		
		final var book2 = createInstance(ePackage, "Book");
		book2.eSet(book2.eClass().getEStructuralFeature("title"), "Animal Farm");
		
		final var author1 = createInstance(ePackage, "Author");
		author1.eSet(author1.eClass().getEStructuralFeature("name"), "George Orwell");
		
		final var author2 = createInstance(ePackage, "Author");
		author2.eSet(author2.eClass().getEStructuralFeature("name"), "Aldous Huxley");
		
		library.eSet(library.eClass().getEStructuralFeature("books"), java.util.List.of(book1, book2));
		library.eSet(library.eClass().getEStructuralFeature("authors"), java.util.List.of(author1, author2));
		
		book1.eSet(book1.eClass().getEStructuralFeature("authors"), java.util.List.of(author1));
		book2.eSet(book2.eClass().getEStructuralFeature("authors"), java.util.List.of(author1));
		
		resource.getContents().add(library);

		final var result = printer.prettyPrint(library);

		assertThat(result).isEqualTo("""
				Library
				  name: "City Library"
				  books (2):
				    [0] Book
				      title: "1984"
				      authors (opposite) (1):
				        [0] -> Author (authors[0])
				    [1] Book
				      title: "Animal Farm"
				      authors (opposite) (1):
				        [0] -> Author (authors[0])
				  authors (2):
				    [0] Author
				      name: "George Orwell"
				      books (opposite) (2):
				        [0] -> Book (books[0])
				        [1] -> Book (books[1])
				    [1] Author
				      name: "Aldous Huxley"
					""");
	}

	@Test
	void testPrettyPrintResourceSetWithCrossResourceReferences() {
		final var resourceSet = createResourceSet();
		final var libraryResource1 = resourceSet.createResource(URI.createFileURI("test_Library_1.xmi"));
		final var libraryResource2 = resourceSet.createResource(URI.createFileURI("test_Library_2.xmi"));
		
		final var ePackage = loadEPackage("inputs/simplelibrary.ecore");
		
		// First library with a book
		final var library1 = createInstance(ePackage, "Library");
		library1.eSet(library1.eClass().getEStructuralFeature("name"), "Central Library");
		
		final var book = createInstance(ePackage, "Book");
		book.eSet(book.eClass().getEStructuralFeature("title"), "Brave New World");
		
		library1.eSet(library1.eClass().getEStructuralFeature("books"), java.util.List.of(book));
		
		// Second library with an author
		final var library2 = createInstance(ePackage, "Library");
		library2.eSet(library2.eClass().getEStructuralFeature("name"), "University Library");
		
		final var author = createInstance(ePackage, "Author");
		author.eSet(author.eClass().getEStructuralFeature("name"), "Aldous Huxley");
		
		library2.eSet(library2.eClass().getEStructuralFeature("authors"), java.util.List.of(author));
		
		// Cross-resource reference: book in library1 references author in library2
		book.eSet(book.eClass().getEStructuralFeature("authors"), java.util.List.of(author));
		
		libraryResource1.getContents().add(library1);
		libraryResource2.getContents().add(library2);

		final var result = printer.prettyPrint(resourceSet);

		assertThat(result).isEqualTo("""
			Resource: test_Library_1.xmi
			  Library
			    name: "Central Library"
			    books (1):
			      [0] Book
			        title: "Brave New World"
			        authors (opposite) (1):
			          [0] -> Author (test_Library_2.xmi > authors[0])
			Resource: test_Library_2.xmi
			  Library
			    name: "University Library"
			    authors (1):
			      [0] Author
			        name: "Aldous Huxley"
			        books (opposite) (1):
			          [0] -> Book (test_Library_1.xmi > books[0])
			""");
	}

	@Test
	void testPrettyPrintEObjectWithCrossReferenceToObjectWithoutResource() {
		final var resourceSet = createResourceSet();
		final var libraryResource = resourceSet.createResource(URI.createFileURI("test_Library_1.xmi"));
		
		final var ePackage = loadEPackage("inputs/simplelibrary.ecore");
		final var library = createInstance(ePackage, "Library");
		library.eSet(library.eClass().getEStructuralFeature("name"), "City Library");
		
		final var book = createInstance(ePackage, "Book");
		book.eSet(book.eClass().getEStructuralFeature("title"), "1984");
		
		// Author is NOT added to any resource
		final var author = createInstance(ePackage, "Author");
		author.eSet(author.eClass().getEStructuralFeature("name"), "Orwell");
		
		library.eSet(library.eClass().getEStructuralFeature("books"), java.util.List.of(book));
		libraryResource.getContents().add(library);
		
		// Cross-reference from book (in resource) to author (NOT in resource)
		book.eSet(book.eClass().getEStructuralFeature("authors"), java.util.List.of(author));

		final var result = printer.prettyPrint(library);

		// When referenced object has no resource, path should be empty
		assertThat(result)
			.contains("authors (opposite) (1):")
			.contains("-> Author ()");
	}
}