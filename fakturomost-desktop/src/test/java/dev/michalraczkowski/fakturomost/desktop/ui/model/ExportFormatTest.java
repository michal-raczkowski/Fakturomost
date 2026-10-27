package dev.michalraczkowski.fakturomost.desktop.ui.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.michalraczkowski.fakturomost.core.application.CsvOptions;
import dev.michalraczkowski.fakturomost.core.application.InvoiceEntry;
import dev.michalraczkowski.fakturomost.core.application.InvoiceQuery;
import dev.michalraczkowski.fakturomost.desktop.DemoData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportFormatTest {

    @TempDir
    Path dir;

    private final DemoData.Services services = DemoData.Services.create();
    private final List<InvoiceEntry> selection;

    ExportFormatTest() {
        services.importDemoFolder();
        // Two invoices in table order, as a multi-selection would give them.
        selection = services.catalog().search(InvoiceQuery.all()).subList(0, 2);
    }

    // --- target names -----------------------------------------------------------------------------

    @Test
    void missingExtensionIsAppendedSoAZipIsNeverWrittenAsAFolder() {
        assertThat(ExportFormat.XML_ZIP.normalizeTarget(dir.resolve("invoices"))).isEqualTo(dir.resolve("invoices.zip"));
        assertThat(ExportFormat.CSV_EXCEL_PL.normalizeTarget(dir.resolve("a"))).isEqualTo(dir.resolve("a.csv"));
        assertThat(ExportFormat.JSON.normalizeTarget(dir.resolve("a.csv"))).isEqualTo(dir.resolve("a.csv.json"));
    }

    @Test
    void existingExtensionIsKeptInAnyCase() {
        assertThat(ExportFormat.CSV_STANDARD.normalizeTarget(dir.resolve("A.CSV"))).isEqualTo(dir.resolve("A.CSV"));
        assertThat(ExportFormat.XML_ZIP.normalizeTarget(dir.resolve("p.Zip"))).isEqualTo(dir.resolve("p.Zip"));
    }

    @Test
    void folderTargetsAreUnchanged() {
        assertThat(ExportFormat.XML_FOLDER.normalizeTarget(dir.resolve("out"))).isEqualTo(dir.resolve("out"));
        assertThat(ExportFormat.XML_FOLDER.writesFolder()).isTrue();
        assertThat(ExportFormat.XML_FOLDER.extension()).isNull();
    }

    @Test
    void suggestedNamesCarryTheDateAndExtension() {
        LocalDate day = LocalDate.of(2026, 10, 26);
        assertThat(ExportFormat.CSV_EXCEL_PL.suggestedFileName(day)).isEqualTo("fakturomost-2026-10-26.csv");
        assertThat(ExportFormat.JSON.suggestedFileName(day)).isEqualTo("fakturomost-2026-10-26.json");
        assertThat(ExportFormat.XML_ZIP.suggestedFileName(day)).isEqualTo("fakturomost-2026-10-26.zip");
    }

    // --- exports ----------------------------------------------------------------------------------

    @Test
    void csvPresetsWriteExactlyWhatCoreWritesForTheSameSelection() throws IOException {
        Path excel = dir.resolve("excel.csv");
        Path standard = dir.resolve("standard.csv");

        assertThat(ExportFormat.CSV_EXCEL_PL.export(services.exports(), selection, excel, false)).containsExactly(excel);
        ExportFormat.CSV_STANDARD.export(services.exports(), selection, standard, false);

        assertThat(Files.readAllBytes(excel)).isEqualTo(csv(CsvOptions.excelPl()));
        assertThat(Files.readAllBytes(standard)).isEqualTo(csv(CsvOptions.standard()));
    }

    @Test
    void jsonListsTheSelectedInvoicesInOrder() throws IOException {
        Path json = dir.resolve("x.json");

        ExportFormat.JSON.export(services.exports(), selection, json, false);

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        services.exports().writeJson(selection, expected);
        assertThat(Files.readAllBytes(json)).isEqualTo(expected.toByteArray());
    }

    @Test
    void originalXmlGoesIntoOneZipOrOneFilePerInvoice() throws IOException {
        Path zip = dir.resolve("x.zip");
        Path folder = dir.resolve("folder");

        assertThat(ExportFormat.XML_ZIP.export(services.exports(), selection, zip, false)).containsExactly(zip);
        List<Path> files = ExportFormat.XML_FOLDER.export(services.exports(), selection, folder, false);

        try (ZipFile archive = new ZipFile(zip.toFile())) {
            assertThat(archive.size()).isEqualTo(2);
        }
        assertThat(files).hasSize(2).allSatisfy(file -> assertThat(file.getParent()).isEqualTo(folder));
        // The correction has a KSeF number, so its file is named by it; the EUR invoice by its ID.
        assertThat(files).extracting(file -> file.getFileName().toString()).containsExactly(
                selection.get(0).id().sha256Hex() + ".xml", "9999999999-20261020-3A41F0C20004-99.xml");
        assertThat(Files.readAllBytes(files.get(1))).isEqualTo(
                services.catalog().find(selection.get(1).id()).orElseThrow().original().bytes());
    }

    @Test
    void anExistingFileIsReplacedOnlyWithOverwrite() throws IOException {
        Path csv = Files.writeString(dir.resolve("old.csv"), "keep me");

        assertThatThrownBy(() -> ExportFormat.CSV_EXCEL_PL.export(services.exports(), selection, csv, false))
                .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(csv)).isEqualTo("keep me");

        ExportFormat.CSV_EXCEL_PL.export(services.exports(), selection, csv, true);
        assertThat(Files.readAllBytes(csv)).isEqualTo(csv(CsvOptions.excelPl()));
    }

    @Test
    void aFolderExportWritesNothingIfAnyNameIsTakenWithoutOverwrite() throws IOException {
        Path folder = Files.createDirectories(dir.resolve("out"));
        Path taken = Files.writeString(folder.resolve("9999999999-20261020-3A41F0C20004-99.xml"), "keep me");

        assertThatThrownBy(() -> ExportFormat.XML_FOLDER.export(services.exports(), selection, folder, false))
                .isInstanceOf(FileAlreadyExistsException.class);
        try (var listing = Files.list(folder)) {
            assertThat(listing).containsExactly(taken);
        }
        assertThat(Files.readString(taken)).isEqualTo("keep me");
    }

    // --- messages ---------------------------------------------------------------------------------

    @Test
    void messages() {
        Path file = dir.resolve("a.csv");
        assertThat(ExportFormat.CSV_EXCEL_PL.overwriteQuestion(file)).isEqualTo(file + " already exists. Replace it?");
        assertThat(ExportFormat.XML_FOLDER.overwriteQuestion(dir)).contains("Other files in the folder are not touched");
        assertThat(ExportFormat.JSON.doneMessage(1, file)).isEqualTo("Exported 1 invoice to " + file + " (JSON).");
        assertThat(ExportFormat.NOT_ENCRYPTED_NOTICE).contains("NOT encrypted");
    }

    private byte[] csv(CsvOptions options) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        services.exports().writeCsv(selection, options, bytes);
        return bytes.toByteArray();
    }
}
