package com.javalens.web;

import com.javalens.Config;
import com.javalens.JavaAnalyzer;
import com.javalens.engine.CompareEngine;
import com.javalens.engine.MergeEngine;
import com.javalens.engine.ReportGenerator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import java.awt.EventQueue;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class WebServer {

    private static final PrintStream originalOut = System.out;
    private static final PrintStream originalErr = System.err;

    public static void start(int preferredPort) throws IOException {
        JavaAnalyzer.ensureBundledResourcesExtracted();
        System.setProperty("java.awt.headless", "false");
        HttpServer server = null;
        int port = preferredPort;
        while (port < preferredPort + 100) {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                break;
            } catch (java.net.BindException e) {
                port++;
            }
        }
        if (server == null) {
            throw new IOException("Could not find an available port to bind the server.");
        }

        server.createContext("/", new StaticHandler());
        server.createContext("/java_report_data.js", new ReportDataHandler());
        server.createContext("/api/config", new ConfigHandler());
        server.createContext("/api/config/load", new ConfigLoadHandler());
        server.createContext("/api/config/download", new ConfigDownloadHandler());
        server.createContext("/api/analyze", new AnalyzeHandler());
        server.createContext("/api/compare", new CompareHandler());
        server.createContext("/api/merge", new MergeHandler());
        server.createContext("/api/browse", new BrowseHandler());
        server.createContext("/api/generate-report", new GenerateReportHandler());
        server.createContext("/api/report-csv", new ReportCsvHandler());
        server.createContext("/api/report-download", new ReportDownloadHandler());
        server.createContext("/api/file", new FileContentHandler());
        server.setExecutor(null); // default executor
        server.start();
        System.out.println("=================================================");
        System.out.println("  JavaLens Web GUI server active!");
        System.out.println("  Open: http://localhost:" + port);
        System.out.println("=================================================");
    }

    private static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String pathStr = exchange.getRequestURI().getPath();
            if (pathStr.equals("/") || pathStr.equals("/index.html")) {
                byte[] content = null;
                Path indexFile = Paths.get("index.html");
                if (Files.exists(indexFile)) {
                    content = Files.readAllBytes(indexFile);
                } else {
                    try (InputStream is = WebServer.class.getResourceAsStream("/index.html")) {
                        if (is != null) {
                            content = is.readAllBytes();
                        }
                    }
                }

                if (content != null) {
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
                    exchange.getResponseHeaders().set("Pragma", "no-cache");
                    exchange.getResponseHeaders().set("Expires", "0");
                    exchange.sendResponseHeaders(200, content.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(content);
                    os.close();
                } else {
                    sendTextResponse(exchange, 404, "index.html not found.");
                }
            } else if (pathStr.equals("/README.md") || pathStr.equals("/readme")) {
                byte[] content = null;
                Path readmeFile = Paths.get("README.md");
                if (Files.exists(readmeFile)) {
                    content = Files.readAllBytes(readmeFile);
                } else {
                    try (InputStream is = WebServer.class.getResourceAsStream("/README.md")) {
                        if (is != null) {
                            content = is.readAllBytes();
                        }
                    }
                }

                if (content != null) {
                    exchange.getResponseHeaders().set("Content-Type", "text/markdown; charset=utf-8");
                    exchange.sendResponseHeaders(200, content.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(content);
                    os.close();
                } else {
                    sendTextResponse(exchange, 404, "README.md not found.");
                }
            } else if (pathStr.equals("/javalens.conf") || pathStr.equals("/analyzer.properties")) {
                Config config = new Config();
                config.load();
                File f = new File(config.getConfigFilePath());
                byte[] content = null;
                if (f.exists()) {
                    content = Files.readAllBytes(f.toPath());
                } else {
                    try (InputStream is = WebServer.class.getResourceAsStream("/javalens.conf")) {
                        if (is != null) content = is.readAllBytes();
                    }
                }
                if (content != null) {
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(200, content.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(content);
                    os.close();
                } else {
                    sendTextResponse(exchange, 404, "Configuration file not found.");
                }
            } else {
                sendTextResponse(exchange, 404, "Not Found");
            }
        }
    }

    private static class ReportDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Path reportFile = Paths.get("java_report_data.js");
            byte[] content;
            if (Files.exists(reportFile)) {
                content = Files.readAllBytes(reportFile);
            } else {
                content = "window.javaLensReportData = null;".getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().set("Content-Type", "application/javascript; charset=utf-8");
            exchange.sendResponseHeaders(200, content.length);
            OutputStream os = exchange.getResponseBody();
            os.write(content);
            os.close();
        }
    }

    private static class ConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Config config = new Config();
            config.load();

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String json = String.format(
                    "{\"configFilePath\":\"%s\",\"mode\":\"%s\",\"sourceFolder\":\"%s\",\"outputDir\":\"%s\",\"threads\":%d,\"serverPort\":%d,\"activeRunFolder\":\"%s\",\"oldPath\":\"%s\",\"newPath\":\"%s\",\"startMarker\":\"%s\",\"endMarker\":\"%s\",\"compareEnabled\":%b,\"mergeEnabled\":%b}",
                    escapeJson(config.getConfigFilePath()),
                    escapeJson(config.getMode().name().toLowerCase()),
                    escapeJson(config.getSourceFolder()),
                    escapeJson(config.getOutputDir()),
                    config.getThreads(),
                    config.getServerPort(),
                    escapeJson(config.getActiveRunFolder()),
                    escapeJson(config.getOldPath()),
                    escapeJson(config.getNewPath()),
                    escapeJson(config.getStartMarker()),
                    escapeJson(config.getEndMarker()),
                    config.isCompareEnabled(),
                    config.isMergeEnabled()
                );
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> params = parseBodyParams(exchange);
                String targetPath = params.get("configFilePath");
                if (targetPath != null && !targetPath.trim().isEmpty()) {
                    config.setConfigFilePath(targetPath.trim());
                }
                if (params.containsKey("mode")) {
                    try { config.setMode(Config.Mode.valueOf(params.get("mode").trim().toUpperCase())); } catch (Exception ignored) {}
                }
                if (params.containsKey("sourceFolder")) config.setSourceFolder(params.get("sourceFolder"));
                if (params.containsKey("outputDir")) config.setOutputDir(params.get("outputDir"));
                if (params.containsKey("threads")) {
                    try { config.setThreads(Integer.parseInt(params.get("threads"))); } catch (Exception ignored) {}
                }
                if (params.containsKey("serverPort")) {
                    try { config.setServerPort(Integer.parseInt(params.get("serverPort"))); } catch (Exception ignored) {}
                }
                if (params.containsKey("oldPath")) config.setOldPath(params.get("oldPath"));
                if (params.containsKey("newPath")) config.setNewPath(params.get("newPath"));
                if (params.containsKey("startMarker")) config.setStartMarker(params.get("startMarker"));
                if (params.containsKey("endMarker")) config.setEndMarker(params.get("endMarker"));
                if (params.containsKey("compareEnabled")) config.setCompareEnabled(Boolean.parseBoolean(params.get("compareEnabled")));
                if (params.containsKey("mergeEnabled")) config.setMergeEnabled(Boolean.parseBoolean(params.get("mergeEnabled")));

                // Save to active .conf file on local machine
                config.save();
                sendTextResponse(exchange, 200, "Configuration saved successfully to " + config.getConfigFilePath());
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    private static class ConfigLoadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            Map<String, String> params = parseBodyParams(exchange);
            String path = params.get("configFilePath");
            if (path == null || path.trim().isEmpty()) {
                sendTextResponse(exchange, 400, "Missing configFilePath parameter");
                return;
            }
            File f = new File(path.trim());
            if (!f.exists()) {
                sendTextResponse(exchange, 404, "Configuration file not found on local machine: " + path);
                return;
            }
            Config config = new Config();
            config.loadProperties(path.trim());
            String json = String.format(
                "{\"configFilePath\":\"%s\",\"mode\":\"%s\",\"sourceFolder\":\"%s\",\"outputDir\":\"%s\",\"threads\":%d,\"serverPort\":%d,\"activeRunFolder\":\"%s\",\"oldPath\":\"%s\",\"newPath\":\"%s\",\"startMarker\":\"%s\",\"endMarker\":\"%s\",\"compareEnabled\":%b,\"mergeEnabled\":%b}",
                escapeJson(config.getConfigFilePath()),
                escapeJson(config.getMode().name().toLowerCase()),
                escapeJson(config.getSourceFolder()),
                escapeJson(config.getOutputDir()),
                config.getThreads(),
                config.getServerPort(),
                escapeJson(config.getActiveRunFolder()),
                escapeJson(config.getOldPath()),
                escapeJson(config.getNewPath()),
                escapeJson(config.getStartMarker()),
                escapeJson(config.getEndMarker()),
                config.isCompareEnabled(),
                config.isMergeEnabled()
            );
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    private static class ConfigDownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            Config config = new Config();
            config.load();
            File f = new File(config.getConfigFilePath());
            byte[] bytes;
            String filename = "javalens.conf";
            if (f.exists()) {
                bytes = Files.readAllBytes(f.toPath());
                filename = f.getName();
            } else {
                config.save();
                bytes = Files.readAllBytes(new File(config.getConfigFilePath()).toPath());
            }
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    private static class AnalyzeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Map<String, String> params = parseBodyParams(exchange);
            Config config = new Config();
            config.load();
            if (params.containsKey("sourceFolder")) config.setSourceFolder(params.get("sourceFolder"));
            if (params.containsKey("outputDir")) config.setOutputDir(params.get("outputDir"));
            if (params.containsKey("threads")) {
                try { config.setThreads(Integer.parseInt(params.get("threads"))); } catch (Exception ignored) {}
            }
            config.save();

            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, 0); // chunked transfer
            OutputStream clientOut = exchange.getResponseBody();

            PrintStream customOut = new PrintStream(new DualOutputStream(originalOut, clientOut));
            PrintStream customErr = new PrintStream(new DualOutputStream(originalErr, clientOut));

            System.setOut(customOut);
            System.setErr(customErr);

            try {
                JavaAnalyzer.runAnalyzeFromGui(config);
                System.out.println("\n[GUI] Codebase analysis completed successfully.");
            } catch (Exception e) {
                System.err.println("\n[ERROR] Analysis failed: " + e.getMessage());
                e.printStackTrace(System.err);
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
                clientOut.close();
            }
        }
    }

    private static class CompareHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Map<String, String> params = parseBodyParams(exchange);
            Config config = new Config();
            config.load();
            if (params.containsKey("oldPath")) config.setOldPath(params.get("oldPath"));
            if (params.containsKey("newPath")) config.setNewPath(params.get("newPath"));
            config.save();

            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, 0); // chunked
            OutputStream clientOut = exchange.getResponseBody();

            PrintStream customOut = new PrintStream(new DualOutputStream(originalOut, clientOut));
            PrintStream customErr = new PrintStream(new DualOutputStream(originalErr, clientOut));

            System.setOut(customOut);
            System.setErr(customErr);

            try {
                CompareEngine.execute(config);
                System.out.println("\n[GUI] Semantic comparison completed successfully.");
            } catch (Exception e) {
                System.err.println("\n[ERROR] Semantic comparison failed: " + e.getMessage());
                e.printStackTrace(System.err);
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
                clientOut.close();
            }
        }
    }

    private static class MergeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Map<String, String> params = parseBodyParams(exchange);
            Config config = new Config();
            config.load();
            if (params.containsKey("oldPath")) config.setOldPath(params.get("oldPath"));
            if (params.containsKey("newPath")) config.setNewPath(params.get("newPath"));
            if (params.containsKey("startMarker")) config.setStartMarker(params.get("startMarker"));
            if (params.containsKey("endMarker")) config.setEndMarker(params.get("endMarker"));
            config.save();

            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, 0); // chunked
            OutputStream clientOut = exchange.getResponseBody();

            PrintStream customOut = new PrintStream(new DualOutputStream(originalOut, clientOut));
            PrintStream customErr = new PrintStream(new DualOutputStream(originalErr, clientOut));

            System.setOut(customOut);
            System.setErr(customErr);

            try {
                MergeEngine.execute(config);
                System.out.println("\n[GUI] Merge wizard completed successfully.");
            } catch (Exception e) {
                System.err.println("\n[ERROR] Merge wizard failed: " + e.getMessage());
                e.printStackTrace(System.err);
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
                clientOut.close();
            }
        }
    }

    private static class GenerateReportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Map<String, String> params = parseBodyParams(exchange);
            Config config = new Config();
            config.load();
            if (params.containsKey("oldPath")) config.setOldPath(params.get("oldPath"));
            if (params.containsKey("newPath")) config.setNewPath(params.get("newPath"));
            if (params.containsKey("startMarker")) config.setStartMarker(params.get("startMarker"));
            if (params.containsKey("endMarker")) config.setEndMarker(params.get("endMarker"));
            if (params.containsKey("outputDir")) config.setOutputDir(params.get("outputDir"));
            config.save();

            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, 0); // chunked
            OutputStream clientOut = exchange.getResponseBody();

            PrintStream customOut = new PrintStream(new DualOutputStream(originalOut, clientOut));
            PrintStream customErr = new PrintStream(new DualOutputStream(originalErr, clientOut));

            System.setOut(customOut);
            System.setErr(customErr);

            try {
                Path reportPath = ReportGenerator.generateFullReport(config);
                System.out.println("\n[GUI] Full report pipeline completed successfully.");
                System.out.println("[REPORT_PATH] " + reportPath.toAbsolutePath());
            } catch (Exception e) {
                System.err.println("\n[ERROR] Report generation failed: " + e.getMessage());
                e.printStackTrace(System.err);
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
                clientOut.close();
            }
        }
    }

    private static Path resolveActiveRunDir() {
        Config config = new Config();
        config.load();
        String activeFolder = config.getActiveRunFolder();

        // 1. Direct active folder
        if (activeFolder != null && !activeFolder.trim().isEmpty()) {
            Path candidate = Paths.get(activeFolder.trim());
            if (Files.exists(candidate) && Files.isDirectory(candidate)) {
                return candidate;
            }
        }

        // 2. outputDir directory itself (if it contains report files) or its run_* subfolders
        String outDirStr = config.getOutputDir();
        if (outDirStr != null && !outDirStr.trim().isEmpty()) {
            Path baseOut = Paths.get(outDirStr.trim());
            if (Files.exists(baseOut) && Files.isDirectory(baseOut)) {
                if (Files.exists(baseOut.resolve("javalens_report.csv"))) {
                    return baseOut;
                }
                try (var stream = Files.list(baseOut)) {
                    Path latest = stream.filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith("run_"))
                                        .max(Comparator.comparing(p -> p.getFileName().toString()))
                                        .orElse(null);
                    if (latest != null) return latest;
                } catch (Exception ignored) {}
            }
            if (baseOut.getParent() != null && Files.exists(baseOut.getParent()) && Files.isDirectory(baseOut.getParent())) {
                try (var stream = Files.list(baseOut.getParent())) {
                    Path latest = stream.filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith("run_"))
                                        .max(Comparator.comparing(p -> p.getFileName().toString()))
                                        .orElse(null);
                    if (latest != null) return latest;
                } catch (Exception ignored) {}
            }
        }

        // 3. Default output folder: java_analysis_output
        Path defaultOut = Paths.get("java_analysis_output");
        if (Files.exists(defaultOut) && Files.isDirectory(defaultOut)) {
            if (Files.exists(defaultOut.resolve("javalens_report.csv"))) {
                return defaultOut;
            }
            try (var stream = Files.list(defaultOut)) {
                Path latest = stream.filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith("run_") && Files.exists(p.resolve("javalens_report.csv")))
                                    .max(Comparator.comparing(p -> p.getFileName().toString()))
                                    .orElse(null);
                if (latest != null) return latest;
            } catch (Exception ignored) {}
        }

        // 4. Fallback discovery in standard locations
        for (String fallback : new String[]{"java_analysis_output", "test_out/report_out", "test_out", "out", "."}) {
            Path fb = Paths.get(fallback);
            if (Files.exists(fb) && Files.isDirectory(fb)) {
                try (var stream = Files.walk(fb, 3)) {
                    Path latest = stream.filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith("run_") && Files.exists(p.resolve("javalens_report.csv")))
                                        .max(Comparator.comparing(p -> p.getFileName().toString()))
                                        .orElse(null);
                    if (latest != null) return latest;
                } catch (Exception ignored) {}
            }
        }

        // 5. If no run directory exists, auto-generate a fresh report so downloads never 404
        try {
            if (config.getOldPath() == null || !Files.exists(Paths.get(config.getOldPath()))) {
                config.setOldPath(Files.exists(Paths.get("samples/v2")) ? "samples/v2" : "test_workspace/v1");
            }
            if (config.getNewPath() == null || !Files.exists(Paths.get(config.getNewPath()))) {
                config.setNewPath(Files.exists(Paths.get("samples/v1")) ? "samples/v1" : "test_workspace/v2");
            }
            config.setOutputDir("java_analysis_output");
            Path reportCsv = ReportGenerator.generateFullReport(config);
            return reportCsv.getParent();
        } catch (Exception e) {
            System.err.println("Warning: Auto-generating baseline report failed: " + e.getMessage());
        }

        return null;
    }

    private static class ReportCsvHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Path runDir = resolveActiveRunDir();
            Path reportCsv = null;
            if (runDir != null) {
                try {
                    reportCsv = ReportGenerator.ensureReportFormat(runDir, "csv");
                } catch (Exception ignored) {
                    reportCsv = runDir.resolve("javalens_report.csv");
                }
            }

            if (reportCsv == null || !Files.exists(reportCsv)) {
                sendTextResponse(exchange, 404, "No report CSV found. Run Generate Report first.");
                return;
            }

            byte[] content = Files.readAllBytes(reportCsv);
            exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"javalens_report.csv\"");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, content.length);
            OutputStream os = exchange.getResponseBody();
            os.write(content);
            os.close();
        }
    }

    private static class ReportDownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod().toUpperCase();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String query = exchange.getRequestURI().getRawQuery();
            String format = "csv";
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=", 2);
                    if (pair.length == 2 && "format".equalsIgnoreCase(pair[0])) {
                        format = URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name()).toLowerCase().trim();
                    }
                }
            }

            Path runDir = resolveActiveRunDir();
            if (runDir == null) {
                sendTextResponse(exchange, 404, "No report run directory found. Please run Generate Report first.");
                return;
            }

            String targetFileName;
            String contentType;
            switch (format) {
                case "json":
                    targetFileName = "javalens_report.json";
                    contentType = "application/json; charset=utf-8";
                    break;
                case "html":
                    targetFileName = "javalens_report.html";
                    contentType = "text/html; charset=utf-8";
                    break;
                case "md":
                case "markdown":
                    targetFileName = "javalens_report.md";
                    contentType = "text/markdown; charset=utf-8";
                    break;
                case "xml":
                    targetFileName = "javalens_report.xml";
                    contentType = "application/xml; charset=utf-8";
                    break;
                case "zip":
                case "bundle":
                case "all":
                    targetFileName = "javalens_report_bundle.zip";
                    contentType = "application/zip";
                    break;
                case "csv":
                default:
                    targetFileName = "javalens_report.csv";
                    contentType = "text/csv; charset=utf-8";
                    break;
            }

            Path targetFile;
            try {
                targetFile = ReportGenerator.ensureReportFormat(runDir, format);
            } catch (Exception e) {
                targetFile = runDir.resolve(targetFileName);
            }

            if (targetFile == null || !Files.exists(targetFile)) {
                sendTextResponse(exchange, 404, "Requested report format file not found: " + targetFileName);
                return;
            }

            byte[] content = Files.readAllBytes(targetFile);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + targetFileName + "\"");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            if ("HEAD".equals(method)) {
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(200, content.length);
                OutputStream os = exchange.getResponseBody();
                os.write(content);
                os.close();
            }
        }
    }

    private static class FileContentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getRawQuery();
            String filePath = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=", 2);
                    if (pair.length == 2 && "path".equals(pair[0])) {
                        filePath = URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name());
                    }
                }
            }
            if (filePath == null || filePath.trim().isEmpty()) {
                sendTextResponse(exchange, 400, "Missing path parameter");
                return;
            }
            File file = new File(filePath.trim());
            if (!file.exists() || !file.isFile()) {
                file = new File(".", filePath.trim());
            }
            if (!file.exists() || !file.isFile()) {
                try {
                    final String searchName = new File(filePath.trim()).getName();
                    java.util.Optional<Path> found = Files.walk(Paths.get("."))
                        .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().equals(searchName))
                        .findFirst();
                    if (found.isPresent()) {
                        file = found.get().toFile();
                    }
                } catch (Exception ignored) {}
            }
            if (!file.exists() || !file.isFile()) {
                String resPath = filePath.startsWith("/") ? filePath : "/" + filePath;
                try (InputStream is = WebServer.class.getResourceAsStream(resPath)) {
                    if (is != null) {
                        byte[] resContent = is.readAllBytes();
                        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                        exchange.sendResponseHeaders(200, resContent.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(resContent);
                        os.close();
                        return;
                    }
                } catch (Exception ignored) {}

                sendTextResponse(exchange, 404, "File not found: " + filePath);
                return;
            }
            byte[] content = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, content.length);
            OutputStream os = exchange.getResponseBody();
            os.write(content);
            os.close();
        }
    }

    private static class DualOutputStream extends OutputStream {
        private final OutputStream out1;
        private final OutputStream out2;

        public DualOutputStream(OutputStream out1, OutputStream out2) {
            this.out1 = out1;
            this.out2 = out2;
        }

        @Override
        public void write(int b) throws IOException {
            out1.write(b);
            if (out2 != null) {
                try {
                    out2.write(b);
                    out2.flush();
                } catch (IOException ignored) {}
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out1.write(b, off, len);
            if (out2 != null) {
                try {
                    out2.write(b, off, len);
                    out2.flush();
                } catch (IOException ignored) {}
            }
        }
    }

    private static Map<String, String> parseBodyParams(HttpExchange exchange) throws IOException {
        Map<String, String> result = new HashMap<>();
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        String body = bos.toString(StandardCharsets.UTF_8.name());
        
        if (body.startsWith("{")) {
            body = body.substring(1, body.length() - 1);
            String[] pairs = body.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String k = kv[0].trim().replace("\"", "");
                    String v = kv[1].trim().replace("\"", "");
                    result.put(k, v);
                }
            }
        } else {
            String[] pairs = body.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name());
                    String v = URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name());
                    result.put(k, v);
                }
            }
        }
        return result;
    }

    private static void sendTextResponse(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static class BrowseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            if (java.awt.GraphicsEnvironment.isHeadless()) {
                sendTextResponse(exchange, 500, "Error: Headless environment does not support file selection dialog.");
                return;
            }

            Map<String, String> params = parseBodyParams(exchange);
            final String modeParam = params.getOrDefault("mode", "directories");

            final String[] selectedPath = new String[]{""};
            try {
                EventQueue.invokeAndWait(() -> {
                    JFrame frame = new JFrame();
                    frame.setAlwaysOnTop(true);
                    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

                    JFileChooser chooser = new JFileChooser();
                    if ("files".equalsIgnoreCase(modeParam)) {
                        chooser.setDialogTitle("Select JavaLens Configuration File (.conf)");
                        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                    } else if ("files_and_directories".equalsIgnoreCase(modeParam)) {
                        chooser.setDialogTitle("Select File or Directory");
                        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                    } else {
                        chooser.setDialogTitle("Select JavaLens Workspace Folder");
                        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    }

                    int returnVal = chooser.showOpenDialog(frame);
                    if (returnVal == JFileChooser.APPROVE_OPTION) {
                        selectedPath[0] = chooser.getSelectedFile().getAbsolutePath();
                    }
                    frame.dispose();
                });
            } catch (Exception e) {
                sendTextResponse(exchange, 500, "Error: " + e.getMessage());
                return;
            }

            if (!selectedPath[0].isEmpty()) {
                sendTextResponse(exchange, 200, selectedPath[0]);
            } else {
                sendTextResponse(exchange, 204, "No folder selected");
            }
        }
    }
}
