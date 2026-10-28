package dev.michalraczkowski.fakturomost.desktop.ui.model;

import dev.michalraczkowski.fakturomost.core.application.InvoiceEntry;
import dev.michalraczkowski.fakturomost.core.application.InvoiceExports;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Everything one export needs besides the invoices and the target: the format and, for formats with
 * columns, the column layout and (CSV only) the file settings.
 *
 * @param layout {@code null} unless {@link ExportFormat#hasColumns()}
 * @param csv    {@code null} unless {@link ExportFormat#isCsv()}
 */
public record ExportSetup(ExportFormat format, ColumnLayout layout, CsvSettings csv) {

    public ExportSetup {
        Objects.requireNonNull(format, "format");
        if (format.hasColumns() != (layout != null)) {
            throw new IllegalArgumentException(format + (layout == null ? " needs" : " takes no") + " column layout");
        }
        if (format.isCsv() != (csv != null)) {
            throw new IllegalArgumentException(format + (csv == null ? " needs" : " takes no") + " CSV settings");
        }
    }

    /** The format's defaults: the preset's settings and the default columns with default names. */
    public static ExportSetup defaults(ExportFormat format) {
        return new ExportSetup(format,
                format.hasColumns() ? ColumnLayout.defaults(format.defaultNames()) : null,
                format.isCsv() ? CsvSettings.of(format.csvPreset()) : null);
    }

    public ExportSetup withLayout(ColumnLayout value) {
        return new ExportSetup(format, value, csv);
    }

    public ExportSetup withCsv(CsvSettings value) {
        return new ExportSetup(format, layout, value);
    }

    /** What stops the export, in display order; empty when it can run. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (csv != null) {
            csv.problem().ifPresent(problems::add);
        }
        if (layout != null) {
            problems.addAll(layout.problems());
        }
        return problems;
    }

    /**
     * Runs the export. Without {@code overwrite} nothing existing is replaced (core throws
     * {@link java.nio.file.FileAlreadyExistsException}).
     *
     * @throws IllegalStateException if {@link #problems()} is not empty
     */
    public List<Path> export(InvoiceExports exports, List<InvoiceEntry> invoices, Path target, boolean overwrite)
            throws IOException {
        requireValid();
        switch (format) {
            case CSV_EXCEL_PL, CSV_STANDARD -> exports.exportCsv(invoices, layout.toCsv(csv.toOptions()), target, overwrite);
            case JSON_FIELDS -> exports.exportJson(invoices, layout.toJson(), target, overwrite);
            case JSON -> exports.exportJson(invoices, target, overwrite);
            case XML_ZIP, XML_FOLDER -> {
                return exports.exportOriginals(invoices.stream().map(InvoiceEntry::id).toList(), target, overwrite);
            }
        }
        return List.of(target);
    }

    /**
     * The beginning of the file this setup would write for {@code sample} — exactly the exported
     * text, formatted in memory by core (nothing is read from the store or written to disk). A BOM
     * is not shown. Empty for formats without columns.
     */
    public String preview(InvoiceExports exports, List<InvoiceEntry> sample) {
        if (layout == null || !problems().isEmpty()) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (csv != null) {
                exports.writeCsv(sample, layout.toCsv(csv.toOptions()), out);
            } else {
                exports.writeJson(sample, layout.toJson(), out);
            }
        } catch (IOException e) {
            throw new IllegalStateException("In-memory export failed", e); // a ByteArrayOutputStream does not fail
        }
        String text = out.toString(StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private void requireValid() {
        List<String> problems = problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException(problems.getFirst());
        }
    }
}
