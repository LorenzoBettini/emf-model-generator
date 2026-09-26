package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEAttribute;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEClass;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEPackage;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.xml.type.XMLTypePackage;
import org.junit.jupiter.api.Test;

class EMFAttributeSetterXMLTypeTest {

	private record GeneratedXMLTypeValue(
			EDataType dataType, EAttribute attribute, EObject owner, Object value) {}

	@Test
	void shouldGenerateAValidValueForEveryXMLTypeEDataType() {
		final var dataTypes = XMLTypePackage.eINSTANCE.getEClassifiers().stream()
				.filter(EDataType.class::isInstance)
				.map(EDataType.class::cast)
				.toList();

		assertThat(dataTypes).isNotEmpty();
		for (final var dataType : dataTypes) {
			assertThatCode(() -> assertCanGenerateAndRoundTrip(dataType))
					.as("XMLType %s", dataType.getName())
					.doesNotThrowAnyException();
		}
	}

	@Test
	void shouldRespectConstrainedIntegerSemantics() {
		assertThat(asBigInteger(generate(XMLTypePackage.Literals.POSITIVE_INTEGER).value()))
				.isPositive();
		assertThat(asBigInteger(generate(XMLTypePackage.Literals.NEGATIVE_INTEGER).value()))
				.isNegative();
		assertThat(asBigInteger(generate(XMLTypePackage.Literals.NON_NEGATIVE_INTEGER).value()))
				.isNotNegative();

		final var nonPositiveValues = generateTwice(XMLTypePackage.Literals.NON_POSITIVE_INTEGER);
		assertThat(asBigInteger(nonPositiveValues.get(0))).isZero();
		assertThat(asBigInteger(nonPositiveValues.get(1))).isNegative();
	}

	@Test
	void shouldAlternateXMLBooleanValues() {
		assertThat(generateValues(XMLTypePackage.Literals.BOOLEAN, 3))
				.containsExactly(true, false, true);
	}

	@Test
	void shouldPreserveDeterministicCounterValuesForXMLTypes() {
		assertThat(generateValues(XMLTypePackage.Literals.ANY_URI, 2))
				.containsExactly(
						"https://example.org/resource/1",
						"https://example.org/resource/2");
		assertThat(generateValues(XMLTypePackage.Literals.DOUBLE, 2))
				.containsExactly(20.5, 21.5);
		assertThat(generateValues(XMLTypePackage.Literals.INT, 2))
				.containsExactly(20, 21);
		assertThat(generateValues(XMLTypePackage.Literals.SHORT, 2))
				.containsExactly((short) 20, (short) 21);
		assertThat(generateValues(XMLTypePackage.Literals.BYTE, 2))
				.containsExactly((byte) 20, (byte) 21);
		assertThat(generateValues(XMLTypePackage.Literals.NMTOKEN, 2))
				.containsExactly("token1", "token2");
	}

	@Test
	void shouldGenerateNonNegativeUnsignedValues() {
		final var unsignedTypes = List.of(
				XMLTypePackage.Literals.UNSIGNED_LONG,
				XMLTypePackage.Literals.UNSIGNED_INT,
				XMLTypePackage.Literals.UNSIGNED_INT_OBJECT,
				XMLTypePackage.Literals.UNSIGNED_SHORT,
				XMLTypePackage.Literals.UNSIGNED_SHORT_OBJECT,
				XMLTypePackage.Literals.UNSIGNED_BYTE,
				XMLTypePackage.Literals.UNSIGNED_BYTE_OBJECT);

		assertThat(unsignedTypes).isNotEmpty();
		for (final var dataType : unsignedTypes) {
			final var value = generate(dataType).value();
			assertThat(asBigInteger(value)).as(dataType.getName()).isNotNegative();
		}
	}

	@Test
	void shouldGenerateValidConstrainedStrings() {
		final var language = generate(XMLTypePackage.Literals.LANGUAGE);
		assertThat(language.value()).isEqualTo("en");
		assertValidDatatypeValue(language.dataType(), language.value());

		final var ncName = generate(XMLTypePackage.Literals.NC_NAME);
		assertThat(ncName.value()).isEqualTo("name1");
		assertValidDatatypeValue(ncName.dataType(), ncName.value());
	}

