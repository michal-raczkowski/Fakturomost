package dev.michalraczkowski.fakturomost.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.michalraczkowski.fakturomost.core.fa3.Fa3Parser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link InvoiceImporter#importInputs}: folder expansion (moved from the CLI in M4), progress and
 * cancellation. The CLI's {@code InspectCommandTest} still checks the same rules end to end.
 */
class InputImportTest {

    private static final String KSEF_1 = "9999999999-20261020-0123456789AB-F2";
    private static final String KSEF_2 = "9999999999-20261021-ABCDEF012345-7A";

    private static final byte[] VAT = fixture("ksef-style-vat.xml");
    private static final byte[] CORRECTION = fixture("correction-eur-prefixed.xml");
    /** A third distinct, valid document: same invoice, different bytes (trailing newline). */
    private static final byte[] VAT_VARIANT = (new String(VAT, StandardCharsets.UTF_8) + "\n")
            .getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    private final InMemoryDocumentStore store = new InMemoryDocumentStore();
    private final InvoiceImporter importer = new InvoiceImporter(new Fa3Parser(), store);

    // --- Folder expansion ------------------------------------------------------------------------

    @Test
    void foldersAreWalkedRecursivelyInNameOrderReadingOnlyInvoiceExtensions() throws IOException {
        Files.createDirectories(dir.resolve("b/deeper"));
        Files.createDirectories(dir.resolve("a"));
        Path second = Files.write(dir.resolve("b/deeper/" + KSEF_2 + ".xml"), CORRECTION);
        Path first = Files.write(dir.resolve("a/" + KSEF_1 + ".xml"), VAT);
        Path third = Files.write(dir.resolve("c-UPPER.XML"), VAT_VARIANT);
        Files.writeString(dir.resolve("notes.txt"), "not an invoice");
        Files.writeString(dir.resolve("b/desktop.ini"), "[.ShellClassInfo]");

        ImportBatch batch = importer.importInputs(List.of(dir));

        assertEquals(List.of(first.toString(), second.toString(), third.toString()), names(batch));
        batch.results().forEach(r -> assertInstanceOf(ImportResult.Imported.class, r));
        assertEquals(2, batch.skippedFiles());
        assertEquals(0, batch.skippedLinks());
        assertFalse(batch.cancelled());
        assertEquals(Set.of(first.toRealPath(), second.toRealPath(), third.toRealPath()), batch.filesRead());
        assertEquals(3, store.size());
    }

    @Test
    void zipAndPdfFilesInFoldersAreRead() throws IOException {
        Files.write(dir.resolve("pack.zip"), zip(KSEF_1 + ".xml", VAT, KSEF_2 + ".xml", CORRECTION));
        Files.write(dir.resolve("scan.PDF"), "%PDF-1.7 not really".getBytes(StandardCharsets.US_ASCII));

        ImportBatch batch = importer.importInputs(List.of(dir));

        assertEquals(List.of(dir.resolve("pack.zip") + "!/" + KSEF_1 + ".xml",
                dir.resolve("pack.zip") + "!/" + KSEF_2 + ".xml",
                dir.resolve("scan.PDF").toString()), names(batch));
        ImportResult.Invalid pdf = assertInstanceOf(ImportResult.Invalid.class, batch.results().get(2));
        assertTrue(pdf.reason().startsWith("PDF file"), pdf.reason());
        assertEquals(0, batch.skippedFiles());
    }

    @Test
    void aFileNamedDirectlyIsReadWhateverItsExtension() throws IOException {
        Path renamed = Files.write(dir.resolve("invoice.txt"), VAT);
        Path notes = Files.writeString(dir.resolve("notes.md"), "# not an invoice");

        ImportBatch batch = importer.importInputs(List.of(renamed, notes));

        assertInstanceOf(ImportResult.Imported.class, batch.results().get(0));
        assertInstanceOf(ImportResult.Invalid.class, batch.results().get(1));
        assertEquals(0, batch.skippedFiles());
    }

    @Test
    void aMissingInputIsFailedAndNotCountedAsRead() {
        Path missing = dir.resolve("missing.xml");

        ImportBatch batch = importer.importInputs(List.of(missing));

        ImportResult.Failed failed = assertInstanceOf(ImportResult.Failed.class, batch.results().getFirst());
        assertEquals("File not found", failed.reason());
        assertEquals(Set.of(), batch.filesRead());
    }

    @Test
    void symbolicLinksInsideFoldersAreNotFollowed() throws IOException {
        Path folder = Files.createDirectories(dir.resolve("in"));
        Path inside = Files.write(folder.resolve(KSEF_1 + ".xml"), VAT);
        Path outside = Files.write(dir.resolve("outside.xml"), CORRECTION);
        createLinkOrSkip(folder.resolve("link-to-file.xml"), outside);
        createLinkOrSkip(folder.resolve("loop"), dir);

        ImportBatch batch = importer.importInputs(List.of(folder));

        assertEquals(List.of(inside.toString()), names(batch));
        assertEquals(2, batch.skippedLinks());
        assertEquals(Set.of(inside.toRealPath()), batch.filesRead());
    }

