package dev.faham.rag;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagChatbotTest {
    /** Fake LLM that records what it was sent. */
    static final class RecordingLlm implements LlmClient {
        String lastSystem;
        List<Message> lastMessages;

        @Override
        public String complete(String systemPrompt, List<Message> messages) {
            lastSystem = systemPrompt;
            lastMessages = new ArrayList<>(messages);
            return "Antwort [1]";
        }
    }

    private final Retriever retriever = new Bm25Retriever(List.of(
            new Chunk("kaution.md", 0, "Die Mietkaution beträgt höchstens drei Nettokaltmieten."),
            new Chunk("betrieb.md", 0, "Betriebskosten werden jährlich abgerechnet.")));

    @Test
    void promptContainsNumberedSourcesAndQuestion() throws Exception {
        RecordingLlm llm = new RecordingLlm();
        RagChatbot bot = new RagChatbot(retriever, llm, 2);

        RagChatbot.Answer answer = bot.ask("Wie hoch darf die Kaution sein?");

        String prompt = llm.lastMessages.get(llm.lastMessages.size() - 1).content();
        assertTrue(prompt.contains("[1] (kaution.md#0)"));
        assertTrue(prompt.contains("drei Nettokaltmieten"));
        assertTrue(prompt.endsWith("Frage: Wie hoch darf die Kaution sein?"));
        assertEquals("Antwort [1]", answer.text());
        assertEquals("kaution.md", answer.sources().get(0).chunk().source());
    }

    @Test
    void historyKeepsPlainQuestionsNotRetrievedContext() throws Exception {
        RecordingLlm llm = new RecordingLlm();
        RagChatbot bot = new RagChatbot(retriever, llm, 2);

        bot.ask("Was ist die Kaution?");
        bot.ask("Und die Betriebskosten?");

        // second call: [history user, history assistant, new user prompt]
        assertEquals(3, llm.lastMessages.size());
        assertEquals("Was ist die Kaution?", llm.lastMessages.get(0).content());
        assertEquals("assistant", llm.lastMessages.get(1).role());
    }

    @Test
    void noHitsIsMarkedInPrompt() throws Exception {
        RecordingLlm llm = new RecordingLlm();
        new RagChatbot(retriever, llm, 2).ask("Raumschiff");
        assertTrue(llm.lastMessages.get(0).content().contains("keine passenden Quellen"));
    }
}