	@Test
	void shouldGenerateValidNonEmptyListValues() {
		final var listTypes = List.of(
				XMLTypePackage.Literals.ENTITIES,
				XMLTypePackage.Literals.ENTITIES_BASE,
				XMLTypePackage.Literals.IDREFS,
				XMLTypePackage.Literals.IDREFS_BASE,
				XMLTypePackage.Literals.NMTOKENS,
				XMLTypePackage.Literals.NMTOKENS_BASE);

		assertThat(listTypes).isNotEmpty();
		for (final var dataType : listTypes) {
			final var generated = generate(dataType);
			assertThat((List<?>) generated.value()).as(dataType.getName()).isNotEmpty();
			assertValidDatatypeValue(dataType, generated.value());
		}
	}

	@Test
	void shouldGenerateTheCorrectCalendarKinds() {
		final var expectedCalendarKinds = Map.of(
				XMLTypePackage.Literals.DATE, DatatypeConstants.DATE,
				XMLTypePackage.Literals.DATE_TIME, DatatypeConstants.DATETIME,
				XMLTypePackage.Literals.TIME, DatatypeConstants.TIME,
				XMLTypePackage.Literals.GDAY, DatatypeConstants.GDAY,
				XMLTypePackage.Literals.GMONTH, DatatypeConstants.GMONTH,
				XMLTypePackage.Literals.GMONTH_DAY, DatatypeConstants.GMONTHDAY,
				XMLTypePackage.Literals.GYEAR, DatatypeConstants.GYEAR,
				XMLTypePackage.Literals.GYEAR_MONTH, DatatypeConstants.GYEARMONTH);

		for (final var entry : expectedCalendarKinds.entrySet()) {
			final var dataType = entry.getKey();
			final var value = (XMLGregorianCalendar) generate(dataType).value();
			assertThat(value.getXMLSchemaType())
					.as(dataType.getName())
					.isEqualTo(entry.getValue());
			assertValidDatatypeValue(dataType, value);
		}
	}

	@Test
	void shouldGenerateDurationQNamesAndBinaryValues() {
		final var duration = generate(XMLTypePackage.Literals.DURATION);
		assertThat(duration.value()).isInstanceOf(Duration.class).hasToString("P1D");

		for (final var dataType : List.of(
				XMLTypePackage.Literals.QNAME, XMLTypePackage.Literals.NOTATION)) {
			final var qName = (QName) generate(dataType).value();
			assertThat(qName.getLocalPart()).as(dataType.getName()).isEqualTo("name1");
			assertThat(qName.getPrefix()).as(dataType.getName()).isEmpty();
			assertValidDatatypeValue(dataType, qName);
		}

		for (final var dataType : List.of(
				XMLTypePackage.Literals.BASE64_BINARY, XMLTypePackage.Literals.HEX_BINARY)) {
			assertThat((byte[]) generate(dataType).value())
					.as(dataType.getName())
					.containsExactly(1, 2);
		}
	}

	@Test
	void shouldRecognizeEquivalentDynamicXMLTypePackageByNamespaceAndName() {
		final var dynamicPackage = createEPackage(
				"xmltype", XMLTypePackage.eNS_URI, "xmltype");
		final var dynamicPositiveInteger = EcoreFactory.eINSTANCE.createEDataType();
		dynamicPositiveInteger.setName(XMLTypePackage.Literals.POSITIVE_INTEGER.getName());
		dynamicPositiveInteger.setInstanceClassName(BigInteger.class.getName());
		dynamicPackage.getEClassifiers().add(dynamicPositiveInteger);

		assertThat(generate(dynamicPackage, dynamicPositiveInteger).value())
				.isEqualTo(BigInteger.ONE);
	}

	@Test
	void shouldNotTreatNamesFromOtherPackagesAsXMLTypes() {
		final var customPackage = createEPackage("custom", "http://example.org/custom", "custom");
		final var customLanguage = EcoreFactory.eINSTANCE.createEDataType();
		customLanguage.setName(XMLTypePackage.Literals.LANGUAGE.getName());
		customLanguage.setInstanceClassName(String.class.getName());
		customPackage.getEClassifiers().add(customLanguage);

		assertThat(generate(customPackage, customLanguage).value())
				.isEqualTo("TestClass_value_1");
	}

