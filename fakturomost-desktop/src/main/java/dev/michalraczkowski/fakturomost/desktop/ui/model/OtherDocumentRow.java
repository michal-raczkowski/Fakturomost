package dev.michalraczkowski.fakturomost.desktop.ui.model;

import dev.michalraczkowski.fakturomost.core.application.OtherDocument;
import java.util.Objects;

/** A line of the "documents in other formats" window: an Unsupported document that is kept. */
public record OtherDocumentRow(OtherDocument document, String source, String ksefNumber, String size) {

    public OtherDocumentRow {
        Objects.requireNonNull(document, "document");
    }

    public static OtherDocumentRow of(OtherDocument document) {
        return new OtherDocumentRow(document,
                Formats.singleLine(document.sourceName()),
                document.ksefNumber() == null ? "" : document.ksefNumber().value(),
                Formats.size(document.size()));
    }

    /** {@code 1 document in another format} — the status bar link; empty for none. */
    public static String linkText(int count) {
        return count == 0 ? "" : count + (count == 1 ? " document" : " documents") + " in another format";
    }

    /** Shown above the list. */
    public static final String EXPLANATION =
            "Fakturomost keeps these files unchanged but cannot read them as FA(3) invoices "
                    + "(e.g. older FA(2) or FA_RR invoices), so they are not in the invoice table. "
                    + "You can export the originals as XML.";
}
