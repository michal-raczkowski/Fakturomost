package dev.michalraczkowski.fakturomost.core.application;

import dev.michalraczkowski.fakturomost.core.domain.CurrencyCode;
import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import dev.michalraczkowski.fakturomost.core.domain.InvoiceType;
import dev.michalraczkowski.fakturomost.core.domain.Party;
import dev.michalraczkowski.fakturomost.core.domain.TaxIdentifier;
import dev.michalraczkowski.fakturomost.core.domain.VatBreakdown;
import dev.michalraczkowski.fakturomost.core.fa3.Fa3Parser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** Shared fixtures for the export tests: real parsed invoices plus hand-made edge cases. */
final class ExportTestData {

    static final String KSEF_VAT = "9999999999-20261020-0123456789AB-F2";
    static final String KSEF_CORRECTION = "9999999999-20261021-ABCDEF012345-7A";

    final InMemoryDocumentStore store = new InMemoryDocumentStore();
    final byte[] vatXml = fixture("ksef-style-vat.xml");
    final byte[] correctionXml = fixture("correction-eur-prefixed.xml");
    final byte[] fa2Xml = "<Faktura xmlns=\"http://crd.gov.pl/wzor/2023/06/29/12648/\"><Naglowek/></Faktura>"
            .getBytes(StandardCharsets.UTF_8);

    /** The two fixtures, imported under KSeF file names, plus one unsupported FA(2) document. */
    ExportTestData() {
        InvoiceImporter importer = new InvoiceImporter(new Fa3Parser(), store);
        importer.importDocuments(List.of(
                SourceDocument.of("C:/Users/secret-folder/" + KSEF_VAT + ".xml", vatXml),
                SourceDocument.of("C:/Users/secret-folder/" + KSEF_CORRECTION + ".xml", correctionXml),
                SourceDocument.of("C:/Users/secret-folder/old-fa2.xml", fa2Xml)));
    }

    InvoiceEntry vat() {
        return entry(DocumentId.of(vatXml));
    }

    InvoiceEntry correction() {
        return entry(DocumentId.of(correctionXml));
    }

    DocumentId fa2Id() {
        return DocumentId.of(fa2Xml);
    }

    private InvoiceEntry entry(DocumentId id) {
        return InvoiceEntry.of(store.find(id).orElseThrow());
    }

    /** A hand-made invoice for text edge cases; buckets and amounts are whatever the test needs. */
    static InvoiceEntry crafted(String number, String sellerName, String buyerName, List<VatBreakdown> buckets,
            BigDecimal gross) {
        Invoice invoice = new Invoice(InvoiceType.VAT, number, LocalDate.of(2026, 10, 1), null, CurrencyCode.PLN,
                new Party(new TaxIdentifier.Nip("9999999999"), sellerName, null),
                new Party(new TaxIdentifier.None(), buyerName, null),
                buckets, gross, List.of(), null, List.of());
        return new InvoiceEntry(DocumentId.of((number + sellerName + buyerName).getBytes(StandardCharsets.UTF_8)),
                "crafted.xml", null, invoice);
    }

    private static byte[] fixture(String name) {
        try (InputStream in = ExportTestData.class.getResourceAsStream("/fa3/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing test fixture " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
