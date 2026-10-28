package dev.michalraczkowski.fakturomost.core.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.michalraczkowski.fakturomost.core.domain.CurrencyCode;
import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import dev.michalraczkowski.fakturomost.core.domain.InvoiceType;
import dev.michalraczkowski.fakturomost.core.domain.KsefNumber;
import dev.michalraczkowski.fakturomost.core.domain.OriginalDocument;
import dev.michalraczkowski.fakturomost.core.domain.Party;
import dev.michalraczkowski.fakturomost.core.domain.TaxIdentifier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InvoiceCatalogTest {

    private static final TaxIdentifier SELLER_A = new TaxIdentifier.Nip("9999999999");
    private static final TaxIdentifier SELLER_B = new TaxIdentifier.Nip("1111111111");
    private static final TaxIdentifier BUYER_PL = new TaxIdentifier.Nip("2222222222");
    private static final TaxIdentifier BUYER_EU = new TaxIdentifier.EuVatNumber("DE", "123456789");

    private final InMemoryDocumentStore store = new InMemoryDocumentStore();
    private final InvoiceCatalog catalog = new InvoiceCatalog(store);

    private DocumentId january;
    private DocumentId februaryA;
    private DocumentId februaryB;
    private DocumentId correction;
    private DocumentId foreign;

    @BeforeEach
    void storeInvoices() {
        january = add(invoice(InvoiceType.VAT, "FV/2026/01/7", "2026-01-31", SELLER_A, "Przykładowa Spółka", BUYER_PL,
                "Żółty   Ząb s.c.", "1230.00"), "9999999999-20261020-0123456789AB-F2");
        februaryA = add(invoice(InvoiceType.VAT, "FV/2026/02/1", "2026-02-15", SELLER_A, "Przykładowa Spółka", BUYER_PL,
                "Żółty Ząb s.c.", "100"), null);
        februaryB = add(invoice(InvoiceType.VAT, "A-17", "2026-02-15", SELLER_B, "Inna Firma", BUYER_PL,
                "Żółty Ząb s.c.", "50.5"), null);
        correction = add(invoice(InvoiceType.CORRECTION, "FK/2026/03/1", "2026-03-01", SELLER_A, "Przykładowa Spółka",
                BUYER_PL, "Żółty Ząb s.c.", "-123.00"), null);
        foreign = add(invoice(InvoiceType.ADVANCE, "EXP/9", "2026-03-10", SELLER_A, "Przykładowa Spółka", BUYER_EU,
                null, "999.99"), null);
        // An unsupported format: stored, but never listed.
        store.addIfAbsent(new StoredDocument(OriginalDocument.of(utf8("<fa2/>")), "old.xml", null, null));
    }

    // --- Listing ---------------------------------------------------------------------------------

    @Test
    void listsAllInvoicesNewestFirstWithStableTieBreaks() {
        // Same day: seller 1111111111 before 9999999999.
        assertEquals(List.of(foreign, correction, februaryB, februaryA, january), ids(InvoiceQuery.all()));
    }

    @Test
    void doesNotListUnsupportedDocuments() {
        assertEquals(6, store.size());
        assertEquals(5, catalog.search(InvoiceQuery.all()).size());
    }

    @Test
    void findsTheFullDocumentIncludingTheOriginalBytes() {
        StoredDocument document = catalog.find(january).orElseThrow();

        assertEquals("FV/2026/01/7", document.invoice().number());
        assertArrayEquals(utf8("FV/2026/01/7"), document.original().bytes());
        assertEquals(Optional.empty(), catalog.find(DocumentId.of(utf8("nothing"))));
    }

    // --- Criteria --------------------------------------------------------------------------------

    @Test
    void filtersByIssueDateInclusively() {
        InvoiceQuery february = InvoiceQuery.all()
                .withIssuedBetween(LocalDate.parse("2026-02-15"), LocalDate.parse("2026-03-01"));

        assertEquals(List.of(correction, februaryB, februaryA), ids(february));
        assertEquals(List.of(foreign), ids(InvoiceQuery.all().withIssuedBetween(LocalDate.parse("2026-03-02"), null)));
        assertEquals(List.of(january), ids(InvoiceQuery.all().withIssuedBetween(null, LocalDate.parse("2026-01-31"))));
    }

    @Test
    void filtersBySellerOrBuyerTaxIdInAnyCommonNotation() {
        assertEquals(List.of(februaryB), ids(InvoiceQuery.all().withTaxId("1111111111")));
        assertEquals(List.of(februaryB), ids(InvoiceQuery.all().withTaxId(" 111-111-11-11 ")));
        assertEquals(List.of(februaryB), ids(InvoiceQuery.all().withTaxId("PL1111111111")));
        assertEquals(List.of(februaryB), ids(InvoiceQuery.all().withTaxId("pl 111 111 11 11")));
        assertEquals(4, ids(InvoiceQuery.all().withTaxId("2222222222")).size(), "buyer NIP");
        assertEquals(List.of(foreign), ids(InvoiceQuery.all().withTaxId("de 123 456 789")));
    }

    @Test
    void taxIdIsAnExactMatchNotASubstring() {
        assertEquals(List.of(), ids(InvoiceQuery.all().withTaxId("11111")));
        assertEquals(List.of(), ids(InvoiceQuery.all().withTaxId("123456789")), "EU VAT number needs its prefix");
        assertEquals(List.of(), ids(InvoiceQuery.all().withTaxId("DE2222222222")), "PL prefix only for a NIP");
    }

    @Test
    void filtersByTextInNumberNamesAndKsefNumber() {
        assertEquals(List.of(january), ids(InvoiceQuery.all().withText("fv/2026/01")));
        assertEquals(List.of(februaryB), ids(InvoiceQuery.all().withText("inna")));
        assertEquals(4, ids(InvoiceQuery.all().withText("ŻÓŁTY ZĄB")).size(), "Polish letters, any case");
        assertEquals(4, ids(InvoiceQuery.all().withText("  żółty   ząb ")).size(), "whitespace collapsed");
        assertEquals(List.of(january), ids(InvoiceQuery.all().withText("0123456789AB")), "KSeF number");
    }

    @Test
    void filtersByGrossTotalNumericallyIncludingCorrections() {
        assertEquals(List.of(january), ids(InvoiceQuery.all().withGrossBetween(new BigDecimal("1230"), null)),
                "1230 matches 1230.00");
        assertEquals(List.of(correction), ids(InvoiceQuery.all().withGrossBetween(null, BigDecimal.ZERO)));
        assertEquals(List.of(februaryB, februaryA),
                ids(InvoiceQuery.all().withGrossBetween(new BigDecimal("50.50"), new BigDecimal("100.00"))));
    }

    @Test
    void filtersByInvoiceType() {
        assertEquals(List.of(foreign, correction),
                ids(InvoiceQuery.all().withTypes(EnumSet.of(InvoiceType.CORRECTION, InvoiceType.ADVANCE))));
    }

    @Test
    void combinesCriteriaWithAnd() {
        InvoiceQuery query = InvoiceQuery.all()
                .withTaxId("9999999999")
                .withIssuedBetween(LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28"))
                .withText("FV");

        assertEquals(List.of(februaryA), ids(query));
    }

    @Test
    void ignoresBlankCriteria() {
        assertEquals(5, ids(InvoiceQuery.all().withText("   ").withTaxId("").withTypes(Set.of())).size());
    }

    @Test
    void rejectsReversedRanges() {
        assertThrows(IllegalArgumentException.class, () -> InvoiceQuery.all()
                .withIssuedBetween(LocalDate.parse("2026-02-02"), LocalDate.parse("2026-02-01")));
        assertThrows(IllegalArgumentException.class, () -> InvoiceQuery.all()
                .withGrossBetween(BigDecimal.TEN, BigDecimal.ONE));
    }

    @Test
    void queryKeepsItsOwnCopyOfTheTypes() {
        Set<InvoiceType> types = EnumSet.of(InvoiceType.VAT);
        InvoiceQuery query = InvoiceQuery.all().withTypes(types);

        types.add(InvoiceType.CORRECTION);

        assertEquals(Set.of(InvoiceType.VAT), query.types());
        assertThrows(UnsupportedOperationException.class, () -> query.types().add(InvoiceType.ADVANCE));
    }

    @Test
    void appliesTheQueryEvenWhenTheStoreReturnsTooMuch() {
        // A store may narrow cheaply (e.g. by date in SQL) and leave the rest to the catalog.
        DocumentStore loose = new DocumentStore() {
            @Override
            public boolean contains(DocumentId id) {
                return store.contains(id);
            }

            @Override
            public boolean addIfAbsent(StoredDocument document) {
                return store.addIfAbsent(document);
            }

            @Override
            public Optional<StoredDocument> find(DocumentId id) {
                return store.find(id);
            }

            @Override
            public List<InvoiceEntry> findInvoices(InvoiceQuery query) {
                return store.findInvoices(InvoiceQuery.all());
            }

            @Override
            public List<OtherDocument> findOtherDocuments() {
                return store.findOtherDocuments();
            }
        };

        List<InvoiceEntry> result = new InvoiceCatalog(loose).search(InvoiceQuery.all().withText("inna"));

        assertEquals(List.of(februaryB), result.stream().map(InvoiceEntry::id).toList());
    }

    @Test
    void searchResultsAreImmutable() {
        List<InvoiceEntry> result = catalog.search(InvoiceQuery.all());

        assertThrows(UnsupportedOperationException.class, result::clear);
        assertTrue(result.size() > 0);
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private List<DocumentId> ids(InvoiceQuery query) {
        return catalog.search(query).stream().map(InvoiceEntry::id).toList();
    }

    /** Stores the invoice; the "original bytes" are just its number, which keeps IDs distinct. */
    private DocumentId add(Invoice invoice, String ksefNumber) {
        OriginalDocument original = OriginalDocument.of(utf8(invoice.number()));
        KsefNumber ksef = ksefNumber == null ? null : new KsefNumber(ksefNumber);
        store.addIfAbsent(new StoredDocument(original, invoice.number() + ".xml", ksef, invoice));
        return original.id();
    }

    private static Invoice invoice(InvoiceType type, String number, String issueDate, TaxIdentifier sellerId,
            String sellerName, TaxIdentifier buyerId, String buyerName, String gross) {
        return new Invoice(type, number, LocalDate.parse(issueDate), null, CurrencyCode.PLN,
                new Party(sellerId, sellerName, null), new Party(buyerId, buyerName, null),
                List.of(), new BigDecimal(gross), new ArrayList<>(), null, List.of());
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
