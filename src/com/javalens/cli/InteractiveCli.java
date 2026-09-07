package com.javalens.cli;

import com.javalens.Config;
import com.javalens.JavaAnalyzer;
import com.javalens.engine.CompareEngine;
import com.javalens.engine.MergeEngine;
import com.javalens.engine.ReportGenerator;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class InteractiveCli {

    private static final Scanner scanner = new Scanner(System.in);
    private static Config currentConfig;

    public static void start() {
        printBanner();

        // Load initial config from properties template
        currentConfig = Config.parse(new String[0]);

        while (true) {
            System.out.println("\n\u001B[1;97mSelect Operation Mode:\u001B[0m");
            System.out.println("  \u001B[36;1m[1]\u001B[0m \u001B[1mANALYZE\u001B[0m   - Scan codebase and extract class signatures");
            System.out.println("  \u001B[36;1m[2]\u001B[0m \u001B[1mCOMPARE\u001B[0m   - Contrast two versions of files/folders");
            System.out.println("  \u001B[36;1m[3]\u001B[0m \u001B[1mMERGE\u001B[0m     - Copy marked portions from source to destination");
            System.out.println("  \u001B[32;1m[4]\u001B[0m \u001B[1mREPORT\u001B[0m    - Full pipeline: analyze, compare, merge & unified report");
            System.out.println("  \u001B[33;1m[5]\u001B[0m \u001B[1mCONFIG\u001B[0m    - View and edit properties configurations");
            System.out.println("  \u001B[31;1m[6]\u001B[0m \u001B[1mEXIT\u001B[0m      - Terminate tool");
            System.out.print("\u001B[35;1m⚡ Select action [1-6]: \u001B[0m");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
                    runAnalyzeFlow();
                    break;
                case "2":
                    runCompareFlow();
                    break;
                case "3":
                    runMergeFlow();
                    break;
                case "4":
                    runReportFlow();
                    break;
                case "5":
                    runConfigFlow();
                    break;
                case "6":
                    System.out.println("\n\u001B[32;1m✔ Exiting JavaLens. Goodbye!\u001B[0m");
                    return;
                default:
                    System.out.println("\u001B[31;1m⚠ Invalid selection. Please choose an option from 1 to 6.\u001B[0m");
            }
        }
    }

    private static void printBanner() {
        System.out.println("\u001B[35;1m       _                  _                    \u001B[0m");
        System.out.println("\u001B[35;1m      | | __ ___   ____ _| |    ___ _ __  ___  \u001B[0m");
        System.out.println("\u001B[35;1m   _  | |/ _` \\ \\ / / _` | |   / _ \\ '_ \\/ __| \u001B[0m");
        System.out.println("\u001B[36;1m  | |_| | (_| |\\ V / (_| | |__|  __/ | | \\__ \\ \u001B[0m");
        System.out.println("\u001B[36;1m   \\___/ \\__,_| \\_/ \\__,_|_____\\___|_| |_|___/ \u001B[0m");
        System.out.println("\u001B[33;1m          === ENTERPRISE STATIC AST ENGINE === \u001B[0m");
    }

    private static void runAnalyzeFlow() {
        System.out.println("\n\u001B[35;1m╔══════════════════════════════════════════════════════╗\u001B[0m");
        System.out.println("\u001B[35;1m║             ACTION: CODEBASE ANALYSIS                ║\u001B[0m");
        System.out.println("\u001B[35;1m╚══════════════════════════════════════════════════════╝\u001B[0m");
        
        String workspace = promptInput("Enter Workspace Folder path", ".");
        if (workspace.isEmpty()) {
            System.out.println("\u001B[31;1m⚠ Error: Workspace folder path is required.\u001B[0m");
            return;
        }

        // Default output path inside the workspace folder
        String defaultOut = workspace.equals(".") || workspace.equals("./") ? "java_analysis_output" : workspace + "/java_analysis_output";
        String outDir = promptInput("Enter Output Directory Path", defaultOut);
        String threadStr = promptInput("Enter Parallel Threads", String.valueOf(currentConfig.getThreads()));
        int threads = parseThreadCount(threadStr);

        System.out.print("\u001B[33;1m▶ Confirm execution? (y/n) [y]: \u001B[0m");
        String confirm = scanner.nextLine().trim().toLowerCase();
        if (confirm.equals("n")) {
            System.out.println("\u001B[90mOperation aborted.\u001B[0m");
            return;
        }

        System.out.println("\n\u001B[33m[SYS] Initiating AST scan thread-pool...\u001B[0m");
        try {
            List<String> argsList = new ArrayList<>(Arrays.asList("-m", "analyze", "-s", workspace, "--output-dir", outDir, "-t", String.valueOf(threads)));
            Config config = Config.parse(argsList.toArray(new String[0]));
            
            long startTime = System.currentTimeMillis();
            JavaAnalyzer.runAnalyzeFromGui(config);
            long duration = System.currentTimeMillis() - startTime;
            
            System.out.println("\u001B[32;1m✔ SUCCESS: Codebase analysis finished in " + duration + "ms.\u001B[0m");
            showAnalyzeSummary(outDir);
        } catch (Exception e) {
            System.out.println("\u001B[31;1m❌ FAILED: Analysis task failed: " + e.getMessage() + "\u001B[0m");
            e.printStackTrace();
        }
    }

    private static void runCompareFlow() {
        if (!currentConfig.isCompareEnabled()) {
            System.out.println("\u001B[31;1m⚠ Error: Compare engine is currently disabled in config. Enable in configuration menu first.\u001B[0m");
            return;
        }

        System.out.println("\n\u001B[35;1m╔══════════════════════════════════════════════════════╗\u001B[0m");
        System.out.println("\u001B[35;1m║            ACTION: COMPARE JAVA VERSIONS             ║\u001B[0m");
        System.out.println("\u001B[35;1m╚══════════════════════════════════════════════════════╝\u001B[0m");

        String oldPath = promptInput("Enter Old Version Path (folder or file)", currentConfig.getOldPath());
        if (oldPath.isEmpty()) {
            System.out.println("\u001B[31;1m⚠ Error: Old version path is required.\u001B[0m");
            return;
        }

        String newPath = promptInput("Enter New Version Path (folder or file)", currentConfig.getNewPath());
        if (newPath.isEmpty()) {
            System.out.println("\u001B[31;1m⚠ Error: New version path is required.\u001B[0m");
            return;
        }

        String outDir = promptInput("Enter Output Directory Path", currentConfig.getOutputDir());
        String threadStr = promptInput("Enter Parallel Threads", String.valueOf(currentConfig.getThreads()));
        int threads = parseThreadCount(threadStr);

        System.out.print("\u001B[33;1m▶ Confirm comparison? (y/n) [y]: \u001B[0m");
        String confirm = scanner.nextLine().trim().toLowerCase();
        if (confirm.equals("n")) {
            System.out.println("\u001B[90mOperation aborted.\u001B[0m");
            return;
        }

        System.out.println("\n\u001B[33m[SYS] Starting delta computations...\u001B[0m");
        try {
            List<String> argsList = new ArrayList<>(Arrays.asList("-m", "compare", "-o", oldPath, "-n", newPath, "--output-dir", outDir, "-t", String.valueOf(threads)));
            Config config = Config.parse(argsList.toArray(new String[0]));
            
            CompareEngine.execute(config);
            System.out.println("\u001B[32;1m✔ SUCCESS: Deltas computed successfully.\u001B[0m");
            showCompareSummary(outDir);
        } catch (Exception e) {
            System.out.println("\u001B[31;1m❌ FAILED: Comparison failed: " + e.getMessage() + "\u001B[0m");
            e.printStackTrace();
        }
    }

    private static void runMergeFlow() {
        if (!currentConfig.isMergeEnabled()) {
            System.out.println("\u001B[31;1m⚠ Error: Merge engine is currently disabled in config. Enable in configuration menu first.\u001B[0m");
            return;
        }

        System.out.println("\n\u001B[35;1m╔══════════════════════════════════════════════════════╗\u001B[0m");
        System.out.println("\u001B[35;1m║            ACTION: MARKER-GUIDED MERGE               ║\u001B[0m");
        System.out.println("\u001B[35;1m╚══════════════════════════════════════════════════════╝\u001B[0m");

        String folder1 = promptInput("Enter Input Folder 1 (Base version)", currentConfig.getOldPath());
        if (folder1.isEmpty()) {
            System.out.println("\u001B[31;1m⚠ Error: Input Folder 1 is required.\u001B[0m");
            return;
        }

        String folder2 = promptInput("Enter Input Folder 2 (With markers)", currentConfig.getNewPath());
        if (folder2.isEmpty()) {
            System.out.println("\u001B[31;1m⚠ Error: Input Folder 2 is required.\u001B[0m");
            return;
        }

        String outDir = promptInput("Enter Output Folder (Merged Result)", currentConfig.getOutputDir());
        if (outDir.isEmpty()) {
            outDir = "java_analysis_output/merged";
        }

        String startMarker = promptInput("Enter Start Marker", currentConfig.getStartMarker());
        String endMarker = promptInput("Enter End Marker", currentConfig.getEndMarker());
        String threadStr = promptInput("Enter Parallel Threads", String.valueOf(currentConfig.getThreads()));
        int threads = parseThreadCount(threadStr);

        System.out.print("\u001B[33;1m▶ Merged files will be created in: " + outDir + ". Continue? (y/n) [y]: \u001B[0m");
        String confirm = scanner.nextLine().trim().toLowerCase();
        if (confirm.equals("n")) {
            System.out.println("\u001B[90mOperation aborted.\u001B[0m");
            return;
        }

        System.out.println("\n\u001B[33m[SYS] Initiating marker scanner and merge to output folder...\u001B[0m");
        try {
            List<String> argsList = new ArrayList<>(Arrays.asList("-m", "merge", "-o", folder1, "-n", folder2, "--output-dir", outDir, "--start-marker", startMarker, "--end-marker", endMarker, "-t", String.valueOf(threads)));
            Config config = Config.parse(argsList.toArray(new String[0]));
            
            MergeEngine.execute(config);
            System.out.println("\u001B[32;1m✔ SUCCESS: Merged files written to " + outDir + ".\u001B[0m");
        } catch (Exception e) {
            System.out.println("\u001B[31;1m❌ FAILED: Merge operation failed: " + e.getMessage() + "\u001B[0m");
            e.printStackTrace();
        }
    }

    private static void runReportFlow() {
        System.out.println("\n\u001B[35;1m╔══════════════════════════════════════════════════════╗\u001B[0m");
        System.out.println("\u001B[35;1m║     ACTION: FULL PIPELINE & UNIFIED REPORT           ║\u001B[0m");
        System.out.println("\u001B[35;1m╚══════════════════════════════════════════════════════╝\u001B[0m");

        String oldPath = promptInput("Enter Old Version Path (folder or file)", currentConfig.getOldPath());
        if (oldPath.isEmpty()) {
            System.out.println("\u001B[31;1m⚠ Error: Old version path is required.\u001B[0m");
            return;
        }

        String newPath = promptInput("Enter New Version Path (folder or file)", currentConfig.getNewPath());
        if (newPath.isEmpty()) {
            System.out.println("\u001B[31;1m⚠ Error: New version path is required.\u001B[0m");
            return;
        }

        String outDir = promptInput("Enter Output Directory Path", currentConfig.getOutputDir());
        String threadStr = promptInput("Enter Parallel Threads", String.valueOf(currentConfig.getThreads()));
        int threads = parseThreadCount(threadStr);

        System.out.print("\u001B[33;1m▶ Run analyze, compare, merge & generate unified report? (y/n) [y]: \u001B[0m");
        String confirm = scanner.nextLine().trim().toLowerCase();
        if (confirm.equals("n")) {
            System.out.println("\u001B[90mOperation aborted.\u001B[0m");
            return;
        }

        System.out.println("\n\u001B[33m[SYS] Starting unified pipeline...\u001B[0m");
        try {
            List<String> argsList = new ArrayList<>(Arrays.asList("-m", "report", "-o", oldPath, "-n", newPath, "--output-dir", outDir, "-t", String.valueOf(threads)));
            Config config = Config.parse(argsList.toArray(new String[0]));
            Path reportCsv = ReportGenerator.generateFullReport(config);
            System.out.println("\u001B[32;1m✔ SUCCESS: Unified report generated at: " + reportCsv.toAbsolutePath() + "\u001B[0m");
        } catch (Exception e) {
            System.out.println("\u001B[31;1m❌ FAILED: Pipeline failed: " + e.getMessage() + "\u001B[0m");
            e.printStackTrace();
        }
    }

    private static void runConfigFlow() {
        while (true) {
            System.out.println("\n\u001B[35;1m╔══════════════════════════════════════════════════════╗\u001B[0m");
            System.out.println("\u001B[35;1m║             CONFIGURATION & .CONF SETTINGS           ║\u001B[0m");
            System.out.println("\u001B[35;1m╚══════════════════════════════════════════════════════╝\u001B[0m");
            System.out.println("  \u001B[90mActive File           :\u001B[0m \u001B[32;1m" + currentConfig.getConfigFilePath() + "\u001B[0m");
            System.out.println("  \u001B[36m[1]\u001B[0m Execution Mode       : \u001B[33m" + currentConfig.getMode().name().toLowerCase() + "\u001B[0m");
            System.out.println("  \u001B[36m[2]\u001B[0m Default Source Folder: \u001B[33m" + currentConfig.getSourceFolder() + "\u001B[0m");
            System.out.println("  \u001B[36m[3]\u001B[0m Baseline Old Path    : \u001B[33m" + currentConfig.getOldPath() + "\u001B[0m");
            System.out.println("  \u001B[36m[4]\u001B[0m Feature New Path     : \u001B[33m" + currentConfig.getNewPath() + "\u001B[0m");
            System.out.println("  \u001B[36m[5]\u001B[0m Output Directory     : \u001B[33m" + currentConfig.getOutputDir() + "\u001B[0m");
            System.out.println("  \u001B[36m[6]\u001B[0m Parallel Threads     : \u001B[33m" + currentConfig.getThreads() + "\u001B[0m");
            System.out.println("  \u001B[36m[7]\u001B[0m Web Server Port      : \u001B[33m" + currentConfig.getServerPort() + "\u001B[0m");
            System.out.println("  \u001B[36m[8]\u001B[0m Merge Start Marker   : \u001B[33m" + currentConfig.getStartMarker() + "\u001B[0m");
            System.out.println("  \u001B[36m[9]\u001B[0m Merge End Marker     : \u001B[33m" + currentConfig.getEndMarker() + "\u001B[0m");
            System.out.println("  \u001B[36m[10]\u001B[0m Compare Enabled     : \u001B[33m" + currentConfig.isCompareEnabled() + "\u001B[0m");
            System.out.println("  \u001B[36m[11]\u001B[0m Merge Enabled       : \u001B[33m" + currentConfig.isMergeEnabled() + "\u001B[0m");
            System.out.println("  ──────────────────────────────────────────────────────");
            System.out.println("  \u001B[32;1m[S]\u001B[0m SAVE configuration to active .conf file");
            System.out.println("  \u001B[32;1m[W]\u001B[0m WRITE configuration to custom .conf file path");
            System.out.println("  \u001B[34;1m[L]\u001B[0m LOAD configuration from .conf file path");
            System.out.println("  \u001B[31m[B]\u001B[0m BACK TO MAIN MENU");
            System.out.print("\u001B[35;1m⚡ Select option [1-11, S, W, L, B]: \u001B[0m");

            String choice = scanner.nextLine().trim().toUpperCase();
            if (choice.equals("B") || choice.isEmpty()) {
                return;
            }

            switch (choice) {
                case "1":
                    System.out.print("Enter mode (analyze, compare, merge, report, server, interactive): ");
                    String m = scanner.nextLine().trim().toUpperCase();
                    try { currentConfig.setMode(Config.Mode.valueOf(m)); } catch (Exception e) { System.out.println("\u001B[31mInvalid mode.\u001B[0m"); }
                    break;
                case "2":
                    System.out.print("Enter default source folder: ");
                    currentConfig.setSourceFolder(scanner.nextLine().trim());
                    break;
                case "3":
                    System.out.print("Enter baseline old path: ");
                    currentConfig.setOldPath(scanner.nextLine().trim());
                    break;
                case "4":
                    System.out.print("Enter feature new path: ");
                    currentConfig.setNewPath(scanner.nextLine().trim());
                    break;
                case "5":
                    System.out.print("Enter output directory: ");
                    currentConfig.setOutputDir(scanner.nextLine().trim());
                    break;
                case "6":
                    System.out.print("Enter thread pool size: ");
                    try { currentConfig.setThreads(Integer.parseInt(scanner.nextLine().trim())); } catch (Exception ignored) {}
                    break;
                case "7":
                    System.out.print("Enter web server port: ");
                    try { currentConfig.setServerPort(Integer.parseInt(scanner.nextLine().trim())); } catch (Exception ignored) {}
                    break;
                case "8":
                    System.out.print("Enter merge start marker: ");
                    currentConfig.setStartMarker(scanner.nextLine().trim());
                    break;
                case "9":
                    System.out.print("Enter merge end marker: ");
                    currentConfig.setEndMarker(scanner.nextLine().trim());
                    break;
                case "10":
                    System.out.print("Enable compare engine? (true/false): ");
                    currentConfig.setCompareEnabled(Boolean.parseBoolean(scanner.nextLine().trim()));
                    break;
                case "11":
                    System.out.print("Enable merge engine? (true/false): ");
                    currentConfig.setMergeEnabled(Boolean.parseBoolean(scanner.nextLine().trim()));
                    break;
                case "S":
                    currentConfig.save();
                    System.out.println("\u001B[32;1m✔ Configuration successfully saved to: " + currentConfig.getConfigFilePath() + "\u001B[0m");
                    break;
                case "W":
                    System.out.print("Enter target .conf file path on local machine: ");
                    String customPath = scanner.nextLine().trim();
                    if (!customPath.isEmpty()) {
                        currentConfig.saveProperties(customPath);
                        System.out.println("\u001B[32;1m✔ Configuration written to: " + customPath + "\u001B[0m");
                    }
                    break;
                case "L":
                    System.out.print("Enter .conf file path to load: ");
                    String loadPath = scanner.nextLine().trim();
                    if (!loadPath.isEmpty()) {
                        File lf = new File(loadPath);
                        if (lf.exists()) {
                            currentConfig.loadProperties(loadPath);
                            System.out.println("\u001B[32;1m✔ Configuration reloaded from: " + loadPath + "\u001B[0m");
                        } else {
                            System.out.println("\u001B[31;1m⚠ File does not exist: " + loadPath + "\u001B[0m");
                        }
                    }
                    break;
                default:
                    System.out.println("\u001B[31;1m⚠ Invalid option.\u001B[0m");
            }
        }
    }

    private static void showAnalyzeSummary(String outDir) {
        Path attrFile = Paths.get(outDir).resolve("java_attributes.csv");
        Path methFile = Paths.get(outDir).resolve("java_methods.csv");

        int attrCount = Math.max(0, countLines(attrFile) - 1);
        int methCount = Math.max(0, countLines(methFile) - 1);

        System.out.println("\n\u001B[36;1m┌────────────────────────────────────────────────────────┐\u001B[0m");
        System.out.println("\u001B[36;1m│                 ANALYSIS REPORT SUMMARY                │\u001B[0m");
        System.out.println("\u001B[36;1m├────────────────────────────────────────────────────────┤\u001B[0m");
        System.out.printf("\u001B[36;1m│\u001B[0m Output Folder:  %-38s \u001B[36;1m│\u001B[0m\n", truncatePath(outDir, 38));
        System.out.printf("\u001B[36;1m│\u001B[0m Attributes:     %-38s \u001B[36;1m│\u001B[0m\n", attrCount + " fields");
        System.out.printf("\u001B[36;1m│\u001B[0m Methods:        %-38s \u001B[36;1m│\u001B[0m\n", methCount + " methods");
        System.out.println("\u001B[36;1m└────────────────────────────────────────────────────────┘\u001B[0m");

        System.out.print("\u001B[33;1m▶ Print tabular reports inside CLI? (y/n) [n]: \u001B[0m");
        String ans = scanner.nextLine().trim().toLowerCase();
        if (ans.equals("y")) {
            System.out.println("\n\u001B[35;1m--- ATTRIBUTES LOG (MAX 15) ---\u001B[0m");
            printCsvTable(attrFile, new int[]{25, 15, 20, 20}, 15);

            System.out.println("\n\u001B[35;1m--- METHODS LOG (MAX 15) ---\u001B[0m");
            printCsvTable(methFile, new int[]{25, 15, 20, 20}, 15);
        }
    }

    private static void showCompareSummary(String outDir) {
        Path attrDiffFile = Paths.get(outDir).resolve("comparison_attributes.csv");
        Path methDiffFile = Paths.get(outDir).resolve("comparison_methods.csv");

        int attrChanges = Math.max(0, countLines(attrDiffFile) - 1);
        int methChanges = Math.max(0, countLines(methDiffFile) - 1);

        System.out.println("\n\u001B[36;1m┌────────────────────────────────────────────────────────┐\u001B[0m");
        System.out.println("\u001B[36;1m│                COMPARISON DELTA SUMMARY                │\u001B[0m");
        System.out.println("\u001B[36;1m├────────────────────────────────────────────────────────┤\u001B[0m");
        System.out.printf("\u001B[36;1m│\u001B[0m Output Folder:  %-38s \u001B[36;1m│\u001B[0m\n", truncatePath(outDir, 38));
        System.out.printf("\u001B[36;1m│\u001B[0m Fields Delta:   %-38s \u001B[36;1m│\u001B[0m\n", attrChanges + " modifications");
        System.out.printf("\u001B[36;1m│\u001B[0m Methods Delta:  %-38s \u001B[36;1m│\u001B[0m\n", methChanges + " modifications");
        System.out.println("\u001B[36;1m└────────────────────────────────────────────────────────┘\u001B[0m");

        System.out.print("\u001B[33;1m▶ Print comparison delta table inside CLI? (y/n) [n]: \u001B[0m");
        String ans = scanner.nextLine().trim().toLowerCase();
        if (ans.equals("y")) {
            System.out.println("\n\u001B[35;1m--- ATTRIBUTES DELTA LOG ---\u001B[0m");
            printCsvTable(attrDiffFile, new int[]{10, 20, 25, 15, 15}, 30);

            System.out.println("\n\u001B[35;1m--- METHODS DELTA LOG ---\u001B[0m");
            printCsvTable(methDiffFile, new int[]{10, 20, 25, 15, 15}, 30);
        }
    }

    private static String promptInput(String label, String defaultValue) {
        System.out.print("\u001B[36;1m▶ " + label + "\u001B[0m" + 
            (defaultValue.isEmpty() ? "" : " \u001B[90m(" + defaultValue + ")\u001B[0m") + ": ");
        String input = scanner.nextLine().trim();
        return input.isEmpty() ? defaultValue : input;
    }

    private static int parseThreadCount(String threadStr) {
        try {
            return Integer.parseInt(threadStr);
        } catch (NumberFormatException e) {
            return Runtime.getRuntime().availableProcessors();
        }
    }

    private static int countLines(Path path) {
        if (!Files.exists(path)) return 0;
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            int lines = 0;
            while (reader.readLine() != null) lines++;
            return lines;
        } catch (IOException e) {
            return 0;
        }
    }

    private static String truncatePath(String path, int length) {
        if (path == null) return "";
        if (path.length() <= length) return path;
        return "..." + path.substring(path.length() - length + 3);
    }

    private static void printCsvTable(Path path, int[] colWidths, int limit) {
        if (!Files.exists(path)) {
            System.out.println("\u001B[31mFile not found: " + path.getFileName() + "\u001B[0m");
            return;
        }

        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            int printed = 0;
            
            // Print top outline
            printHorizontalLine(colWidths, true);
            
            // Print header row
            if ((line = br.readLine()) != null) {
                printHeaderRow(parseCsvLine(line), colWidths);
                printHorizontalLine(colWidths, false); // cross lines separator
            }

            while ((line = br.readLine()) != null) {
                List<String> cols = parseCsvLine(line);
                printRow(cols, colWidths);
                printed++;
                if (printed >= limit) {
                    printHorizontalLine(colWidths, false);
                    System.out.println("  \u001B[90m... truncated (showing top " + limit + " items) ...\u001B[0m");
                    break;
                }
            }
            
            if (printed < limit) {
                printHorizontalLine(colWidths, false); // Print bottom closing outline if not truncated already
            }
        } catch (IOException e) {
            System.out.println("\u001B[31mFailed to read report CSV: " + e.getMessage() + "\u001B[0m");
        }
    }

    private static void printHorizontalLine(int[] widths, boolean isTop) {
        StringBuilder sb = new StringBuilder();
        if (isTop) {
            sb.append("┌");
            for (int i = 0; i < widths.length; i++) {
                for (int w = 0; w < widths[i] + 2; w++) sb.append("─");
                if (i < widths.length - 1) sb.append("┬");
            }
            sb.append("┐");
        } else {
            sb.append("├");
            for (int i = 0; i < widths.length; i++) {
                for (int w = 0; w < widths[i] + 2; w++) sb.append("─");
                if (i < widths.length - 1) sb.append("┼");
            }
            sb.append("┤");
        }
        System.out.println(sb.toString());
    }

    private static void printHeaderRow(List<String> columns, int[] widths) {
        StringBuilder sb = new StringBuilder("│ ");
        for (int i = 0; i < widths.length; i++) {
            String col = (i < columns.size()) ? columns.get(i) : "";
            int w = widths[i];
            if (col.length() > w) {
                col = col.substring(0, w - 3) + "...";
            }
            // Magenta headers
            sb.append("\u001B[35;1m").append(String.format("%-" + w + "s", col)).append("\u001B[0m").append(" │ ");
        }
        System.out.println(sb.toString());
    }

    private static void printRow(List<String> columns, int[] widths) {
        StringBuilder sb = new StringBuilder("│ ");
        for (int i = 0; i < widths.length; i++) {
            String col = (i < columns.size()) ? columns.get(i) : "";
            int w = widths[i];
            
            // Text color highlighting based on status
            String prefix = "";
            String suffix = "";
            if (col.equals("ADDED")) {
                prefix = "\u001B[32;1m"; // bold green
                suffix = "\u001B[0m";
            } else if (col.equals("REMOVED")) {
                prefix = "\u001B[31;1m"; // bold red
                suffix = "\u001B[0m";
            } else if (col.equals("MODIFIED")) {
                prefix = "\u001B[33;1m"; // bold yellow
                suffix = "\u001B[0m";
            } else if (col.equals("UNCHANGED")) {
                prefix = "\u001B[90m"; // dim grey
                suffix = "\u001B[0m";
            }

            if (col.length() > w) {
                col = col.substring(0, w - 3) + "...";
            }
            sb.append(prefix).append(String.format("%-" + w + "s", col)).append(suffix).append(" │ ");
        }
        System.out.println(sb.toString());
    }

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder curVal = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(curVal.toString().trim());
                curVal.setLength(0);
            } else {
                curVal.append(c);
            }
        }
        result.add(curVal.toString().trim());
        return result;
    }
}
