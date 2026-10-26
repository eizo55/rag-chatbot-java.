package dev.faham.rag;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {
    @Test
    void shortTextBecomesOneChunk() {
        List<Chunk> chunks = new Chunker(200, 20).chunk("a.md", "Erster Absatz.\n\nZweiter Absatz.");
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).text().contains("Zweiter"));
        assertEquals("a.md#0", chunks.get(0).label());
    }

    @Test
    void longTextIsSplitWithinLimitPlusOverlap() {
        String sentence = "Dies ist ein Satz mit einigen Woertern. ";
        String text = sentence.repeat(50);
        List<Chunk> chunks = new Chunker(200, 40).chunk("b.txt", text);
        assertTrue(chunks.size() > 1);
        for (Chunk c : chunks) {
            assertTrue(c.text().length() <= 200 + 2 + 40, "chunk too long: " + c.text().length());
        }
    }

    @Test
    void overlapCarriesContextIntoNextChunk() {
        String p1 = "alpha ".repeat(30).strip();
        String p2 = "omega ".repeat(30).strip();
        List<Chunk> chunks = new Chunker(200, 50).chunk("c.txt", p1 + "\n\n" + p2);
        assertEquals(2, chunks.size());
        assertTrue(chunks.get(1).text().startsWith("alpha"));
    }

    @Test
    void emptyInputGivesNoChunks() {
        assertTrue(new Chunker(100, 10).chunk("d.txt", "  \n\n  ").isEmpty());
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> new Chunker(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Chunker(10, 10));
    }
}
