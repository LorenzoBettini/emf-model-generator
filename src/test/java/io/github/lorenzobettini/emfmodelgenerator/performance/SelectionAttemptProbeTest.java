package io.github.lorenzobettini.emfmodelgenerator.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.junit.jupiter.api.Test;

import io.github.lorenzobettini.emfmodelgenerator.EMFCrossReferenceSetter;
import io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils;

class SelectionAttemptProbeTest {
	private record Graph(Resource resource, EObject owner, List<EObject> candidates,
			EReference reference) {
	}

	private Graph graph(final EPackage model, final String type, final String feature,
			final int candidates) {
		final var set = EMFTestUtils.createResourceSet();
		set.getPackageRegistry().put(model.getNsURI(), model);
		final var resource = set.createResource(URI.createURI("test:/model.xmi"));
		final var ownerClass = (EClass) model.getEClassifier(type);
		final var owner = EcoreUtil.create(ownerClass);
		resource.getContents().add(owner);
		final var reference = (EReference) ownerClass.getEStructuralFeature(feature);
		final var values = new java.util.ArrayList<EObject>();
		for (int i = 0; i < candidates; i++) {
			final var value = EcoreUtil.create(reference.getEReferenceType());
			resource.getContents().add(value);
			values.add(value);
		}
		return new Graph(resource, owner, values, reference);
	}

	private byte[] xmi(final Resource resource) throws Exception {
		final var bytes = new ByteArrayOutputStream();
		resource.save(bytes, Map.of());
		return bytes.toByteArray();
	}

	@Test
	void emptySelfAndRoundRobinPreserveSelectionsAndStructure() throws Exception {
		final var model = EMFTestUtils.loadEPackage("inputs/self-reference.ecore");
		final var emptyModel = EMFTestUtils.loadEPackage("inputs/featuremap_mixed.ecore");
		final var baseline = graph(emptyModel, "Document", "favorite", 0);
		final var observed = graph(emptyModel, "Document", "favorite", 0);
		final var ordinary = new EMFCrossReferenceSetter();
		final var probe = new SelectionAttemptProbe();
		assertThat(ordinary.selectValue(baseline.owner(), baseline.reference())).isNull();
		assertThat(probe.selectValue(observed.owner(), observed.reference())).isNull();
		assertThat(probe.features().values().iterator().next().noCandidates).isEqualTo(1);
		assertThat(probe.features().values().iterator().next().attempts).isZero();

		final var baseNodes = graph(model, "Node", "next", 2);
		final var probeNodes = graph(model, "Node", "next", 2);
		final var baseSetter = new EMFCrossReferenceSetter();
		final var probeSetter = new SelectionAttemptProbe();
		for (int i = 0; i < 4; i++) {
			final EObject expected = baseSetter.selectValue(baseNodes.owner(), baseNodes.reference());
			final EObject actual = probeSetter.selectValue(probeNodes.owner(), probeNodes.reference());
			assertThat(probeNodes.candidates().indexOf(actual))
					.isEqualTo(baseNodes.candidates().indexOf(expected));
		}
		assertThat(probeSetter.features().values().iterator().next().histogramText())
				.isEqualTo("1:2;2:2");
		assertThat(Diagnostician.INSTANCE.validate(baseNodes.owner()).getSeverity()).isZero();
		assertThat(Diagnostician.INSTANCE.validate(probeNodes.owner()).getSeverity()).isZero();
		assertThat(xmi(probeNodes.resource())).isEqualTo(xmi(baseNodes.resource()));
	}

	@Test
	void uniqueListRetriesRemainSeparateSelectionCalls() throws Exception {
		final var model = EMFTestUtils.loadEPackage("inputs/standalone-references.ecore");
		final var baseline = graph(model, "Document", "relatedDocs", 2);
		final var observed = graph(model, "Document", "relatedDocs", 2);
		final var ordinary = new EMFCrossReferenceSetter();
		final var probe = new SelectionAttemptProbe();
		ordinary.setDefaultMaxCount(3);
		probe.setDefaultMaxCount(3);
		ordinary.setCrossReference(baseline.owner(), baseline.reference());
		probe.setCrossReference(observed.owner(), observed.reference());
		assertThat(observed.owner().eGet(observed.reference()))
				.isEqualTo(List.of(observed.candidates().get(0), observed.candidates().get(1)));
		assertThat(xmi(observed.resource())).isEqualTo(xmi(baseline.resource()));
		final var counters = probe.features().values().iterator().next();
		assertThat(counters.calls).isGreaterThan(3);
		assertThat(counters.rejectedUnknown).isZero();
		assertThat(Diagnostician.INSTANCE.validate(observed.owner()).getSeverity()).isZero();
	}

