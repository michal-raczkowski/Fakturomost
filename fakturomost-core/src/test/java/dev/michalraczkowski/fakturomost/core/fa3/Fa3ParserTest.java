package dev.michalraczkowski.fakturomost.core.fa3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.michalraczkowski.fakturomost.core.domain.Address;
import dev.michalraczkowski.fakturomost.core.domain.CorrectedInvoice;
import dev.michalraczkowski.fakturomost.core.domain.CurrencyCode;
import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import dev.michalraczkowski.fakturomost.core.domain.InvoiceItem;
import dev.michalraczkowski.fakturomost.core.domain.InvoiceType;
import dev.michalraczkowski.fakturomost.core.domain.TaxIdentifier;
import dev.michalraczkowski.fakturomost.core.domain.VatBreakdown;
import dev.michalraczkowski.fakturomost.core.domain.VatBucket;
import dev.michalraczkowski.fakturomost.core.domain.VatRate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Fa3ParserTest {

    private final Fa3Parser parser = new Fa3Parser();

    /**
     * Skips XSD validation, to reach the extraction code's own checks with documents the schema
     * would reject first. Schema behaviour itself is covered by {@code Fa3SchemaValidationTest}.
     */
    private final Fa3Parser extractor = Fa3Parser.withoutSchemaValidation();

    // --- Full fixtures -------------------------------------------------------------------------

    @Test
    void parsesInvoiceShapedLikeAKsefDownload() throws Exception {
        Invoice invoice = parser.parse(fixture("ksef-style-vat.xml"));

        assertEquals(InvoiceType.VAT, invoice.type());
        assertEquals("FV/2026/10/0042", invoice.number());
        assertEquals(LocalDate.of(2026, 10, 20), invoice.issueDate());
        assertEquals(LocalDate.of(2026, 10, 19), invoice.saleDate());
        assertEquals(CurrencyCode.PLN, invoice.currency());

        assertEquals(new TaxIdentifier.Nip("9999999999"), invoice.seller().taxId());
        assertEquals("Przykładowa Spółka Testowa sp. z o.o.", invoice.seller().name());
        assertEquals(new Address("PL", "ul. Fikcyjna 1", "00-000 Testowo"), invoice.seller().address());
        assertEquals(new TaxIdentifier.Nip("2222222222"), invoice.buyer().taxId());
        assertEquals("Żółty Ząb Gęś s.c.", invoice.buyer().name());
        assertEquals(new Address("PL", "ul. Zmyślona 2", null), invoice.buyer().address());

        assertEquals(List.of(
                new VatBreakdown(VatBucket.BASIC_RATE, dec("1000"), dec("230"), null),
                new VatBreakdown(VatBucket.SECOND_REDUCED_RATE, dec("200.50"), dec("10.03"), null)),
                invoice.vatBreakdown());
        assertEquals(dec("1440.53"), invoice.grossTotal());

        assertEquals(List.of(
                new InvoiceItem(1, "Usługa wdrożeniowa – etap 1", "h", dec("8"), dec("125"), null,
                        dec("1000"), null, null, VatRate.RATE_23, null, false),
                new InvoiceItem(2, "Książka „Zażółć gęślą jaźń”", "szt.", dec("1.5"), dec("133.66666667"), null,
                        dec("200.50"), null, null, VatRate.RATE_5, null, false)),
                invoice.items());

        assertNull(invoice.correctionReason());
        assertTrue(invoice.correctedInvoices().isEmpty());
    }

    @Test
    void parsesPrettyPrintedPrefixedCorrection() throws Exception {
        Invoice invoice = parser.parse(fixture("correction-eur-prefixed.xml"));

        assertEquals(InvoiceType.CORRECTION, invoice.type());
        assertTrue(invoice.type().isCorrection());
        assertEquals("FK/2026/10/0007", invoice.number(), "token whitespace is collapsed");
        assertNull(invoice.saleDate());
        assertEquals(new CurrencyCode("EUR"), invoice.currency());

        // Buyer comes from Podmiot2 only, never from the Podmiot3 / Podmiot2K decoys.
        assertEquals(new TaxIdentifier.EuVatNumber("DE", "123456789"), invoice.buyer().taxId());
        assertEquals("Beispiel Handels GmbH", invoice.buyer().name());
        assertEquals("DE123456789", invoice.buyer().taxId().display());

        assertEquals(List.of(new VatBreakdown(VatBucket.BASIC_RATE, dec("-100.00"), dec("-23.00"), dec("-98.12"))),
                invoice.vatBreakdown());
        assertEquals(dec("-123.00"), invoice.grossTotal());

        assertEquals("Rabat posprzedażowy", invoice.correctionReason());
        assertEquals(List.of(
                new CorrectedInvoice(LocalDate.of(2026, 10, 1), "FV/2026/10/0001",
                        "9999999999-20261001-0123456789AB-CD"),
                new CorrectedInvoice(LocalDate.of(2026, 9, 15), "FV/2026/09/0099", null)),
                invoice.correctedInvoices());

        // Line numbers repeat for "before" and "after" rows; both are kept, in order.
        assertEquals(2, invoice.items().size());
        assertEquals(1, invoice.items().get(0).lineNumber());
        assertEquals(1, invoice.items().get(1).lineNumber());
        assertTrue(invoice.items().get(0).beforeCorrection());
        assertFalse(invoice.items().get(1).beforeCorrection());
        assertEquals(dec("500"), invoice.items().get(0).netAmount());
        assertEquals(dec("400"), invoice.items().get(1).netAmount());
    }

    @Test
    void doesNotModifyTheInputBytes() throws Exception {
        byte[] original = fixture("ksef-style-vat.xml");
        byte[] copy = original.clone();

        parser.parse(original);

        assertTrue(Arrays.equals(copy, original));
    }

    // --- Amounts are kept exactly as written ---------------------------------------------------

    @Test
    void keepsTheScaleOfAmountsAsWritten() throws Exception {
        Invoice invoice = parser.parse(TestInvoiceXml.minimal()
                .totals("<P_13_1>2051</P_13_1><P_14_1>0.10</P_14_1><P_15>2051.1</P_15>").bytes());

        VatBreakdown basic = invoice.vatBreakdown().get(0);
        assertEquals(0, basic.net().scale());
        assertEquals(2, basic.vat().scale());
        assertEquals(dec("2051.1"), invoice.grossTotal());
    }

    @Test
    void doesNotCheckThatBucketsAddUpToTheGrossTotal() throws Exception {
        Invoice invoice = parser.parse(TestInvoiceXml.minimal()
                .totals("<P_13_1>1666.66</P_13_1><P_14_1>383.33</P_14_1><P_15>2051</P_15>").bytes());

        assertEquals(dec("2051"), invoice.grossTotal());
    }

    @Test
    void readsBucketsWithoutVatFieldsAndOptionalOssVat() throws Exception {
        Invoice invoice = parser.parse(TestInvoiceXml.minimal()
                .totals("<P_13_5>50</P_13_5><P_13_6_2>10</P_13_6_2><P_13_7>20</P_13_7>"
                        + "<P_13_10>30</P_13_10><P_13_11>40</P_13_11><P_15>150</P_15>")
                .bytes());

        assertEquals(List.of(
                new VatBreakdown(VatBucket.OSS_PROCEDURE, dec("50"), null, null),
                new VatBreakdown(VatBucket.ZERO_INTRA_EU, dec("10"), null, null),
                new VatBreakdown(VatBucket.EXEMPT, dec("20"), null, null),
                new VatBreakdown(VatBucket.REVERSE_CHARGE, dec("30"), null, null),
                new VatBreakdown(VatBucket.MARGIN, dec("40"), null, null)),
                invoice.vatBreakdown());
    }

    @Test
    void acceptsAnInvoiceWithOnlyAGrossTotal() throws Exception {
        Invoice invoice = parser.parse(TestInvoiceXml.minimal().totals("<P_15>0</P_15>").items("").bytes());

        assertTrue(invoice.vatBreakdown().isEmpty());
        assertTrue(invoice.items().isEmpty());
        assertEquals(dec("0"), invoice.grossTotal());
    }

    // --- Line items ----------------------------------------------------------------------------

    @Test
    void readsGrossFieldsOfMarginSchemeItems() throws Exception {
        Invoice invoice = parser.parse(TestInvoiceXml.minimal()
                .totals("<P_13_11>15000</P_13_11><P_15>15000</P_15>")
                .items("<FaWiersz><NrWierszaFa>1</NrWierszaFa><P_7>Samochód używany</P_7><P_8A>szt.</P_8A>"
                        + "<P_8B>1</P_8B><P_9B>15000</P_9B><P_11A>15000</P_11A></FaWiersz>")
                .bytes());

        InvoiceItem item = invoice.items().get(0);
        assertNull(item.unitNetPrice());
        assertNull(item.netAmount());
        assertNull(item.vatRate());
        assertEquals(dec("15000"), item.unitGrossPrice());
        assertEquals(dec("15000"), item.grossAmount());
    }

    @Test
    void readsNonNumericVatRatesAndOssRate() throws Exception {
        Invoice invoice = parser.parse(TestInvoiceXml.minimal()
                .items("<FaWiersz><NrWierszaFa>1</NrWierszaFa><P_11>10</P_11><P_12>zw</P_12></FaWiersz>"
                        + "<FaWiersz><NrWierszaFa>2</NrWierszaFa><P_11>10</P_11><P_12>0 WDT</P_12></FaWiersz>"
                        + "<FaWiersz><NrWierszaFa>3</NrWierszaFa><P_11>10</P_11><P_12>np II</P_12></FaWiersz>"
                        + "<FaWiersz><NrWierszaFa>4</NrWierszaFa><P_11>10</P_11><P_12_XII>19.5</P_12_XII>"
                        + "</FaWiersz>")
                .bytes());

        List<InvoiceItem> items = invoice.items();
        assertEquals(VatRate.EXEMPT, items.get(0).vatRate());
        assertTrue(items.get(0).vatRate().percent().isEmpty());
        assertEquals(VatRate.ZERO_INTRA_EU, items.get(1).vatRate());
        assertEquals(dec("0"), items.get(1).vatRate().percent().orElseThrow());
        assertEquals(VatRate.NOT_SUBJECT_ART_100, items.get(2).vatRate());
        assertNull(items.get(3).vatRate());
        assertEquals(dec("19.5"), items.get(3).ossVatRatePercent());
    }

    // --- Buyer identification choice -----------------------------------------------------------

    @Test
    void readsBuyerWithoutAnyIdentifierNameOrAddress() throws Exception {
        Invoice invoice = parser.parse(TestInvoiceXml.minimal()
                .buyerIdentification("<BrakID>1</BrakID>").buyerAddress("").bytes());

        assertEquals(new TaxIdentifier.None(), invoice.buyer().taxId());
        assertNull(invoice.buyer().name());
        assertNull(invoice.buyer().address());
    }

    @Test
    void readsBuyerWithOtherTaxIdWithAndWithoutCountry() throws Exception {
        Invoice withCountry = parser.parse(TestInvoiceXml.minimal()
                .buyerIdentification("<KodKraju>US</KodKraju><NrID>98-7654321</NrID><Nazwa>Example Inc.</Nazwa>")
                .bytes());
        Invoice withoutCountry = parser.parse(TestInvoiceXml.minimal()
                .buyerIdentification("<NrID>X-1</NrID>").bytes());

        assertEquals(new TaxIdentifier.OtherTaxId("US", "98-7654321"), withCountry.buyer().taxId());
        assertEquals(new TaxIdentifier.OtherTaxId(null, "X-1"), withoutCountry.buyer().taxId());
    }

    // --- Values the schema allows but Java might not -------------------------------------------

    @Test
    void acceptsSchemaValidCurrencyThatTheJdkDoesNotKnow() throws Exception {
        Invoice invoice = parser.parse(TestInvoiceXml.minimal().currency("JEP").bytes());

        assertEquals(new CurrencyCode("JEP"), invoice.currency());
        assertTrue(invoice.currency().toCurrency().isEmpty());
    }

    @Test
    void doesNotApplyBusinessRulesToDates() throws Exception {
        // KSeF itself accepted a due date before the issue date; the parser has no opinion either.
        Invoice invoice = parser.parse(TestInvoiceXml.minimal()
                .totals("<P_6>2049-12-31</P_6><P_13_1>100</P_13_1><P_14_1>23</P_14_1><P_15>123</P_15>")
                .bytes());

        assertEquals(LocalDate.of(2049, 12, 31), invoice.saleDate());
    }

    @Test
    void acceptsUtf8ByteOrderMark() throws Exception {
        byte[] xml = TestInvoiceXml.minimal().bytes();
        byte[] withBom = new byte[xml.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(xml, 0, withBom, 3, xml.length);

        assertEquals("T/1", parser.parse(withBom).number());
    }

    @Test
    void ignoresElementsOutsideTheFa3Namespace() throws Exception {
        // Extraction-level check: the schema rejects such documents (Fa3SchemaValidationTest), but
        // the lookup itself must still match by namespace, not by local name alone.
        String xml = TestInvoiceXml.minimal().xml()
                .replace("<P_2>", "<x:P_2 xmlns:x=\"urn:other\">IGNORED</x:P_2><P_2>");

        Invoice invoice = extractor.parse(utf8(xml));

        assertEquals("T/1", invoice.number());
    }

    // --- Unsupported documents -----------------------------------------------------------------

    @Test
    void rejectsFa2AsUnsupported() {
        String fa2 = "<Faktura xmlns=\"http://crd.gov.pl/wzor/2023/06/29/12648/\"><Naglowek/></Faktura>";

        UnsupportedFormatException e = assertThrows(UnsupportedFormatException.class,
                () -> parser.parse(utf8(fa2)));

        assertEquals("http://crd.gov.pl/wzor/2023/06/29/12648/", e.rootNamespace());
        assertEquals("Faktura", e.rootElement());
    }

    @Test
    void rejectsOtherXmlAsUnsupported() {
        assertThrows(UnsupportedFormatException.class, () -> parser.parse(utf8("<html><body/></html>")));
        assertThrows(UnsupportedFormatException.class, () -> parser.parse(utf8("<Faktura><Fa/></Faktura>")));
    }

    @Test
    void rejectsOtherVersionOfTheFormAsUnsupported() {
        byte[] otherVersion = TestInvoiceXml.minimal()
                .formCode("<KodFormularza kodSystemowy=\"FA (4)\" wersjaSchemy=\"1-0E\">FA</KodFormularza>")
                .bytes();
        byte[] noSpace = TestInvoiceXml.minimal()
                .formCode("<KodFormularza kodSystemowy=\"FA(3)\" wersjaSchemy=\"1-0E\">FA</KodFormularza>")
                .bytes();

        assertThrows(UnsupportedFormatException.class, () -> parser.parse(otherVersion));
        assertThrows(UnsupportedFormatException.class, () -> parser.parse(noSpace));
    }

    // --- Extraction defences (schema validation off; the schema rejects these first) -----------

    @Test
    void rejectsMissingGrossTotalWithItsPath() {
        InvalidInvoiceException e = assertThrows(InvalidInvoiceException.class,
                () -> extractor.parse(TestInvoiceXml.minimal().totals("").bytes()));

        assertEquals("Missing required element Faktura/Fa/P_15", e.getMessage());
    }

    @Test
    void rejectsGrossTotalInTheWrongNamespaceAsMissing() {
        byte[] xml = TestInvoiceXml.minimal().totals("<P_15 xmlns=\"urn:other\">123</P_15>").bytes();

        assertThrows(InvalidInvoiceException.class, () -> extractor.parse(xml));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1,50", "1 000.00", "1e3", "+5", ".5", "5.", "12.3.4", "NaN", "0x10"})
    void rejectsMalformedAmountsWithoutEchoingThem(String amount) {
        InvalidInvoiceException e = assertThrows(InvalidInvoiceException.class,
                () -> extractor.parse(TestInvoiceXml.minimal().totals("<P_15>" + amount + "</P_15>").bytes()));

        assertEquals("Malformed number in Faktura/Fa/P_15", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13-01", "2026-02-30", "26-10-01", "2026-10-01Z", "2026/10/01", "01.10.2026"})
    void rejectsMalformedDates(String date) {
        InvalidInvoiceException e = assertThrows(InvalidInvoiceException.class,
                () -> extractor.parse(TestInvoiceXml.minimal().issueDate(date).bytes()));

        assertEquals("Malformed date in Faktura/Fa/P_1", e.getMessage());
    }

    @Test
    void rejectsUnknownCodes() {
        assertThrows(InvalidInvoiceException.class,
                () -> extractor.parse(TestInvoiceXml.minimal().type("PRO").bytes()));
        assertThrows(InvalidInvoiceException.class,
                () -> extractor.parse(TestInvoiceXml.minimal().currency("zł").bytes()));
        assertThrows(InvalidInvoiceException.class, () -> extractor.parse(TestInvoiceXml.minimal()
                .items("<FaWiersz><NrWierszaFa>1</NrWierszaFa><P_12>23%</P_12></FaWiersz>").bytes()));
    }

    @Test
    void rejectsBuyerWithoutAnyIdentificationChoice() {
        InvalidInvoiceException e = assertThrows(InvalidInvoiceException.class, () -> extractor.parse(
                TestInvoiceXml.minimal().buyerIdentification("<Nazwa>Bez identyfikatora</Nazwa>").bytes()));

        assertTrue(e.getMessage().contains("Faktura/Podmiot2/DaneIdentyfikacyjne"));
    }

    @Test
    void rejectsItemWithoutLineNumber() {
        InvalidInvoiceException e = assertThrows(InvalidInvoiceException.class, () -> extractor.parse(
                TestInvoiceXml.minimal().items("<FaWiersz><P_7>x</P_7></FaWiersz>").bytes()));

        assertEquals("Missing required element Faktura/Fa/FaWiersz[1]/NrWierszaFa", e.getMessage());
    }

    @Test
    void rejectsDocumentsThatAreNotXml() {
        assertThrows(InvalidInvoiceException.class, () -> parser.parse(new byte[0]));
        assertThrows(InvalidInvoiceException.class, () -> parser.parse(utf8("%PDF-1.7\n")));
        assertThrows(InvalidInvoiceException.class, () -> parser.parse(utf8("<Faktura xmlns=\"" + TestInvoiceXml.NS)));
    }

    @Test
    void rejectsDocumentsAboveTheSizeLimitBeforeParsing() {
        byte[] xml = TestInvoiceXml.minimal().bytes();
        Fa3Parser small = new Fa3Parser(xml.length - 1);

        InvalidInvoiceException e = assertThrows(InvalidInvoiceException.class, () -> small.parse(xml));

        assertTrue(e.getMessage().startsWith("Document is larger than the limit"));
    }

    @Test
    void parseExceptionsAreTheTwoKindsTheImporterMaps() {
        InvoiceParseException e = assertThrows(InvoiceParseException.class,
                () -> parser.parse(utf8("<not-closed>")));

        assertInstanceOf(InvalidInvoiceException.class, e);
    }

    // --- Helpers -------------------------------------------------------------------------------

    private static BigDecimal dec(String value) {
        return new BigDecimal(value);
    }

    private static byte[] utf8(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] fixture(String name) {
        try (InputStream in = Fa3ParserTest.class.getResourceAsStream("/fa3/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing test fixture " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
