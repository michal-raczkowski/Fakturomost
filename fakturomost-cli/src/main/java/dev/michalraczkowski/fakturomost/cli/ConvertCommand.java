package dev.michalraczkowski.fakturomost.cli;

import dev.michalraczkowski.fakturomost.core.application.CsvOptions;
import dev.michalraczkowski.fakturomost.core.application.ImportBatch;
import dev.michalraczkowski.fakturomost.core.application.ImportResult;
import dev.michalraczkowski.fakturomost.core.application.InvoiceEntry;
import dev.michalraczkowski.fakturomost.core.application.InvoiceQuery;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.TypeConversionException;

/**
 * {@code convert <path...> --format csv|json|xml --out <path>}: imports in memory, selects the FA(3)
 * invoices matching the filters (newest first, as in the catalog) and exports them with core's
 * {@code InvoiceExports}. The same selection is used for every format; unsupported documents are
 * reported, not exported.
 *
 * <p>Output rules (addendum §4): invoice data goes only to {@code --out}; stdout stays empty. stderr
 * gets the problem lines and summary ({@link ImportReport}), what was written, and the warning that
 * the output is not encrypted. An existing output is never replaced without {@code --force}, and never
 * one of the input files, even with it.
 */
@Command(
        name = "convert",
        mixinStandardHelpOptions = true,
        versionProvider = CliVersion.class,
        sortOptions = false,
        description = {
                "Export FA(3) invoices to CSV, JSON or their original XML.",
                "Imports the given files in memory, applies the filters and writes one output. "
                        + "Problems are reported on stderr; the output file is not encrypted."
        },
        exitCodeListHeading = "%nExit codes:%n",
        exitCodeList = {
                "0:Output written; every document was a valid FA(3) invoice.",
                "1:Usage error, or the output already exists (use --force) or is one of the inputs.",
                "2:Output written, but some documents were invalid or unsupported.",
                "3:Some inputs could not be read (output may be incomplete), the output could not be written, "
                        + "or an unexpected error occurred."
        })
final class ConvertCommand implements Callable<Integer> {

    enum Format {
        CSV, JSON, XML;

        String option() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    enum CsvPreset {
        EXCEL_PL("excel-pl", "semicolon, decimal comma, UTF-8 BOM, Polish headers"),
        STANDARD("standard", "comma, decimal point, no BOM");

        private final String option;
        private final String description;

        CsvPreset(String option, String description) {
            this.option = option;
            this.description = description;
        }

        String option() {
            return option;
        }

        CsvOptions options() {
            return this == EXCEL_PL ? CsvOptions.excelPl() : CsvOptions.standard();
        }
    }

    static final class FormatConverter implements ITypeConverter<Format> {
        @Override
        public Format convert(String value) {
            for (Format format : Format.values()) {
                if (format.option().equals(value)) {
                    return format;
                }
            }
            throw new TypeConversionException("expected csv, json or xml but was '" + value + "'");
        }
    }

    static final class CsvPresetConverter implements ITypeConverter<CsvPreset> {
        @Override
        public CsvPreset convert(String value) {
            for (CsvPreset preset : CsvPreset.values()) {
                if (preset.option().equals(value)) {
                    return preset;
                }
            }
            throw new TypeConversionException("expected excel-pl or standard but was '" + value + "'");
        }
    }

    /** ISO dates only ({@code 2026-10-31}), with a message that does not quote JDK internals. */
    static final class DateConverter implements ITypeConverter<LocalDate> {
        @Override
        public LocalDate convert(String value) {
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException e) {
                throw new TypeConversionException("expected a date like 2026-10-31 but was '" + value + "'");
            }
        }
    }

    @Parameters(
            arity = "1..*",
            paramLabel = "<path>",
            description = "XML or ZIP files, or folders (searched recursively for .xml, .zip and .pdf files; "
                    + "symbolic links are not followed).")
    private List<Path> paths;

    @Option(names = "--format", required = true, paramLabel = "csv|json|xml", converter = FormatConverter.class,
            description = "csv or json: one row/object per invoice. xml: the original files, unchanged.")
    private Format format;

    @Option(names = "--out", required = true, paramLabel = "<path>",
            description = "Output file. For xml: a folder (one file per invoice, named by KSeF number), "
                    + "or a name ending in .zip for one archive.")
    private Path out;

    @Option(names = "--from", paramLabel = "<yyyy-mm-dd>", converter = DateConverter.class,
            description = "Only invoices issued on or after this date.")
    private LocalDate from;

    @Option(names = "--to", paramLabel = "<yyyy-mm-dd>", converter = DateConverter.class,
            description = "Only invoices issued on or before this date.")
    private LocalDate to;

    @Option(names = "--nip", paramLabel = "<tax id>",
            description = "Only invoices where the seller or the buyer has this tax ID (NIP or EU VAT number; "
                    + "spaces, dashes and a PL prefix are ignored).")
    private String taxId;

    @Option(names = "--csv-preset", paramLabel = "excel-pl|standard", converter = CsvPresetConverter.class,
            description = "CSV dialect. excel-pl (default): semicolon, decimal comma, UTF-8 BOM, Polish headers. "
                    + "standard: comma, decimal point, no BOM, Polish headers.")
    private CsvPreset csvPreset;

    @Option(names = "--force", description = "Replace an existing output.")
    private boolean force;

    @Spec
    private CommandSpec spec;

    private final Supplier<CoreServices> services;

