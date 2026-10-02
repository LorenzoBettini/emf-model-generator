package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEAttributeExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEClassExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEReferenceExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.loadEcoreModel;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for EMFFeatureMapSetter.
 * This class achieves 100% coverage of the feature map setter functionality.
 */
class EMFFeatureMapSetterTest {

	private static final String TEST_INPUTS_DIR = "src/test/resources/inputs";
	
	private EMFFeatureMapSetter setter;
	private EPackage ePackage;
	private EClass libraryClass;
	private EAttribute peopleAttr;

	@BeforeEach
	void setUp() {
		setter = new EMFFeatureMapSetter();
		setter.setAttributeSetter(new EMFAttributeSetter());
		setter.setContainmentReferenceSetter(new EMFContainmentReferenceSetter());
		setter.setCrossReferenceSetter(new EMFCrossReferenceSetter());
		ePackage = loadEcoreModel(TEST_INPUTS_DIR, "extlibrary.ecore");
		libraryClass = assertEClassExists(ePackage, "Library");
		peopleAttr = assertEAttributeExists(libraryClass, "people");
		
		// Register the package for test
		EMFTestUtils.registerPackageForTest(ePackage);
	}

	@AfterEach
	void tearDown() {
		EMFTestUtils.cleanupRegisteredPackages();
	}

	@Test
	void testSetFeatureMapPopulatesFeatureMap() {
		final var library = EcoreUtil.create(libraryClass);
		
		setter.setFeatureMap(library, peopleAttr);
		
		final var featureMap = (FeatureMap) library.eGet(peopleAttr);
		
		assertThat(featureMap)
			.as("Feature map should be populated")
			.isNotEmpty();
		
		// Check that derived references are automatically populated through the feature map
		final var writersRef = libraryClass.getEStructuralFeature("writers");
		final var writers = EMFUtils.getAsEObjectsList(library, writersRef);
		
		assertThat(writers)
			.as("Writers should be accessible through derived reference")
			.isNotEmpty();
	}

	@Test
	void testFeatureMapEntriesHaveCorrectTypes() {
		final var library = EcoreUtil.create(libraryClass);
		setter.setDefaultMaxCount(3);
		
		setter.setFeatureMap(library, peopleAttr);
		
		final var featureMap = (FeatureMap) library.eGet(peopleAttr);
		
		// Check that entries in the feature map have the correct EStructuralFeature set
		for (var entry : featureMap) {
			assertThat(entry.getEStructuralFeature())
				.as("Each feature map entry should have a structural feature")
				.isNotNull();
			
			assertThat(entry.getValue())
				.as("Each feature map entry should have a value")
				.isNotNull()
				.isInstanceOf(EObject.class);
		}
	}

	@Test
	void testFeatureMapMaxCountConfiguration() {
		final var library = EcoreUtil.create(libraryClass);
		
		// Set specific max count for the feature map
		setter.setMaxCountFor(peopleAttr, 5);
		
		setter.setFeatureMap(library, peopleAttr);
		
		final var featureMap = (FeatureMap) library.eGet(peopleAttr);
		
		assertThat(featureMap)
			.as("Feature map should have 5 entries as configured")
			.hasSize(5);
	}

	@Test
	void testFeatureMapWithDifferentGroupMembers() {
		final var library = EcoreUtil.create(libraryClass);
		
		// Set feature map to create 6 entries
		setter.setMaxCountFor(peopleAttr, 6);
		
		setter.setFeatureMap(library, peopleAttr);
		
		// Check the feature map itself
		final var featureMap = (FeatureMap) library.eGet(peopleAttr);
		assertThat(featureMap)
			.as("Feature map should have 6 entries")
			.hasSize(6);
		
		// Get derived references
		final var writersRef = libraryClass.getEStructuralFeature("writers");
		final var employeesRef = libraryClass.getEStructuralFeature("employees");
		final var borrowersRef = libraryClass.getEStructuralFeature("borrowers");
		
		final var writers = EMFUtils.getAsEObjectsList(library, writersRef);
		final var employees = EMFUtils.getAsEObjectsList(library, employeesRef);
		final var borrowers = EMFUtils.getAsEObjectsList(library, borrowersRef);
		
		// With 6 items and 3 group members, we should get 2 of each type (round-robin)
		assertThat(writers).hasSize(2);
		assertThat(employees).hasSize(2);
		assertThat(borrowers).hasSize(2);
	}

