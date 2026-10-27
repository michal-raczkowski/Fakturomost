package dev.michalraczkowski.fakturomost.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.michalraczkowski.fakturomost.core.application.CsvOptions;
import dev.michalraczkowski.fakturomost.core.application.InvoiceEntry;
import dev.michalraczkowski.fakturomost.core.application.InvoiceQuery;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests of {@code convert}: real files, the real command line, captured stdout/stderr.
 *
 * <p>Expected outputs are checked two ways: against what core's {@code InvoiceExports} writes for the
 * same selection (the CLI must not reshape data), and independently (row counts, BOM, separators,
 * original bytes). Uses the committed {@code demo-data/} like {@link InspectCommandTest}.
 */
class ConvertCommandTest {

    private static final Path DEMO_DIR = Path.of(System.getProperty("fakturomost.demoDataDir", "../demo-data"));

    private static final String SALE_1 = "9999999999-20261002-3A41F0C20001-22.xml";
    private static final String SALE_2 = "9999999999-20261009-3A41F0C20002-3E.xml";
    private static final String EUR = "faktura-EUR-WDT.xml";
    private static final String ZIP = "zakupy-ksef-2026-10.zip";
    private static final String PURCHASE_1 = "8880000002-20261012-7B20C11D0006-7D.xml";

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Invoice contents from the demo files that must never reach stdout or stderr. */
    private static final List<String> INVOICE_CONTENTS =
            List.of("FV/2026", "FK/2026", "Fikcyjne", "Studio", "5550000001", "7770000001", "5460", "492.00");

    @TempDir
    Path temp;

    private record Run(int exitCode, String out, String err) {
        boolean errHasLine(String line) {
            return err.lines().anyMatch(line::equals);
        }
    }

    private static Run convert(Object... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        String[] arguments = Stream.concat(Stream.of("convert"), Arrays.stream(args).map(String::valueOf))
                .toArray(String[]::new);
        int exitCode = Main.run(arguments, new PrintWriter(out), new PrintWriter(err), CoreServices::create);
        return new Run(exitCode, out.toString(), err.toString());
    }

    private static Path demo(String relative) {
        assertTrue(Files.isDirectory(DEMO_DIR), "Demo dataset missing: " + DEMO_DIR.toAbsolutePath());
        return DEMO_DIR.resolve(relative);
    }

    private Path copyOfDemo(String name) throws IOException {
        return Files.write(temp.resolve(name), Files.readAllBytes(demo(name)));
    }

    /** What core selects for {@code query} after importing the whole dataset — the reference. */
    private static List<InvoiceEntry> coreSelection(InvoiceQuery query) {
        CoreServices core = CoreServices.create();
        core.importer().importInputs(List.of(demo("")));
        return core.catalog().search(query);
    }

