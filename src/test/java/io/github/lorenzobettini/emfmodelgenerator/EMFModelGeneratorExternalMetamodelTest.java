package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEAttributeExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEClassExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEReferenceExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertXMIMatchesExpected;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.namespace.QName;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.eclipse.emf.ecore.util.FeatureMapUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EMFModelGeneratorExternalMetamodelTest {

	private static final String TEST_OUTPUT_DIR = "target/test-output";
	private static final String EXTERNAL_METAMODELS_DIR =
			"target/external-metamodels";
	private static final String EXTERNAL_EXPECTED_OUTPUTS_DIR =
			"src/test/resources/expected-external-outputs";

	private EMFModelGenerator generator;

	@BeforeEach
	void setUp() throws IOException {
		generator = new EMFModelGenerator();
		generator.setOutputDirectory(TEST_OUTPUT_DIR);
		var outputPath = Paths.get(TEST_OUTPUT_DIR);
		if (Files.exists(outputPath)) {
			Files.walk(outputPath)
					.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(File::delete);
		}
		Files.createDirectories(outputPath);
	}

	@AfterEach
	void tearDown() {
		EMFTestUtils.cleanupRegisteredPackages();
		generator.unloadEcoreModels();
	}

	private void assertGeneratedQNameRoundTrips(final EAttribute attribute,
			final EObject owner) {
		assertThat(attribute.getEAttributeType().getInstanceClassName())
				.isEqualTo("javax.xml.namespace.QName");
		var value = owner.eGet(attribute);
		assertThat(value).isInstanceOf(QName.class);
		var factory = attribute.getEAttributeType().getEPackage().getEFactoryInstance();
		var lexicalValue = factory.convertToString(attribute.getEAttributeType(), value);
		assertThat(factory.createFromString(attribute.getEAttributeType(), lexicalValue))
				.isEqualTo(value);
	}

	private void assertGenerationIsValid(final String failureMessage) {
		var validation = generator.validate();
		assertThat(validation.isValid())
				.withFailMessage(failureMessage,
						validation.rejectedDiagnostics().stream()
								.map(Diagnostic::getMessage)
								.toList())
				.isTrue();
	}

	@Test
	void testGenerateBpelProcessWithSchemaLocation() throws Exception {
		var packages = generator.loadEcoreModelPackages(EXTERNAL_METAMODELS_DIR + "/BPEL.ecore");

		assertThat(packages).extracting(EPackage::getName)
				.containsExactly("model", "ecore", "wsdl", "partnerlinktype",
						"messageproperties", "xsd");
		var modelPackage = packages.stream()
				.filter(ePackage -> "model".equals(ePackage.getName()))
				.findFirst()
				.orElseThrow();
		var processClass = assertEClassExists(modelPackage, "Process");

		// The default depth expands a large optional BPEL/WSDL/XSD object graph. One level
		// keeps this integration test focused while still generating the required activity.
		generator.getInstancePopulator().setMaxDepth(1);
		generator.setFilePrefix("bpel_");
		var generatedProcess = generator.generateFrom(processClass);

		assertThat(generatedProcess).isNotNull();
		assertThat(generatedProcess.eClass()).isSameAs(processClass);
		var nameAttribute = assertEAttributeExists(processClass, "name");
		assertThat(nameAttribute.getEAttributeType().getInstanceClassName())
				.isEqualTo("java.lang.String");
		assertThat(generatedProcess.eGet(nameAttribute)).isEqualTo("Process_name_1");
		var activityReference = assertEReferenceExists(processClass, "activity");
		var activity = (EObject) generatedProcess.eGet(activityReference);
		assertThat(activity).isNotNull();
		assertThat(activityReference.getEReferenceType().isSuperTypeOf(activity.eClass())).isTrue();

		var validation = generator.validate();
		assertThat(validation.isValid())
				.withFailMessage("BPEL Process validation failed: %s",
						validation.rejectedDiagnostics().stream()
								.map(Diagnostic::getMessage)
								.toList())
				.isTrue();

		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));
		var generatedFileName = "bpel_model_Process_1.xmi";
		assertThat(new File(TEST_OUTPUT_DIR, generatedFileName)).exists();
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXTERNAL_EXPECTED_OUTPUTS_DIR,
				generatedFileName, generatedFileName);
	}

	@Test
	void testGenerateCustomizedBpelProcessWithStructuredActivities() throws Exception {
		var packages = generator.loadEcoreModelPackages(EXTERNAL_METAMODELS_DIR + "/BPEL.ecore");
		var modelPackage = packages.stream()
				.filter(ePackage -> "model".equals(ePackage.getName()))
				.findFirst()
				.orElseThrow();
		var processClass = assertEClassExists(modelPackage, "Process");
		var sequenceClass = assertEClassExists(modelPackage, "Sequence");
		var emptyClass = assertEClassExists(modelPackage, "Empty");
		var whileClass = assertEClassExists(modelPackage, "While");
		var exitClass = assertEClassExists(modelPackage, "Exit");
		var activityClass = assertEClassExists(modelPackage, "Activity");
		var conditionClass = assertEClassExists(modelPackage, "Condition");
		var processActivity = assertEReferenceExists(processClass, "activity");
		var sequenceActivities = assertEReferenceExists(sequenceClass, "activities");
		var whileActivity = assertEReferenceExists(whileClass, "activity");
		var whileCondition = assertEReferenceExists(whileClass, "condition");

		var populator = generator.getInstancePopulator();
		// At depth 3, optional BPEL extensions and handlers introduce WSDL cross-references
		// with no candidates. Keep this example focused on required structured activities.
		populator.setContainmentReferenceSetter(new EMFContainmentReferenceSetter() {
			@Override
			public Collection<EObject> setContainmentReference(final EObject owner,
					final EReference reference) {
				if (reference.getLowerBound() == 0) {
					return List.of();
				}
				return super.setContainmentReference(owner, reference);
			}
		});
		populator.functionForContainmentReference(processActivity,
				owner -> EcoreUtil.create(sequenceClass));
		var selectedActivityClasses = List.of(emptyClass, whileClass, exitClass);
		var activityIndex = new AtomicInteger();
		populator.functionForContainmentReference(sequenceActivities,
				owner -> EcoreUtil.create(selectedActivityClasses.get(
						activityIndex.getAndIncrement() % selectedActivityClasses.size())));
		populator.setContainmentReferenceMaxCountFor(sequenceActivities, 3);
		populator.setContainmentReferenceDefaultMaxCount(1);
		populator.setMaxDepth(3);

		generator.setFilePrefix("bpel_structured_");
		var generatedProcess = generator.generateFrom(processClass);

		assertThat(generatedProcess.eClass()).isSameAs(processClass);
		var sequence = (EObject) generatedProcess.eGet(processActivity);
		assertThat(sequence.eClass()).isSameAs(sequenceClass);
		var activities = EMFUtils.getAsEObjectsList(sequence, sequenceActivities);
		assertThat(activities).extracting(activity -> activity.eClass().getName())
				.containsExactly("Empty", "While", "Exit");
		var generatedWhile = activities.get(1);
		var generatedWhileActivity = (EObject) generatedWhile.eGet(whileActivity);
		var generatedWhileCondition = (EObject) generatedWhile.eGet(whileCondition);
		assertThat(generatedWhileActivity).isNotNull();
		assertThat(activityClass.isSuperTypeOf(generatedWhileActivity.eClass())).isTrue();
		assertThat(generatedWhileCondition).isNotNull();
		assertThat(conditionClass.isSuperTypeOf(generatedWhileCondition.eClass())).isTrue();

		var validation = generator.validate();
		assertThat(validation.isValid())
				.withFailMessage("BPEL Process validation failed: %s",
						validation.rejectedDiagnostics().stream()
								.map(Diagnostic::getMessage)
								.toList())
				.isTrue();

		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));
		var generatedFileName = "bpel_structured_model_Process_1.xmi";
		assertThat(new File(TEST_OUTPUT_DIR, generatedFileName)).exists();
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXTERNAL_EXPECTED_OUTPUTS_DIR,
				generatedFileName, generatedFileName);
	}

	/**
	 * Generates a non-trivial BPEL process together with the WSDL model needed to
	 * satisfy BPEL's required cross-references, and verifies that both models remain
	 * valid after an XMI round trip.
	 *
	 * @see #testGenerateBpelWsdlDefinitionSkippingRequiredTransientContainmentsIsValid()
	 * @throws Exception
	 */
	@Test
	void testGenerateCustomizedBpelProcessWithFlowAndWsdlDefinition() throws Exception {
		var packages = generator.loadEcoreModelPackages(
				EXTERNAL_METAMODELS_DIR + "/BPEL.ecore");
		var modelPackage = packages.stream()
				.filter(ePackage -> "model".equals(ePackage.getName()))
				.findFirst()
				.orElseThrow();
		var wsdlPackage = packages.stream()
				.filter(ePackage -> "wsdl".equals(ePackage.getName()))
				.findFirst()
				.orElseThrow();

		var processClass = assertEClassExists(modelPackage, "Process");
		var flowClass = assertEClassExists(modelPackage, "Flow");
		var processActivity = assertEReferenceExists(processClass, "activity");
		var definitionClass = assertEClassExists(wsdlPackage, "Definition");

		var populator = generator.getInstancePopulator();
		var maxDepth = 3;

		/*
		 * BPEL.ecore combines BPEL with WSDL and several extension packages.
		 * Polymorphic containment may therefore select optional objects whose
		 * structural requirements cannot safely be satisfied by ordinary reflective
		 * generation.
		 *
		 * There are two cases to exclude:
		 *
		 * 1. Some optional candidate classes have required transient features.
		 *    Such features can make the in-memory object valid, but they are not
		 *    serialized; consequently, the model becomes invalid when reloaded.
		 *    For example, messageproperties::PropertyAlias has the required,
		 *    transient wsdlPart reference.
		 *
		 * 2. An optional object created exactly at maxDepth may itself have a
		 *    required containment. The object would be created at the depth frontier,
		 *    while its mandatory child could not be generated. For example, an
		 *    optional CompensationHandler requires an Activity.
		 *
		 * In both cases it is valid to omit the optional outer containment. Required
		 * containments themselves are never suppressed by this policy.
		 */
		populator.setContainmentReferenceSetter(new EMFContainmentReferenceSetter() {
			@Override
			public EObject createValue(final EObject owner, final EReference reference) {
				var value = super.createValue(owner, reference);

				if (value == null || reference.getLowerBound() > 0) {
					return value;
				}

				// Required transient state would be lost during XMI serialization.
				if (hasRequiredTransientFeature(value.eClass())) {
					return null;
				}

				// At the depth frontier, do not create an optional object whose own
				// mandatory containment would necessarily remain unpopulated.
				if (containmentDepth(owner) + 1 >= maxDepth
						&& hasRequiredContainmentFeature(value.eClass())) {
					return null;
				}

				return value;
			}
		});

		/*
		 * Process.activity is required and polymorphic. Select Flow deliberately
		 * because Flow contains BPEL Link objects. These provide normal generated
		 * candidates for the required Source.Link and Target.Link cross-references,
		 * instead of creating unrelated Link roots merely to satisfy those references.
		 */
		populator.functionForContainmentReference(
				processActivity,
				owner -> EcoreUtil.create(flowClass));

		populator.setMaxDepth(maxDepth);
		generator.setFilePrefix("bpel_flow_");

		/*
		 * Several generated BPEL elements, in particular PartnerActivity subclasses
		 * and OnEvent, have required non-containment references to WSDL Message,
		 * PortType, and Operation objects. Since cross-references select existing
		 * compatible objects rather than creating their targets, generate a WSDL
		 * Definition as a supporting root together with the Process. Its containment
		 * hierarchy supplies the WSDL objects that the ordinary cross-reference
		 * population phase can then select.
		 */
		generator.generateFromSeveral(definitionClass, processClass);

		// First verify the generated in-memory models.
		var validation = generator.validate();
		assertThat(validation.isValid())
				.withFailMessage("BPEL Process validation failed: %s",
						validation.rejectedDiagnostics().stream()
								.map(Diagnostic::getMessage)
								.toList())
				.isTrue();

		/*
		 * In-memory validity is not sufficient for this metamodel because transient
		 * state can disappear during serialization. Save, reload, and validate again
		 * to ensure that the actual persisted BPEL/WSDL models are valid as well.
		 */
		var roundTripValidation = generator.saveAndValidateRoundTrip(
				Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE),
				ignored -> EMFModelValidator.standard());
		assertThat(roundTripValidation.isValid())
				.withFailMessage("BPEL/WSDL round-trip validation failed: %s",
						roundTripValidation.rejectedDiagnostics().stream()
								.map(Diagnostic::getMessage)
								.toList())
				.isTrue();

		var generatedProcessFileName = "bpel_flow_model_Process_1.xmi";
		assertThat(new File(TEST_OUTPUT_DIR, generatedProcessFileName)).exists();
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXTERNAL_EXPECTED_OUTPUTS_DIR,
				generatedProcessFileName, generatedProcessFileName);

		var generatedDefinitionFileName = "bpel_flow_wsdl_Definition_1.xmi";
		assertThat(new File(TEST_OUTPUT_DIR, generatedDefinitionFileName)).exists();
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXTERNAL_EXPECTED_OUTPUTS_DIR,
				generatedDefinitionFileName, generatedDefinitionFileName);
	}

	private boolean hasRequiredContainmentFeature(final EClass eClass) {
		return eClass.getEAllReferences().stream()
				.filter(EReference::isContainment)
				.anyMatch(reference -> reference.getLowerBound() > 0);
	}

	private int containmentDepth(final EObject object) {
		int depth = 0;
		var current = object;
		while (current.eContainer() != null) {
			depth++;
			current = current.eContainer();
		}
		return depth;
	}

	@Test
	void testGenerateBpelWsdlDefinitionSkippingRequiredTransientContainmentsIsValid() throws Exception {
		// BPEL.ecore contains the WSDL package together with BPEL extension packages
		// such as "messageproperties". Registering those packages makes additional
		// ExtensibilityElement subclasses available to the normal polymorphic
		// containment selection; these subclasses are not present when WSDL.ecore is
		// loaded on its own.
		var packages = generator.loadEcoreModelPackages(EXTERNAL_METAMODELS_DIR + "/BPEL.ecore");

		assertThat(packages).extracting(EPackage::getName)
				.containsExactly("model", "ecore", "wsdl", "partnerlinktype",
						"messageproperties", "xsd");
		var wsdlPackage = packages.stream()
				.filter(ePackage -> "wsdl".equals(ePackage.getName()))
				.findFirst()
				.orElseThrow();
		var definitionClass = assertEClassExists(wsdlPackage, "Definition");

		var populator = generator.getInstancePopulator();

		// Skip optional containment candidates whose validity depends on required
		// transient state. Such state can be populated in memory, so Diagnostician
		// accepts the generated object, but it is not serialized to XMI and is
		// therefore lost when the model is loaded again. Container references are
		// excluded because they can be reconstructed from the containment relation.
		// Example: The messageproperties.PropertyAlias.wsdlPart has
		// PropertyAlias.wsdlPart : wsdl::Part [1..1] but it's TRANSIENT
		// So, the generated model in memory would be valid with the required features set,
		// but the generated XMI file is not valid because the wsdlPart is not serialized.
		// This is specific of BPEL that provides a custom implementation to deal with that.
		populator.setContainmentReferenceSetter(new EMFContainmentReferenceSetter() {
			@Override
			public EObject createValue(final EObject owner, final EReference reference) {
				var value = super.createValue(owner, reference);
				if (value == null || reference.getLowerBound() > 0) {
					return value;
				}
				return hasRequiredTransientFeature(value.eClass()) ? null : value;
			}
		});

		populator.setMaxDepth(2);
		generator.setFilePrefix("bpel_deep_");
		generator.generateFromSeveral(definitionClass);

		var validation = generator.validate();
		assertThat(validation.isValid())
				.withFailMessage("BPEL-hosted WSDL Definition validation failed: %s",
						validation.rejectedDiagnostics().stream()
								.map(Diagnostic::getMessage)
								.toList())
				.isTrue();

		var roundTripValidation = generator.saveAndValidateRoundTrip(
				Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE),
				ignored -> EMFModelValidator.standard());
		assertThat(roundTripValidation.isValid())
				.withFailMessage("BPEL-hosted WSDL Definition round-trip validation failed: %s",
						roundTripValidation.rejectedDiagnostics().stream()
								.map(Diagnostic::getMessage)
								.toList())
				.isTrue();

		var generatedFileName = "bpel_deep_wsdl_Definition_1.xmi";
		assertThat(new File(TEST_OUTPUT_DIR, generatedFileName)).exists();
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXTERNAL_EXPECTED_OUTPUTS_DIR,
				generatedFileName, generatedFileName);
	}

	@Test
	void testGenerateBpelWsdlDefinitionWithRequiredTransientContainmentsIsValidButNotAfterSaving() throws Exception {
		var packages = generator.loadEcoreModelPackages(EXTERNAL_METAMODELS_DIR + "/BPEL.ecore");

		assertThat(packages).extracting(EPackage::getName)
				.containsExactly("model", "ecore", "wsdl", "partnerlinktype",
						"messageproperties", "xsd");
		var wsdlPackage = packages.stream()
				.filter(ePackage -> "wsdl".equals(ePackage.getName()))
				.findFirst()
				.orElseThrow();
		var definitionClass = assertEClassExists(wsdlPackage, "Definition");

		generator.getInstancePopulator().setMaxDepth(2);
		generator.setFilePrefix("bpel_deep_");
		generator.generateFromSeveral(definitionClass);

		// The messageproperties.PropertyAlias.wsdlPart has
		// PropertyAlias.wsdlPart : wsdl::Part [1..1] but it's TRANSIENT
		// So, the generated model in memory would be valid with the required features set,
		// but the generated XMI file is not valid because the wsdlPart is not serialized.
		// This is specific of BPEL that provides a custom implementation to deal with that.

		var validation = generator.validate();
		assertThat(validation.isValid())
				.withFailMessage("BPEL-hosted WSDL Definition validation failed: %s",
						validation.rejectedDiagnostics().stream()
								.map(Diagnostic::getMessage)
								.toList())
				.isTrue();

		var roundTripValidation = generator.saveAndValidateRoundTrip(
				Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE),
				ignored -> EMFModelValidator.standard());
		var diagnosticList = roundTripValidation.flattenedDiagnostics();
		assertThat(roundTripValidation.isValid())
				.isFalse();

		// verify that error messages are related to missing required transient features
		assertThat(diagnosticList).
			extracting(Diagnostic::getMessage)
			.anySatisfy(message -> assertThat(message).containsAnyOf("wsdlPart", "PropertyAlias"));
		var generatedFileName = "bpel_deep_wsdl_Definition_1.xmi";
		assertThat(new File(TEST_OUTPUT_DIR, generatedFileName)).exists();
	}

	private boolean hasRequiredTransientFeature(EClass eClass) {
		return eClass
				.getEAllStructuralFeatures()
				.stream()
				.filter(feature -> feature.getLowerBound() > 0)
				.filter(EStructuralFeature::isTransient)
				.anyMatch(feature -> !(feature instanceof EReference eReference)
						|| !eReference.isContainer());
	}

	private void assertBpmnMessageFeatureMapStructure(final EAttribute orderedMessages,
			final EClass messageVertexClass,
			final EReference incomingMessages,
			final EReference outgoingMessages,
			final EClass messagingEdgeClass,
			final EReference messageSource,
			final EReference messageTarget) {
		assertThat(orderedMessages.getEContainingClass()).isSameAs(messageVertexClass);
		assertThat(orderedMessages.isMany()).isTrue();
		assertThat(orderedMessages.isUnique()).isFalse();
		assertThat(FeatureMapUtil.isFeatureMap(orderedMessages)).isTrue();
		assertThat(EMFUtils.findFeatureMapGroupMembers(orderedMessages))
				.containsExactly(incomingMessages, outgoingMessages);
		assertThat(List.of(incomingMessages, outgoingMessages)).allSatisfy(reference -> {
			assertThat(reference.isMany()).isTrue();
			assertThat(reference.isContainment()).isFalse();
			assertThat(reference.isDerived()).isTrue();
			assertThat(reference.isTransient()).isTrue();
			assertThat(reference.isVolatile()).isTrue();
			assertThat(reference.getEReferenceType()).isSameAs(messagingEdgeClass);
		});
		assertThat(incomingMessages.getEOpposite()).isSameAs(messageTarget);
		assertThat(outgoingMessages.getEOpposite()).isSameAs(messageSource);
		assertThat(messageTarget.getEOpposite()).isSameAs(incomingMessages);
		assertThat(messageSource.getEOpposite()).isSameAs(outgoingMessages);
		assertThat(List.of(messageSource, messageTarget)).allSatisfy(reference -> {
			assertThat(reference.isMany()).isFalse();
			assertThat(reference.isContainment()).isFalse();
			assertThat(reference.getEReferenceType()).isSameAs(messageVertexClass);
		});
	}

	private void assertBpmnGeneratedDiagram(final EObject generatedDiagram,
			final EClass bpmnDiagramClass,
			final List<EObject> pools,
			final List<EObject> messages,
			final List<Map.Entry<EReference, EObject>> featureMapSelections,
			final EClass messagingEdgeClass,
			final EAttribute orderedMessages,
			final EReference incomingMessages,
			final EReference outgoingMessages,
			final EReference messageSource,
			final EReference messageTarget) {
		assertThat(generatedDiagram.eClass()).isSameAs(bpmnDiagramClass);
		assertThat(pools).hasSize(2);
		assertThat(messages).hasSize(2);
		assertThat(featureMapSelections).extracting(Map.Entry::getKey)
				.containsExactly(incomingMessages, outgoingMessages,
						incomingMessages, outgoingMessages);
		assertThat(featureMapSelections).extracting(Map.Entry::getValue)
				.allSatisfy(value -> assertThat(value).isIn(messages));
		var generatedMessagingEdges = new ArrayList<EObject>();
		generatedDiagram.eAllContents().forEachRemaining(candidate -> {
			if (candidate.eClass() == messagingEdgeClass) {
				generatedMessagingEdges.add(candidate);
			}
		});
		assertThat(generatedMessagingEdges).containsExactlyElementsOf(messages);
		assertThat(pools).zipSatisfy(
				List.of(List.of(messages.get(0), messages.get(1)),
						List.of(messages.get(1), messages.get(0))),
				(pool, expectedValues) -> {
			var entries = (FeatureMap) pool.eGet(orderedMessages);
			assertThat(entries).extracting(FeatureMap.Entry::getEStructuralFeature)
					.containsExactly(incomingMessages, outgoingMessages);
			assertThat(entries).extracting(FeatureMap.Entry::getValue)
					.containsExactlyElementsOf(expectedValues);
			assertThat(entries).allSatisfy(entry -> {
				var edge = (EObject) entry.getValue();
				var opposite = ((EReference) entry.getEStructuralFeature()).getEOpposite();
				assertThat(edge.eGet(opposite)).isSameAs(pool);
			});
		});
		assertThat(messages).allSatisfy(message -> {
			assertThat(message.eGet(messageSource)).isIn(pools);
			assertThat(message.eGet(messageTarget)).isIn(pools);
		});
	}

	@Test
	void testGenerateBpmnDiagramWithDefaultConfigurationIsValid() throws Exception {
		var packages = generator.loadEcoreModelPackages(EXTERNAL_METAMODELS_DIR + "/BPMN.ecore");

		assertThat(packages).extracting(EPackage::getName)
				.containsExactly("bpmn", "ecore", "type");
		var bpmnPackage = packages.stream()
				.filter(ePackage -> "bpmn".equals(ePackage.getName()))
				.findFirst()
				.orElseThrow();
		var bpmnDiagramClass = assertEClassExists(bpmnPackage, "BpmnDiagram");
		// The default depth 5 expands recursively enough to generate a valid model.
		// keep it explicit here to make it clear.
		generator.getInstancePopulator().setMaxDepth(5);
		generator.generateFrom(bpmnDiagramClass);

		var validation = generator.validate();
		assertThat(validation.isValid())
				.withFailMessage("BPMN validation failed: %s",
						validation.rejectedDiagnostics().stream()
								.map(Diagnostic::getMessage)
								.toList())
				.isTrue();
	}

	@Test
	void testGenerateDeepBpmnDiagramWithSchemaLocation() throws Exception {
		var packages = generator.loadEcoreModelPackages(EXTERNAL_METAMODELS_DIR + "/BPMN.ecore");

		assertThat(packages).extracting(EPackage::getName)
				.containsExactly("bpmn", "ecore", "type");
		var bpmnPackage = packages.stream()
				.filter(ePackage -> "bpmn".equals(ePackage.getName()))
				.findFirst()
				.orElseThrow();
		var bpmnDiagramClass = assertEClassExists(bpmnPackage, "BpmnDiagram");
		// The default depth expands recursively enough to generate a valid model.
		// but it gets about 640K!
		generator.getInstancePopulator().setMaxDepth(3);
		generator.setFilePrefix("bpmn_deep_");
		generator.generateFrom(bpmnDiagramClass);

		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));

		var validation = generator.validate();
		assertThat(validation.isValid())
				.withFailMessage("BPMN validation failed: %s",
						validation.rejectedDiagnostics().stream()
								.map(Diagnostic::getMessage)
								.toList())
				.isTrue();

		var generatedFileName = "bpmn_deep_bpmn_BpmnDiagram_1.xmi";
		assertThat(new File(TEST_OUTPUT_DIR, generatedFileName)).exists();
		// avoid asserting against expected output because the generated model is large
	}

	@Test
	void testGenerateBpmnDiagramWithSchemaLocation() throws Exception {
		var packages = generator.loadEcoreModelPackages(EXTERNAL_METAMODELS_DIR + "/BPMN.ecore");

		assertThat(packages).extracting(EPackage::getName)
				.containsExactly("bpmn", "ecore", "type");
		var bpmnPackage = packages.stream()
				.filter(ePackage -> "bpmn".equals(ePackage.getName()))
				.findFirst()
				.orElseThrow();
		var bpmnDiagramClass = assertEClassExists(bpmnPackage, "BpmnDiagram");
		var messageVertexClass = assertEClassExists(bpmnPackage, "MessageVertex");
		var poolClass = assertEClassExists(bpmnPackage, "Pool");
		var poolsReference = assertEReferenceExists(bpmnDiagramClass, "pools");
		var messagesReference = assertEReferenceExists(bpmnDiagramClass, "messages");
		var orderedMessages = assertEAttributeExists(poolClass, "orderedMessages");
		var incomingMessages = assertEReferenceExists(poolClass, "incomingMessages");
		var outgoingMessages = assertEReferenceExists(poolClass, "outgoingMessages");
		var messagingEdgeClass = assertEClassExists(bpmnPackage, "MessagingEdge");
		var messageSource = assertEReferenceExists(messagingEdgeClass, "source");
		var messageTarget = assertEReferenceExists(messagingEdgeClass, "target");

		assertBpmnMessageFeatureMapStructure(orderedMessages, messageVertexClass,
				incomingMessages, outgoingMessages, messagingEdgeClass,
				messageSource, messageTarget);

		var featureMapSelections = new ArrayList<Map.Entry<EReference, EObject>>();
		generator.getInstancePopulator().setCrossReferenceSetter(
				new EMFCrossReferenceSetter() {
					@Override
					public EObject selectValue(final EObject owner,
							final EReference crossReference) {
						var selected = super.selectValue(owner, crossReference);
						if (crossReference == incomingMessages
								|| crossReference == outgoingMessages) {
							featureMapSelections.add(Map.entry(crossReference, selected));
						}
						return selected;
					}
				});

		// The default depth expands recursively through optional Pool/Graph containments.
		// One level retains the diagram's pools, messages, and artifacts without growing
		// unrelated nested subprocess graphs.
		generator.getInstancePopulator().setMaxDepth(1);
		generator.setFilePrefix("bpmn_");
		var generatedDiagram = generator.generateFrom(bpmnDiagramClass);

		var pools = EMFUtils.getAsEObjectsList(generatedDiagram, poolsReference);
		var messages = EMFUtils.getAsEObjectsList(generatedDiagram, messagesReference);
		assertBpmnGeneratedDiagram(generatedDiagram, bpmnDiagramClass, pools, messages,
				featureMapSelections, messagingEdgeClass, orderedMessages,
				incomingMessages, outgoingMessages, messageSource, messageTarget);

		var validation = generator.validate();
		assertThat(validation.isValid())
				.withFailMessage("BPMN validation failed: %s",
						validation.rejectedDiagnostics().stream()
								.map(Diagnostic::getMessage)
								.toList())
				.isTrue();

		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));
		var generatedFileName = "bpmn_bpmn_BpmnDiagram_1.xmi";
		assertThat(new File(TEST_OUTPUT_DIR, generatedFileName)).exists();
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXTERNAL_EXPECTED_OUTPUTS_DIR,
				generatedFileName, generatedFileName);
	}

	@Test
	void testGenerateCustomizedBpmnGraph() throws Exception {
		var packages = generator.loadEcoreModelPackages(EXTERNAL_METAMODELS_DIR + "/BPMN.ecore");
		var bpmnPackage = packages.stream()
				.filter(ePackage -> "bpmn".equals(ePackage.getName()))
				.findFirst()
				.orElseThrow();
		var graphClass = assertEClassExists(bpmnPackage, "Graph");
		var activityClass = assertEClassExists(bpmnPackage, "Activity");
		var verticesReference = assertEReferenceExists(graphClass, "vertices");
		var sequenceEdgesReference = assertEReferenceExists(graphClass, "sequenceEdges");
		var sourceReference = assertEReferenceExists(
				assertEClassExists(bpmnPackage, "SequenceEdge"), "source");
		var targetReference = assertEReferenceExists(
				assertEClassExists(bpmnPackage, "SequenceEdge"), "target");
		var outgoingEdgesReference = assertEReferenceExists(
				assertEClassExists(bpmnPackage, "Vertex"), "outgoingEdges");
		var incomingEdgesReference = assertEReferenceExists(
				assertEClassExists(bpmnPackage, "Vertex"), "incomingEdges");

		var populator = generator.getInstancePopulator();
		// Populate connectivity from the edge ends. Their opposites maintain the vertex
		// collections; assigning both directions independently could select different edges.
		populator.setCrossReferenceSetter(new EMFCrossReferenceSetter() {
			@Override
			public void setCrossReference(final EObject owner, final EReference reference) {
				if (reference != outgoingEdgesReference && reference != incomingEdgesReference) {
					super.setCrossReference(owner, reference);
				}
			}
		});
		populator.functionForContainmentReference(verticesReference,
				owner -> EcoreUtil.create(activityClass));
		populator.setContainmentReferenceMaxCountFor(verticesReference, 2);
		populator.setContainmentReferenceMaxCountFor(sequenceEdgesReference, 1);
		populator.functionForCrossReference(sourceReference,
				owner -> EMFUtils.getAsEObjectsList(owner.eContainer(), verticesReference).get(0));
		populator.functionForCrossReference(targetReference,
				owner -> EMFUtils.getAsEObjectsList(owner.eContainer(), verticesReference).get(1));
		populator.setMaxDepth(1);
		generator.setFilePrefix("bpmn_graph_");
		var generatedGraph = generator.generateFrom(graphClass);

		var vertices = EMFUtils.getAsEObjectsList(generatedGraph, verticesReference);
		var edges = EMFUtils.getAsEObjectsList(generatedGraph, sequenceEdgesReference);
		assertThat(vertices).hasSize(2).allMatch(vertex -> vertex.eClass() == activityClass);
		assertThat(edges).singleElement().satisfies(edge -> {
			assertThat(edge.eGet(sourceReference)).isIn(vertices);
			assertThat(edge.eGet(targetReference)).isIn(vertices);
			assertThat(EMFUtils.getAsEObjectsList(vertices.get(0), outgoingEdgesReference))
					.containsExactly(edge);
			assertThat(EMFUtils.getAsEObjectsList(vertices.get(1), incomingEdgesReference))
					.containsExactly(edge);
		});

		var validation = generator.validate();
		assertThat(validation.isValid())
				.withFailMessage("BPMN Graph validation failed: %s",
						validation.rejectedDiagnostics().stream()
								.map(Diagnostic::getMessage)
								.toList())
				.isTrue();

		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));
		var generatedFileName = "bpmn_graph_bpmn_Graph_1.xmi";
		assertThat(new File(TEST_OUTPUT_DIR, generatedFileName)).exists();
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXTERNAL_EXPECTED_OUTPUTS_DIR,
				generatedFileName, generatedFileName);
	}

	@Test
	void testGenerateWsdlDefinitionWithSchemaLocation() throws Exception {
		var packages = generator.loadEcoreModelPackages(EXTERNAL_METAMODELS_DIR + "/WSDL.ecore");

		assertThat(packages).extracting(EPackage::getName)
				.containsExactly("wsdl", "ecore", "xsd");
		var wsdlPackage = packages.stream()
				.filter(ePackage -> "wsdl".equals(ePackage.getName()))
				.findFirst()
				.orElseThrow();
		var definitionClass = assertEClassExists(wsdlPackage, "Definition");
		var messagesReference = assertEReferenceExists(definitionClass, "eMessages");
		var portTypesReference = assertEReferenceExists(definitionClass, "ePortTypes");
		var bindingsReference = assertEReferenceExists(definitionClass, "eBindings");
		var servicesReference = assertEReferenceExists(definitionClass, "eServices");
		var importsReference = assertEReferenceExists(definitionClass, "eImports");
		var typesReference = assertEReferenceExists(definitionClass, "eTypes");
		var targetNamespaceAttribute = assertEAttributeExists(
				definitionClass, "targetNamespace");
		var qNameAttribute = assertEAttributeExists(definitionClass, "qName");

		// The default depth enters optional extensibility elements and recursively creates
		// copied XSD semantic structures. One level retains every direct WSDL component
		// while avoiding those unrelated optional extensions.
		generator.getInstancePopulator().setMaxDepth(1);
		generator.setFilePrefix("wsdl_");
		var generatedDefinition = generator.generateFrom(definitionClass);

		assertThat(generatedDefinition.eClass()).isSameAs(definitionClass);
		assertThat(generatedDefinition.eGet(targetNamespaceAttribute))
				.isEqualTo("Definition_targetNamespace_1");
		assertThat(EMFUtils.getAsEObjectsList(generatedDefinition, importsReference))
				.hasSize(2);
		assertThat(generatedDefinition.eGet(typesReference)).isNotNull();
		assertThat(EMFUtils.getAsEObjectsList(generatedDefinition, messagesReference))
				.hasSize(2);
		assertThat(EMFUtils.getAsEObjectsList(generatedDefinition, portTypesReference))
				.hasSize(2);
		var portTypes = EMFUtils.getAsEObjectsList(generatedDefinition, portTypesReference);
		var bindingPortTypeReference = assertEReferenceExists(
				assertEClassExists(wsdlPackage, "Binding"), "ePortType");
		assertThat(EMFUtils.getAsEObjectsList(generatedDefinition, bindingsReference))
				.hasSize(2)
				.allSatisfy(binding -> assertThat(binding.eGet(bindingPortTypeReference))
						.isIn(portTypes));
		assertThat(EMFUtils.getAsEObjectsList(generatedDefinition, servicesReference))
				.hasSize(2);
		assertGeneratedQNameRoundTrips(qNameAttribute, generatedDefinition);
		assertGenerationIsValid("WSDL Definition validation failed: %s");

		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));
		var generatedFileName = "wsdl_wsdl_Definition_1.xmi";
		assertThat(new File(TEST_OUTPUT_DIR, generatedFileName)).exists();
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXTERNAL_EXPECTED_OUTPUTS_DIR,
				generatedFileName, generatedFileName);
	}

	@Test
	void testGenerateConnectedWsdlDefinition() throws Exception {
		var packages = generator.loadEcoreModelPackages(EXTERNAL_METAMODELS_DIR + "/WSDL.ecore");
		var wsdlPackage = packages.stream()
				.filter(ePackage -> "wsdl".equals(ePackage.getName()))
				.findFirst()
				.orElseThrow();
		var definitionClass = assertEClassExists(wsdlPackage, "Definition");
		var portTypeClass = assertEClassExists(wsdlPackage, "PortType");
		var operationClass = assertEClassExists(wsdlPackage, "Operation");
		var inputClass = assertEClassExists(wsdlPackage, "Input");
		var outputClass = assertEClassExists(wsdlPackage, "Output");
		var messagesReference = assertEReferenceExists(definitionClass, "eMessages");
		var portTypesReference = assertEReferenceExists(definitionClass, "ePortTypes");
		var operationsReference = assertEReferenceExists(portTypeClass, "eOperations");
		var inputReference = assertEReferenceExists(operationClass, "eInput");
		var outputReference = assertEReferenceExists(operationClass, "eOutput");
		var messageReference = assertEReferenceExists(inputClass, "eMessage");

		var selectedContainments = List.of(
				messagesReference, portTypesReference, operationsReference,
				inputReference, outputReference);
		var populator = generator.getInstancePopulator();
		// Keep the example on the WSDL message/operation aggregate. In particular, this
		// excludes optional extensibility elements that would enter the copied XSD model.
		populator.setContainmentReferenceSetter(new EMFContainmentReferenceSetter() {
			@Override
			public Collection<EObject> setContainmentReference(final EObject owner,
					final EReference reference) {
				if (!selectedContainments.contains(reference)) {
					return List.of();
				}
				return super.setContainmentReference(owner, reference);
			}
		});
		populator.setContainmentReferenceMaxCountFor(messagesReference, 2);
		populator.setContainmentReferenceMaxCountFor(portTypesReference, 1);
		populator.setContainmentReferenceMaxCountFor(operationsReference, 1);
		populator.functionForCrossReference(messageReference, owner -> {
			var definition = EcoreUtil.getRootContainer(owner);
			var messages = EMFUtils.getAsEObjectsList(definition, messagesReference);
			return messages.get(owner.eClass() == outputClass ? 1 : 0);
		});
		populator.setMaxDepth(3);
		generator.setFilePrefix("wsdl_connected_");

		var generatedDefinition = generator.generateFrom(definitionClass);

		var messages = EMFUtils.getAsEObjectsList(generatedDefinition, messagesReference);
		assertThat(messages).hasSize(2);
		assertThat(messages)
				.extracting(message -> message.eGet(
						assertEAttributeExists(message.eClass(), "qName")))
				.containsExactly(new QName("name1"), new QName("name2"));
		var portTypes = EMFUtils.getAsEObjectsList(generatedDefinition, portTypesReference);
		assertThat(portTypes).singleElement().satisfies(portType -> {
			var operations = EMFUtils.getAsEObjectsList(portType, operationsReference);
			assertThat(operations).singleElement().satisfies(operation -> {
				var input = (EObject) operation.eGet(inputReference);
				var output = (EObject) operation.eGet(outputReference);
				assertThat(input.eClass()).isSameAs(inputClass);
				assertThat(output.eClass()).isSameAs(outputClass);
				assertThat(input.eGet(messageReference)).isSameAs(messages.get(0));
				assertThat(output.eGet(messageReference)).isSameAs(messages.get(1));
			});
		});

		assertGenerationIsValid("Connected WSDL Definition validation failed: %s");

		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));
		var generatedFileName = "wsdl_connected_wsdl_Definition_1.xmi";
		assertThat(new File(TEST_OUTPUT_DIR, generatedFileName)).exists();
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXTERNAL_EXPECTED_OUTPUTS_DIR,
				generatedFileName, generatedFileName);
	}

	@Test
	void testGenerateConnectedWsdlDefinitionDeep() throws Exception {
		var packages = generator.loadEcoreModelPackages(EXTERNAL_METAMODELS_DIR + "/WSDL.ecore");
		var wsdlPackage = packages.stream()
				.filter(ePackage -> "wsdl".equals(ePackage.getName()))
				.findFirst()
				.orElseThrow();
		var definitionClass = assertEClassExists(wsdlPackage, "Definition");
		var xsdSchemaExtensibilityElementClass = assertEClassExists(wsdlPackage, "XSDSchemaExtensibilityElement");
		var schemaReference = assertEReferenceExists(xsdSchemaExtensibilityElementClass, "schema");

		var populator = generator.getInstancePopulator();

		// Skip the optional XSDSchema containment. A dynamically instantiated
		// XSDSchema requires XSD runtime-derived state (for example rootContainer,
		// rootVersion, and schemaForSchema) that cannot be populated through ordinary
		// reflective assignment. Omitting this optional branch keeps the generated
		// WSDL model structurally valid while leaving the rest of the generation generic.
		populator.setContainmentReferenceSetter(new EMFContainmentReferenceSetter() {
			@Override
			public Collection<EObject> setContainmentReference(final EObject owner, final EReference reference) {
				if (reference == schemaReference) {
					return List.of();
				}
				return super.setContainmentReference(owner, reference);
			}
		});
		populator.setMaxDepth(3);
		generator.setFilePrefix("wsdl_deep_");

		generator.generateFrom(definitionClass);
		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));
		assertGenerationIsValid("Connected WSDL Definition validation failed: %s");

		var generatedFileName = "wsdl_deep_wsdl_Definition_1.xmi";
		assertThat(new File(TEST_OUTPUT_DIR, generatedFileName)).exists();
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXTERNAL_EXPECTED_OUTPUTS_DIR,
				generatedFileName, generatedFileName);
	}

	@Test
	void testGenerateConnectedWsdlDefinitionWithDefaultDepthIsValid() throws Exception {
		var packages = generator.loadEcoreModelPackages(EXTERNAL_METAMODELS_DIR + "/WSDL.ecore");
		var wsdlPackage = packages.stream()
				.filter(ePackage -> "wsdl".equals(ePackage.getName()))
				.findFirst()
				.orElseThrow();
		var definitionClass = assertEClassExists(wsdlPackage, "Definition");
		var xsdSchemaExtensibilityElementClass = assertEClassExists(wsdlPackage, "XSDSchemaExtensibilityElement");
		var schemaReference = assertEReferenceExists(xsdSchemaExtensibilityElementClass, "schema");

		var populator = generator.getInstancePopulator();

		// Skip the optional XSDSchema containment. A dynamically instantiated
		// XSDSchema requires XSD runtime-derived state (for example rootContainer,
		// rootVersion, and schemaForSchema) that cannot be populated through ordinary
		// reflective assignment. Omitting this optional branch keeps the generated
		// WSDL model structurally valid while leaving the rest of the generation generic.
		populator.setContainmentReferenceSetter(new EMFContainmentReferenceSetter() {
			@Override
			public Collection<EObject> setContainmentReference(final EObject owner, final EReference reference) {
				if (reference == schemaReference) {
					return List.of();
				}
				return super.setContainmentReference(owner, reference);
			}
		});
		populator.setMaxDepth(5);
		generator.setFilePrefix("wsdl_deep_default_");

		generator.generateFrom(definitionClass);
		generator.save(Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE));
		assertGenerationIsValid("Connected WSDL Definition validation failed: %s");

		var generatedFileName = "wsdl_deep_default_wsdl_Definition_1.xmi";
		assertThat(new File(TEST_OUTPUT_DIR, generatedFileName)).exists();
		// avoid asserting against expected output because the generated model is large
	}

	@Test
	void testGenerateAadlPackageWithDefaultGeneration() throws Exception {
		var aadlPackage = generator.loadEcoreModel(
				EXTERNAL_METAMODELS_DIR + "/aadl2.ecore");

		assertThat(aadlPackage.getName()).isEqualTo("aadl2");

		var aadlPackageClass = assertEClassExists(aadlPackage, "AadlPackage");

		var namedElementClass = assertEClassExists(aadlPackage, "NamedElement");
		var ownedPropertyAssociation =
				assertEReferenceExists(namedElementClass, "ownedPropertyAssociation");

		var packageSectionClass = assertEClassExists(aadlPackage, "PackageSection");
		var ownedClassifier =
				assertEReferenceExists(packageSectionClass, "ownedClassifier");

		var populator = generator.getInstancePopulator();

		/*
		 * Property associations are optional, but once created they require a
		 * Property target. An isolated AadlPackage does not contain Property
		 * definitions that can serve as cross-reference candidates.
		 */
		populator.setContainmentReferenceMaxCountFor(
				ownedPropertyAssociation, 0);

		/*
		 * Keep one classifier per package section.
		 * With the deterministic default selector this avoids reaching
		 * ComponentImplementation subclasses, whose required derived `type`
		 * is normally provided by OSATE-specific runtime semantics.
		 */
		populator.setContainmentReferenceMaxCountFor(
				ownedClassifier, 1);

		populator.setMaxDepth(2);

		generator.setFilePrefix("aadl_");
		var generatedPackage = generator.generateFrom(aadlPackageClass);

		assertThat(generatedPackage.eClass()).isSameAs(aadlPackageClass);

		var validation = generator.validate();
		assertThat(validation.isValid())
				.withFailMessage("AADL package validation failed: %s",
						validation.rejectedDiagnostics().stream()
								.map(Diagnostic::getMessage)
								.toList())
				.isTrue();

		var roundTripValidation = generator.saveAndValidateRoundTrip(
				Map.of(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE),
				ignored -> EMFModelValidator.standard());

		assertThat(roundTripValidation.isValid())
				.withFailMessage("AADL package round-trip validation failed: %s",
						roundTripValidation.rejectedDiagnostics().stream()
								.map(Diagnostic::getMessage)
								.toList())
				.isTrue();

		var generatedFileName = "aadl_aadl2_AadlPackage_1.xmi";
		assertThat(new File(TEST_OUTPUT_DIR, generatedFileName)).exists();
		assertXMIMatchesExpected(TEST_OUTPUT_DIR, EXTERNAL_EXPECTED_OUTPUTS_DIR,
				generatedFileName, generatedFileName);
	}
}
