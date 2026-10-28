package dev.michalraczkowski.fakturomost.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.michalraczkowski.fakturomost.core.domain.VatBreakdown;
import dev.michalraczkowski.fakturomost.core.domain.VatBucket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The opt-in columns: their values, and that the presets still write exactly the default set. */
class ExportColumnsTest {

    private final ExportTestData data = new ExportTestData();
    private final InvoiceExports exports = new InvoiceExports(data.store);

    @Test
    void presetsWriteOnlyTheDefaultColumnsInTheEarlierOrder() {
        assertEquals(ExportColumn.defaults(), CsvOptions.excelPl().columns());
        assertEquals(ExportColumn.defaults(), CsvOptions.standard().columns());
        assertEquals(ExportColumn.defaults(), Arrays.asList(ExportColumn.values()).subList(0, 14),
                "the default set comes first, so the field list starts with what the presets write");
    }

    @Test
    void everyColumnHasDistinctNonBlankHeadersInBothLanguages() {
        Set<String> polish = new HashSet<>();
        Set<String> english = new HashSet<>();
        for (ExportColumn column : ExportColumn.values()) {
            assertFalse(column.polishHeader().isBlank(), column.name());
            assertTrue(column.englishHeader().matches("[a-z0-9_]+"), column.name());
            assertTrue(polish.add(column.polishHeader()), "duplicate Polish header " + column.polishHeader());
            assertTrue(english.add(column.englishHeader()), "duplicate English header " + column.englishHeader());
        }
    }

    @Test
    void partyColumnsOfAPolishSale() {
        Map<ExportColumn, String> row = row(data.vat());

        assertEquals("PL", row.get(ExportColumn.SELLER_COUNTRY));
        assertEquals("ul. Fikcyjna 1", row.get(ExportColumn.SELLER_ADDRESS_LINE1));
        assertEquals("00-000 Testowo", row.get(ExportColumn.SELLER_ADDRESS_LINE2));
        assertEquals("NIP", row.get(ExportColumn.BUYER_TAX_ID_TYPE));
        assertEquals("PL", row.get(ExportColumn.BUYER_COUNTRY));
        assertEquals("ul. Zmyślona 2", row.get(ExportColumn.BUYER_ADDRESS_LINE1));
        assertEquals("", row.get(ExportColumn.BUYER_ADDRESS_LINE2), "no second line → empty cell");
    }

    @Test
    void perRateAmountsAreTheStatedBucketsAndEmptyWhenNotStated() {
        Map<ExportColumn, String> row = row(data.vat());

        assertEquals("1000", row.get(ExportColumn.NET_BASIC_RATE));
        assertEquals("230", row.get(ExportColumn.VAT_BASIC_RATE));
        assertEquals("200.50", row.get(ExportColumn.NET_SECOND_REDUCED_RATE));
        assertEquals("10.03", row.get(ExportColumn.VAT_SECOND_REDUCED_RATE));
        assertEquals("", row.get(ExportColumn.NET_FIRST_REDUCED_RATE), "8% not on this invoice");
        assertEquals("", row.get(ExportColumn.VAT_FIRST_REDUCED_RATE));
        assertEquals("", row.get(ExportColumn.NET_ZERO_RATE));
        assertEquals("", row.get(ExportColumn.NET_EXEMPT));
        assertEquals("", row.get(ExportColumn.NET_REVERSE_CHARGE));
        assertEquals("", row.get(ExportColumn.VAT_TOTAL_PLN), "PLN invoice states no P_14_xW");
    }

    @Test
    void zeroRateColumnAddsUpAllThreeZeroBuckets() {
        InvoiceEntry entry = ExportTestData.crafted("FV/0", "S", "B", List.of(
                new VatBreakdown(VatBucket.ZERO_DOMESTIC, new BigDecimal("10.00"), null, null),
                new VatBreakdown(VatBucket.ZERO_INTRA_EU, new BigDecimal("20.00"), null, null),
                new VatBreakdown(VatBucket.ZERO_EXPORT, new BigDecimal("30.05"), null, null),
                new VatBreakdown(VatBucket.EXEMPT, new BigDecimal("7"), null, null)), new BigDecimal("67.05"));

        Map<ExportColumn, String> row = row(entry);

        assertEquals("60.05", row.get(ExportColumn.NET_ZERO_RATE));
        assertEquals("7", row.get(ExportColumn.NET_EXEMPT));
        assertEquals("", row.get(ExportColumn.VAT_BASIC_RATE));
        assertEquals("NONE", row.get(ExportColumn.BUYER_TAX_ID_TYPE));
        assertEquals("", row.get(ExportColumn.BUYER_COUNTRY), "no address → empty, not an error");
    }

    @Test
    void correctionColumns() {
        Map<ExportColumn, String> row = row(data.correction());

        assertEquals("EU_VAT", row.get(ExportColumn.BUYER_TAX_ID_TYPE));
        assertEquals("DE", row.get(ExportColumn.BUYER_COUNTRY));
        assertEquals("-98.12", row.get(ExportColumn.VAT_TOTAL_PLN));
        assertEquals("-100.00", row.get(ExportColumn.NET_BASIC_RATE));
        assertEquals("Rabat posprzedażowy", row.get(ExportColumn.CORRECTION_REASON));
        assertEquals("FV/2026/10/0001, FV/2026/09/0099", row.get(ExportColumn.CORRECTED_INVOICE_NUMBERS));
        assertEquals("9999999999-20261001-0123456789AB-CD", row.get(ExportColumn.CORRECTED_KSEF_NUMBERS),
                "the invoice issued outside KSeF has no number and is left out");
        assertEquals("", row(data.vat()).get(ExportColumn.CORRECTED_INVOICE_NUMBERS));
    }

    @Test
    void renamedOptInColumnKeepsItsValue() throws IOException {
        CsvOptions options = CsvOptions.standard()
                .withColumns(ExportColumn.ISSUE_DATE, ExportColumn.BUYER_COUNTRY)
                .withHeader(ExportColumn.ISSUE_DATE, "Data21")
                .withHeaders(Map.of(ExportColumn.BUYER_COUNTRY, "Kraj"));

        assertEquals(List.of("Data21,Kraj", "2026-10-20,PL"), lines(List.of(data.vat()), options));
    }

    @Test
    void numberAndDateFlagsMatchTheValues() {
        assertTrue(ExportColumn.GROSS_TOTAL.isNumber());
        assertTrue(ExportColumn.NET_EXEMPT.isNumber());
        assertTrue(ExportColumn.SALE_DATE.isDate());
        assertFalse(ExportColumn.CORRECTION_REASON.isNumber());
        assertNull(ExportColumn.SALE_DATE.valueOf(data.correction()), "correction states no sale date");
    }

    // --- Helpers ---------------------------------------------------------------------------------

    /** Every column of one invoice, written with the standard preset (decimal point, no BOM). */
    private Map<ExportColumn, String> row(InvoiceEntry entry) {
        List<ExportColumn> all = Arrays.asList(ExportColumn.values());
        CsvOptions options = CsvOptions.standard().withColumns(all).withSeparator('|').withHeaderRow(false);
        String line = lines(List.of(entry), options).get(0);
        String[] cells = line.split("\\|", -1);
        assertEquals(all.size(), cells.length, line);
        return all.stream().collect(Collectors.toMap(c -> c, c -> cells[c.ordinal()]));
    }

    private List<String> lines(List<InvoiceEntry> entries, CsvOptions options) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            exports.writeCsv(entries, options, out);
            return List.of(out.toString(StandardCharsets.UTF_8).split("\r\n"));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
