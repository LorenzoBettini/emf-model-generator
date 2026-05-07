# EMF Model Generator

[![Build Status](https://github.com/LorenzoBettini/emf-model-generator/actions/workflows/build.yml/badge.svg)](https://github.com/LorenzoBettini/emf-model-generator/actions) [![codecov](https://codecov.io/gh/LorenzoBettini/emf-model-generator/graph/badge.svg?token=TNphE6zefq)](https://codecov.io/gh/LorenzoBettini/emf-model-generator) [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=LorenzoBettini_emf-model-generator&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=LorenzoBettini_emf-model-generator) [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=LorenzoBettini_emf-model-generator&metric=coverage)](https://sonarcloud.io/summary/new_code?id=LorenzoBettini_emf-model-generator) [![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=LorenzoBettini_emf-model-generator&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=LorenzoBettini_emf-model-generator)

A Java library for programmatically generating instances of EMF (Eclipse Modeling Framework) models. Given an Ecore metamodel and an EClass or EPackage, the tool generates model instances with populated attributes and references, and serializes them as XMI files.

## Features

- **Automatic Model Generation**: Generate instances of EMF models from Ecore metamodels
- **Smart Reference Handling**: Automatically creates and links referenced objects with proper containment
- **Constraint Compliance**: Respects multiplicities, required features, and metamodel constraints
- **Flexible Configuration**: Customize generation behavior for attributes, references, and containments
- **Multiple Generation Modes**: Generate from EPackage, EClass, or multiple EClasses
- **File Extension Customization**: Configure extensions at global, package, or class level
- **Feature Map Support**: Handles EMF feature maps correctly
- **OSGi Compatible**: Includes OSGi bundle manifest

## Requirements

- **Java**: 21 or higher
- **Maven**: 3.x

## Building and Installing

### Clone the Repository

```bash
git clone https://github.com/LorenzoBettini/emf-model-generator-experiments.git
cd emf-model-generator-experiments
```

### Build and Install Locally

To build and install the library to your local Maven repository:

```bash
./mvnw clean install -DskipTests
```

### Run Tests Only

To run the test suite without installing:

```bash
./mvnw clean test
```

### Run Tests with Coverage

To generate a JaCoCo coverage report:

```bash
./mvnw -P jacoco clean test jacoco:report
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

Include schema location in generated XMI files:

```java
import org.eclipse.emf.ecore.xmi.XMLResource;
import java.util.Map;

EMFModelGenerator generator = new EMFModelGenerator();
generator.generateFrom(personClass);

// Save with schemaLocation attribute
Map<Object, Object> options = Map.of(
    XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE
);
generator.save(options);
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

### Example 11: Custom Containment Reference Behavior

Control how many instances are created for containment references:

```java
import io.github.lorenzobettini.emfmodelgenerator.EMFContainmentReferenceSetter;
import org.eclipse.emf.ecore.EReference;
import java.util.Collection;

EMFModelGenerator generator = new EMFModelGenerator();

generator.getInstancePopulator().setContainmentReferenceSetter(new EMFContainmentReferenceSetter() {
    @Override
    public Collection<EObject> setContainmentReference(EObject owner, EReference reference) {
        // Create 5 books for libraries instead of the default 2
        if ("books".equals(reference.getName())) {
            return createInstances(owner, reference, 5);
        }
        return super.setContainmentReference(owner, reference);
    }
});

generator.generateFrom(libraryClass);
generator.save();
```

### Example 12: Custom Cross-Reference Strategy

Control how cross-references are set between objects:

```java
import io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceSetter;
import io.github.lorenzobettini.emfmodelgenerator.EMFUtils;

EMFModelGenerator generator = new EMFModelGenerator();

generator.getInstancePopulator().setCrossReferenceSetter(new EMFCrossReferenceSetter() {
    @Override
    public void setCrossReference(EObject owner, EReference reference) {
        // All books reference only the first author
        if ("author".equals(reference.getName())) {
            var authors = getInstantiableObjects(owner, reference);
            if (!authors.isEmpty()) {
                EMFUtils.setReference(owner, reference, authors.get(0));
            }
            return;
        }
        super.setCrossReference(owner, reference);
    }
});

generator.generateFrom(libraryClass);
generator.save();
```

### Example 13: Function-Based Customization for Individual Features

Configure specific functions for individual attributes, containments, or cross-references:

```java
import io.github.lorenzobettini.emfmodelgenerator.EMFInstancePopulator;

EMFModelGenerator generator = new EMFModelGenerator();
EMFInstancePopulator populator = generator.getInstancePopulator();

// Custom function for a specific attribute
populator.getAttributeSetter().setFunctionFor(
    myAttribute,
    (owner, attr) -> owner.eSet(attr, "SpecialValue")
);

// Custom function for a specific containment reference
populator.getContainmentReferenceSetter().setFunctionFor(
    myContainment,
    (owner, ref) -> createCustomInstances(owner, ref, 10)
);

// Custom function for a specific cross-reference
populator.getCrossReferenceSetter().setFunctionFor(
    myCrossRef,
    (owner, ref) -> setSpecificCrossReference(owner, ref)
);

generator.generateFrom(myClass);
generator.save();
```

### Example 14: Configure Feature Map Max Count

Control the number of entries generated for feature maps:

```java
EMFModelGenerator generator = new EMFModelGenerator();
EMFInstancePopulator populator = generator.getInstancePopulator();

// Set max count for a specific feature map attribute
populator.getFeatureMapSetter().setMaxCountFor(featureMapAttribute, 5);

generator.generateFrom(documentClass);
generator.save();
```

### Example 15: Reuse ResourceSet Across Multiple Generations

For generating multiple related models that reference each other:

```java
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

// Create a shared ResourceSet
ResourceSet resourceSet = new ResourceSetImpl();
resourceSet.getResourceFactoryRegistry()
    .getExtensionToFactoryMap()
    .put("xmi", new XMIResourceFactoryImpl());

// Use the same ResourceSet for all generations
EMFModelGenerator generator = new EMFModelGenerator(resourceSet);

// First generation
EObject company = generator.generateFrom(companyClass);

// Second generation - can reference instances from first generation
EObject employee = generator.generateFrom(employeeClass);

// Save all resources together
generator.save();
```

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

Control how many instances are created for references:

```java
EMFInstancePopulator populator = new EMFInstancePopulator();

// Configure multiplicity for all containment references
populator.getContainmentReferenceSetter().setDefaultMaxCount(3);

// Configure multiplicity for all cross-references
populator.getCrossReferenceSetter().setDefaultMaxCount(2);

// Configure maximum depth for recursive population
populator.setMaxDepth(4);

// Populate
populator.populateEObjects(library);
```

### Example 19: Populate Multiple Independent Objects

You can populate multiple objects in separate calls, and they maintain independent state (if they are in different resources in different ResourceSets):

```java
EMFInstancePopulator populator = new EMFInstancePopulator();

// Create and add first library to a resource
EObject library1 = EcoreUtil.create(libraryClass);
resource1.getContents().add(library1);
populator.populateEObjects(library1);

// Create and add second library to another resource
EObject library2 = EcoreUtil.create(libraryClass);
resource2.getContents().add(library2);
populator.populateEObjects(library2);

// Each library gets its own sample data with independent counters
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

- **Strings**: `<AttributeName>_<Counter>` (e.g., `name_1`, `title_2`)
- **Integers**: Fixed range (20-30)
- **Booleans**: Alternates between `true` and `false`
- **Dates**: Fixed date for reproducibility
- **References**: Round-robin selection from available instances

## Constraints

- Abstract EClasses cannot be instantiated directly (use `generateAllFrom()` for their concrete subclasses)
- Maximum recursion depth is configurable (default: 2) to prevent infinite loops
- Feature maps require the ExtendedMetaData annotations in the Ecore model
- Container references (opposites of containment) cannot be set directly (EMF does not allow it)

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
- **GitHub Repository**: <https://github.com/LorenzoBettini/emf-model-generator-experiments>
- **Issues**: <https://github.com/LorenzoBettini/emf-model-generator-experiments/issues>
