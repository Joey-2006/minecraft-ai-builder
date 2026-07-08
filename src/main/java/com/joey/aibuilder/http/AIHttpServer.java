package com.joey.aibuilder.http;

import com.joey.aibuilder.AIBuilderConfig;
import com.joey.aibuilder.api.ApiHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class AIHttpServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("aibuilder");

    private final MinecraftServer server;
    private final ApiHandler apiHandler;
    private HttpServer httpServer;
    private volatile boolean running = false;

    public AIHttpServer(MinecraftServer server) {
        this.server = server;
        this.apiHandler = new ApiHandler(server);
    }

    public void start() {
        if (running) return;
        try {
            httpServer = HttpServer.create(
                    new InetSocketAddress(
                            AIBuilderConfig.INSTANCE.httpHost,
                            AIBuilderConfig.INSTANCE.httpPort),
                    0); // backlog

            // POST /api/v1/build - main build endpoint
            httpServer.createContext("/api/v1/build", this::handleBuild);
            // GET /api/v1/ping - health check
            httpServer.createContext("/api/v1/ping", this::handlePing);
            // GET /api/v1/status - server status
            httpServer.createContext("/api/v1/status", this::handleStatus);

            httpServer.setExecutor(Executors.newFixedThreadPool(4));
            httpServer.start();
            running = true;
            LOGGER.info("AI Builder HTTP API started on {}:{}",
                    AIBuilderConfig.INSTANCE.httpHost, AIBuilderConfig.INSTANCE.httpPort);
        } catch (IOException e) {
            LOGGER.error("Failed to start HTTP server", e);
        }
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(2);
            running = false;
            LOGGER.info("AI Builder HTTP API stopped");
        }
    }

    public boolean isRunning() {
        return running;
    }

    // ---- Handlers ----

    private void handleBuild(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"success\":false,\"error\":\"Method not allowed. Use POST.\"}");
            return;
        }

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.isEmpty()) {
                sendResponse(exchange, 400, "{\"success\":false,\"error\":\"Empty request body\"}");
                return;
            }

            String response = apiHandler.handleRequest(body);
            sendResponse(exchange, 200, response);

        } catch (Exception e) {
            LOGGER.error("Error in /build handler", e);
            sendResponse(exchange, 500,
                    "{\"success\":false,\"error\":\"Internal server error: " +
                            e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private void handlePing(HttpExchange exchange) throws IOException {
        String response = apiHandler.handleRequest("{\"commands\":[{\"action\":\"ping\"}]}");
        sendResponse(exchange, 200, response);
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        String response = apiHandler.handleRequest(
                "{\"commands\":[{\"action\":\"get_world_info\"},{\"action\":\"get_player\"}]}");
        sendResponse(exchange, 200, response);
    }

    private void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
