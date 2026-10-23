package dev.michalraczkowski.fakturomost.core.application;

import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import dev.michalraczkowski.fakturomost.core.domain.KsefNumber;
import java.util.Objects;

/**
 * Outcome of importing one document. Exactly one per offered document, so nothing is dropped
 * silently. Switch over it exhaustively:
 *
 * <pre>{@code
 * switch (result) {
 *     case ImportResult.Imported r -> ...
 *     case ImportResult.Duplicate r -> ...
 *     case ImportResult.Unsupported r -> ...
 *     case ImportResult.Invalid r -> ...
 *     case ImportResult.Failed r -> ...
 * }
 * }</pre>
 *
 * <p>{@code reason} texts are safe to show and log: they name rules and element paths, never values
 * from the document.
 */
public sealed interface ImportResult {

    /** Name the document was offered under (file name, path or ZIP entry). */
    String sourceName();

    /**
     * A new FA(3) invoice was stored.
     *
     * @param ksefNumber from the source name, or {@code null} if the name is not a valid KSeF number
     *                   or its seller NIP differs from the invoice's seller
     */
    record Imported(String sourceName, DocumentId id, KsefNumber ksefNumber, Invoice invoice) implements ImportResult {
        public Imported {
            Objects.requireNonNull(sourceName, "sourceName");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(invoice, "invoice");
        }
    }

    /** A byte-identical document is already stored; nothing changed. */
    record Duplicate(String sourceName, DocumentId id) implements ImportResult {
        public Duplicate {
            Objects.requireNonNull(sourceName, "sourceName");
            Objects.requireNonNull(id, "id");
        }
    }

    /**
     * Well-formed XML in a format Fakturomost cannot read (e.g. FA(2), FA_RR(1)). The original was
     * stored anyway so it can be exported later.
     *
     * @param ksefNumber from the source name if valid (not cross-checked), otherwise {@code null}
     */
    record Unsupported(String sourceName, DocumentId id, KsefNumber ksefNumber, String reason) implements ImportResult {
        public Unsupported {
            Objects.requireNonNull(sourceName, "sourceName");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Not well-formed, schema-invalid, oversized or otherwise unusable. Not stored. */
    record Invalid(String sourceName, DocumentId id, String reason) implements ImportResult {
        public Invalid {
            Objects.requireNonNull(sourceName, "sourceName");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * An unexpected error (e.g. the store failed). Not stored; trying again may succeed.
     *
     * @param cause for diagnostics; unlike {@code reason}, its message is not guaranteed free of
     *              document data, so do not log it verbatim
     */
    record Failed(String sourceName, String reason, Throwable cause) implements ImportResult {
        public Failed {
            Objects.requireNonNull(sourceName, "sourceName");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
