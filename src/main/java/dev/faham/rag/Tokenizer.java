package dev.faham.rag;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Lower-cases, folds umlauts/diacritics and drops very common German and English stop words. */
public final class Tokenizer {
    // Set.copyOf tolerates words that appear in both the German and the English list.
    private static final Set<String> STOP_WORDS = Set.copyOf(List.of(
            "der", "die", "das", "den", "dem", "des", "ein", "eine", "einer", "einen", "einem",
            "und", "oder", "ist", "sind", "war", "wird", "werden", "mit", "von", "zu", "im", "in",
            "am", "an", "auf", "fur", "ueber", "nicht", "auch", "es", "sie", "er", "wie", "was",
            "the", "a", "an", "and", "or", "is", "are", "of", "to", "in", "on", "for", "with", "what", "how"));

    private Tokenizer() {}

    public static List<String> tokenize(String text) {
        String folded = text.toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        folded = Normalizer.normalize(folded, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        List<String> tokens = new ArrayList<>();
        for (String t : folded.split("[^\\p{L}\\p{Nd}]+")) {
            if (t.length() > 1 && !STOP_WORDS.contains(t)) tokens.add(t);
        }
        return tokens;
    }

    private static final int MIN_COMPOUND_LENGTH = 8;
    private static final int MIN_PART_LENGTH = 5;

    /**
     * Index-side expansion for German compounds: a long word such as "mietkaution" also yields its
     * prefixes and suffixes of at least five letters ("kaution", "miet..."), so a question about
     * "Kaution" finds text that only says "Mietkaution". No dictionary needed; parts that are not
     * real words are harmless because queries never contain them.
     */
    public static List<String> expandCompounds(List<String> tokens) {
        List<String> expanded = new ArrayList<>(tokens);
        for (String t : tokens) {
            if (t.length() < MIN_COMPOUND_LENGTH) continue;
            for (int len = MIN_PART_LENGTH; len < t.length(); len++) {
                expanded.add(t.substring(0, len));
                expanded.add(t.substring(t.length() - len));
            }
        }
        return expanded;
    }
}
