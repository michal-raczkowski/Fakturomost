package dev.michalraczkowski.fakturomost.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.michalraczkowski.fakturomost.core.domain.CorrectedInvoice;
import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import dev.michalraczkowski.fakturomost.core.domain.InvoiceType;
import dev.michalraczkowski.fakturomost.core.domain.Party;
import dev.michalraczkowski.fakturomost.core.domain.TaxIdentifier;
import dev.michalraczkowski.fakturomost.core.fa3.Fa3Parser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

/**
 * The committed synthetic demo dataset ({@code demo-data/} at the repository root) imports exactly
 * as its README says, and contains nothing that could be real data. The dataset is reused by the
 * CLI tests and the installer demo, so it must not drift.
 *
 * <p>Override the location with {@code -Dfakturomost.demoDataDir=...}. Unlike the git-ignored
 * reference files, the dataset is part of the repository: a missing folder fails the test.
 */
class DemoDatasetTest {

    private static final Path DEMO_DIR = Path.of(System.getProperty("fakturomost.demoDataDir", "../demo-data"));
    private static final String DEMO_COMPANY_NIP = "9999999999";

    private final InMemoryDocumentStore store = new InMemoryDocumentStore();
    private final InvoiceImporter importer = new InvoiceImporter(new Fa3Parser(), store);

    @Test
    void importsEveryFileWithTheDocumentedOutcome() throws IOException {
        List<ImportResult> results = importer.importPaths(demoFiles());

        Map<String, String> outcomes = new TreeMap<>();
        for (ImportResult result : results) {
            outcomes.put(result.sourceName(), result.getClass().getSimpleName());
        }
        assertEquals(12, results.size(), "documents in the dataset: " + outcomes.keySet());
        assertEquals(Map.of("Imported", 7L, "Duplicate", 1L, "Unsupported", 1L, "Invalid", 3L),
                results.stream().collect(Collectors.groupingBy(r -> r.getClass().getSimpleName(), Collectors.counting())),
                "outcomes: " + outcomes);
        assertEquals("Duplicate", outcome(outcomes, "kopia-"));
        assertEquals("Unsupported", outcome(outcomes, "faktura-FA2.xml"));
        assertEquals("Invalid", outcome(outcomes, "bez-numeru-faktury.xml"));
        assertEquals("Invalid", outcome(outcomes, "uszkodzona.xml"));
        assertEquals("Invalid", outcome(outcomes, "skan-faktury.pdf"));
    }

    @Test
    void catalogShowsSalesPurchasesAdvanceCorrectionAndForeignCurrency() throws IOException {
        importer.importPaths(demoFiles());
        List<InvoiceEntry> all = new InvoiceCatalog(store).search(InvoiceQuery.all());

        assertEquals(7, all.size());
        assertEquals(Map.of(InvoiceType.VAT, 5L, InvoiceType.ADVANCE, 1L, InvoiceType.CORRECTION, 1L),
                all.stream().collect(Collectors.groupingBy(e -> e.invoice().type(), Collectors.counting())));
        assertEquals(6L, all.stream().filter(e -> e.ksefNumber() != null).count(),
                "every file named by its KSeF number has it recorded; the EUR file is not named that way");
        assertEquals(2L, all.stream().filter(e -> isDemoCompany(e.invoice().buyer())).count(), "purchases");
        assertEquals(List.of("EUR"), all.stream().map(e -> e.invoice().currency().value())
                .filter(c -> !c.equals("PLN")).toList());

        Invoice correction = only(all, InvoiceType.CORRECTION).invoice();
        CorrectedInvoice corrected = correction.correctedInvoices().getFirst();
        InvoiceEntry original = all.stream().filter(e -> e.invoice().number().equals(corrected.number())).findFirst()
                .orElseThrow();
        assertEquals(original.ksefNumber().value(), corrected.ksefNumber(),
                "the correction points at an invoice that is in the dataset, by its KSeF number");
        assertEquals(0, new BigDecimal("-492.00").compareTo(correction.grossTotal()));
    }

