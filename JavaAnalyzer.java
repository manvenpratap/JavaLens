import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Java Analyzer — uses the built-in JDK Compiler Tree API (no external JARs).
 *
 * Compile:  javac JavaAnalyzer.java
 * Run:      java -jar javalens.jar <source-folder> [output-dir] [threads]
 */
public class JavaAnalyzer {

    // ── CSV headers ───────────────────────────────────────────────────────────

    static final String[] ATTR_HDR = {
        "file", "package", "class", "attribute_name", "attribute_type",
        "modifiers", "annotations", "initializer"
    };
    static final String[] METH_HDR = {
        "file", "package", "class", "method_name", "return_type",
        "modifiers", "parameters", "parameter_count", "throws", "annotations", "kind"
    };

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);

        if (config.getMode() == Config.Mode.INTERACTIVE) {
            InteractiveCli.start();
        } else if (config.getMode() == Config.Mode.COMPARE) {
            CompareEngine.execute(config);
        } else if (config.getMode() == Config.Mode.MERGE) {
            MergeEngine.execute(config);
        } else {
            runAnalyze(config);
        }
    }

    public static void runAnalyzeFromGui(Config config) throws Exception {
        runAnalyze(config);
    }

    private static void runAnalyze(Config config) throws Exception {
        Path sourceRoot = Paths.get(config.getSourceFolder()).toAbsolutePath();
        Path parentOutputDir = Paths.get(config.getOutputDir()).toAbsolutePath();
        int  threads    = config.getThreads();

        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                               .format(java.time.LocalDateTime.now());
        String runFolderName = "run_" + timestamp;
        Path runFolderPath = parentOutputDir.resolve(runFolderName);
        Files.createDirectories(runFolderPath);

        // Save active run folder context
        Config.updateActiveRunFolder(runFolderPath.toString());

        Path outputDir = runFolderPath;

        // Walk directory for .java files
        List<Path> javaFiles = new ArrayList<>();
        Path projectRoot = java.nio.file.Paths.get("").toAbsolutePath();
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    try {
                        Path parentAbs = file.getParent().toAbsolutePath();
                        if (parentAbs.equals(projectRoot)) {
                            return FileVisitResult.CONTINUE;
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        int total = javaFiles.size();
        if (total == 0) { System.out.println("No .java files found in: " + sourceRoot); return; }

        System.out.println("Found   : " + total + " Java files");
        System.out.println("Output  : " + outputDir);
        System.out.println("Threads : " + threads);
        System.out.println();

        Path attrCsv  = outputDir.resolve("java_attributes.csv");
        Path methCsv  = outputDir.resolve("java_methods.csv");
        Path errorLog = outputDir.resolve("parse_errors.txt");

        BlockingQueue<String[]> attrQ  = new LinkedBlockingQueue<>();
        BlockingQueue<String[]> methQ  = new LinkedBlockingQueue<>();
        List<String>            errLog = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger           done   = new AtomicInteger(0);
        long startMs = System.currentTimeMillis();

        Thread attrWriter = csvWriter(attrCsv, ATTR_HDR, attrQ);
        Thread methWriter = csvWriter(methCsv, METH_HDR, methQ);

        // Each thread gets its own JavaCompiler instance (not thread-safe)
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (Path file : javaFiles) {
            futures.add(pool.submit(() -> {
                try {
                    analyzeFile(file, sourceRoot, attrQ, methQ);
                } catch (Exception e) {
                    errLog.add(sourceRoot.relativize(file) + " : " + e.getMessage());
                }
                int n = done.incrementAndGet();
                if (n % 500 == 0 || n == total) {
                    double pct   = n * 100.0 / total;
                    long   ms    = System.currentTimeMillis() - startMs;
                    double rate  = n / (ms / 1000.0);
                    double eta   = rate > 0 ? (total - n) / rate : 0;
                    
                    int width = 30;
                    int progress = (int) ((double) n / total * width);
                    StringBuilder sb = new StringBuilder("\r  Analyze Progress: \u001B[36m[");
                    for (int k = 0; k < width; k++) {
                        if (k < progress) sb.append("█");
                        else sb.append("░");
                    }
                    sb.append("]\u001B[0m ");
                    sb.append(String.format("%.1f%%", pct));
                    if (n == total) {
                        sb.append(" | \u001B[32mCOMPLETE\u001B[0m                 \n");
                    } else {
                        sb.append(String.format(" | %.0f files/s | ETA %.0fs", rate, eta));
                    }
                    System.out.print(sb.toString());
                }
                return null;
            }));
        }

        for (Future<?> f : futures) f.get();
        pool.shutdown();

        attrQ.put(new String[0]);
        methQ.put(new String[0]);
        attrWriter.join();
        methWriter.join();

        long elapsed = System.currentTimeMillis() - startMs;
        System.out.printf("%nDone in %.1fs%n", elapsed / 1000.0);
        System.out.println("  Attributes CSV : " + attrCsv);
        System.out.println("  Methods CSV    : " + methCsv);
        if (!errLog.isEmpty()) {
            Files.write(errorLog, errLog);
            System.out.println("  Parse errors   : " + errLog.size() + " — see " + errorLog);
        } else {
            System.out.println("  Parse errors   : 0");
        }

        // Write report data js for serverless UI
        ParserUtil.writeReportDataJs("ANALYZE", runFolderName, outputDir);
    }

    private static void analyzeFile(Path file, Path root,
                                    BlockingQueue<String[]> attrQ,
                                    BlockingQueue<String[]> methQ) throws Exception {
        JavaModel model = ParserUtil.parseFile(file, root);
        String relPath = model.getFile();
        String pkg = model.getPackageName();

        for (AttributeModel a : model.getAttributes()) {
            attrQ.put(new String[]{
                relPath, pkg, a.className, a.name, a.type, a.modifiers, a.annotations, a.initializer
            });
        }

        for (MethodModel m : model.getMethods()) {
            methQ.put(new String[]{
                relPath, pkg, m.className, m.name, m.returnType, m.modifiers,
                m.parameters, String.valueOf(m.parameterCount), m.throwsList, m.annotations, m.kind
            });
        }
    }

    // ── Background CSV writer ─────────────────────────────────────────────────

    private static Thread csvWriter(Path path, String[] headers,
                                     BlockingQueue<String[]> queue) throws Exception {
        FileOutputStream fos = new FileOutputStream(path.toFile());
        Thread t = new Thread(() -> {
            try (PrintWriter pw = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(fos, "UTF-8")))) {
                pw.println(csvRow(headers));
                while (true) {
                    String[] row = queue.take();
                    if (row.length == 0) break;
                    pw.println(csvRow(row));
                }
            } catch (Exception e) {
                System.err.println("Error writing report: " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

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