	@Test
	void testSetGroupMemberSelectorStrategy() {
		final var library = EcoreUtil.create(libraryClass);
		final var writersRef = assertEReferenceExists(libraryClass, "writers");
		
		final var customSelector =
				new EMFCandidateSelectorStrategy<EAttribute, EStructuralFeature>() {
			@Override
			public EStructuralFeature getNextCandidate(EObject context, EAttribute type) {
				return writersRef;
			}

			@Override
			public boolean hasCandidates(EObject context, EAttribute type) {
				return true;
			}
		};

		setter.setGroupMemberSelectorStrategy(customSelector);
		setter.setMaxCountFor(peopleAttr, 3);
		setter.setFeatureMap(library, peopleAttr);

		final var writers = EMFUtils.getAsEObjectsList(library, writersRef);
		final var employeesRef = libraryClass.getEStructuralFeature("employees");
		final var employees = EMFUtils.getAsEObjectsList(library, employeesRef);
		final var borrowersRef = libraryClass.getEStructuralFeature("borrowers");
		final var borrowers = EMFUtils.getAsEObjectsList(library, borrowersRef);

		assertThat(writers).hasSize(3);
		assertThat(employees).isEmpty();
		assertThat(borrowers).isEmpty();
	}

	@Test
	void testResetClearsGroupMemberSelectorState() {
		final var library1 = EcoreUtil.create(libraryClass);
		final var library2 = EcoreUtil.create(libraryClass);
		final var library3 = EcoreUtil.create(libraryClass);
		
		// Use count=1 so we get one item per call
		setter.setMaxCountFor(peopleAttr, 1);
		
		// First call - round-robin starts at writers (index 0)
		setter.setFeatureMap(library1, peopleAttr);
		
		// Second call WITHOUT reset - round-robin continues to employees (index 1)
		setter.setFeatureMap(library2, peopleAttr);
		
		// Call reset() - this should clear nextIndexMap so we restart from writers
		setter.reset();
		
		// Third call WITH reset - should restart at writers (index 0)
		setter.setFeatureMap(library3, peopleAttr);
		
		final var writersRef = assertEReferenceExists(libraryClass, "writers");
		final var employeesRef = assertEReferenceExists(libraryClass, "employees");
		
		// library1 should have writers (first call, index 0)
		assertThat(EMFUtils.getAsEObjectsList(library1, writersRef)).hasSize(1);
		assertThat(EMFUtils.getAsEObjectsList(library1, employeesRef)).isEmpty();
		
		// library2 should have employees (second call WITHOUT reset, index 1)
		assertThat(EMFUtils.getAsEObjectsList(library2, writersRef)).isEmpty();
		assertThat(EMFUtils.getAsEObjectsList(library2, employeesRef)).hasSize(1);
		
		// library3 should have writers again (third call WITH reset, back to index 0)
		assertThat(EMFUtils.getAsEObjectsList(library3, writersRef)).hasSize(1);
		assertThat(EMFUtils.getAsEObjectsList(library3, employeesRef)).isEmpty();
	}

	@Test
	void testSetFeatureMapReturnsOnlyObjectsCreatedByEachCall() {
		final var library1 = EcoreUtil.create(libraryClass);
		final var library2 = EcoreUtil.create(libraryClass);
		
		setter.setMaxCountFor(peopleAttr, 2);
		
		// First call - populate and get reference to created objects collection
		final var createdObjects1 = setter.setFeatureMap(library1, peopleAttr);
		final var firstCallSize = createdObjects1.size();
		assertThat(firstCallSize).isEqualTo(2);
		
		// Second call - should clear the collection before populating
		final var createdObjects2 = setter.setFeatureMap(library2, peopleAttr);
		
		assertThat(createdObjects2)
			.as("Second call should return only its own two created objects")
			.hasSize(2);
		assertThat(createdObjects1).doesNotContainAnyElementsOf(createdObjects2);
	}

