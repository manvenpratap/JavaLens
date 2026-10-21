# JavaLens — Architecture & System Design

> **Executive Overview**: High-performance, zero-external-dependency static analysis and codebase synthesis engine for Java 17+ repositories. Powered directly by the standard OpenJDK Compiler Tree API (`com.sun.source.tree`), JavaLens provides microsecond AST parsing, semantic diffing, marker-guided safe merging, and an interactive cyber-dark Web GUI.

All system architectures, synthesis workflows, comparison sequences, data pipelines, and engine lifecycles are embedded below as native Mermaid diagrams with full architectural explanations.

---

## 🏛️ 1. System Architecture

```mermaid
flowchart TD
    User["👤 Developer / CI Pipeline\n(CLI / Web GUI :8080)"]
    
    subgraph JVMBoundary["☕ Java 17+ JVM (Zero Third-Party Libraries)"]
        Runtime["⚡ JavaLens Runtime\n(CLI & Embedded HTTP Server)"]
        
        subgraph ASTCore["🛡️ Pure JDK AST Analysis Core"]
            Tree["🔍 Compiler Tree API\n(com.sun.source.tree)"]
            Diff["⚖️ Semantic Diff Engine\n(AST Delta Comparator)"]
            Merge["🔀 Marker Merge Engine\n(Safe Block Transfer)"]
        end
        
        Web["🌐 Precision Web GUI\n(Single-file index.html)"]
        CSV[("📊 Activity Reports\n(Consolidated CSV Matrix)")]
    end

    User -->|"CLI / HTTP"| Runtime
    Runtime --> Tree
    Runtime -->|"compare"| Diff
    Runtime --> Merge
    Diff -->|"AST telemetry"| Web
    Diff --> CSV
```

### Architectural Breakdown & Component Responsibilities
* **Zero External Dependencies**: JavaLens relies exclusively on standard OpenJDK modules (`java.compiler`, `jdk.compiler`, `jdk.httpserver`). It contains 0 third-party JAR dependencies, eliminating supply-chain vulnerabilities, licensing overhead, and classpath collisions.
* **Java Compiler Tree API Core (`com.sun.source.tree`)**: Directly utilizes the Java compiler's native parser and scanner to build fully resolved Abstract Syntax Trees (CompilationUnitTree, ClassTree, MethodTree, VariableTree).
* **Semantic Diff Engine**: Compares AST trees at the symbol signature level rather than raw text line diffs. Accurately detects added, modified, overloaded, and removed methods even when code formatting, whitespace, or comments have shifted.
* **Marker Merge Engine**: Implements marker-guided code synthesis (`/* START_MERGE */ ... /* END_MERGE */`), allowing developers to transplant new or updated methods between codebase branches safely.
* **Embedded HTTP Server & Web GUI**: Standard `com.sun.net.httpserver.HttpServer` serving the single-file cyber-dark engineering GUI with REST telemetry endpoints (`/api/stats`, `/api/diff`, `/api/classes`).
* **Consolidated Activity Matrix**: Produces structured CSV reports auditing every class, field, method signature, cyclomatic complexity, and delta categorization.

---

## 🔄 2. Codebase Synthesis & Merge Workflow

```mermaid
flowchart LR
    A["1. Source Input\n(Baseline vs Feature)"] --> B["2. Compiler AST\n(com.sun.source.tree)"]
    B --> C["3. Member Extractor\n(Fields, methods, types)"]
    C --> D["4. Delta Classifier\n(Added, Modified, Removed)"]
    D --> E["5. Marker Merger\n(START_MERGE blocks)"]
    E --> F["6. Consolidated CSV\n(Full activity audit)"]
```

### Operational Workflow Stages
1. **Source Discovery**: Recursively traverses baseline and target repository directories to collect `.java` source files.
2. **Compiler AST Parsing**: Compiles source strings into `CompilationUnitTree` instances using `JavacTask.parse()`.
3. **Symbolic Member Extraction**: Traverses AST nodes to catalogue types, interfaces, constructors, method signatures, return types, modifiers, and field declarations.
4. **Delta Classification**: Compares baseline member signatures against target signatures, categorizing each entity as ADDED, MODIFIED, UNCHANGED, or REMOVED.
5. **Marker-Guided Code Merging**: Synthesizes clean code blocks into destination source files, guarding insertions with non-destructive markers.
6. **Activity Matrix Generation**: Exports audit logs summarizing line churn, signature diffs, and structural changes.

