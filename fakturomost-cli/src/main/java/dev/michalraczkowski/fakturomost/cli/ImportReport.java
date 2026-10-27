package dev.michalraczkowski.fakturomost.cli;

import dev.michalraczkowski.fakturomost.core.application.ImportBatch;
import dev.michalraczkowski.fakturomost.core.application.ImportResult;
import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * How import results are shown, shared by {@code inspect} (every line, on stdout) and {@code convert}
 * (problem lines only, on stderr). Lines carry only source names, statuses, KSeF numbers (taken from
 * file names) and core's value-free reasons — never invoice numbers, parties or amounts.
 */
final class ImportReport {

    /** Width of the status column: the longest status, {@code Unsupported}. */
    private static final int STATUS_WIDTH = 11;

    private ImportReport() {
    }

    /** {@code Unsupported}, {@code Invalid} and {@code Failed}: what {@code convert} reports. */
    static boolean isProblem(ImportResult result) {
        return switch (result) {
            case ImportResult.Imported r -> false;
            case ImportResult.Duplicate r -> false;
            case ImportResult.Unsupported r -> true;
            case ImportResult.Invalid r -> true;
            case ImportResult.Failed r -> true;
        };
    }

    /** Prints the line of every result accepted by {@code filter}, in order. */
    static void printLines(List<ImportResult> results, Predicate<ImportResult> filter, PrintWriter writer) {
        Map<DocumentId, String> firstSource = new HashMap<>();
        for (ImportResult result : results) {
            if (filter.test(result)) {
                writer.println(line(result, firstSource));
            }
            switch (result) {
                case ImportResult.Imported r -> firstSource.putIfAbsent(r.id(), r.sourceName());
                case ImportResult.Unsupported r -> firstSource.putIfAbsent(r.id(), r.sourceName());
                case ImportResult.Duplicate r -> { }
                case ImportResult.Invalid r -> { }
                case ImportResult.Failed r -> { }
            }
        }
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

    /** Notes about what folder expansion left out, and about finding nothing at all. */
    static void printNotes(ImportBatch batch, PrintWriter err) {
        if (batch.skippedFiles() > 0) {
            err.println("Note: skipped " + plural(batch.skippedFiles(), "other file")
                    + " in folders (only .xml, .zip and .pdf files are read from folders).");
        }
        if (batch.skippedLinks() > 0) {
            err.println("Note: skipped " + plural(batch.skippedLinks(), "symbolic link")
                    + " in folders (links are not followed).");
        }
        if (batch.results().isEmpty()) {
            err.println("Note: no documents found.");
        }
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

    /** Same rule as core: control characters in names must not break report lines. */
    static String displayName(Path path) {
        StringBuilder result = new StringBuilder();
        path.toString().codePoints().forEach(c -> result.appendCodePoint(Character.isISOControl(c) ? '?' : c));
        return result.toString();
    }

    static String plural(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
