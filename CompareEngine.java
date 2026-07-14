import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CompareEngine {

    private static final String[] COMP_ATTR_HDR = {
        "status", "file", "package", "class", "attribute_name",
        "attribute_type_old", "attribute_type_new",
        "modifiers_old", "modifiers_new",
        "annotations_old", "annotations_new",
        "initializer_old", "initializer_new"
    };

    private static final String[] COMP_METH_HDR = {
        "status", "file", "package", "class", "method_name",
        "return_type_old", "return_type_new",
        "modifiers_old", "modifiers_new",
        "parameters_old", "parameters_new",
        "throws_old", "throws_new",
        "annotations_old", "annotations_new",
        "kind_old", "kind_new"
    };

    public static void execute(Config config) throws Exception {
        Path oldPath = Paths.get(config.getOldPath()).toAbsolutePath().normalize();
        Path newPath = Paths.get(config.getNewPath()).toAbsolutePath().normalize();
        Path outputDir = Paths.get(config.getOutputDir()).toAbsolutePath().normalize();

        Files.createDirectories(outputDir);

        boolean oldIsDir = Files.isDirectory(oldPath);
        boolean newIsDir = Files.isDirectory(newPath);

        if (oldIsDir != newIsDir) {
            System.err.println("Error: Cannot compare a file with a directory. Both paths must be either files or directories.");
            System.exit(1);
        }

        System.out.println("Starting Java Comparison...");
        System.out.println("  Old version : " + oldPath);
        System.out.println("  New version : " + newPath);
        System.out.println("  Output dir  : " + outputDir);
        System.out.println();

        Path attrCsv = outputDir.resolve("comparison_attributes.csv");
        Path methCsv = outputDir.resolve("comparison_methods.csv");
        Path errorLog = outputDir.resolve("comparison_errors.txt");

        BlockingQueue<String[]> attrQ = new LinkedBlockingQueue<>();
        BlockingQueue<String[]> methQ = new LinkedBlockingQueue<>();
        List<String> errLog = Collections.synchronizedList(new ArrayList<>());

        Thread attrWriter = csvWriter(attrCsv, COMP_ATTR_HDR, attrQ);
        Thread methWriter = csvWriter(methCsv, COMP_METH_HDR, methQ);

        long startMs = System.currentTimeMillis();

        if (!oldIsDir) {
            // Compare single files
            try {
                compareSingleFiles(oldPath, newPath, attrQ, methQ);
            } catch (Exception e) {
                errLog.add("Error comparing " + oldPath.getFileName() + " and " + newPath.getFileName() + ": " + e.getMessage());
            }
        } else {
            // Compare directories
            compareDirectories(oldPath, newPath, config.getThreads(), attrQ, methQ, errLog);
        }

        // Signal EOF to writers
        attrQ.put(new String[0]);
        methQ.put(new String[0]);
        attrWriter.join();
        methWriter.join();

        long elapsed = System.currentTimeMillis() - startMs;
        System.out.printf("%nComparison completed in %.1fs%n", elapsed / 1000.0);
        System.out.println("  Attributes Comparison CSV : " + attrCsv);
        System.out.println("  Methods Comparison CSV    : " + methCsv);
        if (!errLog.isEmpty()) {
            Files.write(errorLog, errLog);
            System.out.println("  Errors encountered        : " + errLog.size() + " — see " + errorLog);
        } else {
            System.out.println("  Errors encountered        : 0");
        }

        // Write report data js for serverless UI
        String runFolderName = outputDir.getFileName().toString();
        ParserUtil.writeReportDataJs("COMPARE", runFolderName, outputDir);
    }

    private static void compareSingleFiles(Path oldFile, Path newFile,
                                           BlockingQueue<String[]> attrQ,
                                           BlockingQueue<String[]> methQ) throws Exception {
        JavaModel oldModel = ParserUtil.parseFile(oldFile, oldFile.getParent());
        JavaModel newModel = ParserUtil.parseFile(newFile, newFile.getParent());
        compareModels(oldModel, newModel, attrQ, methQ);
    }

    private static void compareDirectories(Path oldDir, Path newDir, int threads,
                                           BlockingQueue<String[]> attrQ,
                                           BlockingQueue<String[]> methQ,
                                           List<String> errLog) throws Exception {
        // Collect files
        Map<String, Path> oldFiles = scanJavaFiles(oldDir);
        Map<String, Path> newFiles = scanJavaFiles(newDir);

        Set<String> allRelPaths = new TreeSet<>();
        allRelPaths.addAll(oldFiles.keySet());
        allRelPaths.addAll(newFiles.keySet());

        int total = allRelPaths.size();
        if (total == 0) {
            System.out.println("No Java files found in either directory.");
            return;
        }

        System.out.println("Found " + oldFiles.size() + " files in old version, " + newFiles.size() + " in new version.");
        System.out.println("Total unique Java files to compare: " + total);
        System.out.println("Using " + threads + " parallel threads.");
        System.out.println();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger done = new AtomicInteger(0);
        long startMs = System.currentTimeMillis();

        for (String relPath : allRelPaths) {
            Path oldFile = oldFiles.get(relPath);
            Path newFile = newFiles.get(relPath);

            futures.add(pool.submit(() -> {
                try {
                    if (oldFile != null && newFile != null) {
                        JavaModel oldModel = ParserUtil.parseFile(oldFile, oldDir);
                        JavaModel newModel = ParserUtil.parseFile(newFile, newDir);
                        compareModels(oldModel, newModel, attrQ, methQ);
                    } else if (oldFile != null) {
                        // Removed completely
                        JavaModel oldModel = ParserUtil.parseFile(oldFile, oldDir);
                        reportAllAs(oldModel, "REMOVED", attrQ, methQ);
                    } else {
                        // Added completely
                        JavaModel newModel = ParserUtil.parseFile(newFile, newDir);
                        reportAllAs(newModel, "ADDED", attrQ, methQ);
                    }
                } catch (Exception e) {
                    errLog.add(relPath + " : " + e.getMessage());
                }

                int n = done.incrementAndGet();
                if (n % 500 == 0 || n == total) {
                    double pct = n * 100.0 / total;
                    long ms = System.currentTimeMillis() - startMs;
                    double rate = n / (ms / 1000.0);
                    double eta = rate > 0 ? (total - n) / rate : 0;
                    
                    int width = 30;
                    int progress = (int) ((double) n / total * width);
                    StringBuilder sb = new StringBuilder("\r  Compare Progress: \u001B[36m[");
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
    }

    private static Map<String, Path> scanJavaFiles(Path dir) throws IOException {
        Map<String, Path> fileMap = new HashMap<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    String rel = dir.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
                    fileMap.put(rel, file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return fileMap;
    }

    private static void compareModels(JavaModel oldModel, JavaModel newModel,
                                      BlockingQueue<String[]> attrQ,
                                      BlockingQueue<String[]> methQ) throws InterruptedException {
        String relPath = oldModel != null ? oldModel.getFile() : newModel.getFile();

        // 1. Compare attributes
        Map<String, AttributeModel> oldAttrs = new HashMap<>();
        if (oldModel != null) {
            for (AttributeModel a : oldModel.getAttributes()) {
                oldAttrs.put(a.className + "#" + a.name, a);
            }
        }

        Map<String, AttributeModel> newAttrs = new HashMap<>();
        if (newModel != null) {
            for (AttributeModel a : newModel.getAttributes()) {
                newAttrs.put(a.className + "#" + a.name, a);
            }
        }

        Set<String> allAttrKeys = new TreeSet<>();
        allAttrKeys.addAll(oldAttrs.keySet());
        allAttrKeys.addAll(newAttrs.keySet());

        for (String key : allAttrKeys) {
            AttributeModel oldA = oldAttrs.get(key);
            AttributeModel newA = newAttrs.get(key);

            if (oldA != null && newA != null) {
                boolean match = Objects.equals(oldA.type, newA.type) &&
                                Objects.equals(oldA.modifiers, newA.modifiers) &&
                                Objects.equals(oldA.annotations, newA.annotations) &&
                                Objects.equals(oldA.initializer, newA.initializer);
                String status = match ? "UNCHANGED" : "MODIFIED";
                attrQ.put(new String[]{
                    status, relPath, newA.packageName, newA.className, newA.name,
                    oldA.type, newA.type, oldA.modifiers, newA.modifiers,
                    oldA.annotations, newA.annotations, oldA.initializer, newA.initializer
                });
            } else if (oldA != null) {
                attrQ.put(new String[]{
                    "REMOVED", relPath, oldA.packageName, oldA.className, oldA.name,
                    oldA.type, "", oldA.modifiers, "",
                    oldA.annotations, "", oldA.initializer, ""
                });
            } else {
                attrQ.put(new String[]{
                    "ADDED", relPath, newA.packageName, newA.className, newA.name,
                    "", newA.type, "", newA.modifiers,
                    "", newA.annotations, "", newA.initializer
                });
            }
        }

        // 2. Compare methods
        // Method key consists of className + "#" + methodName + "(" + parameterTypes + ")"
        Map<String, MethodModel> oldMeths = new HashMap<>();
        if (oldModel != null) {
            for (MethodModel m : oldModel.getMethods()) {
                oldMeths.put(m.className + "#" + m.name + "(" + m.paramTypes + ")", m);
            }
        }

        Map<String, MethodModel> newMeths = new HashMap<>();
        if (newModel != null) {
            for (MethodModel m : newModel.getMethods()) {
                newMeths.put(m.className + "#" + m.name + "(" + m.paramTypes + ")", m);
            }
        }

        Set<String> allMethKeys = new TreeSet<>();
        allMethKeys.addAll(oldMeths.keySet());
        allMethKeys.addAll(newMeths.keySet());

        for (String key : allMethKeys) {
            MethodModel oldM = oldMeths.get(key);
            MethodModel newM = newMeths.get(key);

            if (oldM != null && newM != null) {
                boolean match = Objects.equals(oldM.returnType, newM.returnType) &&
                                Objects.equals(oldM.modifiers, newM.modifiers) &&
                                Objects.equals(oldM.parameters, newM.parameters) && // also matches param names
                                Objects.equals(oldM.throwsList, newM.throwsList) &&
                                Objects.equals(oldM.annotations, newM.annotations) &&
                                Objects.equals(oldM.kind, newM.kind);
                String status = match ? "UNCHANGED" : "MODIFIED";
                methQ.put(new String[]{
                    status, relPath, newM.packageName, newM.className, newM.name,
                    oldM.returnType, newM.returnType, oldM.modifiers, newM.modifiers,
                    oldM.parameters, newM.parameters, oldM.throwsList, newM.throwsList,
                    oldM.annotations, newM.annotations, oldM.kind, newM.kind
                });
            } else if (oldM != null) {
                methQ.put(new String[]{
                    "REMOVED", relPath, oldM.packageName, oldM.className, oldM.name,
                    oldM.returnType, "", oldM.modifiers, "",
                    oldM.parameters, "", oldM.throwsList, "",
                    oldM.annotations, "", oldM.kind, ""
                });
            } else {
                methQ.put(new String[]{
                    "ADDED", relPath, newM.packageName, newM.className, newM.name,
                    "", newM.returnType, "", newM.modifiers,
                    "", newM.parameters, "", newM.throwsList,
                    "", newM.annotations, "", newM.kind
                });
            }
        }
    }

    private static void reportAllAs(JavaModel model, String status,
                                    BlockingQueue<String[]> attrQ,
                                    BlockingQueue<String[]> methQ) throws InterruptedException {
        if (model == null) return;
        String relPath = model.getFile();

        for (AttributeModel a : model.getAttributes()) {
            if ("ADDED".equals(status)) {
                attrQ.put(new String[]{
                    status, relPath, a.packageName, a.className, a.name,
                    "", a.type, "", a.modifiers, "", a.annotations, "", a.initializer
                });
            } else {
                attrQ.put(new String[]{
                    status, relPath, a.packageName, a.className, a.name,
                    a.type, "", a.modifiers, "", a.annotations, "", a.initializer, ""
                });
            }
        }

        for (MethodModel m : model.getMethods()) {
            if ("ADDED".equals(status)) {
                methQ.put(new String[]{
                    status, relPath, m.packageName, m.className, m.name,
                    "", m.returnType, "", m.modifiers, "", m.parameters, "", m.throwsList, "", m.annotations, "", m.kind
                });
            } else {
                methQ.put(new String[]{
                    status, relPath, m.packageName, m.className, m.name,
                    m.returnType, "", m.modifiers, "", m.parameters, "", m.throwsList, "", m.annotations, "", m.kind, ""
                });
            }
        }
    }

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
