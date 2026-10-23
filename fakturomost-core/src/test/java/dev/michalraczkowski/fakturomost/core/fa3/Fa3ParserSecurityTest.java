package dev.michalraczkowski.fakturomost.core.fa3;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hostile-input tests. Every DOCTYPE is rejected, so these documents must fail as invalid — and,
 * just as important, must not read local files, open network connections, or burn CPU first.
 */
class Fa3ParserSecurityTest {

    private static final String MARKER = "SECRET-MARKER-7f3a";

    private final Fa3Parser parser = new Fa3Parser();

    @TempDir
    Path tempDir;

    // Each hostile document is an otherwise valid invoice, so the DOCTYPE is the only thing that
    // can make it fail; the message check pins which defence did the rejecting.

    @Test
    void rejectsExternalEntityPointingAtALocalFile() throws IOException {
        Path secret = Files.writeString(tempDir.resolve("secret.txt"), MARKER);
        String xml = withDoctype("<!DOCTYPE Faktura [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>",
                TestInvoiceXml.minimal().number("&xxe;"));

        InvalidInvoiceException e = assertThrows(InvalidInvoiceException.class, () -> parser.parse(utf8(xml)));

        assertFalse(e.getMessage().contains(MARKER), "message must not leak the file content");
        assertRejectedForDoctype(e);
    }

    @Test
    void rejectsParameterEntityAndExternalDtdWithoutFetchingThem() {
        // Unroutable address: if the parser tried to fetch it, the test would hang or fail on timeout.
        String xml = withDoctype("<!DOCTYPE Faktura SYSTEM \"http://192.0.2.1/evil.dtd\" "
                + "[<!ENTITY % remote SYSTEM \"http://192.0.2.1/x\"> %remote;]>", TestInvoiceXml.minimal());

        InvalidInvoiceException e = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(InvalidInvoiceException.class, () -> parser.parse(utf8(xml))));

        assertRejectedForDoctype(e);
    }

    @Test
    void rejectsEntityExpansionBombQuickly() {
        StringBuilder dtd = new StringBuilder("<!DOCTYPE Faktura [<!ENTITY lol0 \"lol\">");
        for (int i = 1; i <= 10; i++) {
            dtd.append("<!ENTITY lol").append(i).append(" \"");
            dtd.append(("&lol" + (i - 1) + ";").repeat(10)).append("\">");
        }
        dtd.append("]>");
        String xml = withDoctype(dtd.toString(), TestInvoiceXml.minimal().number("&lol10;"));

        InvalidInvoiceException e = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(InvalidInvoiceException.class, () -> parser.parse(utf8(xml))));

        assertRejectedForDoctype(e);
    }

    @Test
    void doesNotProcessXInclude() throws Exception {
        Path secret = Files.writeString(tempDir.resolve("included.txt"), MARKER);
        byte[] xml = TestInvoiceXml.minimal()
                .items("<FaWiersz><NrWierszaFa>1</NrWierszaFa><P_7>"
                        + "<xi:include xmlns:xi=\"http://www.w3.org/2001/XInclude\" href=\"" + secret.toUri()
                        + "\" parse=\"text\"/></P_7></FaWiersz>")
                .bytes();

        // The XML layer leaves it inert (checked without the schema, which would reject it first)...
        Invoice invoice = Fa3Parser.withoutSchemaValidation().parse(xml);
        assertNull(invoice.items().get(0).description(), "XInclude must stay an inert element");

        // ...and the schema then rejects the stray element without revealing the file.
        InvalidInvoiceException e = assertThrows(InvalidInvoiceException.class, () -> parser.parse(xml));
        assertFalse(e.getMessage().contains(MARKER));
    }

    @Test
    void ignoresXsiSchemaLocationWithoutFetchingIt() {
        // xsi:schemaLocation is just an attribute to a non-validating parser; it must not trigger I/O.
        byte[] xml = TestInvoiceXml.minimal()
                .rootOpen("<Faktura xmlns=\"" + TestInvoiceXml.NS + "\" "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:schemaLocation=\"" + TestInvoiceXml.NS + " http://192.0.2.1/schemat.xsd\">")
                .bytes();

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> parser.parse(xml));
    }

    private static String withDoctype(String doctype, TestInvoiceXml invoice) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + doctype
                + invoice.xml().replaceFirst("^<\\?xml[^>]*\\?>", "");
    }

    private static void assertRejectedForDoctype(InvalidInvoiceException e) {
        assertTrue(e.getMessage().contains("DOCTYPE"), "expected DOCTYPE rejection, got: " + e.getMessage());
    }

    private static byte[] utf8(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }
}
