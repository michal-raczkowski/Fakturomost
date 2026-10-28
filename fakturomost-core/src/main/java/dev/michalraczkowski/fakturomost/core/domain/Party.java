package dev.michalraczkowski.fakturomost.core.domain;

import java.util.Objects;

/**
 * Seller ({@code Podmiot1}) or buyer ({@code Podmiot2}).
 *
 * @param taxId   tax identifier; never {@code null} (see {@link TaxIdentifier.None})
 * @param name    name, or {@code null} — optional for the buyer on simplified invoices
 * @param address address, or {@code null} — optional for the buyer
 */
public record Party(TaxIdentifier taxId, String name, Address address) {

    public Party {
        Objects.requireNonNull(taxId, "taxId");
    }
}
