package dev.michalraczkowski.fakturomost.core.application;

import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import java.util.List;
import java.util.Optional;

/**
 * Port for keeping imported documents. Implemented in memory in core (CLI, tests) and by the
 * encrypted SQLite store in the desktop module.
 */
public interface DocumentStore {

    /** Whether a document with this ID is already stored. A fast pre-check only; see {@link #addIfAbsent}. */
    boolean contains(DocumentId id);

    /**
     * Stores the document unless one with the same ID exists. Must be atomic with respect to that
     * check (e.g. a primary key on the ID), because it is what makes duplicate detection reliable.
     *
     * @return {@code true} if stored, {@code false} if the ID was already present (nothing changed)
     */
    boolean addIfAbsent(StoredDocument document);

    /** The stored document with this ID, including its original bytes. */
    Optional<StoredDocument> find(DocumentId id);

    /**
     * Supported invoices that may match the query, in any order. May return more than match (the
     * caller applies {@link InvoiceQuery#matches}), never fewer.
     */
    List<InvoiceEntry> findInvoices(InvoiceQuery query);
}