    ConvertCommand(Supplier<CoreServices> services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    @Override
    public Integer call() {
        PrintWriter err = spec.commandLine().getErr();
        validateOptions();
        checkOutputBeforeImport();

        CoreServices core = services.get();
        ImportBatch batch = core.importer().importInputs(paths);
        checkOutputIsNotAnInput(batch.filesRead());

        List<ImportResult> results = batch.results();
        ImportReport.printLines(results, ImportReport::isProblem, err);
        ImportReport.Summary summary = ImportReport.Summary.of(results);
        err.println(summary.text());
        ImportReport.printNotes(batch, err);

        int available = core.catalog().search(InvoiceQuery.all()).size();
        List<InvoiceEntry> selected = core.catalog().search(query());
        try {
            switch (format) {
                case CSV -> core.exports().exportCsv(selected, preset().options(), out, force);
                case JSON -> core.exports().exportJson(selected, out, force);
                case XML -> core.exports().exportOriginals(
                        selected.stream().map(InvoiceEntry::id).toList(), out, force);
            }
        } catch (FileAlreadyExistsException e) {
            if (force) {
                err.println("Error: could not write " + display(out) + " (" + e.getClass().getSimpleName() + ").");
                return ExitCodes.FATAL;
            }
            // xml into a folder (core checks every target name before writing anything), or a file
            // created since the check before importing.
            err.println("Error: " + display(e.getFile() == null ? out : Path.of(e.getFile()))
                    + " already exists; nothing was written. Add --force to replace it.");
            return ExitCodes.USAGE;
        } catch (IOException | UncheckedIOException e) {
            err.println("Error: could not write " + display(out) + " (" + e.getClass().getSimpleName() + ").");
            return ExitCodes.FATAL;
        }

        err.println(written(selected.size(), available));
        if (selected.isEmpty()) {
            err.println("Note: no invoices matched; the output contains none.");
        }
        if (summary.unsupported() > 0) {
            err.println("Note: " + ImportReport.plural(summary.unsupported(), "unsupported document")
                    + " not exported (only FA(3) invoices are).");
        }
        err.println("Warning: " + display(out) + " is not encrypted and contains invoice data. "
                + "Store and share it accordingly.");
        return summary.exitCode();
    }

    private void validateOptions() {
        if (from != null && to != null && from.isAfter(to)) {
            throw usage("--from " + from + " is after --to " + to);
        }
        if (taxId != null && taxId.isBlank()) {
            throw usage("--nip must not be empty");
        }
        if (csvPreset != null && format != Format.CSV) {
            throw usage("--csv-preset applies only to --format csv");
        }
    }

    /** Cheap checks first, so nothing is imported for a command that cannot succeed. */
    private void checkOutputBeforeImport() {
        for (Path parent = out.toAbsolutePath().getParent(); parent != null; parent = parent.getParent()) {
            if (Files.exists(parent) && !Files.isDirectory(parent)) {
                throw usage("--out " + display(out) + " cannot be created: " + display(parent) + " is a file");
            }
        }
        if (writesFolder()) {
            if (Files.exists(out) && !Files.isDirectory(out)) {
                throw usage("--out " + display(out) + " is a file; for --format xml give a folder, "
                        + "or a name ending in .zip");
            }
            return;
        }
        if (Files.isDirectory(out)) {
            throw usage("--out " + display(out) + " is a folder; give a file name");
        }
        if (Files.exists(out) && !force) {
            throw usage(display(out) + " already exists; add --force to replace it");
        }
    }

    /**
     * Input files are never modified (addendum §4), not even with {@code --force}: the output must not
     * be one of them, and xml must not be written into a folder that holds them (a same-named original
     * could be replaced).
     */
    private void checkOutputIsNotAnInput(Set<Path> filesRead) {
        if (!Files.exists(out)) {
            return;
        }
        Path realOut;
        try {
            realOut = out.toRealPath();
        } catch (IOException e) {
            return; // core will report the write failure
        }
        if (filesRead.contains(realOut)) {
            throw usage("--out " + display(out) + " is one of the input files; choose another output");
        }
        if (writesFolder() && force && filesRead.stream().anyMatch(file -> realOut.equals(file.getParent()))) {
            throw usage("--out " + display(out) + " contains input files, which --force could replace; "
                    + "choose another folder");
        }
    }

    private InvoiceQuery query() {
        return InvoiceQuery.all().withIssuedBetween(from, to).withTaxId(taxId);
    }

    private CsvPreset preset() {
        return csvPreset == null ? CsvPreset.EXCEL_PL : csvPreset;
    }

    private boolean writesFolder() {
        return format == Format.XML && !isZipName(out);
    }

    private static boolean isZipName(Path path) {
        return path.getFileName() != null && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private String written(int count, int available) {
        String invoices = count == available
                ? ImportReport.plural(count, "invoice")
                : count + " of " + ImportReport.plural(available, "invoice");
        String what = switch (format) {
            case CSV -> "CSV, " + preset().option() + ": " + preset().description;
            case JSON -> "JSON";
            case XML -> writesFolder() ? "original XML, one file per invoice" : "original XML in one ZIP archive";
        };
        return "Wrote " + invoices + " to " + display(out) + " (" + what + ").";
    }

    private ParameterException usage(String message) {
        return new ParameterException(spec.commandLine(), message);
    }

    private static String display(Path path) {
        return ImportReport.displayName(path);
    }
}
