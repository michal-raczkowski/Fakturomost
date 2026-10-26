package dev.michalraczkowski.fakturomost.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.michalraczkowski.fakturomost.core.application.ImportLimits;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests of {@code inspect}: real files, the real command line, captured stdout/stderr.
 *
 * <p>Uses the committed synthetic dataset ({@code demo-data/} at the repository root, override with
 * {@code -Dfakturomost.demoDataDir=...}); its expected outcomes are pinned by core's
 * {@code DemoDatasetTest}. A missing dataset fails the test, like there.
 */
class InspectCommandTest {

    private static final Path DEMO_DIR = Path.of(System.getProperty("fakturomost.demoDataDir", "../demo-data"));

    private static final String SALE_1 = "9999999999-20261002-3A41F0C20001-22.xml";
    private static final String SALE_2 = "9999999999-20261009-3A41F0C20002-3E.xml";
    private static final String SALE_3 = "9999999999-20261015-3A41F0C20003-2C.xml";
    private static final String CORRECTION = "9999999999-20261020-3A41F0C20004-99.xml";
    private static final String EUR = "faktura-EUR-WDT.xml";
    private static final String ZIP = "zakupy-ksef-2026-10.zip";
    private static final String PURCHASE_1 = "8880000002-20261012-7B20C11D0006-7D.xml";
    private static final String PURCHASE_2 = "6660000003-20261016-51E9A0B30007-52.xml";

    /** Status padded to 11 characters + two spaces; the source name starts here. */
    private static final int STATUS_COLUMN = 13;

    @TempDir
    Path temp;

    private record Run(int exitCode, String out, String err) {
        List<String> outLines() {
            return out.lines().toList();
        }

        /**
         * The report line for the only source whose name is {@code nameSuffix} or ends with it after a
         * path separator or {@code !/} (so {@code x.xml} does not also match {@code kopia-x.xml}).
         */
        String lineFor(String nameSuffix) {
            List<String> matching = outLines().stream()
                    .filter(line -> line.contains("  ["))
                    .filter(line -> {
                        String source = line.substring(STATUS_COLUMN, line.indexOf("  ["));
                        return source.equals(nameSuffix) || source.endsWith("/" + nameSuffix)
                                || source.endsWith("\\" + nameSuffix);
                    })
                    .toList();
            assertEquals(1, matching.size(), "lines for " + nameSuffix + " in:\n" + out);
            return matching.getFirst();
        }

        String summary() {
            return outLines().getLast();
        }
    }

    private static Run run(Supplier<CoreServices> services, Object... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        String[] arguments = Arrays.stream(args).map(String::valueOf).toArray(String[]::new);
        int exitCode = Main.run(arguments, new PrintWriter(out), new PrintWriter(err), services);
        return new Run(exitCode, out.toString(), err.toString());
    }

    private static Run inspect(Object... paths) {
        Object[] args = Stream.concat(Stream.of("inspect"), Arrays.stream(paths)).toArray();
        return run(CoreServices::create, args);
    }

    private static Path demo(String relative) {
        assertTrue(Files.isDirectory(DEMO_DIR), "Demo dataset missing: " + DEMO_DIR.toAbsolutePath());
        return DEMO_DIR.resolve(relative);
    }

    private static byte[] demoBytes(String relative) throws IOException {
        return Files.readAllBytes(demo(relative));
    }

    private static String ksefNumber(String fileName) {
        return fileName.substring(0, fileName.length() - ".xml".length());
    }

    // --- demo dataset -------------------------------------------------------------------------

