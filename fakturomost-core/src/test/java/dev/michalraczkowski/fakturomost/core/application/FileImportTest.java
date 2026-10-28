package dev.michalraczkowski.fakturomost.core.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.michalraczkowski.fakturomost.core.domain.KsefNumber;
import dev.michalraczkowski.fakturomost.core.fa3.Fa3Parser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link InvoiceImporter#importPaths}: XML files and ZIP archives, including hostile ones. */
class FileImportTest {

    private static final String KSEF_1 = "9999999999-20261020-0123456789AB-F2";
    private static final String KSEF_2 = "9999999999-20261021-ABCDEF012345-7A";

    private static final byte[] VAT = fixture("ksef-style-vat.xml");
    private static final byte[] CORRECTION = fixture("correction-eur-prefixed.xml");

    @TempDir
    Path dir;

    private final InMemoryDocumentStore store = new InMemoryDocumentStore();
    private final InvoiceImporter importer = new InvoiceImporter(new Fa3Parser(), store);

    // --- Plain XML files -------------------------------------------------------------------------

    @Test
    void importsAnXmlFileWithTheKsefNumberFromItsName() throws IOException {
        Path file = Files.write(dir.resolve(KSEF_1 + ".xml"), VAT);

        ImportResult.Imported imported = assertInstanceOf(ImportResult.Imported.class, only(importer.importPaths(List.of(file))));

        assertEquals(file.toString(), imported.sourceName());
        assertEquals(new KsefNumber(KSEF_1), imported.ksefNumber());
    }

    @Test
    void doesNotModifyImportedFiles() throws IOException {
        Path xml = Files.write(dir.resolve(KSEF_1 + ".xml"), VAT);
        Path zip = writeZip("pack.zip", ordered(KSEF_2 + ".xml", CORRECTION));
        byte[] zipBefore = Files.readAllBytes(zip);

        importer.importPaths(List.of(xml, zip));

        assertTrue(java.util.Arrays.equals(VAT, Files.readAllBytes(xml)));
        assertTrue(java.util.Arrays.equals(zipBefore, Files.readAllBytes(zip)));
    }

    @Test
    void rejectsAnOversizedFileWithoutReadingItAll() throws IOException {
        Path file = Files.write(dir.resolve("big.xml"), new byte[2_000]);
        InvoiceImporter strict = new InvoiceImporter(new Fa3Parser(), store, new ImportLimits(1_000, 10, 10_000));

        ImportResult.Invalid invalid = assertInstanceOf(ImportResult.Invalid.class, only(strict.importPaths(List.of(file))));

        assertNull(invalid.id());
        assertTrue(invalid.reason().startsWith("Larger than the limit"), invalid.reason());
    }

    @Test
    void reportsMissingFilesAndDirectories() {
        List<ImportResult> results = importer.importPaths(List.of(dir.resolve("missing.xml"), dir));

        assertEquals("File not found", assertInstanceOf(ImportResult.Failed.class, results.get(0)).reason());
        assertEquals("Not a regular file", assertInstanceOf(ImportResult.Failed.class, results.get(1)).reason());
    }

