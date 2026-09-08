package com.javalens.parser;

import com.javalens.model.AttributeModel;
import com.javalens.model.JavaModel;
import com.javalens.model.MethodModel;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ParserUtil {

    public static JavaModel parseFile(Path file, Path root) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
            throw new IllegalStateException("No system Java compiler — run with a JDK, not just a JRE.");

        StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(List.of(file));

        JavacTask task = (JavacTask) compiler.getTask(
                Writer.nullWriter(), fm, d -> {}, null, null, units);

        Iterable<? extends CompilationUnitTree> trees = task.parse();
        fm.close();

        String relPath = root == null ? file.getFileName().toString() 
                                      : root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');

        JavaModel model = null;

        for (CompilationUnitTree cu : trees) {
            String pkg = cu.getPackageName() != null ? cu.getPackageName().toString() : "";
            if (model == null) {
                model = new JavaModel(relPath, pkg);
            }

            for (Tree typeTree : cu.getTypeDecls()) {
                if (!(typeTree instanceof ClassTree cls)) continue;

                String className = pkg.isEmpty() ? cls.getSimpleName().toString()
                                                 : pkg + "." + cls.getSimpleName();

                for (Tree member : cls.getMembers()) {

                    // ── Fields ───────────────────────────────────────────────
                    if (member instanceof VariableTree vt) {
                        String mods  = modifiers(vt.getModifiers());
                        String anns  = annotations(vt.getModifiers());
                        String ftype = vt.getType() != null ? vt.getType().toString() : "";
                        String init  = vt.getInitializer() != null
                                       ? truncate(vt.getInitializer().toString(), 80) : "";
                        model.addAttribute(new AttributeModel(
                            relPath, pkg, className, vt.getName().toString(), ftype, mods, anns, init
                        ));
                    }

                    // ── Methods ──────────────────────────────────────────────
                    else if (member instanceof MethodTree mt) {
                        String mname   = mt.getName().toString();
                        boolean isCtor = "<init>".equals(mname);
                        String rtype   = isCtor ? "(constructor)"
                                                : (mt.getReturnType() != null ? mt.getReturnType().toString() : "void");
                        String mods    = modifiers(mt.getModifiers());
                        String anns    = annotations(mt.getModifiers());
                        String kind    = isCtor ? "constructor" : "method";

                        // Parameters: "Type name, Type name"
                        String params = mt.getParameters().stream()
                                          .map(p -> p.getType() + " " + p.getName())
                                          .collect(Collectors.joining(", "));

                        // Types-only signature for overload identification: "Type,Type"
                        String paramTypes = mt.getParameters().stream()
                                              .map(p -> p.getType().toString())
                                              .collect(Collectors.joining(","));

                        int pCount = mt.getParameters().size();

                        String throwsList = mt.getThrows().stream()
                                              .map(Object::toString)
                                              .collect(Collectors.joining(", "));

                        model.addMethod(new MethodModel(
                            relPath, pkg, className, mname, rtype, mods,
                            params, pCount, throwsList, anns, kind, paramTypes
                        ));
                    }
                }
            }
        }
        return model != null ? model : new JavaModel(relPath, "");
    }

    // ── AST extraction helpers ────────────────────────────────────────────────

    private static String modifiers(ModifiersTree modTree) {
        if (modTree == null) return "";
        return modTree.getFlags().stream()
                      .map(f -> f.name().toLowerCase())
                      .collect(Collectors.joining(" "));
    }

    private static String annotations(ModifiersTree modTree) {
        if (modTree == null) return "";
        return modTree.getAnnotations().stream()
                      .map(a -> a.toString().trim())
                      .collect(Collectors.joining(" "));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        s = s.replace("\r", " ").replace("\n", " ").trim();
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    // ── CSV to JSON serialization ─────────────────────────────────────────────

    public static String csvToJson(Path csvPath) {
        if (!Files.exists(csvPath)) return "[]";
        try (BufferedReader br = Files.newBufferedReader(csvPath)) {
            String headerLine = br.readLine();
            if (headerLine == null) return "[]";
            String[] headers = parseCsvLine(headerLine);
            
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                String[] cols = parseCsvLine(line);
                if (!first) sb.append(",\n");
                first = false;
                
                sb.append("  {");
                for (int i = 0; i < headers.length; i++) {
                    String val = i < cols.length ? cols[i] : "";
                    String escapedVal = val.replace("\\", "\\\\")
                                           .replace("\"", "\\\"")
                                           .replace("\n", "\\n")
                                           .replace("\r", "\\r");
                    sb.append("\"").append(headers[i]).append("\": \"").append(escapedVal).append("\"");
                    if (i < headers.length - 1) sb.append(", ");
                }
                sb.append("}");
            }
            sb.append("\n]");
            return sb.toString();
        } catch (IOException e) {
            return "[]";
        }
    }

    public static String[] parseCsvLine(String line) {
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
        return result.toArray(new String[0]);
    }

    public static void writeReportDataJs(String mode, String runFolder, Path outputDir) {
        Path attrFile = outputDir.resolve("java_attributes.csv");
        String attrJson = csvToJson(attrFile);
        
        Path methFile = outputDir.resolve("java_methods.csv");
        String methJson = csvToJson(methFile);

        Path compAttrFile = outputDir.resolve("comparison_attributes.csv");
        String compAttrJson = csvToJson(compAttrFile);

        Path compMethFile = outputDir.resolve("comparison_methods.csv");
        String compMethJson = csvToJson(compMethFile);

        Path reportFile = outputDir.resolve("javalens_report.csv");
        String reportJson = csvToJson(reportFile);

        Path mergeResultsFile = outputDir.resolve("merge_results.csv");
        String mergeResultsJson = csvToJson(mergeResultsFile);

        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                               .format(java.time.LocalDateTime.now());

        StringBuilder js = new StringBuilder();
        js.append("window.javaLensReportData = {\n");
        js.append("  \"timestamp\": \"").append(timestamp).append("\",\n");
        js.append("  \"mode\": \"").append(mode).append("\",\n");
        js.append("  \"activeRunFolder\": \"").append(runFolder.replace("\\", "/")).append("\",\n");
        js.append("  \"attributes\": ").append(attrJson).append(",\n");
        js.append("  \"methods\": ").append(methJson).append(",\n");
        js.append("  \"compare_attributes\": ").append(compAttrJson).append(",\n");
        js.append("  \"compare_methods\": ").append(compMethJson).append(",\n");
        js.append("  \"report\": ").append(reportJson).append(",\n");
        js.append("  \"merge_results\": ").append(mergeResultsJson).append("\n");
        js.append("};\n");

        try {
            Files.writeString(Paths.get("java_report_data.js"), js.toString());
            System.out.println("  Auto-load dashboard updated: java_report_data.js");
        } catch (IOException e) {
            System.err.println("Warning: Failed to write java_report_data.js: " + e.getMessage());
        }
    }
}