	@Test
	void testAlreadyPopulatedFeatureMapIsSkipped() {
		final var library = EcoreUtil.create(libraryClass);
		setter.setFeatureMap(library, peopleAttr);
		final var featureMap = (FeatureMap) library.eGet(peopleAttr);
		final var existingEntries = featureMap.size();

		assertThat(setter.setFeatureMap(library, peopleAttr)).isEmpty();
		assertThat(featureMap).hasSize(existingEntries);
	}

	@Test
	void nullContainmentValueIsNotReportedAsCreated() {
		final var library = EcoreUtil.create(libraryClass);
		final var writersRef = assertEReferenceExists(libraryClass, "writers");
		setter.setContainmentReferenceSetter(new EMFContainmentReferenceSetter() {
			@Override
			public EObject createValue(final EObject owner, final EReference reference) {
				return null;
			}
		});
		setter.setGroupMemberSelectorStrategy(
				new EMFCandidateSelectorStrategy<EAttribute, EStructuralFeature>() {
					@Override
					public EStructuralFeature getNextCandidate(final EObject context,
							final EAttribute type) {
						return writersRef;
					}

					@Override
					public boolean hasCandidates(final EObject context, final EAttribute type) {
						return true;
					}
				});
		setter.setMaxCountFor(peopleAttr, 1);

		assertThat(setter.setFeatureMap(library, peopleAttr)).isEmpty();
		assertThat((FeatureMap) library.eGet(peopleAttr)).isEmpty();
	}

	@Test
	void testEmptyGroupMembersReturnsEarly() {
		final var emptyPackage = loadEcoreModel(TEST_INPUTS_DIR, "featuremap_no_members.ecore");
		EMFTestUtils.registerPackageForTest(emptyPackage);
		
		final var testClass = assertEClassExists(emptyPackage, "TestClass");
		final var emptyFeatureMapAttr = assertEAttributeExists(testClass, "emptyFeatureMap");
		final var testInstance = EcoreUtil.create(testClass);
		
		final var createdObjects = setter.setFeatureMap(testInstance, emptyFeatureMapAttr);
		
		assertThat(createdObjects)
			.as("No objects should be created when feature map has no group members")
			.isEmpty();
		
		final var featureMap = (FeatureMap) testInstance.eGet(emptyFeatureMapAttr);
		
		assertThat(featureMap)
			.as("Feature map should be empty when no group members exist")
			.isEmpty();
	}

	@Test
	void planFreezesTheCompleteMixedSequenceWithoutGeneratingValues() {
		final var fixture = mixedFixture();
		final var attributeCalls = new AtomicInteger();
		final var containmentCalls = new AtomicInteger();
		final var crossReferenceCalls = new AtomicInteger();
		setter.setAttributeSetter(new EMFAttributeSetter() {
			@Override
			public Object generateValue(final EObject owner, final EAttribute attribute) {
				attributeCalls.incrementAndGet();
				return super.generateValue(owner, attribute);
			}
		});
		setter.setContainmentReferenceSetter(new EMFContainmentReferenceSetter() {
			@Override
			public EObject createValue(final EObject owner, final EReference reference) {
				containmentCalls.incrementAndGet();
				return super.createValue(owner, reference);
			}
		});
		setter.setCrossReferenceSetter(new EMFCrossReferenceSetter() {
			@Override
			public EObject selectValue(final EObject owner, final EReference reference) {
				crossReferenceCalls.incrementAndGet();
				return super.selectValue(owner, reference);
			}
		});
		setter.setMaxCountFor(fixture.group(), 6);

		final var plan = setter.createPlan(fixture.document(), fixture.group());

		assertThat(plan.owner()).isSameAs(fixture.document());
		assertThat(plan.featureMapAttribute()).isSameAs(fixture.group());
		assertThat(plan.groupMembers())
				.extracting(EStructuralFeature::getName)
				.containsExactly("text", "child", "related", "text", "child", "related");
		assertThat(attributeCalls).hasValue(0);
		assertThat(containmentCalls).hasValue(0);
		assertThat(crossReferenceCalls).hasValue(0);
		assertThat((FeatureMap) fixture.document().eGet(fixture.group())).isEmpty();
		assertThat(fixture.document().eContents()).isEmpty();
	}

