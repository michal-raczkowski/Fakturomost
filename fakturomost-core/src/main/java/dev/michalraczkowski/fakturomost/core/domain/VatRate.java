package dev.michalraczkowski.fakturomost.core.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

/**
 * VAT rate of a line item ({@code P_12}). Not a number: FA(3) also uses codes for exempt,
 * reverse-charge and out-of-scope supplies, and three different 0% rates.
 * Codes are exactly the FA(3) {@code TStawkaPodatku} values.
 */
public enum VatRate {
    RATE_23("23", "23"),
    RATE_22("22", "22"),
    RATE_8("8", "8"),
    RATE_7("7", "7"),
    RATE_5("5", "5"),
    RATE_4("4", "4"),
    RATE_3("3", "3"),
    /** 0% on domestic supplies (excluding intra-EU supply and export). */
    ZERO_DOMESTIC("0 KR", "0"),
    /** 0% on intra-Community supply of goods (WDT). */
    ZERO_INTRA_EU("0 WDT", "0"),
    /** 0% on export of goods. */
    ZERO_EXPORT("0 EX", "0"),
    /** Exempt ({@code zw}). */
    EXEMPT("zw", null),
    /** Reverse charge ({@code oo}). */
    REVERSE_CHARGE("oo", null),
    /** Not subject to tax in Poland — supplies outside the country (excluding art. 100 and OSS). */
    NOT_SUBJECT_ABROAD("np I", null),
    /** Not subject to tax in Poland — services under art. 100 ust. 1 pkt 4. */
    NOT_SUBJECT_ART_100("np II", null);

    private final String code;
    private final BigDecimal percent;

    VatRate(String code, String percent) {
        this.code = code;
        this.percent = percent == null ? null : new BigDecimal(percent);
    }

    /** The code as it appears in the XML. */
    public String code() {
        return code;
    }

    /** The numeric rate in percent, or empty for exempt / reverse charge / not subject. */
    public Optional<BigDecimal> percent() {
        return Optional.ofNullable(percent);
    }

    /**
     * @throws IllegalArgumentException if the code is not an FA(3) VAT rate
     */
    public static VatRate fromCode(String code) {
        return Arrays.stream(values())
                .filter(rate -> rate.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown VAT rate code"));
    }
}
