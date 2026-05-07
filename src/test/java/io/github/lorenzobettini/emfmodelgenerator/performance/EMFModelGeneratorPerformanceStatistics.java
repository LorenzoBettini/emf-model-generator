package io.github.lorenzobettini.emfmodelgenerator.performance;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.util.EcoreUtil;

import io.github.lorenzobettini.emfmodelgenerator.EMFModelGenerator;
import io.github.lorenzobettini.emfmodelgenerator.EMFTestUtils;

/**
 * Performance statistics for EMFModelGenerator.
 * 
 * This class measures how execution time increases with different metrics:
 * - Number of containment generation depth
 * - Number of models to be generated
 * 
 * Results are presented in table format and CSV format for easy import into
 * spreadsheet tools.
 */
public class EMFModelGeneratorPerformanceStatistics {

	// Default configuration constants
	private static final int DEFAULT_DEPTH_MIN = 1;
	private static final int DEFAULT_DEPTH_MAX = 10;
	private static final int DEFAULT_DEPTH_STEP = 1;
	
	private static final int DEFAULT_MODELS_MIN = 1;
	private static final int DEFAULT_MODELS_MAX = 1000;
	private static final int DEFAULT_MODELS_STEP = 100;
	
	private static final int DEFAULT_REPETITIONS = 3;
	private static final int DEFAULT_WARMUP_ITERATIONS = 10;
	
	private static final String DEFAULT_METAMODEL = "src/test/resources/inputs/extlibrary.ecore";
	
	// Baseline configuration
	private static final int BASELINE_DEPTH = 3;
	private static final int BASELINE_INSTANCES = 10;
	
	// Configuration fields
	private int depthMin = DEFAULT_DEPTH_MIN;
	private int depthMax = DEFAULT_DEPTH_MAX;
	private int depthStep = DEFAULT_DEPTH_STEP;
	
	private int modelsMin = DEFAULT_MODELS_MIN;
	private int modelsMax = DEFAULT_MODELS_MAX;
	private int modelsStep = DEFAULT_MODELS_STEP;
	
	private int repetitions = DEFAULT_REPETITIONS;
	private int warmupIterations = DEFAULT_WARMUP_ITERATIONS;
	
	private String metamodelPath = DEFAULT_METAMODEL;
	
	private EPackage ePackage;

	/**
	 * Measurement result for a single data point.
	 */
	private static class MeasurementResult {
		int metricValue;
		int objectCount;
		double avgTimeMs;
		double minTimeMs;
		double maxTimeMs;
		double stdDevMs;
	}

	public static void main(String[] args) {
		var stats = new EMFModelGeneratorPerformanceStatistics();
		stats.parseArguments(args);
		stats.run();
	}

