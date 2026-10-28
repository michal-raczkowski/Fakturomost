package dev.michalraczkowski.fakturomost.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonFactory;

class JsonExportTest {

    private final ExportTestData data = new ExportTestData();
    private final InvoiceExports exports = new InvoiceExports(data.store);

    @Test
    void writesAVersionedDocumentWithOneObjectPerInvoice() throws IOException {
        Map<String, Object> root = read(json(List.of(data.vat(), data.correction())));

        assertEquals("fakturomost-invoices", root.get("format"));
        assertEquals(new BigDecimal("1"), root.get("formatVersion"));
        assertEquals(2, list(root, "invoices").size());
    }

    @Test
    void writesTheInvoiceFieldsWithExactAmounts() throws IOException {
        Map<String, Object> invoice = map(list(read(json(List.of(data.vat()))), "invoices").get(0));

        assertEquals(data.vat().id().sha256Hex(), invoice.get("documentId"));
        assertEquals(ExportTestData.KSEF_VAT, invoice.get("ksefNumber"));
        assertEquals("VAT", invoice.get("type"));
        assertEquals("FV/2026/10/0042", invoice.get("number"));
        assertEquals("2026-10-20", invoice.get("issueDate"));
        assertEquals("2026-10-19", invoice.get("saleDate"));
        assertEquals("PLN", invoice.get("currency"));
        assertEquals(new BigDecimal("1440.53"), invoice.get("grossTotal"));

        Map<String, Object> seller = map(invoice.get("seller"));
        assertEquals(Map.of("type", "NIP", "countryCode", "PL", "value", "9999999999"), seller.get("taxId"));
        assertEquals("Przykładowa Spółka Testowa sp. z o.o.", seller.get("name"));
        assertEquals("00-000 Testowo", map(seller.get("address")).get("line2"));

        List<Object> buckets = list(invoice, "vatBreakdown");
        assertEquals("BASIC_RATE", map(buckets.get(0)).get("bucket"));
        assertEquals(new BigDecimal("1000"), map(buckets.get(0)).get("net"));
        assertNull(map(buckets.get(0)).get("vatInPln"));
        assertTrue(map(buckets.get(0)).containsKey("vatInPln"), "absent values are explicit nulls");

        Map<String, Object> secondItem = map(list(invoice, "items").get(1));
        assertEquals(new BigDecimal("133.66666667"), secondItem.get("unitNetPrice"));
        assertEquals(new BigDecimal("1.5"), secondItem.get("quantity"));
        assertEquals("5", secondItem.get("vatRate"));
        assertEquals(Boolean.FALSE, secondItem.get("beforeCorrection"));
    }

    @Test
    void writesCorrectionsForeignBuyersAndCorrectedInvoices() throws IOException {
        Map<String, Object> invoice = map(list(read(json(List.of(data.correction()))), "invoices").get(0));

        assertEquals(new BigDecimal("-123.00"), invoice.get("grossTotal"));
        assertEquals(Map.of("type", "EU_VAT", "countryCode", "DE", "value", "123456789"),
                map(invoice.get("buyer")).get("taxId"));
        assertEquals("Rabat posprzedażowy", invoice.get("correctionReason"));
        List<Object> corrected = list(invoice, "correctedInvoices");
        assertEquals("9999999999-20261001-0123456789AB-CD", map(corrected.get(0)).get("ksefNumber"));
        assertNull(map(corrected.get(1)).get("ksefNumber"));
        assertEquals(Boolean.TRUE, map(list(invoice, "items").get(0)).get("beforeCorrection"));
    }

    @Test
    void writesBuyersWithoutIdentifierOrAddressAsNulls() throws IOException {
        InvoiceEntry entry = ExportTestData.crafted("N/1", "S", null, List.of(), BigDecimal.ONE);

        Map<String, Object> buyer = map(map(list(read(json(List.of(entry))), "invoices").get(0)).get("buyer"));

        Map<String, Object> noId = new LinkedHashMap<>();
        noId.put("type", "NONE");
        noId.put("countryCode", null);
        noId.put("value", null);
        assertEquals(noId, buyer.get("taxId"));
        assertNull(buyer.get("name"));
        assertTrue(buyer.containsKey("address"));
        assertNull(buyer.get("address"));
    }

    @Test
    void writesAmountsInPlainNotation() throws IOException {
        InvoiceEntry entry = ExportTestData.crafted("N/1", "S", "B", List.of(), new BigDecimal("1E+3"));

        String text = new String(json(List.of(entry)), StandardCharsets.UTF_8);

        assertTrue(text.contains("1000"), text);
        assertFalse(text.contains("1E+3"), text);
    }

    @Test
    void doesNotExportLocalFilePaths() throws IOException {
        String text = new String(json(List.of(data.vat(), data.correction())), StandardCharsets.UTF_8);

        assertFalse(text.contains("secret-folder"), "source names can reveal local folders");
        assertFalse(text.contains("sourceName"));
    }

    @Test
    void writesUtf8AndEscapesQuotes() throws IOException {
        InvoiceEntry entry = ExportTestData.crafted("N/\"1\"", "Żółw \\ sp.", "B", List.of(), BigDecimal.ONE);

        Map<String, Object> invoice = map(list(read(json(List.of(entry))), "invoices").get(0));

        assertEquals("N/\"1\"", invoice.get("number"));
        assertEquals("Żółw \\ sp.", map(invoice.get("seller")).get("name"));
        assertTrue(new String(json(List.of(entry)), StandardCharsets.UTF_8).contains("Żółw"), "not \\u-escaped");
    }

