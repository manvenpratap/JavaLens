import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Properties;

public class Config {
    public static final String DEFAULT_CONF_FILE = "javalens.conf";
    public static final String LEGACY_PROPERTIES_FILE = "analyzer.properties";

    public enum Mode {
        ANALYZE, COMPARE, MERGE, INTERACTIVE, SERVER, REPORT
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
    private String sourceFolder = "";
    private int serverPort = 8080;
    private String configFilePath = DEFAULT_CONF_FILE;

    /**
     * Resolves the configuration file location following priority:
     * 1. Explicitly supplied path
     * 2. JAVALENS_CONF environment variable
     * 3. Current working directory javalens.conf
     * 4. Current working directory analyzer.properties
     * 5. User home ~/.javalens.conf
     * 6. Default fallback: javalens.conf
     */
    public static String resolveConfigPath(String customPath) {
        if (customPath != null && !customPath.trim().isEmpty()) {
            return customPath.trim();
        }
        String envPath = System.getenv("JAVALENS_CONF");
        if (envPath != null && !envPath.trim().isEmpty()) {
            Path p = Paths.get(envPath.trim());
            if (Files.exists(p)) {
                return envPath.trim();
            }
        }
        if (Files.exists(Paths.get(DEFAULT_CONF_FILE))) {
            return DEFAULT_CONF_FILE;
        }
        if (Files.exists(Paths.get(LEGACY_PROPERTIES_FILE))) {
            return LEGACY_PROPERTIES_FILE;
        }
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            Path homeConf = Paths.get(userHome, ".javalens.conf");
            if (Files.exists(homeConf)) {
                return homeConf.toString();
            }
        }
        return DEFAULT_CONF_FILE;
    }

    public static Config parse(String[] args) {
        Config config = new Config();

        // 1. Scan for -c or --config first to load base configuration
        String explicitConfig = null;
        for (int j = 0; j < args.length; j++) {
            if (("-c".equals(args[j]) || "--config".equals(args[j])) && j + 1 < args.length) {
                explicitConfig = args[j + 1];
                break;
            }
        }
        String resolvedPath = resolveConfigPath(explicitConfig);
        config.loadProperties(resolvedPath);

        if (args.length == 0) {
            config.mode = Mode.INTERACTIVE;
            return config;
        }

        // 2. Parse CLI args overrides
        int i = 0;
        boolean hasFlags = false;
        String saveConfigTarget = null;
        boolean saveAndExit = false;

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
                                System.err.println("Invalid mode: " + modeStr + ". Expected analyze, compare, merge, interactive, server, or report.");
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
                        if (i + 1 < args.length) {
                            try {
                                config.threads = Integer.parseInt(args[++i]);
                            } catch (NumberFormatException e) {
                                System.err.println("Invalid thread count: " + args[i]);
                                System.exit(1);
                            }
                        }
                        break;
                    case "-p":
                    case "--port":
                        if (i + 1 < args.length) {
                            try {
                                config.serverPort = Integer.parseInt(args[++i]);
                            } catch (NumberFormatException e) {
                                System.err.println("Invalid port: " + args[i]);
                                System.exit(1);
                            }
                        }
                        break;
                    case "-c":
                    case "--config":
                        // already scanned above, skip next arg
                        if (i + 1 < args.length) i++;
                        break;
                    case "--save-config":
                        if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                            saveConfigTarget = args[++i];
                        } else {
                            saveConfigTarget = config.configFilePath;
                        }
                        saveAndExit = true;
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

