package dev.michalraczkowski.fakturomost.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import dev.michalraczkowski.fakturomost.core.application.ImportBatch;
import dev.michalraczkowski.fakturomost.core.application.InMemoryDocumentStore;
import dev.michalraczkowski.fakturomost.core.application.InvoiceCatalog;
import dev.michalraczkowski.fakturomost.core.application.InvoiceExports;
import dev.michalraczkowski.fakturomost.core.application.InvoiceImporter;
import dev.michalraczkowski.fakturomost.core.fa3.Fa3Parser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The repository's {@code demo-data/} folder (override with {@code -Dfakturomost.demoDataDir=...},
 * like core's {@code DemoDatasetTest}). Its expected outcomes are pinned by that core test; desktop
 * tests rely on them. Fails, rather than skips, when the folder is missing.
 */
public final class DemoData {

    private DemoData() {
    }

    public static Path folder() {
        Path folder = Path.of(System.getProperty("fakturomost.demoDataDir", "../demo-data"));
        assertThat(folder).as("demo data folder (run from fakturomost-desktop/)").isDirectory();
        return folder;
    }

    public static Path file(String name) {
        Path file = folder().resolve(name);
        assertThat(Files.isRegularFile(file)).as(name).isTrue();
        return file;
    }

    /** Core use cases over one fresh in-memory store, wired by hand (tests only). */
    public record Services(InvoiceImporter importer, InvoiceCatalog catalog, InvoiceExports exports) {

        public static Services create() {
            InMemoryDocumentStore store = new InMemoryDocumentStore();
            return new Services(new InvoiceImporter(new Fa3Parser(), store), new InvoiceCatalog(store),
                    new InvoiceExports(store));
        }

        public ImportBatch importDemoFolder() {
            return importer.importInputs(List.of(folder()));
        }
    }
}
