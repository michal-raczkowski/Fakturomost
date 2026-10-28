package dev.michalraczkowski.fakturomost.core.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.michalraczkowski.fakturomost.core.domain.VatBreakdown;
import dev.michalraczkowski.fakturomost.core.domain.VatBucket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvExportTest {

    private final ExportTestData data = new ExportTestData();
    private final InvoiceExports exports = new InvoiceExports(data.store);

    // --- Default: Excel (PL) ---------------------------------------------------------------------

    @Test
    void excelPlPresetWritesBomSemicolonsDecimalCommasAndPolishHeaders() throws IOException {
        byte[] csv = csv(List.of(data.vat(), data.correction()), CsvOptions.excelPl());

        assertArrayEquals(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(csv, 3));
        List<String> lines = lines(csv, 3, "\r\n");
        assertEquals("Numer KSeF;Numer faktury;Rodzaj faktury;Data wystawienia;Data sprzedaży;NIP sprzedawcy;"
                + "Nazwa sprzedawcy;Identyfikator nabywcy;Nazwa nabywcy;Waluta;Netto;VAT;Brutto;ID dokumentu", lines.get(0));
        assertEquals(ExportTestData.KSEF_VAT + ";FV/2026/10/0042;VAT;2026-10-20;2026-10-19;9999999999;"
                + "Przykładowa Spółka Testowa sp. z o.o.;2222222222;Żółty Ząb Gęś s.c.;PLN;1200,50;240,03;1440,53;"
                + data.vat().id().sha256Hex(), lines.get(1));
    }

    @Test
    void writesCorrectionsAsNegativeNumbersNotAsEscapedText() throws IOException {
        List<String> lines = lines(csv(List.of(data.correction()), CsvOptions.excelPl()), 3, "\r\n");

        assertEquals(ExportTestData.KSEF_CORRECTION + ";FK/2026/10/0007;KOR;2026-10-21;;9999999999;"
                + "Przykładowa Spółka Testowa sp. z o.o.;DE123456789;Beispiel Handels GmbH;EUR;-100,00;-23,00;-123,00;"
                + data.correction().id().sha256Hex(), lines.get(1));
    }

    // --- Standard preset and custom options ------------------------------------------------------

    @Test
    void standardPresetWritesCommasDecimalPointsAndNoBom() throws IOException {
        byte[] csv = csv(List.of(data.vat()), CsvOptions.standard());

        assertEquals((byte) 'N', csv[0], "no BOM");
        List<String> lines = lines(csv, 0, "\r\n");
        assertTrue(lines.get(0).startsWith("Numer KSeF,Numer faktury,"), lines.get(0));
        assertTrue(lines.get(1).contains(",PLN,1200.50,240.03,1440.53,"), lines.get(1));
    }

    @ParameterizedTest
    @ValueSource(chars = {'|', '\t', ':', '#'})
    void acceptsAnyOtherSeparator(char separator) throws IOException {
        CsvOptions options = CsvOptions.excelPl().withSeparator(separator);

        List<String> lines = lines(csv(List.of(data.vat()), options), 3, "\r\n");

        assertTrue(lines.get(0).startsWith("Numer KSeF" + separator + "Numer faktury" + separator), lines.get(0));
        assertTrue(lines.get(1).contains(separator + "1440,53" + separator), lines.get(1));
    }

    @Test
    void acceptsCommaSeparatorWithDecimalPoint() {
        CsvOptions options = CsvOptions.excelPl().withDecimalSeparator('.').withSeparator(',');

        assertEquals(',', options.separator());
    }

    @ParameterizedTest
    @ValueSource(chars = {'"', '\n', '\r', '-', '0', '7', ',', '\u0000'})
    void rejectsSeparatorsThatWouldMakeTheFileAmbiguous(char separator) {
        // ',' is rejected here because the Excel (PL) preset uses the decimal comma.
        assertThrows(IllegalArgumentException.class, () -> CsvOptions.excelPl().withSeparator(separator));
    }

    @Test
    void rejectsOtherInvalidOptions() {
        assertThrows(IllegalArgumentException.class, () -> CsvOptions.standard().withDecimalSeparator(';'));
        assertThrows(IllegalArgumentException.class, () -> CsvOptions.standard().withLineEnding("\r"));
        assertThrows(IllegalArgumentException.class, () -> CsvOptions.standard().withColumns(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> CsvOptions.standard().withColumns(ExportColumn.GROSS_TOTAL, ExportColumn.GROSS_TOTAL));
        assertThrows(IllegalArgumentException.class, () -> CsvOptions.standard().withHeader(ExportColumn.TYPE, " "));
    }

    @Test
    void writesOnlyTheChosenColumnsInTheChosenOrder() throws IOException {
        CsvOptions options = CsvOptions.excelPl().withColumns(ExportColumn.GROSS_TOTAL, ExportColumn.INVOICE_NUMBER);

        assertEquals(List.of("Brutto;Numer faktury", "1440,53;FV/2026/10/0042"),
                lines(csv(List.of(data.vat()), options), 3, "\r\n"));
    }

    @Test
    void headersCanBeChangedOneByOneOrAllToEnglish() throws IOException {
        CsvOptions custom = CsvOptions.excelPl()
                .withColumns(ExportColumn.INVOICE_NUMBER, ExportColumn.GROSS_TOTAL)
                .withHeader(ExportColumn.INVOICE_NUMBER, "Nr dokumentu");
        CsvOptions english = custom.withEnglishHeaders();

        assertEquals("Nr dokumentu;Brutto", lines(csv(List.of(), custom), 3, "\r\n").get(0));
        assertEquals("invoice_number;gross_total", lines(csv(List.of(), english), 3, "\r\n").get(0));
        assertEquals("Numer faktury;Brutto", lines(csv(List.of(), english.withPolishHeaders()), 3, "\r\n").get(0));
    }

    @Test
    void canOmitTheHeaderRowAndUseLf() throws IOException {
        CsvOptions options = CsvOptions.standard().withHeaderRow(false).withLineEnding(CsvOptions.LF)
                .withColumns(ExportColumn.INVOICE_NUMBER);

        assertEquals("FV/2026/10/0042\nFK/2026/10/0007\n",
                new String(csv(List.of(data.vat(), data.correction()), options), StandardCharsets.UTF_8));
    }

    // --- Values ----------------------------------------------------------------------------------

    @Test
    void leavesCellsEmptyForValuesTheInvoiceDoesNotState() throws IOException {
        InvoiceEntry exemptOnly = ExportTestData.crafted("Z/1", "S", null,
                List.of(new VatBreakdown(VatBucket.EXEMPT, new BigDecimal("50"), null, null)), new BigDecimal("50"));
        InvoiceEntry noBuckets = ExportTestData.crafted("Z/2", "S", null, List.of(), BigDecimal.ZERO);
        CsvOptions options = CsvOptions.excelPl().withColumns(ExportColumn.KSEF_NUMBER, ExportColumn.SALE_DATE,
                ExportColumn.BUYER_TAX_ID, ExportColumn.BUYER_NAME, ExportColumn.NET_TOTAL, ExportColumn.VAT_TOTAL);

        List<String> lines = lines(csv(List.of(exemptOnly, noBuckets), options), 3, "\r\n");

        assertEquals(";;;;50;", lines.get(1), "exempt: net stated, VAT not");
        assertEquals(";;;;;", lines.get(2), "no buckets at all");
    }

    @Test
    void writesNumbersInPlainNotationWithTheirScale() throws IOException {
        InvoiceEntry entry = ExportTestData.crafted("N/1", "S", "B", List.of(), new BigDecimal("1E+3"));
        CsvOptions options = CsvOptions.excelPl().withColumns(ExportColumn.GROSS_TOTAL).withHeaderRow(false);

        assertEquals("1000\r\n", text(csv(List.of(entry), options), 3));
    }

    // --- Text safety -----------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK(\"http://x.example\",\"klik\")", "+48 123 456", "-17", "@SUM(A1:A9)",
            "\tTAB", "\rCR"})
    void neutralisesSpreadsheetFormulasInTextCells(String malicious) throws IOException {
        InvoiceEntry entry = ExportTestData.crafted(malicious, malicious, malicious, List.of(), BigDecimal.ONE);
        CsvOptions options = CsvOptions.excelPl().withColumns(ExportColumn.INVOICE_NUMBER, ExportColumn.SELLER_NAME)
                .withHeaderRow(false);

        List<List<String>> rows = parse(text(csv(List.of(entry), options), 3), ';');

        assertEquals(List.of(List.of("'" + malicious, "'" + malicious)), rows);
    }

    @Test
    void neutralisesFormulasInCustomHeadersToo() throws IOException {
        CsvOptions options = CsvOptions.excelPl().withColumns(ExportColumn.TYPE).withHeader(ExportColumn.TYPE, "=1+1");

        assertEquals("'=1+1", lines(csv(List.of(), options), 3, "\r\n").get(0));
    }

    @Test
    void quotesTextContainingSeparatorsQuotesAndLineBreaksSoItSurvivesARoundTrip() throws IOException {
        String tricky = "Firma \"Ala; Ola\", s.c.\r\nul. Długa 1";
        InvoiceEntry entry = ExportTestData.crafted("Q/1", tricky, tricky, List.of(), BigDecimal.TEN);
        CsvOptions options = CsvOptions.standard()
                .withColumns(ExportColumn.INVOICE_NUMBER, ExportColumn.SELLER_NAME, ExportColumn.GROSS_TOTAL);

        for (CsvOptions variant : List.of(options, options.withSeparator(';'), options.withSeparator('\t'))) {
            List<List<String>> rows = parse(text(csv(List.of(entry), variant), 0), variant.separator());
            assertEquals(List.of("Q/1", tricky, "10"), rows.get(1), "separator " + (int) variant.separator());
        }
    }

    @Test
    void doesNotCloseTheCallersStream() throws IOException {
        boolean[] closed = {false};
        ByteArrayOutputStream out = new ByteArrayOutputStream() {
            @Override
            public void close() {
                closed[0] = true;
            }
        };

        exports.writeCsv(List.of(data.vat()), CsvOptions.excelPl(), out);

        assertFalse(closed[0]);
        assertTrue(out.size() > 0, "flushed");
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private byte[] csv(List<InvoiceEntry> invoices, CsvOptions options) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exports.writeCsv(invoices, options, out);
        return out.toByteArray();
    }

    private static String text(byte[] csv, int skip) {
        return new String(csv, skip, csv.length - skip, StandardCharsets.UTF_8);
    }

    /** Splits on line endings; only valid when no cell contains a line break. */
    private static List<String> lines(byte[] csv, int skip, String lineEnding) {
        String text = text(csv, skip);
        assertTrue(text.endsWith(lineEnding), "ends with the line ending");
        return List.of(text.substring(0, text.length() - lineEnding.length()).split(lineEnding, -1));
    }

    /** Minimal RFC 4180 reader, independent of the writer, to check what a consumer would see. */
    private static List<List<String>> parse(String csv, char separator) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == separator) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                continue;
            } else if (c == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        return rows;
    }
}
