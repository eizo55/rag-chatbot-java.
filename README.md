# RAG-Chatbot in Java

Ein kleiner Retrieval-Augmented-Generation-Chatbot: Er zerlegt Textdokumente in Abschnitte, findet zu jeder Frage die passendsten Abschnitte (BM25) und lässt Claude **nur anhand dieser Quellen** antworten, mit Quellenangaben.

## Architektur

```
docs/*.md|txt ──► DocumentLoader ──► Chunker ──► Bm25Retriever (Index im Speicher)
                                                       │ top-k Abschnitte
Frage ──────────────────────────────────────────► RagChatbot ──► ClaudeClient ──► Antwort + Quellen
```

| Klasse | Aufgabe |
| --- | --- |
| `Chunker` | Teilt Text in überlappende Abschnitte (Standard: 800 Zeichen, 120 Überlappung) |
| `Tokenizer` | Kleinschreibung, Umlaute falten, deutsche/englische Stoppwörter entfernen |
| `Bm25Retriever` | Okapi-BM25-Ranking über einen invertierten Index |
| `RagChatbot` | Baut den Prompt mit nummerierten Quellen, hält kurzen Gesprächsverlauf |
| `ClaudeClient` | Ruft die Anthropic Messages API mit `java.net.http` auf |
| `LlmClient` / `Retriever` | Interfaces, damit Komponenten austauschbar und testbar sind |

## Starten

Voraussetzungen: Java 21, Maven.

```bash
mvn package
export ANTHROPIC_API_KEY=sk-ant-...        # Schlüssel aus der Anthropic Console
java -jar target/rag-chatbot-1.0.0.jar docs
```

Optional: `ANTHROPIC_MODEL` setzt das Modell (Standard: `claude-sonnet-5-5`).

### Kostenlos mit lokalem Modell (Ollama)

Ohne API-Key nutzt der Bot automatisch ein lokal laufendes [Ollama](https://ollama.com), falls vorhanden:

```bash
# 1. Ollama installieren (ollama.com) und starten (die App öffnen oder `ollama serve`)
# 2. Modell einmalig laden (ca. 2 GB)
ollama pull llama3.2
# 3. Bot starten, ohne ANTHROPIC_API_KEY
java -jar target/rag-chatbot-1.0.0.jar docs
```

Die Startmeldung zeigt, welches Modell aktiv ist. Einstellungen per Umgebungsvariable:

| Variable | Bedeutung |
| --- | --- |
| `LLM_BACKEND` | `claude`, `ollama` oder `none` erzwingt ein Backend (sonst automatisch) |
| `OLLAMA_MODEL` | Modellname, Standard `llama3.2` (z. B. `qwen2.5` oder `llama3.1:8b` für bessere deutsche Antworten, braucht mehr RAM) |
| `OLLAMA_URL` | Standard `http://localhost:11434` |

Reihenfolge der automatischen Wahl: API-Key gesetzt → Claude; sonst Ollama läuft → Ollama; sonst nur Retrieval (zeigt die gefundenen Abschnitte).

Beispielfragen zu den mitgelieferten Dokumenten:

- Wie hoch darf eine Mietkaution sein?
- Bis wann muss die Betriebskostenabrechnung beim Mieter sein?
- Was macht eine Hausverwaltung?

Eigene Dokumente: einfach `.md`/`.txt`-Dateien in einen Ordner legen und diesen als Argument übergeben. Die Beispieltexte in `docs/` sind kurze Demo-Inhalte und keine Rechtsberatung.

## Tests

```bash
mvn test
```

Die Tests laufen ohne Netzwerk (der LLM-Aufruf wird durch ein Fake ersetzt).

## Ideen zum Ausbauen

- **Embeddings statt BM25:** neue `Retriever`-Implementierung mit Vektorsuche (z. B. pgvector), ggf. hybrid mit BM25.
- **PDF-Unterstützung** im `DocumentLoader` (Apache PDFBox).
- **Streaming** der Antwort und eine kleine Weboberfläche (Spring Boot).
- **Evaluation:** Testfragen mit erwarteten Quellen, um Chunk-Größe und top-k zu vergleichen.
