package com.javalens.engine;

import com.javalens.Config;
import com.javalens.JavaAnalyzer;
import com.javalens.model.AttributeModel;
import com.javalens.model.JavaModel;
import com.javalens.model.MethodModel;
import com.javalens.parser.ParserUtil;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Generates a unified CSV report consolidating analyze, compare, and merge activities.
 * Pipeline: parse old → parse new → compare → merge → re-parse merged → generate report.
 */
public class ReportGenerator {

    private static final String[] REPORT_HDR = {
        "file", "package", "class", "member_type", "member_name",
        "type", "modifiers", "annotations", "extra_info",
        "in_old_version", "in_new_version", "in_merged_output", "status"
    };

    /**
     * Runs the full pipeline and generates a unified CSV report.
     * @return Path to the generated report CSV
     */
    public static Path generateFullReport(Config config) throws Exception {
        Path oldPath = Paths.get(config.getOldPath()).toAbsolutePath().normalize();
        Path newPath = Paths.get(config.getNewPath()).toAbsolutePath().normalize();
        Path outputBaseDir = Paths.get(config.getOutputDir()).toAbsolutePath().normalize();

        // Resolve to parent if outputDir itself is a run_ folder
        while (outputBaseDir.getFileName() != null && outputBaseDir.getFileName().toString().startsWith("run_")) {
            outputBaseDir = outputBaseDir.getParent();
        }

        // Create timestamped run folder
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                               .format(java.time.LocalDateTime.now());
        String runFolderName = "run_" + timestamp;
        Path runFolder = outputBaseDir.resolve(runFolderName);
        Files.createDirectories(runFolder);
        Config.updateActiveRunFolder(runFolder.toString());

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   JavaLens Unified Report Generation Pipeline   ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        // ── Step 1: Parse old version (baseline) ────────────────────────────
        System.out.println("▸ Step 1/5: Analyzing original (old) version...");
        Map<String, JavaModel> oldModels = parseAllFiles(oldPath);
        System.out.println("  Found " + oldModels.size() + " Java file(s) in old version.");

        // ── Step 2: Parse new version ───────────────────────────────────────
        System.out.println("▸ Step 2/5: Analyzing new version...");
        Map<String, JavaModel> newModels = parseAllFiles(newPath);
        System.out.println("  Found " + newModels.size() + " Java file(s) in new version.");

        // ── Step 3: Write comparison CSVs (old vs new) ─────────────────────
        System.out.println("▸ Step 3/5: Comparing old vs new versions...");
        writeComparisonCsvs(runFolder, oldModels, newModels);
        System.out.println("  Comparison CSVs written.");

        // ── Step 4: Execute merge ───────────────────────────────────────────
        System.out.println("▸ Step 4/5: Merging new into old (marker-guided)...");
        List<MergeEngine.MergeResult> mergeResults = MergeEngine.execute(config);
        int mergedCount = (int) mergeResults.stream().filter(r -> "MERGED".equals(r.status)).count();
        System.out.println("  " + mergedCount + " file(s) merged, " +
                          (mergeResults.size() - mergedCount) + " skipped/warned.");

        // ── Step 5: Re-parse merged output & generate report ───────────────
        System.out.println("▸ Step 5/5: Re-analyzing merged output & generating report...");
        Map<String, JavaModel> mergedModels = parseAllFiles(oldPath);
        System.out.println("  Found " + mergedModels.size() + " Java file(s) in merged output.");

        // Write analysis CSVs from merged output
        writeAnalysisCsvs(runFolder, mergedModels);

        // Generate unified CSV report
        Path reportCsv = runFolder.resolve("javalens_report.csv");
        int rowCount = writeUnifiedReport(reportCsv, oldModels, newModels, mergedModels);

        // Write merge results CSV
        Path mergeCsv = runFolder.resolve("merge_results.csv");
        writeMergeResultsCsv(mergeCsv, mergeResults);

        // Write report data JS for web UI
        ParserUtil.writeReportDataJs("REPORT", runFolderName, runFolder);

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  Unified Report : " + reportCsv.toAbsolutePath());
        System.out.println("  Total members  : " + rowCount);
        System.out.println("  Merge results  : " + mergeResults.size() + " file(s) processed");
        System.out.println("  Output folder  : " + runFolder.toAbsolutePath());
        System.out.println("═══════════════════════════════════════════════════");

        return reportCsv;
    }

    // ── Unified Report CSV Writer ─────────────────────────────────────────────

