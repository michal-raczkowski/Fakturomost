package dev.michalraczkowski.fakturomost.desktop.ui;

import dev.michalraczkowski.fakturomost.core.ProductInfo;
import dev.michalraczkowski.fakturomost.core.application.InvoiceCatalog;
import dev.michalraczkowski.fakturomost.core.application.InvoiceExports;
import dev.michalraczkowski.fakturomost.core.application.InvoiceImporter;
import java.util.Objects;

/**
 * What the UI may use: the three application-layer use cases and the product name. Handed over by
 * {@code FakturomostApp} from the Spring context, so no UI class touches Spring, the store or the
 * parser ({@code DesktopArchitectureTest}).
 */
public record UiServices(ProductInfo product, InvoiceImporter importer, InvoiceCatalog catalog, InvoiceExports exports) {

    public UiServices {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(importer, "importer");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(exports, "exports");
    }
}
