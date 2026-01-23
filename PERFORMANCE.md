# EMF Model Generator Performance Tests

This document describes how to run performance tests for the EMF Model Generator.

## Overview

The performance tests measure how execution time increases with different metrics:
- **Generation Depth**: How performance scales with containment hierarchy depth
- **Number of Models**: How performance scales with the number of instances generated

Results are presented in both table format and CSV format for easy analysis.

## Running Performance Tests

### Basic Execution

Run with default configuration:

```bash
./mvnw test-compile exec:java@run-performance-tests -Pperformance-tests
```

Default configuration:
- Depth range: 1-10 (step: 1)
- Models range: 1, 100, 200, ..., 1000 (step: 100)
- Repetitions: 3
- Warmup iterations: 10
- Metamodel: `src/test/resources/inputs/extlibrary.ecore`

### Custom Configuration via Maven Properties

Override specific parameters:

```bash
# Run with reduced ranges for quick testing
./mvnw test-compile exec:java@run-performance-tests -Pperformance-tests \
  -Ddepth.max=10 \
  -Dmodels.max=100 \
  -Drepetitions=2

# Run with specific depth range
./mvnw test-compile exec:java@run-performance-tests -Pperformance-tests \
  -Ddepth.min=1 \
  -Ddepth.max=5 \
  -Ddepth.step=1

# Run with different metamodel
./mvnw test-compile exec:java@run-performance-tests -Pperformance-tests \
  -Dmetamodel=src/test/resources/inputs/library.ecore
```

Available properties:
- `depth.min` - Minimum depth (default: 1)
- `depth.max` - Maximum depth (default: 10)
- `depth.step` - Depth step size (default: 1)
- `models.min` - Minimum number of models (default: 1)
- `models.max` - Maximum number of models (default: 1000)
- `models.step` - Models step size (default: 100)
- `repetitions` - Number of repetitions per measurement (default: 3)
- `warmup` - Number of warmup iterations (default: 10)
- `metamodel` - Path to metamodel file (default: extlibrary.ecore)

### Running Directly with Java

You can also run the performance test class directly:

```bash
# Compile test classes first
./mvnw test-compile

# Run with default configuration
./mvnw exec:java -Dexec.classpathScope=test \
  -Dexec.mainClass=io.github.lorenzobettini.emfmodelgenerator.performance.EMFModelGeneratorPerformanceStatistics

# Run with custom arguments
./mvnw exec:java -Dexec.classpathScope=test \
  -Dexec.mainClass=io.github.lorenzobettini.emfmodelgenerator.performance.EMFModelGeneratorPerformanceStatistics \
  -Dexec.args="--depth-max 10 --models-max 50 --repetitions 5"

# Show help
./mvnw exec:java -Dexec.classpathScope=test \
  -Dexec.mainClass=io.github.lorenzobettini.emfmodelgenerator.performance.EMFModelGeneratorPerformanceStatistics \
  -Dexec.args="--help"
```

## Command-Line Arguments

When running directly with Java, you can use these arguments:

| Argument | Description | Default |
|----------|-------------|---------|
| `--depth-min <n>` | Minimum depth | 1 |
| `--depth-max <n>` | Maximum depth | 20 |
| `--depth-step <n>` | Depth step size | 1 |
| `--models-min <n>` | Minimum number of models | 1 |
| `--models-max <n>` | Maximum number of models | 200 |
| `--models-step <n>` | Models step size | 10 |
| `--repetitions <n>` | Number of repetitions per measurement | 3 |
| `--warmup <n>` | Number of warmup iterations | 10 |
| `--metamodel <path>` | Path to metamodel file | extlibrary.ecore |
| `--help` | Show help message | - |

## Understanding the Output

### Console Output

The performance tests output results in two formats:

1. **Table Format**: Human-readable table with columns:
   - Metric value (Depth or Models)
   - Objects: Total number of EObjects generated (including nested)
   - Avg (ms): Average execution time
   - Min (ms): Minimum execution time
   - Max (ms): Maximum execution time
   - StdDev (ms): Standard deviation

2. **CSV Format**: Easy to import into spreadsheet tools (Excel, Google Sheets, etc.)
   - Same columns as table format
   - Can be copied directly into a spreadsheet

### Example Output

```
================================================================================
MEASUREMENT 1: Generation Depth Performance
================================================================================
Measuring how execution time increases with containment depth
Baseline: 10 instances per EClass

Depth:   1 | Objects:    140 | Avg:     3.20 ms | Min:     2.57 ms | Max:     3.83 ms | StdDev:     0.63 ms
Depth:   2 | Objects:    260 | Avg:     2.97 ms | Min:     2.76 ms | Max:     3.17 ms | StdDev:     0.21 ms
Depth:   3 | Objects:    500 | Avg:     5.17 ms | Min:     5.10 ms | Max:     5.24 ms | StdDev:     0.07 ms

CSV FORMAT:
Depth,Objects,Avg_ms,Min_ms,Max_ms,StdDev_ms
1,140,3.20,2.57,3.83,0.63
2,260,2.97,2.76,3.17,0.21
3,500,5.17,5.10,5.24,0.07
```

### Analyzing Results

To analyze the results:

1. **Copy CSV output** to a spreadsheet tool
2. **Create charts** to visualize trends:
   - Line chart: Metric (x-axis) vs Avg Time (y-axis)
   - Scatter plot: Objects (x-axis) vs Avg Time (y-axis)
3. **Check standard deviation** to assess measurement stability
   - Low StdDev: Consistent measurements
   - High StdDev: May need more repetitions or longer warmup

## Baseline Configuration

The performance tests use these baseline values when measuring one metric independently:

- **When measuring depth**: Fixed 10 instances per EClass
- **When measuring models**: Fixed depth of 3

This ensures each metric is measured independently while keeping other factors constant.

## Tips for Running Performance Tests

1. **Close unnecessary applications** to reduce system noise
2. **Run multiple times** to verify consistency
3. **Increase warmup iterations** (e.g., `--warmup 20`) for more stable results
4. **Start with small ranges** to estimate total runtime
5. **Use reduced ranges** for quick testing during development

## Example Workflows

### Testing Specific Metamodel

```bash
./mvnw test-compile exec:java@run-performance-tests -Pperformance-tests \
  -Dmetamodel=src/test/resources/inputs/library.ecore \
  -Ddepth.max=10 \
  -Dmodels.max=100
```

## Redirecting Output to File

Save results to a file for later analysis:

```bash
./mvnw test-compile exec:java@run-performance-tests -Pperformance-tests > performance-results.txt 2>&1

# Or just save the important parts
./mvnw test-compile exec:java@run-performance-tests -Pperformance-tests 2>&1 | tee performance-results.txt
```

Extract just the CSV data:

```bash
./mvnw test-compile exec:java@run-performance-tests -Pperformance-tests 2>&1 | grep -A 100 "CSV FORMAT:" > results.csv
```

## Troubleshooting

### OutOfMemoryError

If you encounter memory issues with large ranges:

```bash
# Increase heap size
export MAVEN_OPTS="-Xmx4g"
./mvnw test-compile exec:java@run-performance-tests -Pperformance-tests
```

### Slow Execution

For very large ranges, tests may take considerable time. Consider:
- Reducing the range or step size
- Decreasing repetitions
- Using a simpler metamodel

### Inconsistent Results

If results vary significantly between runs:
- Increase warmup iterations
- Increase number of repetitions
- Close other applications
- Run on a less loaded system
