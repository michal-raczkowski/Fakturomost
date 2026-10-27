package dev.michalraczkowski.fakturomost.desktop.ui.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.michalraczkowski.fakturomost.core.application.ImportBatch;
import dev.michalraczkowski.fakturomost.core.application.ImportResult;
import dev.michalraczkowski.fakturomost.desktop.DemoData;
import dev.michalraczkowski.fakturomost.desktop.ui.model.ImportSummary.Status;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ImportSummaryTest {

    @Test
    void demoFolderGivesOneLinePerDocumentAndTheDocumentedCounts() {
        ImportSummary summary = ImportSummary.of(DemoData.Services.create().importDemoFolder());

        assertThat(summary.headline())
                .isEqualTo("12 documents: 7 imported, 1 duplicate, 1 unsupported, 3 invalid, 0 failed.");
        assertThat(summary.lines()).hasSize(12);
        assertThat(summary.hasProblems()).isTrue();
        assertThat(summary.lines().stream().filter(l -> l.status().isProblem())).hasSize(4);
        assertThat(summary.notes()).containsExactly(
                "Skipped 1 other file in folders (only .xml, .zip and .pdf files are read from folders).",
                "Unsupported documents are kept with their original XML but are not listed in the invoice table.");
    }

    @Test
    void duplicateLineNamesTheFirstSource() {
        ImportSummary summary = ImportSummary.of(DemoData.Services.create().importDemoFolder());

        ImportSummary.Line duplicate = summary.lines().stream()
                .filter(l -> l.status() == Status.DUPLICATE).findFirst().orElseThrow();
        assertThat(duplicate.source()).endsWith("kopia-9999999999-20261009-3A41F0C20002-3E.xml");
        assertThat(duplicate.detail())
                .isEqualTo("same content as " + DemoData.file("9999999999-20261009-3A41F0C20002-3E.xml"));
    }

    @Test
    void linesUseTheCliFormatAndNeverInvoiceContents() {
        ImportSummary summary = ImportSummary.of(DemoData.Services.create().importDemoFolder());

        assertThat(summary.lines().getFirst().asText()).isEqualTo("Imported     "
                + DemoData.file("9999999999-20261002-3A41F0C20001-22.xml")
                + "  [KSeF 9999999999-20261002-3A41F0C20001-22]");
        // Names, NIPs and amounts from the demo invoices must not appear.
        assertThat(summary.asText()).doesNotContain("Kawiarnia", "5550000001", "5460", "FV/2026");
    }

    @Test
    void notesForCancelledEmptyAndSkippedLinks() {
        ImportSummary cancelled = ImportSummary.of(new ImportBatch(List.of(), Set.of(), 0, 2, true));
        assertThat(cancelled.notes()).containsExactly(
                "Import cancelled. Documents listed here were processed; the rest was not read.",
                "Skipped 2 symbolic links in folders (links are not followed).");

        ImportSummary empty = ImportSummary.of(new ImportBatch(List.of(), Set.of(), 0, 0, false));
        assertThat(empty.notes()).containsExactly("No documents found.");
        assertThat(empty.headline()).isEqualTo("0 documents: 0 imported, 0 duplicate, 0 unsupported, 0 invalid, 0 failed.");
        assertThat(empty.hasProblems()).isFalse();
    }

    @Test
    void failedShowsOnlyTheReasonNotTheCause() {
        ImportResult failed = new ImportResult.Failed("x.xml", "Could not read file: AccessDeniedException",
                new IOException("secret NIP 5550000001"));

        ImportSummary summary = ImportSummary.of(new ImportBatch(List.of(failed), Set.of(), 0, 0, false));

        assertThat(summary.lines()).containsExactly(
                new ImportSummary.Line(Status.FAILED, "x.xml", "Could not read file: AccessDeniedException"));
        assertThat(summary.asText()).doesNotContain("secret");
    }
}
