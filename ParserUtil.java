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
                        boolean isCtor = mt.getReturnType() == null;
                        String rtype   = isCtor ? "(constructor)"
                                                : mt.getReturnType().toString();
                        String name    = mt.getName().toString();
                        // Skip synthetic default constructor with no body
                        if (name.equals("<init>") && mt.getBody() == null) continue;

                        String mods = modifiers(mt.getModifiers());
                        String parameters = params(mt.getParameters());
                        int paramCount = mt.getParameters().size();
                        String throwsL = throwsList(mt.getThrows());
                        String anns = annotations(mt.getModifiers());
                        String kind = isCtor ? "constructor" : "method";
                        String pTypes = paramTypes(mt.getParameters());

                        model.addMethod(new MethodModel(
                            relPath, pkg, className, name, rtype, mods, parameters, paramCount,
                            throwsL, anns, kind, pTypes
                        ));
                    }
                }
            }
        }

        if (model == null) {
            model = new JavaModel(relPath, "");
        }
        return model;
    }

    private static String modifiers(ModifiersTree m) {
        return m.getFlags().stream()
                .map(f -> f.name().toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private static String annotations(ModifiersTree m) {
        return m.getAnnotations().stream()
                .map(a -> "@" + a.getAnnotationType())
                .collect(Collectors.joining(" "));
    }

    private static String params(List<? extends VariableTree> params) {
        return params.stream()
                .map(p -> p.getType() + " " + p.getName())
                .collect(Collectors.joining(", "));
    }

    private static String paramTypes(List<? extends VariableTree> params) {
        return params.stream()
                .map(p -> p.getType() != null ? p.getType().toString().replaceAll("\\s+", "") : "")
                .collect(Collectors.joining(","));
    }

    private static String throwsList(List<? extends ExpressionTree> throws_) {
        return throws_.stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    public static String csvToJson(Path csvFile) {
        if (!Files.exists(csvFile)) {
            return "[]";
        }
        try (BufferedReader br = Files.newBufferedReader(csvFile)) {
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

    private static String[] parseCsvLine(String line) {
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
        js.append("  \"compare_methods\": ").append(compMethJson).append("\n");
        js.append("};\n");

        try {
            Files.writeString(Paths.get("java_report_data.js"), js.toString());
            System.out.println("  Auto-load dashboard updated: java_report_data.js");
        } catch (IOException e) {
            System.err.println("Warning: Failed to write java_report_data.js: " + e.getMessage());
        }
    }
}
