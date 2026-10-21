# JavaLens — Project Context & System Architecture

> **Precision AST Static Analysis & Telemetry Engine**  
> High-performance zero-external-dependency static analysis and codebase synthesis platform powered directly by the standard JDK Java Compiler Tree API (`com.sun.source.tree`).

---

## 🏛️ System Architecture

Interactive Archify HTML: [`docs/diagrams/architecture.html`](./docs/diagrams/architecture.html)  
Specification: [`docs/diagrams/architecture.json`](./docs/diagrams/architecture.json)

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

---

## 🔄 Codebase Synthesis & Merge Workflow

Interactive Archify HTML: [`docs/diagrams/workflow.html`](./docs/diagrams/workflow.html)  
Specification: [`docs/diagrams/workflow.json`](./docs/diagrams/workflow.json)

```mermaid
flowchart LR
    A["1. Source Input\n(Baseline vs Feature)"] --> B["2. Compiler AST\n(com.sun.source.tree)"]
    B --> C["3. Member Extractor\n(Fields, methods, types)"]
    C --> D["4. Delta Classifier\n(Added, Modified, Removed)"]
    D --> E["5. Marker Merger\n(START_MERGE blocks)"]
    E --> F["6. Consolidated CSV\n(Full activity audit)"]
```

---

## ⚡ Semantic Comparison & Report Sequence

Interactive Archify HTML: [`docs/diagrams/sequence.html`](./docs/diagrams/sequence.html)  
Specification: [`docs/diagrams/sequence.json`](./docs/diagrams/sequence.json)

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

---

## 🌊 AST Synthesis Data Flow Pipeline

Interactive Archify HTML: [`docs/diagrams/dataflow.html`](./docs/diagrams/dataflow.html)  
Specification: [`docs/diagrams/dataflow.json`](./docs/diagrams/dataflow.json)

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

---

## ⏱️ Analysis & Merge Pipeline Lifecycle

Interactive Archify HTML: [`docs/diagrams/lifecycle.html`](./docs/diagrams/lifecycle.html)  
Specification: [`docs/diagrams/lifecycle.json`](./docs/diagrams/lifecycle.json)

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