    @Test
    void exportsTheWholeCatalogToCsvAndJson() throws IOException {
        importer.importPaths(demoFiles());
        List<InvoiceEntry> all = new InvoiceCatalog(store).search(InvoiceQuery.all());
        InvoiceExports exports = new InvoiceExports(store);

        ByteArrayOutputStream csv = new ByteArrayOutputStream();
        exports.writeCsv(all, CsvOptions.excelPl(), csv);
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        exports.writeJson(all, json);

        assertEquals(1 + 7, csv.toString(StandardCharsets.UTF_8).split("\r\n").length, "header + one row per invoice");
        assertTrue(json.toString(StandardCharsets.UTF_8).contains("\"FK/2026/10/0001\""));
    }

    @Test
    void containsNoTaxNumberThatCouldBelongToARealCompany() throws IOException {
        importer.importPaths(demoFiles());
        List<String> nips = new ArrayList<>();
        for (InvoiceEntry entry : new InvoiceCatalog(store).search(InvoiceQuery.all())) {
            for (Party party : List.of(entry.invoice().seller(), entry.invoice().buyer())) {
                switch (party.taxId()) {
                    case TaxIdentifier.Nip nip -> nips.add(nip.value());
                    case TaxIdentifier.EuVatNumber eu -> assertTrue(eu.number().matches("0+"), "EU VAT numbers are zeros");
                    case TaxIdentifier.OtherTaxId other -> throw new AssertionError("unexpected foreign tax ID");
                    case TaxIdentifier.None none -> { }
                }
            }
        }
        assertFalse(nips.isEmpty());
        for (String nip : nips) {
            // 9999999999 is the Ministry of Finance's own sample NIP; every other one fails the
            // NIP checksum, so it cannot be issued to a real taxpayer.
            assertTrue(nip.equals(DEMO_COMPANY_NIP) || !hasValidNipChecksum(nip),
                    "Demo data must use NIPs with an invalid checksum");
        }
    }

    @Test
    void everyXmlFileDeclaresItselfSynthetic() throws IOException {
        for (Path file : demoFiles()) {
            String name = file.getFileName().toString();
            if (name.endsWith(".xml")) {
                assertMarked(name, Files.readAllBytes(file));
            } else if (name.endsWith(".zip")) {
                try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
                    for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                        assertMarked(name + "/" + entry.getName(), zip.readAllBytes());
                    }
                }
            }
        }
    }

    // --- Helpers ---------------------------------------------------------------------------------

    /** Top-level files first, then {@code problemy/}, each sorted by name — the order the README uses. */
    private static List<Path> demoFiles() throws IOException {
        assertTrue(Files.isDirectory(DEMO_DIR), "Demo dataset missing: " + DEMO_DIR.toAbsolutePath());
        List<Path> files = new ArrayList<>(list(DEMO_DIR));
        files.addAll(list(DEMO_DIR.resolve("problemy")));
        return files;
    }

    private static List<Path> list(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(f -> !f.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
        }
    }

    private static String outcome(Map<String, String> outcomes, String namePart) {
        List<String> matching = outcomes.entrySet().stream().filter(e -> e.getKey().contains(namePart))
                .map(Map.Entry::getValue).toList();
        assertEquals(1, matching.size(), "exactly one source named like " + namePart);
        return matching.getFirst();
    }

    private static InvoiceEntry only(List<InvoiceEntry> entries, InvoiceType type) {
        List<InvoiceEntry> matching = entries.stream().filter(e -> e.invoice().type() == type).toList();
        assertEquals(1, matching.size());
        return matching.getFirst();
    }

    private static boolean isDemoCompany(Party party) {
        return party.taxId() instanceof TaxIdentifier.Nip nip && nip.value().equals(DEMO_COMPANY_NIP);
    }

    private static void assertMarked(String name, byte[] bytes) {
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("Fakturomost demo data"),
                name + " must carry the 'Fakturomost demo data' comment");
    }

    /** Standard NIP checksum: weights 6,5,7,2,3,4,5,6,7, sum mod 11 equals the last digit (10 never valid). */
    static boolean hasValidNipChecksum(String nip) {
        int[] weights = {6, 5, 7, 2, 3, 4, 5, 6, 7};
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (nip.charAt(i) - '0') * weights[i];
        }
        int check = sum % 11;
        return check != 10 && check == nip.charAt(9) - '0';
    }
}
