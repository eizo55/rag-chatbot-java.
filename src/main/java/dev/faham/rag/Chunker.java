package dev.faham.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits text into overlapping chunks. Paragraphs are kept together where possible;
 * paragraphs longer than the limit are split at sentence or word boundaries.
 */
public final class Chunker {
    private final int maxChars;
    private final int overlapChars;

    public Chunker(int maxChars, int overlapChars) {
        if (maxChars <= 0) throw new IllegalArgumentException("maxChars must be > 0");
        if (overlapChars < 0 || overlapChars >= maxChars)
            throw new IllegalArgumentException("overlapChars must be in [0, maxChars)");
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
    }

    public List<Chunk> chunk(String source, String text) {
        List<String> pieces = new ArrayList<>();
        for (String paragraph : text.split("\\R\\s*\\R")) {
            String p = paragraph.strip();
            if (p.isEmpty()) continue;
            if (p.length() <= maxChars) {
                pieces.add(p);
            } else {
                pieces.addAll(splitLong(p));
            }
        }

        List<Chunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (current.length() > 0 && current.length() + piece.length() + 2 > maxChars) {
                chunks.add(new Chunk(source, chunks.size(), current.toString()));
                current = new StringBuilder(tail(current.toString()));
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(piece);
        }
        if (current.length() > 0) {
            chunks.add(new Chunk(source, chunks.size(), current.toString()));
        }
        return chunks;
    }

    /** Last {@code overlapChars} characters, cut at a word boundary, to carry context into the next chunk. */
    private String tail(String s) {
        if (overlapChars == 0 || s.length() <= overlapChars) return overlapChars == 0 ? "" : s;
        String t = s.substring(s.length() - overlapChars);
        int space = t.indexOf(' ');
        return space >= 0 ? t.substring(space + 1) : t;
    }

    private List<String> splitLong(String paragraph) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (String sentence : paragraph.split("(?<=[.!?])\\s+")) {
            if (sentence.length() > maxChars) {
                flush(parts, sb);
                for (String word : sentence.split("\\s+")) {
                    if (sb.length() + word.length() + 1 > maxChars) flush(parts, sb);
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(word);
                }
                continue;
            }
            if (sb.length() + sentence.length() + 1 > maxChars) flush(parts, sb);
            if (sb.length() > 0) sb.append(' ');
            sb.append(sentence);
        }
        flush(parts, sb);
        return parts;
    }

    private static void flush(List<String> parts, StringBuilder sb) {
        if (sb.length() > 0) {
            parts.add(sb.toString());
            sb.setLength(0);
        }
    }
}