    @Test
    void aLinkNamedDirectlyIsFollowed() throws IOException {
        Path target = Files.write(dir.resolve(KSEF_1 + ".xml"), VAT);
        Path link = dir.resolve("link.xml");
        createLinkOrSkip(link, target);

        ImportBatch batch = importer.importInputs(List.of(link));

        assertInstanceOf(ImportResult.Imported.class, batch.results().getFirst());
        assertEquals(Set.of(target.toRealPath()), batch.filesRead());
    }

    @Test
    void anUnreadableFolderIsFailed() throws IOException {
        Path locked = Files.createDirectories(dir.resolve("locked"));
        Files.write(locked.resolve(KSEF_1 + ".xml"), VAT);
        try {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        } catch (UnsupportedOperationException e) {
            assumeTrue(false, "no POSIX permissions (Windows)");
        }
        try {
            assumeFalse(Files.isReadable(locked), "permissions not enforced (running as root)");

            ImportBatch batch = importer.importInputs(List.of(dir));

            ImportResult.Failed failed = assertInstanceOf(ImportResult.Failed.class, batch.results().getFirst());
            assertEquals(locked.toString(), failed.sourceName());
            assertTrue(failed.reason().startsWith("Could not read folder: "), failed.reason());
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }

    // --- Progress and cancellation ---------------------------------------------------------------

    @Test
    void progressHearsEveryResultInOrder() throws IOException {
        Files.write(dir.resolve("a.zip"), zip(KSEF_1 + ".xml", VAT, KSEF_2 + ".xml", CORRECTION));
        Files.write(dir.resolve("b.xml"), VAT); // duplicate of the first entry
        Files.writeString(dir.resolve("c.xml"), "<broken");
        List<ImportResult> heard = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();

        ImportBatch batch = importer.importInputs(List.of(dir), new ImportProgress() {
            @Override
            public void resultAdded(ImportResult result, int resultsSoFar) {
                heard.add(result);
                counts.add(resultsSoFar);
            }
        });

        assertEquals(batch.results(), heard);
        assertEquals(List.of(1, 2, 3, 4), counts);
        assertInstanceOf(ImportResult.Duplicate.class, heard.get(2));
        assertInstanceOf(ImportResult.Invalid.class, heard.get(3));
    }

    @Test
    void cancellingBeforeTheStartReadsNothing() throws IOException {
        Files.write(dir.resolve(KSEF_1 + ".xml"), VAT);

        ImportBatch batch = importer.importInputs(List.of(dir), cancelAfter(0));

        assertTrue(batch.cancelled());
        assertEquals(List.of(), batch.results());
        assertEquals(Set.of(), batch.filesRead());
        assertEquals(0, store.size());
    }

    @Test
    void cancellingInsideAZipStopsBeforeTheNextEntryAndKeepsWhatWasImported() throws IOException {
        Path zip = Files.write(dir.resolve("pack.zip"), zip(KSEF_1 + ".xml", VAT, KSEF_2 + ".xml", CORRECTION));
        Path later = Files.write(dir.resolve("later.xml"), VAT_VARIANT);

        // Asked before the file, before entry 1, then before entry 2 -> cancel there.
        ImportBatch batch = importer.importInputs(List.of(zip, later), cancelAfter(2));

        assertTrue(batch.cancelled());
        assertEquals(List.of(zip + "!/" + KSEF_1 + ".xml"), names(batch));
        assertEquals(1, store.size(), "the document imported before cancelling stays");
        assertFalse(batch.filesRead().contains(later.toRealPath()), "nothing after the cancel point is read");
    }

    @Test
    void cancellingBetweenFilesInAFolderStopsTheWalk() throws IOException {
        Files.write(dir.resolve("1.xml"), VAT);
        Files.write(dir.resolve("2.xml"), CORRECTION);
        Files.write(dir.resolve("3.xml"), VAT_VARIANT);

        // Per file: asked before reading it and before its one document.
        ImportBatch batch = importer.importInputs(List.of(dir), cancelAfter(4));

        assertTrue(batch.cancelled());
        assertEquals(List.of(dir.resolve("1.xml").toString(), dir.resolve("2.xml").toString()), names(batch));
        assertEquals(2, store.size());
    }

    @Test
    void anUncancelledImportIsNotMarkedCancelled() throws IOException {
        Files.write(dir.resolve(KSEF_1 + ".xml"), VAT);

        assertFalse(importer.importInputs(List.of(dir), cancelAfter(Integer.MAX_VALUE)).cancelled());
    }

    // --- helpers ---------------------------------------------------------------------------------

    /** Answers "not cancelled" {@code allowed} times, then "cancelled". */
    private static ImportProgress cancelAfter(int allowed) {
        AtomicInteger asked = new AtomicInteger();
        return new ImportProgress() {
            @Override
            public boolean isCancelled() {
                return asked.getAndIncrement() >= allowed;
            }
        };
    }

    private static List<String> names(ImportBatch batch) {
        return batch.results().stream().map(ImportResult::sourceName).toList();
    }

    private static byte[] zip(Object... nameBytesPairs) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (int i = 0; i < nameBytesPairs.length; i += 2) {
                zip.putNextEntry(new ZipEntry((String) nameBytesPairs[i]));
                zip.write((byte[]) nameBytesPairs[i + 1]);
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

    private static byte[] fixture(String name) {
        try (InputStream in = InputImportTest.class.getResourceAsStream("/fa3/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing test fixture " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