	private void parseArguments(String[] args) {
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--depth-min":
					depthMin = Integer.parseInt(args[++i]);
					break;
				case "--depth-max":
					depthMax = Integer.parseInt(args[++i]);
					break;
				case "--depth-step":
					depthStep = Integer.parseInt(args[++i]);
					break;
				case "--models-min":
					modelsMin = Integer.parseInt(args[++i]);
					break;
				case "--models-max":
					modelsMax = Integer.parseInt(args[++i]);
					break;
				case "--models-step":
					modelsStep = Integer.parseInt(args[++i]);
					break;
				case "--repetitions":
					repetitions = Integer.parseInt(args[++i]);
					break;
				case "--warmup":
					warmupIterations = Integer.parseInt(args[++i]);
					break;
				case "--metamodel":
					metamodelPath = args[++i];
					break;
				case "--help":
					printHelp();
					System.exit(0);
					break;
				default:
					System.err.println("Unknown argument: " + args[i]);
					printHelp();
					System.exit(1);
			}
		}
	}

	private void printHelp() {
		System.out.println("EMF Model Generator Performance Statistics");
		System.out.println();
		System.out.println("Usage: java EMFModelGeneratorPerformanceStatistics [options]");
		System.out.println();
		System.out.println("Options:");
		System.out.println("  --depth-min <n>      Minimum depth (default: " + DEFAULT_DEPTH_MIN + ")");
		System.out.println("  --depth-max <n>      Maximum depth (default: " + DEFAULT_DEPTH_MAX + ")");
		System.out.println("  --depth-step <n>     Depth step size (default: " + DEFAULT_DEPTH_STEP + ")");
		System.out.println("  --models-min <n>     Minimum number of models (default: " + DEFAULT_MODELS_MIN + ")");
		System.out.println("  --models-max <n>     Maximum number of models (default: " + DEFAULT_MODELS_MAX + ")");
		System.out.println("  --models-step <n>    Models step size (default: " + DEFAULT_MODELS_STEP + ")");
		System.out.println("  --repetitions <n>    Number of repetitions per measurement (default: " + DEFAULT_REPETITIONS + ")");
		System.out.println("  --warmup <n>         Number of warmup iterations (default: " + DEFAULT_WARMUP_ITERATIONS + ")");
		System.out.println("  --metamodel <path>   Path to metamodel file (default: " + DEFAULT_METAMODEL + ")");
		System.out.println("  --help               Show this help message");
	}

	private void run() {
		System.out.println("=".repeat(80));
		System.out.println("EMF MODEL GENERATOR PERFORMANCE STATISTICS");
		System.out.println("=".repeat(80));
		System.out.println();
		
		printConfiguration();
		
		// Load metamodel
		loadMetamodel();
		
		// Warm up JVM
		warmUp();
		
		// Run measurements
		System.out.println("\nRunning performance measurements...\n");
		measureDepthPerformance();
		measureModelsPerformance();
		
		System.out.println("\n" + "=".repeat(80));
		System.out.println("Performance measurements completed.");
		System.out.println("=".repeat(80));
	}

	private void printConfiguration() {
		System.out.println("Configuration:");
		System.out.println("-".repeat(80));
		System.out.println("Metamodel:           " + metamodelPath);
		System.out.println("Repetitions:         " + repetitions);
		System.out.println("Warmup iterations:   " + warmupIterations);
		System.out.println();
		System.out.println("Depth measurement:");
		System.out.println("  Range:             " + depthMin + "-" + depthMax + " (step: " + depthStep + ")");
		System.out.println("  Baseline instances: " + BASELINE_INSTANCES);
		System.out.println();
		System.out.println("Models measurement:");
		System.out.println("  Range:             " + modelsMin + "-" + modelsMax + " (step: " + modelsStep + ")");
		System.out.println("  Baseline depth:    " + BASELINE_DEPTH);
		System.out.println("-".repeat(80));
	}

	private void loadMetamodel() {
		System.out.println("\nLoading metamodel: " + metamodelPath);
		var file = new File(metamodelPath);
		if (!file.exists()) {
			throw new IllegalArgumentException("Metamodel file not found: " + metamodelPath);
		}
		
		ePackage = EMFTestUtils.loadEcoreModel(file.getParent(), file.getName());
		System.out.println("Metamodel loaded: " + ePackage.getName());
	}

	private void warmUp() {
		System.out.println("\nWarming up JVM with " + warmupIterations + " iterations...");
		
		for (int i = 0; i < warmupIterations; i++) {
			var generator = new EMFModelGenerator();
			generator.getInstancePopulator().setMaxDepth(2);
			generator.setNumberOfInstances(5);
			generator.generateAllFrom(ePackage);
		}
		
		System.out.println("Warmup completed.");
	}

	private void measureDepthPerformance() {
		System.out.println("\n" + "=".repeat(80));
		System.out.println("MEASUREMENT 1: Generation Depth Performance");
		System.out.println("=".repeat(80));
		System.out.println("Measuring how execution time increases with containment depth");
		System.out.println("Baseline: " + BASELINE_INSTANCES + " instances per EClass");
		System.out.println();
		
		var results = new ArrayList<MeasurementResult>();
		
		for (int depth = depthMin; depth <= depthMax; depth += depthStep) {
			var result = measureDepth(depth);
			results.add(result);
			
			System.out.printf("Depth: %3d | Objects: %6d | Avg: %8.2f ms | Min: %8.2f ms | Max: %8.2f ms | StdDev: %8.2f ms%n",
					result.metricValue, result.objectCount,
					result.avgTimeMs, result.minTimeMs, result.maxTimeMs, result.stdDevMs);
		}
		
		System.out.println();
		printTableFormat("Depth", results);
		System.out.println();
		printCSVFormat("Depth", results);
	}

	private MeasurementResult measureDepth(int depth) {
		var times = new ArrayList<Double>();
		int objectCount = 0;
		
		for (int i = 0; i < repetitions; i++) {
			var generator = new EMFModelGenerator();
			generator.getInstancePopulator().setMaxDepth(depth);
			generator.setNumberOfInstances(BASELINE_INSTANCES);
			
			var startTime = System.nanoTime();
			var models = generator.generateAllFrom(ePackage);
			var endTime = System.nanoTime();
			
			var timeMs = (endTime - startTime) / 1_000_000.0;
			times.add(timeMs);
			
			// Count objects only once
			if (i == 0) {
				objectCount = countAllObjects(models);
			}
		}
		
		return computeStatistics(depth, objectCount, times);
	}

	private void measureModelsPerformance() {
		System.out.println("\n" + "=".repeat(80));
		System.out.println("MEASUREMENT 2: Number of Models Performance");
		System.out.println("=".repeat(80));
		System.out.println("Measuring how execution time increases with number of instances");
		System.out.println("Baseline: depth " + BASELINE_DEPTH);
		System.out.println();
		
		var results = new ArrayList<MeasurementResult>();
		
		// First measure with modelsMin, then continue with multiples of modelsStep
		// This gives us: 1, 100, 200, 300, ... instead of 1, 101, 201, 301, ...
		var result = measureModels(modelsMin);
		results.add(result);
		System.out.printf("Models: %4d | Objects: %7d | Avg: %8.2f ms | Min: %8.2f ms | Max: %8.2f ms | StdDev: %8.2f ms%n",
				result.metricValue, result.objectCount,
				result.avgTimeMs, result.minTimeMs, result.maxTimeMs, result.stdDevMs);
		
		// Continue with multiples of modelsStep
		for (int numModels = modelsStep; numModels <= modelsMax; numModels += modelsStep) {
			result = measureModels(numModels);
			results.add(result);
			
			System.out.printf("Models: %4d | Objects: %7d | Avg: %8.2f ms | Min: %8.2f ms | Max: %8.2f ms | StdDev: %8.2f ms%n",
					result.metricValue, result.objectCount,
					result.avgTimeMs, result.minTimeMs, result.maxTimeMs, result.stdDevMs);
		}
		
		System.out.println();
		printTableFormat("Models", results);
		System.out.println();
		printCSVFormat("Models", results);
	}

	private MeasurementResult measureModels(int numInstances) {
		var times = new ArrayList<Double>();
		int objectCount = 0;
		
		for (int i = 0; i < repetitions; i++) {
			var generator = new EMFModelGenerator();
			generator.getInstancePopulator().setMaxDepth(BASELINE_DEPTH);
			generator.setNumberOfInstances(numInstances);
			
			var startTime = System.nanoTime();
			var models = generator.generateAllFrom(ePackage);
			var endTime = System.nanoTime();
			
			var timeMs = (endTime - startTime) / 1_000_000.0;
			times.add(timeMs);
			
			// Count objects only once
			if (i == 0) {
				objectCount = countAllObjects(models);
			}
		}
		
		return computeStatistics(numInstances, objectCount, times);
	}

	private int countAllObjects(List<EObject> roots) {
		int count = roots.size();
		for (var root : roots) {
			var iterator = EcoreUtil.getAllContents(root, true);
			while (iterator.hasNext()) {
				iterator.next();
				count++;
			}
		}
		return count;
	}

	private MeasurementResult computeStatistics(int metricValue, int objectCount, List<Double> times) {
		var stats = times.stream()
				.mapToDouble(Double::doubleValue)
				.summaryStatistics();
		
		var result = new MeasurementResult();
		result.metricValue = metricValue;
		result.objectCount = objectCount;
		result.avgTimeMs = stats.getAverage();
		result.minTimeMs = stats.getMin();
		result.maxTimeMs = stats.getMax();
		
		// Calculate standard deviation
		var mean = stats.getAverage();
		var variance = times.stream()
				.mapToDouble(t -> Math.pow(t - mean, 2))
				.sum() / times.size();
		result.stdDevMs = Math.sqrt(variance);
		
		return result;
	}

	private void printTableFormat(String metricName, List<MeasurementResult> results) {
		System.out.println("TABLE FORMAT:");
		System.out.println("-".repeat(100));
		System.out.printf("%-10s | %-12s | %-12s | %-12s | %-12s | %-12s%n",
				metricName, "Objects", "Avg (ms)", "Min (ms)", "Max (ms)", "StdDev (ms)");
		System.out.println("-".repeat(100));
		
		for (var result : results) {
			System.out.printf("%-10d | %-12d | %12.2f | %12.2f | %12.2f | %12.2f%n",
					result.metricValue, result.objectCount,
					result.avgTimeMs, result.minTimeMs, result.maxTimeMs, result.stdDevMs);
		}
		
		System.out.println("-".repeat(100));
	}

	private void printCSVFormat(String metricName, List<MeasurementResult> results) {
		System.out.println("CSV FORMAT:");
		System.out.println("-".repeat(100));
		System.out.printf("%s,Objects,Avg_ms,Min_ms,Max_ms,StdDev_ms%n", metricName);
		
		for (var result : results) {
			System.out.printf("%d,%d,%.2f,%.2f,%.2f,%.2f%n",
					result.metricValue, result.objectCount,
					result.avgTimeMs, result.minTimeMs, result.maxTimeMs, result.stdDevMs);
		}
		
		System.out.println("-".repeat(100));
	}
}
