package dev.michalraczkowski.fakturomost.core.application;

import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Use case: browse imported invoices — search with an {@link InvoiceQuery} and open one.
 *
 * <p>No paging yet: results are a list. Fine for the expected local volumes (thousands); revisit
 * with the SQLite store if needed.
 */
public final class InvoiceCatalog {

    /**
     * Newest first; ties broken by seller, invoice number and finally document ID, so the order is
     * the same on every call and in every store.
     */
    public static final Comparator<InvoiceEntry> DEFAULT_ORDER = Comparator
            .comparing((InvoiceEntry e) -> e.invoice().issueDate()).reversed()
            .thenComparing(e -> e.invoice().seller().taxId().display())
            .thenComparing(e -> e.invoice().number())
            .thenComparing(e -> e.id().sha256Hex());

    private final DocumentStore store;

    public InvoiceCatalog(DocumentStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** FA(3) invoices matching the query, in {@link #DEFAULT_ORDER}. Unsupported documents are not listed. */
    public List<InvoiceEntry> search(InvoiceQuery query) {
        Objects.requireNonNull(query, "query");
        return store.findInvoices(query).stream()
                .filter(query::matches) // stores may over-approximate; the query has the final word
                .sorted(DEFAULT_ORDER)
                .toList();
    }

    /**
     * Stored documents that are not FA(3) invoices (reported as {@code Unsupported} on import), sorted
     * by source name then ID. They have no invoice data but can be exported as original XML.
     */
    public List<OtherDocument> otherDocuments() {
        return store.findOtherDocuments().stream()
                .sorted(Comparator.comparing(OtherDocument::sourceName).thenComparing(d -> d.id().sha256Hex()))
                .toList();
    }

    /** The full stored document, including the original bytes, for details and XML export. */
    public Optional<StoredDocument> find(DocumentId id) {
        return store.find(Objects.requireNonNull(id, "id"));
    }
}
