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

import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generates unified reports consolidating analyze, compare, and merge activities
 * across multiple standard formats: CSV, JSON, HTML, Markdown, XML, and ZIP bundle.
 * Pipeline: parse old → parse new → compare → merge → re-parse merged → generate reports.
 */
public class ReportGenerator {

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
        System.out.println("▸ Step 5/5: Re-analyzing merged output & generating reports...");
        Map<String, JavaModel> mergedModels = parseAllFiles(oldPath);
        System.out.println("  Found " + mergedModels.size() + " Java file(s) in merged output.");

        // Write analysis CSVs from merged output
        writeAnalysisCsvs(runFolder, mergedModels);

        // Collect unified report items
        List<ReportItem> items = collectReportItems(oldModels, newModels, mergedModels);

        // Compute summary statistics
        ReportSummary summary = new ReportSummary();
        summary.timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                               .format(java.time.LocalDateTime.now());
        summary.oldPath = oldPath.toString();
        summary.newPath = newPath.toString();
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

        // Generate unified reports across all formats
        Path reportCsv = runFolder.resolve("javalens_report.csv");
        writeUnifiedReportCsv(reportCsv, items);

        Path reportJson = runFolder.resolve("javalens_report.json");
        writeUnifiedReportJson(reportJson, summary, items, mergeResults);

        Path reportHtml = runFolder.resolve("javalens_report.html");
        writeUnifiedReportHtml(reportHtml, summary, items, mergeResults);

        Path reportMd = runFolder.resolve("javalens_report.md");
        writeUnifiedReportMarkdown(reportMd, summary, items, mergeResults);

        Path reportXml = runFolder.resolve("javalens_report.xml");
        writeUnifiedReportXml(reportXml, summary, items, mergeResults);

        // Write merge results CSV
        Path mergeCsv = runFolder.resolve("merge_results.csv");
        writeMergeResultsCsv(mergeCsv, mergeResults);

        // Create unified ZIP bundle
        Path reportZip = runFolder.resolve("javalens_report_bundle.zip");
        Map<String, Path> bundleFiles = new LinkedHashMap<>();
        bundleFiles.put("javalens_report.csv", reportCsv);
        bundleFiles.put("javalens_report.json", reportJson);
        bundleFiles.put("javalens_report.html", reportHtml);
        bundleFiles.put("javalens_report.md", reportMd);
        bundleFiles.put("javalens_report.xml", reportXml);
        bundleFiles.put("merge_results.csv", mergeCsv);
        if (Files.exists(runFolder.resolve("comparison_attributes.csv"))) {
            bundleFiles.put("comparison_attributes.csv", runFolder.resolve("comparison_attributes.csv"));
        }
        if (Files.exists(runFolder.resolve("comparison_methods.csv"))) {
            bundleFiles.put("comparison_methods.csv", runFolder.resolve("comparison_methods.csv"));
        }
        writeReportZipBundle(reportZip, bundleFiles);

        // Write report data JS for web UI
        ParserUtil.writeReportDataJs("REPORT", runFolderName, runFolder);

        System.out.println();
        System.out.println("═════════════════════════════════════════════════════════════════");
        System.out.println("  JavaLens Unified Reports Generated in Multiple Formats:");
        System.out.println("    ▸ CSV       : " + reportCsv.toAbsolutePath());
        System.out.println("    ▸ JSON      : " + reportJson.toAbsolutePath());
        System.out.println("    ▸ HTML      : " + reportHtml.toAbsolutePath());
        System.out.println("    ▸ Markdown  : " + reportMd.toAbsolutePath());
        System.out.println("    ▸ XML       : " + reportXml.toAbsolutePath());
        System.out.println("    ▸ ZIP Bundle: " + reportZip.toAbsolutePath());
        System.out.println("  Total members : " + items.size() + " (" + summary.newlyAdded + " Added, " +
                           summary.modified + " Modified, " + summary.original + " Original)");
        System.out.println("  Merge results : " + mergeResults.size() + " file(s) processed");
        System.out.println("  Output folder : " + runFolder.toAbsolutePath());
        System.out.println("═════════════════════════════════════════════════════════════════");

        return reportCsv;
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

            pw.println("    <div class=\"section-title\">Merge Operation Audit Trail</div>");
            pw.println("    <div class=\"table-box\">");
            pw.println("      <table>");
            pw.println("        <thead><tr><th>Target File</th><th>Status</th><th>Diagnostics</th></tr></thead>");
            pw.println("        <tbody>");
            for (MergeEngine.MergeResult r : mergeResults) {
                pw.println("          <tr>");
                pw.println("            <td>" + escapeHtml(r.relPath) + "</td>");
                pw.println("            <td><span class=\"badge badge-added\">" + escapeHtml(r.status) + "</span></td>");
                pw.println("            <td style=\"color: var(--text-muted);\">" + escapeHtml(r.message) + "</td>");
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
            pw.println("| File | Status | Message |");
            pw.println("|---|---|---|");
            for (MergeEngine.MergeResult r : mergeResults) {
                pw.println("| `" + r.relPath + "` | **" + r.status + "** | " + r.message + " |");
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
