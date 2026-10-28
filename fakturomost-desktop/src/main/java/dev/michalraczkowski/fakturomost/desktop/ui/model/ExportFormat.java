package dev.michalraczkowski.fakturomost.desktop.ui.model;

import dev.michalraczkowski.fakturomost.core.application.CsvOptions;
import dev.michalraczkowski.fakturomost.core.application.InvoiceEntry;
import dev.michalraczkowski.fakturomost.core.application.InvoiceExports;
import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The choices of the export dialog and how each maps onto {@link InvoiceExports}. Kept free of
 * JavaFX so the mapping — above all the file-name rules — is unit-tested.
 */
public enum ExportFormat {

    CSV_EXCEL_PL("CSV for Excel (Polish settings)", "CSV file", ".csv"),
    CSV_STANDARD("CSV, standard (comma, decimal point)", "CSV file", ".csv"),
    JSON("JSON", "JSON file", ".json"),
    XML_ZIP("Original XML, in one ZIP archive", "ZIP archive", ".zip"),
    XML_FOLDER("Original XML, one file per document in a folder", null, null);

    private final String label;
    private final String fileDescription;
    private final String extension;

    ExportFormat(String label, String fileDescription, String extension) {
        this.label = label;
        this.fileDescription = fileDescription;
        this.extension = extension;
    }

    public String label() {
        return label;
    }

    /** {@code true}: ask for a folder; {@code false}: ask for a file name. */
    public boolean writesFolder() {
        return extension == null;
    }

    /** For the file chooser's filter, e.g. {@code CSV file}; {@code null} for a folder. */
    public String fileDescription() {
        return fileDescription;
    }

    /** {@code .csv}, ...; {@code null} for a folder. */
    public String extension() {
        return extension;
    }

    /** Suggested name in the save dialog, e.g. {@code fakturomost-2026-10-26.csv}. */
    public String suggestedFileName(LocalDate today) {
        return "fakturomost-" + today + (writesFolder() ? "" : extension);
    }

    /**
     * The file actually written: the chosen one, with the extension appended if it is missing. This
     * matters most for {@link #XML_ZIP} — core treats a target not ending in {@code .zip} as a folder —
     * and on Linux, where the save dialog does not add an extension.
     */
    public Path normalizeTarget(Path chosen) {
        if (writesFolder() || chosen.getFileName() == null) {
            return chosen;
        }
        String name = chosen.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(extension) ? chosen : chosen.resolveSibling(name + extension);
    }

    /**
     * Runs the export. Without {@code overwrite} nothing existing is replaced (core throws
     * {@link java.nio.file.FileAlreadyExistsException}); the dialog asks the user and only then
     * repeats with {@code overwrite = true}.
     *
     * @param invoices in table order
     * @param target   already {@linkplain #normalizeTarget normalized}
     * @return the files written
     */
    public List<Path> export(InvoiceExports exports, List<InvoiceEntry> invoices, Path target, boolean overwrite)
            throws IOException {
        switch (this) {
            case CSV_EXCEL_PL -> exports.exportCsv(invoices, CsvOptions.excelPl(), target, overwrite);
            case CSV_STANDARD -> exports.exportCsv(invoices, CsvOptions.standard(), target, overwrite);
            case JSON -> exports.exportJson(invoices, target, overwrite);
            case XML_ZIP, XML_FOLDER -> {
                List<DocumentId> ids = invoices.stream().map(InvoiceEntry::id).toList();
                return exports.exportOriginals(ids, target, overwrite);
            }
        }
        return List.of(target);
    }

    /** Asked when an export without overwrite hit an existing file; the answer decides {@code overwrite}. */
    public String overwriteQuestion(Path target) {
        String where = Formats.singleLine(target.toString());
        return writesFolder()
                ? "Some files in " + where + " have the same names as the exported ones. Replace them? "
                        + "Other files in the folder are not touched."
                : where + " already exists. Replace it?";
    }

    /** Shown after a successful export. */
    public String doneMessage(int invoiceCount, Path target) {
        return doneMessage(invoiceCount, "invoice", target);
    }

    /** @param noun singular, e.g. {@code document} for the originals of other-format documents */
    public String doneMessage(int count, String noun, Path target) {
        String what = ImportSummary.plural(count, noun);
        return "Exported " + what + " to " + Formats.singleLine(target.toString()) + " (" + label + ").";
    }

    /** The formats that write original files — the only ones for documents Fakturomost cannot read. */
    public static List<ExportFormat> originalFormats() {
        return List.of(XML_ZIP, XML_FOLDER);
    }

    public boolean writesOriginals() {
        return this == XML_ZIP || this == XML_FOLDER;
    }

    /** Shown in the export dialog and again after exporting. */
    public static final String NOT_ENCRYPTED_NOTICE =
            "Exported files are NOT encrypted and contain invoice data (names, tax IDs, amounts). "
                    + "Store and share them carefully, and delete copies you no longer need.";
}
