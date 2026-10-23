package dev.michalraczkowski.fakturomost.core.fa3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.ls.LSInput;
import org.xml.sax.SAXException;

class Fa3SchemaValidationTest {

    /** Distinctive values: if any shows up in an error message, a document value leaked. */
    private static final String LEAK_MARKER = "Q7Z";

    private final Fa3Parser parser = new Fa3Parser();

    // --- Valid documents (fixtures and reference samples are also validated by the other tests) --

    @Test
    void acceptsTheCommittedFixtures() throws Exception {
        parser.parse(Fa3ParserTest.fixture("ksef-style-vat.xml"));
        parser.parse(Fa3ParserTest.fixture("correction-eur-prefixed.xml"));
    }

    @Test
    void acceptsMoreRowsThanTheRelaxationThreshold() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int i = 1; i <= Fa3Schema.RELAXED_FROM + 50; i++) {
            rows.append("<FaWiersz><NrWierszaFa>").append(i).append("</NrWierszaFa><P_11>1</P_11></FaWiersz>");
        }

        assertEquals(150, parser.parse(TestInvoiceXml.minimal().items(rows.toString()).bytes()).items().size());
    }

    // --- Schema rules the extraction code does not check -----------------------------------------

    @Test
    void rejectsIssueDateAfterTheSchemaMaximumWithRuleAndPath() {
        InvalidInvoiceException e = assertThrows(InvalidInvoiceException.class,
                () -> parser.parse(TestInvoiceXml.minimal().issueDate("2051-01-01").bytes()));

        assertTrue(e.getMessage().startsWith("Schema validation failed: cvc-maxInclusive-valid at Faktura/Fa/P_1"),
                e.getMessage());
        assertFalse(e.getMessage().contains("2051"), "message must not contain the value");
    }

    @Test
    void rejectsNipThatDoesNotMatchThePattern() {
        InvalidInvoiceException e = assertThrows(InvalidInvoiceException.class, () -> parser.parse(
                TestInvoiceXml.minimal().buyerIdentification("<NIP>0" + LEAK_MARKER + "</NIP><Nazwa>X</Nazwa>").bytes()));

        assertTrue(e.getMessage().contains("cvc-pattern-valid at Faktura/Podmiot2/DaneIdentyfikacyjne/NIP"),
                e.getMessage());
        assertFalse(e.getMessage().contains(LEAK_MARKER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1,50", "1e3", "+5", "100.123", "12345678901234567"})
    void rejectsAmountsOutsideTKwotowy(String amount) {
        InvalidInvoiceException e = assertThrows(InvalidInvoiceException.class,
                () -> parser.parse(TestInvoiceXml.minimal().totals("<P_15>" + amount + "</P_15>").bytes()));

        assertTrue(e.getMessage().contains("at Faktura/Fa/P_15"), e.getMessage());
    }

    @Test
    void rejectsElementsInTheWrongOrder() {
        // P_2 before P_1 — the extraction code would not care, the schema does.
        String xml = TestInvoiceXml.minimal().xml()
                .replace("<P_1>2026-10-01</P_1><P_2>T/1</P_2>", "<P_2>T/1</P_2><P_1>2026-10-01</P_1>");

        InvalidInvoiceException e = assertThrows(InvalidInvoiceException.class, () -> parser.parse(utf8(xml)));

        assertTrue(e.getMessage().contains("cvc-complex-type"), e.getMessage());
    }

    @Test
    void rejectsElementsFromOtherNamespaces() {
        String xml = TestInvoiceXml.minimal().xml()
                .replace("<P_2>", "<x:Extra xmlns:x=\"urn:other\">" + LEAK_MARKER + "</x:Extra><P_2>");

        InvalidInvoiceException e = assertThrows(InvalidInvoiceException.class, () -> parser.parse(utf8(xml)));

        assertFalse(e.getMessage().contains(LEAK_MARKER));
    }

    @Test
    void rejectsMissingRequiredElement() {
        InvalidInvoiceException e = assertThrows(InvalidInvoiceException.class,
                () -> parser.parse(TestInvoiceXml.minimal().totals("").bytes()));

        assertTrue(e.getMessage().contains("cvc-complex-type"), e.getMessage());
    }

    @Test
    void reportsHowManyFurtherViolationsThereAre() {
        byte[] xml = TestInvoiceXml.minimal().issueDate("2051-01-01").currency("XYZ").bytes();

        InvalidInvoiceException e = assertThrows(InvalidInvoiceException.class, () -> parser.parse(xml));

        assertTrue(e.getMessage().matches(".* \\(and \\d+ more\\)"), e.getMessage());
    }

    // --- Order of checks -------------------------------------------------------------------------

    @Test
    void reportsOtherFormatsAsUnsupportedNotAsSchemaViolations() {
        String fa2 = "<Faktura xmlns=\"http://crd.gov.pl/wzor/2023/06/29/12648/\"><Naglowek/></Faktura>";

        assertThrows(UnsupportedFormatException.class, () -> parser.parse(utf8(fa2)));
    }

    // --- Schema loading cannot reach the network ------------------------------------------------

    @Test
    void resolverServesOnlyTheThreeBaseSchemas() {
        BundledSchemaResolver resolver = new BundledSchemaResolver();
        String base = BundledSchemaResolver.DEFINITIONS_BASE;

        // Absolute (crd.gov.pl originals) and relative (ksef-docs copies) forms both resolve.
        LSInput absolute = resolver.resolveResource(XMLConstants.W3C_XML_SCHEMA_NS_URI, null, null,
                base + "KodyKrajow_v10-0E.xsd", base + "ElementarneTypyDanych_v10-0E.xsd");
        LSInput relative = resolver.resolveResource(XMLConstants.W3C_XML_SCHEMA_NS_URI, null, null,
                "KodyKrajow_v10-0E.xsd", base + "ElementarneTypyDanych_v10-0E.xsd");
        assertNotNull(absolute.getByteStream());
        assertEquals(absolute.getSystemId(), relative.getSystemId());

        for (String refused : new String[] {
                "http://example.com/evil.xsd",
                base + "../evil.xsd",
                "https://crd.gov.pl/xml/schematy/dziedzinowe/mf/2022/01/05/eD/DefinicjeTypy/KodyKrajow_v10-0E.xsd",
                "file:///etc/passwd",
                "KodyKrajow_v10-0E.xsd?x=1",
                null}) {
            assertThrows(BundledSchemaResolver.SchemaReferenceRefusedException.class,
                    () -> resolver.resolveResource(XMLConstants.W3C_XML_SCHEMA_NS_URI, null, null,
                            refused, base + "StrukturyDanych_v10-0E.xsd"),
                    String.valueOf(refused));
        }
    }

    @Test
    void schemaImportingAnUnknownLocationFailsWithoutNetworkAccess() {
        String hostile = "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">"
                + "<xsd:import namespace=\"urn:x\" schemaLocation=\"http://192.0.2.1/x.xsd\"/></xsd:schema>";
        SchemaFactory factory = SchemaFactory.newDefaultInstance();
        factory.setResourceResolver(new BundledSchemaResolver());

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThrows(RuntimeException.class,
                () -> factory.newSchema(new StreamSource(new StringReader(hostile), "urn:test"))));
    }

    // --- The in-memory relaxation of large occurrence bounds -------------------------------------

    @Test
    void relaxesOnlyLargeOccurrenceBounds() {
        String relaxed = Fa3Schema.relaxLargeOccurrenceBounds(
                "<a maxOccurs=\"3\"/><b maxOccurs=\"99\"/><c maxOccurs=\"100\"/><d maxOccurs=\"50000\"/>"
                        + "<e maxOccurs=\"unbounded\"/><f minOccurs=\"500\"/>");

        assertEquals("<a maxOccurs=\"3\"/><b maxOccurs=\"99\"/><c maxOccurs=\"unbounded\"/>"
                + "<d maxOccurs=\"unbounded\"/><e maxOccurs=\"unbounded\"/><f minOccurs=\"500\"/>", relaxed);
    }

    @Test
    void theUnrelaxedOfficialSchemaIsTooLargeForTheJdkValidator() throws Exception {
        // Documents why relaxLargeOccurrenceBounds exists: with secure processing the JDK refuses
        // to compile the official schema as published.
        String official;
        try (var in = Fa3Schema.openBundled(Fa3Schema.MAIN_SCHEMA_FILE)) {
            official = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        SchemaFactory factory = SchemaFactory.newDefaultInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setResourceResolver(new BundledSchemaResolver());

        SAXException e = assertThrows(SAXException.class,
                () -> factory.newSchema(new StreamSource(new StringReader(official), "urn:official")));
        assertTrue(String.valueOf(e.getMessage()).contains("5,000") || String.valueOf(e.getMessage()).contains("5000"),
                e.getMessage());
    }

    private static byte[] utf8(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }
}
