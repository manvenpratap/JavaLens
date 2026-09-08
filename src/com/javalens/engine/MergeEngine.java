package com.javalens.engine;

import com.javalens.Config;
import com.javalens.model.AttributeModel;
import com.javalens.model.JavaModel;
import com.javalens.model.MethodModel;
import com.javalens.parser.ParserUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class MergeEngine {

    public static class MergeResult {
        public final String relPath;
        public final String status;
        public final String message;
        public final long existingSizeBytes;
        public final long mergedSizeBytes;
        public final long deltaBytes;
        public final int existingLines;
        public final int mergedLines;
        public final int deltaLines;
        public final String typeChanges;
        public final String primaryKeyChanges;
        public final String mandatoryChanges;
        public final String sizeSummary;

        public MergeResult(String relPath, String status, String message,
                           long existingSizeBytes, long mergedSizeBytes, long deltaBytes,
                           int existingLines, int mergedLines, int deltaLines,
                           String typeChanges, String primaryKeyChanges, String mandatoryChanges,
                           String sizeSummary) {
            this.relPath = relPath;
            this.status = status;
            this.message = message;
            this.existingSizeBytes = existingSizeBytes;
            this.mergedSizeBytes = mergedSizeBytes;
            this.deltaBytes = deltaBytes;
            this.existingLines = existingLines;
            this.mergedLines = mergedLines;
            this.deltaLines = deltaLines;
            this.typeChanges = typeChanges != null ? typeChanges : "No type changes";
            this.primaryKeyChanges = primaryKeyChanges != null ? primaryKeyChanges : "No PK changes";
            this.mandatoryChanges = mandatoryChanges != null ? mandatoryChanges : "No mandatory changes";
            this.sizeSummary = sizeSummary != null ? sizeSummary : "";
        }

        // Backward compatibility constructor (11 arguments)
        public MergeResult(String relPath, String status, String message,
                           long existingSizeBytes, long mergedSizeBytes, long deltaBytes,
                           int existingLines, int mergedLines, int deltaLines,
                           String typeChanges, String sizeSummary) {
            this(relPath, status, message, existingSizeBytes, mergedSizeBytes, deltaBytes,
                 existingLines, mergedLines, deltaLines, typeChanges, "No PK changes", "No mandatory changes", sizeSummary);
        }

        // Backward compatibility constructor (3 arguments)
        public MergeResult(String relPath, String status, String message) {
            this(relPath, status, message, 0L, 0L, 0L, 0, 0, 0, "No type changes", "No PK changes", "No mandatory changes", "");
        }
    }

    public static List<MergeResult> execute(Config config) throws Exception {
        Path existingJavaFiles = Paths.get(config.getExistingPath()).toAbsolutePath().normalize();
        Path generatedJavaFiles = Paths.get(config.getGeneratedPath()).toAbsolutePath().normalize();
        String startMarker = config.getStartMarker();
        String endMarker = config.getEndMarker();

        boolean oldIsDir = Files.isDirectory(existingJavaFiles);
        boolean newIsDir = Files.isDirectory(generatedJavaFiles);

        if (oldIsDir != newIsDir) {
            System.err.println("Error: Cannot merge. Both paths must be either files or directories.");
            System.exit(1);
        }

        // Determine destination output directory
        Path outputDir;
        Path configuredPath = Paths.get(config.getOutputDir()).toAbsolutePath().normalize();
        if (configuredPath.toString().contains("/run_") || configuredPath.toString().contains("\\run_") ||
            (configuredPath.getFileName() != null && configuredPath.getFileName().toString().startsWith("run_"))) {
            outputDir = configuredPath;
            Files.createDirectories(outputDir);
        } else {
            outputDir = config.createRunFolder();
        }

        System.out.println("Starting Java Marker-Guided Merge...");
        System.out.println("  Existing Java Files (Base)             : " + existingJavaFiles);
        System.out.println("  Newly Generated Java Files (Features)  : " + generatedJavaFiles);
        System.out.println("  Output Folder (Merged Codebase)        : " + outputDir);
        System.out.println("  Start marker                           : \"" + startMarker + "\"");
        System.out.println("  End marker                             : \"" + endMarker + "\"");
        System.out.println();

        List<MergeResult> results;
        if (!oldIsDir) {
            // Merge single files
            results = mergeSingleFiles(generatedJavaFiles, existingJavaFiles, outputDir, startMarker, endMarker);
        } else {
            // Merge directories
            results = mergeDirectories(existingJavaFiles, generatedJavaFiles, outputDir, startMarker, endMarker);
        }

        System.out.println("\nMerge process completed.");
        return results;
    }

    private static List<MergeResult> mergeSingleFiles(Path sourceFile, Path baseFile, Path outputTarget, String startMarker, String endMarker) {
        List<MergeResult> results = new ArrayList<>();
        try {
            Path destOut = outputTarget;
            if (Files.isDirectory(outputTarget)) {
                destOut = outputTarget.resolve(baseFile.getFileName());
            } else if (destOut.getParent() != null) {
                Files.createDirectories(destOut.getParent());
            }
            long exSize = Files.exists(baseFile) ? Files.size(baseFile) : 0L;
            int exLines = countLines(baseFile);

            boolean modified = mergeFileContentsToOutput(sourceFile, baseFile, destOut, startMarker, endMarker);
            long mgSize = Files.exists(destOut) ? Files.size(destOut) : 0L;
            int mgLines = countLines(destOut);
            long deltaBytes = mgSize - exSize;
            int deltaLines = mgLines - exLines;
            String typeChanges = detectTypeChanges(baseFile, destOut);
            KeyAndMandatoryChanges km = detectKeyAndMandatoryChanges(baseFile, destOut);
            String sizeSummary = buildSizeSummary(exSize, mgSize, exLines, mgLines);

            if (modified) {
                System.out.println("Successfully merged to: " + destOut);
                System.out.println("  Size      : " + sizeSummary);
                System.out.println("  Types     : " + typeChanges);
                System.out.println("  PK        : " + km.primaryKeyChanges);
                System.out.println("  Mandatory : " + km.mandatoryChanges);
                results.add(new MergeResult(destOut.getFileName().toString(), "MERGED", "Successfully merged to " + destOut,
                        exSize, mgSize, deltaBytes, exLines, mgLines, deltaLines, typeChanges, km.primaryKeyChanges, km.mandatoryChanges, sizeSummary));
            } else {
                System.out.println("Preserved base file to: " + destOut);
                System.out.println("  Size      : " + sizeSummary);
                System.out.println("  Types     : " + typeChanges);
                System.out.println("  PK        : " + km.primaryKeyChanges);
                System.out.println("  Mandatory : " + km.mandatoryChanges);
                results.add(new MergeResult(destOut.getFileName().toString(), "COPIED", "Preserved base file to " + destOut,
                        exSize, mgSize, deltaBytes, exLines, mgLines, deltaLines, typeChanges, km.primaryKeyChanges, km.mandatoryChanges, sizeSummary));
            }
            try {
                if (Files.isDirectory(outputTarget)) {
                    writeMergeResultsCsv(outputTarget.resolve("merge_results.csv"), results);
                } else if (outputTarget.getParent() != null) {
                    writeMergeResultsCsv(outputTarget.getParent().resolve("merge_results.csv"), results);
                }
            } catch (Exception e) {
                System.err.println("Warning: failed to write merge_results.csv: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Error merging file: " + e.getMessage());
            results.add(new MergeResult(baseFile.getFileName().toString(), "ERROR", e.getMessage()));
        }
        return results;
    }

    private static List<MergeResult> mergeDirectories(Path existingFolder, Path generatedFolder, Path outputDir, String startMarker, String endMarker) throws IOException {
        List<MergeResult> results = new ArrayList<>();
        Files.createDirectories(outputDir);

        Set<String> existingFiles = scanRelativeFiles(existingFolder);
        Set<String> generatedFiles = scanRelativeFiles(generatedFolder);

        Set<String> allRelativeFiles = new TreeSet<>(existingFiles);
        allRelativeFiles.addAll(generatedFiles);

        int mergedCount = 0;
        int copiedCount = 0;
        int addedCount = 0;

        for (String rel : allRelativeFiles) {
            Path file1 = existingFolder.resolve(rel);
            Path file2 = generatedFolder.resolve(rel);
            Path destOut = outputDir.resolve(rel);

            if (destOut.getParent() != null) {
                Files.createDirectories(destOut.getParent());
            }

            boolean inExisting = existingFiles.contains(rel);
            boolean inGenerated = generatedFiles.contains(rel);

            long exSize = (inExisting && Files.exists(file1)) ? Files.size(file1) : 0L;
            int exLines = inExisting ? countLines(file1) : 0;

            if (inExisting && !inGenerated) {
                // File from existing Java files does not exist in newly generated -> preserve in output folder
                Files.copy(file1, destOut, StandardCopyOption.REPLACE_EXISTING);
                long mgSize = Files.size(destOut);
                int mgLines = countLines(destOut);
                long deltaBytes = mgSize - exSize;
                int deltaLines = mgLines - exLines;
                String typeChanges = detectTypeChanges(file1, destOut);
                KeyAndMandatoryChanges km = detectKeyAndMandatoryChanges(file1, destOut);
                String sizeSummary = buildSizeSummary(exSize, mgSize, exLines, mgLines);

                System.out.println("  [COPIED]    " + rel + " (present in existing Java files only)");
                System.out.println("            Size      : " + sizeSummary);
                System.out.println("            Types     : " + typeChanges);
                System.out.println("            PK        : " + km.primaryKeyChanges);
                System.out.println("            Mandatory : " + km.mandatoryChanges);
                copiedCount++;
                results.add(new MergeResult(rel, "COPIED", "Present in existing Java files only; preserved in output folder",
                        exSize, mgSize, deltaBytes, exLines, mgLines, deltaLines, typeChanges, km.primaryKeyChanges, km.mandatoryChanges, sizeSummary));
            } else if (!inExisting && inGenerated) {
                // Newly generated file does not exist in existing Java files -> copy new file to output folder
                Files.copy(file2, destOut, StandardCopyOption.REPLACE_EXISTING);
                long mgSize = Files.exists(destOut) ? Files.size(destOut) : 0L;
                int mgLines = countLines(destOut);
                long deltaBytes = mgSize;
                int deltaLines = mgLines;
                String typeChanges = detectTypeChanges(null, destOut);
                KeyAndMandatoryChanges km = detectKeyAndMandatoryChanges(null, destOut);
                String sizeSummary = buildSizeSummary(0L, mgSize, 0, mgLines);
                System.out.println("  [ADDED]     " + rel + " (new file from generated javafiles folder)");
                System.out.println("            Size      : " + sizeSummary);
                System.out.println("            Types     : " + typeChanges);
                System.out.println("            PK        : " + km.primaryKeyChanges);
                System.out.println("            Mandatory : " + km.mandatoryChanges);
                addedCount++;
                results.add(new MergeResult(rel, "ADDED", "New file from generated folder",
                        0L, mgSize, deltaBytes, 0, mgLines, deltaLines, typeChanges, km.primaryKeyChanges, km.mandatoryChanges, sizeSummary));
            } else {
                // File exists in both -> perform marker-based merge
                try {
                    boolean modified = mergeFileContentsToOutput(file2, file1, destOut, startMarker, endMarker);
                    long mgSize = Files.exists(destOut) ? Files.size(destOut) : 0L;
                    int mgLines = countLines(destOut);
                    long deltaBytes = mgSize - exSize;
                    int deltaLines = mgLines - exLines;
                    String typeChanges = detectTypeChanges(file1, destOut);
                    KeyAndMandatoryChanges km = detectKeyAndMandatoryChanges(file1, destOut);
                    String sizeSummary = buildSizeSummary(exSize, mgSize, exLines, mgLines);

                    if (modified) {
                        System.out.println("  [MERGED]    " + rel + " (features merged from generated files)");
                        System.out.println("            Size      : " + sizeSummary);
                        System.out.println("            Types     : " + typeChanges);
                        System.out.println("            PK        : " + km.primaryKeyChanges);
                        System.out.println("            Mandatory : " + km.mandatoryChanges);
                        mergedCount++;
                        results.add(new MergeResult(rel, "MERGED", "Merged features from generated folder",
                                exSize, mgSize, deltaBytes, exLines, mgLines, deltaLines, typeChanges, km.primaryKeyChanges, km.mandatoryChanges, sizeSummary));
                    } else {
                        System.out.println("  [COPIED]    " + rel + " (no marker modifications found)");
                        System.out.println("            Size      : " + sizeSummary);
                        System.out.println("            Types     : " + typeChanges);
                        System.out.println("            PK        : " + km.primaryKeyChanges);
                        System.out.println("            Mandatory : " + km.mandatoryChanges);
                        copiedCount++;
                        results.add(new MergeResult(rel, "COPIED", "No marker modifications; wrote to output folder",
                                exSize, mgSize, deltaBytes, exLines, mgLines, deltaLines, typeChanges, km.primaryKeyChanges, km.mandatoryChanges, sizeSummary));
                    }
                } catch (Exception e) {
                    System.err.println("  [ERROR]   " + rel + " : " + e.getMessage());
                    // Fall back to copying file1 so output folder still has the file
                    try {
                        Files.copy(file1, destOut, StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ignored) {}
                    long mgSize = Files.exists(destOut) ? Files.size(destOut) : 0L;
                    int mgLines = countLines(destOut);
                    results.add(new MergeResult(rel, "ERROR", e.getMessage(),
                            exSize, mgSize, mgSize - exSize, exLines, mgLines, mgLines - exLines, "Error: " + e.getMessage(), "Error", "Error", ""));
                }
            }
        }

        // Print Merge Size & Type Changes Summary Table
        printMergeSummaryTable(results);
        printKeyAndMandatoryAuditTable(results);

        System.out.printf("Summary: %d files merged, %d files copied/preserved, %d files added to %s%n",
                mergedCount, copiedCount, addedCount, outputDir);
        try {
            writeMergeResultsCsv(outputDir.resolve("merge_results.csv"), results);
        } catch (Exception e) {
            System.err.println("Warning: failed to write merge_results.csv: " + e.getMessage());
        }
        try {
            com.javalens.parser.ParserUtil.writeReportDataJs("MERGE", outputDir.getFileName().toString(), outputDir);
        } catch (Exception ignored) {}
        return results;
    }

    public static void writeMergeResultsCsv(Path mergeCsv, List<MergeResult> mergeResults) throws IOException {
        if (mergeCsv.getParent() != null) {
            Files.createDirectories(mergeCsv.getParent());
        }
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(mergeCsv.toFile()), StandardCharsets.UTF_8)))) {
            pw.println(toCsvRow(
                "file", "status", "existing_size_bytes", "merged_size_bytes", "delta_bytes",
                "existing_lines", "merged_lines", "delta_lines",
                "type_changes", "primary_key_changes", "mandatory_changes",
                "size_summary", "message"
            ));
            for (MergeResult r : mergeResults) {
                pw.println(toCsvRow(
                    r.relPath,
                    r.status,
                    String.valueOf(r.existingSizeBytes),
                    String.valueOf(r.mergedSizeBytes),
                    String.valueOf(r.deltaBytes),
                    String.valueOf(r.existingLines),
                    String.valueOf(r.mergedLines),
                    String.valueOf(r.deltaLines),
                    r.typeChanges != null ? r.typeChanges : "No type changes",
                    r.primaryKeyChanges != null ? r.primaryKeyChanges : "No PK changes",
                    r.mandatoryChanges != null ? r.mandatoryChanges : "No mandatory changes",
                    r.sizeSummary != null ? r.sizeSummary : "",
                    r.message != null ? r.message : ""
                ));
            }
        }
    }

    private static String toCsvRow(String... cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append(',');
            String c = cols[i] == null ? "" : cols[i];
            if (c.contains(",") || c.contains("\"") || c.contains("\n") || c.contains("\r")) {
                sb.append('"').append(c.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void printMergeSummaryTable(List<MergeResult> results) {
        System.out.println();
        System.out.println("========================================================================================================================");
        System.out.println("                                      MERGE SIZE & TYPE CHANGES SUMMARY");
        System.out.println("========================================================================================================================");
        System.out.printf("%-24s %-8s %10s %10s %12s %12s  %-30s%n",
                "File", "Status", "Old Size", "New Size", "Byte Delta", "Lines Delta", "Type Changes");
        System.out.println("------------------------------------------------------------------------------------------------------------------------");
        for (MergeResult r : results) {
            String oldSz = r.existingSizeBytes > 0 ? formatBytes(r.existingSizeBytes) : "0 B";
            String newSz = r.mergedSizeBytes > 0 ? formatBytes(r.mergedSizeBytes) : "0 B";
            String byteDelta = (r.deltaBytes > 0 ? "+" : "") + formatBytes(r.deltaBytes);
            String linesDelta = (r.deltaLines > 0 ? "+" : "") + r.deltaLines + " L";
            String typeShort = r.typeChanges.length() > 40 ? r.typeChanges.substring(0, 37) + "..." : r.typeChanges;
            System.out.printf("%-24s %-8s %10s %10s %12s %12s  %-30s%n",
                    truncate(r.relPath, 24), r.status, oldSz, newSz, byteDelta, linesDelta, typeShort);
        }
        System.out.println("========================================================================================================================");
        System.out.println();
    }

    public static void printKeyAndMandatoryAuditTable(List<MergeResult> results) {
        System.out.println();
        System.out.println("========================================================================================================================");
        System.out.println("                                      PRIMARY KEY & MANDATORY ATTRIBUTE AUDIT");
        System.out.println("========================================================================================================================");
        System.out.printf("%-24s %-8s %-38s %-45s%n",
                "File", "Status", "Primary Key Changes", "Mandatory Attribute Changes");
        System.out.println("------------------------------------------------------------------------------------------------------------------------");
        for (MergeResult r : results) {
            String pkShort = r.primaryKeyChanges.length() > 38 ? r.primaryKeyChanges.substring(0, 35) + "..." : r.primaryKeyChanges;
            String mandShort = r.mandatoryChanges.length() > 45 ? r.mandatoryChanges.substring(0, 42) + "..." : r.mandatoryChanges;
            System.out.printf("%-24s %-8s %-38s %-45s%n",
                    truncate(r.relPath, 24), r.status, pkShort, mandShort);
        }
        System.out.println("========================================================================================================================");
        System.out.println();
    }

    public static class KeyAndMandatoryChanges {
        public final String primaryKeyChanges;
        public final String mandatoryChanges;

        public KeyAndMandatoryChanges(String primaryKeyChanges, String mandatoryChanges) {
            this.primaryKeyChanges = primaryKeyChanges;
            this.mandatoryChanges = mandatoryChanges;
        }
    }

    public static KeyAndMandatoryChanges detectKeyAndMandatoryChanges(Path existingFile, Path mergedFile) {
        if (existingFile == null || !Files.exists(existingFile)) {
            try {
                if (mergedFile != null && Files.exists(mergedFile)) {
                    JavaModel newM = ParserUtil.parseFile(mergedFile, mergedFile.getParent());
                    List<String> pkList = new ArrayList<>();
                    List<String> mandList = new ArrayList<>();
                    for (AttributeModel a : newM.getAttributes()) {
                        if (a.isPrimaryKey()) {
                            pkList.add("'" + a.name + "' (" + a.type + ", " + a.getPrimaryKeyReason() + ")");
                        } else if (a.isMandatory()) {
                            mandList.add("'" + a.name + "' (" + a.type + ", " + a.getMandatoryReason() + ")");
                        }
                    }
                    String pk = pkList.isEmpty() ? "No primary key" : "New file PK: " + String.join(", ", pkList);
                    String mand = mandList.isEmpty() ? "No mandatory attributes" : "New file mandatory: " + String.join("; ", mandList);
                    return new KeyAndMandatoryChanges(pk, mand);
                }
            } catch (Exception ignored) {}
            return new KeyAndMandatoryChanges("New file added", "New file added");
        }

        if (mergedFile == null || !Files.exists(mergedFile)) {
            return new KeyAndMandatoryChanges("File removed", "File removed");
        }

        List<String> pkChanges = new ArrayList<>();
        List<String> mandChanges = new ArrayList<>();

        try {
            JavaModel oldM = ParserUtil.parseFile(existingFile, existingFile.getParent());
            JavaModel newM = ParserUtil.parseFile(mergedFile, mergedFile.getParent());

            Map<String, AttributeModel> oldAttrs = new LinkedHashMap<>();
            for (AttributeModel a : oldM.getAttributes()) {
                oldAttrs.put(a.name, a);
            }
            Map<String, AttributeModel> newAttrs = new LinkedHashMap<>();
            for (AttributeModel a : newM.getAttributes()) {
                newAttrs.put(a.name, a);
            }

            // 1. Primary Key Analysis
            Map<String, AttributeModel> oldPKs = new LinkedHashMap<>();
            for (AttributeModel a : oldM.getAttributes()) {
                if (a.isPrimaryKey()) oldPKs.put(a.name, a);
            }
            Map<String, AttributeModel> newPKs = new LinkedHashMap<>();
            for (AttributeModel a : newM.getAttributes()) {
                if (a.isPrimaryKey()) newPKs.put(a.name, a);
            }

            if (oldPKs.isEmpty() && newPKs.isEmpty()) {
                pkChanges.add("No primary key");
            } else if (oldPKs.isEmpty() && !newPKs.isEmpty()) {
                for (AttributeModel a : newPKs.values()) {
                    pkChanges.add("Primary key added: '" + a.name + "' (" + a.type + ", " + a.getPrimaryKeyReason() + ")");
                }
            } else if (!oldPKs.isEmpty() && newPKs.isEmpty()) {
                for (AttributeModel a : oldPKs.values()) {
                    pkChanges.add("WARNING: Primary key removed: '" + a.name + "'");
                }
            } else {
                for (String oldName : oldPKs.keySet()) {
                    if (!newPKs.containsKey(oldName)) {
                        pkChanges.add("Primary key removed: '" + oldName + "'");
                    }
                }
                for (String newName : newPKs.keySet()) {
                    if (!oldPKs.containsKey(newName)) {
                        AttributeModel newPK = newPKs.get(newName);
                        pkChanges.add("Primary key added: '" + newName + "' (" + newPK.type + ", " + newPK.getPrimaryKeyReason() + ")");
                    }
                }
                for (String name : oldPKs.keySet()) {
                    if (newPKs.containsKey(name)) {
                        AttributeModel oldPK = oldPKs.get(name);
                        AttributeModel newPK = newPKs.get(name);
                        if (!Objects.equals(oldPK.type, newPK.type)) {
                            pkChanges.add("PK '" + name + "' type changed: " + oldPK.type + " -> " + newPK.type);
                        } else if (!Objects.equals(oldPK.annotations, newPK.annotations)) {
                            pkChanges.add("PK '" + name + "' annotations updated: " + newPK.annotations);
                        } else {
                            pkChanges.add("Preserved PK '" + name + "' (" + newPK.type + ")");
                        }
                    }
                }
            }

            // 2. Mandatory Attributes Analysis
            for (Map.Entry<String, AttributeModel> entry : oldAttrs.entrySet()) {
                String name = entry.getKey();
                AttributeModel oldA = entry.getValue();
                if (newAttrs.containsKey(name)) {
                    AttributeModel newA = newAttrs.get(name);
                    boolean oldMand = oldA.isMandatory();
                    boolean newMand = newA.isMandatory();
                    if (!oldMand && newMand) {
                        mandChanges.add("Attribute '" + name + "' became mandatory (" + newA.getMandatoryReason() + ")");
                    } else if (oldMand && !newMand) {
                        mandChanges.add("Attribute '" + name + "' became optional (was: " + oldA.getMandatoryReason() + ")");
                    } else if (oldMand && newMand && !Objects.equals(oldA.type, newA.type)) {
                        mandChanges.add("Mandatory attribute '" + name + "' type changed: " + oldA.type + " -> " + newA.type);
                    }
                }
            }

            for (Map.Entry<String, AttributeModel> entry : newAttrs.entrySet()) {
                String name = entry.getKey();
                if (!oldAttrs.containsKey(name)) {
                    AttributeModel newA = entry.getValue();
                    if (newA.isMandatory() && !newA.isPrimaryKey()) {
                        mandChanges.add("Added mandatory: '" + name + "' (" + newA.type + ", " + newA.getMandatoryReason() + ")");
                    }
                }
            }

            for (Map.Entry<String, AttributeModel> entry : oldAttrs.entrySet()) {
                String name = entry.getKey();
                if (!newAttrs.containsKey(name)) {
                    AttributeModel oldA = entry.getValue();
                    if (oldA.isMandatory()) {
                        mandChanges.add("Removed mandatory: '" + name + "'");
                    }
                }
            }

            if (mandChanges.isEmpty()) {
                long totalMand = newAttrs.values().stream().filter(AttributeModel::isMandatory).count();
                mandChanges.add(totalMand > 0 ? "Preserved " + totalMand + " mandatory attribute(s)" : "No mandatory attributes");
            }

        } catch (Exception e) {
            pkChanges.add("PK check error: " + e.getMessage());
            mandChanges.add("Mandatory check error: " + e.getMessage());
        }

        String pkResult = String.join("; ", pkChanges);
        String mandResult = String.join("; ", mandChanges);
        return new KeyAndMandatoryChanges(pkResult, mandResult);
    }

    public static String detectTypeChanges(Path existingFile, Path mergedFile) {
        if (existingFile == null || !Files.exists(existingFile)) {
            try {
                if (mergedFile != null && Files.exists(mergedFile)) {
                    JavaModel newM = ParserUtil.parseFile(mergedFile, mergedFile.getParent());
                    Set<String> types = new TreeSet<>();
                    for (AttributeModel a : newM.getAttributes()) {
                        if (a.type != null && !a.type.isBlank()) types.add(a.type);
                    }
                    for (MethodModel m : newM.getMethods()) {
                        if (m.returnType != null && !m.returnType.isBlank() && !"(constructor)".equals(m.returnType)) {
                            types.add(m.returnType);
                        }
                    }
                    return types.isEmpty() ? "New file" : "New file types: " + String.join(", ", types);
                }
            } catch (Exception ignored) {}
            return "New file added";
        }
        if (mergedFile == null || !Files.exists(mergedFile)) {
            return "File removed";
        }

        List<String> changes = new ArrayList<>();
        try {
            JavaModel oldM = ParserUtil.parseFile(existingFile, existingFile.getParent());
            JavaModel newM = ParserUtil.parseFile(mergedFile, mergedFile.getParent());

            // 1. Attribute type modifications
            Map<String, String> oldAttrTypes = new HashMap<>();
            for (AttributeModel a : oldM.getAttributes()) {
                oldAttrTypes.put(a.name, a.type);
            }
            Map<String, String> newAttrTypes = new HashMap<>();
            for (AttributeModel a : newM.getAttributes()) {
                newAttrTypes.put(a.name, a.type);
            }

            for (Map.Entry<String, String> entry : oldAttrTypes.entrySet()) {
                String name = entry.getKey();
                String oldType = entry.getValue();
                if (newAttrTypes.containsKey(name)) {
                    String newType = newAttrTypes.get(name);
                    if (!Objects.equals(oldType, newType) && oldType != null && newType != null) {
                        changes.add("Attribute '" + name + "' type: " + oldType + " -> " + newType);
                    }
                }
            }

            // 2. Method return type and parameter type modifications
            Map<String, MethodModel> oldMethods = new HashMap<>();
            for (MethodModel m : oldM.getMethods()) {
                oldMethods.put(m.name + "#" + m.parameterCount, m);
            }
            for (MethodModel newMeth : newM.getMethods()) {
                String key = newMeth.name + "#" + newMeth.parameterCount;
                if (oldMethods.containsKey(key)) {
                    MethodModel oldMeth = oldMethods.get(key);
                    if (!Objects.equals(oldMeth.returnType, newMeth.returnType) &&
                        oldMeth.returnType != null && newMeth.returnType != null &&
                        !"(constructor)".equals(oldMeth.returnType)) {
                        changes.add("Method '" + newMeth.name + "()' return type: " + oldMeth.returnType + " -> " + newMeth.returnType);
                    }
                    if (!Objects.equals(oldMeth.paramTypes, newMeth.paramTypes) &&
                        oldMeth.paramTypes != null && newMeth.paramTypes != null &&
                        !oldMeth.paramTypes.isEmpty()) {
                        changes.add("Method '" + newMeth.name + "()' params: (" + oldMeth.paramTypes + ") -> (" + newMeth.paramTypes + ")");
                    }
                }
            }

            // 3. New types added via extra attributes or methods
            Set<String> addedTypes = new LinkedHashSet<>();
            for (AttributeModel a : newM.getAttributes()) {
                if (!oldAttrTypes.containsKey(a.name) && a.type != null && !a.type.isBlank()) {
                    addedTypes.add(a.type);
                }
            }
            for (MethodModel m : newM.getMethods()) {
                if (!oldMethods.containsKey(m.name + "#" + m.parameterCount) &&
                    m.returnType != null && !m.returnType.isBlank() && !"(constructor)".equals(m.returnType) && !"void".equals(m.returnType)) {
                    addedTypes.add(m.returnType);
                }
            }
            if (!addedTypes.isEmpty()) {
                changes.add("Added types: " + String.join(", ", addedTypes));
            }

        } catch (Exception e) {
            changes.add("Type check error: " + e.getMessage());
        }

        return changes.isEmpty() ? "No type changes" : String.join("; ", changes);
    }

    private static int countLines(Path path) {
        if (path == null || !Files.exists(path)) return 0;
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            return lines.size();
        } catch (Exception e) {
            return 0;
        }
    }

    public static String buildSizeSummary(long exBytes, long mgBytes, int exLines, int mgLines) {
        long dBytes = mgBytes - exBytes;
        int dLines = mgLines - exLines;
        String bSign = dBytes > 0 ? "+" : "";
        String lSign = dLines > 0 ? "+" : "";
        if (exBytes == 0) {
            return formatBytes(mgBytes) + " (" + mgLines + " lines, NEW)";
        }
        if (dBytes == 0 && dLines == 0) {
            return formatBytes(exBytes) + " (unchanged, " + exLines + " lines)";
        }
        return formatBytes(exBytes) + " -> " + formatBytes(mgBytes) + " (" + bSign + formatBytes(dBytes) + ") | " +
               exLines + " -> " + mgLines + " lines (" + lSign + dLines + " lines)";
    }

    public static String formatBytes(long bytes) {
        if (bytes == 0) return "0 B";
        long abs = Math.abs(bytes);
        String sign = bytes < 0 ? "-" : "";
        if (abs < 1024) return sign + abs + " B";
        int exp = (int) (Math.log(abs) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.US, "%s%.1f %cB", sign, abs / Math.pow(1024, exp), pre);
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
    }

    private static Set<String> scanRelativeFiles(Path dir) throws IOException {
        Set<String> set = new TreeSet<>();
        if (!Files.exists(dir)) return set;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    set.add(dir.relativize(file).toString());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return set;
    }

    private static boolean mergeFileContentsToOutput(Path sourceFile, Path baseFile, Path destOut, String startMarker, String endMarker) throws IOException {
        List<String> sourceLines = Files.readAllLines(sourceFile, StandardCharsets.UTF_8);
        List<String> baseLines = Files.readAllLines(baseFile, StandardCharsets.UTF_8);

        // Extract blocks from source
        List<List<String>> sourceBlocks = new ArrayList<>();
        List<String> currentBlock = null;
        boolean inside = false;
        for (String line : sourceLines) {
            if (line.contains(startMarker)) {
                inside = true;
                currentBlock = new ArrayList<>();
            } else if (line.contains(endMarker)) {
                if (inside) {
                    sourceBlocks.add(currentBlock);
                    inside = false;
                    currentBlock = null;
                }
            } else if (inside) {
                currentBlock.add(line);
            }
        }

        if (sourceBlocks.isEmpty()) {
            // Source doesn't have any markers -> copy base content to output
            Files.write(destOut, baseLines, StandardCharsets.UTF_8);
            return false;
        }

        // Merge into destination lines
        List<String> mergedLines = new ArrayList<>();
        int blockIndex = 0;
        boolean destInside = false;
        boolean modified = false;

        for (int i = 0; i < baseLines.size(); i++) {
            String line = baseLines.get(i);
            if (line.contains(startMarker)) {
                mergedLines.add(line);
                destInside = true;
                if (blockIndex < sourceBlocks.size()) {
                    List<String> srcBlock = sourceBlocks.get(blockIndex);
                    // Check if block actually changed
                    List<String> oldBlock = new ArrayList<>();
                    int j = i + 1;
                    while (j < baseLines.size() && !baseLines.get(j).contains(endMarker)) {
                        oldBlock.add(baseLines.get(j));
                        j++;
                    }
                    if (!srcBlock.equals(oldBlock)) {
                        modified = true;
                    }
                    mergedLines.addAll(srcBlock);
                    blockIndex++;
                } else {
                    System.err.println("Warning [" + destOut.getFileName() + "]: Base contains more markers than source. Extra block left unchanged.");
                    int j = i + 1;
                    while (j < baseLines.size() && !baseLines.get(j).contains(endMarker)) {
                        mergedLines.add(baseLines.get(j));
                        j++;
                    }
                }
            } else if (line.contains(endMarker)) {
                mergedLines.add(line);
                destInside = false;
            } else if (!destInside) {
                mergedLines.add(line);
            }
        }

        if (blockIndex == 0) {
            // No markers found in base file -> write base lines to output
            System.err.println("Warning [" + destOut.getFileName() + "]: Markers not found in base file; copying base version.");
            Files.write(destOut, baseLines, StandardCharsets.UTF_8);
            return false;
        }

        if (blockIndex < sourceBlocks.size()) {
            System.err.println("Warning [" + destOut.getFileName() + "]: Source contains more markers (" + sourceBlocks.size() + ") than base (" + blockIndex + "). Remaining blocks ignored.");
        }

        Files.write(destOut, mergedLines, StandardCharsets.UTF_8);
        return modified;
    }
}
