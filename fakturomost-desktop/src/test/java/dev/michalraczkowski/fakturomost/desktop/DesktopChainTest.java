package dev.michalraczkowski.fakturomost.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import dev.michalraczkowski.fakturomost.core.application.DocumentStore;
import dev.michalraczkowski.fakturomost.core.application.ImportBatch;
import dev.michalraczkowski.fakturomost.core.application.InvoiceCatalog;
import dev.michalraczkowski.fakturomost.core.application.InvoiceEntry;
import dev.michalraczkowski.fakturomost.core.application.InvoiceExports;
import dev.michalraczkowski.fakturomost.core.application.InvoiceImporter;
import dev.michalraczkowski.fakturomost.core.application.InvoiceQuery;
import dev.michalraczkowski.fakturomost.desktop.ui.model.ExportFormat;
import dev.michalraczkowski.fakturomost.desktop.ui.model.ImportSummary;
import dev.michalraczkowski.fakturomost.desktop.ui.model.InvoiceDetails;
import dev.michalraczkowski.fakturomost.desktop.ui.model.InvoiceRow;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The UI's chain — import → table → details → export — through the real Spring context and the same
 * calls the JavaFX classes make, minus the toolkit. The manual run-through in the M4 log covers the
 * JavaFX part.
 */
class DesktopChainTest {

    @TempDir
    Path dir;

    @Test
    void importTableDetailsExportThroughTheSpringBeans() throws IOException {
        try (ConfigurableApplicationContext context = DesktopContext.start()) {
            InvoiceImporter importer = context.getBean(InvoiceImporter.class);
            InvoiceCatalog catalog = context.getBean(InvoiceCatalog.class);
            InvoiceExports exports = context.getBean(InvoiceExports.class);

            // Import (what ImportTask does)
            ImportBatch batch = importer.importInputs(List.of(DemoData.folder()));
            assertThat(ImportSummary.of(batch).headline())
                    .isEqualTo("12 documents: 7 imported, 1 duplicate, 1 unsupported, 3 invalid, 0 failed.");

            // Table: the catalog sees what the importer stored, so both share one store
            List<InvoiceEntry> entries = catalog.search(InvoiceQuery.all());
            List<InvoiceRow> rows = entries.stream().map(InvoiceRow::of).toList();
            assertThat(rows).hasSize(7);

            // Details
            InvoiceDetails details = InvoiceDetails.of(catalog.find(rows.get(1).entry().id()).orElseThrow());
            assertThat(details.title()).isEqualTo("FK/2026/10/0001 · Correction (KOR)");

            // Export selected (rows 0 and 1)
            List<InvoiceEntry> selected = List.of(rows.get(0).entry(), rows.get(1).entry());
            Path target = ExportFormat.CSV_EXCEL_PL.normalizeTarget(dir.resolve("chain"));
            ExportFormat.CSV_EXCEL_PL.export(exports, selected, target, false);
            assertThat(Files.readAllLines(target)).hasSize(3); // header + 2 rows
        }
    }

    @Test
    void eachContextHasItsOwnEmptyStore() {
        try (ConfigurableApplicationContext context = DesktopContext.start()) {
            assertThat(context.getBean(InvoiceCatalog.class).search(InvoiceQuery.all())).isEmpty();
            assertThat(context.getBeansOfType(DocumentStore.class)).hasSize(1);
        }
    }
}
