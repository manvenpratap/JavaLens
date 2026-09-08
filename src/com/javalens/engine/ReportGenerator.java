package com.javalens.engine;

import com.javalens.Config;
import com.javalens.JavaAnalyzer;
import com.javalens.model.AttributeModel;
import com.javalens.model.JavaModel;
import com.javalens.model.MethodModel;
import com.javalens.parser.ParserUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generates unified reports consolidating analyze, compare, and merge activities
 * across multiple standard formats: CSV, JSON, HTML, Markdown, XML, and ZIP bundle.
 * Pipeline: parse old → parse new → compare → merge → re-parse merged → generate reports.
 */
public class ReportGenerator {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java -cp javalens.jar com.javalens.engine.ReportGenerator <run-dir> <format>");
            return;
        }
        Path runDir = Paths.get(args[0]);
        String format = args[1];
        Path target = ensureReportFormat(runDir, format);
        System.out.println("Report generated successfully: " + (target != null ? target.toAbsolutePath() : "null"));
    }

    private static final String[] REPORT_HDR = {
        "file", "package", "class", "member_type", "member_name",
        "type", "modifiers", "annotations", "extra_info",
        "in_old_version", "in_new_version", "in_merged_output", "status"
    };

    public static class ReportItem {
        public String file = "";
        public String packageName = "";
        public String className = "";
        public String memberType = "";
        public String memberName = "";
        public String type = "";
        public String modifiers = "";
        public String annotations = "";
        public String extraInfo = "";
        public boolean inOld;
        public boolean inNew;
        public boolean inMerged;
        public String status = "ORIGINAL";
    }

    public static class ReportSummary {
        public String timestamp = "";
        public String oldPath = "";
        public String newPath = "";
        public String runFolder = "";
        public int totalMembers = 0;
        public int newlyAdded = 0;
        public int modified = 0;
        public int original = 0;
        public int removed = 0;
        public int totalFiles = 0;
        public int mergedFiles = 0;
    }

    /**
     * Runs the full pipeline and generates unified reports in multiple formats.
     * @return Path to the generated report CSV
     */
    public static Path generateFullReport(Config config) throws Exception {
        Path existingPath = Paths.get(config.getExistingPath()).toAbsolutePath().normalize();
        Path generatedPath = Paths.get(config.getGeneratedPath()).toAbsolutePath().normalize();
        Path runFolder = config.createRunFolder();
        String runFolderName = runFolder.getFileName().toString();

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   JavaLens Unified Report Generation Pipeline   ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        // ── Step 1: Parse existing version (baseline) ────────────────────────────
        System.out.println("▸ Step 1/5: Analyzing existing Java files (baseline)...");
        Map<String, JavaModel> oldModels = parseAllFiles(existingPath);
        System.out.println("  Found " + oldModels.size() + " Java file(s) in existing version.");

        // ── Step 2: Parse newly generated version ────────────────────────────────
        System.out.println("▸ Step 2/5: Analyzing newly generated Java files (with extra attributes & functions)...");
        Map<String, JavaModel> newModels = parseAllFiles(generatedPath);
        System.out.println("  Found " + newModels.size() + " Java file(s) in newly generated version.");

        // ── Step 3: Write comparison CSVs ────────────────────────────────────────
        System.out.println("▸ Step 3/5: Comparing existing vs newly generated versions...");
        writeComparisonCsvs(runFolder, oldModels, newModels);
        System.out.println("  Comparison CSVs written.");

        // ── Step 4: Execute merge ───────────────────────────────────────────
        System.out.println("▸ Step 4/5: Merging newly generated code into existing Java files...");
        Path mergedFolder = runFolder.resolve("merged");
        Files.createDirectories(mergedFolder);
        Config mergeConfig = new Config();
        mergeConfig.setExistingPath(existingPath.toString());
        mergeConfig.setGeneratedPath(generatedPath.toString());
        mergeConfig.setOutputDir(mergedFolder.toString());
        mergeConfig.setStartMarker(config.getStartMarker());
        mergeConfig.setEndMarker(config.getEndMarker());
        List<MergeEngine.MergeResult> mergeResults = MergeEngine.execute(mergeConfig);
        int mergedCount = (int) mergeResults.stream().filter(r -> "MERGED".equals(r.status)).count();
        System.out.println("  " + mergedCount + " file(s) merged, " +
                          (mergeResults.size() - mergedCount) + " copied/preserved/added.");

        // ── Step 5: Re-parse merged output & generate report ───────────────
        System.out.println("▸ Step 5/5: Re-analyzing merged output & generating CSV reports...");
        Map<String, JavaModel> mergedModels = parseAllFiles(mergedFolder);
        System.out.println("  Found " + mergedModels.size() + " Java file(s) in merged output.");

        // Write analysis CSVs from merged output
        writeAnalysisCsvs(runFolder, mergedModels);

        // Collect unified report items
        List<ReportItem> items = collectReportItems(oldModels, newModels, mergedModels);

        // Compute summary statistics
        ReportSummary summary = new ReportSummary();
        summary.timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                               .format(java.time.LocalDateTime.now());
        summary.oldPath = existingPath.toString();
        summary.newPath = generatedPath.toString();
        summary.runFolder = runFolder.toString();
        summary.totalMembers = items.size();
        summary.totalFiles = Math.max(oldModels.size(), Math.max(newModels.size(), mergedModels.size()));
        summary.mergedFiles = mergedCount;

        for (ReportItem item : items) {
            String s = item.status.toUpperCase();
            if ("NEWLY_ADDED".equals(s)) summary.newlyAdded++;
            else if ("MODIFIED_BY_MERGE".equals(s)) summary.modified++;
            else if ("ORIGINAL".equals(s)) summary.original++;
            else if ("REMOVED".equals(s)) summary.removed++;
        }

        // Script run writes ONLY CSV reports and executive text summary:
        Path reportCsv = runFolder.resolve("javalens_report.csv");
        writeUnifiedReportCsv(reportCsv, items);

        // Write merge results CSV
        Path mergeCsv = runFolder.resolve("merge_results.csv");
        writeMergeResultsCsv(mergeCsv, mergeResults);

        // Write human-readable summary text
        Path summaryTxt = runFolder.resolve("javalens_summary.txt");
        writeSummaryTxt(summaryTxt, summary, mergeResults);

        // (Other formats: Excel, JSON, HTML, Markdown, XML, ZIP are generated on-demand when download options on GUI are triggered)

        // Write report data JS for Web GUI
        ParserUtil.writeReportDataJs("REPORT", runFolderName, runFolder);

        System.out.println();
        System.out.println("═════════════════════════════════════════════════════════════════");
        System.out.println("  JavaLens CSV Reports Generated for Run: " + runFolderName);
        System.out.println("    ▸ Unified CSV : " + reportCsv.toAbsolutePath());
        System.out.println("    ▸ Merge CSV   : " + mergeCsv.toAbsolutePath());
        System.out.println("    ▸ Summary     : " + summaryTxt.toAbsolutePath());
        System.out.println("  (Other formats: Excel, JSON, HTML, MD, XML, ZIP generated on-demand via GUI)");
        System.out.println("  Total members : " + items.size() + " (" + summary.newlyAdded + " Added, " +
                           summary.modified + " Modified, " + summary.original + " Original)");
        System.out.println("  Merge results : " + mergeResults.size() + " file(s) processed");
        System.out.println("  Output folder : " + runFolder.toAbsolutePath());
        System.out.println("═════════════════════════════════════════════════════════════════");

        return reportCsv;
    }

    /**
     * Parses an existing javalens_report.csv file back into ReportItem objects.
     */
    public static List<ReportItem> parseReportCsv(Path csvPath) {
        List<ReportItem> items = new ArrayList<>();
        if (csvPath == null || !Files.exists(csvPath)) return items;
        try {
            List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;
                String[] cols = ParserUtil.parseCsvLine(line);
                if (cols.length < 13) continue;
                ReportItem item = new ReportItem();
                item.file = cols[0];
                item.packageName = cols[1];
                item.className = cols[2];
                item.memberType = cols[3];
                item.memberName = cols[4];
                item.type = cols[5];
                item.modifiers = cols[6];
                item.annotations = cols[7];
                item.extraInfo = cols[8];
                item.inOld = "YES".equalsIgnoreCase(cols[9]);
                item.inNew = "YES".equalsIgnoreCase(cols[10]);
                item.inMerged = "YES".equalsIgnoreCase(cols[11]);
                item.status = cols[12];
                items.add(item);
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to parse report CSV: " + e.getMessage());
        }
        return items;
    }

    /**
     * Ensures the requested report format exists on disk in the run folder,
     * generating it on-the-fly from the CSV data or running the generator if needed.
     */
    public static Path ensureReportFormat(Path runDir, String format) throws Exception {
        if (runDir == null) return null;
        String fmt = (format != null ? format : "csv").toLowerCase().trim();
        String targetFileName;
        switch (fmt) {
            case "json": targetFileName = "javalens_report.json"; break;
            case "html": targetFileName = "javalens_report.html"; break;
            case "md":
            case "markdown": targetFileName = "javalens_report.md"; break;
            case "xml": targetFileName = "javalens_report.xml"; break;
            case "xlsx":
            case "excel": targetFileName = "javalens_report.xlsx"; break;
            case "zip":
            case "bundle":
            case "all": targetFileName = "javalens_report_bundle.zip"; break;
            case "csv":
            default: targetFileName = "javalens_report.csv"; break;
        }

        Path target = runDir.resolve(targetFileName);
        if (Files.exists(target)) {
            return target;
        }

        Path reportCsv = runDir.resolve("javalens_report.csv");
        if (!Files.exists(reportCsv)) {
            Config config = new Config();
            config.load();
            config.setOutputDir(runDir.toString());
            return generateFullReport(config);
        }

        List<ReportItem> items = parseReportCsv(reportCsv);
        ReportSummary summary = new ReportSummary();
        summary.timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                               .format(java.time.LocalDateTime.now());
        summary.runFolder = runDir.toString();
        summary.totalMembers = items.size();
        for (ReportItem it : items) {
            String s = (it.status != null ? it.status : "").toUpperCase();
            if ("NEWLY_ADDED".equals(s)) summary.newlyAdded++;
            else if ("MODIFIED_BY_MERGE".equals(s) || "MODIFIED".equals(s)) summary.modified++;
            else if ("ORIGINAL".equals(s) || "UNCHANGED".equals(s)) summary.original++;
            else if ("REMOVED".equals(s)) summary.removed++;
        }

        List<MergeEngine.MergeResult> mergeResults = new ArrayList<>();
        Path mergeCsv = runDir.resolve("merge_results.csv");
        if (Files.exists(mergeCsv)) {
            try {
                List<String> mLines = Files.readAllLines(mergeCsv, StandardCharsets.UTF_8);
                for (int i = 1; i < mLines.size(); i++) {
                    String[] parts = ParserUtil.parseCsvLine(mLines.get(i));
                    if (parts.length >= 2) {
                        String file = parts[0];
                        String status = parts[1];
                        if (parts.length >= 11) {
                            long exSize = parseLong(parts[2], 0L);
                            long mgSize = parseLong(parts[3], 0L);
                            long dBytes = parseLong(parts[4], 0L);
                            int exLines = parseInt(parts[5], 0);
                            int mgLines = parseInt(parts[6], 0);
                            int dLines = parseInt(parts[7], 0);
                            String typeChanges = parts[8];
                            String sizeSummary = parts[9];
                            String msg = parts[10];
                            mergeResults.add(new MergeEngine.MergeResult(file, status, msg, exSize, mgSize, dBytes, exLines, mgLines, dLines, typeChanges, sizeSummary));
                        } else {
                            String msg = parts.length > 2 ? parts[2] : "";
                            mergeResults.add(new MergeEngine.MergeResult(file, status, msg));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        Path jsonPath = runDir.resolve("javalens_report.json");
        if ("json".equals(fmt) || "zip".equals(fmt) || "bundle".equals(fmt) || "all".equals(fmt)) {
            if (!Files.exists(jsonPath) || "json".equals(fmt)) {
                writeUnifiedReportJson(jsonPath, summary, items, mergeResults);
            }
        }
        Path htmlPath = runDir.resolve("javalens_report.html");
        if ("html".equals(fmt) || "zip".equals(fmt) || "bundle".equals(fmt) || "all".equals(fmt)) {
            if (!Files.exists(htmlPath) || "html".equals(fmt)) {
                writeUnifiedReportHtml(htmlPath, summary, items, mergeResults);
            }
        }
        Path mdPath = runDir.resolve("javalens_report.md");
        if ("md".equals(fmt) || "markdown".equals(fmt) || "zip".equals(fmt) || "bundle".equals(fmt) || "all".equals(fmt)) {
            if (!Files.exists(mdPath) || "md".equals(fmt) || "markdown".equals(fmt)) {
                writeUnifiedReportMarkdown(mdPath, summary, items, mergeResults);
            }
        }
        Path xmlPath = runDir.resolve("javalens_report.xml");
        if ("xml".equals(fmt) || "zip".equals(fmt) || "bundle".equals(fmt) || "all".equals(fmt)) {
            if (!Files.exists(xmlPath) || "xml".equals(fmt)) {
                writeUnifiedReportXml(xmlPath, summary, items, mergeResults);
            }
        }
        Path xlsxPath = runDir.resolve("javalens_report.xlsx");
        if ("xlsx".equals(fmt) || "excel".equals(fmt) || "zip".equals(fmt) || "bundle".equals(fmt) || "all".equals(fmt)) {
            if (!Files.exists(xlsxPath) || "xlsx".equals(fmt) || "excel".equals(fmt)) {
                writeUnifiedReportExcel(xlsxPath, summary, items, mergeResults);
            }
        }

        if ("zip".equals(fmt) || "bundle".equals(fmt) || "all".equals(fmt)) {
            Path zipPath = runDir.resolve("javalens_report_bundle.zip");
            Map<String, Path> bundleFiles = new LinkedHashMap<>();
            bundleFiles.put("javalens_report.csv", reportCsv);
            bundleFiles.put("javalens_report.xlsx", xlsxPath);
            bundleFiles.put("javalens_report.json", jsonPath);
            bundleFiles.put("javalens_report.html", htmlPath);
            bundleFiles.put("javalens_report.md", mdPath);
            bundleFiles.put("javalens_report.xml", xmlPath);
            Path summaryTxt = runDir.resolve("javalens_summary.txt");
            if (Files.exists(summaryTxt)) {
                bundleFiles.put("javalens_summary.txt", summaryTxt);
            }
            if (Files.exists(mergeCsv)) {
                bundleFiles.put("merge_results.csv", mergeCsv);
            }
            if (Files.exists(runDir.resolve("comparison_attributes.csv"))) {
                bundleFiles.put("comparison_attributes.csv", runDir.resolve("comparison_attributes.csv"));
            }
            if (Files.exists(runDir.resolve("comparison_methods.csv"))) {
                bundleFiles.put("comparison_methods.csv", runDir.resolve("comparison_methods.csv"));
            }
            writeReportZipBundle(zipPath, bundleFiles);
            return zipPath;
        }

        return target;
    }

    // ── Item Collector ────────────────────────────────────────────────────────

    private static List<ReportItem> collectReportItems(Map<String, JavaModel> oldModels,
                                                       Map<String, JavaModel> newModels,
                                                       Map<String, JavaModel> mergedModels) {
        List<ReportItem> items = new ArrayList<>();

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

                AttributeModel displayA = mergedA != null ? mergedA : (newA != null ? newA : oldA);

                boolean inOld = oldA != null;
                boolean inNew = newA != null;
                boolean inMerged = mergedA != null;
                boolean modified = inOld && inMerged && !attributesEqual(oldA, mergedA);

                String status = determineStatus(inOld, inNew, inMerged, modified);

                ReportItem item = new ReportItem();
                item.file = fileKey;
                item.packageName = displayA.packageName;
                item.className = displayA.className;
                item.memberType = "ATTRIBUTE";
                item.memberName = displayA.name;
                item.type = displayA.type;
                item.modifiers = displayA.modifiers;
                item.annotations = displayA.annotations != null ? displayA.annotations : "";
                item.extraInfo = displayA.initializer != null ? displayA.initializer : "";
                item.inOld = inOld;
                item.inNew = inNew;
                item.inMerged = inMerged;
                item.status = status;
                items.add(item);
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

                String extraInfo = displayM.parameters != null ? displayM.parameters : "";
                if (displayM.throwsList != null && !displayM.throwsList.isEmpty()) {
                    extraInfo += " throws " + displayM.throwsList;
                }

                ReportItem item = new ReportItem();
                item.file = fileKey;
                item.packageName = displayM.packageName;
                item.className = displayM.className;
                item.memberType = "METHOD";
                item.memberName = displayM.name;
                item.type = displayM.returnType;
                item.modifiers = displayM.modifiers;
                item.annotations = displayM.annotations != null ? displayM.annotations : "";
                item.extraInfo = extraInfo;
                item.inOld = inOld;
                item.inNew = inNew;
                item.inMerged = inMerged;
                item.status = status;
                items.add(item);
            }
        }

        return items;
    }

    // ── Multi-Format Writers ──────────────────────────────────────────────────

    private static void writeUnifiedReportCsv(Path reportCsv, List<ReportItem> items) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(reportCsv.toFile()), "UTF-8")))) {

            pw.println(csvRow(REPORT_HDR));
            for (ReportItem item : items) {
                pw.println(csvRow(new String[]{
                    item.file, item.packageName, item.className,
                    item.memberType, item.memberName, item.type, item.modifiers,
                    item.annotations, item.extraInfo,
                    item.inOld ? "YES" : "NO", item.inNew ? "YES" : "NO", item.inMerged ? "YES" : "NO",
                    item.status
                }));
            }
        }
    }

    private static void writeUnifiedReportJson(Path reportJson,
                                               ReportSummary summary,
                                               List<ReportItem> items,
                                               List<MergeEngine.MergeResult> mergeResults) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(reportJson.toFile()), "UTF-8")))) {

            pw.println("{");
            pw.println("  \"generator\": \"JavaLens Precision AST Static Analysis & Telemetry\",");
            pw.println("  \"version\": \"1.0.0\",");
            pw.println("  \"timestamp\": \"" + escapeJson(summary.timestamp) + "\",");
            pw.println("  \"summary\": {");
            pw.println("    \"totalMembers\": " + summary.totalMembers + ",");
            pw.println("    \"newlyAdded\": " + summary.newlyAdded + ",");
            pw.println("    \"modified\": " + summary.modified + ",");
            pw.println("    \"original\": " + summary.original + ",");
            pw.println("    \"removed\": " + summary.removed + ",");
            pw.println("    \"totalFiles\": " + summary.totalFiles + ",");
            pw.println("    \"mergedFiles\": " + summary.mergedFiles);
            pw.println("  },");
            pw.println("  \"sources\": {");
            pw.println("    \"oldPath\": \"" + escapeJson(summary.oldPath) + "\",");
            pw.println("    \"newPath\": \"" + escapeJson(summary.newPath) + "\",");
            pw.println("    \"runFolder\": \"" + escapeJson(summary.runFolder) + "\"");
            pw.println("  },");
            pw.println("  \"mergeResults\": [");
            for (int i = 0; i < mergeResults.size(); i++) {
                MergeEngine.MergeResult r = mergeResults.get(i);
                pw.print("    { \"file\": \"" + escapeJson(r.relPath) +
                         "\", \"status\": \"" + escapeJson(r.status) +
                         "\", \"existingSizeBytes\": " + r.existingSizeBytes +
                         ", \"mergedSizeBytes\": " + r.mergedSizeBytes +
                         ", \"deltaBytes\": " + r.deltaBytes +
                         ", \"existingLines\": " + r.existingLines +
                         ", \"mergedLines\": " + r.mergedLines +
                         ", \"deltaLines\": " + r.deltaLines +
                         ", \"typeChanges\": \"" + escapeJson(r.typeChanges) +
                         "\", \"sizeSummary\": \"" + escapeJson(r.sizeSummary) +
                         "\", \"message\": \"" + escapeJson(r.message) + "\" }");
                if (i < mergeResults.size() - 1) pw.println(",");
                else pw.println();
            }
            pw.println("  ],");
            pw.println("  \"members\": [");
            for (int i = 0; i < items.size(); i++) {
                ReportItem it = items.get(i);
                pw.println("    {");
                pw.println("      \"file\": \"" + escapeJson(it.file) + "\",");
                pw.println("      \"package\": \"" + escapeJson(it.packageName) + "\",");
                pw.println("      \"class\": \"" + escapeJson(it.className) + "\",");
                pw.println("      \"memberType\": \"" + escapeJson(it.memberType) + "\",");
                pw.println("      \"memberName\": \"" + escapeJson(it.memberName) + "\",");
                pw.println("      \"type\": \"" + escapeJson(it.type) + "\",");
                pw.println("      \"modifiers\": \"" + escapeJson(it.modifiers) + "\",");
                pw.println("      \"annotations\": \"" + escapeJson(it.annotations) + "\",");
                pw.println("      \"extraInfo\": \"" + escapeJson(it.extraInfo) + "\",");
                pw.println("      \"inOld\": " + it.inOld + ",");
                pw.println("      \"inNew\": " + it.inNew + ",");
                pw.println("      \"inMerged\": " + it.inMerged + ",");
                pw.println("      \"status\": \"" + escapeJson(it.status) + "\"");
                pw.print("    }");
                if (i < items.size() - 1) pw.println(",");
                else pw.println();
            }
            pw.println("  ]");
            pw.println("}");
        }
    }

    private static void writeUnifiedReportHtml(Path reportHtml,
                                               ReportSummary summary,
                                               List<ReportItem> items,
                                               List<MergeEngine.MergeResult> mergeResults) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(reportHtml.toFile()), "UTF-8")))) {

            pw.println("<!DOCTYPE html>");
            pw.println("<html lang=\"en\">");
            pw.println("<head>");
            pw.println("  <meta charset=\"UTF-8\">");
            pw.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            pw.println("  <title>JavaLens Unified Report — " + escapeHtml(summary.timestamp) + "</title>");
            pw.println("  <style>");
            pw.println("    :root { --bg: #09090b; --card: #121215; --border: #27272a; --text: #f4f4f5; --text-muted: #a1a1aa; --accent: #8b5cf6; }");
            pw.println("    * { box-sizing: border-box; margin: 0; padding: 0; }");
            pw.println("    body { font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: var(--bg); color: var(--text); padding: 32px; line-height: 1.5; font-size: 13px; }");
            pw.println("    .container { max-width: 1280px; margin: 0 auto; }");
            pw.println("    .header { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border); padding-bottom: 20px; margin-bottom: 24px; gap: 16px; }");
            pw.println("    .logo-badge { display: inline-flex; align-items: center; gap: 8px; font-weight: 700; font-size: 18px; letter-spacing: -0.02em; color: #fff; }");
            pw.println("    .logo-icon { width: 28px; height: 28px; border-radius: 6px; background: linear-gradient(135deg, #6366f1, #8b5cf6); display: flex; align-items: center; justify-content: center; font-family: monospace; font-size: 14px; font-weight: bold; }");
            pw.println("    .meta { font-family: monospace; font-size: 11px; color: var(--text-muted); }");
            pw.println("    .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 14px; margin-bottom: 24px; }");
            pw.println("    .card { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 14px 18px; }");
            pw.println("    .card-label { font-family: monospace; font-size: 10px; text-transform: uppercase; color: var(--text-muted); margin-bottom: 6px; }");
            pw.println("    .card-val { font-family: monospace; font-size: 24px; font-weight: 700; color: #fff; }");
            pw.println("    .card-val.green { color: #34d399; }");
            pw.println("    .card-val.amber { color: #fbbf24; }");
            pw.println("    .card-val.indigo { color: #818cf8; }");
            pw.println("    .controls { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; align-items: center; }");
            pw.println("    .search-input { background: var(--card); border: 1px solid var(--border); color: #fff; font-family: monospace; font-size: 12px; padding: 8px 14px; border-radius: 6px; flex: 1; min-width: 240px; }");
            pw.println("    .filter-select { background: var(--card); border: 1px solid var(--border); color: #fff; font-family: monospace; font-size: 12px; padding: 8px 12px; border-radius: 6px; }");
            pw.println("    .print-btn { background: #27272a; border: 1px solid #3f3f46; color: #fff; font-family: monospace; font-size: 12px; padding: 8px 16px; border-radius: 6px; cursor: pointer; }");
            pw.println("    .print-btn:hover { background: #3f3f46; }");
            pw.println("    .table-box { background: var(--card); border: 1px solid var(--border); border-radius: 8px; overflow-x: auto; margin-bottom: 32px; }");
            pw.println("    table { width: 100%; border-collapse: collapse; text-align: left; font-size: 12px; }");
            pw.println("    th { background: #18181b; padding: 10px 14px; font-family: monospace; font-size: 11px; text-transform: uppercase; color: var(--text-muted); border-bottom: 1px solid var(--border); }");
            pw.println("    td { padding: 9px 14px; border-bottom: 1px solid #1f1f23; font-family: monospace; white-space: nowrap; }");
            pw.println("    tr:hover td { background: #18181b; }");
            pw.println("    .badge { display: inline-block; padding: 2px 7px; border-radius: 4px; font-size: 10px; font-weight: 600; font-family: monospace; }");
            pw.println("    .badge-added { background: rgba(16,185,129,0.15); color: #34d399; border: 1px solid rgba(16,185,129,0.3); }");
            pw.println("    .badge-modified { background: rgba(245,158,11,0.15); color: #fbbf24; border: 1px solid rgba(245,158,11,0.3); }");
            pw.println("    .badge-orig { background: rgba(113,113,122,0.15); color: #a1a1aa; border: 1px solid rgba(113,113,122,0.3); }");
            pw.println("    .badge-meth { background: rgba(99,102,241,0.15); color: #818cf8; border: 1px solid rgba(99,102,241,0.3); }");
            pw.println("    .badge-attr { background: rgba(236,72,153,0.15); color: #f472b6; border: 1px solid rgba(236,72,153,0.3); }");
            pw.println("    .check-yes { color: #34d399; font-weight: bold; }");
            pw.println("    .check-no { color: #52525b; }");
            pw.println("    .section-title { font-size: 14px; font-weight: 600; margin-bottom: 12px; font-family: monospace; color: #fff; display: flex; align-items: center; gap: 8px; }");
            pw.println("    @media print {");
            pw.println("      body { background: #fff !important; color: #000 !important; padding: 12px; }");
            pw.println("      .controls, .print-btn { display: none !important; }");
            pw.println("      .card { border: 1px solid #ccc !important; background: #fff !important; }");
            pw.println("      .card-val { color: #000 !important; }");
            pw.println("      th { background: #f4f4f5 !important; color: #000 !important; border: 1px solid #ddd; }");
            pw.println("      td { border: 1px solid #ddd !important; color: #000 !important; }");
            pw.println("      .table-box { border: 1px solid #ddd !important; }");
            pw.println("    }");
            pw.println("  </style>");
            pw.println("</head>");
            pw.println("<body>");
            pw.println("  <div class=\"container\">");
            pw.println("    <header class=\"header\">");
            pw.println("      <div>");
            pw.println("        <div class=\"logo-badge\">");
            pw.println("          <div class=\"logo-icon\">JL</div>");
            pw.println("          <span>JavaLens Unified Report</span>");
            pw.println("        </div>");
            pw.println("        <div class=\"meta\" style=\"margin-top: 4px;\">Generated: " + escapeHtml(summary.timestamp) + " | Precision AST Static Analysis & Telemetry</div>");
            pw.println("      </div>");
            pw.println("      <div class=\"meta\" style=\"text-align: right;\">");
            pw.println("        <div>Baseline: " + escapeHtml(summary.oldPath) + "</div>");
            pw.println("        <div>Target  : " + escapeHtml(summary.newPath) + "</div>");
            pw.println("      </div>");
            pw.println("    </header>");

            pw.println("    <div class=\"cards\">");
            pw.println("      <div class=\"card\"><div class=\"card-label\">Total Members</div><div class=\"card-val\">" + summary.totalMembers + "</div></div>");
            pw.println("      <div class=\"card\"><div class=\"card-label\">Newly Added</div><div class=\"card-val green\">" + summary.newlyAdded + "</div></div>");
            pw.println("      <div class=\"card\"><div class=\"card-label\">Modified by Merge</div><div class=\"card-val amber\">" + summary.modified + "</div></div>");
            pw.println("      <div class=\"card\"><div class=\"card-label\">Original / Baseline</div><div class=\"card-val indigo\">" + summary.original + "</div></div>");
            pw.println("      <div class=\"card\"><div class=\"card-label\">Files Merged</div><div class=\"card-val green\">" + summary.mergedFiles + " / " + summary.totalFiles + "</div></div>");
            pw.println("    </div>");

            pw.println("    <div class=\"controls\">");
            pw.println("      <input type=\"text\" id=\"filter-input\" class=\"search-input\" placeholder=\"Search by file, class, member, type, modifier...\" oninput=\"filterRows()\">");
            pw.println("      <select id=\"status-select\" class=\"filter-select\" onchange=\"filterRows()\">");
            pw.println("        <option value=\"\">All Statuses</option>");
            pw.println("        <option value=\"NEWLY_ADDED\">Newly Added</option>");
            pw.println("        <option value=\"MODIFIED_BY_MERGE\">Modified by Merge</option>");
            pw.println("        <option value=\"ORIGINAL\">Original</option>");
            pw.println("      </select>");
            pw.println("      <button onclick=\"window.print()\" class=\"print-btn\">Print / Save PDF</button>");
            pw.println("    </div>");

            pw.println("    <div class=\"table-box\">");
            pw.println("      <table id=\"report-table\">");
            pw.println("        <thead>");
            pw.println("          <tr>");
            pw.println("            <th>File</th><th>Class</th><th>Type</th><th>Member Name</th><th>Signature / Type</th><th>Modifiers</th><th>Old</th><th>New</th><th>Merged</th><th>Status</th>");
            pw.println("          </tr>");
            pw.println("        </thead>");
            pw.println("        <tbody>");

            for (ReportItem it : items) {
                String statusBadge;
                if ("NEWLY_ADDED".equalsIgnoreCase(it.status)) statusBadge = "<span class=\"badge badge-added\">ADDED</span>";
                else if ("MODIFIED_BY_MERGE".equalsIgnoreCase(it.status)) statusBadge = "<span class=\"badge badge-modified\">MODIFIED</span>";
                else statusBadge = "<span class=\"badge badge-orig\">ORIGINAL</span>";

                String typeBadge = "METHOD".equalsIgnoreCase(it.memberType) ?
                    "<span class=\"badge badge-meth\">METH</span>" : "<span class=\"badge badge-attr\">ATTR</span>";

                pw.println("          <tr data-status=\"" + escapeHtml(it.status) + "\">");
                pw.println("            <td>" + escapeHtml(it.file) + "</td>");
                pw.println("            <td>" + escapeHtml(it.className) + "</td>");
                pw.println("            <td>" + typeBadge + "</td>");
                pw.println("            <td><strong>" + escapeHtml(it.memberName) + "</strong></td>");
                pw.println("            <td style=\"color: #67e8f9;\">" + escapeHtml(it.type) + "</td>");
                pw.println("            <td style=\"color: #a78bfa;\">" + escapeHtml(it.modifiers) + "</td>");
                pw.println("            <td class=\"" + (it.inOld ? "check-yes" : "check-no") + "\">" + (it.inOld ? "✓" : "—") + "</td>");
                pw.println("            <td class=\"" + (it.inNew ? "check-yes" : "check-no") + "\">" + (it.inNew ? "✓" : "—") + "</td>");
                pw.println("            <td class=\"" + (it.inMerged ? "check-yes" : "check-no") + "\">" + (it.inMerged ? "✓" : "—") + "</td>");
                pw.println("            <td>" + statusBadge + "</td>");
                pw.println("          </tr>");
            }

            pw.println("        </tbody>");
            pw.println("      </table>");
            pw.println("    </div>");

            pw.println("    <div class=\"section-title\">Merge Operation Audit Trail &amp; Size / Type Telemetry</div>");
            pw.println("    <div class=\"table-box\">");
            pw.println("      <table>");
            pw.println("        <thead><tr><th>Target File</th><th>Status</th><th>Existing Size</th><th>Merged Size</th><th>Delta (Bytes)</th><th>Lines Delta</th><th>Type Changes</th><th>Diagnostics</th></tr></thead>");
            pw.println("        <tbody>");
            for (MergeEngine.MergeResult r : mergeResults) {
                String bColor = r.deltaBytes > 0 ? "#10b981" : (r.deltaBytes < 0 ? "#f43f5e" : "var(--text-muted)");
                pw.println("          <tr>");
                pw.println("            <td style=\"font-weight: 600;\">" + escapeHtml(r.relPath) + "</td>");
                pw.println("            <td><span class=\"badge badge-added\">" + escapeHtml(r.status) + "</span></td>");
                pw.println("            <td>" + (r.existingSizeBytes > 0 ? MergeEngine.formatBytes(r.existingSizeBytes) : "0 B") + " (" + r.existingLines + " L)</td>");
                pw.println("            <td>" + (r.mergedSizeBytes > 0 ? MergeEngine.formatBytes(r.mergedSizeBytes) : "0 B") + " (" + r.mergedLines + " L)</td>");
                pw.println("            <td style=\"color: " + bColor + "; font-family: monospace;\">" + (r.deltaBytes > 0 ? "+" : "") + MergeEngine.formatBytes(r.deltaBytes) + "</td>");
                pw.println("            <td style=\"font-family: monospace;\">" + (r.deltaLines > 0 ? "+" : "") + r.deltaLines + " L</td>");
                pw.println("            <td style=\"color: #38bdf8; font-size: 11px;\">" + escapeHtml(r.typeChanges) + "</td>");
                pw.println("            <td style=\"color: var(--text-muted); font-size: 11px;\">" + escapeHtml(r.message) + "</td>");
                pw.println("          </tr>");
            }
            pw.println("        </tbody>");
            pw.println("      </table>");
            pw.println("    </div>");

            pw.println("  </div>");
            pw.println("  <script>");
            pw.println("    function filterRows() {");
            pw.println("      const q = document.getElementById('filter-input').value.toLowerCase().trim();");
            pw.println("      const s = document.getElementById('status-select').value.toUpperCase().trim();");
            pw.println("      const rows = document.querySelectorAll('#report-table tbody tr');");
            pw.println("      rows.forEach(tr => {");
            pw.println("        const st = tr.getAttribute('data-status') || '';");
            pw.println("        const txt = tr.textContent.toLowerCase();");
            pw.println("        const matchesS = !s || st === s;");
            pw.println("        const matchesQ = !q || txt.includes(q);");
            pw.println("        tr.style.display = (matchesS && matchesQ) ? '' : 'none';");
            pw.println("      });");
            pw.println("    }");
            pw.println("  </script>");
            pw.println("</body>");
            pw.println("</html>");
        }
    }

    private static void writeUnifiedReportMarkdown(Path reportMd,
                                                  ReportSummary summary,
                                                  List<ReportItem> items,
                                                  List<MergeEngine.MergeResult> mergeResults) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(reportMd.toFile()), "UTF-8")))) {

            pw.println("# JavaLens Unified AST Static Analysis & Telemetry Report");
            pw.println();
            pw.println("- **Generated At**: `" + summary.timestamp + "`");
            pw.println("- **Baseline (Old) Path**: `" + summary.oldPath + "`");
            pw.println("- **Target (New) Path**: `" + summary.newPath + "`");
            pw.println("- **Active Run Folder**: `" + summary.runFolder + "`");
            pw.println();
            pw.println("## Executive Summary");
            pw.println();
            pw.println("| Metric | Value | Description |");
            pw.println("|---|---|---|");
            pw.println("| **Total Members** | " + summary.totalMembers + " | Total attributes and methods evaluated across ASTs |");
            pw.println("| **Newly Added** | " + summary.newlyAdded + " | Declarations introduced by the new version |");
            pw.println("| **Modified by Merge** | " + summary.modified + " | Declarations successfully updated inside merge boundaries |");
            pw.println("| **Original / Baseline** | " + summary.original + " | Unaltered baseline declarations |");
            pw.println("| **Removed** | " + summary.removed + " | Declarations absent from target/merged output |");
            pw.println("| **Merged Files** | " + summary.mergedFiles + " / " + summary.totalFiles + " | Files merged via marker-guided boundary tags |");
            pw.println();
            pw.println("## Merge Operations Audit");
            pw.println();
            pw.println("| File | Status | Old Size | Merged Size | Byte Delta | Lines Delta | Type Changes | Details |");
            pw.println("|---|---|---|---|---|---|---|---|");
            for (MergeEngine.MergeResult r : mergeResults) {
                String oldSz = r.existingSizeBytes > 0 ? MergeEngine.formatBytes(r.existingSizeBytes) : "0 B";
                String newSz = r.mergedSizeBytes > 0 ? MergeEngine.formatBytes(r.mergedSizeBytes) : "0 B";
                String byteDelta = (r.deltaBytes > 0 ? "+" : "") + MergeEngine.formatBytes(r.deltaBytes);
                String linesDelta = (r.deltaLines > 0 ? "+" : "") + r.deltaLines + " L";
                pw.println("| `" + r.relPath + "` | **" + r.status + "** | " + oldSz + " | " + newSz + " | " + byteDelta + " | " + linesDelta + " | " + r.typeChanges + " | " + r.message + " |");
            }
            pw.println();
            pw.println("## AST Members Breakdown");
            pw.println();
            pw.println("| File | Class | Type | Name | Signature / Return | Modifiers | Old | New | Merged | Status |");
            pw.println("|---|---|---|---|---|---|---|---|---|---|");
            for (ReportItem it : items) {
                pw.println("| `" + it.file + "` | `" + it.className + "` | " + it.memberType +
                           " | **" + it.memberName + "** | `" + it.type + "` | " + it.modifiers +
                           " | " + (it.inOld ? "YES" : "NO") + " | " + (it.inNew ? "YES" : "NO") +
                           " | " + (it.inMerged ? "YES" : "NO") + " | `" + it.status + "` |");
            }
        }
    }

    private static void writeUnifiedReportXml(Path reportXml,
                                              ReportSummary summary,
                                              List<ReportItem> items,
                                              List<MergeEngine.MergeResult> mergeResults) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(reportXml.toFile()), "UTF-8")))) {

            pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            pw.println("<JavaLensReport generated=\"" + escapeXml(summary.timestamp) + "\" version=\"1.0.0\">");
            pw.println("  <Summary>");
            pw.println("    <TotalMembers>" + summary.totalMembers + "</TotalMembers>");
            pw.println("    <NewlyAdded>" + summary.newlyAdded + "</NewlyAdded>");
            pw.println("    <Modified>" + summary.modified + "</Modified>");
            pw.println("    <Original>" + summary.original + "</Original>");
            pw.println("    <Removed>" + summary.removed + "</Removed>");
            pw.println("    <TotalFiles>" + summary.totalFiles + "</TotalFiles>");
            pw.println("    <MergedFiles>" + summary.mergedFiles + "</MergedFiles>");
            pw.println("    <OldPath>" + escapeXml(summary.oldPath) + "</OldPath>");
            pw.println("    <NewPath>" + escapeXml(summary.newPath) + "</NewPath>");
            pw.println("    <RunFolder>" + escapeXml(summary.runFolder) + "</RunFolder>");
            pw.println("  </Summary>");
            pw.println("  <MergeResults>");
            for (MergeEngine.MergeResult r : mergeResults) {
                pw.println("    <MergeResult file=\"" + escapeXml(r.relPath) +
                           "\" status=\"" + escapeXml(r.status) +
                           "\" existingSizeBytes=\"" + r.existingSizeBytes +
                           "\" mergedSizeBytes=\"" + r.mergedSizeBytes +
                           "\" deltaBytes=\"" + r.deltaBytes +
                           "\" existingLines=\"" + r.existingLines +
                           "\" mergedLines=\"" + r.mergedLines +
                           "\" deltaLines=\"" + r.deltaLines +
                           "\" typeChanges=\"" + escapeXml(r.typeChanges) +
                           "\" sizeSummary=\"" + escapeXml(r.sizeSummary) +
                           "\" message=\"" + escapeXml(r.message) + "\" />");
            }
            pw.println("  </MergeResults>");
            pw.println("  <Members>");
            for (ReportItem it : items) {
                pw.println("    <Member file=\"" + escapeXml(it.file) +
                           "\" package=\"" + escapeXml(it.packageName) +
                           "\" class=\"" + escapeXml(it.className) +
                           "\" memberType=\"" + escapeXml(it.memberType) +
                           "\" name=\"" + escapeXml(it.memberName) +
                           "\" type=\"" + escapeXml(it.type) +
                           "\" modifiers=\"" + escapeXml(it.modifiers) +
                           "\" annotations=\"" + escapeXml(it.annotations) +
                           "\" extraInfo=\"" + escapeXml(it.extraInfo) +
                           "\" inOld=\"" + it.inOld +
                           "\" inNew=\"" + it.inNew +
                           "\" inMerged=\"" + it.inMerged +
                           "\" status=\"" + escapeXml(it.status) + "\" />");
            }
            pw.println("  </Members>");
            pw.println("</JavaLensReport>");
        }
    }

    private static void writeReportZipBundle(Path zipPath, Map<String, Path> filesToZip) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipPath.toFile())))) {
            byte[] buffer = new byte[8192];
            for (Map.Entry<String, Path> entry : filesToZip.entrySet()) {
                Path file = entry.getValue();
                if (Files.exists(file)) {
                    ZipEntry ze = new ZipEntry(entry.getKey());
                    zos.putNextEntry(ze);
                    try (InputStream is = Files.newInputStream(file)) {
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    private static void writeUnifiedReportExcel(Path reportXlsx,
                                                ReportSummary summary,
                                                List<ReportItem> items,
                                                List<MergeEngine.MergeResult> mergeResults) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(reportXlsx.toFile())))) {
            // 1. [Content_Types].xml
            String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n"
                    + "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n"
                    + "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n"
                    + "  <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n"
                    + "  <Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>\n"
                    + "  <Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n"
                    + "  <Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n"
                    + "  <Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n"
                    + "</Types>";
            addZipEntry(zos, "[Content_Types].xml", contentTypes.getBytes(StandardCharsets.UTF_8));

            // 2. _rels/.rels
            String rootRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n"
                    + "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>\n"
                    + "</Relationships>";
            addZipEntry(zos, "_rels/.rels", rootRels.getBytes(StandardCharsets.UTF_8));

            // 3. xl/_rels/workbook.xml.rels
            String wbRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n"
                    + "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>\n"
                    + "  <Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>\n"
                    + "  <Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet3.xml\"/>\n"
                    + "  <Relationship Id=\"rId4\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>\n"
                    + "</Relationships>";
            addZipEntry(zos, "xl/_rels/workbook.xml.rels", wbRels.getBytes(StandardCharsets.UTF_8));

            // 4. xl/workbook.xml
            String workbookXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n"
                    + "  <sheets>\n"
                    + "    <sheet name=\"Unified AST Report\" sheetId=\"1\" r:id=\"rId1\"/>\n"
                    + "    <sheet name=\"Merge Results\" sheetId=\"2\" r:id=\"rId2\"/>\n"
                    + "    <sheet name=\"Executive Summary\" sheetId=\"3\" r:id=\"rId3\"/>\n"
                    + "  </sheets>\n"
                    + "</workbook>";
            addZipEntry(zos, "xl/workbook.xml", workbookXml.getBytes(StandardCharsets.UTF_8));

            // 5. xl/styles.xml
            String stylesXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                    + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n"
                    + "  <fonts count=\"2\">\n"
                    + "    <font><sz val=\"11\"/><name val=\"Calibri\"/></font>\n"
                    + "    <font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>\n"
                    + "  </fonts>\n"
                    + "  <fills count=\"2\">\n"
                    + "    <fill><patternFill patternType=\"none\"/></fill>\n"
                    + "    <fill><patternFill patternType=\"gray125\"/></fill>\n"
                    + "  </fills>\n"
                    + "  <borders count=\"1\">\n"
                    + "    <border><left/><right/><top/><bottom/><diagonal/></border>\n"
                    + "  </borders>\n"
                    + "  <cellStyleXfs count=\"1\">\n"
                    + "    <xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/>\n"
                    + "  </cellStyleXfs>\n"
                    + "  <cellXfs count=\"2\">\n"
                    + "    <xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>\n"
                    + "    <xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>\n"
                    + "  </cellXfs>\n"
                    + "</styleSheet>";
            addZipEntry(zos, "xl/styles.xml", stylesXml.getBytes(StandardCharsets.UTF_8));

            // 6. xl/worksheets/sheet1.xml - Unified AST Report
            StringBuilder s1 = new StringBuilder(65536);
            s1.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            s1.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");
            s1.append("  <sheetData>\n");

            int rowIdx = 1;
            s1.append("    <row r=\"").append(rowIdx).append("\">\n");
            for (int c = 0; c < REPORT_HDR.length; c++) {
                String colRef = toExcelCol(c) + rowIdx;
                s1.append("      <c r=\"").append(colRef).append("\" t=\"inlineStr\" s=\"1\"><is><t>")
                  .append(escapeXml(REPORT_HDR[c])).append("</t></is></c>\n");
            }
            s1.append("    </row>\n");

            for (ReportItem item : items) {
                rowIdx++;
                s1.append("    <row r=\"").append(rowIdx).append("\">\n");
                String[] vals = new String[]{
                    item.file, item.packageName, item.className, item.memberType, item.memberName,
                    item.type, item.modifiers, item.annotations, item.extraInfo,
                    item.inOld ? "YES" : "NO", item.inNew ? "YES" : "NO", item.inMerged ? "YES" : "NO",
                    item.status
                };
                for (int c = 0; c < vals.length; c++) {
                    String colRef = toExcelCol(c) + rowIdx;
                    s1.append("      <c r=\"").append(colRef).append("\" t=\"inlineStr\"><is><t>")
                      .append(escapeXml(vals[c])).append("</t></is></c>\n");
                }
                s1.append("    </row>\n");
            }
            s1.append("  </sheetData>\n</worksheet>");
            addZipEntry(zos, "xl/worksheets/sheet1.xml", s1.toString().getBytes(StandardCharsets.UTF_8));

            // 7. xl/worksheets/sheet2.xml - Merge Results
            StringBuilder s2 = new StringBuilder(16384);
            s2.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            s2.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");
            s2.append("  <sheetData>\n");
            rowIdx = 1;
            s2.append("    <row r=\"").append(rowIdx).append("\">\n");
            String[] mHdr = new String[]{
                "File", "Status", "Existing Size (Bytes)", "Merged Size (Bytes)", "Byte Delta",
                "Existing Lines", "Merged Lines", "Lines Delta", "Type Changes", "Size Summary", "Message"
            };
            for (int c = 0; c < mHdr.length; c++) {
                String colRef = toExcelCol(c) + rowIdx;
                s2.append("      <c r=\"").append(colRef).append("\" t=\"inlineStr\" s=\"1\"><is><t>")
                  .append(escapeXml(mHdr[c])).append("</t></is></c>\n");
            }
            s2.append("    </row>\n");

            if (mergeResults != null) {
                for (MergeEngine.MergeResult r : mergeResults) {
                    rowIdx++;
                    s2.append("    <row r=\"").append(rowIdx).append("\">\n");
                    String[] mVals = new String[]{
                        r.relPath,
                        r.status,
                        String.valueOf(r.existingSizeBytes),
                        String.valueOf(r.mergedSizeBytes),
                        (r.deltaBytes > 0 ? "+" : "") + r.deltaBytes,
                        String.valueOf(r.existingLines),
                        String.valueOf(r.mergedLines),
                        (r.deltaLines > 0 ? "+" : "") + r.deltaLines,
                        r.typeChanges != null ? r.typeChanges : "No type changes",
                        r.sizeSummary != null ? r.sizeSummary : "",
                        r.message != null ? r.message : ""
                    };
                    for (int c = 0; c < mVals.length; c++) {
                        String colRef = toExcelCol(c) + rowIdx;
                        s2.append("      <c r=\"").append(colRef).append("\" t=\"inlineStr\"><is><t>")
                          .append(escapeXml(mVals[c])).append("</t></is></c>\n");
                    }
                    s2.append("    </row>\n");
                }
            }
            s2.append("  </sheetData>\n</worksheet>");
            addZipEntry(zos, "xl/worksheets/sheet2.xml", s2.toString().getBytes(StandardCharsets.UTF_8));

            // 8. xl/worksheets/sheet3.xml - Executive Summary
            StringBuilder s3 = new StringBuilder(4096);
            s3.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            s3.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");
            s3.append("  <sheetData>\n");
            rowIdx = 1;
            s3.append("    <row r=\"").append(rowIdx).append("\">\n");
            s3.append("      <c r=\"A1\" t=\"inlineStr\" s=\"1\"><is><t>Metric / Attribute</t></is></c>\n");
            s3.append("      <c r=\"B1\" t=\"inlineStr\" s=\"1\"><is><t>Value</t></is></c>\n");
            s3.append("    </row>\n");

            String[][] sumRows = new String[][]{
                {"Generated At", summary.timestamp},
                {"Existing Java Files Path", summary.oldPath},
                {"Newly Generated Java Files Path", summary.newPath},
                {"Run Output Directory", summary.runFolder},
                {"Total AST Members Evaluated", String.valueOf(summary.totalMembers)},
                {"Newly Added Members", String.valueOf(summary.newlyAdded)},
                {"Modified by Merge", String.valueOf(summary.modified)},
                {"Original Unchanged Members", String.valueOf(summary.original)},
                {"Removed Members", String.valueOf(summary.removed)},
                {"Total Files Analyzed", String.valueOf(summary.totalFiles)},
                {"Total Files Merged", String.valueOf(summary.mergedFiles)}
            };
            for (String[] sr : sumRows) {
                rowIdx++;
                s3.append("    <row r=\"").append(rowIdx).append("\">\n");
                s3.append("      <c r=\"A").append(rowIdx).append("\" t=\"inlineStr\"><is><t>").append(escapeXml(sr[0])).append("</t></is></c>\n");
                s3.append("      <c r=\"B").append(rowIdx).append("\" t=\"inlineStr\"><is><t>").append(escapeXml(sr[1])).append("</t></is></c>\n");
                s3.append("    </row>\n");
            }
            s3.append("  </sheetData>\n</worksheet>");
            addZipEntry(zos, "xl/worksheets/sheet3.xml", s3.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void addZipEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry ze = new ZipEntry(name);
        zos.putNextEntry(ze);
        zos.write(data);
        zos.closeEntry();
    }

    private static String toExcelCol(int col) {
        StringBuilder sb = new StringBuilder();
        col++;
        while (col > 0) {
            int rem = (col - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            col = (col - 1) / 26;
        }
        return sb.toString();
    }

    private static void writeSummaryTxt(Path summaryTxt, ReportSummary summary,
                                        List<MergeEngine.MergeResult> mergeResults) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(summaryTxt.toFile()), StandardCharsets.UTF_8)))) {
            pw.println("================================================================================");
            pw.println("                        JAVALENS AST ANALYSIS & MERGE SUMMARY                   ");
            pw.println("================================================================================");
            pw.println("Generated At        : " + summary.timestamp);
            pw.println("Run Directory       : " + summary.runFolder);
            pw.println("Existing Files Path : " + summary.oldPath);
            pw.println("Generated Files Path: " + summary.newPath);
            pw.println();
            pw.println("--------------------------------------------------------------------------------");
            pw.println(" AST CODEBASE METRICS");
            pw.println("--------------------------------------------------------------------------------");
            pw.println("Total Members Evaluated : " + summary.totalMembers);
            pw.println("  ▸ Newly Added         : " + summary.newlyAdded);
            pw.println("  ▸ Modified by Merge   : " + summary.modified);
            pw.println("  ▸ Original (Baseline) : " + summary.original);
            pw.println("  ▸ Removed             : " + summary.removed);
            pw.println();
            pw.println("--------------------------------------------------------------------------------");
            pw.println(" MERGE ACTIVITY DETAILS");
            pw.println("--------------------------------------------------------------------------------");
            pw.println("Total Files Processed   : " + (mergeResults != null ? mergeResults.size() : summary.totalFiles));
            pw.println("Files Merged            : " + summary.mergedFiles);
            if (mergeResults != null && !mergeResults.isEmpty()) {
                pw.println();
                pw.println("--------------------------------------------------------------------------------");
                pw.println(" MERGE SIZE & TYPE CHANGES SUMMARY");
                pw.println("--------------------------------------------------------------------------------");
                pw.printf("%-24s %-8s %10s %10s %12s %12s  %-30s%n",
                        "File", "Status", "Old Size", "New Size", "Byte Delta", "Lines Delta", "Type Changes");
                pw.println("--------------------------------------------------------------------------------");
                for (MergeEngine.MergeResult r : mergeResults) {
                    String oldSz = r.existingSizeBytes > 0 ? MergeEngine.formatBytes(r.existingSizeBytes) : "0 B";
                    String newSz = r.mergedSizeBytes > 0 ? MergeEngine.formatBytes(r.mergedSizeBytes) : "0 B";
                    String byteDelta = (r.deltaBytes > 0 ? "+" : "") + MergeEngine.formatBytes(r.deltaBytes);
                    String linesDelta = (r.deltaLines > 0 ? "+" : "") + r.deltaLines + " L";
                    pw.printf("%-24s %-8s %10s %10s %12s %12s  %s%n",
                            truncateStr(r.relPath, 24), r.status, oldSz, newSz, byteDelta, linesDelta, r.typeChanges);
                }
                pw.println();
                pw.println("--------------------------------------------------------------------------------");
                pw.println(" MERGE ACTIVITY DETAILS");
                pw.println("--------------------------------------------------------------------------------");
                pw.printf("%-40s %-15s %s%n", "File", "Status", "Details");
                pw.println("--------------------------------------------------------------------------------");
                for (MergeEngine.MergeResult r : mergeResults) {
                    pw.printf("%-40s %-15s %s%n", r.relPath, r.status, r.message != null ? r.message : "");
                }
            }
            pw.println("================================================================================");
        }
    }

    private static String escapeXml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }

    private static String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
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
        MergeEngine.writeMergeResultsCsv(mergeCsv, mergeResults);
    }

    private static long parseLong(String val, long fallback) {
        if (val == null || val.isBlank()) return fallback;
        try { return Long.parseLong(val.trim()); } catch (Exception e) { return fallback; }
    }

    private static int parseInt(String val, int fallback) {
        if (val == null || val.isBlank()) return fallback;
        try { return Integer.parseInt(val.trim()); } catch (Exception e) { return fallback; }
    }

    private static String truncateStr(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
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
