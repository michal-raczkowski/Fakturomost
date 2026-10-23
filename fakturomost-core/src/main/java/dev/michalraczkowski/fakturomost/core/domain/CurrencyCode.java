package dev.michalraczkowski.fakturomost.core.domain;

import java.util.Currency;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Three-letter currency code as written in the invoice ({@code KodWaluty}).
 *
 * <p>Deliberately not {@link Currency}: the FA(3) code list contains codes that the JDK does not
 * know (for example {@code GGP}, {@code IMP}, {@code JEP}, {@code UYW} on Java 21), and the JDK's
 * list changes between updates. Mapping straight to {@code Currency} would turn a schema-valid
 * invoice into a parse failure. Use {@link #toCurrency()} where JDK formatting helps.
 */
public record CurrencyCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z]{3}");

    public static final CurrencyCode PLN = new CurrencyCode("PLN");

    public CurrencyCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Currency code must be three upper-case letters");
        }
    }

    /** The JDK currency for this code, if the running JDK knows it. */
    public Optional<Currency> toCurrency() {
        try {
            return Optional.of(Currency.getInstance(value));
        } catch (IllegalArgumentException unknownToJdk) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