        if (saveAndExit) {
            config.saveProperties(saveConfigTarget);
            System.out.println("JavaLens configuration written to: " + saveConfigTarget);
            System.exit(0);
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
            if ((config.mode == Mode.COMPARE || config.mode == Mode.MERGE || config.mode == Mode.REPORT) && 
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

    public void load() {
        loadProperties(resolveConfigPath(this.configFilePath));
    }

    public void save() {
        saveProperties(this.configFilePath);
    }

    public void loadProperties(String path) {
        if (path == null || path.trim().isEmpty()) {
            path = resolveConfigPath(null);
        }
        this.configFilePath = path;
        Properties props = new Properties();
        boolean loaded = false;

        Path filePath = Paths.get(path);
        if (Files.exists(filePath)) {
            try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                props.load(fis);
                loaded = true;
            } catch (IOException e) {
                System.err.println("Warning: Failed to load config from " + path + ": " + e.getMessage());
            }
        } else {
            // Check classpath fallbacks
            String[] resourcePaths = {
                path.startsWith("/") ? path : "/" + path,
                "/" + DEFAULT_CONF_FILE,
                "/" + LEGACY_PROPERTIES_FILE
            };
            for (String res : resourcePaths) {
                try (InputStream is = Config.class.getResourceAsStream(res)) {
                    if (is != null) {
                        props.load(is);
                        loaded = true;
                        break;
                    }
                } catch (IOException ignored) {}
            }
        }

        if (loaded) {
            // Mode
            String modeVal = props.getProperty("mode");
            if (modeVal != null && !modeVal.trim().isEmpty()) {
                try {
                    this.mode = Mode.valueOf(modeVal.trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }

            // Source & Paths
            this.sourceFolder = getProp(props, sourceFolder, "source.folder", "sourceFolder");
            this.oldPath = getProp(props, oldPath, "old.path", "oldPath");
            this.newPath = getProp(props, newPath, "new.path", "newPath");
            this.outputDir = getProp(props, outputDir, "output.dir", "compare.output_dir", "outputDir");
            this.activeRunFolder = getProp(props, activeRunFolder, "active.run_folder", "activeRunFolder");
            if (!this.activeRunFolder.isEmpty()) {
                this.outputDir = this.activeRunFolder;
            }

            // Merge markers
            this.startMarker = getProp(props, startMarker, "merge.start_marker", "startMarker");
            this.endMarker = getProp(props, endMarker, "merge.end_marker", "endMarker");

            // Toggles
            if (props.containsKey("enhancement.compare.enabled") || props.containsKey("compareEnabled")) {
                String val = props.getProperty("enhancement.compare.enabled", props.getProperty("compareEnabled", "true"));
                this.compareEnabled = Boolean.parseBoolean(val.trim());
            }
            if (props.containsKey("enhancement.merge.enabled") || props.containsKey("mergeEnabled")) {
                String val = props.getProperty("enhancement.merge.enabled", props.getProperty("mergeEnabled", "true"));
                this.mergeEnabled = Boolean.parseBoolean(val.trim());
            }

            // Numeric settings
            if (props.containsKey("threads")) {
                try {
                    this.threads = Integer.parseInt(props.getProperty("threads").trim());
                } catch (NumberFormatException ignored) {}
            }
            if (props.containsKey("server.port") || props.containsKey("port") || props.containsKey("serverPort")) {
                try {
                    String pVal = props.getProperty("server.port", props.getProperty("port", props.getProperty("serverPort", "8080")));
                    this.serverPort = Integer.parseInt(pVal.trim());
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private static String getProp(Properties props, String defaultVal, String... keys) {
        for (String key : keys) {
            if (props.containsKey(key)) {
                String val = props.getProperty(key);
                if (val != null) return val.trim();
            }
        }
        return defaultVal;
    }

    public void saveProperties(String path) {
        if (path == null || path.trim().isEmpty()) {
            path = (configFilePath != null && !configFilePath.trim().isEmpty()) ? configFilePath.trim() : DEFAULT_CONF_FILE;
        }
        this.configFilePath = path;
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# ===================================================================\n");
        sb.append("# JavaLens Configuration File (").append(file.getName()).append(")\n");
        sb.append("# Precision AST Static Analysis, Code Comparator & Merge Telemetry\n");
        sb.append("# Saved: ").append(new Date()).append("\n");
        sb.append("# ===================================================================\n\n");

        sb.append("# Execution Mode: analyze, compare, merge, report, server, interactive\n");
        sb.append("mode=").append(mode.name().toLowerCase()).append("\n\n");

        sb.append("# Default Analysis Source Path (file or directory)\n");
        sb.append("source.folder=").append(sourceFolder).append("\n\n");

        sb.append("# Baseline Old Version Path (used in compare, merge, and report modes)\n");
        sb.append("old.path=").append(oldPath).append("\n\n");

        sb.append("# Feature New Version Path (used in compare, merge, and report modes)\n");
        sb.append("new.path=").append(newPath).append("\n\n");

        sb.append("# Output Directory for Generated Reports, CSVs, and Telemetry Data\n");
        sb.append("output.dir=").append(outputDir).append("\n\n");

        sb.append("# Active Run Output Folder Context\n");
        sb.append("active.run_folder=").append(activeRunFolder).append("\n\n");

        sb.append("# Parallel Worker Thread Pool Size\n");
        sb.append("threads=").append(threads).append("\n\n");

        sb.append("# Marker-Guided Merge Boundaries\n");
        sb.append("merge.start_marker=").append(startMarker).append("\n");
        sb.append("merge.end_marker=").append(endMarker).append("\n\n");

        sb.append("# Feature Engine Toggles\n");
        sb.append("enhancement.compare.enabled=").append(compareEnabled).append("\n");
        sb.append("enhancement.merge.enabled=").append(mergeEnabled).append("\n\n");

        sb.append("# Web GUI Server Port\n");
        sb.append("server.port=").append(serverPort).append("\n");

        try {
            Files.writeString(file.toPath(), sb.toString());
        } catch (IOException e) {
            System.err.println("Warning: Failed to save config to " + path + ": " + e.getMessage());
        }

        // Also update legacy analyzer.properties if it exists alongside javalens.conf to maintain backward compatibility
        if (!path.equals(LEGACY_PROPERTIES_FILE) && Files.exists(Paths.get(LEGACY_PROPERTIES_FILE))) {
            try {
                Files.writeString(Paths.get(LEGACY_PROPERTIES_FILE), sb.toString());
            } catch (IOException ignored) {}
        }
    }

    public static void updateActiveRunFolder(String path) {
        String activeConf = resolveConfigPath(null);
        Config config = new Config();
        config.loadProperties(activeConf);
        config.setActiveRunFolder(path);
        config.saveProperties(activeConf);
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
        System.out.println("  -m, --mode <analyze|compare|merge|interactive|server|report>   Execution mode (default: analyze)");
        System.out.println("  -i, --interactive                                Start interactive console wizard");
        System.out.println("  -s, --source <path>                             Source folder for analysis (analyze mode)");
        System.out.println("  -o, --old <path>                    Path to old version of file/directory (compare/merge modes)");
        System.out.println("  -n, --new <path>                    Path to new version of file/directory (compare/merge modes)");
        System.out.println("  --start-marker <string>             Start marker for merging (default: // START_MERGE)");
        System.out.println("  --end-marker <string>               End marker for merging (default: // END_MERGE)");
        System.out.println("  --output-dir <path>                 Directory for output report files (default: java_analysis_output)");
        System.out.println("  -t, --threads <num>                 Number of parallel threads (default: available cores)");
        System.out.println("  -p, --port <num>                    Web server port (default: 8080)");
        System.out.println("  -c, --config <path>                 Path to .conf or properties config file (default: javalens.conf)");
        System.out.println("  --save-config [path]                Write current runtime configuration to .conf file and exit");
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
    public int getServerPort() { return serverPort; }
    public String getConfigFilePath() { return configFilePath; }

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
    public void setCompareEnabled(boolean compareEnabled) { this.compareEnabled = compareEnabled; }
    public void setMergeEnabled(boolean mergeEnabled) { this.mergeEnabled = mergeEnabled; }
    public void setServerPort(int serverPort) { this.serverPort = serverPort; }
    public void setConfigFilePath(String configFilePath) { this.configFilePath = configFilePath; }
}
