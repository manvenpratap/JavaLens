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
        public final String sizeSummary;

        public MergeResult(String relPath, String status, String message,
                           long existingSizeBytes, long mergedSizeBytes, long deltaBytes,
                           int existingLines, int mergedLines, int deltaLines,
                           String typeChanges, String sizeSummary) {
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
            this.sizeSummary = sizeSummary != null ? sizeSummary : "";
        }

        // Backward compatibility constructor
        public MergeResult(String relPath, String status, String message) {
            this(relPath, status, message, 0L, 0L, 0L, 0, 0, 0, "No type changes", "");
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
            String sizeSummary = buildSizeSummary(exSize, mgSize, exLines, mgLines);

            if (modified) {
                System.out.println("Successfully merged to: " + destOut);
                System.out.println("  Size  : " + sizeSummary);
                System.out.println("  Types : " + typeChanges);
                results.add(new MergeResult(destOut.getFileName().toString(), "MERGED", "Successfully merged to " + destOut,
                        exSize, mgSize, deltaBytes, exLines, mgLines, deltaLines, typeChanges, sizeSummary));
            } else {
                System.out.println("Preserved base file to: " + destOut);
                System.out.println("  Size  : " + sizeSummary);
                System.out.println("  Types : " + typeChanges);
                results.add(new MergeResult(destOut.getFileName().toString(), "COPIED", "Preserved base file to " + destOut,
                        exSize, mgSize, deltaBytes, exLines, mgLines, deltaLines, typeChanges, sizeSummary));
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
                String sizeSummary = buildSizeSummary(exSize, mgSize, exLines, mgLines);

                System.out.println("  [COPIED]  " + rel + " (present in existing Java files only)");
                System.out.println("            Size  : " + sizeSummary);
                System.out.println("            Types : " + typeChanges);
                copiedCount++;
                results.add(new MergeResult(rel, "COPIED", "Present in existing Java files only; preserved in output folder",
                        exSize, mgSize, deltaBytes, exLines, mgLines, deltaLines, typeChanges, sizeSummary));
            } else if (!inExisting && inGenerated) {
                // Newly generated file does not exist in existing Java files -> copy new file to output folder
                Files.copy(file2, destOut, StandardCopyOption.REPLACE_EXISTING);
                long mgSize = Files.exists(destOut) ? Files.size(destOut) : 0L;
                int mgLines = countLines(destOut);
                long deltaBytes = mgSize;
                int deltaLines = mgLines;
                String typeChanges = detectTypeChanges(null, destOut);
                String sizeSummary = buildSizeSummary(0L, mgSize, 0, mgLines);
                System.out.println("  [ADDED]     " + rel + " (new file from generated javafiles folder)");
                System.out.println("            Size  : " + sizeSummary);
                System.out.println("            Types : " + typeChanges);
                addedCount++;
                results.add(new MergeResult(rel, "ADDED", "New file from generated folder",
                        0L, mgSize, deltaBytes, 0, mgLines, deltaLines, typeChanges, sizeSummary));
            } else {
                // File exists in both -> perform marker-based merge
                try {
                    boolean modified = mergeFileContentsToOutput(file2, file1, destOut, startMarker, endMarker);
                    long mgSize = Files.exists(destOut) ? Files.size(destOut) : 0L;
                    int mgLines = countLines(destOut);
                    long deltaBytes = mgSize - exSize;
                    int deltaLines = mgLines - exLines;
                    String typeChanges = detectTypeChanges(file1, destOut);
                    String sizeSummary = buildSizeSummary(exSize, mgSize, exLines, mgLines);

                    if (modified) {
                        System.out.println("  [MERGED]    " + rel + " (features merged from generated files)");
                        System.out.println("            Size  : " + sizeSummary);
                        System.out.println("            Types : " + typeChanges);
                        mergedCount++;
                        results.add(new MergeResult(rel, "MERGED", "Merged features from generated folder",
                                exSize, mgSize, deltaBytes, exLines, mgLines, deltaLines, typeChanges, sizeSummary));
                    } else {
                        System.out.println("  [COPIED]    " + rel + " (no marker modifications found)");
                        System.out.println("            Size  : " + sizeSummary);
                        System.out.println("            Types : " + typeChanges);
                        copiedCount++;
                        results.add(new MergeResult(rel, "COPIED", "No marker modifications; wrote to output folder",
                                exSize, mgSize, deltaBytes, exLines, mgLines, deltaLines, typeChanges, sizeSummary));
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
                            exSize, mgSize, mgSize - exSize, exLines, mgLines, mgLines - exLines, "Error: " + e.getMessage(), ""));
                }
            }
        }

        // Print Merge Size & Type Changes Summary Table
        printMergeSummaryTable(results);

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
                "existing_lines", "merged_lines", "delta_lines", "type_changes", "size_summary", "message"
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
