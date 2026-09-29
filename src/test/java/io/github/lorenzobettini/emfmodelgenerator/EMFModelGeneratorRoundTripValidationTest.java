package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEClassExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEReferenceExists;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EMFModelGeneratorRoundTripValidationTest {

	private static final String INPUTS = "target/inputs";

	@TempDir
	Path temporaryDirectory;

	private EMFModelGenerator generator;

	@AfterEach
	void tearDown() {
		EMFTestUtils.cleanupRegisteredPackages();
		if (generator != null) {
			generator.unloadEcoreModels();
		}
	}

	@Test
	void requiredTransientReferenceIsValidInMemoryButInvalidAfterReload() throws IOException {
		generator = newGenerator("transient");
		var model = generator.loadEcoreModel(INPUTS + "/transient-reference.ecore");
		var rootClass = assertEClassExists(model, "Root");
		var holderClass = assertEClassExists(model, "Holder");
		var holderReference = assertEReferenceExists(rootClass, "holder");
		var targetReference = assertEReferenceExists(holderClass, "target");
		var root = generator.generateFrom(rootClass);
		var holder = (EObject) root.eGet(holderReference);

		assertThat(holder.eGet(targetReference)).isNotNull();
		assertThat(generator.validate().isValid()).isTrue();

		var result = generator.saveAndValidateRoundTrip();

		assertThat(result.isValid()).isFalse();
		assertThat(result.rejectedDiagnostics())
				.extracting(diagnostic -> diagnostic.getMessage())
				.anySatisfy(message -> assertThat(message).contains("target"));
		assertThat(temporaryDirectory.resolve("transient/roundtrip_Root_1.xmi"))
				.exists()
				.content().doesNotContain("target=");
	}

	@Test
	void validRoundTripForwardsSaveOptionsAndUsesLocalPackageRegistry() throws IOException {
		generator = newGenerator("valid");
		var model = generator.loadEcoreModel(INPUTS + "/simple.ecore");
		generator.generateFrom(assertEClassExists(model, "Person"));

		var result = generator.saveAndValidateRoundTrip(
				Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));

		assertThat(result.isValid()).isTrue();
		assertThat(temporaryDirectory.resolve("valid/simple_Person_1.xmi"))
				.content().contains("xsi:schemaLocation");
	}

	@Test
	void packageAvailableOnlyInGeneratorRegistryIsUsedForReload() throws IOException {
		generator = newGenerator("local-package");
		var model = generator.loadEcoreModel(INPUTS + "/simple.ecore");
		generator.generateFrom(assertEClassExists(model, "Person"));
		EPackage.Registry.INSTANCE.remove(model.getNsURI(), model);

		assertThat(generator.saveAndValidateRoundTrip().isValid()).isTrue();
	}

	@Test
	void multipleResourcesReloadTogetherWithCrossResourceReference() throws IOException {
		generator = newGenerator("several");
		var model = generator.loadEcoreModel(INPUTS + "/extlibrary.ecore");
		var bookClass = assertEClassExists(model, "Book");
		var writerClass = assertEClassExists(model, "Writer");
		var generated = generator.generateFromSeveral(bookClass, writerClass);

		assertThat(generated.getFirst().eGet(assertEReferenceExists(bookClass, "author")))
				.isSameAs(generated.get(1));
		assertThat(generator.saveAndValidateRoundTrip().isValid()).isTrue();
		assertThat(temporaryDirectory.resolve("several/extlibrary_Book_1.xmi"))
				.content().contains("extlibrary_Writer_1.xmi#/");
		assertThat(temporaryDirectory.resolve("several/extlibrary_Writer_1.xmi")).exists();
	}

	@Test
	void customExtensionFactoryIsReusedAndTemporaryResourceIsUnloaded() throws IOException {
		generator = newGenerator("custom");
		var unloadCalls = new AtomicInteger();
		var factoryCalls = new AtomicInteger();
		Resource.Factory factory = uri -> {
			factoryCalls.incrementAndGet();
			return new XMIResourceImpl(uri) {
				@Override
				protected void doUnload() {
					super.doUnload();
					unloadCalls.incrementAndGet();
				}
			};
		};
		generator.getResourceSet().getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put("model", factory);
		generator.setGlobalFileExtension("model");
		var model = generator.loadEcoreModel(INPUTS + "/simple.ecore");
		generator.generateFrom(assertEClassExists(model, "Person"));

		assertThat(generator.saveAndValidateRoundTrip().isValid()).isTrue();
		assertThat(temporaryDirectory.resolve("custom/simple_Person_1.model")).exists();
		assertThat(factoryCalls).hasValue(2);
		assertThat(unloadCalls).hasValue(1);
	}

	@Test
	void protocolFactoryIsReusedForReload() throws IOException {
		generator = newGenerator("protocol");
		var model = generator.loadEcoreModel(INPUTS + "/simple.ecore");
		var factoryCalls = new AtomicInteger();
		Resource.Factory factory = uri -> {
			factoryCalls.incrementAndGet();
			return new XMIResourceImpl(uri);
		};
		generator.getResourceSet().getResourceFactoryRegistry().getProtocolToFactoryMap()
				.put("file", factory);
		generator.generateFrom(assertEClassExists(model, "Person"));

		assertThat(generator.saveAndValidateRoundTrip().isValid()).isTrue();
		assertThat(factoryCalls).hasValue(2);
	}

	@Test
	void defaultContentTypeFactoryIsReusedForReload() throws IOException {
		generator = newGenerator("content-type");
		var model = generator.loadEcoreModel(INPUTS + "/simple.ecore");
		var factoryCalls = new AtomicInteger();
		Resource.Factory factory = uri -> {
			factoryCalls.incrementAndGet();
			return new XMIResourceImpl(uri);
		};
		var registry = generator.getResourceSet().getResourceFactoryRegistry();
		registry.getExtensionToFactoryMap().clear();
		registry.getContentTypeToFactoryMap()
				.put(Resource.Factory.Registry.DEFAULT_CONTENT_TYPE_IDENTIFIER, factory);
		generator.setGlobalFileExtension("model");
		generator.generateFrom(assertEClassExists(model, "Person"));

		assertThat(generator.saveAndValidateRoundTrip().isValid()).isTrue();
		assertThat(factoryCalls).hasValue(2);
	}

	@Test
	void validationBeforeSaveStillPreventsWriting() throws IOException {
		generator = newGenerator("rejected");
		var model = generator.loadEcoreModel(INPUTS + "/extlibrary.ecore");
		generator.generateFrom(assertEClassExists(model, "Book"));
		generator.enableValidationBeforeSave();

		assertThatExceptionOfType(EMFValidationException.class)
				.isThrownBy(generator::saveAndValidateRoundTrip);
		assertThat(temporaryDirectory.resolve("rejected")).doesNotExist();
	}

	@Test
	void noRootsProduceAValidRoundTripResultAndPreserveSaveDirectoryBehavior()
			throws IOException {
		generator = newGenerator("empty");

		var result = generator.saveAndValidateRoundTrip();

		assertThat(result.isValid()).isTrue();
		assertThat(result.diagnostic().getChildren()).isEmpty();
		assertThat(temporaryDirectory.resolve("empty")).isDirectory().isEmptyDirectory();
		assertThat(generator.getResourceSet().getResources()).isEmpty();
	}

	private EMFModelGenerator newGenerator(final String directory) {
		var result = new EMFModelGenerator();
		result.setOutputDirectory(temporaryDirectory.resolve(directory).toString());
		return result;
	}
}
