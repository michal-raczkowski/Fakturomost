package dev.michalraczkowski.fakturomost.desktop.ui.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.michalraczkowski.fakturomost.core.application.CsvOptions;
import dev.michalraczkowski.fakturomost.core.application.ExportColumn;
import dev.michalraczkowski.fakturomost.core.application.InvoiceEntry;
import dev.michalraczkowski.fakturomost.core.application.InvoiceQuery;
import dev.michalraczkowski.fakturomost.desktop.DemoData;
import dev.michalraczkowski.fakturomost.desktop.ui.model.ColumnLayout.Names;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** CSV settings, the preview, and what the session remembers between exports. */
class ExportSetupTest {

    @TempDir
    Path dir;

    private final DemoData.Services services = DemoData.Services.create();
    private final List<InvoiceEntry> invoices;

    ExportSetupTest() {
        services.importDemoFolder();
        invoices = services.catalog().search(InvoiceQuery.all());
    }

    // --- CSV settings ----------------------------------------------------------------------------

    @Test
    void settingsStartFromThePresets() {
        assertThat(CsvSettings.of(CsvOptions.excelPl())).isEqualTo(new CsvSettings(';', ',', true, true, true));
        assertThat(CsvSettings.of(CsvOptions.standard())).isEqualTo(new CsvSettings(',', '.', false, true, true));
        assertThat(ExportSetup.defaults(ExportFormat.CSV_STANDARD).csv()).isEqualTo(CsvSettings.of(CsvOptions.standard()));
    }

    @Test
    void everyOfferedSeparatorWorksWithAtLeastOneDecimalSign() {
        for (CsvSettings.Choice separator : CsvSettings.SEPARATORS) {
            assertThat(CsvSettings.DECIMAL_SEPARATORS)
                    .anySatisfy(decimal -> assertThat(new CsvSettings(separator.value(), decimal.value(), true, true, true)
                            .problem()).isEmpty());
        }
    }

    @Test
    void commaSeparatorWithDecimalCommaIsReportedNotThrown() {
        CsvSettings clash = CsvSettings.of(CsvOptions.excelPl()).withSeparator(',');

        assertThat(clash.problem()).hasValueSatisfying(text -> assertThat(text).contains("cannot be the same"));
        ExportSetup setup = ExportSetup.defaults(ExportFormat.CSV_EXCEL_PL).withCsv(clash);
        assertThat(setup.problems()).hasSize(1);
        assertThat(setup.preview(services.exports(), invoices)).isEmpty();
        assertThatThrownBy(() -> setup.export(services.exports(), invoices, dir.resolve("x.csv"), false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(dir.resolve("x.csv")).doesNotExist();
    }

    @Test
    void changedSettingsReachTheFile() throws IOException {
        CsvSettings settings = new CsvSettings('\t', ',', false, false, false);
        ColumnLayout layout = ColumnLayout.defaults(Names.POLISH).removeAll()
                .add(List.of(ExportColumn.INVOICE_NUMBER, ExportColumn.GROSS_TOTAL), -1);
        Path file = dir.resolve("tab.csv");

        new ExportSetup(ExportFormat.CSV_EXCEL_PL, layout, settings).export(services.exports(), invoices.subList(0, 1),
                file, false);

        byte[] bytes = Files.readAllBytes(file);
        assertThat(bytes[0]).as("no BOM").isEqualTo((byte) 'F');
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("FV/2026/10/0004\t2500,00\n");
    }

    @Test
    void defaultSetupWritesExactlyThePreset() throws IOException {
        Path file = dir.resolve("p.csv");

        ExportSetup.defaults(ExportFormat.CSV_EXCEL_PL).export(services.exports(), invoices, file, false);

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        services.exports().writeCsv(invoices, CsvOptions.excelPl(), expected);
        assertThat(Files.readAllBytes(file)).isEqualTo(expected.toByteArray());
    }

    // --- preview ---------------------------------------------------------------------------------

    @Test
    void previewIsTheStartOfTheExportedTextWithoutTheBom() throws IOException {
        ExportSetup setup = ExportSetup.defaults(ExportFormat.CSV_EXCEL_PL)
                .withLayout(ColumnLayout.defaults(Names.POLISH).rename(3, "Data21"));
        List<InvoiceEntry> sample = invoices.subList(0, 2);
        Path file = dir.resolve("same.csv");
        setup.export(services.exports(), sample, file, false);

        String preview = setup.preview(services.exports(), sample);

        assertThat(preview).startsWith("Numer KSeF;Numer faktury;Rodzaj faktury;Data21;");
        assertThat(("\uFEFF" + preview).getBytes(StandardCharsets.UTF_8))
                .isEqualTo(Files.readAllBytes(file));
        assertThat(ExportSetup.defaults(ExportFormat.JSON_FIELDS).preview(services.exports(), sample))
                .startsWith("{").contains("fakturomost-invoice-fields");
        assertThat(ExportSetup.defaults(ExportFormat.XML_ZIP).preview(services.exports(), sample)).isEmpty();
    }

    @Test
    void setupsWithoutColumnsTakeNone() {
        assertThatThrownBy(() -> new ExportSetup(ExportFormat.XML_ZIP, ColumnLayout.defaults(Names.POLISH), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExportSetup(ExportFormat.JSON_FIELDS, ColumnLayout.defaults(Names.ENGLISH),
                CsvSettings.of(CsvOptions.standard()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExportSetup(ExportFormat.CSV_STANDARD, null, CsvSettings.of(CsvOptions.standard())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- session memory --------------------------------------------------------------------------

    @Test
    void sessionRemembersLayoutsAndSettingsPerFormatFamily() {
        ExportSession session = new ExportSession();
        assertThat(session.setupFor(ExportFormat.CSV_EXCEL_PL)).isEqualTo(ExportSetup.defaults(ExportFormat.CSV_EXCEL_PL));

        ColumnLayout csvLayout = ColumnLayout.defaults(Names.POLISH).removeAll()
                .add(List.of(ExportColumn.ISSUE_DATE), -1).rename(0, "Data21");
        CsvSettings tab = CsvSettings.of(CsvOptions.excelPl()).withSeparator('\t');
        session.remember(new ExportSetup(ExportFormat.CSV_EXCEL_PL, csvLayout, tab));

        assertThat(session.lastFormat()).isEqualTo(ExportFormat.CSV_EXCEL_PL);
        assertThat(session.setupFor(ExportFormat.CSV_EXCEL_PL).layout()).isEqualTo(csvLayout);
        assertThat(session.setupFor(ExportFormat.CSV_EXCEL_PL).csv()).isEqualTo(tab);
        assertThat(session.setupFor(ExportFormat.CSV_STANDARD).layout()).as("CSV formats share columns")
                .isEqualTo(csvLayout);
        assertThat(session.setupFor(ExportFormat.CSV_STANDARD).csv()).as("but keep their own file settings")
                .isEqualTo(CsvSettings.of(CsvOptions.standard()));
        assertThat(session.setupFor(ExportFormat.JSON_FIELDS).layout()).as("JSON has its own layout")
                .isEqualTo(ColumnLayout.defaults(Names.ENGLISH));

        session.rememberFormat(ExportFormat.XML_ZIP);
        assertThat(session.lastFormat()).isEqualTo(ExportFormat.XML_ZIP);
        assertThat(session.setupFor(ExportFormat.CSV_EXCEL_PL).layout()).isEqualTo(csvLayout);
    }
}
