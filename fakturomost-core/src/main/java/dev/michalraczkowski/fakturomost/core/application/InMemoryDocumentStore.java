package dev.michalraczkowski.fakturomost.core.application;

import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link DocumentStore} kept in memory, in import order. For the stateless CLI and for tests.
 * Thread-safe.
 */
public final class InMemoryDocumentStore implements DocumentStore {

    private final Map<DocumentId, StoredDocument> documents = new LinkedHashMap<>();

    @Override
    public synchronized boolean contains(DocumentId id) {
        return documents.containsKey(id);
    }

    @Override
    public synchronized boolean addIfAbsent(StoredDocument document) {
        Objects.requireNonNull(document, "document");
        return documents.putIfAbsent(document.id(), document) == null;
    }

    public synchronized Optional<StoredDocument> find(DocumentId id) {
        return Optional.ofNullable(documents.get(id));
    }

    /** Snapshot of all documents in import order. */
    public synchronized List<StoredDocument> all() {
        return List.copyOf(documents.values());
    }

    public synchronized int size() {
        return documents.size();
    }
}
