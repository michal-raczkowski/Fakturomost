package dev.michalraczkowski.fakturomost.core.domain;

import java.util.Objects;

/**
 * Postal address in the FA(3) free-form layout.
 *
 * @param countryCode ISO country code ({@code KodKraju})
 * @param line1       first address line ({@code AdresL1})
 * @param line2       second address line ({@code AdresL2}), or {@code null}
 */
public record Address(String countryCode, String line1, String line2) {

    public Address {
        Objects.requireNonNull(countryCode, "countryCode");
        Objects.requireNonNull(line1, "line1");
    }
}
