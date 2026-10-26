package dev.faham.rag;

/** A passage of a source document, the unit that gets indexed and retrieved. */
public record Chunk(String source, int index, String text) {
    public String label() {
        return source + "#" + index;
    }
}
