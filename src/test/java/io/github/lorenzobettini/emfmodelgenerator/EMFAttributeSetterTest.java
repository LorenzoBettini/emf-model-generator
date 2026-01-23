package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEAttribute;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEClass;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createEPackage;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.createInstance;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.validateModel;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EMFAttributeSetterTest {

	private EMFAttributeSetter setter;
	private EPackage testPackage;
	private EClass testClass;

	@BeforeEach
	void setUp() {
		setter = new EMFAttributeSetter();
		
		testPackage = createEPackage("test", "http://test", "test");
		testClass = createEClass(testPackage, "TestClass");
	}

	@Test
	void testSetDefaultIntValue() {
		setter.setDefaultIntValue(100);
		assertThat(setter.getDefaultIntValue()).isEqualTo(100);
	}

	@Test
	void testSetDefaultDoubleValue() {
		setter.setDefaultDoubleValue(50.5);
		assertThat(setter.getDefaultDoubleValue()).isEqualTo(50.5);
	}

	@Test
	void testSetAttributeValue_ShouldSkipAlreadySetAttribute() {
		final EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName("testString");
		attribute.setEType(EcorePackage.Literals.ESTRING);
		testClass.getEStructuralFeatures().add(attribute);
		
		final EObject instance = createInstance(testClass);
		
		// Set the attribute manually first
		instance.eSet(attribute, "AlreadySet");
		
		// Try to set it again - should skip due to shouldSetFeature returning false
		setter.setAttribute(instance, attribute);
		
		// Value should remain unchanged
		assertThat(instance.eGet(attribute)).isEqualTo("AlreadySet");
	}

	@Test
	void testSetAttributeValue_SingleValuedString() {
		final EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName("testString");
		attribute.setEType(EcorePackage.Literals.ESTRING);
		testClass.getEStructuralFeatures().add(attribute);
		
		final EObject instance = createInstance(testClass);
		setter.setAttribute(instance, attribute);
		
		assertThat(instance.eGet(attribute)).isEqualTo("TestClass_testString_1");
		validateModel(instance);
	}

	@Test
	void testSetAttributeValue_MultiValuedString() {
		final EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName("testMultiString");
		attribute.setEType(EcorePackage.Literals.ESTRING);
		attribute.setUpperBound(-1); // multi-valued
		testClass.getEStructuralFeatures().add(attribute);
		
		final EObject instance = createInstance(testClass);
		setter.setAttribute(instance, attribute);
		
		final List<Object> values = EMFUtils.getAsList(instance, attribute);
		assertThat(values).containsExactly("TestClass_testMultiString_1", "TestClass_testMultiString_2");
		validateModel(instance);
	}

	@Test
	void testSetAttributeValue_MultiValuedWithCustomCount() {
		setter.setDefaultMaxCount(3);
		
		final EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName("testMultiString");
		attribute.setEType(EcorePackage.Literals.ESTRING);
		attribute.setUpperBound(-1); // multi-valued
		testClass.getEStructuralFeatures().add(attribute);
		
		final EObject instance = createInstance(testClass);
		setter.setAttribute(instance, attribute);
		
		final List<Object> values = EMFUtils.getAsList(instance, attribute);
		assertThat(values).containsExactly("TestClass_testMultiString_1", "TestClass_testMultiString_2", "TestClass_testMultiString_3");
		validateModel(instance);
	}

	@Test
	void testGenerateAttributeValue_EString() {
		final EAttribute attribute = createEAttribute(testClass, "name", EcorePackage.Literals.ESTRING);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo("TestClass_name_1");
		
		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo("TestClass_name_2");
	}

	@Test
	void testGenerateAttributeValue_UsesOwnerClassName() {
		// Test that the generated string value uses the owner's EClass name,
		// not the attribute's declaring class
		// Create an attribute that's NOT added to the class (not using utility method here)
		final EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName("myAttr");
		attribute.setEType(EcorePackage.Literals.ESTRING);
		
		// Attribute has no containing class
		assertThat(attribute.getEContainingClass()).isNull();
		
		// Should generate value using owner's EClass name (TestClass)
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo("TestClass_myAttr_1");
		
		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo("TestClass_myAttr_2");
	}

	@Test
	void testGenerateAttributeValue_CustomStringType() {
		// Create a custom String data type
		final EDataType stringType = EcoreFactory.eINSTANCE.createEDataType();
		stringType.setName("String");
		stringType.setInstanceClassName("java.lang.String");
		
		final EAttribute attribute = createEAttribute(testClass, "customString", stringType);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo("TestClass_customString_1");
	}

	@Test
	void testGenerateAttributeValue_EInt() {
		final EAttribute attribute = createEAttribute(testClass, "age", EcorePackage.Literals.EINT);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(20);
		
		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo(21);
	}

	@Test
	void testGenerateAttributeValue_CustomIntType() {
		// Create a custom int data type
		final EDataType intType = EcoreFactory.eINSTANCE.createEDataType();
		intType.setName("int");
		intType.setInstanceClassName("java.lang.Integer");
		
		final EAttribute attribute = createEAttribute(testClass, "customInt", intType);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(20);
	}

	@Test
	void testGenerateAttributeValue_CustomIntegerType() {
		// Create a custom Integer data type
		final EDataType integerType = EcoreFactory.eINSTANCE.createEDataType();
		integerType.setName("Integer");
		integerType.setInstanceClassName("java.lang.Integer");
		
		final EAttribute attribute = createEAttribute(testClass, "customInteger", integerType);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(20);
	}

	@Test
	void testGenerateAttributeValue_WithCustomDefaultIntValue() {
		setter.setDefaultIntValue(100);
		
		final EAttribute attribute = createEAttribute(testClass, "age", EcorePackage.Literals.EINT);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(100);
		
		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo(101);
	}

	@Test
	void testGenerateAttributeValue_EBoolean() {
		final EAttribute attribute = createEAttribute(testClass, "active", EcorePackage.Literals.EBOOLEAN);
		
		final Object value0 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value0).isEqualTo(true);
		
		final Object value1 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value1).isEqualTo(false);
		
		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo(true);
	}

	@Test
	void testGenerateAttributeValue_CustomBooleanType() {
		// Create a custom boolean data type
		final EDataType boolType = EcoreFactory.eINSTANCE.createEDataType();
		boolType.setName("boolean");
		boolType.setInstanceClassName("java.lang.Boolean");
		
		final EAttribute attribute = createEAttribute(testClass, "customBool", boolType);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(true);
	}

	@Test
	void testGenerateAttributeValue_CustomBooleanBoxedType() {
		// Create a custom Boolean data type
		final EDataType boolType = EcoreFactory.eINSTANCE.createEDataType();
		boolType.setName("Boolean");
		boolType.setInstanceClassName("java.lang.Boolean");
		
		final EAttribute attribute = createEAttribute(testClass, "customBoolean", boolType);
		
		// First call returns counter=0 (true), so we need another call to get false
		setter.generateAttributeValue(createInstance(testClass), attribute); // counter=0→1, returns true
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute); // counter=1→2, returns false
		assertThat(value).isEqualTo(false);
	}

	@Test
	void testGenerateAttributeValue_EDouble() {
		final EAttribute attribute = createEAttribute(testClass, "price", EcorePackage.Literals.EDOUBLE);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(20.5);
		
		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo(21.5);
	}

	@Test
	void testGenerateAttributeValue_CustomDoubleType() {
		// Create a custom double data type
		final EDataType doubleType = EcoreFactory.eINSTANCE.createEDataType();
		doubleType.setName("double");
		doubleType.setInstanceClassName("java.lang.Double");
		
		final EAttribute attribute = createEAttribute(testClass, "customDouble", doubleType);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(20.5);
	}

	@Test
	void testGenerateAttributeValue_CustomDoubleBoxedType() {
		// Create a custom Double data type
		final EDataType doubleType = EcoreFactory.eINSTANCE.createEDataType();
		doubleType.setName("Double");
		doubleType.setInstanceClassName("java.lang.Double");
		
		final EAttribute attribute = createEAttribute(testClass, "customDoubleBoxed", doubleType);
		
		// Call twice to get counter=2
		setter.generateAttributeValue(createInstance(testClass), attribute); // counter=0→1, returns 20.5
		setter.generateAttributeValue(createInstance(testClass), attribute); // counter=1→2, returns 21.5
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute); // counter=2→3, returns 22.5
		assertThat(value).isEqualTo(22.5);
	}

	@Test
	void testGenerateAttributeValue_WithCustomDefaultDoubleValue() {
		setter.setDefaultDoubleValue(100.5);
		
		final EAttribute attribute = createEAttribute(testClass, "price", EcorePackage.Literals.EDOUBLE);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(100.5);
		
		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo(101.5);
	}

	@Test
	void testGenerateAttributeValue_EFloat() {
		final EAttribute attribute = createEAttribute(testClass, "weight", EcorePackage.Literals.EFLOAT);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(20.5f);
		
		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo(21.5f);
	}

	@Test
	void testGenerateAttributeValue_CustomFloatType() {
		// Create a custom float data type
		final EDataType floatType = EcoreFactory.eINSTANCE.createEDataType();
		floatType.setName("float");
		floatType.setInstanceClassName("java.lang.Float");
		
		final EAttribute attribute = createEAttribute(testClass, "customFloat", floatType);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(20.5f);
	}

	@Test
	void testGenerateAttributeValue_CustomFloatBoxedType() {
		// Create a custom Float data type
		final EDataType floatType = EcoreFactory.eINSTANCE.createEDataType();
		floatType.setName("Float");
		floatType.setInstanceClassName("java.lang.Float");
		
		final EAttribute attribute = createEAttribute(testClass, "customFloatBoxed", floatType);
		
		// Call once to get counter=1
		setter.generateAttributeValue(createInstance(testClass), attribute); // counter=0→1, returns 20.5f
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute); // counter=1→2, returns 21.5f
		assertThat(value).isEqualTo(21.5f);
	}

	@Test
	void testGenerateAttributeValue_ELong() {
		final EAttribute attribute = createEAttribute(testClass, "id", EcorePackage.Literals.ELONG);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(20L);
		
		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo(21L);
	}

	@Test
	void testGenerateAttributeValue_CustomLongType() {
		// Create a custom long data type
		final EDataType longType = EcoreFactory.eINSTANCE.createEDataType();
		longType.setName("long");
		longType.setInstanceClassName("java.lang.Long");
		
		final EAttribute attribute = createEAttribute(testClass, "customLong", longType);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(20L);
	}

	@Test
	void testGenerateAttributeValue_CustomLongBoxedType() {
		// Create a custom Long data type
		final EDataType longType = EcoreFactory.eINSTANCE.createEDataType();
		longType.setName("Long");
		longType.setInstanceClassName("java.lang.Long");
		
		final EAttribute attribute = createEAttribute(testClass, "customLongBoxed", longType);
		
		// Call twice to get counter=2
		setter.generateAttributeValue(createInstance(testClass), attribute); // counter=0→1, returns 20L
		setter.generateAttributeValue(createInstance(testClass), attribute); // counter=1→2, returns 21L
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute); // counter=2→3, returns 22L
		assertThat(value).isEqualTo(22L);
	}

	@Test
	void testGenerateAttributeValue_EShort() {
		final EAttribute attribute = createEAttribute(testClass, "count", EcorePackage.Literals.ESHORT);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo((short) 20);
		
		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo((short) 21);
	}

	@Test
	void testGenerateAttributeValue_CustomShortType() {
		// Create a custom short data type
		final EDataType shortType = EcoreFactory.eINSTANCE.createEDataType();
		shortType.setName("short");
		shortType.setInstanceClassName("java.lang.Short");
		
		final EAttribute attribute = createEAttribute(testClass, "customShort", shortType);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo((short) 20);
	}

	@Test
	void testGenerateAttributeValue_CustomShortBoxedType() {
		// Create a custom Short data type
		final EDataType shortType = EcoreFactory.eINSTANCE.createEDataType();
		shortType.setName("Short");
		shortType.setInstanceClassName("java.lang.Short");
		
		final EAttribute attribute = createEAttribute(testClass, "customShortBoxed", shortType);
		
		// Call once to get counter=1
		setter.generateAttributeValue(createInstance(testClass), attribute); // counter=0→1, returns 20
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute); // counter=1→2, returns 21
		assertThat(value).isEqualTo((short) 21);
	}

	@Test
	void testGenerateAttributeValue_EByte() {
		final EAttribute attribute = createEAttribute(testClass, "flags", EcorePackage.Literals.EBYTE);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo((byte) 20);
		
		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo((byte) 21);
	}

	@Test
	void testGenerateAttributeValue_CustomByteType() {
		// Create a custom byte data type
		final EDataType byteType = EcoreFactory.eINSTANCE.createEDataType();
		byteType.setName("byte");
		byteType.setInstanceClassName("java.lang.Byte");
		
		final EAttribute attribute = createEAttribute(testClass, "customByte", byteType);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo((byte) 20);
	}

	@Test
	void testGenerateAttributeValue_CustomByteBoxedType() {
		// Create a custom Byte data type
		final EDataType byteType = EcoreFactory.eINSTANCE.createEDataType();
		byteType.setName("Byte");
		byteType.setInstanceClassName("java.lang.Byte");
		
		final EAttribute attribute = createEAttribute(testClass, "customByteBoxed", byteType);
		
		// Call twice to get counter=2
		setter.generateAttributeValue(createInstance(testClass), attribute); // counter=0→1, returns 20
		setter.generateAttributeValue(createInstance(testClass), attribute); // counter=1→2, returns 21
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute); // counter=2→3, returns 22
		assertThat(value).isEqualTo((byte) 22);
	}

	@Test
	void testGenerateAttributeValue_EChar() {
		final EAttribute attribute = createEAttribute(testClass, "initial", EcorePackage.Literals.ECHAR);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo('A');
		
		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo('B');
		
		final Object value3 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value3).isEqualTo('C');
	}

	@Test
	void testGenerateAttributeValue_CustomCharType() {
		// Create a custom char data type
		final EDataType charType = EcoreFactory.eINSTANCE.createEDataType();
		charType.setName("char");
		charType.setInstanceClassName("java.lang.Character");
		
		final EAttribute attribute = createEAttribute(testClass, "customChar", charType);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo('A');
	}

	@Test
	void testGenerateAttributeValue_CustomCharacterType() {
		// Create a custom Character data type
		final EDataType charType = EcoreFactory.eINSTANCE.createEDataType();
		charType.setName("Character");
		charType.setInstanceClassName("java.lang.Character");
		
		final EAttribute attribute = createEAttribute(testClass, "customCharacter", charType);
		
		// Call once to get counter=1
		setter.generateAttributeValue(createInstance(testClass), attribute); // counter=0→1, returns 'A'
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute); // counter=1→2, returns 'B'
		assertThat(value).isEqualTo('B');
	}

	@Test
	void testGenerateAttributeValue_EDate() {
		final EAttribute attribute = createEAttribute(testClass, "birthDate", EcorePackage.Literals.EDATE);
		
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isInstanceOf(java.util.Date.class);
		
		final java.util.Calendar cal = java.util.Calendar.getInstance();
		cal.setTime((java.util.Date) value);
		assertThat(cal.get(java.util.Calendar.YEAR)).isEqualTo(2025);
		assertThat(cal.get(java.util.Calendar.MONTH)).isZero(); // January
		assertThat(cal.get(java.util.Calendar.DAY_OF_MONTH)).isEqualTo(1);
		
		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isInstanceOf(java.util.Date.class);
		
		final java.util.Calendar cal2 = java.util.Calendar.getInstance();
		cal2.setTime((java.util.Date) value2);
		assertThat(cal2.get(java.util.Calendar.YEAR)).isEqualTo(2025);
		assertThat(cal2.get(java.util.Calendar.MONTH)).isZero(); // January
		assertThat(cal2.get(java.util.Calendar.DAY_OF_MONTH)).isEqualTo(2);
	}

	@Test
	void testGenerateAttributeValue_UnknownTypeReturnsDefaultValue() {
		// Create a custom unknown data type
		final EDataType unknownType = EcoreFactory.eINSTANCE.createEDataType();
		unknownType.setName("UnknownType");
		unknownType.setInstanceClassName("java.lang.Object");
		
		// Create an attribute with unknown type that returns null
		final EAttribute attribute = createEAttribute(testClass, "unknown", unknownType);
		
		// For unknown types, getDefaultValue() returns null
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isNull();
	}

	@Test
	void testGenerateAttributeValue_UnknownTypeWithNonNullDefaultValue() {
		// Create a custom EDataType (not in Ecore Literals) with a default value
		// This tests the else branch that returns attribute.getDefaultValue(), which is not null
		final EDataType customType = EcoreFactory.eINSTANCE.createEDataType();
		customType.setName("CustomStringType");
		// Set instanceClassName so EMF knows how to convert the defaultValueLiteral
		customType.setInstanceClassName("java.lang.String");
		testPackage.getEClassifiers().add(customType);
		
		final EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName("customAttr");
		attribute.setEType(customType);
		attribute.setDefaultValueLiteral("defaultStringValue");
		testClass.getEStructuralFeatures().add(attribute);
		
		// This should return the non-null default value
		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isNotNull()
			.isInstanceOf(String.class)
			.isEqualTo("defaultStringValue");
	}

	@Test
	void testSetAttributeValue_SingleValuedWithNullValue() {
		// Create a custom unknown data type that returns null
		final EDataType unknownType = EcoreFactory.eINSTANCE.createEDataType();
		unknownType.setName("UnknownType");
		unknownType.setInstanceClassName("java.lang.Object");
		
		final EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName("unknown");
		attribute.setEType(unknownType);
		testClass.getEStructuralFeatures().add(attribute);
		
		final EObject instance = createInstance(testClass);
		setter.setAttribute(instance, attribute);
		
		// Value should remain null because generateAttributeValue returns null
		assertThat(instance.eGet(attribute)).isNull();
	}

	@Test
	void testSetAttributeValue_MultiValuedWithNullValue() {
		// Create a custom unknown data type that returns null
		final EDataType unknownType = EcoreFactory.eINSTANCE.createEDataType();
		unknownType.setName("UnknownType");
		unknownType.setInstanceClassName("java.lang.Object");
		
		final EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName("unknownMulti");
		attribute.setEType(unknownType);
		attribute.setUpperBound(-1); // multi-valued
		testClass.getEStructuralFeatures().add(attribute);
		
		final EObject instance = createInstance(testClass);
		setter.setAttribute(instance, attribute);
		
		final List<Object> values = EMFUtils.getAsList(instance, attribute);
		assertThat(values).isEmpty();
	}

	@Test
	void testSetAttributeValue_MultiValuedWithBoundsIntegration() {
		// Integration test to verify that EMFUtils.getEffectiveCount is properly called
		// The detailed bounds logic is fully tested in EMFUtilsTest
		setter.setDefaultMaxCount(10);
		
		final EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName("testBounds");
		attribute.setEType(EcorePackage.Literals.ESTRING);
		attribute.setLowerBound(2);
		attribute.setUpperBound(4);
		testClass.getEStructuralFeatures().add(attribute);
		
		final EObject instance = createInstance(testClass);
		setter.setAttribute(instance, attribute);
		
		final List<Object> values = EMFUtils.getAsList(instance, attribute);
		assertThat(values).containsExactly("TestClass_testBounds_1", "TestClass_testBounds_2", "TestClass_testBounds_3", "TestClass_testBounds_4");
		validateModel(instance);
	}

	@Test
	void testSetAttributeValue_SingleValuedEnum() {
		final var statusEnum = EcoreFactory.eINSTANCE.createEEnum();
		statusEnum.setName("Status");
		testPackage.getEClassifiers().add(statusEnum);
		
		final var active = EcoreFactory.eINSTANCE.createEEnumLiteral();
		active.setName("ACTIVE");
		active.setValue(0);
		statusEnum.getELiterals().add(active);
		
		final var inactive = EcoreFactory.eINSTANCE.createEEnumLiteral();
		inactive.setName("INACTIVE");
		inactive.setValue(1);
		statusEnum.getELiterals().add(inactive);
		
		final EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName("status");
		attribute.setEType(statusEnum);
		testClass.getEStructuralFeatures().add(attribute);
		
		final EObject instance = createInstance(testClass);
		setter.setAttribute(instance, attribute);
		
		assertThat(instance.eGet(attribute)).isEqualTo(active.getInstance());
		validateModel(instance);
	}

	@Test
	void testSetAttributeValue_SingleValuedEnum_RoundRobin() {
		final var statusEnum = EcoreFactory.eINSTANCE.createEEnum();
		statusEnum.setName("Status");
		testPackage.getEClassifiers().add(statusEnum);
		
		final var active = EcoreFactory.eINSTANCE.createEEnumLiteral();
		active.setName("ACTIVE");
		active.setValue(0);
		statusEnum.getELiterals().add(active);
		
		final var inactive = EcoreFactory.eINSTANCE.createEEnumLiteral();
		inactive.setName("INACTIVE");
		inactive.setValue(1);
		statusEnum.getELiterals().add(inactive);
		
		final var pending = EcoreFactory.eINSTANCE.createEEnumLiteral();
		pending.setName("PENDING");
		pending.setValue(2);
		statusEnum.getELiterals().add(pending);
		
		final EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName("status");
		attribute.setEType(statusEnum);
		testClass.getEStructuralFeatures().add(attribute);
		
		final EObject instance1 = createInstance(testClass);
		final EObject instance2 = createInstance(testClass);
		final EObject instance3 = createInstance(testClass);
		final EObject instance4 = createInstance(testClass);
		
		setter.setAttribute(instance1, attribute);
		setter.setAttribute(instance2, attribute);
		setter.setAttribute(instance3, attribute);
		setter.setAttribute(instance4, attribute);
		
		assertThat(instance1.eGet(attribute)).isEqualTo(active.getInstance());
		assertThat(instance2.eGet(attribute)).isEqualTo(inactive.getInstance());
		assertThat(instance3.eGet(attribute)).isEqualTo(pending.getInstance());
		assertThat(instance4.eGet(attribute)).isEqualTo(active.getInstance()); // Wrapped around
		validateModel(instance1);
		validateModel(instance2);
		validateModel(instance3);
		validateModel(instance4);
	}

	@Test
	void testSetAttributeValue_MultiValuedEnum() {
		final var statusEnum = EcoreFactory.eINSTANCE.createEEnum();
		statusEnum.setName("Status");
		testPackage.getEClassifiers().add(statusEnum);
		
		final var active = EcoreFactory.eINSTANCE.createEEnumLiteral();
		active.setName("ACTIVE");
		active.setValue(0);
		statusEnum.getELiterals().add(active);
		
		final var inactive = EcoreFactory.eINSTANCE.createEEnumLiteral();
		inactive.setName("INACTIVE");
		inactive.setValue(1);
		statusEnum.getELiterals().add(inactive);
		
		final EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName("statuses");
		attribute.setEType(statusEnum);
		attribute.setUpperBound(-1); // Multi-valued
		testClass.getEStructuralFeatures().add(attribute);
		
		final EObject instance = createInstance(testClass);
		setter.setAttribute(instance, attribute);
		
		final List<Object> values = EMFUtils.getAsList(instance, attribute);
		assertThat(values).containsExactly(active.getInstance(), inactive.getInstance());
		validateModel(instance);
	}

	@Test
	void testSetAttributeValue_MultiValuedEnum_RoundRobinAcrossMultipleValues() {
		final var priorityEnum = EcoreFactory.eINSTANCE.createEEnum();
		priorityEnum.setName("Priority");
		testPackage.getEClassifiers().add(priorityEnum);
		
		final var low = EcoreFactory.eINSTANCE.createEEnumLiteral();
		low.setName("LOW");
		low.setValue(0);
		priorityEnum.getELiterals().add(low);
		
		final var medium = EcoreFactory.eINSTANCE.createEEnumLiteral();
		medium.setName("MEDIUM");
		medium.setValue(1);
		priorityEnum.getELiterals().add(medium);
		
		final var high = EcoreFactory.eINSTANCE.createEEnumLiteral();
		high.setName("HIGH");
		high.setValue(2);
		priorityEnum.getELiterals().add(high);
		
		final EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName("priorities");
		attribute.setEType(priorityEnum);
		attribute.setUpperBound(-1); // Multi-valued
		testClass.getEStructuralFeatures().add(attribute);
		
		setter.setDefaultMaxCount(3);
		
		final EObject instance = createInstance(testClass);
		setter.setAttribute(instance, attribute);
		
		final List<Object> values = EMFUtils.getAsList(instance, attribute);
		assertThat(values).containsExactly(low.getInstance(), medium.getInstance(), high.getInstance());
		validateModel(instance);
	}

	@Test
	void testSetAttributeValue_EnumWithNoLiterals() {
		final var emptyEnum = EcoreFactory.eINSTANCE.createEEnum();
		emptyEnum.setName("EmptyEnum");
		testPackage.getEClassifiers().add(emptyEnum);
		
		final EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName("emptyValue");
		attribute.setEType(emptyEnum);
		testClass.getEStructuralFeatures().add(attribute);
		
		final EObject instance = createInstance(testClass);
		setter.setAttribute(instance, attribute);
		
		assertThat(instance.eGet(attribute)).isNull();
	}

	@Test
	void testSetAttributeValue_EnumWithSingleLiteral() {
		final var singleEnum = EcoreFactory.eINSTANCE.createEEnum();
		singleEnum.setName("SingleEnum");
		testPackage.getEClassifiers().add(singleEnum);
		
		final var onlyLiteral = EcoreFactory.eINSTANCE.createEEnumLiteral();
		onlyLiteral.setName("ONLY");
		onlyLiteral.setValue(0);
		singleEnum.getELiterals().add(onlyLiteral);
		
		final EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName("singleValue");
		attribute.setEType(singleEnum);
		testClass.getEStructuralFeatures().add(attribute);
		
		final EObject instance1 = createInstance(testClass);
		final EObject instance2 = createInstance(testClass);
		
		setter.setAttribute(instance1, attribute);
		setter.setAttribute(instance2, attribute);
		
		assertThat(instance1.eGet(attribute)).isEqualTo(onlyLiteral.getInstance());
		assertThat(instance2.eGet(attribute)).isEqualTo(onlyLiteral.getInstance());
		validateModel(instance1);
		validateModel(instance2);
	}

	@Test
	void testSetAttributeValue_WithCustomFunction() {
		final EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
		attribute.setName("customAttr");
		attribute.setEType(EcorePackage.Literals.ESTRING);
		testClass.getEStructuralFeatures().add(attribute);

		final EObject instance = createInstance(testClass);

		// Custom function that always returns "CustomValue"
		setter.setFunctionFor(attribute, inst -> "CustomValue");
		setter.setAttribute(instance, attribute);

		assertThat(instance.eGet(attribute)).isEqualTo("CustomValue");
		validateModel(instance);
	}

	@Test
	void testGenerateAttributeValue_EBigDecimal() {
		final EAttribute attribute = createEAttribute(testClass, "price", EcorePackage.Literals.EBIG_DECIMAL);

		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(java.math.BigDecimal.valueOf(20.5));

		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo(java.math.BigDecimal.valueOf(21.5));
	}

	@Test
	void testGenerateAttributeValue_CustomBigDecimalType() {
		final EDataType bigDecimalType = EcoreFactory.eINSTANCE.createEDataType();
		bigDecimalType.setName("BigDecimal");
		bigDecimalType.setInstanceClassName("java.math.BigDecimal");

		final EAttribute attribute = createEAttribute(testClass, "customBigDecimal", bigDecimalType);

		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(java.math.BigDecimal.valueOf(20.5));
	}

	@Test
	void testGenerateAttributeValue_EBigInteger() {
		final EAttribute attribute = createEAttribute(testClass, "bigId", EcorePackage.Literals.EBIG_INTEGER);

		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(java.math.BigInteger.valueOf(20));

		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo(java.math.BigInteger.valueOf(21));
	}

	@Test
	void testGenerateAttributeValue_CustomBigIntegerType() {
		final EDataType bigIntegerType = EcoreFactory.eINSTANCE.createEDataType();
		bigIntegerType.setName("BigInteger");
		bigIntegerType.setInstanceClassName("java.math.BigInteger");

		final EAttribute attribute = createEAttribute(testClass, "customBigInteger", bigIntegerType);

		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(java.math.BigInteger.valueOf(20));
	}

	@Test
	void testGenerateAttributeValue_EByteArray() {
		final EAttribute attribute = createEAttribute(testClass, "data", EcorePackage.Literals.EBYTE_ARRAY);

		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isInstanceOf(byte[].class);
		assertThat((byte[]) value).containsExactly((byte) 20, (byte) 21);

		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isInstanceOf(byte[].class);
		assertThat((byte[]) value2).containsExactly((byte) 21, (byte) 22);
	}

	@Test
	void testGenerateAttributeValue_CustomByteArrayType() {
		final EDataType byteArrayType = EcoreFactory.eINSTANCE.createEDataType();
		byteArrayType.setName("byte[]");
		byteArrayType.setInstanceClassName("byte[]");

		final EAttribute attribute = createEAttribute(testClass, "customByteArray", byteArrayType);

		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isInstanceOf(byte[].class);
		assertThat((byte[]) value).containsExactly((byte) 20, (byte) 21);
	}

	@Test
	void testGenerateAttributeValue_EIntegerObject() {
		final EAttribute attribute = createEAttribute(testClass, "integerObject", EcorePackage.Literals.EINTEGER_OBJECT);

		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(20);

		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo(21);
	}

	@Test
	void testGenerateAttributeValue_EBooleanObject() {
		final EAttribute attribute = createEAttribute(testClass, "booleanObject", EcorePackage.Literals.EBOOLEAN_OBJECT);

		final Object value0 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value0).isEqualTo(true);

		final Object value1 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value1).isEqualTo(false);
	}

	@Test
	void testGenerateAttributeValue_EDoubleObject() {
		final EAttribute attribute = createEAttribute(testClass, "doubleObject", EcorePackage.Literals.EDOUBLE_OBJECT);

		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(20.5);

		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo(21.5);
	}

	@Test
	void testGenerateAttributeValue_EFloatObject() {
		final EAttribute attribute = createEAttribute(testClass, "floatObject", EcorePackage.Literals.EFLOAT_OBJECT);

		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(20.5f);

		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo(21.5f);
	}

	@Test
	void testGenerateAttributeValue_ELongObject() {
		final EAttribute attribute = createEAttribute(testClass, "longObject", EcorePackage.Literals.ELONG_OBJECT);

		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo(20L);

		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo(21L);
	}

	@Test
	void testGenerateAttributeValue_EShortObject() {
		final EAttribute attribute = createEAttribute(testClass, "shortObject", EcorePackage.Literals.ESHORT_OBJECT);

		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo((short) 20);

		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo((short) 21);
	}

	@Test
	void testGenerateAttributeValue_EByteObject() {
		final EAttribute attribute = createEAttribute(testClass, "byteObject", EcorePackage.Literals.EBYTE_OBJECT);

		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo((byte) 20);

		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo((byte) 21);
	}

	@Test
	void testGenerateAttributeValue_ECharacterObject() {
		final EAttribute attribute = createEAttribute(testClass, "charObject", EcorePackage.Literals.ECHARACTER_OBJECT);

		final Object value = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value).isEqualTo('A');

		final Object value2 = setter.generateAttributeValue(createInstance(testClass), attribute);
		assertThat(value2).isEqualTo('B');
	}

	// ========== Candidate selector strategy tests ==========

	@Test
	void testSetEnumLiteralSelectorStrategy() {
		// Create an enum with two literals
		org.eclipse.emf.ecore.EEnum colorEnum = EcoreFactory.eINSTANCE.createEEnum();
		colorEnum.setName("Color");
		testPackage.getEClassifiers().add(colorEnum);

		org.eclipse.emf.ecore.EEnumLiteral red = EcoreFactory.eINSTANCE.createEEnumLiteral();
		red.setName("RED");
		red.setLiteral("red");
		red.setValue(0);
		colorEnum.getELiterals().add(red);

		org.eclipse.emf.ecore.EEnumLiteral blue = EcoreFactory.eINSTANCE.createEEnumLiteral();
		blue.setName("BLUE");
		blue.setLiteral("blue");
		blue.setValue(1);
		colorEnum.getELiterals().add(blue);

		// Create a custom strategy that always returns RED
		EMFCandidateSelectorStrategy<EEnum, EEnumLiteral> customStrategy = 
			new EMFCandidateSelectorStrategy<EEnum, EEnumLiteral>() {
			@Override
			public EEnumLiteral getNextCandidate(EObject context, EEnum type) {
				return red;
			}

			@Override
			public boolean hasCandidates(EObject context, EEnum type) {
				return true;
			}
		};

		setter.setEnumLiteralSelectorStrategy(customStrategy);

		final EAttribute attribute = createEAttribute(testClass, "color", colorEnum);
		attribute.setUpperBound(-1); // multi-valued
		attribute.setUnique(false); // allow duplicates

		final EObject instance = createInstance(testClass);
		testClass.getEStructuralFeatures().add(attribute);
		setter.setAttribute(instance, attribute);

		final List<Object> values = EMFUtils.getAsList(instance, attribute);
		// All values should be RED (instance 0) due to custom strategy
		assertThat(values).hasSize(2)
			.allMatch(val -> val.equals(red.getInstance()));
	}

}
