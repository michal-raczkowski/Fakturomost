package dev.michalraczkowski.fakturomost.core.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import dev.michalraczkowski.fakturomost.core.domain.KsefNumber;
import dev.michalraczkowski.fakturomost.core.domain.OriginalDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Writing exports to files: original XML to a folder or ZIP, CSV/JSON files, overwrite rules. */
class FileExportTest {

    private final ExportTestData data = new ExportTestData();
    private final InvoiceExports exports = new InvoiceExports(data.store);

    @TempDir
    Path dir;

    // --- Original XML ----------------------------------------------------------------------------

    @Test
    void writesOriginalsUnchangedToAFolderNamedByKsefNumberOrDocumentId() throws IOException {
        Path folder = dir.resolve("out/xml");

        List<Path> written = exports.exportOriginals(
                List.of(data.vat().id(), data.correction().id(), data.fa2Id()), folder, false);

        assertEquals(List.of(folder.resolve(ExportTestData.KSEF_VAT + ".xml"),
                folder.resolve(ExportTestData.KSEF_CORRECTION + ".xml"),
                folder.resolve(data.fa2Id().sha256Hex() + ".xml")), written);
        assertArrayEquals(data.vatXml, Files.readAllBytes(written.get(0)), "byte-identical");
        assertArrayEquals(data.correctionXml, Files.readAllBytes(written.get(1)));
        assertArrayEquals(data.fa2Xml, Files.readAllBytes(written.get(2)), "unsupported formats are exported too");
    }

    @Test
    void writesOriginalsToAZipWhenTheTargetEndsWithZip() throws IOException {
        Path zip = dir.resolve("faktury.ZIP");

        List<Path> written = exports.exportOriginals(List.of(data.vat().id(), data.correction().id()), zip, false);

        assertEquals(List.of(zip), written);
        Map<String, byte[]> entries = unzip(zip);
        assertEquals(List.of(ExportTestData.KSEF_VAT + ".xml", ExportTestData.KSEF_CORRECTION + ".xml"),
                List.copyOf(entries.keySet()));
        assertArrayEquals(data.vatXml, entries.get(ExportTestData.KSEF_VAT + ".xml"));
    }

    @Test
    void exportsEachDocumentOnceAndAvoidsNameClashes() throws IOException {
        // Two different documents that both claim the same KSeF number (e.g. a hand-edited copy).
        byte[] edited = (new String(data.vatXml, StandardCharsets.UTF_8) + " ").getBytes(StandardCharsets.UTF_8);
        data.store.addIfAbsent(new StoredDocument(OriginalDocument.of(edited), "copy.xml",
                new KsefNumber(ExportTestData.KSEF_VAT), data.vat().invoice()));
        DocumentId editedId = DocumentId.of(edited);

        List<Path> written = exports.exportOriginals(
                List.of(data.vat().id(), editedId, data.vat().id()), dir, false);

        assertEquals(List.of(dir.resolve(ExportTestData.KSEF_VAT + ".xml"),
                dir.resolve(ExportTestData.KSEF_VAT + "-" + editedId.shortForm() + ".xml")), written);
        assertArrayEquals(edited, Files.readAllBytes(written.get(1)));
    }

    @Test
    void refusesToOverwriteAndThenWritesNothing() throws IOException {
        Path existing = Files.writeString(dir.resolve(ExportTestData.KSEF_CORRECTION + ".xml"), "mine");

        assertThrows(FileAlreadyExistsException.class, () -> exports.exportOriginals(
                List.of(data.vat().id(), data.correction().id()), dir, false));

        assertEquals("mine", Files.readString(existing));
        assertFalse(Files.exists(dir.resolve(ExportTestData.KSEF_VAT + ".xml")), "no partial export");
    }

    @Test
    void overwritesWhenAskedAndLeavesNoTemporaryFiles() throws IOException {
        Files.writeString(dir.resolve(ExportTestData.KSEF_VAT + ".xml"), "old");

        exports.exportOriginals(List.of(data.vat().id()), dir, true);

        assertArrayEquals(data.vatXml, Files.readAllBytes(dir.resolve(ExportTestData.KSEF_VAT + ".xml")));
        assertEquals(List.of(dir.resolve(ExportTestData.KSEF_VAT + ".xml")), listFiles(dir));
    }

    @Test
    void rejectsUnknownDocumentsBeforeWritingAnything() {
        DocumentId unknown = DocumentId.of("nothing".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class,
                () -> exports.exportOriginals(List.of(data.vat().id(), unknown), dir.resolve("x"), false));

        assertFalse(Files.exists(dir.resolve("x")));
    }

    // --- CSV and JSON files ----------------------------------------------------------------------

    @Test
    void writesCsvAndJsonFilesCreatingFolders() throws IOException {
        Path csv = dir.resolve("a/b/faktury.csv");
        Path json = dir.resolve("a/b/faktury.json");

        exports.exportCsv(List.of(data.vat()), CsvOptions.excelPl(), csv, false);
        exports.exportJson(List.of(data.vat()), json, false);

        assertTrue(Files.size(csv) > 0);
        assertTrue(Files.readString(json).contains("\"fakturomost-invoices\""));
    }

    @Test
    void refusesToOverwriteAnExistingExportFile() throws IOException {
        Path csv = Files.writeString(dir.resolve("faktury.csv"), "previous export");

        assertThrows(FileAlreadyExistsException.class,
                () -> exports.exportCsv(List.of(data.vat()), CsvOptions.excelPl(), csv, false));

        assertEquals("previous export", Files.readString(csv));
    }

    @Test
    void removesItsOwnPartialFileWhenWritingFails() {
        Path csv = dir.resolve("faktury.csv");
        List<InvoiceEntry> broken = new ArrayList<>(Arrays.asList(data.vat(), null)); // fails mid-export

        assertThrows(NullPointerException.class,
                () -> exports.exportCsv(broken, CsvOptions.excelPl(), csv, false));

        assertFalse(Files.exists(csv));
    }

    @Test
    void keepsThePreviousFileWhenAnOverwriteFails() throws IOException {
        Path json = Files.writeString(dir.resolve("faktury.json"), "previous export");
        List<InvoiceEntry> broken = new ArrayList<>(Arrays.asList(data.vat(), null));

        assertThrows(NullPointerException.class, () -> exports.exportJson(broken, json, true));

        assertEquals("previous export", Files.readString(json));
        assertEquals(List.of(json), listFiles(dir), "no temporary file left behind");
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private static Map<String, byte[]> unzip(Path zip) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    private static List<Path> listFiles(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.sorted().toList();
        }
    }
}