---

## ⚡ 3. Semantic Comparison & Report Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer
    participant Core as JavaLens Core
    participant Tree as Compiler Tree (JDK)
    participant Diff as Comparator
    participant Report as Report Engine

    Dev->>Core: javalens.jar -m compare dirA dirB
    Core->>Tree: Parse compilation units
    Tree-->>Core: Extracted AST member signatures
    Core->>Diff: Classify Added, Modified, Removed
    Diff-->>Core: Classified delta map
    Core->>Report: Generate activity_report.csv
    Report-->>Dev: Unified Report & Console Table
```

### Execution Sequence Details
* **Step 1**: Developer invokes JavaLens CLI with comparison mode (`-m compare`) specifying two repository roots.
* **Steps 2–3**: Core feeds source files to `com.sun.source.util.JavacTask`, receiving parsed AST trees and extracting member signatures.
* **Steps 4–5**: The comparator compares AST trees bidirectionally, building a structured delta map of signatures and line numbers.
* **Steps 6–7**: The report engine formats the results, outputting both formatted ANSI console tables and the comprehensive `activity_report.csv`.

---

## 🌊 4. AST Synthesis Data Flow Pipeline

```mermaid
flowchart LR
    subgraph S1["1. Sources"]
        Java["📄 Java Source Files\n(.java codebases)"]
    end
    subgraph S2["2. AST Tree"]
        Tree["🔍 Compiler Tree AST\n(com.sun.source.tree)"]
    end
    subgraph S3["3. Deltas"]
        Diff["⚖️ Delta Comparator\n(Semantic diff)"]
    end
    subgraph S4["4. Merge"]
        Merge["🔀 Marker Merger\n(Block splicing)"]
    end
    subgraph S5["5. Reports"]
        CSV[("📊 Activity CSV Matrix")]
        GUI["🌐 Web GUI Console"]
    end

    Java -->|"parse AST"| Tree
    Tree -->|"signatures"| Diff
    Diff -->|"changes"| Merge
    Merge -->|"activity report"| CSV
    Merge -->|"telemetry"| GUI
```

### Data Pipeline Mechanics & Invariants
* **AST Streaming**: Files are parsed sequentially or via parallel streams without retaining unnecessary syntax tokens, keeping JVM heap overhead < 64MB even on 10,000+ class repositories.
* **Signature Normalization**: Parameter types are fully qualified where possible to distinguish between overloaded method signatures.
* **Non-destructive Splicing**: Merging preserves original source formatting, comments, and imports outside of designated merge blocks.

---

## ⏱️ 5. Analysis & Merge Pipeline Lifecycle

```mermaid
stateDiagram-v2
    [*] --> JVM_Initialize: Load JDK Tree API
    JVM_Initialize --> Mode_Selection: Parse Arguments
    Mode_Selection --> Compiler_AST_Walk: Execute Pipeline
    Compiler_AST_Walk --> Delta_Classification: AST Signatures Extracted
    Delta_Classification --> Report_Merge_Done: Consolidate Activity
    Report_Merge_Done --> Web_GUI_Server: -m server Launched
    Report_Merge_Done --> Clean_Exit: CLI Execution Complete
    Clean_Exit --> [*]
```

### State Transitions & Operational Modes
* **`JVM_Initialize`**: Verifies Java 17+ runtime and checks availability of `jdk.compiler` module.
* **`Mode_Selection`**: Dispatches execution based on CLI flags: `-m scan` (single repo audit), `-m compare` (semantic diff), `-m merge` (marker merger), or `-m server` (launch Web GUI).
* **`Compiler_AST_Walk`**: Executes parsing passes across target `.java` compilation units.
* **`Delta_Classification`**: Classifies structural diffs and computes codebase metrics.
* **`Report_Merge_Done`**: Writes persistent reports and completes merging.
* **`Web_GUI_Server`**: If server mode is requested, binds `http://localhost:8080` to serve the interactive web GUI.