	@Test
	void selfRejectionAndAllowedSelfAreObservedWithoutPolicyReevaluation() {
		final var model = EMFTestUtils.loadEPackage("inputs/self-reference.ecore");
		final var graph = graph(model, "Node", "next", 0);
		final var probe = new SelectionAttemptProbe();
		assertThat(probe.selectValue(graph.owner(), graph.reference())).isNull();
		final var counters = probe.features().values().iterator().next();
		assertThat(counters.exhausted).isEqualTo(1);
		assertThat(counters.attempts).isEqualTo(1);
		assertThat(counters.rejectedUnknown).isEqualTo(1);
		probe.setSelfReferencePolicy((owner, reference) -> true);
		assertThat(probe.selectValue(graph.owner(), graph.reference())).isSameAs(graph.owner());
		assertThat(counters.selected).isEqualTo(1);
		probe.setFunctionFor(graph.reference(), owner -> graph.owner());
		assertThat(probe.selectValue(graph.owner(), graph.reference())).isSameAs(graph.owner());
		assertThat(counters.customSelected).isEqualTo(1);
		assertThat(counters.attempts).isEqualTo(2);
	}

	@Test
	void saturatedOppositeAndFeatureMapReferenceKeepCandidateOrder() throws Exception {
		final var model = EMFTestUtils.loadEPackage("inputs/featuremap_mixed.ecore");
		final var baseline = graph(model, "Document", "related", 2);
		final var observed = graph(model, "Document", "related", 2);
		final var otherClass = (EClass) model.getEClassifier("Document");
		final var baseOther = EcoreUtil.create(otherClass);
		final var probeOther = EcoreUtil.create(otherClass);
		baseline.resource().getContents().add(baseOther);
		observed.resource().getContents().add(probeOther);
		final var opposite = baseline.reference().getEOpposite();
		baseline.candidates().getFirst().eSet(opposite, baseOther);
		observed.candidates().getFirst().eSet(opposite, probeOther);
		final var baseSetter = new EMFCrossReferenceSetter();
		final var probe = new SelectionAttemptProbe();
		assertThat(baseSetter.selectValue(baseline.owner(), baseline.reference()))
				.isSameAs(baseline.candidates().get(1));
		assertThat(probe.selectValue(observed.owner(), observed.reference()))
				.isSameAs(observed.candidates().get(1));
		assertThat(probe.features().values().iterator().next().attempts).isEqualTo(2);
		assertThat(probe.features().values().iterator().next().rejectedUnknown).isEqualTo(1);
		baseSetter.setCrossReference(baseline.owner(), baseline.reference());
		probe.setCrossReference(observed.owner(), observed.reference());
		final var group = (FeatureMap) observed.owner().eGet(
				observed.owner().eClass().getEStructuralFeature("group"));
		assertThat(group.stream().filter(entry -> entry.getEStructuralFeature()
				.equals(observed.reference())).count()).isEqualTo(1);
		assertThat(observed.candidates().get(1).eGet(opposite)).isSameAs(observed.owner());
		assertThat(Diagnostician.INSTANCE.validate(observed.owner()).getSeverity()).isZero();
		assertThat(xmi(observed.resource())).isEqualTo(xmi(baseline.resource()));
	}

	@Test
	void boundedManyValuedOppositeRejectsFirstCandidateWithoutChangingSelection() {
		final var model = EMFTestUtils.loadEPackage("inputs/standalone-references.ecore");
		final var baseline = graph(model, "Document", "relatedDocs", 2);
		final var observed = graph(model, "Document", "relatedDocs", 2);
		final var opposite = baseline.reference().getEOpposite();
		opposite.setUpperBound(2);
		final var occupiedBase = EcoreUtil.create((EClass) model.getEClassifier("Document"));
		final var occupiedProbe = EcoreUtil.create((EClass) model.getEClassifier("Document"));
		final var occupiedBaseTwo = EcoreUtil.create((EClass) model.getEClassifier("Document"));
		final var occupiedProbeTwo = EcoreUtil.create((EClass) model.getEClassifier("Document"));
		baseline.resource().getContents().add(occupiedBase);
		observed.resource().getContents().add(occupiedProbe);
		baseline.resource().getContents().add(occupiedBaseTwo);
		observed.resource().getContents().add(occupiedProbeTwo);
		((List<EObject>) baseline.candidates().getFirst().eGet(opposite)).add(occupiedBase);
		((List<EObject>) observed.candidates().getFirst().eGet(opposite)).add(occupiedProbe);
		((List<EObject>) baseline.candidates().getFirst().eGet(opposite)).add(occupiedBaseTwo);
		((List<EObject>) observed.candidates().getFirst().eGet(opposite)).add(occupiedProbeTwo);
		final var ordinary = new EMFCrossReferenceSetter();
		final var probe = new SelectionAttemptProbe();
		assertThat(ordinary.selectValue(baseline.owner(), baseline.reference()))
				.isSameAs(baseline.candidates().get(1));
		assertThat(probe.selectValue(observed.owner(), observed.reference()))
				.isSameAs(observed.candidates().get(1));
		assertThat(probe.features().values().iterator().next().attempts).isEqualTo(2);
		assertThat(probe.features().values().iterator().next().rejectedUnknown).isEqualTo(1);
	}
}
