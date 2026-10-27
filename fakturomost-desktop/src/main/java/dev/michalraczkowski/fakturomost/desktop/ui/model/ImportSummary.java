package dev.michalraczkowski.fakturomost.desktop.ui.model;

import dev.michalraczkowski.fakturomost.core.application.ImportBatch;
import dev.michalraczkowski.fakturomost.core.application.ImportResult;
import dev.michalraczkowski.fakturomost.core.domain.DocumentId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the import-results window shows: one line per document (nothing is dropped), counts, and
 * notes. Same wording as the CLI's report; the desktop module cannot share the CLI's class
 * (addendum §3.4), and the text is small enough to keep twice.
 *
 * <p>Lines carry only source names, statuses, KSeF numbers and core's value-free reasons — never
 * invoice contents — so they are safe to copy into a bug report.
 */
public record ImportSummary(List<Line> lines, int imported, int duplicate, int unsupported, int invalid, int failed,
                            List<String> notes) {

    public enum Status {
        IMPORTED("Imported"), DUPLICATE("Duplicate"), UNSUPPORTED("Unsupported"), INVALID("Invalid"), FAILED("Failed");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Unsupported, invalid and failed documents need the user's attention; duplicates do not. */
        public boolean isProblem() {
            return this == UNSUPPORTED || this == INVALID || this == FAILED;
        }
    }

    /** @param source file name, path or {@code zip!/entry}, with control characters already replaced by core */
    public record Line(Status status, String source, String detail) {
        public Line {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(detail, "detail");
        }

        /** {@code Invalid  C:\...\x.xml  [reason]} — the CLI's format, for "Copy to clipboard". */
        public String asText() {
            return String.format("%-11s  %s  [%s]", status.label(), source, detail);
        }
    }

    public ImportSummary {
        lines = List.copyOf(lines);
        notes = List.copyOf(notes);
    }

    public static ImportSummary of(ImportBatch batch) {
        List<Line> lines = new ArrayList<>();
        Map<DocumentId, String> firstSource = new HashMap<>();
        int imported = 0;
        int duplicate = 0;
        int unsupported = 0;
        int invalid = 0;
        int failed = 0;
        for (ImportResult result : batch.results()) {
            switch (result) {
                case ImportResult.Imported r -> {
                    imported++;
                    firstSource.putIfAbsent(r.id(), r.sourceName());
                    lines.add(new Line(Status.IMPORTED, r.sourceName(),
                            r.ksefNumber() == null ? "no KSeF number" : "KSeF " + r.ksefNumber().value()));
                }
                case ImportResult.Duplicate r -> {
                    duplicate++;
                    lines.add(new Line(Status.DUPLICATE, r.sourceName(), firstSource.containsKey(r.id())
                            ? "same content as " + firstSource.get(r.id())
                            : "already imported"));
                }
                case ImportResult.Unsupported r -> {
                    unsupported++;
                    firstSource.putIfAbsent(r.id(), r.sourceName());
                    lines.add(new Line(Status.UNSUPPORTED, r.sourceName(), r.reason()));
                }
                case ImportResult.Invalid r -> {
                    invalid++;
                    lines.add(new Line(Status.INVALID, r.sourceName(), r.reason()));
                }
                // Only the reason: the cause's message is not guaranteed free of document data.
                case ImportResult.Failed r -> {
                    failed++;
                    lines.add(new Line(Status.FAILED, r.sourceName(), r.reason()));
                }
            }
        }
        return new ImportSummary(lines, imported, duplicate, unsupported, invalid, failed, notes(batch, unsupported));
    }

    private static List<String> notes(ImportBatch batch, int unsupported) {
        List<String> notes = new ArrayList<>();
        if (batch.cancelled()) {
            notes.add("Import cancelled. Documents listed here were processed; the rest was not read.");
        }
        if (batch.results().isEmpty() && !batch.cancelled()) {
            notes.add("No documents found.");
        }
        if (batch.skippedFiles() > 0) {
            notes.add("Skipped " + plural(batch.skippedFiles(), "other file")
                    + " in folders (only .xml, .zip and .pdf files are read from folders).");
        }
        if (batch.skippedLinks() > 0) {
            notes.add("Skipped " + plural(batch.skippedLinks(), "symbolic link") + " in folders (links are not followed).");
        }
        if (unsupported > 0) {
            notes.add("Unsupported documents are kept with their original XML but are not listed in the invoice table.");
        }
        return notes;
    }

    public int total() {
        return lines.size();
    }

    public boolean hasProblems() {
        return unsupported + invalid + failed > 0;
    }

    /** {@code 12 documents: 7 imported, 1 duplicate, 1 unsupported, 3 invalid, 0 failed.} */
    public String headline() {
        return plural(total(), "document") + ": " + imported + " imported, " + duplicate + " duplicate, "
                + unsupported + " unsupported, " + invalid + " invalid, " + failed + " failed.";
    }

    /** All lines and notes as plain text, for the clipboard. */
    public String asText() {
        StringBuilder text = new StringBuilder();
        lines.forEach(line -> text.append(line.asText()).append('\n'));
        text.append(headline()).append('\n');
        notes.forEach(note -> text.append("Note: ").append(note).append('\n'));
        return text.toString();
    }

    static String plural(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