    @Test
    void explainsThatAPdfIsNotAnXmlInvoice() throws IOException {
        Path pdf = Files.write(dir.resolve(KSEF_1 + ".pdf"), "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII));

        ImportResult.Invalid invalid = assertInstanceOf(ImportResult.Invalid.class, only(importer.importPaths(List.of(pdf))));

        assertTrue(invalid.reason().startsWith("PDF file"), invalid.reason());
    }

    // --- ZIP archives ----------------------------------------------------------------------------

    @Test
    void importsEveryEntryOfAKsefStyleZip() throws IOException {
        Path zip = writeZip("pack1.zip", ordered(KSEF_1 + ".xml", VAT, KSEF_2 + ".xml", CORRECTION));

        List<ImportResult> results = importer.importPaths(List.of(zip));

        assertEquals(2, results.size());
        ImportResult.Imported first = assertInstanceOf(ImportResult.Imported.class, results.get(0));
        ImportResult.Imported second = assertInstanceOf(ImportResult.Imported.class, results.get(1));
        assertEquals(zip + "!/" + KSEF_1 + ".xml", first.sourceName());
        assertEquals(new KsefNumber(KSEF_1), first.ksefNumber());
        assertEquals(new KsefNumber(KSEF_2), second.ksefNumber());
        assertEquals(2, store.size());
    }

    @Test
    void detectsTheSameInvoiceAcrossFilesAndArchives() throws IOException {
        Path single = Files.write(dir.resolve(KSEF_1 + ".xml"), VAT);
        Path zip = writeZip("pack.zip", ordered(KSEF_1 + ".xml", VAT, KSEF_2 + ".xml", CORRECTION));

        List<ImportResult> results = importer.importPaths(List.of(single, zip, zip));

        assertEquals(List.of("Imported", "Duplicate", "Imported", "Duplicate", "Duplicate"), kinds(results));
        assertEquals(2, store.size());
    }

    @Test
    void detectsZipsByContentNotByExtension() throws IOException {
        Path disguised = writeZip("download.xml", ordered(KSEF_1 + ".xml", VAT));

        assertInstanceOf(ImportResult.Imported.class, only(importer.importPaths(List.of(disguised))));
    }

    @Test
    void readsKsefNumbersFromEntriesInFoldersAndSkipsFolderEntries() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("2026/", new byte[0]);
        entries.put("2026/10/" + KSEF_1 + ".xml", VAT);
        Path zip = writeZip("nested-folders.zip", entries);

        ImportResult.Imported imported = assertInstanceOf(ImportResult.Imported.class, only(importer.importPaths(List.of(zip))));

        assertEquals(new KsefNumber(KSEF_1), imported.ksefNumber());
    }

    @Test
    void reportsEachProblemEntryAndImportsTheRest() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(KSEF_1 + ".pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        entries.put("inner.zip", zipBytes(ordered(KSEF_2 + ".xml", CORRECTION)));
        entries.put("broken.xml", "<Faktura".getBytes(StandardCharsets.UTF_8));
        entries.put(KSEF_1 + ".xml", VAT);
        Path zip = writeZip("mixed.zip", entries);

        List<ImportResult> results = importer.importPaths(List.of(zip));

        assertEquals(List.of("Invalid", "Invalid", "Invalid", "Imported"), kinds(results));
        assertTrue(((ImportResult.Invalid) results.get(0)).reason().startsWith("PDF file"));
        assertEquals("Nested ZIP archives are not imported", ((ImportResult.Invalid) results.get(1)).reason());
        assertEquals(1, store.size(), "the nested archive was not opened");
    }

    @Test
    void neverWritesEntriesToDiskWhateverTheirNames() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("../../escaped-" + KSEF_1 + ".xml", VAT);
        entries.put("/absolute/" + KSEF_2 + ".xml", CORRECTION);
        Path zip = writeZip("slip.zip", entries);
        List<Path> before = listTree(dir);

        List<ImportResult> results = importer.importPaths(List.of(zip));

