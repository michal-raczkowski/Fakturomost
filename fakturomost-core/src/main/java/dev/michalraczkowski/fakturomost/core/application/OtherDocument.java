package dev.michalraczkowski.fakturomost.core.application;

import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import dev.michalraczkowski.fakturomost.core.domain.KsefNumber;
import java.util.Objects;

/**
 * A stored document Fakturomost cannot read as an invoice (e.g. FA(2), FA_RR(1)) — the
 * {@code Unsupported} import results. Its original bytes are kept, so it can still be exported as
 * original XML. Listed by {@link InvoiceCatalog#otherDocuments()}; no bytes, like {@link InvoiceEntry}.
 *
 * @param ksefNumber from the source name if valid, otherwise {@code null}
 * @param size       size of the original in bytes
 */
public record OtherDocument(DocumentId id, String sourceName, KsefNumber ksefNumber, int size) {

    public OtherDocument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceName, "sourceName");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
    }

    /** The entry for a stored document, or {@code null} if it is a supported invoice. */
    static OtherDocument of(StoredDocument document) {
        return document.isSupported()
                ? null
                : new OtherDocument(document.id(), document.sourceName(), document.ksefNumber(), document.original().size());
    }
}
