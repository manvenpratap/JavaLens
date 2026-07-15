import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class WebServer {

    private static final PrintStream originalOut = System.out;
    private static final PrintStream originalErr = System.err;

    public static void start(int preferredPort) throws IOException {
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
        server.createContext("/api/analyze", new AnalyzeHandler());
        server.createContext("/api/compare", new CompareHandler());
        server.createContext("/api/merge", new MergeHandler());
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
                Path indexFile = Paths.get("index.html");
                if (Files.exists(indexFile)) {
                    byte[] content = Files.readAllBytes(indexFile);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(200, content.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(content);
                    os.close();
                } else {
                    sendTextResponse(exchange, 404, "index.html not found in working directory.");
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
            config.loadProperties("analyzer.properties");

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String json = String.format(
                    "{\"sourceFolder\":\"%s\",\"outputDir\":\"%s\",\"threads\":%d,\"activeRunFolder\":\"%s\",\"oldPath\":\"%s\",\"newPath\":\"%s\",\"startMarker\":\"%s\",\"endMarker\":\"%s\"}",
                    escapeJson(config.getSourceFolder()),
                    escapeJson(config.getOutputDir()),
                    config.getThreads(),
                    escapeJson(config.getActiveRunFolder()),
                    escapeJson(config.getOldPath()),
                    escapeJson(config.getNewPath()),
                    escapeJson(config.getStartMarker()),
                    escapeJson(config.getEndMarker())
                );
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> params = parseBodyParams(exchange);
                if (params.containsKey("sourceFolder")) config.setSourceFolder(params.get("sourceFolder"));
                if (params.containsKey("outputDir")) config.setOutputDir(params.get("outputDir"));
                if (params.containsKey("threads")) {
                    try { config.setThreads(Integer.parseInt(params.get("threads"))); } catch (Exception ignored) {}
                }
                if (params.containsKey("oldPath")) config.setOldPath(params.get("oldPath"));
                if (params.containsKey("newPath")) config.setNewPath(params.get("newPath"));
                if (params.containsKey("startMarker")) config.setStartMarker(params.get("startMarker"));
                if (params.containsKey("endMarker")) config.setEndMarker(params.get("endMarker"));

                // Save properties
                config.saveProperties("analyzer.properties");
                sendTextResponse(exchange, 200, "Config updated successfully");
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
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
            config.loadProperties("analyzer.properties");
            if (params.containsKey("sourceFolder")) config.setSourceFolder(params.get("sourceFolder"));
            if (params.containsKey("outputDir")) config.setOutputDir(params.get("outputDir"));
            if (params.containsKey("threads")) {
                try { config.setThreads(Integer.parseInt(params.get("threads"))); } catch (Exception ignored) {}
            }
            config.saveProperties("analyzer.properties");

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
            config.loadProperties("analyzer.properties");
            if (params.containsKey("oldPath")) config.setOldPath(params.get("oldPath"));
            if (params.containsKey("newPath")) config.setNewPath(params.get("newPath"));
            config.saveProperties("analyzer.properties");

            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, 0); // chunked
            OutputStream clientOut = exchange.getResponseBody();

            PrintStream customOut = new PrintStream(new DualOutputStream(originalOut, clientOut));
            PrintStream customErr = new PrintStream(new DualOutputStream(originalErr, clientOut));

            System.setOut(customOut);
            System.setErr(customErr);

            try {
                CompareEngine.execute(config);
                System.out.println("\n[GUI] Comparison completed successfully.");
            } catch (Exception e) {
                System.err.println("\n[ERROR] Comparison failed: " + e.getMessage());
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
            config.loadProperties("analyzer.properties");
            if (params.containsKey("oldPath")) config.setOldPath(params.get("oldPath"));
            if (params.containsKey("newPath")) config.setNewPath(params.get("newPath"));
            if (params.containsKey("startMarker")) config.setStartMarker(params.get("startMarker"));
            if (params.containsKey("endMarker")) config.setEndMarker(params.get("endMarker"));
            config.saveProperties("analyzer.properties");

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
            // Very simple JSON parser for string-only flat key-values (e.g. {"key": "value"})
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
            // URL Form parameters
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
}
