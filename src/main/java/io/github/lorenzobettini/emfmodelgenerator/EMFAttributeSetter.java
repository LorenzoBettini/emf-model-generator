package io.github.lorenzobettini.emfmodelgenerator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcorePackage;

/**
 * Responsible for setting attribute values on EMF EObjects. This class handles
 * both single-valued and multi-valued attributes, generating appropriate sample
 * values based on the attribute's data type.
 */
public class EMFAttributeSetter extends EMFConfigurableFeatureSetter<EAttribute, EAttribute, Object> {

	/**
	 * Key for tracking counters per EClass/EAttribute combination.
	 * 
	 * @param eClass the EClass
	 * @param attribute the EAttribute
	 */
	private record CounterKey(EClass eClass, EAttribute attribute) {}

	// Configuration constants
	private static final int DEFAULT_MULTI_VALUED_COUNT = 2;
	private static final int DEFAULT_INT_VALUE = 20;
	private static final double DEFAULT_DOUBLE_VALUE = 20.5;

	private int defaultIntValue = DEFAULT_INT_VALUE;
	private double defaultDoubleValue = DEFAULT_DOUBLE_VALUE;

	// Map to track counters for each EClass/EAttribute combination
	private final Map<CounterKey, Integer> attributeCounters = new HashMap<>();

	// Selector for enum literal candidate selection
	private EMFCandidateSelectorStrategy<EEnum, EEnumLiteral> enumLiteralSelectorStrategy = new EMFRoundRobinEEnumLiteralCandidateSelector();

	/**
	 * Function interface for attribute value operations.
	 */
	@FunctionalInterface
	public static interface EMFAttributeValueFunction extends FeatureFunction<Object> {
	}

	protected EMFAttributeSetter() {
		super(DEFAULT_MULTI_VALUED_COUNT);
	}

	/**
	 * Set the candidate selector strategy for selecting enum literals.
	 * 
	 * @param strategy the candidate selector strategy to use
	 */
	public void setEnumLiteralSelectorStrategy(EMFCandidateSelectorStrategy<EEnum, EEnumLiteral> strategy) {
		this.enumLiteralSelectorStrategy = strategy;
	}

	/**
	 * Set the default integer value to use when generating integer attributes.
	 *
	 * @param defaultIntValue the default integer value
	 */
	public void setDefaultIntValue(final int defaultIntValue) {
		this.defaultIntValue = defaultIntValue;
	}

	/**
	 * Set the default double value to use when generating double/float attributes.
	 *
	 * @param defaultDoubleValue the default double value
	 */
	public void setDefaultDoubleValue(final double defaultDoubleValue) {
		this.defaultDoubleValue = defaultDoubleValue;
	}

	/**
	 * Get the configured default integer value.
	 *
	 * @return the default integer value
	 */
	public int getDefaultIntValue() {
		return defaultIntValue;
	}

	/**
	 * Get the configured default double value.
	 *
	 * @return the default double value
	 */
	public double getDefaultDoubleValue() {
		return defaultDoubleValue;
	}

	/**
	 * Set the value for the given attribute on the given instance. For multi-valued
	 * attributes, adds multiple values. For single-valued attributes, sets a single
	 * value.
	 *
	 * @param owner  the EObject instance to set the attribute on
	 * @param attribute the attribute to set
	 */
	public void setAttribute(final EObject owner, final EAttribute attribute) {
		setFeature(owner, attribute);
	}

	@Override
	protected void setSingleFeature(final EObject owner, final EAttribute attribute) {
		final Object value = generateAttributeValue(owner, attribute);
		// For single-valued attributes
		if (value != null) {
			owner.eSet(attribute, value);
		}
	}

	@Override
	protected void setMultiFeature(final EObject owner, final EAttribute attribute) {
		final int count = EMFUtils.getEffectiveCount(attribute, getMaxCountFor(owner, attribute));
		// For multi-valued attributes, add multiple values
		// Respect upper and lower bounds
		for (int i = 0; i < count; i++) {
			final Object value = generateAttributeValue(owner, attribute);
			if (value != null) {
				final var list = EMFUtils.getAsList(owner, attribute);
				list.add(value);
			}
		}
	}

	/**
	 * Generate a sample value for an attribute based on its data type. Uses a
	 * per-attribute counter to generate unique incremental values. For enum types,
	 * uses round-robin selection of enum literals.
	 * 
	 * @param owner The EObject owning the attribute. By default, this parameter is not used in value generation but may be useful for extensions.
	 * @param attribute the attribute to generate a value for
	 * 
	 * @return the generated value, or null if the data type is not supported
	 */
	protected Object generateAttributeValue(EObject owner, final EAttribute attribute) {
		final EDataType dataType = attribute.getEAttributeType();

		var function = getFunctionFor(attribute);
		if (function != null) {
			return function.apply(owner);
		}

		// Check if the data type is an enum
		if (dataType instanceof EEnum eEnum) {
			return generateEnumValue(eEnum);
		}

		// Get and increment the counter for this EClass/attribute combination
		final int counter = getAndIncrementCounter(owner, attribute);

		// Generate value based on data type
		return generateValueForDataType(owner, attribute, dataType, counter);
	}

	private Object generateEnumValue(final EEnum eEnum) {
		final var literal = enumLiteralSelectorStrategy.getNextCandidate(null, eEnum);
		return literal != null ? literal.getInstance() : null;
	}

