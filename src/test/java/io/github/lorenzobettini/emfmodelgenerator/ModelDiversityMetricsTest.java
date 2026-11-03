package io.github.lorenzobettini.emfmodelgenerator;

import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEAttributeExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEClassExists;
import static io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils.assertEReferenceExists;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Metric sensitivity checks using the real normalized BibTeX metamodel.
 *
 * @author Lorenzo Bettini
 */
class ModelDiversityMetricsTest {

	private EMFModelGenerator generator;
	private EPackage bibtex;

	@BeforeEach
	void loadMetamodel() throws IOException {
		generator = new EMFModelGenerator();
		bibtex = generator.loadEcoreModelPackages(EMFModelGeneratorDiversityTest.METAMODEL)
				.stream().filter(p -> "BIBTEX".equals(p.getName())).findFirst().orElseThrow();
	}

	@AfterEach
	void unloadMetamodel() {
		generator.unloadEcoreModels();
	}

	@Test
	void attributeChangesAffectOnlyValuesAndPreserveDelimiterBoundaries() {
		var book = create("Book");
		var key = assertEAttributeExists(book.eClass(), "key");
		var original = measure(book);
		book.eSet(key, "a:1,b\"\n");
		var changed = measure(book);
		assertThat(changed.values()).isNotEqualTo(original.values());
		assertThat(changed.types()).isEqualTo(original.types());
		assertThat(changed.topology()).isEqualTo(original.topology());
		assertThat(changed.objectCount()).isEqualTo(1);
		assertThat(measure(EcoreUtil.copy(book))).isEqualTo(changed);
		book.eSet(key, "");
		assertThat(measure(book).values()).isNotEqualTo(original.values())
				.isNotEqualTo(changed.values());
		book.eUnset(key);
		assertThat(measure(book)).isEqualTo(original);
	}

	@Test
	void changingConcreteTypesPreservesTopologyAndInheritedAttributeSignature() {
		var book = create("Book");
		var article = create("Article");
		var bookMetrics = measure(book);
		var articleMetrics = measure(article);
		assertThat(articleMetrics.types()).isNotEqualTo(bookMetrics.types());
		assertThat(articleMetrics.values()).isEqualTo(bookMetrics.values());
		assertThat(articleMetrics.topology()).isEqualTo(bookMetrics.topology());
		assertThat(bookMetrics.typeCounts()).containsOnlyKeys(
				"http://example.org/atlantic-zoo/bibtex#//Book");
	}

	@Test
	void topologyDetectsChildPlacementAndRootCountsEvenWithEqualObjectCounts() {
		var root = create("Bibtex");
		var first = create("Book");
		var second = create("Book");
		var field = create("Title");
		var entries = assertEReferenceExists(root.eClass(), "entries");
		var fields = assertEReferenceExists(first.eClass(), "fields");
		EMFUtils.getAsEObjectsList(root, entries).addAll(List.of(first, second));
		EMFUtils.getAsEObjectsList(first, fields).add(field);
		var original = measure(root);
		EMFUtils.getAsEObjectsList(second, fields).add(field);
		var moved = measure(root);
		assertThat(moved.objectCount()).isEqualTo(4).isEqualTo(original.objectCount());
		assertThat(moved.topology()).isNotEqualTo(original.topology());
		assertThat(moved.typeCounts()).isEqualTo(original.typeCounts());
		EMFUtils.getAsEObjectsList(second, fields).clear();
		assertThat(measure(root).objectCount()).isEqualTo(3);
		assertThat(measure(root).topology()).isNotEqualTo(moved.topology());
		var twoRoots = ModelDiversityMetrics.measure(List.of(root, field));
		assertThat(twoRoots.objectCount()).isEqualTo(original.objectCount());
		assertThat(twoRoots.topology()).isNotEqualTo(original.topology());
		assertThat(measure(EcoreUtil.copy(root))).isEqualTo(measure(root));
	}

	private EObject create(final String name) {
		return EcoreUtil.create(assertEClassExists(bibtex, name));
	}

	private static ModelDiversityMetrics.Snapshot measure(final EObject root) {
		return ModelDiversityMetrics.measure(List.of(root));
	}
}
