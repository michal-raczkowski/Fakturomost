package dev.michalraczkowski.fakturomost.core.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import dev.michalraczkowski.fakturomost.core.domain.KsefNumber;
import dev.michalraczkowski.fakturomost.core.fa3.Fa3Parser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceImporterTest {

    /** Seller NIP of both fixtures is 9999999999; checksums computed with the spec's CRC-8. */
    private static final String KSEF_NAME = "9999999999-20261020-0123456789AB-F2.xml";
    private static final String OTHER_KSEF_NAME = "9999999999-20261021-ABCDEF012345-7A.xml";
    /** Valid KSeF number, but for NIP 2222222222 — the buyer, not the seller. */
    private static final String MISMATCHED_KSEF_NAME = "2222222222-20261020-0123456789AB-A8.xml";

    private static final byte[] VAT = fixture("ksef-style-vat.xml");
    private static final byte[] CORRECTION = fixture("correction-eur-prefixed.xml");
    private static final byte[] FA2 = ("<Faktura xmlns=\"http://crd.gov.pl/wzor/2023/06/29/12648/\">"
            + "<Naglowek/></Faktura>").getBytes(StandardCharsets.UTF_8);

    private final InMemoryDocumentStore store = new InMemoryDocumentStore();
    private final InvoiceImporter importer = new InvoiceImporter(new Fa3Parser(), store);

    @Test
    void importsAnInvoiceWithTheKsefNumberFromItsFileName() {
        ImportResult result = importer.importDocument(SourceDocument.of(KSEF_NAME, VAT));

        ImportResult.Imported imported = assertInstanceOf(ImportResult.Imported.class, result);
        assertEquals(KSEF_NAME, imported.sourceName());
        assertEquals(DocumentId.of(VAT), imported.id());
        assertEquals(new KsefNumber("9999999999-20261020-0123456789AB-F2"), imported.ksefNumber());
        assertEquals("FV/2026/10/0042", imported.invoice().number());

        StoredDocument stored = store.find(imported.id()).orElseThrow();
        assertTrue(stored.isSupported());
        assertEquals(imported.ksefNumber(), stored.ksefNumber());
        assertArrayEquals(VAT, stored.original().bytes(), "original bytes are kept unchanged");
    }

    @Test
    void leavesTheKsefNumberEmptyWhenTheFileNameIsNotOne() {
        ImportResult.Imported imported = assertInstanceOf(ImportResult.Imported.class,
                importer.importDocument(SourceDocument.of("faktura.xml", VAT)));

        assertNull(imported.ksefNumber());
    }

    @Test
    void ignoresAKsefNumberWhoseSellerNipDoesNotMatchTheInvoice() {
        ImportResult.Imported imported = assertInstanceOf(ImportResult.Imported.class,
                importer.importDocument(SourceDocument.of(MISMATCHED_KSEF_NAME, VAT)));

        assertNull(imported.ksefNumber());
        assertNull(store.find(imported.id()).orElseThrow().ksefNumber());
    }

    @Test
    void detectsDuplicatesByContentNotByName() {
        importer.importDocument(SourceDocument.of(KSEF_NAME, VAT));

        ImportResult again = importer.importDocument(SourceDocument.of("copy-from-zip/other-name.xml", VAT));

        ImportResult.Duplicate duplicate = assertInstanceOf(ImportResult.Duplicate.class, again);
        assertEquals(DocumentId.of(VAT), duplicate.id());
        assertEquals(1, store.size());
        assertEquals(KSEF_NAME, store.all().get(0).sourceName(), "the first import is kept");
    }

    @Test
    void treatsAnyByteChangeAsADifferentDocument() {
        byte[] withTrailingNewline = (new String(VAT, StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8);

        importer.importDocument(SourceDocument.of(KSEF_NAME, VAT));
        ImportResult second = importer.importDocument(SourceDocument.of(OTHER_KSEF_NAME, withTrailingNewline));

        assertInstanceOf(ImportResult.Imported.class, second);
        assertEquals(2, store.size());
    }

    @Test
    void storesUnsupportedFormatsWithoutInvoiceData() {
        ImportResult result = importer.importDocument(SourceDocument.of(KSEF_NAME, FA2));

        ImportResult.Unsupported unsupported = assertInstanceOf(ImportResult.Unsupported.class, result);
        assertTrue(unsupported.reason().contains("http://crd.gov.pl/wzor/2023/06/29/12648/"), unsupported.reason());
        assertEquals(new KsefNumber("9999999999-20261020-0123456789AB-F2"), unsupported.ksefNumber());
        StoredDocument stored = store.find(unsupported.id()).orElseThrow();
        assertFalse(stored.isSupported());
        assertArrayEquals(FA2, stored.original().bytes());

        assertInstanceOf(ImportResult.Duplicate.class, importer.importDocument(SourceDocument.of("again.xml", FA2)));
    }

    @Test
    void reportsInvalidDocumentsWithoutStoringThem() {
        byte[] schemaInvalid = new String(VAT, StandardCharsets.UTF_8)
                .replace("<P_1>2026-10-20</P_1>", "<P_1>2051-01-01</P_1>").getBytes(StandardCharsets.UTF_8);

        ImportResult result = importer.importDocument(SourceDocument.of(KSEF_NAME, schemaInvalid));

        ImportResult.Invalid invalid = assertInstanceOf(ImportResult.Invalid.class, result);
        assertTrue(invalid.reason().contains("Faktura/Fa/P_1"), invalid.reason());
        assertEquals(DocumentId.of(schemaInvalid), invalid.id());
        assertEquals(0, store.size());
    }

    @Test
    void reportsNonXmlAsInvalid() {
        ImportResult result = importer.importDocument(
                SourceDocument.of("scan.pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));

        assertInstanceOf(ImportResult.Invalid.class, result);
    }

    @Test
    void turnsAFailingStoreIntoAFailedResult() {
        DocumentStore broken = new DocumentStore() {
            @Override
            public boolean contains(DocumentId id) {
                return false;
            }

            @Override
            public boolean addIfAbsent(StoredDocument document) {
                throw new IllegalStateException("disk full");
            }

            @Override
            public java.util.Optional<StoredDocument> find(DocumentId id) {
                return java.util.Optional.empty();
            }

            @Override
            public List<InvoiceEntry> findInvoices(InvoiceQuery query) {
                return List.of();
            }
        };

        ImportResult result = new InvoiceImporter(new Fa3Parser(), broken).importDocument(SourceDocument.of(KSEF_NAME, VAT));

        ImportResult.Failed failed = assertInstanceOf(ImportResult.Failed.class, result);
        assertEquals("Unexpected error: IllegalStateException", failed.reason());
        assertInstanceOf(IllegalStateException.class, failed.cause());
    }

    @Test
    void reportsADuplicateWhenTheStoreAlreadyHasItDespiteThePreCheck() {
        // Another importer (or thread) stored the document between contains() and addIfAbsent().
        DocumentStore store = new DocumentStore() {
            @Override
            public boolean contains(DocumentId id) {
                return false;
            }

            @Override
            public boolean addIfAbsent(StoredDocument document) {
                return false;
            }

            @Override
            public java.util.Optional<StoredDocument> find(DocumentId id) {
                return java.util.Optional.empty();
            }

            @Override
            public List<InvoiceEntry> findInvoices(InvoiceQuery query) {
                return List.of();
            }
        };

        ImportResult result = new InvoiceImporter(new Fa3Parser(), store).importDocument(SourceDocument.of(KSEF_NAME, VAT));

        assertInstanceOf(ImportResult.Duplicate.class, result);
    }

    @Test
    void returnsOneResultPerDocumentInOrder() {
        List<ImportResult> results = importer.importDocuments(List.of(
                SourceDocument.of(KSEF_NAME, VAT),
                SourceDocument.of("broken.xml", "<oops".getBytes(StandardCharsets.UTF_8)),
                SourceDocument.of("fa2.xml", FA2),
                SourceDocument.of("same-again.xml", VAT),
                SourceDocument.of(OTHER_KSEF_NAME, CORRECTION)));

        assertEquals(List.of("Imported", "Invalid", "Unsupported", "Duplicate", "Imported"),
                results.stream().map(r -> r.getClass().getSimpleName()).toList());
        assertEquals(List.of(KSEF_NAME, "broken.xml", "fa2.xml", "same-again.xml", OTHER_KSEF_NAME),
                results.stream().map(ImportResult::sourceName).toList());
        assertEquals(3, store.size());
    }

    @Test
    void resultsCanBeHandledExhaustively() {
        ImportResult result = importer.importDocument(SourceDocument.of(KSEF_NAME, VAT));

        // Compiles only while the switch covers every kind of result.
        String label = switch (result) {
            case ImportResult.Imported r -> "imported";
            case ImportResult.Duplicate r -> "duplicate";
            case ImportResult.Unsupported r -> "unsupported";
            case ImportResult.Invalid r -> "invalid";
            case ImportResult.Failed r -> "failed";
        };

        assertEquals("imported", label);
    }

    private static byte[] fixture(String name) {
        try (InputStream in = InvoiceImporterTest.class.getResourceAsStream("/fa3/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing test fixture " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
