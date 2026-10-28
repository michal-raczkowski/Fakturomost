package dev.michalraczkowski.fakturomost.core.application;

import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import dev.michalraczkowski.fakturomost.core.domain.KsefNumber;
import java.util.Objects;

/**
 * A stored FA(3) invoice as listed by {@link InvoiceCatalog}: everything except the original
 * bytes, which stay in the store until asked for with {@link InvoiceCatalog#find}.
 *
 * @param ksefNumber or {@code null} if unknown
 */
public record InvoiceEntry(DocumentId id, String sourceName, KsefNumber ksefNumber, Invoice invoice) {

    public InvoiceEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(invoice, "invoice");
    }

    /** The entry for a stored document, or {@code null} if it is not a supported invoice. */
    static InvoiceEntry of(StoredDocument document) {
        return document.isSupported()
                ? new InvoiceEntry(document.id(), document.sourceName(), document.ksefNumber(), document.invoice())
                : null;
    }
}
