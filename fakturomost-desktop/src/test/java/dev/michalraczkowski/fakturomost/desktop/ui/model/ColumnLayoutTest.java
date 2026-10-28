package dev.michalraczkowski.fakturomost.desktop.ui.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.michalraczkowski.fakturomost.core.application.CsvOptions;
import dev.michalraczkowski.fakturomost.core.application.ExportColumn;
import dev.michalraczkowski.fakturomost.core.application.InvoiceEntry;
import dev.michalraczkowski.fakturomost.core.application.InvoiceQuery;
import dev.michalraczkowski.fakturomost.desktop.DemoData;
import dev.michalraczkowski.fakturomost.desktop.ui.model.ColumnLayout.Column;
import dev.michalraczkowski.fakturomost.desktop.ui.model.ColumnLayout.Names;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Choosing, ordering and renaming columns, and what reaches the exported file. */
class ColumnLayoutTest {

    @TempDir
    Path dir;

    private final ColumnLayout defaults = ColumnLayout.defaults(Names.POLISH);

    // --- choosing and ordering -------------------------------------------------------------------

    @Test
    void defaultsAreThePresetColumnsAndEverythingElseIsAvailable() {
        assertThat(defaults.fields()).isEqualTo(ExportColumn.defaults());
        assertThat(defaults.columns()).allSatisfy(c -> assertThat(c.name()).isEqualTo(c.field().polishHeader()));
        assertThat(defaults.available()).contains(ExportColumn.NET_BASIC_RATE, ExportColumn.CORRECTION_REASON)
                .doesNotContainAnyElementsOf(ExportColumn.defaults());
        assertThat(defaults.fields().size() + defaults.available().size()).isEqualTo(ExportColumn.values().length);
    }

    @Test
    void addedFieldsGoAfterTheSelectedColumnOrAtTheEnd() {
        ColumnLayout layout = defaults.removeAll().add(List.of(ExportColumn.ISSUE_DATE, ExportColumn.GROSS_TOTAL), -1);

        layout = layout.add(List.of(ExportColumn.NET_BASIC_RATE, ExportColumn.VAT_BASIC_RATE), 0);
        layout = layout.add(List.of(ExportColumn.CURRENCY, ExportColumn.ISSUE_DATE), 99);

        assertThat(layout.fields()).containsExactly(ExportColumn.ISSUE_DATE, ExportColumn.NET_BASIC_RATE,
                ExportColumn.VAT_BASIC_RATE, ExportColumn.GROSS_TOTAL, ExportColumn.CURRENCY);
        assertThat(layout.available()).doesNotContain(ExportColumn.CURRENCY).contains(ExportColumn.TYPE);
        assertThat(layout.columns().get(1).name()).isEqualTo("Netto 23%");
    }

    @Test
    void removedFieldsBecomeAvailableAgainInNaturalOrder() {
        ColumnLayout layout = defaults.remove(List.of(ExportColumn.DOCUMENT_ID, ExportColumn.KSEF_NUMBER));

        assertThat(layout.fields()).doesNotContain(ExportColumn.DOCUMENT_ID, ExportColumn.KSEF_NUMBER);
        assertThat(layout.available().subList(0, 2)).containsExactly(ExportColumn.KSEF_NUMBER, ExportColumn.DOCUMENT_ID);
        assertThat(layout.addAll().fields()).hasSize(ExportColumn.values().length)
                .startsWith(layout.fields().toArray(ExportColumn[]::new));
    }

    @Test
    void movingStopsAtTheEnds() {
        ColumnLayout layout = defaults.removeAll()
                .add(List.of(ExportColumn.TYPE, ExportColumn.CURRENCY, ExportColumn.GROSS_TOTAL), -1);

        assertThat(layout.move(2, -1).fields())
                .containsExactly(ExportColumn.TYPE, ExportColumn.GROSS_TOTAL, ExportColumn.CURRENCY);
        assertThat(layout.move(0, -1)).isEqualTo(layout);
        assertThat(layout.move(2, 1)).isEqualTo(layout);
        assertThat(layout.move(0, 10).fields())
                .containsExactly(ExportColumn.CURRENCY, ExportColumn.GROSS_TOTAL, ExportColumn.TYPE);
        assertThat(layout.move(7, 1)).isEqualTo(layout);
    }

