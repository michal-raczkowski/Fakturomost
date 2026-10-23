package dev.michalraczkowski.fakturomost.core.domain;

/**
 * Invoice-level tax buckets: the {@code P_13_x} (net) / {@code P_14_x} (VAT) / {@code P_14_xW}
 * (VAT converted to PLN) field groups of FA(3). Buckets name the rate <em>tier</em>, not a fixed
 * percentage: "basic" is 23% today but 22% under older rules.
 */
public enum VatBucket {
    /** Basic rate, currently 23% or 22% ({@code P_13_1}). */
    BASIC_RATE,
    /** First reduced rate, currently 8% or 7% ({@code P_13_2}). */
    FIRST_REDUCED_RATE,
    /** Second reduced rate, currently 5% ({@code P_13_3}). */
    SECOND_REDUCED_RATE,
    /** Flat rate for passenger taxis ({@code P_13_4}). */
    TAXI_FLAT_RATE,
    /** OSS special procedure, chapter 6a ({@code P_13_5}). */
    OSS_PROCEDURE,
    /** 0% domestic ({@code P_13_6_1}). */
    ZERO_DOMESTIC,
    /** 0% intra-Community supply ({@code P_13_6_2}). */
    ZERO_INTRA_EU,
    /** 0% export ({@code P_13_6_3}). */
    ZERO_EXPORT,
    /** Exempt ({@code P_13_7}). */
    EXEMPT,
    /** Supplies outside the country ({@code P_13_8}). */
    OUTSIDE_COUNTRY,
    /** Services under art. 100 ust. 1 pkt 4 ({@code P_13_9}). */
    ART_100_SERVICES,
    /** Domestic reverse charge ({@code P_13_10}). */
    REVERSE_CHARGE,
    /** Margin scheme, art. 119 and 120 ({@code P_13_11}). */
    MARGIN
}
