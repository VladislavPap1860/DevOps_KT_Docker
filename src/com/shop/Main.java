package com.shop;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Main {

    record Product(int id, String name, int price) {}
    record CartItem(Product product, int quantity) {}

    private static final List<Product> CATALOG = List.of(
            new Product(1, "Retro Cassette Walkman", 4500),
            new Product(2, "Mechanical Keycap Set", 2900),
            new Product(3, "Desk LED Lamp", 1800),
            new Product(4, "USB-C Hub 6-in-1", 3200)
    );

    private static final List<CartItem> CART = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", Main::handleHome);
        server.createContext("/api/products", Main::handleProducts);
        server.createContext("/api/cart", Main::handleCart);
        server.createContext("/api/cart/add", Main::handleAddToCart);
        server.createContext("/api/cart/clear", Main::handleClearCart);

        server.setExecutor(null);
        server.start();
        System.out.println("[SHOP] Server started on http://localhost:" + port);
    }

    private static void handleHome(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        StringBuilder rows = new StringBuilder();
        for (Product p : CATALOG) {
            rows.append("<tr>")
                    .append("<td>").append(p.id()).append("</td>")
                    .append("<td>").append(escapeHtml(p.name())).append("</td>")
                    .append("<td>").append(p.price()).append(" RUB</td>")
                    .append("<td><form method='POST' action='/api/cart/add' style='margin:0;'>")
                    .append("<input type='hidden' name='id' value='").append(p.id()).append("'/>")
                    .append("<button type='submit'>В корзину</button>")
                    .append("</form></td>")
                    .append("</tr>");
        }

        StringBuilder cartRows = new StringBuilder();
        int total = 0;
        for (CartItem item : CART) {
            int sum = item.product().price() * item.quantity();
            total += sum;
            cartRows.append("<li>")
                    .append(escapeHtml(item.product().name()))
                    .append(" x ").append(item.quantity())
                    .append(" = ").append(sum).append(" RUB</li>");
        }

        String html = """
                <!DOCTYPE html>
                <html lang="ru">
                <head>
                  <meta charset="UTF-8" />
                  <title>Mini Shop</title>
                  <style>
                    body { font-family: Arial, sans-serif; max-width: 720px; margin: 40px auto; padding: 0 16px; }
                    table { width: 100%%; border-collapse: collapse; margin-bottom: 24px; }
                    th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }
                    button { padding: 6px 12px; cursor: pointer; }
                  </style>
                </head>
                <body>
                  <h1>Простой интернет-магазин</h1>
                  <h2>Каталог товаров</h2>
                  <table>
                    <thead>
                      <tr><th>ID</th><th>Товар</th><th>Цена</th><th>Действие</th></tr>
                    </thead>
                    <tbody>
                      %s
                    </tbody>
                  </table>

                  <h2>Корзина</h2>
                  <ul>%s</ul>
                  <p><strong>Итого: %d RUB</strong></p>
                  <form method="POST" action="/api/cart/clear">
                    <button type="submit">Очистить корзину</button>
                  </form>
                </body>
                </html>
                """.formatted(
                rows.toString(),
                cartRows.isEmpty() ? "<li>Корзина пуста</li>" : cartRows.toString(),
                total
        );

        System.out.println("[SHOP] GET / -> rendered catalog and cart");
        sendHtml(exchange, 200, html);
    }

    private static void handleProducts(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < CATALOG.size(); i++) {
            Product p = CATALOG.get(i);
            if (i > 0) json.append(",");
            json.append("{\"id\":").append(p.id())
                    .append(",\"name\":\"").append(escapeJson(p.name())).append("\"")
                    .append(",\"price\":").append(p.price()).append("}");
        }
        json.append("]");
        System.out.println("[SHOP] GET /api/products");
        sendJson(exchange, 200, json.toString());
    }

    private static void handleCart(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }
        int total = 0;
        StringBuilder itemsJson = new StringBuilder("[");
        for (int i = 0; i < CART.size(); i++) {
            CartItem item = CART.get(i);
            int sum = item.product().price() * item.quantity();
            total += sum;
            if (i > 0) itemsJson.append(",");
            itemsJson.append("{\"id\":").append(item.product().id())
                    .append(",\"name\":\"").append(escapeJson(item.product().name())).append("\"")
                    .append(",\"price\":").append(item.product().price())
                    .append(",\"quantity\":").append(item.quantity()).append("}");
        }
        itemsJson.append("]");
        String json = "{\"items\":" + itemsJson + ",\"total\":" + total + "}";
        System.out.println("[SHOP] GET /api/cart -> total=" + total);
        sendJson(exchange, 200, json);
    }

    private static void handleAddToCart(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseFormOrQuery(body);
        if (params.isEmpty() && exchange.getRequestURI().getRawQuery() != null) {
            params = parseFormOrQuery(exchange.getRequestURI().getRawQuery());
        }

        int productId;
        try {
            productId = Integer.parseInt(params.getOrDefault("id", "-1"));
        } catch (NumberFormatException e) {
            sendText(exchange, 400, "Invalid product id");
            return;
        }

        Optional<Product> found = CATALOG.stream().filter(p -> p.id() == productId).findFirst();
        if (found.isEmpty()) {
            sendText(exchange, 404, "Product not found");
            return;
        }

        Product target = found.get();
        addProduct(target);
        System.out.println("[SHOP] POST /api/cart/add -> added product id=" + target.id() + " (" + target.name() + ")");

        String accept = exchange.getRequestHeaders().getFirst("Accept");
        if (accept != null && accept.contains("application/json")) {
            sendJson(exchange, 200, "{\"status\":\"added\",\"productId\":" + target.id() + "}");
        } else {
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        }
    }

    private static void handleClearCart(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }
        CART.clear();
        System.out.println("[SHOP] POST /api/cart/clear -> cart cleared");

        String accept = exchange.getRequestHeaders().getFirst("Accept");
        if (accept != null && accept.contains("application/json")) {
            sendJson(exchange, 200, "{\"status\":\"cleared\"}");
        } else {
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        }
    }

    private static void addProduct(Product target) {
        for (int i = 0; i < CART.size(); i++) {
            CartItem existing = CART.get(i);
            if (existing.product().id() == target.id()) {
                CART.set(i, new CartItem(existing.product(), existing.quantity() + 1));
                return;
            }
        }
        CART.add(new CartItem(target, 1));
    }

    private static Map<String, String> parseFormOrQuery(String raw) {
        Map<String, String> map = new ConcurrentHashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String v = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }

    private static void sendHtml(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
