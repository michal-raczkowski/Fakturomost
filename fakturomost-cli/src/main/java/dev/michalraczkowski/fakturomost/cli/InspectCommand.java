package dev.michalraczkowski.fakturomost.cli;

import dev.michalraczkowski.fakturomost.core.application.ImportResult;
import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code inspect <path...>}: imports in memory and prints one status line per document plus a summary.
 *
 * <p>The report is the command's result, so it goes to stdout (scripts can pipe or grep it); notes go
 * to stderr. Lines carry only source names, statuses, KSeF numbers (taken from file names) and core's
 * value-free reasons — no invoice numbers, parties or amounts.
 */
@Command(
        name = "inspect",
        mixinStandardHelpOptions = true,
        versionProvider = CliVersion.class,
        description = {
                "Check FA(3) invoice files without writing anything.",
                "Prints one line per document (Imported, Duplicate, Unsupported, Invalid or Failed) and a summary.",
                "Lines show file names, KSeF numbers and reasons only, never invoice contents."
        },
        exitCodeListHeading = "%nExit codes:%n",
        exitCodeList = {
                "0:Every document is a valid FA(3) invoice (duplicates included).",
                "1:Usage error.",
                "2:Some documents were invalid or unsupported.",
                "3:Some files or folders could not be read, or an unexpected error occurred."
        })
final class InspectCommand implements Callable<Integer> {

    /** Width of the status column: the longest status, {@code Unsupported}. */
    private static final int STATUS_WIDTH = 11;

    @Parameters(
            arity = "1..*",
            paramLabel = "<path>",
            description = "XML or ZIP files, or folders (searched recursively for .xml, .zip and .pdf files; "
                    + "symbolic links are not followed).")
    private List<Path> paths;

    @Spec
    private CommandSpec spec;

    private final Supplier<CoreServices> services;

    InspectCommand(Supplier<CoreServices> services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        InputFiles.Outcome outcome = InputFiles.importAll(services.get().importer(), paths);
        List<ImportResult> results = outcome.results();

        Map<DocumentId, String> firstSource = new HashMap<>();
        for (ImportResult result : results) {
            out.println(line(result, firstSource));
            switch (result) {
                case ImportResult.Imported r -> firstSource.putIfAbsent(r.id(), r.sourceName());
                case ImportResult.Unsupported r -> firstSource.putIfAbsent(r.id(), r.sourceName());
                case ImportResult.Duplicate r -> { }
                case ImportResult.Invalid r -> { }
                case ImportResult.Failed r -> { }
            }
        }
        Summary summary = Summary.of(results);
        out.println(summary.text());

        if (outcome.skippedFiles() > 0) {
            err.println("Note: skipped " + plural(outcome.skippedFiles(), "other file")
                    + " in folders (only .xml, .zip and .pdf files are read from folders).");
        }
        if (outcome.skippedLinks() > 0) {
            err.println("Note: skipped " + plural(outcome.skippedLinks(), "symbolic link")
                    + " in folders (links are not followed).");
        }
        if (results.isEmpty()) {
            err.println("Note: no documents found.");
        }
        return summary.exitCode();
    }

    /** One report line: padded status, source name, detail in brackets. */
    static String line(ImportResult result, Map<DocumentId, String> firstSource) {
        return switch (result) {
            case ImportResult.Imported r -> row("Imported", r.sourceName(),
                    r.ksefNumber() == null ? "no KSeF number" : "KSeF " + r.ksefNumber().value());
            case ImportResult.Duplicate r -> row("Duplicate", r.sourceName(),
                    firstSource.containsKey(r.id()) ? "same content as " + firstSource.get(r.id()) : "already imported");
            case ImportResult.Unsupported r -> row("Unsupported", r.sourceName(), r.reason());
            case ImportResult.Invalid r -> row("Invalid", r.sourceName(), r.reason());
            // Only the reason: the cause's message is not guaranteed free of document data.
            case ImportResult.Failed r -> row("Failed", r.sourceName(), r.reason());
        };
    }

    private static String row(String status, String sourceName, String detail) {
        return String.format("%-" + STATUS_WIDTH + "s  %s  [%s]", status, sourceName, detail);
    }

    record Summary(int imported, int duplicate, int unsupported, int invalid, int failed) {

        static Summary of(List<ImportResult> results) {
            int imported = 0;
            int duplicate = 0;
            int unsupported = 0;
            int invalid = 0;
            int failed = 0;
            for (ImportResult result : results) {
                switch (result) {
                    case ImportResult.Imported r -> imported++;
                    case ImportResult.Duplicate r -> duplicate++;
                    case ImportResult.Unsupported r -> unsupported++;
                    case ImportResult.Invalid r -> invalid++;
                    case ImportResult.Failed r -> failed++;
                }
            }
            return new Summary(imported, duplicate, unsupported, invalid, failed);
        }

        int total() {
            return imported + duplicate + unsupported + invalid + failed;
        }

        String text() {
            return plural(total(), "document") + ": " + imported + " imported, " + duplicate + " duplicate, "
                    + unsupported + " unsupported, " + invalid + " invalid, " + failed + " failed.";
        }

        /** {@code Failed} wins over rejected documents: an unread input may hide anything. */
        int exitCode() {
            if (failed > 0) {
                return ExitCodes.FATAL;
            }
            if (unsupported > 0 || invalid > 0) {
                return ExitCodes.SOME_DOCUMENTS_REJECTED;
            }
            return ExitCodes.OK;
        }
    }

    static String plural(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