	@Test
	void structuralMaterializationPreservesOrderAroundPendingCrossReferences() {
		final var fixture = mixedFixture();
		final var crossReferenceCalls = new AtomicInteger();
		setter.setCrossReferenceSetter(new EMFCrossReferenceSetter() {
			@Override
			public EObject selectValue(final EObject owner, final EReference reference) {
				crossReferenceCalls.incrementAndGet();
				return super.selectValue(owner, reference);
			}
		});
		setter.setMaxCountFor(fixture.group(), 6);
		final var plan = setter.createPlan(fixture.document(), fixture.group());

		final var created = setter.materializeStructuralFeatures(plan, true);

		final var featureMap = (FeatureMap) fixture.document().eGet(fixture.group());
		assertThat(featureMap)
				.extracting(entry -> entry.getEStructuralFeature().getName())
				.containsExactly("text", "child", "text", "child");
		assertThat(featureMap.getValue(0)).isEqualTo("Document_text_1");
		assertThat(featureMap.getValue(2)).isEqualTo("Document_text_2");
		assertThat(created).hasSize(2)
				.allSatisfy(child -> assertThat(child.eContainer()).isSameAs(fixture.document()));
		assertThat(plan.isMaterialized(0)).isTrue();
		assertThat(plan.isMaterialized(1)).isTrue();
		assertThat(plan.isMaterialized(2)).isFalse();
		assertThat(plan.isMaterialized(3)).isTrue();
		assertThat(plan.isMaterialized(4)).isTrue();
		assertThat(plan.isMaterialized(5)).isFalse();
		assertThat(crossReferenceCalls).hasValue(0);
		assertThat(setter.materializeStructuralFeatures(plan, true)).isEmpty();
		assertThat(featureMap).hasSize(4);
	}

	@Test
	void crossReferenceMaterializationRestoresTheFrozenOrderWithExistingValues() {
		final var fixture = mixedFixture();
		final var firstTarget = EcoreUtil.create(fixture.targetClass());
		final var secondTarget = EcoreUtil.create(fixture.targetClass());
		final var selectedTargets = List.of(firstTarget, secondTarget).iterator();
		setter.setCrossReferenceSetter(new EMFCrossReferenceSetter() {
			@Override
			public EObject selectValue(final EObject owner, final EReference reference) {
				return selectedTargets.next();
			}
		});
		setter.setMaxCountFor(fixture.group(), 6);
		final var plan = setter.createPlan(fixture.document(), fixture.group());
		final var created = setter.materializeStructuralFeatures(plan, true);

		setter.materializeCrossReferences(plan);

		final var featureMap = (FeatureMap) fixture.document().eGet(fixture.group());
		assertThat(featureMap)
				.extracting(entry -> entry.getEStructuralFeature().getName())
				.containsExactly("text", "child", "related", "text", "child", "related");
		assertThat(EMFUtils.getAsEObjectsList(fixture.document(), fixture.related()))
				.containsExactly(firstTarget, secondTarget);
		assertThat(featureMap.getValue(2)).isSameAs(firstTarget);
		assertThat(featureMap.getValue(5)).isSameAs(secondTarget);
		assertThat(created).hasSize(2);
		assertThat(fixture.document().eContents()).containsExactlyElementsOf(created);
		assertThat(firstTarget.eContainer()).isNull();
		assertThat(secondTarget.eContainer()).isNull();
		assertThat(firstTarget.eGet(fixture.targetDocument())).isSameAs(fixture.document());
		assertThat(secondTarget.eGet(fixture.targetDocument())).isSameAs(fixture.document());
		assertThat(plan.isMaterialized(2)).isTrue();
		assertThat(plan.isMaterialized(5)).isTrue();

		setter.materializeCrossReferences(plan);
		assertThat(featureMap).hasSize(6);
	}

