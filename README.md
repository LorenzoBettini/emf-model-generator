# EMF Model Generator

[![Maven Central](https://img.shields.io/maven-central/v/io.github.lorenzobettini/emf-model-generator.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.lorenzobettini/emf-model-generator)

[![Build Status](https://github.com/LorenzoBettini/emf-model-generator/actions/workflows/build.yml/badge.svg)](https://github.com/LorenzoBettini/emf-model-generator/actions) [![codecov](https://codecov.io/gh/LorenzoBettini/emf-model-generator/graph/badge.svg?token=TNphE6zefq)](https://codecov.io/gh/LorenzoBettini/emf-model-generator) [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=LorenzoBettini_emf-model-generator&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=LorenzoBettini_emf-model-generator) [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=LorenzoBettini_emf-model-generator&metric=coverage)](https://sonarcloud.io/summary/new_code?id=LorenzoBettini_emf-model-generator) [![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=LorenzoBettini_emf-model-generator&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=LorenzoBettini_emf-model-generator)

A Java library for programmatically generating instances of EMF (Eclipse Modeling Framework) models.
Given an Ecore metamodel and an EClass or EPackage, the tool creates populated model instances and serializes them as XMI files.
The generator focuses on deterministic, reproducible, structurally valid EMF instances for tests, examples, prototypes, and generated-editor wizards.

## Features

- **Automatic Model Generation**: generate instances from EClasses, EPackages, or selected sets of EClasses
- **Deterministic Defaults**: use predictable attribute values and round-robin candidate selection for reproducible output
- **Reference Handling**: populate containment references first, then assign non-containment cross-references among existing instances
- **Constraint Compliance**: respect multiplicities, required features, containment semantics, opposite references, abstract classes, and interfaces
- **Flexible Configuration**: customize depth, multiplicities, per-feature functions, cycle policy, candidate selectors, and setter implementations
- **Resource Management**: load Ecore files, register packages, create resources, save generated XMI, and unload metamodels
- **File Naming Customization**: configure file prefixes and extensions globally, per package, or per class
- **Feature Map Support**: populate EMF feature maps using ExtendedMetaData group members
- **OSGi Compatible**: includes OSGi bundle metadata

## Requirements

- **Java**: 21 or higher
- **Maven**: 3.x

## Building and Installing

### Clone the Repository

```bash
git clone https://github.com/LorenzoBettini/emf-model-generator.git
cd emf-model-generator
```

### Build and Install Locally

To build and install the library to your local Maven repository:

```bash
./mvnw clean install -DskipTests
```

### Run Tests Only

To run the test suite without installing:

```bash
./mvnw test
```

### Run Tests with Coverage

To run the full verification phase with JaCoCo coverage:

```bash
./mvnw verify -Pjacoco
```

The coverage report will be available at `target/site/jacoco/index.html`.

### Run Mutation Testing

To run PIT mutation testing (it might take a few minutes):

```bash
./mvnw org.pitest:pitest-maven:mutationCoverage
```

The mutation testing report will be available at `target/pit-reports/index.html`.

Note that only "Mutation Coverage" and "Test Strength" are important: "Line Coverage" is below 100% due to a limitation of PIT, which considers also private constructors (in classes with only static utility methods).

For faster mutation testing on a single class:

```bash
./mvnw org.pitest:pitest-maven:mutationCoverage \
    -DtargetClasses=io.github.lorenzobettini.emfmodelgenerator.EMFModelGenerator \
    -DtargetTests=io.github.lorenzobettini.emfmodelgenerator.EMFModelGeneratorTest
```

### Run Performance Tests

To measure how execution time scales with different metrics:

```bash
./mvnw test -Pperformance-tests
```

See [PERFORMANCE.md](PERFORMANCE.md) for detailed instructions on running and analyzing performance tests.

## Usage in a Maven Project

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.lorenzobettini</groupId>
    <artifactId>emf-model-generator</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

You'll also get the transitive dependencies, e.g., EMF dependencies:

```xml
<dependency>
    <groupId>org.eclipse.emf</groupId>
    <artifactId>org.eclipse.emf.ecore.xmi</artifactId>
    <version>2.39.0</version>
</dependency>
```

## Basic Examples

### Example 1: Generate from an EClass

The simplest way to generate a model instance from a specific EClass:

```java
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import io.github.lorenzobettini.emfmodelgenerator.EMFModelGenerator;

// Create generator and load the Ecore model
EMFModelGenerator generator = new EMFModelGenerator();
EPackage ePackage = generator.loadEcoreModel("model.ecore");

// Get the target EClass
EClass personEClass = (EClass) ePackage.getEClassifier("Person");

// Generate and save
generator.setOutputDirectory("output/");
EObject generatedPerson = generator.generateFrom(personEClass);
generator.save();
// Creates: output/mypackage_Person_1.xmi

// Clean up when done
generator.unloadEcoreModels();
```

### Example 2: Generate from an EPackage

When you pass an EPackage, the generator creates an instance of the first instantiable EClass:

```java
EMFModelGenerator generator = new EMFModelGenerator();
EPackage ePackage = generator.loadEcoreModel("model.ecore");

generator.setOutputDirectory("output/");
EObject generated = generator.generateFrom(ePackage);
generator.save();

generator.unloadEcoreModels(); // Clean up
```

### Example 3: Generate Multiple Models

Generate instances of multiple EClasses, each in its own XMI file (interconnected with cross-references if feasible):

```java
EClass companyClass = (EClass) ePackage.getEClassifier("Company");
EClass employeeClass = (EClass) ePackage.getEClassifier("Employee");

EMFModelGenerator generator = new EMFModelGenerator();
generator.setOutputDirectory("output/");
List<EObject> models = generator.generateFromSeveral(companyClass, employeeClass);
generator.save();
// Creates: output/company_Company_1.xmi
//          output/company_Employee_1.xmi
```

### Example 4: Generate All Concrete Classes from Package

Generate instances of all concrete (non-abstract) EClasses in a package:

```java
EMFModelGenerator generator = new EMFModelGenerator();
generator.setOutputDirectory("output/");
List<EObject> allModels = generator.generateAllFrom(ePackage);
generator.save();
```

### Example 5: Generate with Validation

Always validate generated models to ensure they conform to the metamodel:

```java
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.util.Diagnostician;

EObject generated = generator.generateFrom(personClass);

// Validate before saving
var diagnostic = Diagnostician.INSTANCE.validate(generated);
if (diagnostic.getSeverity() != Diagnostic.OK) {
    System.err.println("Validation failed: " + diagnostic);
} else {
    generator.save();
}
```

## Loading Ecore Models

The `EMFModelGenerator` provides a convenient method to load Ecore models without manual ResourceSet configuration:

```java
EMFModelGenerator generator = new EMFModelGenerator();

// Load Ecore model - automatically registers resource factory and package
EPackage ePackage = generator.loadEcoreModel("models/mymodel.ecore");

// Use the loaded package
EClass myClass = (EClass) ePackage.getEClassifier("MyClass");
EObject instance = generator.generateFrom(myClass);
generator.save();

// Clean up - unloads resources and unregisters packages
generator.unloadEcoreModels();
```

The `loadEcoreModel` method:
- Automatically registers `EcoreResourceFactoryImpl` if not already registered
- Loads the Ecore file into the generator's ResourceSet
- Registers the EPackage in the EMF global registry
- Tracks the resource and nsURI for cleanup

Call `unloadEcoreModels()` when finished to:
- Unload all loaded Ecore resources
- Remove resources from the ResourceSet
- Unregister packages from the global registry
- Clear internal tracking lists for reuse

This simplifies Ecore model handling compared to manual ResourceSet configuration.

## Configuration Examples

### Example 6: Configure Output and Multiplicity

```java
EMFModelGenerator generator = new EMFModelGenerator();

// Set output directory
generator.setOutputDirectory("generated-models/");

// Control how many instances are created for references
generator.getInstancePopulator().setContainmentReferenceDefaultMaxCount(3);
generator.getInstancePopulator().setCrossReferenceDefaultMaxCount(2);

// Limit recursion depth to avoid deep hierarchies
generator.getInstancePopulator().setMaxDepth(3);

// Generate multiple instances of the same class
generator.setNumberOfInstances(5);

generator.generateFrom(personClass);
generator.save();
// Creates 5 files: person_Person_1.xmi through person_Person_5.xmi
```

### Example 7: Save with Schema Location

Ask EMF's XMI serializer to emit `xsi:schemaLocation` entries.
When EMF can compute metamodel locations, the generated XMI records the association between package namespace URIs and those locations.
This can make generated files easier to inspect and exchange in contexts where the receiver does not rely only on a pre-populated `EPackage` registry.

```java
import org.eclipse.emf.ecore.xmi.XMLResource;
import java.util.Map;

EMFModelGenerator generator = new EMFModelGenerator();
generator.generateFrom(personClass);

generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));
```

### Example 8: Custom File Extensions

Customize file extensions at different levels:

```java
EMFModelGenerator generator = new EMFModelGenerator();

// Set global extension for all generated files
generator.setGlobalFileExtension("model");

// Override for a specific EPackage
generator.setFileExtensionForEPackage(ePackage, "pkg");

// Override for a specific EClass (highest priority)
generator.setFileExtensionForEClass(companyClass, "company");

generator.generateFromSeveral(companyClass, employeeClass);
generator.save();
// Creates: company_Company_1.company (EClass-specific)
//          company_Employee_1.pkg     (EPackage-specific)
```

### Example 9: Custom File Prefix

Add a prefix to all generated file names:

```java
EMFModelGenerator generator = new EMFModelGenerator();
generator.setFilePrefix("test_");
generator.generateFrom(personClass);
generator.save();
// Creates: test_mypackage_Person_1.xmi
```

## Advanced Customization Examples

### Example 10: Custom Attribute Values

Override the default attribute setter to provide custom values:

```java
import io.github.lorenzobettini.emfmodelgenerator.EMFAttributeSetter;
import org.eclipse.emf.ecore.EAttribute;

EMFModelGenerator generator = new EMFModelGenerator();

// Custom attribute setter
generator.getInstancePopulator().setAttributeSetter(new EMFAttributeSetter() {
    @Override
    public void setAttribute(EObject eObject, EAttribute attribute) {
        if ("name".equals(attribute.getName())) {
            eObject.eSet(attribute, "CUSTOM_" + eObject.eClass().getName());
            return;
        }
        // Delegate to default behavior for other attributes
        super.setAttribute(eObject, attribute);
    }
});

generator.generateFrom(libraryClass);
generator.save();
```

### Example 11: Configure Containment Multiplicity

Control how many instances are created for containment references:

```java
EMFModelGenerator generator = new EMFModelGenerator();
EMFInstancePopulator populator = generator.getInstancePopulator();

// Use 3 as the default for all multi-valued containment references
populator.setContainmentReferenceDefaultMaxCount(3);

// Override one specific containment reference
EReference booksReference = (EReference)
    libraryClass.getEStructuralFeature("books");
populator.setContainmentReferenceMaxCountFor(booksReference, 5);

generator.generateFrom(libraryClass);
generator.save();
```

### Example 12: Custom Cross-Reference Function

Customize one cross-reference while keeping the default validity checks for the rest:

```java
import java.util.concurrent.atomic.AtomicInteger;

EMFModelGenerator generator = new EMFModelGenerator();
EMFInstancePopulator populator = generator.getInstancePopulator();

EClass bookClass = (EClass) ePackage.getEClassifier("Book");
EReference authorsReference = (EReference)
    bookClass.getEStructuralFeature("authors");
AtomicInteger counter = new AtomicInteger();

// Select authors in reverse order instead of the default round-robin order
populator.functionForCrossReference(authorsReference, owner -> {
    EObject library = owner.eContainer();
    var authors = EMFUtils.getAsEObjectsList(
        library,
        library.eClass().getEStructuralFeature("authors")
    );
    if (authors.isEmpty()) {
        return null; // fall back to default behavior if no custom value exists
    }
    int index = authors.size() - 1 - (counter.getAndIncrement() % authors.size());
    return authors.get(index);
});

generator.generateFrom(libraryClass);
generator.save();
```

### Example 13: Function-Based Customization for Individual Features

Configure specific functions for individual attributes, containments, cross-references, or feature-map group members:

```java
import io.github.lorenzobettini.emfmodelgenerator.EMFInstancePopulator;

EMFModelGenerator generator = new EMFModelGenerator();
EMFInstancePopulator populator = generator.getInstancePopulator();

populator.functionForAttribute(nameAttribute,
    owner -> "CUSTOM_" + owner.eClass().getName());

populator.functionForContainmentReference(shelfReference,
    owner -> EcoreUtil.create(shelfReference.getEReferenceType()));

populator.functionForCrossReference(authorsReference,
    owner -> findPreferredAuthor(owner));

populator.functionForFeatureMapGroupMember(writersReference,
    owner -> EcoreUtil.create(writerClass));

generator.generateFrom(myClass);
generator.save();
```

If a custom cross-reference function returns `null`, the default cross-reference selection is used.
For containment references and feature-map group members, returned objects are recursively populated by the populator.

### Example 14: Configure Feature Map Max Count

Control the number of entries generated for feature maps:

```java
EMFModelGenerator generator = new EMFModelGenerator();
EMFInstancePopulator populator = generator.getInstancePopulator();

populator.setFeatureMapDefaultMaxCount(4);
populator.setFeatureMapMaxCountFor(featureMapAttribute, 5);

generator.generateFrom(documentClass);
generator.save();
```

### Example 15: Generate Related Models in One ResourceSet

`EMFModelGenerator` uses one `ResourceSet` for the generated resources.
When several root objects are generated together, containment trees are populated first and then cross-references are assigned among all generated objects.

```java
EMFModelGenerator generator = new EMFModelGenerator();
generator.setOutputDirectory("output/");

EClass companyClass = (EClass) ePackage.getEClassifier("Company");
EClass employeeClass = (EClass) ePackage.getEClassifier("Employee");

generator.generateFromSeveral(companyClass, employeeClass);
generator.save();
```

You can also provide an existing `ResourceSet` to the constructor when generated models must live together with resources managed by your application.

## Using EMFInstancePopulator Directly

While `EMFModelGenerator` provides a complete solution for generating and saving models, you can also use `EMFInstancePopulator` directly when you need more control. This is useful when you have already created EObject instances (manually or programmatically) and only want to populate them with data.
Note that when using `EMFInstancePopulator` directly, you are responsible for managing resources and saving the populated models.
Moreover, the instances passed to `EMFInstancePopulator` should be properly created according to its metamodel (e.g., using `EcoreUtil.create(EClass)`).

### When to Use EMFInstancePopulator

Use `EMFInstancePopulator` directly when:

- You've already created EObject instances and just need to populate them
- You want to populate objects that are already in resources
- You need fine-grained control over the population process
- You're working with existing models that need sample data

### Example 16: Basic Population of Existing EObjects

```java
// The EPackage has already been loaded somewhere else
EClass libraryClass = (EClass) ePackage.getEClassifier("Library");

// Create an empty instance
EObject library = EcoreUtil.create(libraryClass);

// Create a populator and populate the instance
EMFInstancePopulator populator = new EMFInstancePopulator();
populator.populateEObjects(library);

// Now library has sample data for all attributes and references
```

### Example 17: Populate Objects Already in Resources

```java
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.common.util.URI;

// Create resource with an empty instance
ResourceSet resourceSet = new ResourceSetImpl();
Resource resource = resourceSet.createResource(
    URI.createFileURI("output/library.xmi"));

EObject library = EcoreUtil.create(libraryClass);
resource.getContents().add(library);

// Populate the instance
EMFInstancePopulator populator = new EMFInstancePopulator();
populator.populateEObjects(library);

// Save the populated model
resource.save(null);
```

### Example 18: Configure Population Behavior

Control how many values are generated for multi-valued features:

```java
EMFInstancePopulator populator = new EMFInstancePopulator();

populator.setContainmentReferenceDefaultMaxCount(3);
populator.setCrossReferenceDefaultMaxCount(2);
populator.setAttributeDefaultMaxCount(2);
populator.setFeatureMapDefaultMaxCount(4);

// Configure maximum depth for recursive containment and feature-map population
populator.setMaxDepth(4);

populator.populateEObjects(library);
```

### Example 19: Populate Objects in Separate Calls

You can reuse a populator across multiple calls, but its setters and selectors may keep deterministic state such as counters and round-robin positions.
Create a new populator if you want a fresh generation state.

```java
EMFInstancePopulator populator = new EMFInstancePopulator();

EObject library1 = EcoreUtil.create(libraryClass);
resource1.getContents().add(library1);
populator.populateEObjects(library1);

EObject library2 = EcoreUtil.create(libraryClass);
resource2.getContents().add(library2);
populator.populateEObjects(library2);
```

### Example 20: Populate Multiple Related Objects Together

To enable cross-references between objects, populate them together:

```java
EMFInstancePopulator populator = new EMFInstancePopulator();

// Create instances in separate resources
EObject library = EcoreUtil.create(libraryClass);
resource1.getContents().add(library);

EObject book = EcoreUtil.create(bookClass);
resource2.getContents().add(book);

EObject author = EcoreUtil.create(authorClass);
resource3.getContents().add(author);

// Populate all together - this allows cross-references between them
populator.populateEObjects(library, book, author);

// Now library, book, and author can reference each other
```

### Other customizations

The same patterns used for customizations shown above for the `EMFModelGenerator` can be applied to the `EMFInstancePopulator` as well, by configuring its attribute setter, containment reference setter, cross-reference setter, and feature map setter, etc.

### EMFInstancePopulator vs EMFModelGenerator

| Feature | EMFInstancePopulator | EMFModelGenerator |
|---------|---------------------|-------------------|
| **Creates EObjects** | No - you provide them | Yes - automatically creates instances |
| **Creates Resources** | No - you manage resources | Yes - creates and manages resources |
| **Saves to XMI** | No - you handle saving | Yes - provides `save()` method |
| **File naming** | N/A | Automatic with conventions |
| **Use case** | Fine-grained control, existing objects | Complete generation workflow |
| **Complexity** | Lower level, more manual | Higher level, more automated |

Use `EMFModelGenerator` when you want the complete workflow (create, populate, save). Use `EMFInstancePopulator` when you need just the population step with objects you've already created.

## File Naming Convention

Generated XMI files follow the naming pattern:

```
[prefix]<EPackageName>_<EClassName>_<counter>.<extension>
```

Where:

- `[prefix]` is optional (set via `setFilePrefix()`)
- `<EPackageName>` is the name of the containing EPackage
- `<EClassName>` is the name of the EClass
- `<counter>` starts at 1 and increments for multiple instances
- `<extension>` defaults to "xmi" (customizable)

Examples:

- `company_Company_1.xmi`
- `company_Employee_1.xmi`
- `test_library_Book_1.xmi` (with prefix "test_")
- `mymodel_Person_1.model` (with custom extension)

## Key Classes

- **`EMFModelGenerator`**: Main entry point for model generation
- **`EMFInstancePopulator`**: Coordinates the population of model instances
- **`EMFAttributeSetter`**: Sets attribute values (extendable)
- **`EMFContainmentReferenceSetter`**: Creates and sets containment references (extendable)
- **`EMFCrossReferenceSetter`**: Sets cross-references between objects (extendable)
- **`EMFFeatureMapSetter`**: Handles EMF feature maps (extendable)
- **`EMFUtils`**: Utility methods for EMF operations and validation

## Sample Data Generation Patterns

The generator uses predictable patterns for sample data:

- **Strings**: `<EClassName>_<AttributeName>_<Counter>` (e.g., `Person_name_1`)
- **Integers and integer-like values**: start at `20` and increase with the per-class/attribute counter
- **Doubles and floats**: start at `20.5` and increase with the per-class/attribute counter
- **Booleans**: alternate between `true` and `false`
- **Dates**: start from `2025-01-01` and advance by one day per generated value
- **Enums**: selected with a deterministic round-robin selector
- **References**: selected with deterministic round-robin strategies from available assignable instances

## Constraints and Scope

- Abstract EClasses and interfaces cannot be instantiated directly; use `generateAllFrom(EClass)` to generate their concrete subclasses.
- Multi-valued feature counts respect lower and upper bounds; a requested count below the lower bound is raised to the lower bound, and a requested count above the upper bound is capped.
- Maximum containment depth is configurable and defaults to `5`.
- Feature maps require ExtendedMetaData annotations in the Ecore model.
- Container references, which are opposites of containment references, cannot be set directly from the contained side.
- Cross-references are assigned only among existing instances; the generator does not create new objects just to satisfy a non-containment reference.
- The generator targets structural EMF validity, not arbitrary OCL or domain-specific invariants.

## Contributing

Contributions are welcome! Please ensure:

- All new code is covered by tests (aim for 100% coverage)
- Run the full test suite: `./mvnw clean test`
- Check coverage: `./mvnw -P jacoco jacoco:report`
- Code follows the existing style (tabs, max 100 chars per line)
- Use Java 21 features where appropriate

## License

This project is licensed under the Eclipse Public License 2.0.

## Resources

- **Eclipse Modeling Framework (EMF)**: <https://www.eclipse.org/modeling/emf/>
- **GitHub Repository**: <https://github.com/LorenzoBettini/emf-model-generator>
- **Issues**: <https://github.com/LorenzoBettini/emf-model-generator/issues>