    @Test
    void writesAnEmptyListForNoInvoices() throws IOException {
        assertEquals(List.of(), list(read(json(List.of())), "invoices"));
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

        exports.writeJson(List.of(data.vat()), out);

        assertFalse(closed[0]);
        assertTrue(out.size() > 0);
    }

    // --- Chosen fields (JsonOptions) -------------------------------------------------------------

    @Test
    void chosenFieldsAreWrittenFlatInOrderUnderTheChosenKeys() throws IOException {
        JsonOptions options = JsonOptions.of(ExportColumn.ISSUE_DATE, ExportColumn.INVOICE_NUMBER,
                        ExportColumn.GROSS_TOTAL, ExportColumn.SALE_DATE)
                .withKey(ExportColumn.ISSUE_DATE, "Data21")
                .withKey(ExportColumn.GROSS_TOTAL, "Kwota brutto");

        Map<String, Object> root = read(json(List.of(data.vat(), data.correction()), options));

        assertEquals("fakturomost-invoice-fields", root.get("format"));
        assertEquals(new BigDecimal("1"), root.get("formatVersion"));
        Map<String, Object> first = map(list(root, "invoices").get(0));
        assertEquals(List.of("Data21", "invoice_number", "Kwota brutto", "sale_date"), List.copyOf(first.keySet()));
        assertEquals("2026-10-20", first.get("Data21"), "renamed key, same date value");
        assertEquals(new BigDecimal("1440.53"), first.get("Kwota brutto"), "amounts stay JSON numbers");
        Map<String, Object> correction = map(list(root, "invoices").get(1));
        assertEquals(new BigDecimal("-123.00"), correction.get("Kwota brutto"));
        assertTrue(correction.containsKey("sale_date"), "not stated → explicit null, same keys everywhere");
        assertNull(correction.get("sale_date"));
    }

    @Test
    void chosenFieldsKeepTextExactlyWithoutCsvEscaping() throws IOException {
        InvoiceEntry entry = ExportTestData.crafted("=1+2", "\"Quote\" & <tag>", "Nabywca\nz nową linią",
                List.of(), new BigDecimal("1.00"));
        JsonOptions options = JsonOptions.of(ExportColumn.INVOICE_NUMBER, ExportColumn.SELLER_NAME,
                ExportColumn.BUYER_NAME);

        Map<String, Object> invoice = map(list(read(json(List.of(entry), options)), "invoices").get(0));

        assertEquals("=1+2", invoice.get("invoice_number"), "no apostrophe: JSON is not a spreadsheet");
        assertEquals("\"Quote\" & <tag>", invoice.get("seller_name"));
        assertEquals("Nabywca\nz nową linią", invoice.get("buyer_name"));
    }

    @Test
    void jsonOptionsRejectAmbiguousLayouts() {
        assertThrows(IllegalArgumentException.class, () -> JsonOptions.of(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> JsonOptions.of(ExportColumn.TYPE, ExportColumn.TYPE));
        assertThrows(IllegalArgumentException.class,
                () -> JsonOptions.of(ExportColumn.TYPE, ExportColumn.CURRENCY).withKey(ExportColumn.CURRENCY, "type"));
        assertThrows(IllegalArgumentException.class, () -> JsonOptions.defaults().withKey(ExportColumn.TYPE, " "));
        assertThrows(IllegalArgumentException.class,
                () -> JsonOptions.of(ExportColumn.TYPE).withKey(ExportColumn.CURRENCY, "waluta"));
    }

    @Test
    void defaultFieldsAreTheDefaultColumnsWithEnglishKeys() {
        JsonOptions options = JsonOptions.defaults();

        assertEquals(ExportColumn.defaults(), options.columns());
        assertEquals("issue_date", options.keys().get(ExportColumn.ISSUE_DATE));
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private byte[] json(List<InvoiceEntry> invoices, JsonOptions options) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exports.writeJson(invoices, options, out);
        return out.toByteArray();
    }

    private byte[] json(List<InvoiceEntry> invoices) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exports.writeJson(invoices, out);
        return out.toByteArray();
    }

    /** Reads JSON into maps, lists, strings, BigDecimals, booleans and nulls (jackson-core only). */
    private static Map<String, Object> read(byte[] json) {
        try (JsonParser parser = JsonFactory.builder().build().createParser(ObjectReadContext.empty(), json)) {
            parser.nextToken();
            return map(value(parser));
        }
    }

    private static Object value(JsonParser parser) {
        JsonToken token = parser.currentToken();
        switch (token) {
            case START_OBJECT -> {
                Map<String, Object> result = new LinkedHashMap<>();
                while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                    String name = parser.currentName();
                    parser.nextToken();
                    result.put(name, value(parser));
                }
                return result;
            }
            case START_ARRAY -> {
                List<Object> result = new ArrayList<>();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    result.add(value(parser));
                }
                return result;
            }
            case VALUE_STRING -> {
                return parser.getString();
            }
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> {
                return parser.getDecimalValue();
            }
            case VALUE_TRUE, VALUE_FALSE -> {
                return parser.getBooleanValue();
            }
            case VALUE_NULL -> {
                return null;
            }
            default -> throw new IllegalStateException("Unexpected token " + token);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> object, String key) {
        return (List<Object>) object.get(key);
    }
}
