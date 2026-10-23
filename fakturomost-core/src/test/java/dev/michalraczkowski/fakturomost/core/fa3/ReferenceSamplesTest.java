package dev.michalraczkowski.fakturomost.core.fa3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.michalraczkowski.fakturomost.core.domain.Invoice;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Parses the official MF samples and real KSeF test downloads from the git-ignored
 * {@code reference/} folder. Skipped when the folder is absent (e.g. in CI), because those files
 * are not committed. Override the location with {@code -Dfakturomost.referenceDir=...}.
 */
class ReferenceSamplesTest {

    private static final Path REFERENCE_DIR =
            Paths.get(System.getProperty("fakturomost.referenceDir", "../reference"));

    private final Fa3Parser parser = new Fa3Parser();

    @Test
    void parsesEveryReferenceInvoice() throws IOException {
        List<Path> samples = xmlFilesUnder(REFERENCE_DIR.resolve("mf-samples"), REFERENCE_DIR.resolve("ksef-test-downloads"));
        assumeTrue(!samples.isEmpty(), "No reference samples in " + REFERENCE_DIR.toAbsolutePath());

        List<String> failures = new ArrayList<>();
        for (Path sample : samples) {
            try {
                Invoice invoice = parser.parse(Files.readAllBytes(sample));
                assertFalse(invoice.seller().taxId().display().isBlank());
            } catch (InvoiceParseException e) {
                failures.add(sample.getFileName() + ": " + e.getMessage());
            }
        }
        assertEquals(List.of(), failures);
    }

    @Test
    void readsFirstMfSampleAsDocumented() throws Exception {
        // The official ZIP names the file "FA_3_Przykład_1.xml" (with "ł"); copies may use "l", and
        // the "ł" can decode differently depending on the OS file-name encoding. Match around it.
        List<Path> matches = xmlFilesUnder(REFERENCE_DIR.resolve("mf-samples")).stream()
                .filter(path -> path.getFileName().toString().matches("FA_3_Przyk.*ad_1\\.xml"))
                .toList();
        assumeTrue(matches.size() == 1, "MF sample 1 not present in " + REFERENCE_DIR.toAbsolutePath());

        Invoice invoice = parser.parse(Files.readAllBytes(matches.get(0)));

        assertEquals("FV2026/02/150", invoice.number());
        assertEquals(new BigDecimal("2051"), invoice.grossTotal());
        assertEquals(3, invoice.items().size());
        assertEquals(2, invoice.vatBreakdown().size());
    }

    private static List<Path> xmlFilesUnder(Path... directories) throws IOException {
        List<Path> result = new ArrayList<>();
        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(directory)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".xml"))
                        .sorted()
                        .forEach(result::add);
            }
        }
        return result;
    }
}
