package dev.michalraczkowski.fakturomost.core.fa3;

import java.io.Serial;

/**
 * The document claims to be FA(3) (or cannot even be read as XML) but is broken: not well-formed,
 * too large, uses a DOCTYPE, or lacks or malforms a field Fakturomost needs.
 */
public final class InvalidInvoiceException extends InvoiceParseException {

    @Serial
    private static final long serialVersionUID = 1L;

    InvalidInvoiceException(String message) {
        super(message);
    }

    InvalidInvoiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
