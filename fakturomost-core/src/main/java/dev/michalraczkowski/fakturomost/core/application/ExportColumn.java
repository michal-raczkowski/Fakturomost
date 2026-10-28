package dev.michalraczkowski.fakturomost.core.application;

import dev.michalraczkowski.fakturomost.core.domain.Address;
import dev.michalraczkowski.fakturomost.core.domain.CorrectedInvoice;
import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import dev.michalraczkowski.fakturomost.core.domain.Party;
import dev.michalraczkowski.fakturomost.core.domain.TaxIdentifier;
import dev.michalraczkowski.fakturomost.core.domain.VatBreakdown;
import dev.michalraczkowski.fakturomost.core.domain.VatBucket;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One invoice-level value that can be exported as a column (CSV) or a field (JSON with chosen
 * fields): its default names and how its value is taken from an invoice. One row per invoice.
 *
 * <p>The first fourteen constants are the {@linkplain #defaults() default set}, written by the
 * presets; the others are opt-in. Choose, order and rename columns with
 * {@link CsvOptions#withColumns}/{@link CsvOptions#withHeader} or {@link JsonOptions}. A renamed
 * column still carries the same value — only its header text changes.
 *
 * <p>Values are exactly as written on the invoice; totals are sums of stated buckets, never
 * recomputed from line items. An empty cell ({@code null}) means "not stated".
 */
public enum ExportColumn {

    // --- Default set (order = default column order) ---------------------------------------------

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
    NET_TOTAL("Netto", "net_total", Kind.NUMBER, e -> netSum(e.invoice(), EnumSet.allOf(VatBucket.class))),
    /** Sum of the stated VAT amounts ({@code P_14_x}, invoice currency); empty if none is stated. */
    VAT_TOTAL("VAT", "vat_total", Kind.NUMBER, e -> vatSum(e.invoice(), EnumSet.allOf(VatBucket.class))),
    /** {@code P_15} as written. */
    GROSS_TOTAL("Brutto", "gross_total", Kind.NUMBER, e -> e.invoice().grossTotal()),
    DOCUMENT_ID("ID dokumentu", "document_id", Kind.TEXT, e -> e.id().sha256Hex()),

    // --- Opt-in: parties -------------------------------------------------------------------------

    /** {@code KodKraju} of the seller's address; empty without an address. */
    SELLER_COUNTRY("Kraj sprzedawcy", "seller_country", Kind.TEXT, e -> country(e.invoice().seller())),
    SELLER_ADDRESS_LINE1("Adres sprzedawcy (linia 1)", "seller_address_line1", Kind.TEXT,
            e -> line1(e.invoice().seller())),
    SELLER_ADDRESS_LINE2("Adres sprzedawcy (linia 2)", "seller_address_line2", Kind.TEXT,
            e -> line2(e.invoice().seller())),
    /** {@code NIP}, {@code EU_VAT}, {@code OTHER} or {@code NONE} — the same codes as the full JSON export. */
    BUYER_TAX_ID_TYPE("Rodzaj identyfikatora nabywcy", "buyer_tax_id_type", Kind.TEXT,
            e -> taxIdType(e.invoice().buyer().taxId())),
    BUYER_COUNTRY("Kraj nabywcy", "buyer_country", Kind.TEXT, e -> country(e.invoice().buyer())),
    BUYER_ADDRESS_LINE1("Adres nabywcy (linia 1)", "buyer_address_line1", Kind.TEXT,
            e -> line1(e.invoice().buyer())),
    BUYER_ADDRESS_LINE2("Adres nabywcy (linia 2)", "buyer_address_line2", Kind.TEXT,
            e -> line2(e.invoice().buyer())),

    // --- Opt-in: amounts per rate tier -----------------------------------------------------------

    /** {@code P_13_1}: basic rate, 23% (22% under older rules). */
    NET_BASIC_RATE("Netto 23%", "net_basic_rate", Kind.NUMBER, e -> netSum(e.invoice(), EnumSet.of(VatBucket.BASIC_RATE))),
    /** {@code P_14_1}. */
    VAT_BASIC_RATE("VAT 23%", "vat_basic_rate", Kind.NUMBER, e -> vatSum(e.invoice(), EnumSet.of(VatBucket.BASIC_RATE))),
    /** {@code P_13_2}: first reduced rate, 8% (7% under older rules). */
    NET_FIRST_REDUCED_RATE("Netto 8%", "net_first_reduced_rate", Kind.NUMBER,
            e -> netSum(e.invoice(), EnumSet.of(VatBucket.FIRST_REDUCED_RATE))),
    /** {@code P_14_2}. */
    VAT_FIRST_REDUCED_RATE("VAT 8%", "vat_first_reduced_rate", Kind.NUMBER,
            e -> vatSum(e.invoice(), EnumSet.of(VatBucket.FIRST_REDUCED_RATE))),
    /** {@code P_13_3}: second reduced rate, 5%. */
    NET_SECOND_REDUCED_RATE("Netto 5%", "net_second_reduced_rate", Kind.NUMBER,
            e -> netSum(e.invoice(), EnumSet.of(VatBucket.SECOND_REDUCED_RATE))),
    /** {@code P_14_3}. */
    VAT_SECOND_REDUCED_RATE("VAT 5%", "vat_second_reduced_rate", Kind.NUMBER,
            e -> vatSum(e.invoice(), EnumSet.of(VatBucket.SECOND_REDUCED_RATE))),
    /** {@code P_13_6_1} + {@code P_13_6_2} + {@code P_13_6_3}: all 0% buckets (domestic, intra-EU, export). */
    NET_ZERO_RATE("Netto 0%", "net_zero_rate", Kind.NUMBER, e -> netSum(e.invoice(),
            EnumSet.of(VatBucket.ZERO_DOMESTIC, VatBucket.ZERO_INTRA_EU, VatBucket.ZERO_EXPORT))),
    /** {@code P_13_7}. */
    NET_EXEMPT("Netto zw", "net_exempt", Kind.NUMBER, e -> netSum(e.invoice(), EnumSet.of(VatBucket.EXEMPT))),
    /** {@code P_13_10}: domestic reverse charge. */
    NET_REVERSE_CHARGE("Netto odwrotne obciążenie", "net_reverse_charge", Kind.NUMBER,
            e -> netSum(e.invoice(), EnumSet.of(VatBucket.REVERSE_CHARGE))),
    /** Sum of the stated {@code P_14_xW} (VAT converted to PLN, foreign-currency invoices); empty if none. */
    VAT_TOTAL_PLN("VAT w PLN", "vat_total_pln", Kind.NUMBER, e -> vatInPlnSum(e.invoice())),

    // --- Opt-in: corrections ---------------------------------------------------------------------

    /** {@code PrzyczynaKorekty}. */
    CORRECTION_REASON("Przyczyna korekty", "correction_reason", Kind.TEXT, e -> e.invoice().correctionReason()),
    /** {@code NrFaKorygowanej} of every corrected invoice, joined with {@code ", "}. */
    CORRECTED_INVOICE_NUMBERS("Numery faktur korygowanych", "corrected_invoice_numbers", Kind.TEXT,
            e -> joined(e.invoice(), CorrectedInvoice::number)),
    /** {@code NrKSeFFaKorygowanej} of every corrected invoice that has one, joined with {@code ", "}. */
    CORRECTED_KSEF_NUMBERS("Numery KSeF faktur korygowanych", "corrected_ksef_numbers", Kind.TEXT,
            e -> joined(e.invoice(), CorrectedInvoice::ksefNumber));

    /** How a value is written. Only {@code TEXT} values are escaped against formula injection. */
    enum Kind { TEXT, DATE, NUMBER }

    private final String polishHeader;
    private final String englishHeader;
    private final Kind kind;
    private final Function<InvoiceEntry, Object> value;

    ExportColumn(String polishHeader, String englishHeader, Kind kind, Function<InvoiceEntry, Object> value) {
        this.polishHeader = polishHeader;
        this.englishHeader = englishHeader;
        this.kind = kind;
        this.value = value;
    }

    /** The columns the presets write, in their default order (the CSV layout of earlier versions). */
    public static List<ExportColumn> defaults() {
        return List.of(KSEF_NUMBER, INVOICE_NUMBER, TYPE, ISSUE_DATE, SALE_DATE, SELLER_TAX_ID, SELLER_NAME,
                BUYER_TAX_ID, BUYER_NAME, CURRENCY, NET_TOTAL, VAT_TOTAL, GROSS_TOTAL, DOCUMENT_ID);
    }

    public String polishHeader() {
        return polishHeader;
    }

    /** Machine-friendly {@code snake_case} name; the default key of {@link JsonOptions}. */
    public String englishHeader() {
        return englishHeader;
    }

    /** {@code true} for amounts (written as numbers, never quoted or escaped). */
    public boolean isNumber() {
        return kind == Kind.NUMBER;
    }

    /** {@code true} for dates (written as {@code yyyy-MM-dd}). */
    public boolean isDate() {
        return kind == Kind.DATE;
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

    private static BigDecimal netSum(Invoice invoice, Set<VatBucket> buckets) {
        return invoice.vatBreakdown().stream()
                .filter(b -> buckets.contains(b.bucket()))
                .map(VatBreakdown::net)
                .reduce(BigDecimal::add)
                .orElse(null);
    }

    private static BigDecimal vatSum(Invoice invoice, Set<VatBucket> buckets) {
        return invoice.vatBreakdown().stream()
                .filter(b -> buckets.contains(b.bucket()))
                .map(VatBreakdown::vat)
                .filter(Objects::nonNull)
                .reduce(BigDecimal::add)
                .orElse(null);
    }

    private static BigDecimal vatInPlnSum(Invoice invoice) {
        return invoice.vatBreakdown().stream()
                .map(VatBreakdown::vatInPln)
                .filter(Objects::nonNull)
                .reduce(BigDecimal::add)
                .orElse(null);
    }

    private static String country(Party party) {
        Address address = party.address();
        return address == null ? null : address.countryCode();
    }

    private static String line1(Party party) {
        Address address = party.address();
        return address == null ? null : address.line1();
    }

    private static String line2(Party party) {
        Address address = party.address();
        return address == null ? null : address.line2();
    }

    static String taxIdType(TaxIdentifier taxId) {
        return switch (taxId) {
            case TaxIdentifier.Nip nip -> "NIP";
            case TaxIdentifier.EuVatNumber eu -> "EU_VAT";
            case TaxIdentifier.OtherTaxId other -> "OTHER";
            case TaxIdentifier.None none -> "NONE";
        };
    }

    private static String joined(Invoice invoice, Function<CorrectedInvoice, String> part) {
        String result = invoice.correctedInvoices().stream()
                .map(part)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        return result.isEmpty() ? null : result;
    }
}
