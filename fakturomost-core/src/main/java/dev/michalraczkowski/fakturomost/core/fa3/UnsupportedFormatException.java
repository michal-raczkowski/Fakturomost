package dev.michalraczkowski.fakturomost.core.fa3;

import java.io.Serial;

/**
 * Well-formed XML that is not an FA(3) invoice: another document type (e.g. FA(2), FA_RR(1)) or
 * another schema version. The document should be preserved and reported, not discarded.
 */
public final class UnsupportedFormatException extends InvoiceParseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String rootNamespace;
    private final String rootElement;

    UnsupportedFormatException(String message, String rootNamespace, String rootElement) {
        super(message);
        this.rootNamespace = rootNamespace;
        this.rootElement = rootElement;
    }

    /** Namespace URI of the document's root element, or {@code null} if it has none. */
    public String rootNamespace() {
        return rootNamespace;
    }

    /** Local name of the document's root element. */
    public String rootElement() {
        return rootElement;
    }
}
