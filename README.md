# JavaLens

An AST-based static analyzer for Java source repositories. Scans folders recursively, parsing file constructs using the standard Java Compiler Tree API. Includes directory-level analysis, diff comparison between versions, marker-guided merging, and a fully interactive terminal CLI wizard.

---

## Features

* **Codebase Analysis**: Recursively extracts Java class attributes and overloaded method signatures into structured CSV datasets.
* **Diff Comparator**: Evaluates attributes and method signatures between two codebases (or two files), generating delta reports (`comparison_attributes.csv` and `comparison_methods.csv`).
* **Marker-Guided Merge**: Selectively copies blocks of code from a source repository (new version) to a destination repository (old version) governed by custom markers.
* **Interactive CLI Wizard**: Configure modes, paths, thread sizes, and markers, monitor execution status, and view formatted CSV report tables directly in the terminal interface.

---

## Requirements

* **JDK 17 or higher** (JDK is required to support the Compiler APIs).
* No external dependencies — compiled entirely using standard JDK libraries.

---

## Quick Start

### 1. Build the JAR
Run the appropriate compiler script in your terminal:
```bash
# Linux / macOS
./build.sh

# Windows
build.bat
```

### 2. Launch the Interactive CLI Wizard (Recommended)
Start the terminal guide by running with no arguments, or pass `-i` / `--interactive`:
```bash
# Run wizard
./run.sh -i

# Or simply run with no arguments
./run.sh
```
This boots up the interactive setup, monitors parses in real-time, and outputs formatted color-coded ASCII summaries directly into the console!

### 3. Run directly from the CLI (Non-interactive)
You can launch any mode directly via the command line options:

* **Analyze Mode**: Scans repository AST.
  ```bash
  ./run.sh --mode analyze --source /path/to/src --output-dir ./java_analysis_output
  ```
* **Compare Mode**: Generates a delta report of changes between two versions.
  ```bash
  ./run.sh --mode compare --old /path/to/old/src --new /path/to/new/src --output-dir ./diff_report
  ```
* **Merge Mode**: Merges code blocks between markers (default: `// START_MERGE` and `// END_MERGE`) from new to old source.
  ```bash
  ./run.sh --mode merge --old /path/to/old/src --new /path/to/new/src --start-marker "// START_MERGE" --end-marker "// END_MERGE"
  ```

---

## Testing the Package

We have provided a turnkey testing workspace containing test sources and an automated script:
```bash
# Linux / macOS
chmod +x test.sh
./test.sh

# Windows
test.bat
```
This automatically compiles the codebase, scans test assets, runs diff delta validations, performs a code block merge, and outputs the resulting reports directly to your console.

---

## Scalability & Performance

JavaLens features a parallelized parser built on Java's `ExecutorService` and structured as a producer-consumer model:
* **Memory Management**: Rather than loading all file syntax trees into the JVM heap simultaneously, trees are discarded and garbage-collected as soon as their extracted signatures are sent to the output writer queue.
* **No File Count Limit**: The stream-based architecture opens and releases files sequentially, allowing the application to process **hundreds of thousands of classes** without running out of memory or OS file handles.

### Performance Benchmarks (8-Core CPU, SSD)

| Codebase Size (Classes) | Memory Allocation | Processing Duration |
| :--- | :--- | :--- |
| **500 classes** | ~100 MB heap | ~2 seconds |
| **5,000 classes** | ~250 MB heap | ~15–30 seconds |
| **20,000 classes** | ~500 MB heap | ~1–2 minutes |
| **100,000+ classes** | ~1 GB heap | ~5–7 minutes |

Use the `--threads` (`-t`) option to fine-tune parallelism.

---

## Configuration (`analyzer.properties`)

Fine-tune operations and disable/enable components in `analyzer.properties` (or edit these values directly in the wizard config editor menu):
```properties
# Enable/disable enhancements
enhancement.compare.enabled=true
enhancement.merge.enabled=true

# Custom merging markers
merge.start_marker=// START_MERGE
merge.end_marker=// END_MERGE

# Default directories and network settings
compare.output_dir=java_analysis_output
threads=8
```

---

## Output Formats

All outputs are structured as standard CSVs under your defined output folder.

### Analysis Reports
* **`java_attributes.csv`**: Contains `file`, `package`, `class`, `attribute_name`, `attribute_type`, `modifiers`, `annotations`, and `initializer`.
* **`java_methods.csv`**: Contains `file`, `package`, `class`, `method_name`, `return_type`, `modifiers`, `parameters`, `parameter_count`, `throws`, `annotations`, and `kind` (`method` or `constructor`).

### Comparison Reports
* **`comparison_attributes.csv`** & **`comparison_methods.csv`**:
  * `status`: Indicates what changed (`ADDED`, `REMOVED`, or `MODIFIED`).
  * `class`: Fully qualified name of the class.
  * `attribute_name` / `method_signature`: Unique identifier of the field or signature.
  * `old_type` / `old_details`: Field type or signature parameters in the old codebase.
  * `new_type` / `new_details`: Field type or signature parameters in the new codebase.

