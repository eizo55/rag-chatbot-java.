package dev.faham.rag;

import java.io.IOException;
import java.util.List;

/** Minimal chat-completion abstraction so the bot can be tested without network access. */
public interface LlmClient {
    String complete(String systemPrompt, List<Message> messages) throws IOException, InterruptedException;

    record Message(String role, String content) {
        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }
}
