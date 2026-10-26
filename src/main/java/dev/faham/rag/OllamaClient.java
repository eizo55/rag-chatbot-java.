package dev.faham.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Talks to a local Ollama server (https://ollama.com), so the chatbot runs for free and offline.
 * Uses POST /api/chat with streaming turned off.
 */
public final class OllamaClient implements LlmClient {
    public static final String DEFAULT_URL = "http://localhost:11434";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String model;

    public OllamaClient(String baseUrl, String model) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
    }

    /** True if an Ollama server answers at the given URL. */
    public static boolean isRunning(String baseUrl) {
        try {
            String url = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + "/api/tags";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<Void> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
                    .send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public String complete(String systemPrompt, List<Message> messages) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        body.putObject("options").put("temperature", 0.2);
        ArrayNode msgs = body.putArray("messages");
        msgs.addObject().put("role", "system").put("content", systemPrompt);
        for (Message m : messages) {
            msgs.addObject().put("role", m.role()).put("content", m.content());
        }

        // The first request can be slow because Ollama has to load the model into memory.
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .timeout(Duration.ofSeconds(300))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new IOException("Ollama is not reachable at " + baseUrl
                    + ". Start it with 'ollama serve' (or open the Ollama app).", e);
        }
        if (response.statusCode() == 404) {
            throw new IOException("Ollama does not know the model '" + model
                    + "'. Download it with: ollama pull " + model);
        }
        if (response.statusCode() != 200) {
            throw new IOException("Ollama error " + response.statusCode() + ": " + response.body());
        }
        return extractText(mapper.readTree(response.body()));
    }

    static String extractText(JsonNode root) {
        return root.path("message").path("content").asText("");
    }
}