    @Test
    void demoDatasetFolderGivesTheDocumentedOutcomes() {
        Run run = inspect(demo(""));

        assertEquals(ExitCodes.SOME_DOCUMENTS_REJECTED, run.exitCode(), run.out() + run.err());
        assertEquals(13, run.outLines().size(), "12 documents + summary:\n" + run.out());
        assertEquals("12 documents: 7 imported, 1 duplicate, 1 unsupported, 3 invalid, 0 failed.", run.summary());

        for (String sale : List.of(SALE_1, SALE_2, SALE_3, CORRECTION)) {
            assertTrue(run.lineFor(sale).startsWith("Imported     "), run.lineFor(sale));
            assertTrue(run.lineFor(sale).endsWith("[KSeF " + ksefNumber(sale) + "]"), run.lineFor(sale));
        }
        for (String purchase : List.of(PURCHASE_1, PURCHASE_2)) {
            String line = run.lineFor(ZIP + "!/" + purchase);
            assertTrue(line.startsWith("Imported     "), line);
            assertTrue(line.endsWith("[KSeF " + ksefNumber(purchase) + "]"), line);
        }
        assertTrue(run.lineFor(EUR).startsWith("Imported     "), run.lineFor(EUR));
        assertTrue(run.lineFor(EUR).endsWith("[no KSeF number]"), run.lineFor(EUR));

        String duplicate = run.lineFor("kopia-" + SALE_2);
        assertTrue(duplicate.startsWith("Duplicate    "), duplicate);
        assertTrue(duplicate.contains("[same content as "), duplicate);
        assertTrue(duplicate.endsWith(SALE_2 + "]") && !duplicate.endsWith("kopia-" + SALE_2 + "]"),
                "points at the original, not at itself: " + duplicate);

        assertTrue(run.lineFor("faktura-FA2.xml").startsWith("Unsupported  "), run.out());
        for (String invalid : List.of("bez-numeru-faktury.xml", "uszkodzona.xml", "skan-faktury.pdf")) {
            assertTrue(run.lineFor(invalid).startsWith("Invalid      "), run.lineFor(invalid));
        }
        assertTrue(run.lineFor("skan-faktury.pdf").contains("PDF"), run.lineFor("skan-faktury.pdf"));

        // demo-data/README.md is not an invoice: skipped, mentioned once on stderr.
        assertEquals("Note: skipped 1 other file in folders (only .xml, .zip and .pdf files are read from folders).",
                run.err().strip());
    }

    @Test
    void reportNeverContainsInvoiceContents() {
        Run run = inspect(demo(""));
        String everything = run.out() + run.err();

        // Invoice numbers, party names, buyer NIPs and amounts from inside the demo invoices.
        for (String secret : List.of("FV/2026", "FK/2026", "Fikcyjne", "Studio", "5550000001", "7770000001",
                "5460", "492.00")) {
            assertFalse(everything.contains(secret), "'" + secret + "' leaked into:\n" + everything);
        }
    }

    @Test
    void namingTheZipDirectlyReadsItsEntries() {
        Run run = inspect(demo(ZIP));

        assertEquals(ExitCodes.OK, run.exitCode(), run.out() + run.err());
        assertEquals("2 documents: 2 imported, 0 duplicate, 0 unsupported, 0 invalid, 0 failed.", run.summary());
        assertEquals("", run.err());
    }

    // --- exit codes ---------------------------------------------------------------------------

    @Test
    void validInvoicesOnlyExitZeroWithNothingOnStderr() throws IOException {
        Files.write(temp.resolve(SALE_1), demoBytes(SALE_1));
        Files.write(temp.resolve(SALE_2), demoBytes(SALE_2));

        Run run = inspect(temp);

        assertEquals(ExitCodes.OK, run.exitCode(), run.out() + run.err());
        assertEquals("2 documents: 2 imported, 0 duplicate, 0 unsupported, 0 invalid, 0 failed.", run.summary());
        assertEquals("", run.err());
    }

    @Test
    void duplicateInputsAreReportedButNotAnError() throws IOException {
        Path file = Files.write(temp.resolve(SALE_1), demoBytes(SALE_1));

        Run run = inspect(file, file, temp);

        assertEquals(ExitCodes.OK, run.exitCode(), run.out() + run.err());
        List<String> lines = run.outLines();
        assertTrue(lines.get(0).startsWith("Imported     "), run.out());
        assertEquals("Duplicate    " + file + "  [same content as " + file + "]", lines.get(1));
        assertEquals("Duplicate    " + file + "  [same content as " + file + "]", lines.get(2));
        assertEquals("3 documents: 1 imported, 2 duplicate, 0 unsupported, 0 invalid, 0 failed.", run.summary());
    }

