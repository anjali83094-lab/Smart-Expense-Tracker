import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class Main {

    private static ExpenseManager manager;

    public static void main(String[] args) throws IOException {

        manager = new ExpenseManager();

        HttpServer server = HttpServer.create(
                new InetSocketAddress(8080), 0);

        server.setExecutor(Executors.newCachedThreadPool());

        // Get all transactions or add a new transaction.
        server.createContext("/api/transactions", exchange -> {
            try {
                addCorsHeaders(exchange);

                String method = exchange.getRequestMethod();

                if (method.equals("OPTIONS")) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                if (method.equals("GET")) {
                    sendJson(exchange, 200,
                            getTransactionsJson());
                } else if (method.equals("POST")) {
                    Map<String, String> form =
                            parseForm(exchange);

                    String type = form.get("type");
                    String category = form.get("category");
                    String description = form.get("description");
                    double amount = Double.parseDouble(
                            form.get("amount"));

                    Transaction transaction =
                            manager.addTransaction(
                                    type, amount, category, description);

                    sendJson(exchange, 201,
                            "{\"message\":\"Transaction added\","
                            + "\"id\":" + transaction.getId() + "}");
                } else {
                    sendJson(exchange, 405,
                            "{\"error\":\"Method not allowed\"}");
                }

            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400,
                        "{\"error\":\""
                        + escapeJson(e.getMessage()) + "\"}");
            } catch (IOException e) {
                sendJson(exchange, 500,
                        "{\"error\":\"Unable to save transaction\"}");
            } finally {
                exchange.close();
            }
        });

        // Get income, expenses, and balance.
        server.createContext("/api/summary", exchange -> {
            try {
                if (!exchange.getRequestMethod().equals("GET")) {
                    sendJson(exchange, 405,
                            "{\"error\":\"Method not allowed\"}");
                    return;
                }

                String json = String.format(
                        Locale.US,
                        "{\"income\":%.2f,\"expenses\":%.2f,"
                        + "\"balance\":%.2f}",
                        manager.getTotalIncome(),
                        manager.getTotalExpenses(),
                        manager.getBalance());

                sendJson(exchange, 200, json);

            } finally {
                exchange.close();
            }
        });

        // Generate a report in a separate thread.
        server.createContext("/api/report", exchange -> {
            try {
                if (!exchange.getRequestMethod().equals("POST")) {
                    sendJson(exchange, 405,
                            "{\"error\":\"Method not allowed\"}");
                    return;
                }

                String report = manager.generateReport();

                sendJson(exchange, 200,
                        "{\"report\":\""
                        + escapeJson(report) + "\"}");

                ReportThread thread = new ReportThread(manager);
                thread.start();

            } finally {
                exchange.close();
            }
        });

        // Serve HTML, CSS, and JavaScript.
        server.createContext("/", exchange -> {
            try {
                serveStaticFile(exchange);
            } catch (IOException e) {
                if (exchange.getResponseCode() == -1) {
                    sendJson(exchange, 500,
                            "{\"error\":\"Unable to read file\"}");
                }
            } finally {
                exchange.close();
            }
        });

        server.start();

        System.out.println("Smart Expense Tracker is running!");
        System.out.println("Open http://localhost:8080");
    }

    private static Map<String, String> parseForm(
            HttpExchange exchange) throws IOException {

        String body = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);

        Map<String, String> values = new java.util.HashMap<>();

        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);

            String key = URLDecoder.decode(
                    parts[0], StandardCharsets.UTF_8);

            String value = parts.length > 1
                    ? URLDecoder.decode(
                            parts[1], StandardCharsets.UTF_8)
                    : "";

            values.put(key, value);
        }

        return values;
    }

    private static String getTransactionsJson() {
        List<Transaction> transactions =
                manager.getTransactions();

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        for (Transaction t : transactions) {
            if (!first) {
                json.append(",");
            }

            first = false;

            String category = "";

            if (t instanceof Expense) {
                category = ((Expense) t).getCategory();
            } else if (t instanceof Income) {
                category = ((Income) t).getCategory();
            }

            json.append("{")
                    .append("\"id\":").append(t.getId()).append(",")
                    .append("\"type\":\"")
                    .append(escapeJson(t.getType())).append("\",")
                    .append("\"amount\":").append(t.getAmount()).append(",")
                    .append("\"category\":\"")
                    .append(escapeJson(category)).append("\",")
                    .append("\"description\":\"")
                    .append(escapeJson(t.getDescription())).append("\",")
                    .append("\"date\":\"").append(t.getDate()).append("\"")
                    .append("}");
        }

        return json.append("]").toString();
    }

    private static void serveStaticFile(
            HttpExchange exchange) throws IOException {

        if (!exchange.getRequestMethod().equals("GET")) {
            sendJson(exchange, 405,
                    "{\"error\":\"Method not allowed\"}");
            return;
        }

        String path = exchange.getRequestURI().getPath();

        if (path.equals("/")) {
            path = "/index.html";
        }

        java.nio.file.Path root =
                java.nio.file.Paths.get("public")
                        .toAbsolutePath().normalize();

        java.nio.file.Path file =
                root.resolve(path.substring(1)).normalize();

        if (!file.startsWith(root)
                || !java.nio.file.Files.isRegularFile(file)) {

            byte[] notFound = "404 - File not found".getBytes(
                    StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(404, notFound.length);
            exchange.getResponseBody().write(notFound);
            return;
        }

        String contentType = "text/plain; charset=UTF-8";

        if (path.endsWith(".html")) {
            contentType = "text/html; charset=UTF-8";
        } else if (path.endsWith(".css")) {
            contentType = "text/css; charset=UTF-8";
        } else if (path.endsWith(".js")) {
            contentType = "application/javascript; charset=UTF-8";
        }

        byte[] content = java.nio.file.Files.readAllBytes(file);

        exchange.getResponseHeaders().set(
                "Content-Type", contentType);

        exchange.sendResponseHeaders(200, content.length);
        exchange.getResponseBody().write(content);
    }

    private static void sendJson(
            HttpExchange exchange,
            int status,
            String json) throws IOException {

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");

        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Origin", "*");

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Methods", "GET, POST, OPTIONS");

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Headers", "Content-Type");
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}