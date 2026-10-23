package dev.michalraczkowski.fakturomost.core.application;

import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import dev.michalraczkowski.fakturomost.core.domain.KsefNumber;
import dev.michalraczkowski.fakturomost.core.domain.OriginalDocument;
import java.util.Objects;
import java.util.Optional;

/**
 * A document kept by Fakturomost: the original bytes plus what was learned from them.
 *
 * <p>Both FA(3) invoices and documents in formats Fakturomost cannot read yet (FA(2), FA_RR(1),
 * ...) are kept, so nothing the user imported is lost; the latter have no {@link #invoice()}.
 * Invalid documents are not stored.
 *
 * @param original   the bytes exactly as received; also defines the {@link #id()}
 * @param sourceName name it was imported under (first import wins for duplicates)
 * @param ksefNumber KSeF number from the source name, or {@code null} if unknown
 * @param invoice    extracted data, or {@code null} for an unsupported format
 */
public record StoredDocument(OriginalDocument original, String sourceName, KsefNumber ksefNumber, Invoice invoice) {

    public StoredDocument {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(sourceName, "sourceName");
    }

    public DocumentId id() {
        return original.id();
    }

    public Optional<KsefNumber> ksefNumberIfKnown() {
        return Optional.ofNullable(ksefNumber);
    }

    /** {@code true} for an FA(3) invoice, {@code false} for a stored document of another format. */
    public boolean isSupported() {
        return invoice != null;
    }
}
