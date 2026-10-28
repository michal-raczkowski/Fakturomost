package dev.michalraczkowski.fakturomost.desktop.ui.model;

import dev.michalraczkowski.fakturomost.core.application.StoredDocument;
import dev.michalraczkowski.fakturomost.core.domain.CorrectedInvoice;
import dev.michalraczkowski.fakturomost.core.domain.CurrencyCode;
import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import dev.michalraczkowski.fakturomost.core.domain.InvoiceItem;
import dev.michalraczkowski.fakturomost.core.domain.Party;
import dev.michalraczkowski.fakturomost.core.domain.VatBreakdown;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Everything the details pane shows for one invoice (master prompt §12), as display text. Amounts
 * exactly as written — totals are only summed from the stated buckets, never recomputed from items.
 *
 * @param title    e.g. {@code FV/2026/10/0001 · VAT}
 * @param fields   invoice-level facts in display order (dates, KSeF number, totals, correction data)
 * @param seller   seller block
 * @param buyer    buyer block
 * @param items    line items in document order; may be empty (e.g. advance invoices)
 * @param vat      stated tax buckets; may be empty
 * @param original where the original XML is and that it is kept unchanged
 */
public record InvoiceDetails(String title, List<Field> fields, PartyView seller, PartyView buyer,
                             List<ItemRow> items, List<VatRow> vat, List<Field> original) {

    public record Field(String label, String value) {
        public Field {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(value, "value");
        }
    }

    /** @param lines name, tax ID, then the address lines */
    public record PartyView(String role, List<String> lines) {
        public PartyView {
            lines = List.copyOf(lines);
        }
    }

    /**
     * @param position  line number, with {@code (before)} for rows that show the state before a correction
     * @param unitPrice net unit price, or the gross one marked {@code (gross)} when only that is given
     * @param amount    net amount, or the gross one marked {@code (gross)} when only that is given
     */
    public record ItemRow(String position, String description, String quantity, String unit, String unitPrice,
                          String amount, String vatRate) {
    }

    public record VatRow(String bucket, String net, String vat, String vatInPln) {
    }

    public InvoiceDetails {
        fields = List.copyOf(fields);
        items = List.copyOf(items);
        vat = List.copyOf(vat);
        original = List.copyOf(original);
    }

    /**
     * @throws IllegalArgumentException for a stored document of an unsupported format (the table
     *                                  lists FA(3) invoices only, so this means a programming error)
     */
    public static InvoiceDetails of(StoredDocument document) {
        Invoice invoice = document.invoice();
        if (invoice == null) {
            throw new IllegalArgumentException("Not a supported invoice");
        }
        CurrencyCode currency = invoice.currency();
        String title = Formats.singleLine(invoice.number()) + " · " + Formats.type(invoice.type());

        List<Field> fields = new ArrayList<>();
        fields.add(new Field("Issue date", Formats.date(invoice.issueDate())));
        fields.add(new Field("Sale date", invoice.saleDate() == null ? "not stated" : Formats.date(invoice.saleDate())));
        fields.add(new Field("KSeF number", document.ksefNumber() == null
                ? "unknown (not in the file name)" : document.ksefNumber().value()));
        fields.add(new Field("Currency", currency.value()));
        fields.add(new Field("Net total", total(invoice.vatBreakdown().stream().map(VatBreakdown::net).toList(), currency)));
        fields.add(new Field("VAT total", total(invoice.vatBreakdown().stream().map(VatBreakdown::vat).toList(), currency)));
        fields.add(new Field("Gross total", Formats.money(invoice.grossTotal(), currency)));
        if (invoice.correctionReason() != null) {
            fields.add(new Field("Correction reason", Formats.singleLine(invoice.correctionReason())));
        }
        for (CorrectedInvoice corrected : invoice.correctedInvoices()) {
            fields.add(new Field("Corrects", Formats.singleLine(corrected.number()) + " of "
                    + Formats.date(corrected.issueDate())
                    + (corrected.ksefNumber() == null ? " (issued outside KSeF)" : ", KSeF " + corrected.ksefNumber())));
        }

        List<ItemRow> items = invoice.items().stream().map(InvoiceDetails::item).toList();
        List<VatRow> vat = invoice.vatBreakdown().stream()
                .map(b -> new VatRow(Formats.vatBucket(b.bucket()), Formats.amount(b.net()),
                        Formats.amount(b.vat()), Formats.amount(b.vatInPln())))
                .toList();

        List<Field> original = List.of(
                new Field("Original XML", "kept unchanged, " + Formats.size(document.original().size())
                        + " — export it with \"Original XML\""),
                new Field("Imported from", Formats.singleLine(document.sourceName())),
                new Field("Document ID", "SHA-256 " + document.id().sha256Hex()));

        return new InvoiceDetails(title, fields, party("Seller", invoice.seller()), party("Buyer", invoice.buyer()),
                items, vat, original);
    }

    private static PartyView party(String role, Party party) {
        List<String> lines = new ArrayList<>();
        lines.add(Formats.singleLine(Formats.name(party)));
        lines.add(Formats.taxId(party.taxId()));
        Formats.address(party.address()).forEach(line -> lines.add(Formats.singleLine(line)));
        return new PartyView(role, lines);
    }

    private static ItemRow item(InvoiceItem item) {
        String position = item.lineNumber() + (item.beforeCorrection() ? " (before)" : "");
        String unitPrice = item.unitNetPrice() != null ? Formats.number(item.unitNetPrice())
                : item.unitGrossPrice() != null ? Formats.number(item.unitGrossPrice()) + " (gross)" : "";
        String amount = item.netAmount() != null ? Formats.amount(item.netAmount())
                : item.grossAmount() != null ? Formats.amount(item.grossAmount()) + " (gross)" : "";
        String rate = item.vatRate() != null ? Formats.vatRate(item.vatRate())
                : item.ossVatRatePercent() != null ? Formats.number(item.ossVatRatePercent()) + "% (OSS)" : "";
        return new ItemRow(position, Formats.singleLine(item.description()), Formats.number(item.quantity()),
                Formats.singleLine(item.unit()), unitPrice, amount, rate);
    }

    /** Sum of the stated values, or "not stated" when none is; never computed from line items. */
    private static String total(List<BigDecimal> stated, CurrencyCode currency) {
        return stated.stream().filter(Objects::nonNull).reduce(BigDecimal::add)
                .map(sum -> Formats.money(sum, currency))
                .orElse("not stated");
    }
}