    @Test
    void aFieldCannotBeExportedTwice() {
        assertThatThrownBy(() -> new ColumnLayout(Names.POLISH, List.of(
                new Column(ExportColumn.TYPE, "a"), new Column(ExportColumn.TYPE, "b"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- names -----------------------------------------------------------------------------------

    @Test
    void switchingDefaultNamesKeepsWhatTheUserRenamed() {
        int date = defaults.indexOf(ExportColumn.ISSUE_DATE);
        ColumnLayout renamed = defaults.rename(date, "Data21");

        ColumnLayout english = renamed.withNames(Names.ENGLISH);

        assertThat(english.columns().get(date).name()).isEqualTo("Data21");
        assertThat(english.columns().get(defaults.indexOf(ExportColumn.SALE_DATE)).name()).isEqualTo("sale_date");
        assertThat(english.add(List.of(ExportColumn.SELLER_COUNTRY), -1).columns().getLast().name())
                .isEqualTo("seller_country");
        assertThat(english.resetNames().columns().get(date).name()).isEqualTo("issue_date");
    }

    @Test
    void problemsNameTheColumnAndBlockTheExport() {
        ColumnLayout layout = defaults.rename(0, "  ")
                .rename(1, "Data")
                .rename(2, "data ")
                .rename(3, "two\nlines")
                .rename(4, "x".repeat(ColumnLayout.MAX_NAME_LENGTH + 1));

        assertThat(layout.problems()).containsExactly(
                "Column 1 (KSeF number) has no name.",
                "Columns 2 and 3 have the same name \"data\".",
                "Column 4 (Issue date): names cannot contain line breaks or tabs.",
                "Column 5 (Sale date): name is longer than 100 characters.");
        assertThat(layout.isValid()).isFalse();
        assertThatThrownBy(layout::toJson).isInstanceOf(IllegalStateException.class);
        assertThat(defaults.removeAll().problems()).containsExactly("Choose at least one column to export.");
        assertThat(defaults.problems()).isEmpty();
    }

    // --- what reaches the file -------------------------------------------------------------------

    @Test
    void renamedColumnKeepsTheFieldsValuesInCsvAndJson() throws IOException {
        DemoData.Services services = DemoData.Services.create();
        services.importDemoFolder();
        InvoiceEntry first = services.catalog().search(InvoiceQuery.all()).stream()
                .filter(e -> e.invoice().number().equals("FV/2026/10/0001")).findFirst().orElseThrow();
        ColumnLayout layout = defaults.removeAll()
                .add(List.of(ExportColumn.ISSUE_DATE, ExportColumn.INVOICE_NUMBER, ExportColumn.GROSS_TOTAL), -1)
                .rename(0, " Data21 ");

        Path csv = dir.resolve("a.csv");
        Path json = dir.resolve("a.json");
        new ExportSetup(ExportFormat.CSV_STANDARD, layout, CsvSettings.of(CsvOptions.standard()))
                .export(services.exports(), List.of(first), csv, false);
        new ExportSetup(ExportFormat.JSON_FIELDS, layout.withNames(Names.ENGLISH), null)
                .export(services.exports(), List.of(first), json, false);

        assertThat(Files.readAllLines(csv, StandardCharsets.UTF_8))
                .containsExactly("Data21,Numer faktury,Brutto", "2026-10-02,FV/2026/10/0001,5460.00");
        assertThat(Files.readString(json))
                .containsPattern("\"Data21\"\\s*:\\s*\"2026-10-02\"")
                .containsPattern("\"invoice_number\"\\s*:\\s*\"FV/2026/10/0001\"")
                .containsPattern("\"gross_total\"\\s*:\\s*5460\\.00")
                .doesNotContain("seller_name");
    }

    @Test
    void everyFieldHasALabelAndAHint() {
        Arrays.stream(ExportColumn.values()).forEach(field -> {
            assertThat(ExportFields.label(field)).isNotBlank();
            assertThat(ExportFields.hint(field)).endsWith(".");
        });
        assertThat(Arrays.stream(ExportColumn.values()).map(ExportFields::label).distinct())
                .hasSize(ExportColumn.values().length);
    }
}
