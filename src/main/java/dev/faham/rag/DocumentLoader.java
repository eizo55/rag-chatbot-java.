package dev.faham.rag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Reads all .txt and .md files below a directory and turns them into chunks. */
public final class DocumentLoader {
    private final Chunker chunker;

    public DocumentLoader(Chunker chunker) {
        this.chunker = chunker;
    }

    public List<Chunk> load(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            throw new IOException("Document directory not found: " + dir.toAbsolutePath());
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".txt") || name.endsWith(".md");
                    })
                    .sorted()
                    .toList();
        }
        List<Chunk> all = new ArrayList<>();
        for (Path file : files) {
            String text = Files.readString(file);
            all.addAll(chunker.chunk(dir.relativize(file).toString(), text));
        }
        return all;
    }
}