    @Test
    void malformedXmlIsInvalidExitTwo() throws IOException {
        Path broken = Files.writeString(temp.resolve("broken.xml"), "<?xml version=\"1.0\"?><Faktura><Naglowek>");

        Run run = inspect(broken);

        assertEquals(ExitCodes.SOME_DOCUMENTS_REJECTED, run.exitCode());
        assertTrue(run.lineFor("broken.xml").startsWith("Invalid      " + broken + "  ["), run.out());
    }

    @Test
    void unsupportedFormatAloneExitsTwo() {
        Run run = inspect(demo("problemy/faktura-FA2.xml"));

        assertEquals(ExitCodes.SOME_DOCUMENTS_REJECTED, run.exitCode(), run.out());
        assertEquals("1 document: 0 imported, 0 duplicate, 1 unsupported, 0 invalid, 0 failed.", run.summary());
    }

    @Test
    void missingFileIsFailedExitThreeAndOtherFilesAreStillReported() throws IOException {
        Path valid = Files.write(temp.resolve(SALE_1), demoBytes(SALE_1));
        Path missing = temp.resolve("does-not-exist.xml");

        Run run = inspect(valid, missing);

        assertEquals(ExitCodes.FATAL, run.exitCode());
        assertTrue(run.lineFor(SALE_1).startsWith("Imported     "), run.out());
        assertEquals("Failed       " + missing + "  [File not found]", run.lineFor("does-not-exist.xml"));
        assertEquals("2 documents: 1 imported, 0 duplicate, 0 unsupported, 0 invalid, 1 failed.", run.summary());
    }

    @Test
    void failedWinsOverInvalid() {
        Run run = inspect(demo("problemy/uszkodzona.xml"), temp.resolve("missing.xml"));

        assertEquals(ExitCodes.FATAL, run.exitCode());
    }

    @Test
    void noPathIsAUsageError() {
        Run run = inspect();

        assertEquals(ExitCodes.USAGE, run.exitCode());
        assertEquals("", run.out());
        assertTrue(run.err().startsWith("Error: Missing required parameter: '<path>'"), run.err());
        assertTrue(run.err().contains("Try 'fakturomost-cli inspect --help'"), run.err());
    }

    @Test
    void emptyFolderIsNotAnErrorButSaysSo() {
        Run run = inspect(temp);

        assertEquals(ExitCodes.OK, run.exitCode());
        assertEquals(List.of("0 documents: 0 imported, 0 duplicate, 0 unsupported, 0 invalid, 0 failed."),
                run.outLines());
        assertEquals("Note: no documents found.", run.err().strip());
    }

    // --- folders --------------------------------------------------------------------------------

    @Test
    void foldersAreWalkedRecursivelyInNameOrderReadingOnlyInvoiceExtensions() throws IOException {
        Files.createDirectories(temp.resolve("b/deeper"));
        Files.createDirectories(temp.resolve("a"));
        Files.write(temp.resolve("b/deeper/" + SALE_2), demoBytes(SALE_2));
        Files.write(temp.resolve("a/" + SALE_1), demoBytes(SALE_1));
        Files.write(temp.resolve("c-UPPER.XML"), demoBytes(EUR));
        Files.writeString(temp.resolve("notes.txt"), "not an invoice");
        Files.writeString(temp.resolve("b/desktop.ini"), "[.ShellClassInfo]");

        Run run = inspect(temp);

        assertEquals(ExitCodes.OK, run.exitCode(), run.out() + run.err());
        assertEquals(List.of(
                "Imported     " + temp.resolve("a").resolve(SALE_1) + "  [KSeF " + ksefNumber(SALE_1) + "]",
                "Imported     " + temp.resolve("b").resolve("deeper").resolve(SALE_2) + "  [KSeF " + ksefNumber(SALE_2) + "]",
                "Imported     " + temp.resolve("c-UPPER.XML") + "  [no KSeF number]",
                "3 documents: 3 imported, 0 duplicate, 0 unsupported, 0 invalid, 0 failed."), run.outLines());
        assertEquals("Note: skipped 2 other files in folders (only .xml, .zip and .pdf files are read from folders).",
                run.err().strip());
    }