    private static byte[] coreCsv(List<InvoiceEntry> entries, CsvOptions options) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CoreServices.create().exports().writeCsv(entries, options, bytes);
        return bytes.toByteArray();
    }

    private static byte[] coreJson(List<InvoiceEntry> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CoreServices.create().exports().writeJson(entries, bytes);
        return bytes.toByteArray();
    }

    private static List<String> csvLines(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8).lines().toList();
    }

    /** Every original of the dataset that is a valid invoice, by the file name export gives it. */
    private static Map<String, byte[]> expectedOriginals() throws IOException {
        Map<String, byte[]> expected = new TreeMap<>();
        for (String name : List.of(SALE_1, SALE_2, "9999999999-20261015-3A41F0C20003-2C.xml",
                "9999999999-20261020-3A41F0C20004-99.xml")) {
            expected.put(name, Files.readAllBytes(demo(name)));
        }
        byte[] eur = Files.readAllBytes(demo(EUR));
        expected.put(sha256(eur) + ".xml", eur); // no KSeF number → named by document ID
        expected.putAll(zipEntries(Files.readAllBytes(demo(ZIP))));
        return expected;
    }

    // --- formats --------------------------------------------------------------------------------

    @Test
    void csvDefaultsToExcelPlAndEqualsCoreExport() throws IOException {
        Path csv = temp.resolve("faktury.csv");

        Run run = convert(demo(""), "--format", "csv", "--out", csv);

        assertEquals(ExitCodes.SOME_DOCUMENTS_REJECTED, run.exitCode(), run.err());
        byte[] written = Files.readAllBytes(csv);
        assertArrayEquals(coreCsv(coreSelection(InvoiceQuery.all()), CsvOptions.excelPl()), written);
        assertArrayEquals(UTF8_BOM, Arrays.copyOf(written, 3), "Excel needs the BOM for Polish characters");
        List<String> lines = csvLines(csv);
        assertEquals(8, lines.size(), "header + 7 invoices");
        assertTrue(lines.getFirst().contains(";Numer faktury;"), lines.getFirst());
        assertTrue(new String(written, StandardCharsets.UTF_8).contains("\r\n"), "CRLF for Excel");
        assertEquals("", run.out(), "data only goes to --out");
        assertTrue(run.errHasLine("Wrote 7 invoices to " + csv
                + " (CSV, excel-pl: semicolon, decimal comma, UTF-8 BOM, Polish headers)."), run.err());
    }

    @Test
    void csvStandardPreset() throws IOException {
        Path csv = temp.resolve("faktury.csv");

        Run run = convert(demo(""), "--format", "csv", "--csv-preset", "standard", "--out", csv);

        assertEquals(ExitCodes.SOME_DOCUMENTS_REJECTED, run.exitCode(), run.err());
        byte[] written = Files.readAllBytes(csv);
        assertArrayEquals(coreCsv(coreSelection(InvoiceQuery.all()), CsvOptions.standard()), written);
        assertFalse(Arrays.equals(UTF8_BOM, Arrays.copyOf(written, 3)), "no BOM");
        assertTrue(csvLines(csv).getFirst().startsWith("Numer KSeF,"), csvLines(csv).getFirst());
    }

    @Test
    void jsonEqualsCoreExport() throws IOException {
        Path json = temp.resolve("faktury.json");

        Run run = convert(demo(""), "--format", "json", "--out", json);

        assertEquals(ExitCodes.SOME_DOCUMENTS_REJECTED, run.exitCode(), run.err());
        byte[] written = Files.readAllBytes(json);
        assertArrayEquals(coreJson(coreSelection(InvoiceQuery.all())), written);
        String compact = new String(written, StandardCharsets.UTF_8).replaceAll("\\s+", "");
        assertTrue(compact.startsWith("{\"format\":\"fakturomost-invoices\",\"formatVersion\":1,\"invoices\":["),
                compact);
        assertEquals(7, compact.split("\"documentId\":").length - 1, "one object per invoice");
        assertEquals("", run.out());
    }

    @Test
    void xmlZipHoldsTheOriginalsByteForByte() throws IOException {
        Path zip = temp.resolve("oryginaly.zip");

        Run run = convert(demo(""), "--format", "xml", "--out", zip);

        assertEquals(ExitCodes.SOME_DOCUMENTS_REJECTED, run.exitCode(), run.err());
        Map<String, byte[]> written = zipEntries(Files.readAllBytes(zip));
        Map<String, byte[]> expected = expectedOriginals();
        assertEquals(expected.keySet(), written.keySet());
        expected.forEach((name, bytes) -> assertArrayEquals(bytes, written.get(name), name));
        assertTrue(run.errHasLine("Wrote 7 invoices to " + zip + " (original XML in one ZIP archive)."), run.err());
        assertTrue(run.errHasLine("Note: 1 unsupported document not exported (only FA(3) invoices are)."), run.err());
    }

    @Test
    void xmlFolderHoldsOneOriginalPerInvoice() throws IOException {
        Path folder = temp.resolve("oryginaly");

        Run run = convert(demo(""), "--format", "xml", "--out", folder);

        assertEquals(ExitCodes.SOME_DOCUMENTS_REJECTED, run.exitCode(), run.err());
        Map<String, byte[]> expected = expectedOriginals();
        try (Stream<Path> files = Files.list(folder)) {
            assertEquals(expected.keySet().stream().sorted().toList(),
                    files.map(f -> f.getFileName().toString()).sorted().toList());
        }
        for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
            assertArrayEquals(entry.getValue(), Files.readAllBytes(folder.resolve(entry.getKey())), entry.getKey());
        }
    }

    // --- filters --------------------------------------------------------------------------------

    @Test
    void dateFilterSelectsWhatTheCatalogSelects() throws IOException {
        List<LocalDate> dates = coreSelection(InvoiceQuery.all()).stream()
                .map(e -> e.invoice().issueDate()).sorted().toList();
        LocalDate from = dates.get(2);
        LocalDate to = dates.get(5);
        List<InvoiceEntry> expected = coreSelection(InvoiceQuery.all().withIssuedBetween(from, to));
        assertTrue(expected.size() > 0 && expected.size() < 7, "the range must cut the dataset: " + dates);
        Path json = temp.resolve("okres.json");

        Run run = convert(demo(""), "--format", "json", "--from", from, "--to", to, "--out", json);

        assertEquals(ExitCodes.SOME_DOCUMENTS_REJECTED, run.exitCode(), run.err());
        assertArrayEquals(coreJson(expected), Files.readAllBytes(json));
        assertTrue(run.errHasLine("Wrote " + expected.size() + " of 7 invoices to " + json + " (JSON)."), run.err());
    }

    @Test
    void nipFilterMatchesSellerOrBuyerInAnyNotation() throws IOException {
        Path purchases = temp.resolve("zakupy.csv");
        Path all = temp.resolve("wszystkie.csv");

        convert(demo(""), "--format", "csv", "--nip", "PL 888-000-00-02", "--out", purchases);
        convert(demo(""), "--format", "csv", "--nip", "9999999999", "--out", all);

        List<String> purchaseLines = csvLines(purchases);
        assertEquals(2, purchaseLines.size(), "header + the one invoice whose seller is 8880000002");
        assertTrue(purchaseLines.get(1).contains("8880000002"), purchaseLines.get(1));
        assertEquals(8, csvLines(all).size(), "the demo company is seller or buyer on every invoice");
    }

    @Test
    void xmlExportAppliesTheFiltersToo() throws IOException {
        Path zip = temp.resolve("zakup.zip");

        Run run = convert(demo(""), "--format", "xml", "--nip", "8880000002", "--out", zip);

        assertEquals(ExitCodes.SOME_DOCUMENTS_REJECTED, run.exitCode(), run.err());
        Map<String, byte[]> written = zipEntries(Files.readAllBytes(zip));
        assertEquals(List.of(PURCHASE_1), List.copyOf(written.keySet()));
        assertArrayEquals(expectedOriginals().get(PURCHASE_1), written.get(PURCHASE_1));
        assertTrue(run.errHasLine("Wrote 1 of 7 invoices to " + zip + " (original XML in one ZIP archive)."), run.err());
    }

    @Test
    void nothingMatchingWritesAnEmptyExportAndSaysSo() throws IOException {
        Path input = copyOfDemo(SALE_1);
        Path csv = temp.resolve("pusty.csv");

        Run run = convert(input, "--format", "csv", "--from", "2030-01-01", "--out", csv);

        assertEquals(ExitCodes.OK, run.exitCode(), run.err());
        assertEquals(1, csvLines(csv).size(), "header only");
        assertTrue(run.errHasLine("Note: no invoices matched; the output contains none."), run.err());
    }

    @Test
    void duplicateInputsAreExportedOnce() throws IOException {
        Path input = copyOfDemo(SALE_1);
        Path csv = temp.resolve("raz.csv");

        Run run = convert(input, input, "--format", "csv", "--out", csv);

        assertEquals(ExitCodes.OK, run.exitCode(), run.err());
        assertEquals(2, csvLines(csv).size());
        assertTrue(run.errHasLine("2 documents: 1 imported, 1 duplicate, 0 unsupported, 0 invalid, 0 failed."),
                run.err());
        assertFalse(run.err().contains("Duplicate "), "duplicates are not problems: " + run.err());
    }

    // --- reporting --------------------------------------------------------------------------------

    @Test
    void stderrListsOnlyProblemDocumentsThenSummaryNotesAndWarning() throws IOException {
        Path csv = temp.resolve("faktury.csv");

        Run run = convert(demo(""), "--format", "csv", "--out", csv);

        List<String> lines = run.err().lines().toList();
        assertEquals(4, lines.stream().filter(l -> l.startsWith("Invalid ") || l.startsWith("Unsupported ")).count(),
                run.err());
        assertFalse(run.err().contains("Imported ") || run.err().contains("Duplicate "), run.err());
        assertTrue(run.errHasLine("12 documents: 7 imported, 1 duplicate, 1 unsupported, 3 invalid, 0 failed."),
                run.err());
        assertTrue(run.errHasLine("Warning: " + csv
                + " is not encrypted and contains invoice data. Store and share it accordingly."), run.err());
        assertEquals("Warning: ", lines.getLast().substring(0, 9), "the warning comes last: " + run.err());
    }

    @Test
    void consoleOutputNeverContainsInvoiceContents() {
        for (String format : List.of("csv", "json", "xml")) {
            Run run = convert(demo(""), "--format", format, "--out", temp.resolve("out-" + format));

            String everything = run.out() + run.err();
            for (String secret : INVOICE_CONTENTS) {
                assertFalse(everything.contains(secret), format + ": '" + secret + "' leaked into:\n" + everything);
            }
        }
    }

    // --- output safety ------------------------------------------------------------------------------

    @Test
    void existingOutputIsRefusedBeforeAnythingIsImported() throws IOException {
        Path csv = Files.writeString(temp.resolve("istniejacy.csv"), "keep me");

        Run run = convert(demo(""), "--format", "csv", "--out", csv);

        assertEquals(ExitCodes.USAGE, run.exitCode());
        assertEquals("keep me", Files.readString(csv));
        assertTrue(run.err().startsWith("Error: " + csv + " already exists; add --force to replace it"), run.err());
        assertFalse(run.err().contains("documents:"), "nothing imported: " + run.err());
    }

    @Test
    void forceReplacesAnExistingOutput() throws IOException {
        Path csv = Files.writeString(temp.resolve("istniejacy.csv"), "old");

        Run run = convert(demo(""), "--format", "csv", "--out", csv, "--force");

        assertEquals(ExitCodes.SOME_DOCUMENTS_REJECTED, run.exitCode(), run.err());
        assertEquals(8, csvLines(csv).size());
    }

    @Test
    void xmlFolderConflictWithoutForceWritesNothing() throws IOException {
        Path folder = Files.createDirectories(temp.resolve("eksport"));
        Path existing = Files.writeString(folder.resolve(SALE_1), "old");

        Run run = convert(demo(""), "--format", "xml", "--out", folder);

        assertEquals(ExitCodes.USAGE, run.exitCode(), run.err());
        assertEquals("old", Files.readString(existing));
        try (Stream<Path> files = Files.list(folder)) {
            assertEquals(1, files.count(), "nothing else written");
        }
        assertTrue(run.errHasLine("Error: " + existing + " already exists; nothing was written. Add --force to replace it."),
                run.err());
        assertFalse(run.err().contains("Warning:"), run.err());
    }

    @Test
    void anInputFileIsNeverOverwrittenEvenWithForce() throws IOException {
        Path input = copyOfDemo(SALE_1);
        byte[] before = Files.readAllBytes(input);

        Run run = convert(input, "--format", "csv", "--out", input, "--force");

        assertEquals(ExitCodes.USAGE, run.exitCode(), run.err());
        assertArrayEquals(before, Files.readAllBytes(input));
        assertTrue(run.err().startsWith("Error: --out " + input + " is one of the input files"), run.err());
    }

    @Test
    void xmlIsNotWrittenIntoAnInputFolderWithForce() throws IOException {
        Path folder = Files.createDirectories(temp.resolve("wejscie"));
        Path input = Files.write(folder.resolve(SALE_1), Files.readAllBytes(demo(SALE_1)));
        byte[] before = Files.readAllBytes(input);

        Run run = convert(folder, "--format", "xml", "--out", folder, "--force");

        assertEquals(ExitCodes.USAGE, run.exitCode(), run.err());
        assertArrayEquals(before, Files.readAllBytes(input));
        assertTrue(run.err().contains("contains input files"), run.err());
    }

    @Test
    void outputKindMustFitTheFormat() throws IOException {
        Path folder = Files.createDirectories(temp.resolve("folder"));
        Path file = Files.writeString(temp.resolve("plik.txt"), "x");

        Run csvIntoFolder = convert(demo(SALE_1), "--format", "csv", "--out", folder);
        Run xmlIntoFile = convert(demo(SALE_1), "--format", "xml", "--out", file);

        assertEquals(ExitCodes.USAGE, csvIntoFolder.exitCode());
        assertTrue(csvIntoFolder.err().contains("is a folder; give a file name"), csvIntoFolder.err());
        assertEquals(ExitCodes.USAGE, xmlIntoFile.exitCode());
        assertTrue(xmlIntoFile.err().contains("is a file; for --format xml give a folder"), xmlIntoFile.err());
        assertEquals("x", Files.readString(file));
    }

    @Test
    void outputUnderAFileIsAUsageErrorBeforeImporting() throws IOException {
        Path notAFolder = Files.writeString(temp.resolve("plik"), "x");
        Path csv = notAFolder.resolve("faktury.csv");

        Run run = convert(demo(SALE_1), "--format", "csv", "--out", csv);

        assertEquals(ExitCodes.USAGE, run.exitCode(), run.err());
        assertTrue(run.err().startsWith("Error: --out " + csv + " cannot be created: " + notAFolder + " is a file"),
                run.err());
        assertFalse(run.err().contains("documents:"), "nothing imported: " + run.err());
        assertFalse(run.err().contains("--force"), "--force would not help: " + run.err());
    }

    @Test
    void unwritableOutputIsFatalWithoutTheEncryptionWarning() {
        // A 300-character name is rejected by the file system on Linux, macOS and Windows alike.
        Path csv = temp.resolve("x".repeat(300) + ".csv");

        Run run = convert(demo(SALE_1), "--format", "csv", "--out", csv);

        assertEquals(ExitCodes.FATAL, run.exitCode(), run.err());
        assertTrue(run.err().lines().anyMatch(line -> line.startsWith("Error: could not write " + csv + " (")
                && line.endsWith("Exception).")), "names the output and only the exception type: " + run.err());
        assertFalse(run.err().contains("Warning:"), run.err());
        assertFalse(run.err().contains("--force"), run.err());
    }

    @Test
    void unreadableInputStillWritesTheOutputButExitsThree() throws IOException {
        Path input = copyOfDemo(SALE_1);
        Path csv = temp.resolve("czesciowy.csv");

        Run run = convert(input, temp.resolve("brak.xml"), "--format", "csv", "--out", csv);

        assertEquals(ExitCodes.FATAL, run.exitCode(), run.err());
        assertEquals(2, csvLines(csv).size());
        assertTrue(run.errHasLine("Failed       " + temp.resolve("brak.xml") + "  [File not found]"), run.err());
    }

    @Test
    void maliciousZipEntryNamesCannotPlaceFilesOutsideTheOutputFolder() throws IOException {
        byte[] invoice = Files.readAllBytes(demo(SALE_1));
        Path archive = Files.write(temp.resolve("evil.zip"), zip(Map.of("../../" + SALE_1, invoice)));
        Path folder = temp.resolve("wynik");

        Run run = convert(archive, "--format", "xml", "--out", folder);

        assertEquals(ExitCodes.OK, run.exitCode(), run.err());
        try (Stream<Path> files = Files.walk(temp)) {
            assertEquals(List.of(temp, archive, folder, folder.resolve(SALE_1)), files.sorted().toList());
        }
        assertArrayEquals(invoice, Files.readAllBytes(folder.resolve(SALE_1)));
    }

    // --- usage errors -------------------------------------------------------------------------------

    @Test
    void invalidOptionsAreUsageErrorsAndWriteNothing() {
        Path out = temp.resolve("x.csv");
        Map<List<Object>, String> cases = Map.of(
                List.of("--format", "pdf", "--out", out), "Invalid value for option '--format': expected csv, json or xml but was 'pdf'",
                List.of("--format", "csv", "--out", out, "--from", "2026-13-01"), "Invalid value for option '--from': expected a date like 2026-10-31 but was '2026-13-01'",
                List.of("--format", "csv", "--out", out, "--from", "2026-10-20", "--to", "2026-10-01"), "--from 2026-10-20 is after --to 2026-10-01",
                List.of("--format", "json", "--out", out, "--csv-preset", "standard"), "--csv-preset applies only to --format csv",
                List.of("--format", "csv", "--out", out, "--csv-preset", "excel"), "Invalid value for option '--csv-preset': expected excel-pl or standard but was 'excel'",
                List.of("--format", "csv", "--out", out, "--nip", " "), "--nip must not be empty",
                List.of("--format", "csv"), "Missing required option: '--out=<path>'",
                List.of("--out", out), "Missing required option: '--format=csv|json|xml'");

        cases.forEach((options, message) -> {
            Object[] args = Stream.concat(Stream.of((Object) demo(SALE_1)), options.stream()).toArray();
            Run run = convert(args);

            assertEquals(ExitCodes.USAGE, run.exitCode(), options + ": " + run.err());
            assertTrue(run.err().startsWith("Error: " + message), options + ": " + run.err());
            assertTrue(run.err().contains("Try 'fakturomost-cli convert --help'"), run.err());
            assertEquals("", run.out());
            assertFalse(Files.exists(out), options.toString());
        });
    }

    @Test
    void noInputIsAUsageError() {
        Run run = convert("--format", "csv", "--out", temp.resolve("x.csv"));

        assertEquals(ExitCodes.USAGE, run.exitCode());
        assertTrue(run.err().startsWith("Error: Missing required parameter: '<path>'"), run.err());
    }

    // --- help ---------------------------------------------------------------------------------------

    @Test
    void convertHelpDescribesOptionsAndExitCodes() {
        StringWriter out = new StringWriter();
        int exitCode = Main.run(new String[] {"convert", "--help"}, new PrintWriter(out), new PrintWriter(new StringWriter()));

        assertEquals(ExitCodes.OK, exitCode);
        String flat = out.toString().replaceAll("\\s+", " ");
        assertTrue(out.toString().startsWith("Usage: fakturomost-cli convert"), out.toString());
        for (String expected : List.of("--format=csv|json|xml", "--out=<path>", "--from=<yyyy-mm-dd>", "--to=<yyyy-mm-dd>",
                "--nip=<tax id>", "--csv-preset=excel-pl|standard", "--force", "not encrypted",
                "Exit codes: 0 Output written", "1 Usage error, or the output already exists")) {
            assertTrue(flat.contains(expected), "'" + expected + "' missing in:\n" + out);
        }
    }

    @Test
    void rootHelpListsConvert() {
        StringWriter out = new StringWriter();
        Main.run(new String[] {"--help"}, new PrintWriter(out), new PrintWriter(new StringWriter()));

        assertTrue(out.toString().replaceAll("\\s+", " ").contains("convert Export FA(3) invoices to CSV, JSON"),
                out.toString());
    }

    // --- helpers ------------------------------------------------------------------------------------

    private static Map<String, byte[]> zipEntries(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        try (ZipInputStream in = new ZipInputStream(new java.io.ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : new TreeMap<>(entries).entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