	@Test
	void shouldFallBackForUnknownDatatypeInXMLTypeNamespace() {
		final var dynamicPackage = createEPackage(
				"xmltype", XMLTypePackage.eNS_URI, "xmltype");
		final var customString = EcoreFactory.eINSTANCE.createEDataType();
		customString.setName("CustomString");
		customString.setInstanceClassName(String.class.getName());
		dynamicPackage.getEClassifiers().add(customString);

		assertThat(generate(dynamicPackage, customString).value())
				.isEqualTo("TestClass_value_1");
	}

	@Test
	void shouldRejectUnsupportedXMLTypeClassifierID() {
		final var unsupportedPackage = createEPackage("unsupported", "test:unsupported", "test");
		for (int i = 0; i < 100; i++) {
			final var filler = EcoreFactory.eINSTANCE.createEDataType();
			filler.setName("Filler" + i);
			unsupportedPackage.getEClassifiers().add(filler);
		}
		final var unsupported = EcoreFactory.eINSTANCE.createEDataType();
		unsupported.setName("Unsupported");
		unsupportedPackage.getEClassifiers().add(unsupported);
		final var fixture = createFixture(unsupportedPackage, unsupported);
		final var setter = new EMFAttributeSetter();
		final var owner = fixture.owner();
		final var attribute = fixture.attribute();

		assertThatThrownBy(() -> setter.generateXMLTypeLexicalValue(
				owner, attribute, unsupported, 0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Unsupported XMLType EDataType: Unsupported");
	}

	private void assertCanGenerateAndRoundTrip(final EDataType dataType) {
		final var generated = generate(dataType);
		assertThat(generated.owner().eIsSet(generated.attribute())).isTrue();
		assertThat(generated.value()).isNotNull();
		assertThat(dataType.isInstance(generated.value())).isTrue();

		final var factory = XMLTypePackage.eINSTANCE.getEFactoryInstance();
		final var lexicalValue = factory.convertToString(dataType, generated.value());
		assertThat(lexicalValue).isNotNull();
		final var reparsedValue = factory.createFromString(dataType, lexicalValue);
		assertThat(reparsedValue).isNotNull();
		assertThat(dataType.isInstance(reparsedValue)).isTrue();

		assertValidDatatypeValue(dataType, generated.value());
		assertThat(Diagnostician.INSTANCE.validate(generated.owner()).getSeverity())
				.isEqualTo(Diagnostic.OK);
	}

	private void assertValidDatatypeValue(final EDataType dataType, final Object value) {
		final var diagnostic = new BasicDiagnostic();
		final var validator = EValidator.Registry.INSTANCE
				.getEValidator(XMLTypePackage.eINSTANCE);
		assertThat(validator.validate(dataType, value, diagnostic, new HashMap<>()))
				.as(dataType.getName())
				.isTrue();
		assertThat(diagnostic.getSeverity()).as(dataType.getName()).isEqualTo(Diagnostic.OK);
	}

	private GeneratedXMLTypeValue generate(final EDataType dataType) {
		return generate(createEPackage("test", "test:" + dataType.getName(), "test"), dataType);
	}

	private GeneratedXMLTypeValue generate(
			final EPackage ePackage, final EDataType dataType) {
		final var fixture = createFixture(ePackage, dataType);
		new EMFAttributeSetter().setAttribute(fixture.owner(), fixture.attribute());
		return new GeneratedXMLTypeValue(
				dataType, fixture.attribute(), fixture.owner(), fixture.owner().eGet(fixture.attribute()));
	}

	private List<Object> generateTwice(final EDataType dataType) {
		return generateValues(dataType, 2);
	}

	private List<Object> generateValues(final EDataType dataType, final int count) {
		final var fixture = createFixture(
				createEPackage("test", "test:" + dataType.getName(), "test"), dataType);
		final var setter = new EMFAttributeSetter();
		final var values = new java.util.ArrayList<>();
		for (int i = 0; i < count; i++) {
			final var owner = createInstance(fixture.owner().eClass());
			setter.setAttribute(owner, fixture.attribute());
			values.add(owner.eGet(fixture.attribute()));
		}
		return values;
	}

	private GeneratedXMLTypeValue createFixture(
			final EPackage ePackage, final EDataType dataType) {
		final var testClass = createEClass(ePackage, "TestClass");
		final var attribute = createEAttribute(testClass, "value", dataType, 1, 1);
		final var owner = createInstance(testClass);
		return new GeneratedXMLTypeValue(dataType, attribute, owner, null);
	}

	private BigInteger asBigInteger(final Object value) {
		return new BigInteger(value.toString());
	}
}