    @Test
    void aFileNamedExplicitlyIsReadWhateverItsExtension() throws IOException {
        Path renamed = Files.write(temp.resolve("invoice.txt"), demoBytes(SALE_1));
        Path notes = Files.writeString(temp.resolve("notes.md"), "# not an invoice");

        Run run = inspect(renamed, notes);

        assertTrue(run.lineFor("invoice.txt").startsWith("Imported     "), run.out());
        assertTrue(run.lineFor("notes.md").startsWith("Invalid      "), run.out());
        assertEquals(ExitCodes.SOME_DOCUMENTS_REJECTED, run.exitCode());
        assertEquals("", run.err(), "nothing skipped");
    }

    @Test
    void symbolicLinksInsideFoldersAreNotFollowed() throws IOException {
        Path folder = Files.createDirectories(temp.resolve("in"));
        Files.write(folder.resolve(SALE_1), demoBytes(SALE_1));
        Path outside = Files.write(temp.resolve("outside.xml"), demoBytes(SALE_2));
        createLinkOrSkip(folder.resolve("link-to-file.xml"), outside);
        createLinkOrSkip(folder.resolve("loop"), temp);

        Run run = inspect(folder);

        assertEquals(ExitCodes.OK, run.exitCode(), run.out() + run.err());
        assertEquals("1 document: 1 imported, 0 duplicate, 0 unsupported, 0 invalid, 0 failed.", run.summary());
        assertFalse(run.out().contains("outside.xml") || run.out().contains("link-to-file"), run.out());
        assertEquals("Note: skipped 2 symbolic links in folders (links are not followed).", run.err().strip());
    }

    @Test
    void aLinkNamedExplicitlyIsFollowed() throws IOException {
        Path target = Files.write(temp.resolve(SALE_1), demoBytes(SALE_1));
        Path link = temp.resolve("link.xml");
        createLinkOrSkip(link, target);

        Run run = inspect(link);

        assertEquals(ExitCodes.OK, run.exitCode(), run.out() + run.err());
        assertTrue(run.lineFor("link.xml").startsWith("Imported     "), run.out());
    }

