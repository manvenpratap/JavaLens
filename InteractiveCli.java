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
            System.out.println("  \u001B[33;1m[4]\u001B[0m \u001B[1mCONFIG\u001B[0m    - View and edit properties configurations");
            System.out.println("  \u001B[31;1m[5]\u001B[0m \u001B[1mEXIT\u001B[0m      - Terminate tool");
            System.out.print("\u001B[35;1m⚡ Select action [1-5]: \u001B[0m");

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
                    runConfigFlow();
                    break;
                case "5":
                    System.out.println("\n\u001B[32;1m✔ Exiting JavaLens. Goodbye!\u001B[0m");
                    return;
                default:
                    System.out.println("\u001B[31;1m⚠ Invalid selection. Please choose an option from 1 to 5.\u001B[0m");
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

        String destPath = promptInput("Enter Destination Path (Old version to modify)", currentConfig.getOldPath());
        if (destPath.isEmpty()) {
            System.out.println("\u001B[31;1m⚠ Error: Destination path is required.\u001B[0m");
            return;
        }

        String srcPath = promptInput("Enter Source Path (New version with markers)", currentConfig.getNewPath());
        if (srcPath.isEmpty()) {
            System.out.println("\u001B[31;1m⚠ Error: Source path is required.\u001B[0m");
            return;
        }

        String startMarker = promptInput("Enter Start Marker", currentConfig.getStartMarker());
        String endMarker = promptInput("Enter End Marker", currentConfig.getEndMarker());
        String threadStr = promptInput("Enter Parallel Threads", String.valueOf(currentConfig.getThreads()));
        int threads = parseThreadCount(threadStr);

        System.out.print("\u001B[31;1m⚠ WARNING: This will overwrite code inside marked blocks in destination files. Continue? (y/n) [y]: \u001B[0m");
        String confirm = scanner.nextLine().trim().toLowerCase();
        if (confirm.equals("n")) {
            System.out.println("\u001B[90mOperation aborted.\u001B[0m");
            return;
        }

        System.out.println("\n\u001B[33m[SYS] Initiating marker scanner and inline merge...\u001B[0m");
        try {
            List<String> argsList = new ArrayList<>(Arrays.asList("-m", "merge", "-o", destPath, "-n", srcPath, "--start-marker", startMarker, "--end-marker", endMarker, "-t", String.valueOf(threads)));
            Config config = Config.parse(argsList.toArray(new String[0]));
            
            MergeEngine.execute(config);
            System.out.println("\u001B[32;1m✔ SUCCESS: Marked source blocks successfully merged.\u001B[0m");
        } catch (Exception e) {
            System.out.println("\u001B[31;1m❌ FAILED: Merge operation failed: " + e.getMessage() + "\u001B[0m");
            e.printStackTrace();
        }
    }

    private static void runConfigFlow() {
        System.out.println("\n\u001B[35;1m╔══════════════════════════════════════════════════════╗\u001B[0m");
        System.out.println("\u001B[35;1m║             EDIT PROPERTIES CONFIGURATION            ║\u001B[0m");
        System.out.println("\u001B[35;1m╚══════════════════════════════════════════════════════╝\u001B[0m");
        System.out.println("  \u001B[36m[1]\u001B[0m Compare Enhancement  : \u001B[33m" + currentConfig.isCompareEnabled() + "\u001B[0m");
        System.out.println("  \u001B[36m[2]\u001B[0m Merge Enhancement    : \u001B[33m" + currentConfig.isMergeEnabled() + "\u001B[0m");
        System.out.println("  \u001B[36m[3]\u001B[0m Merge Start Marker   : \u001B[33m" + currentConfig.getStartMarker() + "\u001B[0m");
        System.out.println("  \u001B[36m[4]\u001B[0m Merge End Marker     : \u001B[33m" + currentConfig.getEndMarker() + "\u001B[0m");
        System.out.println("  \u001B[36m[5]\u001B[0m Default Output Dir   : \u001B[33m" + currentConfig.getOutputDir() + "\u001B[0m");
        System.out.println("  \u001B[36m[6]\u001B[0m Default Threads      : \u001B[33m" + currentConfig.getThreads() + "\u001B[0m");
        System.out.println("  \u001B[31m[7]\u001B[0m BACK TO MAIN MENU");
        System.out.print("\u001B[35;1m⚡ Select config item [1-7]: \u001B[0m");

        String choice = scanner.nextLine().trim();
        if (choice.equals("7") || choice.isEmpty()) {
            return;
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("analyzer.properties")) {
            props.load(fis);
        } catch (IOException e) { /* ignore */ }

        switch (choice) {
            case "1":
                System.out.print("Enable compare engine? (true/false): ");
                props.setProperty("enhancement.compare.enabled", scanner.nextLine().trim());
                break;
            case "2":
                System.out.print("Enable merge engine? (true/false): ");
                props.setProperty("enhancement.merge.enabled", scanner.nextLine().trim());
                break;
            case "3":
                System.out.print("Enter Start Marker: ");
                props.setProperty("merge.start_marker", scanner.nextLine().trim());
                break;
            case "4":
                System.out.print("Enter End Marker: ");
                props.setProperty("merge.end_marker", scanner.nextLine().trim());
                break;
            case "5":
                System.out.print("Enter Default Output Dir: ");
                props.setProperty("compare.output_dir", scanner.nextLine().trim());
                break;
            case "6":
                System.out.print("Enter Default Threads: ");
                props.setProperty("threads", scanner.nextLine().trim());
                break;
            default:
                System.out.println("\u001B[31;1m⚠ Invalid option.\u001B[0m");
                return;
        }

        try (FileOutputStream fos = new FileOutputStream("analyzer.properties")) {
            props.store(fos, "Updated via terminal interactive config menu");
            System.out.println("\u001B[32;1m✔ Config saved to 'analyzer.properties'. Reloading...\u001B[0m");
            currentConfig = Config.parse(new String[0]); // Reload
        } catch (IOException e) {
            System.out.println("\u001B[31;1m❌ Failed to save config: " + e.getMessage() + "\u001B[0m");
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