	@Test
	void nullCrossReferenceLeavesNoPlaceholderAndDoesNotShiftALaterValue() {
		final var fixture = mixedFixture();
		final var target = EcoreUtil.create(fixture.targetClass());
		final var calls = new AtomicInteger();
		setter.setCrossReferenceSetter(new EMFCrossReferenceSetter() {
			@Override
			public EObject selectValue(final EObject owner, final EReference reference) {
				return calls.getAndIncrement() == 0 ? null : target;
			}
		});
		setter.setMaxCountFor(fixture.group(), 6);
		final var plan = setter.createPlan(fixture.document(), fixture.group());
		setter.materializeStructuralFeatures(plan, true);

		setter.materializeCrossReferences(plan);

		final var featureMap = (FeatureMap) fixture.document().eGet(fixture.group());
		assertThat(featureMap)
				.extracting(entry -> entry.getEStructuralFeature().getName())
				.containsExactly("text", "child", "text", "child", "related");
		assertThat(featureMap.getValue(4)).isSameAs(target);
		assertThat(plan.isMaterialized(2)).isFalse();
		assertThat(plan.isMaterialized(5)).isTrue();
		assertThat(calls).hasValue(2);
	}

	@Test
	void uniqueCrossReferenceSkipsDuplicatesAndStopsAfterCandidateWrap() {
		final var fixture = mixedFixture();
		final var firstTarget = EcoreUtil.create(fixture.targetClass());
		final var secondTarget = EcoreUtil.create(fixture.targetClass());
		final var selections = List.of(firstTarget, firstTarget, secondTarget).iterator();
		setter.setCrossReferenceSetter(new EMFCrossReferenceSetter() {
			@Override
			public EObject selectValue(final EObject owner, final EReference reference) {
				return selections.next();
			}
		});
		setter.setGroupMemberSelectorStrategy(selectorReturning(fixture.related()));
		setter.setMaxCountFor(fixture.group(), 2);
		final var plan = setter.createPlan(fixture.document(), fixture.group());

		setter.materializeCrossReferences(plan);

		assertThat(EMFUtils.getAsEObjectsList(fixture.document(), fixture.related()))
				.containsExactly(firstTarget, secondTarget);

		final var anotherFixture = mixedFixture();
		final var onlyTarget = EcoreUtil.create(anotherFixture.targetClass());
		final var calls = new AtomicInteger();
		setter.setCrossReferenceSetter(new EMFCrossReferenceSetter() {
			@Override
			public EObject selectValue(final EObject owner, final EReference reference) {
				calls.incrementAndGet();
				return onlyTarget;
			}
		});
		setter.setGroupMemberSelectorStrategy(selectorReturning(anotherFixture.related()));
		setter.setMaxCountFor(anotherFixture.group(), 2);
		final var exhaustedPlan = setter.createPlan(
				anotherFixture.document(), anotherFixture.group());

		setter.materializeCrossReferences(exhaustedPlan);

		assertThat(EMFUtils.getAsEObjectsList(
				anotherFixture.document(), anotherFixture.related()))
				.containsExactly(onlyTarget);
		assertThat(calls).hasValue(3);
		assertThat(exhaustedPlan.isMaterialized(1)).isFalse();
	}

