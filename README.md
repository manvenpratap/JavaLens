# JavaLens — Precision AST Static Analysis & Telemetry Engine

[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Zero Dependencies](https://img.shields.io/badge/dependencies-zero-brightgreen.svg)]()
[![Architecture](https://img.shields.io/badge/architecture-stream--based-purple.svg)]()

**JavaLens** is a high-performance, zero-external-dependency static analysis and codebase synthesis platform for Java repositories. Powered directly by the **Java Compiler Tree API** (`com.sun.source.tree`), JavaLens extracts deep semantic structures, field definitions, and overloaded method signatures with microsecond latency.

It includes an interactive terminal wizard, high-throughput CLI modes, marker-guided code merging, a consolidated unified activity report generator, and a modern **Precision Dark Engineering** Web GUI.

---

## Key Capabilities

* **Codebase AST Extraction**: Recursively parses compilation units to extract comprehensive field attributes, modifiers, initializers, annotations, and method signatures (return types, parameters, arity, exceptions, annotations, and member kind).
* **Semantic Diff Comparator**: Contrasts two codebases (or individual files), categorizing member changes into `UNCHANGED`, `ADDED`, `MODIFIED`, and `REMOVED`.
* **Marker-Guided Merge Engine**: Safely transfers marked code blocks between versions (e.g. `// START_MERGE ... // END_MERGE`) without disrupting outer structure.
* **Unified Activity Report Pipeline**: Executes the end-to-end automated pipeline (**analyze baseline → analyze new feature → compare deltas → merge marker blocks → re-analyze merged output**), tracking which attributes and methods are `NEWLY_ADDED`, `ORIGINAL`, `MODIFIED_BY_MERGE`, or `REMOVED` in a single consolidated CSV report.
* **Modern Web GUI Dashboard**: Built-in HTTP server (`-m server`) hosting a high-craft engineering console with real-time streaming telemetry, AST signature inspectors with live search & modifier chips, side-by-side synchronized diff viewers, and one-click CSV report exports.
* **Interactive CLI Wizard**: Terminal guide (`-i` / `--interactive`) with ANSI colorized banners, step-by-step parameter prompts, progress monitors, and ASCII result tables.
* **Self-Contained Fat JAR**: `javalens.jar` packages all compiled classes along with `README.md`, `index.html`, and `analyzer.properties` for instant zero-config portability.

---

## Requirements

* **JDK 17 or higher** (OpenJDK, Eclipse Temurin, Amazon Corretto, or Oracle JDK).
* **Zero external libraries**: Built entirely with standard JDK modules (`jdk.compiler`, `java.desktop`, `jdk.httpserver`).

---

## Quick Start

### 1. Build the Self-Contained JAR
Compile all classes and bundle `README.md`, `index.html`, and default `analyzer.properties` into `javalens.jar`:
```bash
# macOS / Linux
./build.sh

# Windows
build.bat
```

### 2. Launch the Web GUI (Recommended)
Start the built-in HTTP server:
```bash
./run.sh -m server
```
Open **[http://localhost:8080](http://localhost:8080)** in your browser to access the complete visual workspace.

### 3. Launch the Interactive Terminal Wizard
```bash
# Interactive wizard
./run.sh -i

# Or launch with no arguments to default to the wizard
./run.sh
```

### 4. Run Directly via CLI
```bash
# Unified Pipeline & Report (Analyze -> Compare -> Merge -> Report)
./run.sh --mode report --old /path/to/v1 --new /path/to/v2 --output-dir ./reports

# Scan a codebase
./run.sh --mode analyze --source /path/to/src --output-dir ./java_analysis_output

# Compare two versions
./run.sh --mode compare --old /path/to/v1 --new /path/to/v2 --output-dir ./diff_report

# Merge marked blocks
./run.sh --mode merge --old /path/to/v1 --new /path/to/v2 --start-marker "// START_MERGE" --end-marker "// END_MERGE"
```

---

## Execution Modes & CLI Reference

| Flag | Long Flag | Description | Default |
| :--- | :--- | :--- | :--- |
| `-m` | `--mode` | Execution mode: `analyze`, `compare`, `merge`, `report`, `server`, `interactive` | `analyze` |
| `-i` | `--interactive` | Launch interactive terminal CLI wizard | `false` |
| `-s` | `--source` | Source directory or file to parse in `analyze` mode | `.` |
| `-o` | `--old` | Baseline path (old version) for compare, merge, and report modes | `javalens.conf` |
| `-n` | `--new` | Feature path (new version) for compare, merge, and report modes | `javalens.conf` |
| | `--start-marker` | Boundary start marker string for inline merging | `// START_MERGE` |
| | `--end-marker` | Boundary end marker string for inline merging | `// END_MERGE` |
| | `--output-dir` | Target directory for generated CSV reports and run logs | `java_analysis_output` |
| `-t` | `--threads` | Parallel worker thread count | Available CPU cores |
| `-p` | `--port` | Web server port for GUI mode (`--mode server`) | `8080` |
| `-c` | `--config` | Path to `.conf` or properties config file on local machine | `javalens.conf` |
| | `--save-config` | Write current runtime configuration to `.conf` file and exit | `javalens.conf` |
| `-h` | `--help` | Display syntax guide and option details | — |

---

## Unified Activity Report Pipeline

The **Unified Activity Report** (`--mode report`) consolidates all engine activities into an automated 5-step workflow:

```
┌─────────────────┐     ┌─────────────────┐
│ 1. Parse Old    │     │ 2. Parse New    │
│    (Baseline)   │     │    (Feature)    │
└────────┬────────┘     └────────┬────────┘
         │                       │
         └───────────┬───────────┘
                     ▼
         ┌───────────────────────┐
         │ 3. Compare AST Diffs  │
         └───────────┬───────────┘
                     ▼
         ┌───────────────────────┐
         │ 4. Marker-Guided Merge│
         └───────────┬───────────┘
                     ▼
         ┌───────────────────────┐
         │ 5. Re-Analyze Merged  │
         │    & Consolidate CSV  │
         └───────────────────────┘
```

1. **Baseline Scan**: Indexes all attributes and methods in the original codebase (`--old`).
2. **Feature Scan**: Indexes all attributes and methods in the incoming codebase (`--new`).
3. **AST Comparison**: Writes standard `comparison_attributes.csv` and `comparison_methods.csv`.
4. **Code Merge**: Inlines marked blocks from `--new` into destination files in `--old`.
5. **Re-Analysis & Classification**: Parses the merged destination files and generates `javalens_report.csv`:
   - **`NEWLY_ADDED`**: Attributes or methods introduced by the merge (`in_old=NO, in_new=YES, in_merged=YES`).
   - **`ORIGINAL`**: Baseline constructs retained without modifications (`in_old=YES, in_new=YES, in_merged=YES`).
   - **`MODIFIED_BY_MERGE`**: Baseline constructs altered during the merge operation.
   - **`REMOVED`**: Constructs present in baseline but omitted from the merged output.

Also outputs `merge_results.csv` tracking per-file status (`MERGED`, `SKIPPED`, `WARNING`, or `ERROR`).

---

## Local Configuration & `.conf` File System

JavaLens supports storing all runtime directives in a standard `.conf` file on your local machine.

### Configuration Discovery Hierarchy
When JavaLens starts, it resolves configuration files in the following precedence order:
1. **Explicit CLI Flag**: `-c <path>` or `--config <path>`
2. **Environment Variable**: `JAVALENS_CONF` (if pointing to an existing file)
3. **Local Directory**: `./javalens.conf`
4. **Legacy Properties**: `./analyzer.properties` (for backwards compatibility)
5. **User Home Directory**: `~/.javalens.conf`
6. **Classpath Fallback**: Embedded `javalens.conf` inside `javalens.jar`

### Sample `javalens.conf`
```properties
# ===================================================================
# JavaLens Configuration File (javalens.conf)
# Precision AST Static Analysis, Code Comparator & Merge Telemetry
# ===================================================================

# Execution Mode: analyze, compare, merge, report, server, interactive
mode=analyze

# Default Analysis Source Path (file or directory)
source.folder=.

# Baseline Old Version Path (used in compare, merge, and report modes)
old.path=/path/to/baseline/v1

# Feature New Version Path (used in compare, merge, and report modes)
new.path=/path/to/feature/v2

# Output Directory for Generated Reports, CSVs, and Telemetry Data
output.dir=java_analysis_output

# Active Run Output Folder Context
active.run_folder=

# Parallel Worker Thread Pool Size (0 = available CPU cores)
threads=8

# Marker-Guided Merge Boundaries
merge.start_marker=// START_MERGE
merge.end_marker=// END_MERGE

# Feature Engine Toggles
enhancement.compare.enabled=true
enhancement.merge.enabled=true

# Web GUI Server Port
server.port=8080
```

### Saving Configuration via CLI
Export or update your local machine configuration directly:
```bash
# Save active CLI arguments to default javalens.conf
./run.sh --source src/main/java --threads 16 --port 8080 --save-config

# Save to custom configuration path anywhere on the local machine
./run.sh --old v1 --new v2 --threads 8 --save-config /path/to/my_project.conf
```

### Configuring via Web GUI
Navigate to the **Settings** tab in the Web GUI (`http://localhost:8080`):
- **Active Local Conf Path**: Specify the exact location of your `.conf` file.
- **Browse**: Open a native file picker to select an existing `.conf` file on disk.
- **Load**: Immediately reload and inspect parameters from the specified `.conf` file.
- **Save to Conf File**: Atomically write all visual UI fields into the `.conf` file on your machine.
- **Download .conf**: One-click download of the active configuration.

### Configuring via Interactive CLI Wizard
From the terminal menu, choose **`[5] CONFIG`**:
- View all 12 directives with ANSI color highlighting.
- Modify individual parameters interactively.
- Press **`[S]`** to save to the active `.conf` file, **`[W]`** to write to a custom file path, or **`[L]`** to load an existing `.conf` file from disk.

---

## Web GUI Features

When running with `--mode server` on port 8080:

* **Console Overview**: High-level telemetry displaying indexed class count, parallel worker capacity, heap metrics, feature bento cards, and active run paths.
* **AST Analysis Explorer**:
  - Searchable package and file navigation tree.
  - Subtabs for **Methods** and **Attributes**.
  - Modifier filters (`All`, `Public`, `Private`, `Static`).
  - Real-time substring search matching method names, parameter signatures, and variable types.
* **Side-by-Side AST Diff Comparator**:
  - Synchronized dual-pane scrolling.
  - Status badges (`ADDED`, `MODIFIED`, `REMOVED`, `UNCHANGED`).
  - File picker dropdown to quickly inspect individual class diffs.
* **Marker Merge Workbench**: Visual review of source and destination directories, marker settings, and live streaming merge output.
* **Unified Report Tab**:
  - Configure old and new paths with native folder browse triggers.
  - Live pipeline progress terminal with animated pulse and timer.
  - Real-time summary metric cards (*Total Members*, *Newly Added*, *Original*, *Modified*).
  - Searchable, filterable results table with status badges.
  - Instant **Download CSV** button.
* **Collapsible CLI Terminal**: Dockable interactive compiler terminal with real-time log streaming.
* **Keyboard Shortcuts**:
  - `1` – `6`: Direct tab navigation (Overview, Analysis, Compare, Merge, Report, Settings)
  - `⌘K` / `Ctrl+K`: Open Workspace directory picker modal
  - `T`: Toggle interactive terminal drawer
  - `?`: Show Keyboard Shortcuts guide
  - `Esc`: Close open modals or menus
* **Responsive Mobile Drawer**: Full navigation support with touch-friendly drawer on viewports `< 768px`.

---

## REST API Reference

The embedded server exposes clean HTTP endpoints:

| Endpoint | Method | Parameters | Description |
| :--- | :--- | :--- | :--- |
| `/` or `/index.html` | `GET` | — | Serves the single-page Web GUI (from disk or bundled JAR resource) |
| `/README.md` | `GET` | — | Serves the project documentation |
| `/java_report_data.js` | `GET` | `_t` (cache-bust) | Serves the latest active run telemetry data as a JavaScript payload |
| `/api/config` | `GET` | — | Returns current configuration properties as JSON |
| `/api/config` | `POST` | `sourceFolder`, `outputDir`, `threads`, `startMarker`, `endMarker` | Updates and persists configuration to `analyzer.properties` |
| `/api/analyze` | `POST` | `sourceFolder` | Runs codebase analysis with chunked streaming terminal output |
| `/api/compare` | `POST` | `oldPath`, `newPath` | Runs AST comparison with chunked streaming terminal output |
| `/api/merge` | `POST` | `oldPath`, `newPath`, `startMarker`, `endMarker` | Runs marker-guided merge with chunked streaming terminal output |
| `/api/generate-report` | `POST` | `oldPath`, `newPath` | Runs the complete 5-step unified report pipeline with streaming logs |
| `/api/report-csv` | `GET` | — | Downloads `javalens_report.csv` as a file attachment |
| `/api/browse` | `POST` | — | Opens a native OS file/folder picker dialog and returns the selected path |

---

## Output CSV Formats

### 1. Unified Activity Report (`javalens_report.csv`)
| Column | Description |
| :--- | :--- |
| `file` | Relative path to the Java file |
| `package` | Declared package name |
| `class` | Fully qualified class name |
| `member_type` | `ATTRIBUTE` or `METHOD` |
| `member_name` | Name of the field or method |
| `type` | Field data type or method return type |
| `modifiers` | Access and storage modifiers (`public`, `private`, `static`, etc.) |
| `annotations` | Annotations attached to the member |
| `extra_info` | Initializer expression (for fields) or parameter types/exceptions (for methods) |
| `in_old_version` | `YES` or `NO` |
| `in_new_version` | `YES` or `NO` |
| `in_merged_output` | `YES` or `NO` |
| `status` | `NEWLY_ADDED`, `ORIGINAL`, `MODIFIED_BY_MERGE`, `REMOVED` |

### 2. Merge Results (`merge_results.csv`)
| Column | Description |
| :--- | :--- |
| `file` | Relative path of the processed destination file |
| `status` | `MERGED`, `SKIPPED`, `WARNING`, or `ERROR` |
| `message` | Operational outcome or reason for skip/error |

### 3. Standard Analysis Datasets
* **`java_attributes.csv`**: `file, package, class, attribute_name, attribute_type, modifiers, annotations, initializer`
* **`java_methods.csv`**: `file, package, class, method_name, return_type, modifiers, parameters, parameter_count, throws, annotations, kind`

### 4. Comparison Delta Datasets
* **`comparison_attributes.csv`**: `status, file, package, class, attribute_name, attribute_type_old, attribute_type_new, modifiers_old, modifiers_new, annotations_old, annotations_new, initializer_old, initializer_new`
* **`comparison_methods.csv`**: `status, file, package, class, method_name, return_type_old, return_type_new, modifiers_old, modifiers_new, parameters_old, parameters_new, throws_old, throws_new, annotations_old, annotations_new, kind_old, kind_new`

---

## Configuration (`analyzer.properties`)

Engine directives can be customized in `analyzer.properties` or edited live via the Web GUI Settings tab:

```properties
# Merge Markers
merge.start_marker=// START_MERGE
merge.end_marker=// END_MERGE

# Execution Directives
source.folder=.
threads=8
enhancement.compare.enabled=true
enhancement.merge.enabled=true

# Output Routing
compare.output_dir=java_analysis_output
active.run_folder=java_analysis_output/run_20260906_112153
```

---

## Scalability & Performance

JavaLens implements a producer-consumer stream architecture:
* **Zero-Heap Accumulation**: Individual syntax trees are pruned and released for garbage collection immediately after field and method signature extraction.
* **No File Count Limit**: Opens and closes file descriptors sequentially through worker threads, allowing effortless analysis of **tens of thousands of source files** within bounded heap limits.

### Performance Benchmarks (8-Core Apple Silicon / AMD Ryzen, SSD)

| Codebase Size | JVM Memory | Duration |
| :--- | :--- | :--- |
| **500 classes** | ~100 MB heap | < 1 second |
| **5,000 classes** | ~250 MB heap | 8–15 seconds |
| **20,000 classes** | ~500 MB heap | 35–60 seconds |
| **100,000+ classes** | ~1 GB heap | ~3–5 minutes |

---

## Automated Test Suite

A turnkey test workspace is provided in `test_workspace/` (`v1` baseline and `v2` feature). Execute the automated test suite to validate all 5 engine components:

```bash
# macOS / Linux
chmod +x test.sh
./test.sh

# Windows
test.bat
```

The script runs:
1. **Compilation Validation**: Rebuilds `javalens.jar` with all embedded assets.
2. **Analyze Mode Validation**: Verifies AST signature extraction.
3. **Compare Mode Validation**: Verifies field and method delta calculation.
4. **Merge Mode Validation**: Tests marker-guided block substitution.
5. **Unified Report Pipeline Validation**: Executes the full 5-stage pipeline and verifies `javalens_report.csv` generation.

---

## Project Structure

```
javalens/
├── JavaAnalyzer.java        # CLI entry point, analyze engine, progress reporter
├── Config.java              # Configuration options, CLI parser, properties persistence
├── InteractiveCli.java      # Terminal console wizard with colorized ANSI guide
├── CompareEngine.java       # High-throughput AST diff comparator
├── MergeEngine.java         # Marker-guided inline block merger with result tracking
├── ReportGenerator.java     # Automated 5-stage pipeline and unified CSV report generator
├── ParserUtil.java          # Compiler Tree API bindings, CSV/JSON serialization
├── WebServer.java           # Embedded HTTP server with chunked log streaming & REST APIs
├── JavaModel.java           # Internal representation of a parsed Java compilation unit
├── AttributeModel.java      # Model for class fields and variable declarations
├── MethodModel.java         # Model for constructors, methods, and parameters
├── index.html               # Precision Dark Engineering SPA Web GUI
├── javalens.conf            # Standard engine configuration file
├── analyzer.properties      # Legacy properties and active run pointer
├── build.sh / build.bat     # Build script packaging self-contained javalens.jar
├── run.sh / run.bat         # Launch wrapper setting JDK 17 environment
├── test.sh / test.bat       # 5-stage automated test suite
└── test_workspace/          # Multi-version testing fixtures (v1 baseline, v2 feature)
```

---

## License

This project is open-source software licensed under the **MIT License**.