    @Test
    void unreadableFolderIsFailedExitThree() throws IOException {
        Path locked = Files.createDirectories(temp.resolve("locked"));
        Files.write(locked.resolve(SALE_1), demoBytes(SALE_1));
        try {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        } catch (UnsupportedOperationException e) {
            assumeTrue(false, "no POSIX permissions (Windows)");
        }
        try {
            assumeFalse(Files.isReadable(locked), "permissions not enforced (running as root)");

            Run run = inspect(temp);

            assertEquals(ExitCodes.FATAL, run.exitCode(), run.out() + run.err());
            assertTrue(run.lineFor("locked").startsWith("Failed       " + locked + "  [Could not read folder: "),
                    run.out());
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void controlCharactersInFileNamesCannotForgeReportLines() throws IOException {
        Path evil;
        try {
            evil = Files.write(temp.resolve("evil\nImported     fake.xml  [KSeF x].xml"), demoBytes(SALE_1));
        } catch (InvalidPathException | FileSystemException e) {
            assumeTrue(false, "file system does not allow newlines in names (Windows)");
            return;
        }

        Run run = inspect(evil);

        assertEquals(2, run.outLines().size(), "one line + summary:\n" + run.out());
        assertTrue(run.outLines().getFirst().contains("evil?Imported"), run.out());
    }

    // --- malicious ZIP --------------------------------------------------------------------------

    @Test
    void maliciousZipIsReportedEntryByEntryAndNothingIsExtracted() throws IOException {
        byte[] nestedZip = zip(new Entry("inner.xml", demoBytes(SALE_2)));
        byte[] bomb = new byte[11 * 1024 * 1024]; // zeros: compresses to a few KiB, above the 10 MiB limit
        Path archive = Files.write(temp.resolve("evil.zip"), zip(
                new Entry("../../" + SALE_1, demoBytes(SALE_1)),
                new Entry("nested.zip", nestedZip),
                new Entry("bomb.xml", bomb)));
        assertTrue(Files.size(archive) < 1024 * 1024, "the archive itself is small");

        Run run = inspect(archive);

        assertEquals(ExitCodes.SOME_DOCUMENTS_REJECTED, run.exitCode(), run.out() + run.err());
        // A traversal name is only a label: shown as is, nothing written anywhere.
        assertTrue(run.lineFor("evil.zip!/../../" + SALE_1).startsWith("Imported     "), run.out());
        assertTrue(run.lineFor("evil.zip!/nested.zip").startsWith("Invalid      "), run.out());
        assertTrue(run.lineFor("evil.zip!/nested.zip").contains("Nested ZIP"), run.out());
        assertTrue(run.lineFor("evil.zip!/bomb.xml").startsWith("Invalid      "), run.out());
        assertTrue(run.lineFor("evil.zip!/bomb.xml").contains("Larger than the limit"), run.out());
        assertEquals("3 documents: 1 imported, 0 duplicate, 0 unsupported, 2 invalid, 0 failed.", run.summary());

        try (Stream<Path> files = Files.walk(temp)) {
            assertEquals(List.of(temp, archive), files.sorted().toList(), "nothing extracted");
        }
        assertFalse(Files.exists(temp.getParent().resolve(SALE_1)), "nothing escaped the folder");
        assertFalse(Files.exists(temp.getParent().getParent().resolve(SALE_1)), "nothing escaped the folder");
    }

    @Test
    void zipOverTheEntryLimitImportsNothing() throws IOException {
        Path archive = Files.write(temp.resolve("many.zip"), zip(
                new Entry("1.xml", demoBytes(SALE_1)), new Entry("2.xml", demoBytes(SALE_2)),
                new Entry("3.xml", demoBytes(SALE_3))));
        ImportLimits twoEntries = new ImportLimits(ImportLimits.DEFAULT.maxDocumentBytes(), 2,
                ImportLimits.DEFAULT.maxZipUncompressedBytes());

        Run run = run(() -> CoreServices.create(twoEntries), "inspect", archive);

        assertEquals(ExitCodes.SOME_DOCUMENTS_REJECTED, run.exitCode());
        assertEquals(List.of("Invalid      " + archive + "  [ZIP archive has more than 2 entries; nothing imported]",
                "1 document: 0 imported, 0 duplicate, 0 unsupported, 1 invalid, 0 failed."), run.outLines());
    }

    // --- help -----------------------------------------------------------------------------------

    @Test
    void inspectHelpDescribesTheCommandAndItsExitCodes() {
        Run run = run(CoreServices::create, "inspect", "--help");

        assertEquals(ExitCodes.OK, run.exitCode());
        assertTrue(run.out().startsWith("Usage: fakturomost-cli inspect"), run.out());
        String flat = run.out().replaceAll("\\s+", " ");
        assertTrue(flat.contains("never invoice contents"), run.out());
        assertTrue(flat.contains("symbolic links are not followed"), run.out());
        assertTrue(flat.contains("Exit codes: 0 Every document"), run.out());
        assertEquals("", run.err());
    }

    @Test
    void rootHelpListsInspect() {
        Run run = run(CoreServices::create, "--help");

        assertTrue(run.out().replaceAll("\\s+", " ").contains("Commands: inspect Check FA(3) invoice files"),
                run.out());
    }

    // --- helpers --------------------------------------------------------------------------------

    private record Entry(String name, byte[] bytes) {
    }

    private static byte[] zip(Entry... entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Entry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.bytes());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void createLinkOrSkip(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | FileSystemException e) {
            // Windows without developer mode / admin rights.
            assumeTrue(false, "cannot create symbolic links here: " + e.getClass().getSimpleName());
        }
    }
}
