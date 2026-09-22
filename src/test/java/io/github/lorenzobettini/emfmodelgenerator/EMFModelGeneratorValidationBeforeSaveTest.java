package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEClassExists;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EMFModelGeneratorValidationBeforeSaveTest {

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
	void validationIsDisabledByDefaultAndCanBeToggled() throws IOException {
		var bookFile = generateBookOnly("disabled");

		assertThat(generator.isValidationBeforeSaveEnabled()).isFalse();
		generator.enableValidationBeforeSave();
		assertThat(generator.isValidationBeforeSaveEnabled()).isTrue();
		generator.disableValidationBeforeSave();
		assertThat(generator.isValidationBeforeSaveEnabled()).isFalse();
		generator.save();
		assertThat(bookFile).exists();
	}

	@Test
	void standardValidationPreventsSerializationAndDirectoryCreation() throws IOException {
		var output = temporaryDirectory.resolve("absent");
		var bookFile = generateBookOnly(output);
		generator.enableValidationBeforeSave();

		assertThatExceptionOfType(EMFValidationException.class)
				.isThrownBy(generator::save)
				.satisfies(exception -> assertThat(exception.getResult().isValid()).isFalse());
		assertThat(output).doesNotExist();
		assertThat(bookFile).doesNotExist();
	}

	@Test
	void validBookAndWriterSaveNormally() throws IOException {
		generator = newGenerator(temporaryDirectory.resolve("valid"));
		var library = generator.loadEcoreModel(INPUTS + "/extlibrary.ecore");
		generator.generateFromSeveral(assertEClassExists(library, "Book"),
				assertEClassExists(library, "Writer"));
		generator.enableValidationBeforeSave();

		generator.save();

		assertThat(temporaryDirectory.resolve("valid/extlibrary_Book_1.xmi")).exists();
		assertThat(temporaryDirectory.resolve("valid/extlibrary_Writer_1.xmi")).exists();
	}

	@Test
	void saveOptionsAreAppliedAfterSuccessfulValidation() throws IOException {
		generator = newGenerator(temporaryDirectory.resolve("options"));
		var model = generator.loadEcoreModel(INPUTS + "/simple.ecore");
		generator.generateFrom(assertEClassExists(model, "Person"));
		generator.enableValidationBeforeSave();

		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));

		assertThat(temporaryDirectory.resolve("options/simple_Person_1.xmi"))
				.content().contains("xsi:schemaLocation");
	}

	@Test
	void customValidatorSeesAllRootsBeforeAnyResourceIsWritten() throws IOException {
		generator = newGenerator(temporaryDirectory.resolve("several"));
		var model = generator.loadEcoreModel(INPUTS + "/simple.ecore");
		generator.setNumberOfInstances(2);
		generator.generateFrom(assertEClassExists(model, "Person"));
		var validatedRoots = new AtomicInteger();
		generator.enableValidationBeforeSave(resourceSet -> new StubValidator(StubResult.INVALID,
				validatedRoots, new AtomicBoolean()));

		assertThatExceptionOfType(EMFValidationException.class).isThrownBy(generator::save);
		assertThat(validatedRoots).hasValue(2);
		assertThat(temporaryDirectory.resolve("several")).doesNotExist();
	}

	@Test
	void customValidatorIsClosedOnValidInvalidAndExceptionalPaths() throws IOException {
		generator = newGenerator(temporaryDirectory.resolve("lifecycle"));
		var closed = new AtomicBoolean();
		generator.enableValidationBeforeSave(resourceSet -> new StubValidator(StubResult.VALID,
				new AtomicInteger(), closed));
		generator.save();
		assertThat(closed).isTrue();

		closed.set(false);
		generator.enableValidationBeforeSave(resourceSet -> new StubValidator(StubResult.INVALID,
				new AtomicInteger(), closed));
		assertThatExceptionOfType(EMFValidationException.class).isThrownBy(generator::save);
		assertThat(closed).isTrue();

		closed.set(false);
		generator.enableValidationBeforeSave(resourceSet -> new StubValidator(StubResult.THROWING,
				new AtomicInteger(), closed));
		assertThatIllegalStateException().isThrownBy(generator::save).withMessage("validator failed");
		assertThat(closed).isTrue();
	}

	@Test
	void replacingFactoryAffectsNextSaveAndNullIsRejected() throws IOException {
		generator = newGenerator(temporaryDirectory.resolve("replacement"));
		var firstCalls = new AtomicInteger();
		generator.enableValidationBeforeSave(resourceSet -> {
			firstCalls.incrementAndGet();
			return new StubValidator(StubResult.VALID, new AtomicInteger(), new AtomicBoolean());
		});
		generator.save();

		var secondCalls = new AtomicInteger();
		generator.enableValidationBeforeSave(resourceSet -> {
			secondCalls.incrementAndGet();
			return new StubValidator(StubResult.VALID, new AtomicInteger(), new AtomicBoolean());
		});
		generator.save();

		assertThat(firstCalls).hasValue(1);
		assertThat(secondCalls).hasValue(1);
		assertThatNullPointerException()
				.isThrownBy(() -> generator.enableValidationBeforeSave(null))
				.withMessage("validatorFactory");
	}

	private Path generateBookOnly(final String directory) throws IOException {
		return generateBookOnly(temporaryDirectory.resolve(directory));
	}

	private Path generateBookOnly(final Path output) throws IOException {
		generator = newGenerator(output);
		var library = generator.loadEcoreModel(INPUTS + "/extlibrary.ecore");
		generator.generateFrom(assertEClassExists(library, "Book"));
		return output.resolve("extlibrary_Book_1.xmi");
	}

	private static EMFModelGenerator newGenerator(final Path output) {
		var result = new EMFModelGenerator();
		result.setOutputDirectory(output.toString());
		return result;
	}

	private enum StubResult {
		VALID, INVALID, THROWING
	}

	private static final class StubValidator implements EMFModelValidator {
		private final StubResult result;
		private final AtomicInteger validatedRoots;
		private final AtomicBoolean closed;

		private StubValidator(final StubResult result, final AtomicInteger validatedRoots,
				final AtomicBoolean closed) {
			this.result = result;
			this.validatedRoots = validatedRoots;
			this.closed = closed;
		}

		@Override
		public EMFValidationResult validate(final EObject root) {
			throw new UnsupportedOperationException();
		}

		@Override
		public EMFValidationResult validateAll(final Collection<? extends EObject> roots) {
			validatedRoots.addAndGet(roots.size());
			if (result == StubResult.THROWING) {
				throw new IllegalStateException("validator failed");
			}
			var severity = result == StubResult.VALID ? Diagnostic.OK : Diagnostic.ERROR;
			var kind = result == StubResult.VALID
					? EMFValidationKind.VALID
					: EMFValidationKind.VALIDATION_FAILURE;
			return new EMFValidationResult(new BasicDiagnostic(severity, "test", 0,
					result.name(), null), Diagnostic.ERROR, kind);
		}

		@Override
		public void close() {
			closed.set(true);
		}
	}
}
