package dev.michalraczkowski.fakturomost.core.fa3;

import java.io.Serial;

/**
 * A document could not be turned into an invoice. Exactly two kinds, which the importer maps to
 * its {@code Unsupported} and {@code Invalid} outcomes.
 *
 * <p>Messages name elements and paths, never element values, so they are safe to log and show.
 */
public abstract sealed class InvoiceParseException extends Exception
        permits UnsupportedFormatException, InvalidInvoiceException {

    @Serial
    private static final long serialVersionUID = 1L;

    InvoiceParseException(String message) {
        super(message);
    }

    InvoiceParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
