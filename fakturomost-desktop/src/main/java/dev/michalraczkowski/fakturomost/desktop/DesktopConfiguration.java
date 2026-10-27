package dev.michalraczkowski.fakturomost.desktop;

import dev.michalraczkowski.fakturomost.core.ProductInfo;
import dev.michalraczkowski.fakturomost.core.application.DocumentStore;
import dev.michalraczkowski.fakturomost.core.application.InMemoryDocumentStore;
import dev.michalraczkowski.fakturomost.core.application.InvoiceCatalog;
import dev.michalraczkowski.fakturomost.core.application.InvoiceExports;
import dev.michalraczkowski.fakturomost.core.application.InvoiceImporter;
import dev.michalraczkowski.fakturomost.core.fa3.Fa3Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Turns the annotation-free core classes into beans (ADR 0001: core knows nothing about Spring).
 *
 * <p>The UI gets only the three use cases ({@link InvoiceImporter}, {@link InvoiceCatalog},
 * {@link InvoiceExports}); the store and the parser stay wiring details
 * ({@code DesktopArchitectureTest}).
 */
@Configuration(proxyBeanMethods = false)
public class DesktopConfiguration {

    @Bean
    ProductInfo productInfo(@Value("${fakturomost.version}") String version) {
        return new ProductInfo("Fakturomost", version);
    }

    /**
     * M4: in memory, lost on exit — the UI says so ({@code Notices.BANNER}). Replaced by the
     * encrypted SQLite store in M5. Exposed as the port type so nothing depends on the implementation.
     */
    @Bean
    DocumentStore documentStore() {
        return new InMemoryDocumentStore();
    }

    /**
     * Validates against the bundled XSDs. Compiles the schema once, during context start — which
     * runs in {@code Application.init()}, off the JavaFX thread.
     */
    @Bean
    Fa3Parser fa3Parser() {
        return new Fa3Parser();
    }

    @Bean
    InvoiceImporter invoiceImporter(Fa3Parser parser, DocumentStore store) {
        return new InvoiceImporter(parser, store);
    }

    @Bean
    InvoiceCatalog invoiceCatalog(DocumentStore store) {
        return new InvoiceCatalog(store);
    }

    @Bean
    InvoiceExports invoiceExports(DocumentStore store) {
        return new InvoiceExports(store);
    }
}
