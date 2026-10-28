package dev.michalraczkowski.fakturomost.core.domain;

import java.util.Arrays;

/** Invoice kind ({@code RodzajFaktury}). Codes are exactly the FA(3) {@code TRodzajFaktury} values. */
public enum InvoiceType {
    /** Basic VAT invoice. */
    VAT("VAT"),
    /** Corrective invoice. */
    CORRECTION("KOR"),
    /** Advance-payment invoice. */
    ADVANCE("ZAL"),
    /** Settlement invoice issued after advance invoices (art. 106f ust. 3). */
    SETTLEMENT("ROZ"),
    /** Simplified invoice (art. 106e ust. 5 pkt 3). */
    SIMPLIFIED("UPR"),
    /** Correction of an advance-payment invoice. */
    ADVANCE_CORRECTION("KOR_ZAL"),
    /** Correction of a settlement invoice. */
    SETTLEMENT_CORRECTION("KOR_ROZ");

    private final String code;

    InvoiceType(String code) {
        this.code = code;
    }

    /** The code as it appears in the XML. */
    public String code() {
        return code;
    }

    public boolean isCorrection() {
        return this == CORRECTION || this == ADVANCE_CORRECTION || this == SETTLEMENT_CORRECTION;
    }

    /**
     * @throws IllegalArgumentException if the code is not an FA(3) invoice type
     */
    public static InvoiceType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown invoice type code"));
    }
}
