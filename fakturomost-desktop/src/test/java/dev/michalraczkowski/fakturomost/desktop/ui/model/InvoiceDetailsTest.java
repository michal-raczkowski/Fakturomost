package dev.michalraczkowski.fakturomost.desktop.ui.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.michalraczkowski.fakturomost.core.application.InvoiceEntry;
import dev.michalraczkowski.fakturomost.core.application.InvoiceQuery;
import dev.michalraczkowski.fakturomost.core.application.StoredDocument;
import dev.michalraczkowski.fakturomost.core.domain.OriginalDocument;
import dev.michalraczkowski.fakturomost.desktop.DemoData;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Rows and details for the demo invoices; the values are the ones written in {@code demo-data/}. */
class InvoiceDetailsTest {

    private final DemoData.Services services = DemoData.Services.create();

    InvoiceDetailsTest() {
        services.importDemoFolder();
    }

    @Test
    void rowsComeInCatalogOrderWithFormattedValues() {
        List<InvoiceRow> rows = services.catalog().search(InvoiceQuery.all()).stream().map(InvoiceRow::of).toList();

        assertThat(rows).extracting(InvoiceRow::number).containsExactly("FV/2026/10/0004", "FK/2026/10/0001",
                "S/45/2026", "FV/2026/10/0003", "112/10/2026", "FV/2026/10/0002", "FV/2026/10/0001");
        InvoiceRow correction = rows.get(1);
        assertThat(correction.type()).isEqualTo("KOR");
        assertThat(correction.ksefNumber()).isEqualTo("9999999999-20261020-3A41F0C20004-99");
        assertThat(correction.gross()).isEqualByComparingTo("-492.00");
        assertThat(correction.grossText()).isEqualTo("-492,00");
        assertThat(correction.issueDateText()).isEqualTo("2026-10-20");
        assertThat(rows.getFirst().ksefNumber()).as("file not named by a KSeF number").isEmpty();
        assertThat(rows.getFirst().currency()).isEqualTo("EUR");
    }

    @Test
    void correctionShowsReasonCorrectedInvoiceAndBeforeRows() {
        InvoiceDetails details = details("FK/2026/10/0001");

        assertThat(details.title()).isEqualTo("FK/2026/10/0001 · Correction (KOR)");
        Map<String, String> fields = fields(details);
        assertThat(fields).containsEntry("Gross total", "-492,00 PLN")
                .containsEntry("Net total", "-400,00 PLN")
                .containsEntry("VAT total", "-92,00 PLN")
                .containsEntry("Sale date", "not stated")
                .containsEntry("Corrects", "FV/2026/10/0001 of 2026-10-02, KSeF 9999999999-20261002-3A41F0C20001-22");
        assertThat(fields.get("Correction reason")).startsWith("Rabat 10%");
        assertThat(details.items()).extracting(InvoiceDetails.ItemRow::position).containsExactly("1 (before)", "1");
        assertThat(details.items()).extracting(InvoiceDetails.ItemRow::unitPrice).containsExactly("4 000,00", "3 600,00");
        assertThat(details.vat()).containsExactly(
                new InvoiceDetails.VatRow("Basic rate 23% / 22% (P_13_1)", "-400,00", "-92,00", ""));
    }

    @Test
    void foreignBuyerWithEuVatNumberAndNoKsefNumber() {
        InvoiceDetails details = details("FV/2026/10/0004");

        assertThat(details.buyer().role()).isEqualTo("Buyer");
        assertThat(details.buyer().lines()).containsExactly(
                "Beispiel Design Studio GmbH", "VAT EU DE000000000", "Musterstraße 1, 10115 Berlin", "DE");
        assertThat(fields(details)).containsEntry("KSeF number", "unknown (not in the file name)")
                .containsEntry("VAT total", "not stated")
                .containsEntry("Gross total", "2 500,00 EUR");
        assertThat(details.items()).hasSize(1);
        InvoiceDetails.ItemRow item = details.items().getFirst();
        assertThat(item.vatRate()).isEqualTo("0 WDT");
        assertThat(item.quantity()).isEqualTo("1");
        assertThat(item.unit()).isEqualTo("kpl.");
        assertThat(item.amount()).isEqualTo("2 500,00");
    }

    @Test
    void advanceInvoiceWithoutLineItemsStillShowsItsTotals() {
        InvoiceDetails details = details("FV/2026/10/0003");

        assertThat(details.items()).isEmpty();
        assertThat(fields(details)).containsEntry("Net total", "10 000,00 PLN").containsEntry("Gross total", "12 300,00 PLN");
    }

    @Test
    void twoBucketsAreSummedAsStated() {
        assertThat(fields(details("FV/2026/10/0001")))
                .containsEntry("Net total", "4 500,00 PLN")
                .containsEntry("VAT total", "960,00 PLN");
    }

    @Test
    void originalXmlAvailabilityIsShown() {
        InvoiceEntry entry = entry("112/10/2026");
        InvoiceDetails details = InvoiceDetails.of(services.catalog().find(entry.id()).orElseThrow());

        Map<String, String> original = new LinkedHashMap<>();
        details.original().forEach(f -> original.put(f.label(), f.value()));
        assertThat(original.get("Original XML")).startsWith("kept unchanged, ").endsWith(" kB — export it with \"Original XML\"");
        assertThat(original.get("Imported from")).endsWith("zakupy-ksef-2026-10.zip!/8880000002-20261012-7B20C11D0006-7D.xml");
        assertThat(original.get("Document ID")).isEqualTo("SHA-256 " + entry.id().sha256Hex());
    }

    @Test
    void unsupportedDocumentsHaveNoDetails() {
        StoredDocument unsupported = new StoredDocument(OriginalDocument.of(new byte[] {'<', 'x', '/', '>'}), "x.xml", null, null);

        assertThatThrownBy(() -> InvoiceDetails.of(unsupported)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void amountsInDetailsAreNeverRounded() {
        // Gross field taken from P_15 as BigDecimal and formatted, not recomputed.
        InvoiceEntry entry = entry("FV/2026/10/0002");
        assertThat(entry.invoice().grossTotal()).isEqualByComparingTo(new BigDecimal("4428.00"));
        assertThat(fields(details("FV/2026/10/0002"))).containsEntry("Gross total", "4 428,00 PLN");
    }

    private InvoiceEntry entry(String number) {
        return services.catalog().search(InvoiceQuery.all()).stream()
                .filter(e -> e.invoice().number().equals(number)).findFirst().orElseThrow();
    }

    private InvoiceDetails details(String number) {
        return InvoiceDetails.of(services.catalog().find(entry(number).id()).orElseThrow());
    }

    private static Map<String, String> fields(InvoiceDetails details) {
        Map<String, String> result = new LinkedHashMap<>();
        details.fields().forEach(f -> result.putIfAbsent(f.label(), f.value()));
        return result;
    }
}