	private int getAndIncrementCounter(final EObject owner, final EAttribute attribute) {
		final var key = new CounterKey(owner.eClass(), attribute);
		final int counter = attributeCounters.getOrDefault(key, 0);
		attributeCounters.put(key, counter + 1);
		return counter;
	}

	private Object generateValueForDataType(final EObject owner, final EAttribute attribute, final EDataType dataType, final int counter) {
		if (isStringType(dataType)) {
			return generateStringValue(owner, attribute, counter);
		} else if (isIntegerType(dataType)) {
			return defaultIntValue + counter;
		} else if (isBooleanType(dataType)) {
			return counter % 2 == 0;
		} else if (isDoubleType(dataType)) {
			return defaultDoubleValue + counter;
		} else if (isFloatType(dataType)) {
			return (float) (defaultDoubleValue + counter);
		} else if (isLongType(dataType)) {
			return (long) (defaultIntValue + counter);
		} else if (isShortType(dataType)) {
			return (short) (defaultIntValue + counter);
		} else if (isByteType(dataType)) {
			return (byte) (defaultIntValue + counter);
		} else if (isCharType(dataType)) {
			return (char) ('A' + counter);
		} else if (dataType == EcorePackage.Literals.EDATE) {
			return generateDateValue(counter);
		} else if (isBigDecimalType(dataType)) {
			return BigDecimal.valueOf(defaultDoubleValue + counter);
		} else if (isBigIntegerType(dataType)) {
			return BigInteger.valueOf((long)defaultIntValue + counter);
		} else if (isByteArrayType(dataType)) {
			return generateByteArrayValue(counter);
		} else {
			return attribute.getDefaultValue();
		}
	}

	private String generateStringValue(final EObject owner, final EAttribute attribute, final int counter) {
		final String eClassName = owner.eClass().getName();
		return eClassName + "_" + attribute.getName() + "_" + (counter + 1);
	}

	private Object generateDateValue(final int counter) {
		final java.util.Calendar cal = java.util.Calendar.getInstance();
		cal.set(2025, 0, 1 + counter, 0, 0, 0);
		cal.set(java.util.Calendar.MILLISECOND, 0);
		return cal.getTime();
	}

	private byte[] generateByteArrayValue(final int counter) {
		return new byte[] { (byte) (defaultIntValue + counter), (byte) (defaultIntValue + counter + 1) };
	}

	private boolean isStringType(final EDataType dataType) {
		return dataType == EcorePackage.Literals.ESTRING || "String".equals(dataType.getName());
	}

	private boolean isIntegerType(final EDataType dataType) {
		return dataType == EcorePackage.Literals.EINT || dataType == EcorePackage.Literals.EINTEGER_OBJECT
				|| "int".equals(dataType.getName()) || "Integer".equals(dataType.getName());
	}

	private boolean isBooleanType(final EDataType dataType) {
		return dataType == EcorePackage.Literals.EBOOLEAN || dataType == EcorePackage.Literals.EBOOLEAN_OBJECT
				|| "boolean".equals(dataType.getName()) || "Boolean".equals(dataType.getName());
	}

	private boolean isDoubleType(final EDataType dataType) {
		return dataType == EcorePackage.Literals.EDOUBLE || dataType == EcorePackage.Literals.EDOUBLE_OBJECT
				|| "double".equals(dataType.getName()) || "Double".equals(dataType.getName());
	}

	private boolean isFloatType(final EDataType dataType) {
		return dataType == EcorePackage.Literals.EFLOAT || dataType == EcorePackage.Literals.EFLOAT_OBJECT
				|| "float".equals(dataType.getName()) || "Float".equals(dataType.getName());
	}

	private boolean isLongType(final EDataType dataType) {
		return dataType == EcorePackage.Literals.ELONG || dataType == EcorePackage.Literals.ELONG_OBJECT
				|| "long".equals(dataType.getName()) || "Long".equals(dataType.getName());
	}

	private boolean isShortType(final EDataType dataType) {
		return dataType == EcorePackage.Literals.ESHORT || dataType == EcorePackage.Literals.ESHORT_OBJECT
				|| "short".equals(dataType.getName()) || "Short".equals(dataType.getName());
	}

	private boolean isByteType(final EDataType dataType) {
		return dataType == EcorePackage.Literals.EBYTE || dataType == EcorePackage.Literals.EBYTE_OBJECT
				|| "byte".equals(dataType.getName()) || "Byte".equals(dataType.getName());
	}

	private boolean isCharType(final EDataType dataType) {
		return dataType == EcorePackage.Literals.ECHAR || dataType == EcorePackage.Literals.ECHARACTER_OBJECT
				|| "char".equals(dataType.getName()) || "Character".equals(dataType.getName());
	}

	private boolean isBigDecimalType(final EDataType dataType) {
		return dataType == EcorePackage.Literals.EBIG_DECIMAL || "BigDecimal".equals(dataType.getName());
	}

	private boolean isBigIntegerType(final EDataType dataType) {
		return dataType == EcorePackage.Literals.EBIG_INTEGER || "BigInteger".equals(dataType.getName());
	}

	private boolean isByteArrayType(final EDataType dataType) {
		return dataType == EcorePackage.Literals.EBYTE_ARRAY || "byte[]".equals(dataType.getName());
	}
}
