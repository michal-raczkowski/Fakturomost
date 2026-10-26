package dev.michalraczkowski.fakturomost.cli;

import dev.michalraczkowski.fakturomost.core.application.ImportLimits;
import dev.michalraczkowski.fakturomost.core.application.InMemoryDocumentStore;
import dev.michalraczkowski.fakturomost.core.application.InvoiceImporter;
import dev.michalraczkowski.fakturomost.core.fa3.Fa3Parser;
import java.util.Objects;

/**
 * The core objects one command run works with, wired by hand (ADR 0006). Every run gets a fresh
 * in-memory store, so nothing outlives the process.
 *
 * <p>The only CLI class allowed to touch {@code core.fa3} ({@code CliArchitectureTest}): the importer
 * needs a parser, everything else goes through {@code core.application}.
 */
record CoreServices(InMemoryDocumentStore store, InvoiceImporter importer) {

    CoreServices {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(importer, "importer");
    }

    static CoreServices create() {
        return create(ImportLimits.DEFAULT);
    }

    /** Smaller limits are for tests only; the CLI itself always uses the defaults. */
    static CoreServices create(ImportLimits limits) {
        InMemoryDocumentStore store = new InMemoryDocumentStore();
        return new CoreServices(store, new InvoiceImporter(new Fa3Parser(), store, limits));
    }
}
