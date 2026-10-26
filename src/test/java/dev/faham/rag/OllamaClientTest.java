package dev.faham.rag;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the Ollama client against a tiny fake HTTP server, no real Ollama needed. */
class OllamaClientTest {
    private HttpServer server;
    private String url;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile int chatStatus = 200;
    private volatile String chatResponse = "{\"message\":{\"role\":\"assistant\",\"content\":\"Hallo [1]\"},\"done\":true}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", ex -> {
            byte[] b = "{\"models\":[]}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.createContext("/api/chat", ex -> {
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] b = chatResponse.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(chatStatus, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsSystemPromptAndMessagesAndParsesAnswer() throws Exception {
        OllamaClient client = new OllamaClient(url, "llama3.2");
        String answer = client.complete("SYS", List.of(LlmClient.Message.user("Frage?")));

        assertEquals("Hallo [1]", answer);
        JsonNode sent = new ObjectMapper().readTree(lastBody.get());
        assertEquals("llama3.2", sent.get("model").asText());
        assertFalse(sent.get("stream").asBoolean());
        assertEquals("system", sent.get("messages").get(0).get("role").asText());
        assertEquals("SYS", sent.get("messages").get(0).get("content").asText());
        assertEquals("user", sent.get("messages").get(1).get("role").asText());
        assertEquals("Frage?", sent.get("messages").get(1).get("content").asText());
    }

    @Test
    void unknownModelGivesPullHint() {
        chatStatus = 404;
        chatResponse = "{\"error\":\"model 'x' not found\"}";
        IOException e = assertThrows(IOException.class,
                () -> new OllamaClient(url, "x").complete("S", List.of(LlmClient.Message.user("?"))));
        assertTrue(e.getMessage().contains("ollama pull x"));
    }

    @Test
    void detectsRunningAndStoppedServer() {
        assertTrue(OllamaClient.isRunning(url));
        server.stop(0);
        assertFalse(OllamaClient.isRunning(url));
    }

    @Test
    void backendChoiceFollowsEnvironment() {
        // Claude key wins
        assertTrue(Main.chooseBackend(Map.of("ANTHROPIC_API_KEY", "k")).description().startsWith("Claude"));
        // no key, Ollama running -> Ollama
        Main.Backend b = Main.chooseBackend(Map.of("OLLAMA_URL", url, "OLLAMA_MODEL", "qwen2.5"));
        assertEquals("Ollama lokal (qwen2.5)", b.description());
        // forced none -> retrieval only
        assertTrue(Main.chooseBackend(Map.of("LLM_BACKEND", "none", "OLLAMA_URL", url))
                .description().startsWith("nur Retrieval"));
        // no key, nothing running -> retrieval only
        assertTrue(Main.chooseBackend(Map.of("OLLAMA_URL", "http://127.0.0.1:9"))
                .description().startsWith("nur Retrieval"));
    }
}
