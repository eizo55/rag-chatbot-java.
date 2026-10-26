package dev.faham.rag;

import java.util.List;

/**
 * Finds the chunks most relevant to a query. BM25 is used here; an embedding/vector-store
 * implementation can be dropped in behind the same interface.
 */
public interface Retriever {
    List<ScoredChunk> retrieve(String query, int topK);

    record ScoredChunk(Chunk chunk, double score) {}
}
