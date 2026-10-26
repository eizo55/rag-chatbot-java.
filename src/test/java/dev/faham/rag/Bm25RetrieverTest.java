package dev.faham.rag;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class Bm25RetrieverTest {
    private final List<Chunk> chunks = List.of(
            new Chunk("kaution.md", 0, "Die Mietkaution beträgt höchstens drei Nettokaltmieten."),
            new Chunk("betrieb.md", 0, "Die Betriebskostenabrechnung muss innerhalb von zwölf Monaten zugehen."),
            new Chunk("verwaltung.txt", 0, "Eine Hausverwaltung kümmert sich um Instandhaltung und Mahnwesen."));

    @Test
    void findsMostRelevantChunkFirst() {
        Retriever r = new Bm25Retriever(chunks);
        List<Retriever.ScoredChunk> hits = r.retrieve("Wie hoch darf die Kaution sein?", 3);
        assertFalse(hits.isEmpty());
        assertEquals("kaution.md", hits.get(0).chunk().source());
    }

    @Test
    void umlautsAndCaseAreNormalised() {
        Retriever r = new Bm25Retriever(chunks);
        List<Retriever.ScoredChunk> hits = r.retrieve("KUEMMERT Instandhaltung", 1);
        assertEquals("verwaltung.txt", hits.get(0).chunk().source());
    }

    @Test
    void respectsTopKAndSkipsIrrelevantChunks() {
        Retriever r = new Bm25Retriever(chunks);
        assertEquals(1, r.retrieve("Kaution Betriebskostenabrechnung", 1).size());
        assertTrue(r.retrieve("Raumschiff", 3).isEmpty());
    }

    @Test
    void matchesPartOfGermanCompoundWord() {
        Retriever r = new Bm25Retriever(chunks);
        // "Abrechnung" only occurs inside "Betriebskostenabrechnung"
        List<Retriever.ScoredChunk> hits = r.retrieve("Wann kommt die Abrechnung?", 1);
        assertEquals("betrieb.md", hits.get(0).chunk().source());
    }

    @Test
    void emptyIndexReturnsNothing() {
        assertTrue(new Bm25Retriever(List.of()).retrieve("irgendwas", 3).isEmpty());
    }
}
