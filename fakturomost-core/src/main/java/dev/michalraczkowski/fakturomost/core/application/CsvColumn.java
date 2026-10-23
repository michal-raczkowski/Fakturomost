package dev.michalraczkowski.fakturomost.core.application;

import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import dev.michalraczkowski.fakturomost.core.domain.VatBreakdown;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Function;

/**
 * A column of the CSV export: its default headers and how its value is taken from an invoice.
 * One row per invoice. Choose and order columns with {@link CsvOptions#withColumns}.
 */
public enum CsvColumn {

    KSEF_NUMBER("Numer KSeF", "ksef_number", Kind.TEXT,
            e -> e.ksefNumber() == null ? null : e.ksefNumber().value()),
    INVOICE_NUMBER("Numer faktury", "invoice_number", Kind.TEXT, e -> e.invoice().number()),
    /** FA(3) code: {@code VAT}, {@code KOR}, {@code ZAL}, ... */
    TYPE("Rodzaj faktury", "type", Kind.TEXT, e -> e.invoice().type().code()),
    ISSUE_DATE("Data wystawienia", "issue_date", Kind.DATE, e -> e.invoice().issueDate()),
    SALE_DATE("Data sprzedaży", "sale_date", Kind.DATE, e -> e.invoice().saleDate()),
    SELLER_TAX_ID("NIP sprzedawcy", "seller_tax_id", Kind.TEXT, e -> e.invoice().seller().taxId().display()),
    SELLER_NAME("Nazwa sprzedawcy", "seller_name", Kind.TEXT, e -> e.invoice().seller().name()),
    /** NIP, EU VAT number with prefix, other tax ID, or empty. */
    BUYER_TAX_ID("Identyfikator nabywcy", "buyer_tax_id", Kind.TEXT, e -> e.invoice().buyer().taxId().display()),
    BUYER_NAME("Nazwa nabywcy", "buyer_name", Kind.TEXT, e -> e.invoice().buyer().name()),
    CURRENCY("Waluta", "currency", Kind.TEXT, e -> e.invoice().currency().value()),
    /** Sum of the stated net buckets ({@code P_13_x}); empty if the invoice states none. */
    NET_TOTAL("Netto", "net_total", Kind.NUMBER, e -> netTotal(e.invoice())),
    /** Sum of the stated VAT amounts ({@code P_14_x}, invoice currency); empty if none is stated. */
    VAT_TOTAL("VAT", "vat_total", Kind.NUMBER, e -> vatTotal(e.invoice())),
    /** {@code P_15} as written. */
    GROSS_TOTAL("Brutto", "gross_total", Kind.NUMBER, e -> e.invoice().grossTotal()),
    DOCUMENT_ID("ID dokumentu", "document_id", Kind.TEXT, e -> e.id().sha256Hex());

    /** How a value is written. Only {@code TEXT} values are escaped against formula injection. */
    enum Kind { TEXT, DATE, NUMBER }

    private final String polishHeader;
    private final String englishHeader;
    private final Kind kind;
    private final Function<InvoiceEntry, Object> value;

    CsvColumn(String polishHeader, String englishHeader, Kind kind, Function<InvoiceEntry, Object> value) {
        this.polishHeader = polishHeader;
        this.englishHeader = englishHeader;
        this.kind = kind;
        this.value = value;
    }

    public String polishHeader() {
        return polishHeader;
    }

    public String englishHeader() {
        return englishHeader;
    }

    Kind kind() {
        return kind;
    }

    /** {@code String}, {@code LocalDate}, {@code BigDecimal}, or {@code null} for an empty cell. */
    Object valueOf(InvoiceEntry entry) {
        Object result = value.apply(entry);
        assert result == null || result instanceof String || result instanceof LocalDate || result instanceof BigDecimal;
        return result;
    }

    private static BigDecimal netTotal(Invoice invoice) {
        return invoice.vatBreakdown().stream()
                .map(VatBreakdown::net)
                .reduce(BigDecimal::add)
                .orElse(null);
    }

    private static BigDecimal vatTotal(Invoice invoice) {
        return invoice.vatBreakdown().stream()
                .map(VatBreakdown::vat)
                .filter(Objects::nonNull)
                .reduce(BigDecimal::add)
                .orElse(null);
    }
}
