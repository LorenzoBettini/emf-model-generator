package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertXMIMatchesExpected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for XMI fixture comparison and missing fixture reporting.
 *
 * @author Lorenzo Bettini
 */
class EMFTestUtilsTest {

	private static final Path EXPECTED_OUTPUTS = Path.of("src/test/resources/expected-outputs");
	private static final String PERSON = "simple_Person_1.xmi";
	private static final String DATA_HOLDER = "datatypes_DataHolder_1.xmi";

	@TempDir
	Path temporaryDirectory;

	@Test
	void missingExpectedFileIsCreatedButFailsForReview() throws IOException {
		var expectedDirectory = temporaryDirectory.resolve("expected/nested");
		var expectedFile = expectedDirectory.resolve("review.xmi");
		var outputDir = EXPECTED_OUTPUTS.toString();
		var expectedDir = expectedDirectory.toString();

		assertThatThrownBy(() -> assertXMIMatchesExpected(outputDir,
				expectedDir, PERSON, "review.xmi"))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("Expected output files did not exist")
				.hasMessageContaining(expectedFile.toAbsolutePath().toString())
				.hasMessageContaining("Please review all these files and commit them")
				.hasMessageContaining("if they are correct");

		assertThat(expectedFile).hasSameBinaryContentAs(EXPECTED_OUTPUTS.resolve(PERSON));
		assertXMIMatchesExpected(EXPECTED_OUTPUTS.toString(), expectedDirectory.toString(),
				PERSON, "review.xmi");
	}

	@Test
	void existingExpectedFileMatchesWithDifferentTimezoneOffsets() throws IOException {
		var generatedFile = temporaryDirectory.resolve("generated.xmi");
		Files.writeString(generatedFile,
				Files.readString(EXPECTED_OUTPUTS.resolve(DATA_HOLDER)).replace("+0100", "-05:00"));

		assertXMIMatchesExpected(temporaryDirectory.toString(), EXPECTED_OUTPUTS.toString(),
				"generated.xmi", DATA_HOLDER);
	}

	@Test
	void multipleFilesAreMatchedWithTheirCorrespondingExpectedNames() throws IOException {
		Files.copy(EXPECTED_OUTPUTS.resolve(PERSON), temporaryDirectory.resolve("person.xmi"));
		Files.copy(EXPECTED_OUTPUTS.resolve(DATA_HOLDER), temporaryDirectory.resolve("data.xmi"));

		assertXMIMatchesExpected(temporaryDirectory.toString(), EXPECTED_OUTPUTS.toString(),
				List.of("person.xmi", "data.xmi"), List.of(PERSON, DATA_HOLDER));
	}

	@Test
	void allMissingExpectedFilesAreReportedBeforeComparingExistingContents() throws IOException {
		var existingFile = temporaryDirectory.resolve("existing.xmi");
		Files.copy(EXPECTED_OUTPUTS.resolve(DATA_HOLDER), existingFile);
		var firstMissing = temporaryDirectory.resolve("nested/person.xmi");
		var secondMissing = temporaryDirectory.resolve("nested/data.xmi");
		var outputDir = EXPECTED_OUTPUTS.toString();
		var expectedDir = temporaryDirectory.toString();
		var generatedFileNames = List.of(PERSON, PERSON, DATA_HOLDER);
		var expectedFileNames = List.of("existing.xmi", "nested/person.xmi", "nested/data.xmi");

		assertThatThrownBy(() -> assertXMIMatchesExpected(outputDir,
				expectedDir, generatedFileNames, expectedFileNames))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("Expected output files did not exist")
				.hasMessageContaining(firstMissing.toAbsolutePath().toString())
				.hasMessageContaining(secondMissing.toAbsolutePath().toString())
				.hasMessageNotContaining(existingFile.toAbsolutePath().toString())
				.hasMessageContaining("Please review all these files and commit them");

		assertThat(firstMissing).hasSameBinaryContentAs(EXPECTED_OUTPUTS.resolve(PERSON));
		assertThat(secondMissing).hasSameBinaryContentAs(EXPECTED_OUTPUTS.resolve(DATA_HOLDER));
		assertThat(existingFile).hasSameBinaryContentAs(EXPECTED_OUTPUTS.resolve(DATA_HOLDER));
	}

	@Test
	void mismatchInLaterFileFailsWithFileNames() throws IOException {
		Files.copy(EXPECTED_OUTPUTS.resolve(PERSON), temporaryDirectory.resolve("person.xmi"));
		Files.copy(EXPECTED_OUTPUTS.resolve(PERSON), temporaryDirectory.resolve("wrong-data.xmi"));
		var outputDir = temporaryDirectory.toString();
		var expectedDir = EXPECTED_OUTPUTS.toString();
		var generatedFileNames = List.of("person.xmi", "wrong-data.xmi");
		var expectedFileNames = List.of(PERSON, DATA_HOLDER);

		assertThatThrownBy(() -> assertXMIMatchesExpected(outputDir,
				expectedDir, generatedFileNames, expectedFileNames))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("wrong-data.xmi")
				.hasMessageContaining(DATA_HOLDER);
	}

	@Test
	void missingGeneratedFileFailsWithoutCreatingExpectedFile() {
		var directory = temporaryDirectory.toString();

		assertThatThrownBy(() -> assertXMIMatchesExpected(directory,
				directory, "missing.xmi", "expected.xmi"))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("missing.xmi");

		assertThat(temporaryDirectory.resolve("expected.xmi")).doesNotExist();
	}

	@Test
	void differentNumbersOfFileNamesFailBeforeCreatingExpectedFiles() {
		var outputDir = EXPECTED_OUTPUTS.toString();
		var expectedDir = temporaryDirectory.toString();
		var generatedFileNames = List.of(PERSON, DATA_HOLDER);
		var expectedFileNames = List.of("only.xmi");

		assertThatThrownBy(() -> assertXMIMatchesExpected(outputDir,
				expectedDir, generatedFileNames, expectedFileNames))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("Expected XMI file names");

		assertThat(temporaryDirectory.resolve("only.xmi")).doesNotExist();
	}

	@Test
	void emptyFileListsFail() {
		var outputDir = EXPECTED_OUTPUTS.toString();
		var expectedDir = temporaryDirectory.toString();
		var emptyFileNames = List.<String>of();

		assertThatThrownBy(() -> assertXMIMatchesExpected(outputDir,
				expectedDir, emptyFileNames, emptyFileNames))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("Generated XMI file names");
	}
}
