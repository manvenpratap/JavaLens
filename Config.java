import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class Config {
    public enum Mode {
        ANALYZE, COMPARE, MERGE, INTERACTIVE, SERVER
    }

    private Mode mode = Mode.ANALYZE;
    private String oldPath = "";
    private String newPath = "";
    private String startMarker = "// START_MERGE";
    private String endMarker = "// END_MERGE";
    private String outputDir = "java_analysis_output";
    private String activeRunFolder = "";
    private int threads = Runtime.getRuntime().availableProcessors();
    private boolean compareEnabled = true;
    private boolean mergeEnabled = true;

    // Legacy fields
    private String sourceFolder = "";

    public static Config parse(String[] args) {
        Config config = new Config();
        config.loadProperties("analyzer.properties");

        if (args.length == 0) {
            config.mode = Mode.INTERACTIVE;
            return config;
        }

        // Parse CLI args
        int i = 0;
        boolean hasFlags = false;
        while (i < args.length) {
            String arg = args[i];
            if (arg.startsWith("-")) {
                hasFlags = true;
                switch (arg) {
                    case "-m":
                    case "--mode":
                        if (i + 1 < args.length) {
                            String modeStr = args[++i].toUpperCase();
                            try {
                                config.mode = Mode.valueOf(modeStr);
                            } catch (IllegalArgumentException e) {
                                System.err.println("Invalid mode: " + modeStr + ". Expected analyze, compare, merge, or interactive.");
                                System.exit(1);
                            }
                        }
                        break;
                    case "-i":
                    case "--interactive":
                        config.mode = Mode.INTERACTIVE;
                        break;
                    case "-o":
                    case "--old":
                        if (i + 1 < args.length) config.oldPath = args[++i];
                        break;
                    case "-n":
                    case "--new":
                        if (i + 1 < args.length) config.newPath = args[++i];
                        break;
                    case "-s":
                    case "--source":
                        if (i + 1 < args.length) config.sourceFolder = args[++i];
                        break;
                    case "--start-marker":
                        if (i + 1 < args.length) config.startMarker = args[++i];
                        break;
                    case "--end-marker":
                        if (i + 1 < args.length) config.endMarker = args[++i];
                        break;
                    case "--output-dir":
                        if (i + 1 < args.length) config.outputDir = args[++i];
                        break;
                    case "-t":
                    case "--threads":
                        if (i + 1 < args.length) config.threads = Integer.parseInt(args[++i]);
                        break;
                    case "-c":
                    case "--config":
                        if (i + 1 < args.length) config.loadProperties(args[++i]);
                        break;
                    case "-h":
                    case "--help":
                        printHelp();
                        System.exit(0);
                    default:
                        System.err.println("Unknown option: " + arg);
                        printHelp();
                        System.exit(1);
                }
            } else {
                // If positional args are used, check if we had flags already
                if (hasFlags) {
                    System.err.println("Error: Mix of flags and legacy positional arguments is not supported.");
                    printHelp();
                    System.exit(1);
                }
                // Legacy support logic:
                // java JavaAnalyzer <source-folder> [output-dir] [threads]
                config.mode = Mode.ANALYZE;
                config.sourceFolder = args[0];
                if (args.length >= 2) {
                    config.outputDir = args[1];
                }
                if (args.length >= 3) {
                    try {
                        config.threads = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid thread count: " + args[2]);
                        System.exit(1);
                    }
                }
                break; // Handled legacy syntax, finish loop
            }
            i++;
        }

        // Validate toggles
        if (config.mode == Mode.COMPARE && !config.compareEnabled) {
            System.err.println("Error: Compare enhancement is currently disabled in configuration/properties.");
            System.exit(1);
        }
        if (config.mode == Mode.MERGE && !config.mergeEnabled) {
            System.err.println("Error: Merge enhancement is currently disabled in configuration/properties.");
            System.exit(1);
        }

        // Validate required paths for non-legacy
        if (hasFlags) {
            if (config.mode == Mode.ANALYZE && config.sourceFolder.isEmpty()) {
                System.err.println("Error: Source folder (-s / --source) is required in analyze mode.");
                System.exit(1);
            }
            if ((config.mode == Mode.COMPARE || config.mode == Mode.MERGE) && 
                (config.oldPath.isEmpty() || config.newPath.isEmpty())) {
                System.err.println("Error: Both --old (-o) and --new (-n) paths are required in " + config.mode.name().toLowerCase() + " mode.");
                System.exit(1);
            }
        } else if (config.mode != Mode.INTERACTIVE && config.sourceFolder.isEmpty()) {
            System.err.println("Error: Missing source folder.");
            printHelp();
            System.exit(1);
        }

        return config;
    }

    public void loadProperties(String path) {
        if (!Files.exists(Paths.get(path))) {
            return;
        }
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(path)) {
            props.load(fis);
            compareEnabled = Boolean.parseBoolean(props.getProperty("enhancement.compare.enabled", "true"));
            mergeEnabled = Boolean.parseBoolean(props.getProperty("enhancement.merge.enabled", "true"));
            startMarker = props.getProperty("merge.start_marker", startMarker);
            endMarker = props.getProperty("merge.end_marker", endMarker);
            outputDir = props.getProperty("compare.output_dir", outputDir);
            activeRunFolder = props.getProperty("active.run_folder", "");
            if (!activeRunFolder.isEmpty()) {
                outputDir = activeRunFolder;
            }
            if (props.containsKey("threads")) {
                threads = Integer.parseInt(props.getProperty("threads"));
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Warning: Failed to load properties from " + path + ": " + e.getMessage());
        }
    }

    public static void updateActiveRunFolder(String path) {
        Properties props = new Properties();
        File propFile = new File("analyzer.properties");
        if (propFile.exists()) {
            try (FileInputStream fis = new FileInputStream(propFile)) {
                props.load(fis);
            } catch (IOException e) { /* ignore */ }
        }
        props.setProperty("active.run_folder", path);
        try (FileOutputStream fos = new FileOutputStream(propFile)) {
            props.store(fos, "Updated active run folder context");
        } catch (IOException e) {
            System.err.println("Warning: Failed to save active run folder to analyzer.properties: " + e.getMessage());
        }
    }

    private static void printHelp() {
        System.out.println("JavaLens AST Tools");
        System.out.println("Usage (Legacy):");
        System.out.println("  java -jar javalens.jar <source-folder> [output-dir] [threads]");
        System.out.println();
        System.out.println("Usage (Enhanced CLI):");
        System.out.println("  java -jar javalens.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -m, --mode <analyze|compare|merge|interactive>   Execution mode (default: analyze)");
        System.out.println("  -i, --interactive                                Start interactive console wizard");
        System.out.println("  -s, --source <path>                             Source folder for analysis (analyze mode)");
        System.out.println("  -o, --old <path>                    Path to old version of file/directory (compare/merge modes)");
        System.out.println("  -n, --new <path>                    Path to new version of file/directory (compare/merge modes)");
        System.out.println("  --start-marker <string>             Start marker for merging (default: // START_MERGE)");
        System.out.println("  --end-marker <string>               End marker for merging (default: // END_MERGE)");
        System.out.println("  --output-dir <path>                 Directory for output report files (default: java_analysis_output)");
        System.out.println("  -t, --threads <num>                 Number of parallel threads (default: available cores)");
        System.out.println("  -c, --config <path>                 Path to properties config file (default: analyzer.properties)");
        System.out.println("  -h, --help                          Show this help message");
    }

    // Getters
    public Mode getMode() { return mode; }
    public String getOldPath() { return oldPath; }
    public String getNewPath() { return newPath; }
    public String getStartMarker() { return startMarker; }
    public String getEndMarker() { return endMarker; }
    public String getOutputDir() { return outputDir; }
    public int getThreads() { return threads; }
    public String getSourceFolder() { return sourceFolder; }
    public String getActiveRunFolder() { return activeRunFolder; }
    public boolean isCompareEnabled() { return compareEnabled; }
    public boolean isMergeEnabled() { return mergeEnabled; }

    // Setters
    public void setMode(Mode mode) { this.mode = mode; }
    public void setOldPath(String oldPath) { this.oldPath = oldPath; }
    public void setNewPath(String newPath) { this.newPath = newPath; }
    public void setStartMarker(String startMarker) { this.startMarker = startMarker; }
    public void setEndMarker(String endMarker) { this.endMarker = endMarker; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }
    public void setThreads(int threads) { this.threads = threads; }
    public void setSourceFolder(String sourceFolder) { this.sourceFolder = sourceFolder; }
    public void setActiveRunFolder(String activeRunFolder) { this.activeRunFolder = activeRunFolder; }

    public void saveProperties(String path) {
        Properties props = new Properties();
        File propFile = new File(path);
        if (propFile.exists()) {
            try (FileInputStream fis = new FileInputStream(propFile)) {
                props.load(fis);
            } catch (IOException e) { /* ignore */ }
        }
        props.setProperty("enhancement.compare.enabled", String.valueOf(compareEnabled));
        props.setProperty("enhancement.merge.enabled", String.valueOf(mergeEnabled));
        props.setProperty("merge.start_marker", startMarker);
        props.setProperty("merge.end_marker", endMarker);
        props.setProperty("compare.output_dir", outputDir);
        props.setProperty("active.run_folder", activeRunFolder);
        props.setProperty("source.folder", sourceFolder);
        props.setProperty("threads", String.valueOf(threads));

        try (FileOutputStream fos = new FileOutputStream(propFile)) {
            props.store(fos, "Saved configuration from JavaLens web API");
        } catch (IOException e) {
            System.err.println("Warning: Failed to save properties: " + e.getMessage());
        }
    }
}
