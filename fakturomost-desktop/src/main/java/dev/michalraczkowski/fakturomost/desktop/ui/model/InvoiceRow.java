package dev.michalraczkowski.fakturomost.desktop.ui.model;

import dev.michalraczkowski.fakturomost.core.application.InvoiceEntry;
import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One line of the invoice table: the catalog entry plus its display texts. Sortable columns sort by
 * the raw values ({@link #issueDate()}, {@link #gross()}), never by the formatted text.
 */
public record InvoiceRow(
        InvoiceEntry entry,
        String number,
        String type,
        String ksefNumber,
        String seller,
        String buyer,
        LocalDate issueDate,
        BigDecimal gross,
        String grossText,
        String currency) {

    public InvoiceRow {
        Objects.requireNonNull(entry, "entry");
    }

    public static InvoiceRow of(InvoiceEntry entry) {
        Invoice invoice = entry.invoice();
        return new InvoiceRow(
                entry,
                Formats.singleLine(invoice.number()),
                invoice.type().code(),
                entry.ksefNumber() == null ? "" : entry.ksefNumber().value(),
                Formats.singleLine(Formats.name(invoice.seller())),
                Formats.singleLine(Formats.name(invoice.buyer())),
                invoice.issueDate(),
                invoice.grossTotal(),
                Formats.amount(invoice.grossTotal()),
                invoice.currency().value());
    }

    public String issueDateText() {
        return Formats.date(issueDate);
    }
}
