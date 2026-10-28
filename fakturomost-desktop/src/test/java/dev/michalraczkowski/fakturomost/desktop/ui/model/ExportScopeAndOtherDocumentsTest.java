package dev.michalraczkowski.fakturomost.desktop.ui.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.michalraczkowski.fakturomost.core.application.OtherDocument;
import dev.michalraczkowski.fakturomost.desktop.DemoData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportScopeAndOtherDocumentsTest {

    @TempDir
    Path dir;

    // --- export scope -----------------------------------------------------------------------------

    @Test
    void selectedRowsWinWhenThereAreAnyOtherwiseEverythingShown() {
        assertThat(ExportScope.defaultFor(3)).isEqualTo(ExportScope.SELECTED);
        assertThat(ExportScope.defaultFor(0)).isEqualTo(ExportScope.ALL_SHOWN);
    }

    @Test
    void scopeLabelsSayHowManyAndWhetherFiltered() {
        assertThat(ExportScope.SELECTED.label(1, false)).isEqualTo("Selected invoice (1)");
        assertThat(ExportScope.SELECTED.label(3, true)).isEqualTo("Selected invoices (3)");
        assertThat(ExportScope.ALL_SHOWN.label(7, false)).isEqualTo("All 7 shown invoices");
        assertThat(ExportScope.ALL_SHOWN.label(2, true)).isEqualTo("All 2 shown invoices (as filtered)");
        assertThat(ExportScope.ALL_SHOWN.label(1, false)).isEqualTo("The 1 shown invoice");
    }

    @Test
    void onlyOriginalXmlFormatsAreOfferedForOtherDocuments() {
        assertThat(ExportFormat.originalFormats()).containsExactly(ExportFormat.XML_ZIP, ExportFormat.XML_FOLDER);
        assertThat(ExportFormat.originalFormats()).allMatch(ExportFormat::writesOriginals);
        assertThat(ExportFormat.CSV_EXCEL_PL.writesOriginals()).isFalse();
    }

    @Test
    void doneMessageNamesWhatWasExported() {
        Path zip = dir.resolve("other.zip");
        assertThat(ExportFormat.XML_ZIP.doneMessage(1, "document", zip))
                .isEqualTo("Exported 1 document to " + zip + " (Original XML, in one ZIP archive).");
        assertThat(ExportFormat.XML_ZIP.doneMessage(2, zip)).startsWith("Exported 2 invoices to ");
    }

    // --- other documents (the demo data's FA(2) file) ---------------------------------------------

    @Test
    void demoDataHasOneDocumentInAnotherFormatThatCanBeExportedUnchanged() throws IOException {
        DemoData.Services services = DemoData.Services.create();
        services.importDemoFolder();

        List<OtherDocument> others = services.catalog().otherDocuments();
        assertThat(others).hasSize(1);
        OtherDocumentRow row = OtherDocumentRow.of(others.getFirst());
        assertThat(row.source()).endsWith("faktura-FA2.xml");
        assertThat(row.ksefNumber()).isEmpty();
        assertThat(row.size()).matches("\\d+ B|\\d+,\\d kB");

        List<Path> written = services.exports().exportOriginals(List.of(others.getFirst().id()), dir.resolve("out"), false);
        assertThat(Files.readAllBytes(written.getFirst()))
                .isEqualTo(Files.readAllBytes(DemoData.file("problemy/faktura-FA2.xml")));
    }

    @Test
    void linkText() {
        assertThat(OtherDocumentRow.linkText(0)).isEmpty();
        assertThat(OtherDocumentRow.linkText(1)).isEqualTo("1 document in another format");
        assertThat(OtherDocumentRow.linkText(2)).isEqualTo("2 documents in another format");
    }
}
