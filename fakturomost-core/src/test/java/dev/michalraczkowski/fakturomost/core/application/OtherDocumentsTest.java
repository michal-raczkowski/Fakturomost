package dev.michalraczkowski.fakturomost.core.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.michalraczkowski.fakturomost.core.domain.KsefNumber;
import dev.michalraczkowski.fakturomost.core.fa3.Fa3Parser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link InvoiceCatalog#otherDocuments()}: stored documents that are not FA(3) invoices. */
class OtherDocumentsTest {

    private static final String KSEF = "9999999999-20261020-0123456789AB-F2";
    private static final byte[] FA2 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Faktura xmlns="http://crd.gov.pl/wzor/2023/06/29/12648/"><Naglowek/></Faktura>
            """.getBytes(StandardCharsets.UTF_8);
    private static final byte[] OTHER = "<Dokument xmlns=\"urn:example\"/>".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    private final InMemoryDocumentStore store = new InMemoryDocumentStore();
    private final InvoiceImporter importer = new InvoiceImporter(new Fa3Parser(), store);
    private final InvoiceCatalog catalog = new InvoiceCatalog(store);

    @Test
    void listsOnlyUnsupportedDocumentsSortedBySourceName() {
        importer.importDocuments(List.of(
                SourceDocument.of("z-other.xml", OTHER),
                SourceDocument.of("invoice.xml", fixture()),
                SourceDocument.of(KSEF + ".xml", FA2),
                SourceDocument.of("broken.xml", "<oops".getBytes(StandardCharsets.UTF_8))));

        List<OtherDocument> others = catalog.otherDocuments();

        assertEquals(List.of(KSEF + ".xml", "z-other.xml"), others.stream().map(OtherDocument::sourceName).toList());
        assertEquals(new KsefNumber(KSEF), others.get(0).ksefNumber());
        assertNull(others.get(1).ksefNumber());
        assertEquals(FA2.length, others.get(0).size());
        assertEquals(1, catalog.search(InvoiceQuery.all()).size(), "invoices are not listed as other documents");
    }

    @Test
    void invalidAndDuplicateDocumentsAreNotListed() {
        importer.importDocuments(List.of(
                SourceDocument.of("a.xml", FA2),
                SourceDocument.of("copy-of-a.xml", FA2),
                SourceDocument.of("broken.xml", "<oops".getBytes(StandardCharsets.UTF_8))));

        assertEquals(List.of("a.xml"), catalog.otherDocuments().stream().map(OtherDocument::sourceName).toList());
    }

    @Test
    void otherDocumentsCanBeExportedAsUnchangedOriginals() throws IOException {
        ImportResult result = importer.importDocument(SourceDocument.of(KSEF + ".xml", FA2));
        assertInstanceOf(ImportResult.Unsupported.class, result);
        OtherDocument document = catalog.otherDocuments().getFirst();

        List<Path> written = new InvoiceExports(store).exportOriginals(List.of(document.id()), dir.resolve("out"), false);

        assertEquals(List.of(dir.resolve("out").resolve(KSEF + ".xml")), written);
        assertArrayEquals(FA2, Files.readAllBytes(written.getFirst()));
    }

    @Test
    void emptyStoreHasNoOtherDocuments() {
        assertTrue(catalog.otherDocuments().isEmpty());
    }

    private static byte[] fixture() {
        try (InputStream in = OtherDocumentsTest.class.getResourceAsStream("/fa3/ksef-style-vat.xml")) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
