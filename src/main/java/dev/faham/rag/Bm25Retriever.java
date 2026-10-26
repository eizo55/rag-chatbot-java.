package dev.faham.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Okapi BM25 over an in-memory inverted index. */
public final class Bm25Retriever implements Retriever {
    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final List<Chunk> chunks;
    private final List<Map<String, Integer>> termFreqs = new ArrayList<>();
    private final int[] lengths;
    private final Map<String, Integer> docFreq = new HashMap<>();
    private final double avgLength;

    public Bm25Retriever(List<Chunk> chunks) {
        this.chunks = List.copyOf(chunks);
        this.lengths = new int[chunks.size()];
        long total = 0;
        for (int i = 0; i < chunks.size(); i++) {
            List<String> tokens = Tokenizer.tokenize(chunks.get(i).text());
            Map<String, Integer> tf = new HashMap<>();
            // Terms are expanded with compound parts, but length stays the real word count
            // so the BM25 length normalisation is not distorted.
            for (String t : Tokenizer.expandCompounds(tokens)) tf.merge(t, 1, Integer::sum);
            termFreqs.add(tf);
            lengths[i] = tokens.size();
            total += tokens.size();
            for (String term : tf.keySet()) docFreq.merge(term, 1, Integer::sum);
        }
        this.avgLength = chunks.isEmpty() ? 0 : (double) total / chunks.size();
    }

    @Override
    public List<ScoredChunk> retrieve(String query, int topK) {
        List<String> queryTerms = Tokenizer.tokenize(query);
        List<ScoredChunk> scored = new ArrayList<>();
        int n = chunks.size();
        for (int i = 0; i < n; i++) {
            double score = 0;
            Map<String, Integer> tf = termFreqs.get(i);
            for (String term : queryTerms) {
                Integer f = tf.get(term);
                if (f == null) continue;
                int df = docFreq.get(term);
                double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
                double norm = f + K1 * (1 - B + B * lengths[i] / avgLength);
                score += idf * (f * (K1 + 1)) / norm;
            }
            if (score > 0) scored.add(new ScoredChunk(chunks.get(i), score));
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return scored.size() > topK ? scored.subList(0, topK) : scored;
    }
}
