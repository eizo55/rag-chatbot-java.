package dev.faham.rag;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Command-line chat. Usage: java -jar rag-chatbot.jar [docsDir]
 *
 * <p>Which language model answers is chosen like this:
 * <ol>
 *   <li>LLM_BACKEND=claude | ollama | none forces a backend.</li>
 *   <li>Otherwise: ANTHROPIC_API_KEY set -> Claude; else a running local Ollama -> Ollama;
 *       else retrieval only (just prints the matching passages).</li>
 * </ol>
 * Settings: ANTHROPIC_API_KEY, ANTHROPIC_MODEL, OLLAMA_MODEL (default llama3.2), OLLAMA_URL.
 */
public final class Main {
    private static final String DEFAULT_CLAUDE_MODEL = "claude-sonnet-5-5";
    private static final String DEFAULT_OLLAMA_MODEL = "llama3.2";

    /** The chosen backend plus a human-readable description for the start-up message. */
    record Backend(LlmClient client, String description) {}

    public static void main(String[] args) throws Exception {
        Path docsDir = Path.of(args.length > 0 ? args[0] : "docs");
        List<Chunk> chunks = new DocumentLoader(new Chunker(800, 120)).load(docsDir);
        if (chunks.isEmpty()) {
            System.err.println("Keine .txt/.md-Dateien in " + docsDir.toAbsolutePath() + " gefunden.");
            System.exit(1);
        }
        Retriever retriever = new Bm25Retriever(chunks);
        System.out.println(chunks.size() + " Abschnitte aus " + docsDir + " indexiert.");

        Backend backend = chooseBackend(System.getenv());
        System.out.println("Modus: " + backend.description() + " (Beenden mit 'exit')");
        RagChatbot bot = new RagChatbot(retriever, backend.client(), 4);

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            System.out.print("\nDu> ");
            System.out.flush();
            String line = in.readLine();
            if (line == null || line.strip().equalsIgnoreCase("exit")) break;
            if (line.isBlank()) continue;
            try {
                RagChatbot.Answer answer = bot.ask(line);
                System.out.println("\nBot> " + answer.text());
                System.out.println("\nQuellen:");
                if (answer.sources().isEmpty()) System.out.println("  (keine Treffer)");
                for (int i = 0; i < answer.sources().size(); i++) {
                    Retriever.ScoredChunk s = answer.sources().get(i);
                    String preview = s.chunk().text().replaceAll("\\s+", " ");
                    if (preview.length() > 110) preview = preview.substring(0, 110) + "...";
                    System.out.printf("  [%d] %s (Score %.2f): %s%n", i + 1, s.chunk().label(), s.score(), preview);
                }
            } catch (Exception e) {
                System.err.println("Fehler: " + e.getMessage());
            }
        }
    }

    static Backend chooseBackend(Map<String, String> env) {
        String forced = env.getOrDefault("LLM_BACKEND", "").strip().toLowerCase();
        String apiKey = env.getOrDefault("ANTHROPIC_API_KEY", "");
        String ollamaUrl = env.getOrDefault("OLLAMA_URL", OllamaClient.DEFAULT_URL);
        String ollamaModel = env.getOrDefault("OLLAMA_MODEL", DEFAULT_OLLAMA_MODEL);

        boolean useClaude = forced.equals("claude") || (forced.isEmpty() && !apiKey.isBlank());
        if (useClaude) {
            if (apiKey.isBlank()) {
                return noModel("LLM_BACKEND=claude, aber ANTHROPIC_API_KEY ist nicht gesetzt");
            }
            String model = env.getOrDefault("ANTHROPIC_MODEL", DEFAULT_CLAUDE_MODEL);
            return new Backend(new ClaudeClient(apiKey, model, 1024), "Claude (" + model + ")");
        }

        boolean tryOllama = forced.equals("ollama") || forced.isEmpty();
        if (tryOllama && !forced.equals("none")) {
            if (OllamaClient.isRunning(ollamaUrl)) {
                return new Backend(new OllamaClient(ollamaUrl, ollamaModel),
                        "Ollama lokal (" + ollamaModel + ")");
            }
            if (forced.equals("ollama")) {
                return noModel("Ollama laeuft nicht unter " + ollamaUrl + " (starte die Ollama-App oder 'ollama serve')");
            }
        }
        return noModel("kein Sprachmodell konfiguriert");
    }

    private static Backend noModel(String reason) {
        LlmClient none = (system, messages) ->
                "(Nur Suche, keine generierte Antwort: " + reason + ".)";
        return new Backend(none, "nur Retrieval - " + reason
                + ". Fuer Antworten: Ollama starten oder ANTHROPIC_API_KEY setzen.");
    }
}