	@Test
	void nonUniqueCrossReferenceAllowsRepeatedValues() {
		final var fixture = mixedFixture();
		fixture.related().setEOpposite(null);
		fixture.targetDocument().setEOpposite(null);
		fixture.related().setUnique(false);
		final var target = EcoreUtil.create(fixture.targetClass());
		setter.setCrossReferenceSetter(new EMFCrossReferenceSetter() {
			@Override
			public EObject selectValue(final EObject owner, final EReference reference) {
				return target;
			}
		});
		setter.setGroupMemberSelectorStrategy(selectorReturning(fixture.related()));
		setter.setMaxCountFor(fixture.group(), 2);
		final var plan = setter.createPlan(fixture.document(), fixture.group());

		setter.materializeCrossReferences(plan);

		assertThat(EMFUtils.getAsEObjectsList(fixture.document(), fixture.related()))
				.containsExactly(target, target);
	}

	@Test
	void containmentCanBeSuppressedWithoutSuppressingAttributes() {
		final var fixture = mixedFixture();
		setter.setMaxCountFor(fixture.group(), 3);
		final var plan = setter.createPlan(fixture.document(), fixture.group());

		assertThat(setter.materializeStructuralFeatures(plan, false)).isEmpty();

		final var featureMap = (FeatureMap) fixture.document().eGet(fixture.group());
		assertThat(featureMap)
				.extracting(entry -> entry.getEStructuralFeature().getName())
				.containsExactly("text");
		assertThat(plan.isMaterialized(0)).isTrue();
		assertThat(plan.isMaterialized(1)).isFalse();
		assertThat(plan.isMaterialized(2)).isFalse();
		assertThat(fixture.document().eContents()).isEmpty();
	}

	@Test
	void nullAttributeValueDoesNotCreateAPlaceholder() {
		final var fixture = mixedFixture();
		setter.setAttributeSetter(new EMFAttributeSetter() {
			@Override
			public Object generateValue(final EObject owner, final EAttribute attribute) {
				return null;
			}
		});
		setter.setGroupMemberSelectorStrategy(selectorReturning(fixture.text()));
		setter.setMaxCountFor(fixture.group(), 1);
		final var plan = setter.createPlan(fixture.document(), fixture.group());

		assertThat(setter.materializeStructuralFeatures(plan, true)).isEmpty();
		setter.materializeCrossReferences(plan);
		assertThat((FeatureMap) fixture.document().eGet(fixture.group())).isEmpty();
		assertThat(plan.isMaterialized(0)).isFalse();
	}

	@Test
	void planningStopsWhenTheSelectorHasNoCandidate() {
		final var fixture = mixedFixture();
		final var selections = new AtomicInteger();
		setter.setGroupMemberSelectorStrategy(
				new EMFCandidateSelectorStrategy<EAttribute, EStructuralFeature>() {
					@Override
					public EStructuralFeature getNextCandidate(final EObject context,
							final EAttribute type) {
						return selections.getAndIncrement() == 0 ? fixture.text() : null;
					}

					@Override
					public boolean hasCandidates(final EObject context, final EAttribute type) {
						return true;
					}
				});
		setter.setMaxCountFor(fixture.group(), 3);

		assertThat(setter.createPlan(fixture.document(), fixture.group()).groupMembers())
				.containsExactly(fixture.text());
		assertThat(selections).hasValue(2);
	}

	@Test
	void planningDoesNotAskASelectorThatReportsNoCandidates() {
		final var fixture = mixedFixture();
		final var selections = new AtomicInteger();
		setter.setGroupMemberSelectorStrategy(
				new EMFCandidateSelectorStrategy<EAttribute, EStructuralFeature>() {
					@Override
					public EStructuralFeature getNextCandidate(final EObject context,
							final EAttribute type) {
						selections.incrementAndGet();
						return fixture.text();
					}

					@Override
					public boolean hasCandidates(final EObject context, final EAttribute type) {
						return false;
					}
				});

		assertThat(setter.createPlan(fixture.document(), fixture.group()).groupMembers())
				.isEmpty();
		assertThat(selections).hasValue(0);
	}