    private static int writeUnifiedReport(Path reportCsv,
                                           Map<String, JavaModel> oldModels,
                                           Map<String, JavaModel> newModels,
                                           Map<String, JavaModel> mergedModels) throws IOException {
        int rowCount = 0;

        try (PrintWriter pw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(reportCsv.toFile()), "UTF-8")))) {

            pw.println(csvRow(REPORT_HDR));

            // Collect all unique file keys across all three versions
            Set<String> allFiles = new TreeSet<>();
            allFiles.addAll(oldModels.keySet());
            allFiles.addAll(newModels.keySet());
            allFiles.addAll(mergedModels.keySet());

            for (String fileKey : allFiles) {
                JavaModel oldModel = oldModels.get(fileKey);
                JavaModel newModel = newModels.get(fileKey);
                JavaModel mergedModel = mergedModels.get(fileKey);

                // ── Process Attributes ──────────────────────────────────────
                Map<String, AttributeModel> oldAttrs = indexAttributes(oldModel);
                Map<String, AttributeModel> newAttrs = indexAttributes(newModel);
                Map<String, AttributeModel> mergedAttrs = indexAttributes(mergedModel);

                Set<String> allAttrKeys = new TreeSet<>();
                allAttrKeys.addAll(oldAttrs.keySet());
                allAttrKeys.addAll(newAttrs.keySet());
                allAttrKeys.addAll(mergedAttrs.keySet());

                for (String attrKey : allAttrKeys) {
                    AttributeModel oldA = oldAttrs.get(attrKey);
                    AttributeModel newA = newAttrs.get(attrKey);
                    AttributeModel mergedA = mergedAttrs.get(attrKey);

                    // Use the best available model for display values
                    AttributeModel displayA = mergedA != null ? mergedA : (newA != null ? newA : oldA);

                    boolean inOld = oldA != null;
                    boolean inNew = newA != null;
                    boolean inMerged = mergedA != null;
                    boolean modified = inOld && inMerged && !attributesEqual(oldA, mergedA);

                    String status = determineStatus(inOld, inNew, inMerged, modified);

                    pw.println(csvRow(new String[]{
                        fileKey, displayA.packageName, displayA.className,
                        "ATTRIBUTE", displayA.name, displayA.type, displayA.modifiers,
                        displayA.annotations,
                        displayA.initializer != null ? displayA.initializer : "",
                        inOld ? "YES" : "NO", inNew ? "YES" : "NO", inMerged ? "YES" : "NO",
                        status
                    }));
                    rowCount++;
                }

                // ── Process Methods ─────────────────────────────────────────
                Map<String, MethodModel> oldMeths = indexMethods(oldModel);
                Map<String, MethodModel> newMeths = indexMethods(newModel);
                Map<String, MethodModel> mergedMeths = indexMethods(mergedModel);

                Set<String> allMethKeys = new TreeSet<>();
                allMethKeys.addAll(oldMeths.keySet());
                allMethKeys.addAll(newMeths.keySet());
                allMethKeys.addAll(mergedMeths.keySet());

                for (String methKey : allMethKeys) {
                    MethodModel oldM = oldMeths.get(methKey);
                    MethodModel newM = newMeths.get(methKey);
                    MethodModel mergedM = mergedMeths.get(methKey);

                    MethodModel displayM = mergedM != null ? mergedM : (newM != null ? newM : oldM);

                    boolean inOld = oldM != null;
                    boolean inNew = newM != null;
                    boolean inMerged = mergedM != null;
                    boolean modified = inOld && inMerged && !methodsEqual(oldM, mergedM);

                    String status = determineStatus(inOld, inNew, inMerged, modified);

                    String extraInfo = displayM.parameters;
                    if (displayM.throwsList != null && !displayM.throwsList.isEmpty()) {
                        extraInfo += " throws " + displayM.throwsList;
                    }

                    pw.println(csvRow(new String[]{
                        fileKey, displayM.packageName, displayM.className,
                        "METHOD", displayM.name, displayM.returnType, displayM.modifiers,
                        displayM.annotations, extraInfo,
                        inOld ? "YES" : "NO", inNew ? "YES" : "NO", inMerged ? "YES" : "NO",
                        status
                    }));
                    rowCount++;
                }
            }
        }

        return rowCount;
    }

    // ── Status Determination ──────────────────────────────────────────────────

    private static String determineStatus(boolean inOld, boolean inNew, boolean inMerged, boolean modified) {
        if (inOld && inMerged && modified) return "MODIFIED_BY_MERGE";
        if (inOld && inMerged && !modified) return "ORIGINAL";
        if (!inOld && inNew && inMerged) return "NEWLY_ADDED";
        if (!inOld && !inNew && inMerged) return "NEWLY_ADDED";
        if (inOld && !inMerged) return "REMOVED";
        if (!inOld && inNew && !inMerged) return "NEW_VERSION_ONLY";
        return "ORIGINAL";
    }

    // ── Equality Checks ──────────────────────────────────────────────────────

    private static boolean attributesEqual(AttributeModel a, AttributeModel b) {
        return Objects.equals(a.type, b.type) &&
               Objects.equals(a.modifiers, b.modifiers) &&
               Objects.equals(a.annotations, b.annotations) &&
               Objects.equals(a.initializer, b.initializer);
    }

    private static boolean methodsEqual(MethodModel a, MethodModel b) {
        return Objects.equals(a.returnType, b.returnType) &&
               Objects.equals(a.modifiers, b.modifiers) &&
               Objects.equals(a.parameters, b.parameters) &&
               Objects.equals(a.throwsList, b.throwsList) &&
               Objects.equals(a.annotations, b.annotations);
    }

    // ── Indexing Helpers ─────────────────────────────────────────────────────

    private static Map<String, AttributeModel> indexAttributes(JavaModel model) {
        Map<String, AttributeModel> map = new LinkedHashMap<>();
        if (model != null) {
            for (AttributeModel a : model.getAttributes()) {
                map.put(a.className + "#" + a.name, a);
            }
        }
        return map;
    }

    private static Map<String, MethodModel> indexMethods(JavaModel model) {
        Map<String, MethodModel> map = new LinkedHashMap<>();
        if (model != null) {
            for (MethodModel m : model.getMethods()) {
                map.put(m.className + "#" + m.name + "(" + m.paramTypes + ")", m);
            }
        }
        return map;
    }

    // ── File Scanner ─────────────────────────────────────────────────────────

    private static Map<String, JavaModel> parseAllFiles(Path root) throws Exception {
        Map<String, JavaModel> models = new LinkedHashMap<>();
        if (!Files.isDirectory(root)) {
            // Single file
            JavaModel model = ParserUtil.parseFile(root, root.getParent());
            models.put(model.getFile(), model);
            return models;
        }

        List<Path> javaFiles = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        for (Path file : javaFiles) {
            try {
                JavaModel model = ParserUtil.parseFile(file, root);
                models.put(model.getFile(), model);
            } catch (Exception e) {
                System.err.println("  Warning: Could not parse " + file + ": " + e.getMessage());
            }
        }

        return models;
    }

    // ── Standard CSV Writers (for web UI compatibility) ──────────────────────

    private static void writeAnalysisCsvs(Path outputDir, Map<String, JavaModel> models) throws IOException {
        Path attrCsv = outputDir.resolve("java_attributes.csv");
        Path methCsv = outputDir.resolve("java_methods.csv");

        try (PrintWriter attrPw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(attrCsv.toFile()), "UTF-8")));
             PrintWriter methPw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(methCsv.toFile()), "UTF-8")))) {

            attrPw.println(csvRow(JavaAnalyzer.ATTR_HDR));
            methPw.println(csvRow(JavaAnalyzer.METH_HDR));

            for (JavaModel model : models.values()) {
                String relPath = model.getFile();
                String pkg = model.getPackageName();

                for (AttributeModel a : model.getAttributes()) {
                    attrPw.println(csvRow(new String[]{
                        relPath, pkg, a.className, a.name, a.type, a.modifiers, a.annotations, a.initializer
                    }));
                }

                for (MethodModel m : model.getMethods()) {
                    methPw.println(csvRow(new String[]{
                        relPath, pkg, m.className, m.name, m.returnType, m.modifiers,
                        m.parameters, String.valueOf(m.parameterCount), m.throwsList, m.annotations, m.kind
                    }));
                }
            }
        }
    }

    private static void writeComparisonCsvs(Path outputDir,
                                             Map<String, JavaModel> oldModels,
                                             Map<String, JavaModel> newModels) throws IOException {
        String[] compAttrHdr = {
            "status", "file", "package", "class", "attribute_name",
            "attribute_type_old", "attribute_type_new",
            "modifiers_old", "modifiers_new",
            "annotations_old", "annotations_new",
            "initializer_old", "initializer_new"
        };
        String[] compMethHdr = {
            "status", "file", "package", "class", "method_name",
            "return_type_old", "return_type_new",
            "modifiers_old", "modifiers_new",
            "parameters_old", "parameters_new",
            "throws_old", "throws_new",
            "annotations_old", "annotations_new",
            "kind_old", "kind_new"
        };

        Path attrCsv = outputDir.resolve("comparison_attributes.csv");
        Path methCsv = outputDir.resolve("comparison_methods.csv");

        try (PrintWriter attrPw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(attrCsv.toFile()), "UTF-8")));
             PrintWriter methPw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(methCsv.toFile()), "UTF-8")))) {

            attrPw.println(csvRow(compAttrHdr));
            methPw.println(csvRow(compMethHdr));

            Set<String> allFiles = new TreeSet<>();
            allFiles.addAll(oldModels.keySet());
            allFiles.addAll(newModels.keySet());

            for (String fileKey : allFiles) {
                JavaModel oldModel = oldModels.get(fileKey);
                JavaModel newModel = newModels.get(fileKey);

                // Attributes comparison
                Map<String, AttributeModel> oldAttrs = indexAttributes(oldModel);
                Map<String, AttributeModel> newAttrs = indexAttributes(newModel);
                Set<String> allAttrKeys = new TreeSet<>();
                allAttrKeys.addAll(oldAttrs.keySet());
                allAttrKeys.addAll(newAttrs.keySet());

                for (String key : allAttrKeys) {
                    AttributeModel oldA = oldAttrs.get(key);
                    AttributeModel newA = newAttrs.get(key);

                    if (oldA != null && newA != null) {
                        boolean match = attributesEqual(oldA, newA);
                        String status = match ? "UNCHANGED" : "MODIFIED";
                        attrPw.println(csvRow(new String[]{
                            status, fileKey, newA.packageName, newA.className, newA.name,
                            oldA.type, newA.type, oldA.modifiers, newA.modifiers,
                            oldA.annotations, newA.annotations, oldA.initializer, newA.initializer
                        }));
                    } else if (oldA != null) {
                        attrPw.println(csvRow(new String[]{
                            "REMOVED", fileKey, oldA.packageName, oldA.className, oldA.name,
                            oldA.type, "", oldA.modifiers, "", oldA.annotations, "", oldA.initializer, ""
                        }));
                    } else {
                        attrPw.println(csvRow(new String[]{
                            "ADDED", fileKey, newA.packageName, newA.className, newA.name,
                            "", newA.type, "", newA.modifiers, "", newA.annotations, "", newA.initializer
                        }));
                    }
                }

                // Methods comparison
                Map<String, MethodModel> oldMeths = indexMethods(oldModel);
                Map<String, MethodModel> newMeths = indexMethods(newModel);
                Set<String> allMethKeys = new TreeSet<>();
                allMethKeys.addAll(oldMeths.keySet());
                allMethKeys.addAll(newMeths.keySet());

                for (String key : allMethKeys) {
                    MethodModel oldM = oldMeths.get(key);
                    MethodModel newM = newMeths.get(key);

                    if (oldM != null && newM != null) {
                        boolean match = methodsEqual(oldM, newM);
                        String status = match ? "UNCHANGED" : "MODIFIED";
                        methPw.println(csvRow(new String[]{
                            status, fileKey, newM.packageName, newM.className, newM.name,
                            oldM.returnType, newM.returnType, oldM.modifiers, newM.modifiers,
                            oldM.parameters, newM.parameters, oldM.throwsList, newM.throwsList,
                            oldM.annotations, newM.annotations, oldM.kind, newM.kind
                        }));
                    } else if (oldM != null) {
                        methPw.println(csvRow(new String[]{
                            "REMOVED", fileKey, oldM.packageName, oldM.className, oldM.name,
                            oldM.returnType, "", oldM.modifiers, "", oldM.parameters, "",
                            oldM.throwsList, "", oldM.annotations, "", oldM.kind, ""
                        }));
                    } else {
                        methPw.println(csvRow(new String[]{
                            "ADDED", fileKey, newM.packageName, newM.className, newM.name,
                            "", newM.returnType, "", newM.modifiers, "", newM.parameters,
                            "", newM.throwsList, "", newM.annotations, "", newM.kind
                        }));
                    }
                }
            }
        }
    }

    private static void writeMergeResultsCsv(Path mergeCsv,
                                              List<MergeEngine.MergeResult> mergeResults) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(mergeCsv.toFile()), "UTF-8")))) {
            pw.println(csvRow(new String[]{"file", "status", "message"}));
            for (MergeEngine.MergeResult r : mergeResults) {
                pw.println(csvRow(new String[]{r.relPath, r.status, r.message}));
            }
        }
    }

    // ── CSV Utilities ────────────────────────────────────────────────────────

    private static String csvRow(String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(fields[i] == null ? "" : fields[i]));
        }
        return sb.toString();
    }

    private static String escapeCsv(String v) {
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
