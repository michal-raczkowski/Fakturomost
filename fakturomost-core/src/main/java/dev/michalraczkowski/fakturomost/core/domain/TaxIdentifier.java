package dev.michalraczkowski.fakturomost.core.domain;

import java.util.Objects;

/**
 * How a party is identified for tax purposes. FA(3) offers a choice for the buyer: a Polish NIP,
 * an EU VAT number, another (foreign) tax ID, or an explicit "no identifier". The seller always
 * has a NIP.
 */
public sealed interface TaxIdentifier {

    /** Human-readable form for lists and exports, e.g. {@code 1111111111} or {@code DE123456789}. */
    String display();

    /**
     * Polish tax identification number. Kept as text and <strong>not</strong> checksum-validated:
     * the official schema only checks a pattern, and the official samples use NIPs that fail the
     * checksum.
     */
    record Nip(String value) implements TaxIdentifier {
        public Nip {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String display() {
            return value;
        }
    }

    /** EU VAT number: country prefix ({@code KodUE}) plus number ({@code NrVatUE}). */
    record EuVatNumber(String countryCode, String number) implements TaxIdentifier {
        public EuVatNumber {
            Objects.requireNonNull(countryCode, "countryCode");
            Objects.requireNonNull(number, "number");
        }

        @Override
        public String display() {
            return countryCode + number;
        }
    }

    /**
     * Any other tax ID ({@code NrID}).
     *
     * @param countryCode issuing country ({@code KodKraju}), or {@code null} when not given
     */
    record OtherTaxId(String countryCode, String value) implements TaxIdentifier {
        public OtherTaxId {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String display() {
            return countryCode == null ? value : countryCode + " " + value;
        }
    }

    /** The invoice states that the party has no tax identifier ({@code BrakID}). */
    record None() implements TaxIdentifier {
        @Override
        public String display() {
            return "";
        }
    }
}