	@Test
	void planningHonorsFeatureMapBoundsAndConfiguredTotalCount() {
		final var fixture = mixedFixture();
		fixture.group().setLowerBound(4);
		fixture.group().setUpperBound(5);
		setter.setMaxCountFor(fixture.group(), 6);

		assertThat(setter.createPlan(fixture.document(), fixture.group()).groupMembers())
				.hasSize(5);

		final var anotherDocument = EcoreUtil.create(fixture.document().eClass());
		setter.setMaxCountFor(fixture.group(), 2);
		assertThat(setter.createPlan(anotherDocument, fixture.group()).groupMembers())
				.hasSize(4);
	}

	@Test
	void alreadySetAndEmptyFeatureMapsProduceEmptyPlansWithoutSelection() {
		final var fixture = mixedFixture();
		final var selections = new AtomicInteger();
		setter.setGroupMemberSelectorStrategy(new EMFCandidateSelectorStrategy<>() {
			@Override
			public EStructuralFeature getNextCandidate(final EObject context,
					final EAttribute type) {
				selections.incrementAndGet();
				return fixture.text();
			}

			@Override
			public boolean hasCandidates(final EObject context, final EAttribute type) {
				return true;
			}
		});
		final var featureMap = (FeatureMap) fixture.document().eGet(fixture.group());
		featureMap.add(fixture.text(), "existing");

		assertThat(setter.createPlan(fixture.document(), fixture.group()).groupMembers())
				.isEmpty();
		assertThat(featureMap.getValue(0)).isEqualTo("existing");
		assertThat(selections).hasValue(0);

		final var emptyPackage = loadEcoreModel(TEST_INPUTS_DIR, "featuremap_no_members.ecore");
		EMFTestUtils.registerPackageForTest(emptyPackage);
		final var emptyClass = assertEClassExists(emptyPackage, "TestClass");
		final var emptyMap = assertEAttributeExists(emptyClass, "emptyFeatureMap");
		assertThat(setter.createPlan(EcoreUtil.create(emptyClass), emptyMap).groupMembers())
				.isEmpty();
		assertThat(selections).hasValue(0);
	}

	@Test
	void publicPlanCopiesTheSelectedSequence() {
		final var fixture = mixedFixture();
		final var selected = new ArrayList<EStructuralFeature>();
		selected.add(fixture.text());

		final var plan = new EMFFeatureMapSetter.FeatureMapPlan(
				fixture.document(), fixture.group(), selected);
		selected.add(fixture.child());

		assertThat(plan.groupMembers()).containsExactly(fixture.text());
	}

	private EMFCandidateSelectorStrategy<EAttribute, EStructuralFeature> selectorReturning(
			final EStructuralFeature feature) {
		return new EMFCandidateSelectorStrategy<>() {
			@Override
			public EStructuralFeature getNextCandidate(final EObject context,
					final EAttribute type) {
				return feature;
			}

			@Override
			public boolean hasCandidates(final EObject context, final EAttribute type) {
				return true;
			}
		};
	}

	private MixedFixture mixedFixture() {
		final var mixedPackage = loadEcoreModel(TEST_INPUTS_DIR, "featuremap_mixed.ecore");
		EMFTestUtils.registerPackageForTest(mixedPackage);
		final var documentClass = assertEClassExists(mixedPackage, "Document");
		return new MixedFixture(
				EcoreUtil.create(documentClass),
				assertEAttributeExists(documentClass, "group"),
				assertEAttributeExists(documentClass, "text"),
				assertEReferenceExists(documentClass, "child"),
				assertEReferenceExists(documentClass, "related"),
				assertEClassExists(mixedPackage, "Target"),
				assertEReferenceExists(assertEClassExists(mixedPackage, "Target"), "document"));
	}

	private record MixedFixture(EObject document, EAttribute group, EAttribute text,
			EReference child, EReference related, EClass targetClass,
			EReference targetDocument) {
	}
}
