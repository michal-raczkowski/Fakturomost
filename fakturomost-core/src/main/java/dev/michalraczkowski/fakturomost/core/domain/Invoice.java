package dev.michalraczkowski.fakturomost.core.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The fields Fakturomost extracts from one FA(3) invoice, for listing, filtering, details and
 * export. It is a <em>view</em> of the document, not a replacement: the original XML bytes stay the
 * source of truth and are preserved unchanged by the importer. Fields not modelled here (payment
 * terms, third parties, annotations, attachments, ...) remain available in that XML.
 *
 * <p>Identity is not part of this record. Two different documents may carry the same seller and
 * number; the importer identifies documents by the SHA-256 of their bytes.
 *
 * <p>Amounts are exactly as written — no rounding, no recomputation, no arithmetic checks. Only the
 * gross total ({@code P_15}) is always present; net and VAT totals exist only per bucket.
 *
 * @param type               {@code RodzajFaktury}
 * @param number             invoice number ({@code P_2})
 * @param issueDate          {@code P_1}
 * @param saleDate           {@code P_6}, or {@code null} (absent, or a period is given instead)
 * @param currency           {@code KodWaluty}
 * @param seller             {@code Podmiot1}
 * @param buyer              {@code Podmiot2}
 * @param vatBreakdown       stated {@code P_13_x}/{@code P_14_x} buckets, in schema order; may be empty
 * @param grossTotal         {@code P_15}; negative on corrections that reduce the amount due
 * @param items              {@code FaWiersz} rows in document order; may be empty
 * @param correctionReason   {@code PrzyczynaKorekty}, or {@code null}
 * @param correctedInvoices  {@code DaneFaKorygowanej}; empty unless this is a correction
 */
public record Invoice(
        InvoiceType type,
        String number,
        LocalDate issueDate,
        LocalDate saleDate,
        CurrencyCode currency,
        Party seller,
        Party buyer,
        List<VatBreakdown> vatBreakdown,
        BigDecimal grossTotal,
        List<InvoiceItem> items,
        String correctionReason,
        List<CorrectedInvoice> correctedInvoices) {

    public Invoice {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(buyer, "buyer");
        Objects.requireNonNull(grossTotal, "grossTotal");
        vatBreakdown = List.copyOf(vatBreakdown);
        items = List.copyOf(items);
        correctedInvoices = List.copyOf(correctedInvoices);
    }
}