        assertEquals(List.of("Imported", "Imported"), kinds(results));
        assertEquals(before, listTree(dir), "no files were created next to the archive");
        assertFalse(Files.exists(dir.getParent().resolve("escaped-" + KSEF_1 + ".xml")));
        assertFalse(Files.exists(dir.getParent().getParent().resolve("escaped-" + KSEF_1 + ".xml")));
    }

    @Test
    void neutralisesControlCharactersInEntryNames() throws IOException {
        Path zip = writeZip("names.zip", ordered("evil\r\nFAKE LOG LINE\u001b[31m.xml", VAT));

        String name = only(importer.importPaths(List.of(zip))).sourceName();

        assertFalse(name.chars().anyMatch(Character::isISOControl), name);
        assertTrue(name.endsWith("evil??FAKE LOG LINE?[31m.xml"), name);
    }

    // --- Archive limits and hostile archives -----------------------------------------------------

    @Test
    void rejectsArchivesWithTooManyEntriesBeforeReadingAny() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            entries.put("e" + i + ".xml", VAT);
        }
        Path zip = writeZip("many.zip", entries);
        InvoiceImporter strict = new InvoiceImporter(new Fa3Parser(), store, new ImportLimits(1_000_000, 3, 100_000_000));

        ImportResult.Invalid invalid = assertInstanceOf(ImportResult.Invalid.class, only(strict.importPaths(List.of(zip))));

        assertTrue(invalid.reason().contains("more than 3 entries"), invalid.reason());
        assertEquals(0, store.size());
    }

    @Test
    void rejectsArchivesDeclaringTooMuchDataBeforeReadingAny() throws IOException {
        Path zip = writeZip("large.zip", ordered(KSEF_1 + ".xml", VAT, KSEF_2 + ".xml", CORRECTION));
        InvoiceImporter strict = new InvoiceImporter(new Fa3Parser(), store,
                new ImportLimits(1_000_000, 100, VAT.length + 10L));

        ImportResult.Invalid invalid = assertInstanceOf(ImportResult.Invalid.class, only(strict.importPaths(List.of(zip))));

        assertTrue(invalid.reason().contains("nothing imported"), invalid.reason());
        assertEquals(0, store.size());
    }

    @Test
    void rejectsAnOversizedEntryAndContinues() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("huge.xml", new byte[50_000]);
        entries.put(KSEF_1 + ".xml", VAT);
        Path zip = writeZip("entry-too-big.zip", entries);
        InvoiceImporter strict = new InvoiceImporter(new Fa3Parser(), store,
                new ImportLimits(VAT.length + 100, 100, 10_000_000));

        List<ImportResult> results = strict.importPaths(List.of(zip));

        assertEquals(List.of("Invalid", "Imported"), kinds(results));
        assertTrue(((ImportResult.Invalid) results.get(0)).reason().startsWith("Larger than the limit"));
    }

    @Test
    void stopsAZipBombWhoseHeadersUnderstateItsSize() throws IOException {
        // 200 MB of zeros compresses to ~200 KB; the central directory then claims 100 bytes.
        // The JDK inflates past a forged size, so only counting bytes actually read stops this.
        byte[] bomb = zipOfZeros("bomb.xml", 200_000_000);
        forgeUncompressedSizes(bomb, 100);
        Path zip = Files.write(dir.resolve("bomb.zip"), bomb);
        InvoiceImporter guarded = new InvoiceImporter(new Fa3Parser(), store, new ImportLimits(10_000_000, 100, 1_000_000));

        List<ImportResult> results = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> guarded.importPaths(List.of(zip)));

        ImportResult.Invalid invalid = assertInstanceOf(ImportResult.Invalid.class, only(results));
        assertEquals(zip.toString(), invalid.sourceName());
        assertTrue(invalid.reason().contains("exceeds"), invalid.reason());
    }

    @Test
    void capsEachEntryEvenWithDefaultLimits() throws IOException {
        // A single highly compressible 64 MB entry: rejected after reading just over 10 MiB.
        Path zip = Files.write(dir.resolve("big-entry.zip"), zipOfZeros("zeros.xml", 64 * 1024 * 1024));

        ImportResult.Invalid invalid = assertInstanceOf(ImportResult.Invalid.class,
                only(assertTimeoutPreemptively(Duration.ofSeconds(10), () -> importer.importPaths(List.of(zip)))));

        assertTrue(invalid.reason().startsWith("Larger than the limit"), invalid.reason());
    }

    @Test
    void reportsCorruptArchives() throws IOException {
        byte[] truncated = java.util.Arrays.copyOf(zipBytes(ordered(KSEF_1 + ".xml", VAT)), 60);
        Path zip = Files.write(dir.resolve("truncated.zip"), truncated);

        ImportResult.Invalid invalid = assertInstanceOf(ImportResult.Invalid.class, only(importer.importPaths(List.of(zip))));

        assertEquals("Not a readable ZIP archive", invalid.reason());
    }

    @Test
    void reportsAStoredEntryWhoseContentDoesNotMatchItsChecksum() throws IOException {
        byte[] zip = zipBytes(ordered(KSEF_1 + ".xml", VAT), ZipEntry.STORED);
        int dataStart = indexOf(zip, "<?xml".getBytes(StandardCharsets.US_ASCII));
        zip[dataStart + 100] ^= 1; // flip a bit inside the stored invoice
        Path file = Files.write(dir.resolve("bitrot.zip"), zip);

        ImportResult.Invalid invalid = assertInstanceOf(ImportResult.Invalid.class, only(importer.importPaths(List.of(file))));

        assertEquals("Corrupt ZIP entry (checksum mismatch)", invalid.reason());
        assertEquals(0, store.size());
    }

    @Test
    void reportsAChecksumMismatchEvenWhenTheDamagedInvoiceIsStillValid() throws IOException {
        // Change the gross total 1440.53 -> 1440.52: still well-formed and schema-valid, so only
        // the CRC check can notice. This is the case that matters for financial data.
        byte[] zip = zipBytes(ordered(KSEF_1 + ".xml", VAT), ZipEntry.STORED);
        int amount = indexOf(zip, "<P_15>1440.53</P_15>".getBytes(StandardCharsets.US_ASCII));
        zip[amount + "<P_15>1440.5".length()] = '2';
        Path file = Files.write(dir.resolve("altered.zip"), zip);

        ImportResult.Invalid invalid = assertInstanceOf(ImportResult.Invalid.class, only(importer.importPaths(List.of(file))));

        assertEquals("Corrupt ZIP entry (checksum mismatch)", invalid.reason());
        assertEquals(0, store.size());
    }

    @Test
    void importsAnEmptyArchiveAsNothing() throws IOException {
        Path zip = writeZip("empty.zip", ordered());

        assertEquals(List.of(), importer.importPaths(List.of(zip)));
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private Path writeZip(String name, Map<String, byte[]> entries) throws IOException {
        return Files.write(dir.resolve(name), zipBytes(entries));
    }

    private static byte[] zipBytes(Map<String, byte[]> entries) throws IOException {
        return zipBytes(entries, ZipEntry.DEFLATED);
    }

    private static byte[] zipBytes(Map<String, byte[]> entries, int method) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                if (method == ZipEntry.STORED) {
                    CRC32 crc = new CRC32();
                    crc.update(e.getValue());
                    entry.setMethod(ZipEntry.STORED);
                    entry.setSize(e.getValue().length);
                    entry.setCompressedSize(e.getValue().length);
                    entry.setCrc(crc.getValue());
                }
                zip.putNextEntry(entry);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** One DEFLATED entry of {@code size} zero bytes, written in chunks to keep test memory small. */
    private static byte[] zipOfZeros(String name, long size) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[1 << 20];
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(name));
            for (long written = 0; written < size; written += chunk.length) {
                zip.write(chunk, 0, (int) Math.min(chunk.length, size - written));
            }
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    /** Entries in the given order (unlike {@code Map.of}). */
    private static Map<String, byte[]> ordered(Object... namesAndContents) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < namesAndContents.length; i += 2) {
            entries.put((String) namesAndContents[i], (byte[]) namesAndContents[i + 1]);
        }
        return entries;
    }

    /** Overwrites the uncompressed size in every central directory header. */
    private static void forgeUncompressedSizes(byte[] zip, int size) {
        ByteBuffer buffer = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i + 28 <= zip.length; i++) {
            if (buffer.getInt(i) == 0x02014b50) {
                buffer.putInt(i + 24, size);
            }
        }
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        throw new IllegalArgumentException("not found");
    }

    private static List<Path> listTree(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.sorted().toList();
        }
    }

    private static ImportResult only(List<ImportResult> results) {
        assertEquals(1, results.size(), String.valueOf(results));
        return results.get(0);
    }

    private static List<String> kinds(List<ImportResult> results) {
        return results.stream().map(r -> r.getClass().getSimpleName()).toList();
    }

    private static byte[] fixture(String name) {
        try (InputStream in = FileImportTest.class.getResourceAsStream("/fa3/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing test fixture " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
